(ns notify-system.service
  "Core notification service implementing business logic and orchestration"
  (:require [notify-system.db :as db]
            [notify-system.channels :as channels]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.data.json :as json]))

;; Data validation specs (Best practices - validation)
(s/def ::category #{"Sports" "Finance" "Movies"})
(s/def ::content (s/and string? #(> (count %) 0)))
(s/def ::message (s/keys :req-un [::category ::content]))

;; Protocol for notification service (Interface/Abstraction)
(defprotocol NotificationService
  "Protocol defining the notification service interface"
  (send-message [this message] "Send a message to all subscribed users")
  (get-delivery-log [this] "Get delivery log")
  (get-user-preferences [this user-id] "Get user preferences"))

;; Repository interface for data access (Separation of concerns)
(defprotocol NotificationRepository
  "Protocol for data persistence operations"
  (save-notification-log [this log-entry] "Save notification delivery log")
  (get-users-by-category-and-channel [this category channel] "Get users for category/channel")
  (get-categories [this] "Get all categories")
  (get-channels [this] "Get all channels"))

;; Database repository implementation (Dependency inversion)
(defrecord DatabaseRepository []
  NotificationRepository
  (save-notification-log [_ log-entry]
    (try
      (println "DEBUG - Log entry:" log-entry)
      (println "DEBUG - User ID:" (:user-id log-entry))
      (db/execute! 
        "INSERT INTO notifications (user_id, category_id, channel, status, content, metadata, sent_at, error_message) 
         SELECT ?, c.id, ?, ?, ?, ?::jsonb, ?, ?
         FROM categories c WHERE c.name = ?"
        (:user-id log-entry)
        (:channel log-entry)
        (:status log-entry)
        (or (:content log-entry) (:message log-entry))
        (json/write-str (:metadata log-entry {}))
        (java.sql.Timestamp/from (:timestamp log-entry))
        (:error log-entry)
        (:category log-entry))
      (catch Exception e
        (println "Error saving notification log:" (.getMessage e)))))
  
  (get-users-by-category-and-channel [_ category channel]
    (db/get-users-for-category-and-channel category channel))
  
  (get-categories [_]
    (db/get-all-categories))
  
  (get-channels [_]
    (db/get-all-channels)))

;; Core notification service implementation
(defrecord NotificationServiceImpl [repository]
  NotificationService
  (send-message [_ message]
    (try
      ;; Validation (Best practices)
      (when-not (s/valid? ::message message)
        (throw (ex-info "Invalid message format" 
                       {:type :validation-error 
                        :errors (s/explain-data ::message message)})))
      
      (let [category (:category message)
            available-channels (channels/get-available-channels)
            results (atom [])]
        
        ;; Strategy pattern: iterate through all channels
        (doseq [channel-name available-channels]
          (try
            (let [users (get-users-by-category-and-channel repository category channel-name)]
              (when (seq users)
                (println (format "Sending %s notifications via %s to %d users"
                               category channel-name (count users)))
                
                ;; Send to each user via this channel
                (doseq [user users]
                  (try
                    (let [channel (channels/create-channel channel-name)
                          result (channels/send-notification channel user message)]
                      ;; Log the delivery attempt
                      (save-notification-log repository 
                        (assoc result 
                               :category category
                               :metadata {:original-message message}))
                      (swap! results conj result))
                    (catch Exception e
                      (let [error-result {:channel channel-name
                                         :user-id (:id user)
                                         :category category
                                         :status "failed"
                                         :error (.getMessage e)
                                         :timestamp (java.time.Instant/now)}]
                        (save-notification-log repository error-result)
                        (swap! results conj error-result)
                        (println (format "Error sending to user %s via %s: %s"
                                       (:name user) channel-name (.getMessage e)))))))))
            (catch Exception e
              (println (format "Error processing channel %s: %s" channel-name (.getMessage e))))))
        
        {:status "completed"
         :message "Notification processing completed"
         :results @results
         :summary {:total-attempts (count @results)
                  :successful (count (filter #(= "sent" (:status %)) @results))
                  :failed (count (filter #(= "failed" (:status %)) @results))}})
      
      (catch Exception e
        (println "Error in send-message:" (.getMessage e))
        {:status "error"
         :message (.getMessage e)
         :timestamp (java.time.Instant/now)})))
  
  (get-delivery-log [_]
    (try
      (db/query "SELECT n.*, c.name as category_name, u.name as user_name, u.email
                 FROM notifications n
                 JOIN categories c ON n.category_id = c.id
                 JOIN users u ON n.user_id = u.id
                 ORDER BY n.created_at DESC
                 LIMIT 100")
      (catch Exception e
        (println "Error getting delivery log:" (.getMessage e))
        [])))
  
  (get-user-preferences [_ user-id]
    (try
      {:categories (db/get-user-subscribed-categories user-id)
       :channels (db/get-user-preferred-channels user-id)}
      (catch Exception e
        (println "Error getting user preferences:" (.getMessage e))
        {:categories [] :channels []}))))

;; Factory function to create service (Dependency injection)
(defn create-notification-service
  "Create notification service with dependency injection"
  ([]
   (create-notification-service (->DatabaseRepository)))
  ([repository]
   (->NotificationServiceImpl repository)))

;; Service facade for common operations (Facade pattern)
(defn send-notification
  "Send a notification message - main entry point"
  [category content]
  (let [service (create-notification-service)
        message {:category category :content content}]
    (send-message service message)))

(defn get-notification-history
  "Get notification delivery history"
  []
  (let [service (create-notification-service)]
    (get-delivery-log service)))

(defn get-system-stats
  "Get system statistics"
  []
  (let [service (create-notification-service)
        log (get-delivery-log service)]
    {:total-notifications (count log)
     :successful-notifications (count (filter #(= "sent" (:status %)) log))
     :failed-notifications (count (filter #(= "failed" (:status %)) log))
     :channels-used (->> log (map :channel) distinct)
     :categories-used (->> log (map :category_name) distinct)}))

;; Validation helpers (Best practices)
(defn validate-category
  "Validate if category exists in the system"
  [category]
  (let [valid-categories #{"Sports" "Finance" "Movies"}]
    (when-not (contains? valid-categories category)
      (throw (ex-info (str "Invalid category: " category) 
                     {:type :validation-error 
                      :valid-categories valid-categories})))))

(defn validate-content
  "Validate message content"
  [content]
  (when (or (nil? content) (empty? (str/trim content)))
    (throw (ex-info "Message content cannot be empty" {:type :validation-error}))))

;; Error handling wrapper (Best practices)
(defn safe-send-notification
  "Safe wrapper for sending notifications with comprehensive error handling"
  [category content]
  (try
    (validate-category category)
    (validate-content content)
    (send-notification category content)
    (catch Exception e
      {:status "error"
       :message (.getMessage e)
       :type (or (:type (ex-data e)) :unknown-error)
       :timestamp (java.time.Instant/now)})))