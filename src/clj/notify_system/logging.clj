(ns notify-system.logging
  "Logging service and repository for notification system"
  (:require [notify-system.db :as db]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s]))

;; Specs for log validation
(s/def ::user-id uuid?)
(s/def ::category string?)
(s/def ::channel #{"SMS" "Email" "Push"})
(s/def ::status #{"pending" "sent" "delivered" "failed"})
(s/def ::content string?)
;; Support both namespaced and simple keys for user data
(s/def ::user-data (s/or :namespaced (s/keys :req [:users/id :users/name :users/email :users/phone])
                        :simple (s/keys :req-un [::id ::name ::email ::phone])))
(s/def ::timestamp inst?)
(s/def ::log-entry (s/keys :req-un [::user-id ::category ::channel ::status ::content ::user-data ::timestamp]
                          :opt-un [::error-message ::metadata]))

;; Protocol for log repository operations
(defprotocol LogRepository
  "Protocol for log data persistence operations"
  (save-log [this log-entry] "Save a notification log entry")
  (get-logs [this] "Get all logs sorted by timestamp descending")
  (get-logs-paginated [this offset limit] "Get paginated logs")
  (get-logs-by-user [this user-id] "Get logs for a specific user")
  (get-logs-by-category [this category] "Get logs for a specific category")
  (get-logs-by-status [this status] "Get logs by status")
  (get-log-statistics [this] "Get aggregated log statistics"))

;; Database implementation of LogRepository
(defrecord DatabaseLogRepository []
  LogRepository
  (save-log [_ log-entry]
    (try
      (let [user-data (:user-data log-entry)
            metadata (merge 
                       {:user-name (:name user-data)
                        :user-email (:email user-data)
                        :user-phone (:phone user-data)}
                       (:metadata log-entry {}))]
        (db/execute! 
          "INSERT INTO notifications (user_id, category_id, channel, status, content, metadata, sent_at, error_message) 
           SELECT ?, c.id, ?, ?, ?, ?::jsonb, ?, ?
           FROM categories c WHERE c.name = ?"
          (:user-id log-entry)
          (:channel log-entry)
          (:status log-entry)
          (:content log-entry)
          (json/write-str metadata)
          (java.sql.Timestamp/from (:timestamp log-entry))
          (:error-message log-entry)
          (:category log-entry)))
      (catch Exception e
        (println "Error saving log entry:" (.getMessage e))
        (throw e))))
  
  (get-logs [_]
    (try
      (db/query 
        "SELECT n.*, c.name as category_name, u.name as user_name, u.email as user_email, u.phone as user_phone
         FROM notifications n
         JOIN categories c ON n.category_id = c.id
         JOIN users u ON n.user_id = u.id
         ORDER BY n.created_at DESC")
      (catch Exception e
        (println "Error retrieving logs:" (.getMessage e))
        [])))
  
  (get-logs-paginated [_ offset limit]
    (try
      (db/query 
        "SELECT n.*, c.name as category_name, u.name as user_name, u.email as user_email, u.phone as user_phone
         FROM notifications n
         JOIN categories c ON n.category_id = c.id
         JOIN users u ON n.user_id = u.id
         ORDER BY n.created_at DESC
         LIMIT ? OFFSET ?"
        limit offset)
      (catch Exception e
        (println "Error retrieving paginated logs:" (.getMessage e))
        [])))
  
  (get-logs-by-user [_ user-id]
    (try
      (db/query 
        "SELECT n.*, c.name as category_name, u.name as user_name, u.email as user_email, u.phone as user_phone
         FROM notifications n
         JOIN categories c ON n.category_id = c.id
         JOIN users u ON n.user_id = u.id
         WHERE n.user_id = ?
         ORDER BY n.created_at DESC"
        user-id)
      (catch Exception e
        (println "Error retrieving user logs:" (.getMessage e))
        [])))
  
  (get-logs-by-category [_ category]
    (try
      (db/query 
        "SELECT n.*, c.name as category_name, u.name as user_name, u.email as user_email, u.phone as user_phone
         FROM notifications n
         JOIN categories c ON n.category_id = c.id
         JOIN users u ON n.user_id = u.id
         WHERE c.name = ?
         ORDER BY n.created_at DESC"
        category)
      (catch Exception e
        (println "Error retrieving category logs:" (.getMessage e))
        [])))
  
  (get-logs-by-status [_ status]
    (try
      (db/query 
        "SELECT n.*, c.name as category_name, u.name as user_name, u.email as user_email, u.phone as user_phone
         FROM notifications n
         JOIN categories c ON n.category_id = c.id
         JOIN users u ON n.user_id = u.id
         WHERE n.status = ?
         ORDER BY n.created_at DESC"
        status)
      (catch Exception e
        (println "Error retrieving status logs:" (.getMessage e))
        [])))
  
  (get-log-statistics [_]
    (try
      (let [stats (first (db/query 
                           "SELECT 
                             COUNT(*) as total_notifications,
                             COUNT(CASE WHEN status = 'sent' THEN 1 END) as successful,
                             COUNT(CASE WHEN status = 'failed' THEN 1 END) as failed,
                             COUNT(CASE WHEN status = 'pending' THEN 1 END) as pending,
                             COUNT(DISTINCT channel) as channels_used,
                             COUNT(DISTINCT category_id) as categories_used
                            FROM notifications n
                            JOIN categories c ON n.category_id = c.id"))
            by-channel (db/query 
                         "SELECT channel, COUNT(*) as count 
                          FROM notifications 
                          GROUP BY channel 
                          ORDER BY count DESC")
            by-category (db/query 
                          "SELECT c.name as category, COUNT(*) as count 
                           FROM notifications n
                           JOIN categories c ON n.category_id = c.id
                           GROUP BY c.name 
                           ORDER BY count DESC")]
        (assoc stats 
               :by-channel by-channel
               :by-category by-category))
      (catch Exception e
        (println "Error retrieving log statistics:" (.getMessage e))
        {:total-notifications 0 :successful 0 :failed 0 :pending 0}))))

;; Protocol for logging service operations
(defprotocol LoggingService
  "Protocol for logging service operations"
  (log-notification [this user message-category channel status] "Log a notification with success status")
  (log-notification-error [this user message-category channel error] "Log a failed notification")
  (get-notification-history [this] "Get notification history")
  (get-notification-statistics [this] "Get notification statistics"))

;; Helper function to normalize user data
(defn normalize-user
  "Normalize user data to handle both namespaced and simple keys"
  [user]
  (cond
    ;; Already has simple keys
    (and (:id user) (:name user) (:email user) (:phone user))
    user
    
    ;; Has namespaced keys - convert to simple keys
    (and (:users/id user) (:users/name user) (:users/email user) (:users/phone user))
    {:id (:users/id user)
     :name (:users/name user)
     :email (:users/email user)
     :phone (:users/phone user)}
    
    ;; Missing required fields - provide defaults
    :else
    {:id (or (:id user) (:users/id user) (java.util.UUID/randomUUID))
     :name (or (:name user) (:users/name user) "Unknown")
     :email (or (:email user) (:users/email user) "unknown@example.com")
     :phone (or (:phone user) (:users/phone user) "unknown")}))

;; Implementation of LoggingService
(defrecord LoggingServiceImpl [repository]
  LoggingService
  (log-notification [_ user message-category channel status]
    (try
      (let [normalized-user (normalize-user user)]
        (when-not (s/valid? ::user-data normalized-user)
          (println "DEBUG - Invalid user data:" normalized-user)
          (println "DEBUG - Spec validation:" (s/explain-data ::user-data normalized-user))
          (throw (ex-info "Invalid user data" {:user normalized-user})))
        
        (let [log-entry {:user-id (:id normalized-user)
                         :category message-category
                         :channel channel
                         :status status
                         :content (str "Notification sent via " channel " for " message-category)
                         :user-data normalized-user
                         :timestamp (java.time.Instant/now)
                         :metadata {:delivery-method channel
                                    :notification-type message-category}}]
          (when (s/valid? ::log-entry log-entry)
            (save-log repository log-entry)
            log-entry)))
      (catch Exception e
        (println "Error logging notification:" (.getMessage e))
        (throw e))))
  
  (log-notification-error [_ user message-category channel error]
    (try
      (let [normalized-user (normalize-user user)
            log-entry {:user-id (:id normalized-user)
                       :category message-category
                       :channel channel
                       :status "failed"
                       :content (str "Failed to send notification via " channel " for " message-category)
                       :user-data normalized-user
                       :timestamp (java.time.Instant/now)
                       :error-message error
                       :metadata {:delivery-method channel
                                  :notification-type message-category
                                  :error-details error}}]
        (save-log repository log-entry)
        log-entry)
      (catch Exception e
        (println "Error logging notification error:" (.getMessage e))
        (throw e))))
  
  (get-notification-history [_]
    (get-logs repository))
  
  (get-notification-statistics [_]
    (get-log-statistics repository)))

;; Factory functions
(defn create-log-repository
  "Create a log repository instance"
  []
  (->DatabaseLogRepository))

(defn create-logging-service
  "Create a logging service instance"
  ([]
   (create-logging-service (create-log-repository)))
  ([repository]
   (->LoggingServiceImpl repository)))

;; Helper functions for formatting logs
(defn format-log-entry
  "Format a log entry for display"
  [log-entry]
  {:id (:notifications/id log-entry)
   :user {:id (:notifications/user_id log-entry)
          :name (:users/user_name log-entry)
          :email (:users/user_email log-entry)
          :phone (:users/user_phone log-entry)}
   :category (:categories/category_name log-entry)
   :channel (:notifications/channel log-entry)
   :status (:notifications/status log-entry)
   :content (:notifications/content log-entry)
   :timestamp (:notifications/created_at log-entry)
   :sent-at (:notifications/sent_at log-entry)
   :error-message (:notifications/error_message log-entry)
   :metadata (when-let [metadata-obj (:notifications/metadata log-entry)]
               (try
                 (if (string? metadata-obj)
                   (json/read-str metadata-obj :key-fn keyword)
                   (json/read-str (.getValue metadata-obj) :key-fn keyword))
                 (catch Exception _ {})))})

(defn format-logs
  "Format multiple log entries for display"
  [logs]
  (vec (map format-log-entry logs)))
