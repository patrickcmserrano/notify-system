(ns notify-system.service-test
  "Comprehensive tests for the notification service layer"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.spec.alpha :as s]
            [notify-system.service :as service]
            [notify-system.db :as db]
            [notify-system.test-helpers :as helpers]))

(use-fixtures :each
  (fn [test-fn]
    (println (format "[TEST-SETUP] %s - Initializing test database and cleaning tables" 
                     (java.time.Instant/now)))
    (helpers/setup-test-db!)
    (helpers/clean-tables!)
    (println "[TEST-SETUP] Database preparation completed")
    (test-fn)
    (println "[TEST-CLEANUP] Cleaning up test data")
    (helpers/clean-tables!)
    (println "[TEST-CLEANUP] Test cleanup completed")))

(deftest test-notification-service-creation
  (testing "Can create notification service instances"
    (println "[TEST-START] test-notification-service-creation - Testing service instantiation")
    (let [start-time (System/currentTimeMillis)
          service (service/create-notification-service)
          end-time (System/currentTimeMillis)]
      (println (format "[SERVICE-CREATION] Service instance created in %d ms" (- end-time start-time)))
      (println (format "[SERVICE-VALIDATION] Service type: %s" (type service)))
      (is (not (nil? service)))
      (is (satisfies? service/NotificationService service))
      (println "[TEST-SUCCESS] Service creation validation completed successfully"))))

(deftest test-message-validation
  (testing "Message validation works correctly"
    (println "[TEST-START] test-message-validation - Testing message validation logic")
    (testing "Valid messages pass validation"
      (let [valid-message {:category "Sports" :content "Test message"}]
        (println (format "[VALIDATION-TEST] Testing valid message: %s" valid-message))
        (let [validation-result (s/valid? ::service/message valid-message)]
          (println (format "[VALIDATION-RESULT] Valid message result: %s" validation-result))
          (is validation-result))))
    
    (testing "Invalid messages fail validation"
      (let [test-cases [{:msg {:category "InvalidCategory" :content "Test"} 
                        :desc "invalid category"}
                       {:msg {:category "Sports" :content ""} 
                        :desc "empty content"}
                       {:msg {:content "Test"} 
                        :desc "missing category"}]]
        (doseq [{:keys [msg desc]} test-cases]
          (println (format "[VALIDATION-TEST] Testing invalid message - %s: %s" desc msg))
          (let [validation-result (s/valid? ::service/message msg)]
            (println (format "[VALIDATION-RESULT] Invalid message result for %s: %s" desc validation-result))
            (is (not validation-result) (format "Message with %s should fail validation" desc))))))
    (println "[TEST-SUCCESS] Message validation tests completed")))

(deftest test-safe-send-notification
  (testing "Safe notification sending with error handling"
    (println "[TEST-START] test-safe-send-notification - Testing safe notification delivery")
    (testing "Valid category and content succeed"
      (let [category "Sports"
            content "Test notification"
            start-time (System/currentTimeMillis)]
        (println (format "[NOTIFICATION-SEND] Attempting to send notification - Category: %s, Content: %s, Timestamp: %s" 
                         category content (java.time.Instant/now)))
        (let [result (service/safe-send-notification category content)
              end-time (System/currentTimeMillis)]
          (println (format "[NOTIFICATION-RESULT] Send operation completed in %d ms" (- end-time start-time)))
          (println (format "[NOTIFICATION-STATUS] Result status: %s" (:status result)))
          (println (format "[NOTIFICATION-SUMMARY] Summary data: %s" (:summary result)))
          (is (= "completed" (:status result)))
          (is (contains? result :summary)))))
    
    (testing "Invalid category returns error"
      (let [category "InvalidCategory"
            content "Test"]
        (println (format "[NOTIFICATION-ERROR-TEST] Testing invalid category - Category: %s, Content: %s" category content))
        (let [result (service/safe-send-notification category content)]
          (println (format "[NOTIFICATION-ERROR-RESULT] Error result: %s" result))
          (is (= "error" (:status result)))
          (is (= :validation-error (:type result))))))
    
    (testing "Empty content returns error"
      (let [category "Sports"
            content ""]
        (println (format "[NOTIFICATION-ERROR-TEST] Testing empty content - Category: %s, Content: '%s'" category content))
        (let [result (service/safe-send-notification category content)]
          (println (format "[NOTIFICATION-ERROR-RESULT] Empty content error result: %s" result))
          (is (= "error" (:status result)))
          (is (= :validation-error (:type result))))))
    (println "[TEST-SUCCESS] Safe notification sending tests completed")))

(deftest test-send-notification-facade
  (testing "Send notification facade function"
    (println "[TEST-START] test-send-notification-facade - Testing notification facade")
    (let [category "Sports"
          content "Game tonight!"
          start-time (System/currentTimeMillis)]
      (println (format "[FACADE-CALL] Calling send-notification facade - Category: %s, Content: %s, Timestamp: %s" 
                       category content (java.time.Instant/now)))
      (let [result (service/send-notification category content)
            end-time (System/currentTimeMillis)]
        (println (format "[FACADE-RESULT] Facade call completed in %d ms" (- end-time start-time)))
        (println (format "[FACADE-STATUS] Result status: %s" (:status result)))
        (println (format "[FACADE-RESULTS] Results data: %s" (:results result)))
        (println (format "[FACADE-SUMMARY] Summary: %s" (:summary result)))
        (is (= "completed" (:status result)))
        (is (contains? result :results))
        (is (contains? result :summary))
        (is (>= (:total-attempts (:summary result)) 0))
        (println "[TEST-SUCCESS] Notification facade test completed")))))

