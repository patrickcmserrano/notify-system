(ns notify-system.db
  (:require [next.jdbc :as jdbc]
            [hikari-cp.core :as hikari]
            [migratus.core :as migratus]))

;; PostgreSQL database configuration
(def db-config
  {:adapter         "postgresql"
   :server-name     (or (System/getenv "DB_HOST") "localhost")
   :port-number     (Integer/parseInt (or (System/getenv "DB_PORT") "5433"))
   :database-name   (or (System/getenv "DB_NAME") "notifications")
   :username        (or (System/getenv "DB_USER") "notify_user")
   :password        (or (System/getenv "DB_PASSWORD") "notify_pass")
   :maximum-pool-size 10
   :minimum-idle      2})

;; Hikari connection pool configuration
(def datasource-options
  {:jdbc-url (str "jdbc:postgresql://"
                  (:server-name db-config) ":"
                  (:port-number db-config) "/"
                  (:database-name db-config))
   :username (:username db-config)
   :password (:password db-config)
   :maximum-pool-size (:maximum-pool-size db-config)
   :minimum-idle (:minimum-idle db-config)})

;; Connection pool
(defonce datasource (atom nil))

(defn init-db! 
  "Initializes the database connection pool"
  []
  (when-not @datasource
    (reset! datasource (hikari/make-datasource datasource-options))
    (println "Database connection pool initialized")))

(defn close-db! 
  "Closes the connection pool"
  []
  (when @datasource
    (hikari/close-datasource @datasource)
    (reset! datasource nil)
    (println "Database connection pool closed")))

(defn get-connection 
  "Returns a connection from the pool"
  []
  (when-not @datasource
    (init-db!))
  @datasource)

;; Migratus configuration
(def migration-config
  {:store         :database
   :migration-dir "resources/migrations/"
   :db            {:dbtype   "postgresql"
                   :host     (:server-name db-config)
                   :port     (:port-number db-config)
                   :dbname   (:database-name db-config)
                   :user     (:username db-config)
                   :password (:password db-config)}})

(defn migrate! 
  "Executes all pending migrations"
  []
  (migratus/migrate migration-config))

(defn rollback! 
  "Rolls back the last migration"
  []
  (migratus/rollback migration-config))

(defn create-migration! 
  "Creates a new migration with the specified name"
  [name]
  (migratus/create migration-config name))

;; Utility functions for queries
(defn test-connection
  "Test database connection for health checks"
  []
  (try
    (when-not @datasource
      (init-db!))
    (jdbc/execute! @datasource ["SELECT 1"])
    true
    (catch Exception e
      (println "Database connection test failed:" (.getMessage e))
      false)))

(defn query
  "Executes a SELECT query"
  [sql & params]
  (jdbc/execute! (get-connection) (into [sql] params)))

(defn execute!
  "Executes an INSERT/UPDATE/DELETE query"
  [sql & params]
  (jdbc/execute! (get-connection) (into [sql] params)))

(defn execute-one!
  "Executes an INSERT/UPDATE/DELETE query expecting exactly one result"
  [sql & params]
  (jdbc/execute-one! (get-connection) (into [sql] params)))

(defn transaction
  "Executes a function within a transaction"
  [f]
  (jdbc/with-transaction [tx (get-connection)]
    (f tx)))

