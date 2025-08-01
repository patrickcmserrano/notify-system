(ns notify-system.logging-regression-test
  "Critical regression tests to prevent null data in UI display"
  (:require [clojure.test :refer [deftest is testing]]
            [notify-system.logging :as logging]))

;; ================================================================================
;; CRITICAL REGRESSION TEST FOR NULL DATA FIX
;; This test prevents the specific issue where user data appeared as "()" 
;; and categories were empty in the UI log display.
;; ================================================================================

(deftest test-prevent-null-user-display-regression
  "REGRESSION TEST: Prevent the exact scenario that caused null user data in UI"
  (testing "Prevent regression where user data appears as empty parentheses () in UI"
    (let [;; This is the EXACT data structure that was causing nulls in production
          problematic-entry {:notifications/id #uuid "71a867ce-e6f3-415e-9bee-1f782d2594b3"
                            :notifications/user_id #uuid "1a921f81-1bf8-41de-b703-49a296a35a67"
                            :notifications/channel "Push"
                            :notifications/status "sent"
                            :notifications/content "Notification sent via Push for Finance"
                            :notifications/created_at #inst "2025-08-01T02:49:38.020141000-00:00"
                            :notifications/sent_at #inst "2025-08-01T02:49:38.018381000-00:00"
                            :notifications/error_message nil
                            :notifications/metadata (doto (org.postgresql.util.PGobject.)
                                                      (.setType "jsonb")
                                                      (.setValue "{\"user-name\": \"Jane Smith\", \"user-email\": \"jane.smith@example.com\", \"user-phone\": \"+1234567891\", \"delivery-method\": \"Push\", \"notification-type\": \"Finance\"}"))
                            ;; CRITICAL: These field names come from the database JOIN query
                            :users/user_name "Jane Smith"
                            :users/user_email "jane.smith@example.com"
                            :users/user_phone "+1234567891"
                            :categories/category_name "Finance"}
          
          formatted (logging/format-log-entry problematic-entry)]
      
      (testing "User information is correctly extracted (no more empty parentheses)"
        (is (= "Jane Smith" (get-in formatted [:user :name]))
            "User name must be 'Jane Smith', not null")
        (is (= "jane.smith@example.com" (get-in formatted [:user :email])))
        (is (= "+1234567891" (get-in formatted [:user :phone]))))
      
      (testing "Category is correctly extracted (no more empty categories)"
        (is (= "Finance" (:category formatted))
            "Category must be 'Finance', not null or empty")
        (is (= "Push" (:channel formatted))))
      
      (testing "UI will NOT display empty parentheses or empty strings"
        ;; These are the specific UI display issues that were happening
        (is (not= "()" (str "(" (get-in formatted [:user :name]) ")"))
            "User name display must not result in empty parentheses")
        (is (not= "" (:category formatted))
            "Category must not be empty string")
        (is (seq (get-in formatted [:user :name]))
            "User name must have content")
        (is (seq (:category formatted))
            "Category must have content")))))

(deftest test-field-mapping-consistency-regression
  "REGRESSION TEST: Ensure database field mapping uses correct namespaced keys"
  (testing "Database field mapping prevents wrong key access that caused nulls"
    (let [sample-db-result {:notifications/id #uuid "550e8400-e29b-41d4-a716-446655440000"
                           :notifications/user_id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                           :notifications/channel "Email"
                           :notifications/status "sent"
                           :notifications/content "Test content"
                           :notifications/created_at (java.time.Instant/now)
                           :notifications/sent_at (java.time.Instant/now)
                           :notifications/error_message "Test error"
                           :notifications/metadata "{\"test\": \"value\"}"
                           ;; CRITICAL: These are the correct namespaced keys from DB
                           :users/user_name "Test User"
                           :users/user_email "test@example.com"
                           :users/user_phone "+1111111111"
                           :categories/category_name "Finance"}
          
          formatted (logging/format-log-entry sample-db-result)]
      
      ;; CRITICAL: Test all field mappings use the correct namespaced keys
      (is (= (:users/user_name sample-db-result) (get-in formatted [:user :name]))
          "CRITICAL: User name must use :users/user_name NOT :user_name")
      (is (= (:users/user_email sample-db-result) (get-in formatted [:user :email]))
          "CRITICAL: User email must use :users/user_email NOT :user_email")
      (is (= (:users/user_phone sample-db-result) (get-in formatted [:user :phone]))
          "CRITICAL: User phone must use :users/user_phone NOT :user_phone")
      (is (= (:categories/category_name sample-db-result) (:category formatted))
          "CRITICAL: Category must use :categories/category_name NOT :category_name"))))
