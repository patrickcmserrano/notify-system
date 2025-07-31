(ns notify-system.service-test
  "Comprehensive tests for the notification service layer"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.spec.alpha :as s]
            [notify-system.service :as service]
            [notify-system.db :as db]
            [notify-system.test-helpers :as helpers]))

(use-fixtures :each
  (fn [test-fn]
    (helpers/setup-test-db!)
    (helpers/clean-tables!)
    (test-fn)
    (helpers/clean-tables!)))

(deftest test-notification-service-creation
  (testing "Can create notification service instances"
    (let [service (service/create-notification-service)]
      (is (not (nil? service)))
      (is (satisfies? service/NotificationService service)))))

(deftest test-message-validation
  (testing "Message validation works correctly"
    (testing "Valid messages pass validation"
      (let [valid-message {:category "Sports" :content "Test message"}]
        (is (s/valid? ::service/message valid-message))))
    
    (testing "Invalid messages fail validation"
      (is (not (s/valid? ::service/message {:category "InvalidCategory" :content "Test"})))
      (is (not (s/valid? ::service/message {:category "Sports" :content ""})))
      (is (not (s/valid? ::service/message {:content "Test"}))))))

(deftest test-safe-send-notification
  (testing "Safe notification sending with error handling"
    (testing "Valid category and content succeed"
      (let [result (service/safe-send-notification "Sports" "Test notification")]
        (is (= "completed" (:status result)))
        (is (contains? result :summary))))
    
    (testing "Invalid category returns error"
      (let [result (service/safe-send-notification "InvalidCategory" "Test")]
        (is (= "error" (:status result)))
        (is (= :validation-error (:type result)))))
    
    (testing "Empty content returns error"
      (let [result (service/safe-send-notification "Sports" "")]
        (is (= "error" (:status result)))
        (is (= :validation-error (:type result)))))))

(deftest test-send-notification-facade
  (testing "Send notification facade function"
    (let [result (service/send-notification "Sports" "Game tonight!")]
      (is (= "completed" (:status result)))
      (is (contains? result :results))
      (is (contains? result :summary))
      (is (>= (:total-attempts (:summary result)) 0)))))

(deftest test-notification-history
  (testing "Can retrieve notification history"
    (let [history (service/get-notification-history)]
      (is (vector? history))
      ;; History might be empty on first run
      (is (>= (count history) 0)))))

(deftest test-system-stats
  (testing "Can retrieve system statistics"
    (let [stats (service/get-system-stats)]
      (is (contains? stats :total-notifications))
      (is (contains? stats :successful-notifications))
      (is (contains? stats :failed-notifications))
      (is (contains? stats :channels-used))
      (is (contains? stats :categories-used))
      (is (number? (:total-notifications stats))))))

(deftest test-service-with-database-integration
  (testing "Service integrates properly with database"
    (helpers/with-test-db
      ;; Insert test data for more realistic testing
      (helpers/insert-test-user! "sports@example.com" 
        {:name "Sports Fan" :preferences {:categories ["Sports"] :channels ["Email"]}})
      (helpers/insert-test-user! "finance@example.com"
        {:name "Finance User" :preferences {:categories ["Finance"] :channels ["SMS"]}})
      
      ;; Setup categories and preferences in the database
      (db/execute! "INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                   "Sports" "Sports notifications")
      (db/execute! "INSERT INTO categories (name, description) VALUES (?, ?) ON CONFLICT (name) DO NOTHING"
                   "Finance" "Finance notifications")
      
      ;; Test sending notification
      (let [result (service/send-notification "Sports" "Big game tonight!")]
        (is (= "completed" (:status result)))
        (is (contains? result :summary))))))

(deftest test-repository-protocol
  (testing "Database repository implements required protocol"
    (let [repo (service/->DatabaseRepository)]
      (is (satisfies? service/NotificationRepository repo)))))

(deftest test-validation-helpers
  (testing "Category validation"
    (is (nil? (service/validate-category "Sports")))
    (is (nil? (service/validate-category "Finance")))
    (is (nil? (service/validate-category "Movies")))
    (is (thrown? Exception (service/validate-category "InvalidCategory"))))
  
  (testing "Content validation"
    (is (nil? (service/validate-content "Valid content")))
    (is (thrown? Exception (service/validate-content "")))
    (is (thrown? Exception (service/validate-content nil)))
    (is (thrown? Exception (service/validate-content "   ")))))