(deftest test-notification-history
  (testing "Can retrieve notification history"
    (println "[TEST-START] test-notification-history - Testing history retrieval")
    (let [start-time (System/currentTimeMillis)
          history (service/get-notification-history)
          end-time (System/currentTimeMillis)]
      (println (format "[HISTORY-RETRIEVAL] History retrieved in %d ms" (- end-time start-time)))
      (println (format "[HISTORY-DATA] History type: %s, Count: %d" (type history) (count history)))
      (println (format "[HISTORY-CONTENT] History entries: %s" (take 3 history)))
      (is (vector? history))
      ;; History might be empty on first run
      (is (>= (count history) 0))
      (println "[TEST-SUCCESS] Notification history test completed"))))

(deftest test-system-stats
  (testing "Can retrieve system statistics"
    (println "[TEST-START] test-system-stats - Testing system statistics retrieval")
    (let [start-time (System/currentTimeMillis)
          stats (service/get-system-stats)
          end-time (System/currentTimeMillis)]
      (println (format "[STATS-RETRIEVAL] Statistics retrieved in %d ms" (- end-time start-time)))
      (println (format "[STATS-DATA] Complete statistics: %s" stats))
      (println (format "[STATS-BREAKDOWN] Total notifications: %s, Successful: %s, Failed: %s" 
                       (:total-notifications stats)
                       (:successful-notifications stats)
                       (:failed-notifications stats)))
      (println (format "[STATS-CHANNELS] Channels used: %s" (:channels-used stats)))
      (println (format "[STATS-CATEGORIES] Categories used: %s" (:categories-used stats)))
      (is (contains? stats :total-notifications))
      (is (contains? stats :successful-notifications))
      (is (contains? stats :failed-notifications))
      (is (contains? stats :channels-used))
      (is (contains? stats :categories-used))
      (is (number? (:total-notifications stats)))
      (println "[TEST-SUCCESS] System statistics test completed"))))

(deftest test-service-with-database-integration
  (testing "Service integrates properly with database"
    (println "[TEST-START] test-service-with-database-integration - Testing database integration")
    (helpers/with-test-db
      ;; Insert test data for more realistic testing
      (println "[DB-SETUP] Inserting test users and categories")
      (let [user1 (helpers/insert-test-user! "sports@example.com" 
                    {:name "Sports Fan" :preferences {:categories ["Sports"] :channels ["Email"]}})
            user2 (helpers/insert-test-user! "finance@example.com"
                    {:name "Finance User" :preferences {:categories ["Finance"] :channels ["SMS"]}})]
        (println (format "[DB-USER-INSERT] Sports user inserted: %s" user1))
        (println (format "[DB-USER-INSERT] Finance user inserted: %s" user2)))
      
      ;; Setup categories and preferences in the database
      (println "[DB-SETUP] Setting up categories in database")
      (db/execute! "INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                   "Sports" "Sports notifications")
      (db/execute! "INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                   "Finance" "Finance notifications")
      (println "[DB-SETUP] Categories setup completed")
      
      ;; Test sending notification
      (let [category "Sports"
            content "Big game tonight!"
            start-time (System/currentTimeMillis)]
        (println (format "[INTEGRATION-TEST] Sending notification with DB integration - Category: %s, Content: %s" 
                         category content))
        (let [result (service/send-notification category content)
              end-time (System/currentTimeMillis)]
          (println (format "[INTEGRATION-RESULT] Integration test completed in %d ms" (- end-time start-time)))
          (println (format "[INTEGRATION-STATUS] Result status: %s" (:status result)))
          (println (format "[INTEGRATION-SUMMARY] Result summary: %s" (:summary result)))
          (is (= "completed" (:status result)))
          (is (contains? result :summary))
          (println "[TEST-SUCCESS] Database integration test completed"))))))

(deftest test-repository-protocol
  (testing "Database repository implements required protocol"
    (println "[TEST-START] test-repository-protocol - Testing repository pattern implementation")
    (let [start-time (System/currentTimeMillis)
          repo (service/->DatabaseRepository)
          end-time (System/currentTimeMillis)]
      (println (format "[REPOSITORY-CREATION] Repository instance created in %d ms" (- end-time start-time)))
      (println (format "[REPOSITORY-TYPE] Repository type: %s" (type repo)))
      (is (satisfies? service/NotificationRepository repo))
      (println "[REPOSITORY-VALIDATION] Repository protocol compliance verified")
      (println "[TEST-SUCCESS] Repository protocol test completed"))))

(deftest test-validation-helpers
  (testing "Category validation"
    (println "[TEST-START] test-validation-helpers - Testing validation helper functions")
    (let [valid-categories ["Sports" "Finance" "Movies"]]
      (doseq [category valid-categories]
        (println (format "[CATEGORY-VALIDATION] Testing valid category: %s" category))
        (is (nil? (service/validate-category category))))
      
      (println "[CATEGORY-VALIDATION] Testing invalid category")
      (is (thrown? Exception (service/validate-category "InvalidCategory")))))
  
  (testing "Content validation"
    (println "[CONTENT-VALIDATION] Testing content validation scenarios")
    (let [valid-content "Valid content"]
      (println (format "[CONTENT-VALIDATION] Testing valid content: '%s'" valid-content))
      (is (nil? (service/validate-content valid-content))))
    
    (let [invalid-contents [["" "empty string"]
                           [nil "nil value"]
                           ["   " "whitespace only"]]]
      (doseq [[content desc] invalid-contents]
        (println (format "[CONTENT-VALIDATION] Testing invalid content - %s: '%s'" desc content))
        (is (thrown? Exception (service/validate-content content))))))
  (println "[TEST-SUCCESS] Validation helpers test completed"))
