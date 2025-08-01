(ns notify-system.logging-test
  "Comprehensive tests for the logging system"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.data.json :as json]
            [next.jdbc :as jdbc]
            [notify-system.logging :as logging]
            [notify-system.test-helpers :as helpers]))

(use-fixtures :each
  (fn [test-fn]
    (helpers/setup-test-db!)
    (helpers/clean-tables!)
    (test-fn)
    (helpers/clean-tables!)))

;; ================================================================================
;; FORMAT LOG ENTRY TESTS
;; ================================================================================

(deftest test-format-log-entry-with-namespaced-keys
  "Tests that format-log-entry correctly handles database results with namespaced keys"
  (testing "format-log-entry handles namespaced database keys correctly"
    (let [raw-db-entry {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                        :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                        :notifications/channel "Email"
                        :notifications/status "sent"
                        :notifications/content "Test notification content"
                        :notifications/created_at (java.time.Instant/now)
                        :notifications/sent_at (java.time.Instant/now)
                        :notifications/error_message nil
                        :notifications/metadata "{\"test\": \"metadata\"}"
                        :users/user_name "John Doe"
                        :users/user_email "john.doe@example.com"
                        :users/user_phone "+1234567890"
                        :categories/category_name "Finance"}
          
          formatted-entry (logging/format-log-entry raw-db-entry)]
      
      (testing "All fields are properly extracted and not null"
        (is (= (:notifications/id raw-db-entry) (:id formatted-entry)))
        (is (= (:notifications/user_id raw-db-entry) (get-in formatted-entry [:user :id])))
        (is (= (:users/user_name raw-db-entry) (get-in formatted-entry [:user :name])))
        (is (= (:users/user_email raw-db-entry) (get-in formatted-entry [:user :email])))
        (is (= (:users/user_phone raw-db-entry) (get-in formatted-entry [:user :phone])))
        (is (= (:categories/category_name raw-db-entry) (:category formatted-entry)))
        (is (= (:notifications/channel raw-db-entry) (:channel formatted-entry)))
        (is (= (:notifications/status raw-db-entry) (:status formatted-entry))))
      
      (testing "No critical fields are null"
        (is (not (nil? (:id formatted-entry))))
        (is (not (nil? (get-in formatted-entry [:user :name]))))  
        (is (not (nil? (:category formatted-entry))))
        (is (not (nil? (:channel formatted-entry))))))))

(deftest test-format-log-entry-with-postgresql-metadata
  "Tests format-log-entry with PostgreSQL JSONB objects"
  (testing "format-log-entry handles PostgreSQL PGobject metadata"
    (let [pg-object (doto (org.postgresql.util.PGobject.)
                      (.setType "jsonb")
                      (.setValue "{\"delivery-method\": \"Email\", \"notification-type\": \"Finance\"}"))
          
          raw-db-entry {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                        :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                        :notifications/channel "Email"
                        :notifications/status "sent"
                        :notifications/content "Test notification"
                        :notifications/created_at (java.time.Instant/now)
                        :notifications/sent_at (java.time.Instant/now)
                        :notifications/error_message nil
                        :notifications/metadata pg-object
                        :users/user_name "Jane Smith"
                        :users/user_email "jane.smith@example.com"
                        :users/user_phone "+1987654321"
                        :categories/category_name "Finance"}
          
          formatted-entry (logging/format-log-entry raw-db-entry)]
      
      (testing "PostgreSQL JSONB object is properly handled"
        (is (map? (:metadata formatted-entry)))
        (is (= "Email" (get-in formatted-entry [:metadata :delivery-method])))
        (is (= "Finance" (get-in formatted-entry [:metadata :notification-type])))))))

(deftest test-format-logs-collection
  "Tests that format-logs properly handles collections"
  (testing "format-logs handles multiple entries correctly"
    (let [raw-entries [{:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                        :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                        :notifications/channel "Email"
                        :notifications/status "sent"
                        :notifications/content "First notification"
                        :notifications/created_at (java.time.Instant/now)
                        :notifications/sent_at (java.time.Instant/now)
                        :notifications/error_message nil
                        :notifications/metadata "{\"test\": \"first\"}"
                        :users/user_name "John Doe"
                        :users/user_email "john.doe@example.com"
                        :users/user_phone "+1234567890"
                        :categories/category_name "Finance"}
                       
                       {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440001"
                        :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c9"
                        :notifications/channel "SMS"
                        :notifications/status "delivered"
                        :notifications/content "Second notification"
                        :notifications/created_at (java.time.Instant/now)
                        :notifications/sent_at (java.time.Instant/now)
                        :notifications/error_message nil
                        :notifications/metadata "{\"test\": \"second\"}"
                        :users/user_name "Jane Smith"
                        :users/user_email "jane.smith@example.com"
                        :users/user_phone "+1987654321"
                        :categories/category_name "Sports"}]
          
          formatted-logs (logging/format-logs raw-entries)]
      
      (testing "All entries are formatted correctly"
        (is (= 2 (count formatted-logs)))
        (is (vector? formatted-logs))
        
        (let [first-entry (first formatted-logs)]
          (is (= "John Doe" (get-in first-entry [:user :name])))
          (is (= "Finance" (:category first-entry)))
          (is (= "Email" (:channel first-entry))))
        
        (let [second-entry (second formatted-logs)]
          (is (= "Jane Smith" (get-in second-entry [:user :name])))
          (is (= "Sports" (:category second-entry)))
          (is (= "SMS" (:channel second-entry))))))))

