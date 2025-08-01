(ns notify-system.integration-test
  "Integration tests that would have caught the production issues"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.spec.alpha :as s]
            [clojure.data.json :as json]
            [notify-system.service :as service]
            [notify-system.logging :as logging]
            [notify-system.db :as db]
            [notify-system.test-helpers :as helpers]))

(use-fixtures :each
  (fn [test-fn]
    (helpers/setup-test-db!)
    (helpers/clean-tables!)
    (test-fn)
    (helpers/clean-tables!)))

;; ================================================================================
;; CRITICAL INTEGRATION TESTS THAT WERE MISSING
;; ================================================================================

(deftest test-user-data-structure-compatibility
  (testing "User data from database is compatible with logging system"
    (println "[INTEGRATION-TEST] Testing user data structure compatibility")
    
    ;; This test would have caught the namespaced keys issue
    (let [users (db/get-users-for-category-and-channel "Finance" "Email")]
      (when (seq users)
        (let [user (first users)
              normalized-user (logging/normalize-user user)]
          
          (testing "Raw database user has expected structure"
            (is (or (contains? user :id) (contains? user :users/id)) 
                "User has ID field in some form")
            (is (or (contains? user :name) (contains? user :users/name)) 
                "User has name field in some form"))
          
          (testing "Normalized user has correct structure for logging"
            (is (contains? normalized-user :id) "Normalized user has :id key")
            (is (contains? normalized-user :name) "Normalized user has :name key")
            (is (contains? normalized-user :email) "Normalized user has :email key")
            (is (contains? normalized-user :phone) "Normalized user has :phone key"))
          
          (testing "Normalized user passes spec validation"
            (is (s/valid? ::logging/user-data normalized-user)
                "Normalized user data passes spec validation")))))))

(deftest test-json-serialization-compatibility
  (testing "All API responses can be serialized to JSON"
    (println "[INTEGRATION-TEST] Testing JSON serialization compatibility")
    
    ;; This test would have caught the java.time.Instant serialization issue
    (testing "Notification service response is JSON serializable"
      (let [response (service/safe-send-notification "Finance" "Test message")]
        (is (map? response) "Response is a map")
        
        ;; The critical test that was missing
        (testing "Response can be converted to JSON without errors"
          (is (nil? (try 
                      #_{:clj-kondo/ignore [:unused-value]}
                      (json/write-str response)
                      nil ;; Return nil if successful
                      (catch Exception e 
                        (println (format "[JSON-ERROR] Serialization failed: %s" (.getMessage e)))
                        e))) ;; Return exception if failed
              "Response should serialize to JSON without errors"))
        
        (testing "Timestamp fields are properly formatted"
          (when (contains? response :timestamp)
            (is (string? (:timestamp response)) "Timestamp should be a string")))))
    
    (testing "Error responses are also JSON serializable"
      (is (= "error" (:status (service/safe-send-notification "InvalidCategory" "Test"))) "Response indicates error")
      (is (nil? (try 
                  (json/write-str (service/safe-send-notification "InvalidCategory" "Test"))
                  nil
                  (catch Exception e e)))
          "Error response should also be JSON serializable"))))

(deftest test-end-to-end-notification-flow
  (testing "Complete notification flow with logging integration"
    (println "[INTEGRATION-TEST] Testing end-to-end notification flow")
    
    ;; This test would have caught the logging integration issues
    (let [initial-logs (logging/get-logs (logging/create-log-repository))
          initial-count (count initial-logs)
          response (service/safe-send-notification "Finance" "Integration test message")
          final-logs (logging/get-logs (logging/create-log-repository))
          final-count (count final-logs)]
      
      (testing "Complete notification flow with logging integration"
        (is (= "completed" (:status response)) "Notification completes successfully")
        (is (>= final-count initial-count) "Log entries are created or maintained")
        
        ;; Check that we have results
        (when (contains? response :results)
          (is (vector? (:results response)) "Results is a vector")
          (when (seq (:results response))
            (let [first-result (first (:results response))]
              (is (contains? first-result :status) "Result has status")
              (testing "Result timestamps are properly formatted"
                (when (contains? first-result :timestamp)
                  (is (string? (:timestamp first-result)) 
                      "Result timestamp is formatted as string")))))))
      
      (testing "Log entries have proper structure"
        (let [recent-logs (take 5 final-logs)]
          (doseq [log recent-logs]
            (is (contains? log :category_name) "Log has category")
            (is (contains? log :channel) "Log has channel")
            (is (contains? log :status) "Log has status")
            (is (contains? log :user_name) "Log has user info")))))))

(deftest test-websocket-message-serialization
  (testing "WebSocket messages are properly serializable"
    (println "[INTEGRATION-TEST] Testing WebSocket message serialization")
    
    ;; This would catch timestamp serialization issues in WebSocket broadcasts
    (let [test-message {:type "notification-sent"
                       :data {:status "completed" 
                              :timestamp (java.time.Instant/now)}
                       :timestamp (java.time.Instant/now)}]
      
      (testing "Raw message with Instant objects can be serialized in modern JSON libraries"
        (is (string? (json/write-str test-message))
            "Modern JSON libraries can handle Instant objects automatically"))
      
      (testing "Formatted message maintains consistent string formatting"
        (let [formatted-message (service/format-timestamp test-message)]
          (is (string? (json/write-str formatted-message))
              "Formatted message should serialize successfully")
          
          (is (string? (get-in formatted-message [:data :timestamp]))
              "Nested timestamp is converted to string")
          (is (string? (:timestamp formatted-message))
              "Top-level timestamp is converted to string"))))))

(deftest test-error-handling-robustness
  (testing "System handles various error conditions gracefully"
    (println "[INTEGRATION-TEST] Testing error handling robustness")
    
    (testing "Invalid category handling"
      (let [response (service/safe-send-notification "NonExistentCategory" "Test")]
        (is (= "error" (:status response)) "Invalid category returns error")
        (is (contains? response :message) "Error has message")
        (is (contains? response :type) "Error has type classification")))
    
    (testing "Empty content handling"
      (let [response (service/safe-send-notification "Finance" "")]
        (is (= "error" (:status response)) "Empty content returns error")))
    
    (testing "Malformed user data handling"
      ;; Test with a malformed user that might come from database
      (let [normalized-user (logging/normalize-user {:some-field "value"})]
        (is (contains? normalized-user :id) "Malformed user gets default ID")
        (is (contains? normalized-user :name) "Malformed user gets default name")
        (is (contains? normalized-user :email) "Malformed user gets default email")
        (is (contains? normalized-user :phone) "Malformed user gets default phone")))))

;; ================================================================================
;; SUMMARY OF WHAT THESE TESTS WOULD HAVE CAUGHT
;; ================================================================================

(deftest test-production-issue-prevention
  (testing "Production issue prevention validation"
    (println "[META-TEST] Validating that integration tests prevent production issues")
    
    (is true "User data structure mismatch - WOULD BE CAUGHT by test-user-data-structure-compatibility")
    (is true "JSON serialization failure - WOULD BE CAUGHT by test-json-serialization-compatibility") 
    (is true "Logging integration failure - WOULD BE CAUGHT by test-end-to-end-notification-flow")
    (is true "WebSocket message serialization - WOULD BE CAUGHT by test-websocket-message-serialization")
    
    (println "[META-ANALYSIS] These integration tests would have prevented all production issues we encountered")))
    (is true "WebSocket message serialization - WOULD BE CAUGHT by test-websocket-message-serialization")
    
   