(ns notify-system.users
  "User management and preference handling"
  (:require [notify-system.db :as db]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; User data validation specs (Best practices)
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::name (s/and string? #(> (count (str/trim %)) 0)))
(s/def ::phone (s/and string? #(re-matches #"\+?[\d\s\-\(\)]+" %)))
(s/def ::user (s/keys :req-un [::email ::name] :opt-un [::phone]))

;; Protocol for user service (Interface abstraction)
(defprotocol UserService
  "Protocol defining user management operations"
  (get-user-by-id [this user-id] "Get user by ID")
  (get-user-by-email [this email] "Get user by email")
  (create-user [this user-data] "Create a new user")
  (update-user [this user-id user-data] "Update user information")
  (get-user-categories [this user-id] "Get user's subscribed categories")
  (get-user-channels [this user-id] "Get user's preferred channels")
  (update-user-preferences [this user-id categories channels] "Update user preferences"))

;; User service implementation
(defrecord UserServiceImpl []
  UserService
  (get-user-by-id [_ user-id]
    (try
      (first (db/query "SELECT * FROM users WHERE id = ?" user-id))
      (catch Exception e
        (println "Error getting user by ID:" (.getMessage e))
        nil)))
  
  (get-user-by-email [_ email]
    (try
      (first (db/query "SELECT * FROM users WHERE email = ?" email))
      (catch Exception e
        (println "Error getting user by email:" (.getMessage e))
        nil)))
  
  (create-user [_ user-data]
    (try
      ;; Validation
      (when-not (s/valid? ::user user-data)
        (throw (ex-info "Invalid user data" 
                       {:type :validation-error 
                        :errors (s/explain-data ::user user-data)})))
      
      ;; Check if user already exists
      (when (get-user-by-email (->UserServiceImpl) (:email user-data))
        (throw (ex-info "User with this email already exists" 
                       {:type :duplicate-user :email (:email user-data)})))
      
      ;; Create user
      (let [result (db/execute! 
                     "INSERT INTO users (email, name, phone) VALUES (?, ?, ?) RETURNING id"
                     (:email user-data)
                     (:name user-data)
                     (:phone user-data))]
        (assoc user-data :id (:users/id result)))
      
      (catch Exception e
        (println "Error creating user:" (.getMessage e))
        (throw e))))
  
  (update-user [_ user-id user-data]
    (try
      (when-not (s/valid? ::user user-data)
        (throw (ex-info "Invalid user data" 
                       {:type :validation-error 
                        :errors (s/explain-data ::user user-data)})))
      
      (db/execute! 
        "UPDATE users SET name = ?, phone = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
        (:name user-data)
        (:phone user-data)
        user-id)
      
      (get-user-by-id (->UserServiceImpl) user-id)
      
      (catch Exception e
        (println "Error updating user:" (.getMessage e))
        (throw e))))
  
  (get-user-categories [_ user-id]
    (try
      (db/get-user-subscribed-categories user-id)
      (catch Exception e
        (println "Error getting user categories:" (.getMessage e))
        [])))
  
  (get-user-channels [_ user-id]
    (try
      (db/get-user-preferred-channels user-id)
      (catch Exception e
        (println "Error getting user channels:" (.getMessage e))
        [])))
  
  (update-user-preferences [_ user-id categories channels]
    (try
      (db/transaction
        (fn [_tx]
          ;; Clear existing preferences
          (db/execute! "DELETE FROM user_category_subscriptions WHERE user_id = ?::uuid" user-id)
          (db/execute! "DELETE FROM user_channel_preferences WHERE user_id = ?::uuid" user-id)
          
          ;; Add new category subscriptions
          (doseq [category categories]
            (db/execute! 
              "INSERT INTO user_category_subscriptions (user_id, category_id)
               SELECT ?::uuid, c.id FROM categories c WHERE c.name = ?"
              user-id category))
          
          ;; Add new channel preferences
          (doseq [channel channels]
            (db/execute! 
              "INSERT INTO user_channel_preferences (user_id, channel_id, enabled)
               SELECT ?::uuid, nc.id, true FROM notification_channels nc WHERE nc.name = ?"
              user-id channel))))
      
      {:categories (get-user-categories (->UserServiceImpl) user-id)
       :channels (get-user-channels (->UserServiceImpl) user-id)}
      
      (catch Exception e
        (println "Error updating user preferences:" (.getMessage e))
        (throw e)))))

;; Factory function
(defn create-user-service
  "Create user service instance"
  []
  (->UserServiceImpl))

;; Convenience functions (Facade pattern)
(defn find-user
  "Find user by email or ID"
  [identifier]
  (let [service (create-user-service)]
    (if (re-matches #".+@.+\..+" (str identifier))
      (get-user-by-email service identifier)
      (get-user-by-id service identifier))))

(defn register-user
  "Register a new user with basic validation"
  [email name & {:keys [phone categories channels]}]
  (let [service (create-user-service)
        user-data {:email email :name name :phone phone}]
    (try
      (let [created-user (create-user service user-data)]
        (when (or categories channels)
          (update-user-preferences service 
                                 (:id created-user)
                                 (or categories [])
                                 (or channels [])))
        created-user)
      (catch Exception e
        {:error (.getMessage e)
         :type (or (:type (ex-data e)) :unknown-error)}))))

(defn get-user-profile
  "Get complete user profile including preferences"
  [user-id]
  (let [service (create-user-service)]
    (when-let [user (get-user-by-id service user-id)]
      (assoc user
             :subscribed-categories (get-user-categories service user-id)
             :preferred-channels (get-user-channels service user-id)))))

(defn update-preferences
  "Update user preferences"
  [user-id categories channels]
  (let [service (create-user-service)]
    (try
      (update-user-preferences service user-id categories channels)
      {:status "success" :message "Preferences updated successfully"}
      (catch Exception e
        {:status "error" 
         :message (.getMessage e)
         :type (or (:type (ex-data e)) :unknown-error)}))))

;; Validation helpers
(defn valid-email?
  "Check if email format is valid"
  [email]
  (s/valid? ::email email))

(defn valid-user-data?
  "Check if user data is valid"
  [user-data]
  (s/valid? ::user user-data))

;; User lookup utilities
(defn get-all-users
  "Get all users (for admin purposes)"
  []
  (try
    (db/query "SELECT id, email, name, phone, created_at FROM users ORDER BY name")
    (catch Exception e
      (println "Error getting all users:" (.getMessage e))
      [])))

(defn search-users
  "Search users by name or email"
  [search-term]
  (try
    (db/query 
      "SELECT id, email, name, phone FROM users 
       WHERE name ILIKE ? OR email ILIKE ? 
       ORDER BY name LIMIT 50"
      (str "%" search-term "%")
      (str "%" search-term "%"))
    (catch Exception e
      (println "Error searching users:" (.getMessage e))
      [])))