;; Seeding functions
(defn seed-categories!
  "Populates the categories table with initial data"
  []
  (let [categories ["Sports" "Finance" "Movies"]]
    (doseq [category categories]
      (try
        (execute! "INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                  category (str "Notifications related to " category))
        (println (str "Category '" category "' seeded"))
        (catch Exception e
          (println (str "Error seeding category '" category "': " (.getMessage e))))))))

(defn seed-channels!
  "Populates the notification channels table"
  []
  (let [channels [["SMS" "Short Message Service notifications"]
                  ["Email" "Email notifications"]  
                  ["Push" "Push notifications"]]]
    (doseq [[channel description] channels]
      (try
        (execute! "INSERT INTO notification_channels (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                  channel description)
        (println (str "Channel '" channel "' seeded"))
        (catch Exception e
          (println (str "Error seeding channel '" channel "': " (.getMessage e))))))))

(defn seed-users!
  "Populates example users for testing"
  []
  (let [users [["john.doe@example.com" "John Doe" "+1234567890" ["Sports" "Finance"] ["SMS" "Email"]]
               ["jane.smith@example.com" "Jane Smith" "+1234567891" ["Movies" "Finance"] ["Email" "Push"]]
               ["bob.wilson@example.com" "Bob Wilson" "+1234567892" ["Sports" "Movies"] ["SMS" "Push"]]]]
    (doseq [[email name phone subscribed-categories preferred-channels] users]
      (try
        ;; Insert user
        (let [user-result (execute-one! "INSERT INTO users (email, name, phone) VALUES (?, ?, ?) ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, phone = EXCLUDED.phone RETURNING id"
                                        email name phone)
              user-id (:users/id user-result)]
          
          ;; Insert user category subscriptions
          (doseq [category subscribed-categories]
            (execute! "INSERT INTO user_category_subscriptions (user_id, category_id) 
                       SELECT ?, c.id FROM categories c WHERE c.name = ? 
                       ON CONFLICT (user_id, category_id) DO NOTHING"
                      user-id category))
          
          ;; Insert user channel preferences  
          (doseq [channel preferred-channels]
            (execute! "INSERT INTO user_channel_preferences (user_id, channel_id, enabled) 
                       SELECT ?, nc.id, true FROM notification_channels nc WHERE nc.name = ? 
                       ON CONFLICT (user_id, channel_id) DO UPDATE SET enabled = EXCLUDED.enabled"
                      user-id channel))
          
          (println (str "User '" name "' seeded with preferences")))
        (catch Exception e
          (println (str "Error seeding user '" name "': " (.getMessage e))))))))

(defn seed-all!
  "Executes all seeders in the correct order"
  []
  (println "Starting database seeding...")
  (seed-categories!)
  (seed-channels!)
  (seed-users!)
  (println "Database seeding completed!"))

;; Query functions for categories and preferences
(defn get-all-categories
  "Returns all active categories"
  []
  (query "SELECT * FROM categories WHERE active = true ORDER BY name"))

(defn get-all-channels
  "Returns all active notification channels"
  []
  (query "SELECT * FROM notification_channels WHERE active = true ORDER BY name"))

(defn get-user-subscribed-categories
  "Returns the categories that the user is subscribed to"
  [user-id]
  (query "SELECT c.* FROM categories c 
          JOIN user_category_subscriptions ucs ON c.id = ucs.category_id 
          WHERE ucs.user_id = ? AND c.active = true
          ORDER BY c.name" user-id))

(defn get-user-preferred-channels
  "Returns the user's preferred channels"
  [user-id]
  (query "SELECT nc.* FROM notification_channels nc 
          JOIN user_channel_preferences ucp ON nc.id = ucp.channel_id 
          WHERE ucp.user_id = ? AND ucp.enabled = true AND nc.active = true
          ORDER BY nc.name" user-id))

(defn get-users-for-category-and-channel
  "Returns users subscribed to a specific category and who prefer a specific channel"
  [category-name channel-name]
  (query "SELECT DISTINCT u.* FROM users u
          JOIN user_category_subscriptions ucs ON u.id = ucs.user_id
          JOIN categories c ON ucs.category_id = c.id
          JOIN user_channel_preferences ucp ON u.id = ucp.user_id
          JOIN notification_channels nc ON ucp.channel_id = nc.id
          WHERE c.name = ? AND nc.name = ? 
          AND c.active = true AND nc.active = true AND ucp.enabled = true"
         category-name channel-name))

(defn init-and-seed!
  "Initializes the database and executes seeders"
  []
  (init-db!)
  (migrate!)
  (seed-all!))
