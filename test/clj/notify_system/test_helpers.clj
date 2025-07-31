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
  (when-not @test-datasource
    (reset! test-datasource (hikari/make-datasource test-datasource-options))))

(defn teardown-test-db! []
  "Limpa o banco de dados após os testes"
  (when @test-datasource
    (hikari-cp.core/close-datasource @test-datasource)
    (reset! test-datasource nil)))

(defn get-test-connection []
  "Retorna uma conexão para testes"
  (when-not @test-datasource
    (setup-test-db!))
  @test-datasource)

(defn clean-tables! []
  "Limpa todas as tabelas para testes isolados"
  (let [conn (get-test-connection)]
    (jdbc/execute! conn ["DELETE FROM notification_preferences"])
    (jdbc/execute! conn ["DELETE FROM notifications"])  
    (jdbc/execute! conn ["DELETE FROM notification_templates"])
    (jdbc/execute! conn ["DELETE FROM users"])))

(defn insert-test-user! 
  "Insere um usuário de teste"
  ([email] (insert-test-user! email {}))
  ([email attrs]
   (let [conn (get-test-connection)
         user-data (merge {:email email
                          :name (str "Test User " email)
                          :preferences {}}
                         attrs)]
     (jdbc/execute-one! conn 
       ["INSERT INTO users (email, name, preferences) VALUES (?, ?, ?::jsonb) RETURNING *"
        (:email user-data)
        (:name user-data)
        (cheshire.core/generate-string (:preferences user-data))]))))

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
  "Macro para executar testes com limpeza automática do banco"
  `(do
     (setup-test-db!)
     (clean-tables!)
     (try
       ~@body
       (finally
         (clean-tables!)))))

(defn count-records [table]
  "Conta registros em uma tabela"
  (let [conn (get-test-connection)
        result (jdbc/execute-one! conn [(str "SELECT COUNT(*) as count FROM " (name table))])]
    (:count result)))