;; ================================================================================
;; INTEGRATION TESTS
;; ================================================================================

(deftest test-database-integration
  "Tests the full database integration flow"
  (testing "Database queries return properly structured data"
    (helpers/seed-test-data!)
    
    (let [repo (logging/create-log-repository)
          logging-service (logging/create-logging-service repo)
          ;; Get a real user from the database instead of using hardcoded UUID
          conn (helpers/get-test-connection)
          db-user (first (jdbc/execute! conn ["SELECT * FROM users WHERE email = ?" "test1@example.com"]))
          test-user {:id (:users/id db-user)
                     :name (:users/name db-user)
                     :email (:users/email db-user)
                     :phone (:users/phone db-user)}]
      
      (logging/log-notification logging-service test-user "Finance" "Email" "sent")
      
      (let [raw-logs (logging/get-logs repo)]
        (testing "Database returns data with expected structure"
          (is (pos? (count raw-logs)))
          
          (when (seq raw-logs)
            (let [first-log (first raw-logs)]
              (is (contains? first-log :notifications/id))
              (is (contains? first-log :users/user_name))
              (is (contains? first-log :categories/category_name))
              
              (is (not (nil? (:notifications/id first-log))))
              (is (not (nil? (:users/user_name first-log))))
              (is (not (nil? (:categories/category_name first-log)))))))))))

(deftest test-json-serialization
  "Tests that formatted logs serialize to JSON properly"
  (testing "JSON serialization preserves critical data"
    (let [sample-formatted-logs [{:id #uuid "550e8400-e29b-41d4-a716-446655440000"
                                  :user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                         :name "John Doe"
                                         :email "john.doe@example.com"
                                         :phone "+1234567890"}
                                  :category "Finance"
                                  :channel "Email"
                                  :status "sent"
                                  :content "Test notification"
                                  :timestamp (java.time.Instant/now)
                                  :sent-at (java.time.Instant/now)
                                  :error-message nil
                                  :metadata {:test "value"}}]]
      
      (testing "JSON serialization works without errors"
        (is (string? (json/write-str sample-formatted-logs))))
      
      (testing "JSON round-trip preserves critical data"
        (let [json-str (json/write-str sample-formatted-logs)
              parsed-back (json/read-str json-str :key-fn keyword)
              first-log (first parsed-back)]
          (is (not (nil? (:id first-log))))
          (is (not (nil? (get-in first-log [:user :name]))))
          (is (not (nil? (:category first-log))))
          (is (seq (get-in first-log [:user :name])))
          (is (seq (:category first-log))))))))

;; ================================================================================
;; ERROR HANDLING TESTS
;; ================================================================================

(deftest test-error-handling
  "Tests that format-log-entry handles edge cases gracefully"
  (testing "format-log-entry handles malformed data"
    (let [minimal-entry {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                         :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                         :notifications/channel "Email"
                         :notifications/status "sent"
                         :notifications/content "Test"
                         :notifications/created_at (java.time.Instant/now)
                         :users/user_name "Test User"
                         :users/user_email "test@example.com"
                         :users/user_phone "+1111111111"
                         :categories/category_name "Finance"}
          formatted (logging/format-log-entry minimal-entry)]
      
      (is (not (nil? (:id formatted)))))
    
    (let [bad-metadata-entry {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                              :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                              :notifications/channel "Email"
                              :notifications/status "sent"
                              :notifications/content "Test"
                              :notifications/created_at (java.time.Instant/now)
                              :notifications/metadata "invalid-json{"
                              :users/user_name "Test User"
                              :users/user_email "test@example.com"
                              :users/user_phone "+1111111111"
                              :categories/category_name "Finance"}
          formatted (logging/format-log-entry bad-metadata-entry)]
      
      (testing "Handles malformed metadata gracefully"
        (is (map? (:metadata formatted)))
        (is (empty? (:metadata formatted)))))))
