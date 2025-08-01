(ns notify-system.test-helpers
  (:require [next.jdbc :as jdbc]
            [hikari-cp.core :as hikari]
            [cheshire.core :as json]))

(def test-db-config
  {:adapter         "postgresql"
   :server-name     (or (System/getenv "TEST_DB_HOST") "localhost")
   :port-number     (Integer/parseInt (or (System/getenv "TEST_DB_PORT") "5433"))
   :database-name   (or (System/getenv "TEST_DB_NAME") "notifications")
   :username        (or (System/getenv "TEST_DB_USER") "notify_user")
   :password        (or (System/getenv "TEST_DB_PASSWORD") "notify_pass")
   :maximum-pool-size 5
   :minimum-idle      1})

(def test-datasource-options
  {:jdbc-url (str "jdbc:postgresql://"
                  (:server-name test-db-config) ":"
                  (:port-number test-db-config) "/"
                  (:database-name test-db-config))
   :username (:username test-db-config)
   :password (:password test-db-config)
   :maximum-pool-size (:maximum-pool-size test-db-config)
   :minimum-idle (:minimum-idle test-db-config)})

(defonce test-datasource (atom nil))

(defn setup-test-db! 
  "Configura o banco de dados para testes"
  []
  (println "[DB-SETUP] Initializing test database connection")
  (when-not @test-datasource
    (println (format "[DB-SETUP] Creating datasource with config: %s" 
                     (select-keys test-datasource-options [:jdbc-url :username :maximum-pool-size])))
    (reset! test-datasource (hikari/make-datasource test-datasource-options))
    (println "[DB-SETUP] Test database connection established")))

(defn teardown-test-db! 
  "Cleans up test database after tests"
  []
  (println "[DB-TEARDOWN] Initiating test database cleanup")
  (when @test-datasource
    (println "[DB-TEARDOWN] Closing datasource connection")
    (hikari-cp.core/close-datasource @test-datasource)
    (reset! test-datasource nil)
    (println "[DB-TEARDOWN] Test database cleanup completed")))

(defn get-test-connection 
  "Returns a test database connection"
  []
  (when-not @test-datasource
    (println "[DB-CONNECTION] Test datasource not found, initializing")
    (setup-test-db!))
  (println "[DB-CONNECTION] Returning test database connection")
  @test-datasource)

(defn clean-tables! 
  "Cleans all tables for isolated testing"
  []
  (println "[DB-CLEAN] Starting table cleanup for test isolation")
  (let [conn (get-test-connection)]
    (println "[DB-CLEAN] Cleaning notification_preferences table")
    (jdbc/execute! conn ["DELETE FROM notification_preferences"])
    (println "[DB-CLEAN] Cleaning notifications table")
    (jdbc/execute! conn ["DELETE FROM notifications"])
    (println "[DB-CLEAN] Cleaning notification_templates table")
    (jdbc/execute! conn ["DELETE FROM notification_templates"])
    (println "[DB-CLEAN] Cleaning users table")
    (jdbc/execute! conn ["DELETE FROM users"])
    (println "[DB-CLEAN] Table cleanup completed")))

(defn insert-test-user! 
  "Inserts a test user into the database with comprehensive logging"
  ([email] (insert-test-user! email {}))
  ([email attrs]
   (println (format "[DB-INSERT] Inserting test user - Email: %s, Attributes: %s" email attrs))
   (let [conn (get-test-connection)
         user-data (merge {:email email
                          :name (str "Test User " email)
                          :preferences {}}
                         attrs)]
     (println (format "[DB-INSERT] Complete user data: %s" user-data))
     (let [result (jdbc/execute-one! conn 
                    ["INSERT INTO users (email, name, preferences) VALUES (?, ?, ?::jsonb) RETURNING *"
                     (:email user-data)
                     (:name user-data)
                     (cheshire.core/generate-string (:preferences user-data))])]
       (println (format "[DB-INSERT] User insertion result: %s" result))
       result))))

(defn insert-test-template!
  "Insere um template de teste"
  ([name] (insert-test-template! name {}))
  ([name attrs]
   (let [conn (get-test-connection)
         template-data (merge {:name name
                              :subject "Test Subject"
                              :body "Test Body"
                              :template_type "email"
                              :variables []}
                             attrs)]
     (jdbc/execute-one! conn
       ["INSERT INTO notification_templates (name, subject, body, template_type, variables) VALUES (?, ?, ?, ?, ?::jsonb) RETURNING *"
        (:name template-data)
        (:subject template-data)
        (:body template-data)
        (:template_type template-data)
        (cheshire.core/generate-string (:variables template-data))]))))

(defmacro with-test-db [& body]
  "Macro for executing tests with automatic database cleanup and logging"
  `(do
     (println "[TEST-DB-MACRO] Starting test with database isolation")
     (setup-test-db!)
     (clean-tables!)
     (try
       (let [start-time# (System/currentTimeMillis)
             result# (do ~@body)
             end-time# (System/currentTimeMillis)]
         (println (format "[TEST-DB-MACRO] Test body executed in %d ms" (- end-time# start-time#)))
         result#)
       (finally
         (println "[TEST-DB-MACRO] Cleaning up test database")
         (clean-tables!)
         (println "[TEST-DB-MACRO] Test database cleanup completed")))))

(defn seed-test-data!
  "Seed test data for comprehensive testing"
  []
  (println "[SEED-TEST-DATA] Starting test data seeding")
  (try
    (let [conn (get-test-connection)]
      ;; Seed categories
      (doseq [category ["Sports" "Finance" "Movies"]]
        (jdbc/execute! conn
          ["INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
           category (str category " notifications")]))
      
      ;; Seed channels
      (doseq [channel ["SMS" "Email" "Push"]]
        (jdbc/execute! conn
          ["INSERT INTO notification_channels (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
           channel (str channel " notifications")]))
      
      ;; Seed test users
      (doseq [[email name phone] [["test1@example.com" "Test User 1" "+1111111111"]
                                  ["test2@example.com" "Test User 2" "+2222222222"]]]
        (jdbc/execute! conn
          ["INSERT INTO users (email, name, phone) VALUES (?, ?, ?) ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, phone = EXCLUDED.phone"
           email name phone]))
      
      (println "[SEED-TEST-DATA] Test data seeded successfully"))
    (catch Exception e
      (println "[SEED-TEST-DATA] Error seeding test data:" (.getMessage e))
      (throw e))))

(defn count-records 
  "Counts records in a specified database table"
  [table]
  (println (format "[DB-COUNT] Counting records in table: %s" table))
  (let [conn (get-test-connection)
        result (jdbc/execute-one! conn [(str "SELECT COUNT(*) as count FROM " (name table))])
        count (:count result)]
    (println (format "[DB-COUNT] Record count for %s: %d" table count))
    count))
