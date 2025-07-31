(ns notify-system.users-test
  "Comprehensive tests for user management"
  (:require [clojure.test :refer [deftest is testing]]
            [notify-system.users :as users]))

(deftest test-user-validation
  (testing "User data validation works correctly"
    (testing "Valid user data"
      (is (true? (users/valid-user-data? {:email "test@example.com" :name "John Doe"})))
      (is (true? (users/valid-user-data? {:email "test@example.com" :name "John Doe" :phone "+1234567890"}))))
    
    (testing "Invalid email formats"
      (is (false? (users/valid-user-data? {:email "invalid-email" :name "John Doe"})))
      (is (false? (users/valid-user-data? {:email "" :name "John Doe"})))
      (is (false? (users/valid-user-data? {:name "John Doe"}))))
    
    (testing "Invalid names"
      (is (false? (users/valid-user-data? {:email "test@example.com" :name ""})))
      (is (false? (users/valid-user-data? {:email "test@example.com" :name "   "})))
      (is (false? (users/valid-user-data? {:email "test@example.com"}))))))

(deftest test-email-validation
  (testing "Email validation helper works correctly"
    (testing "Valid emails"
      (is (true? (users/valid-email? "test@example.com")))
      (is (true? (users/valid-email? "user.name+tag@domain.co.uk")))
      (is (true? (users/valid-email? "123@test.org"))))
    
    (testing "Invalid emails"
      (is (false? (users/valid-email? "invalid-email")))
      (is (false? (users/valid-email? "@example.com")))
      (is (false? (users/valid-email? "test@")))
      (is (false? (users/valid-email? "")))
      (is (false? (users/valid-email? nil))))))

(deftest test-user-service-interface
  (testing "User service can be created"
    (let [service (users/create-user-service)]
      (is (not (nil? service)))
      (is (satisfies? users/UserService service)))))

(deftest test-facade-functions
  (testing "Facade functions provide convenient access"
    (testing "find-user function"
      ;; This would normally test against a database
      ;; Here we just test that the function exists and handles inputs
      (is (fn? users/find-user)))
    
    (testing "register-user function"
      ;; Test validation without database
      (let [result (users/register-user "invalid-email" "")]
        (is (contains? result :error))))
    
    (testing "valid-email? helper"
      (is (true? (users/valid-email? "test@example.com")))
      (is (false? (users/valid-email? "invalid"))))))

;; Mock tests (would normally use test database)
(deftest test-user-operations-mock
  (testing "User operations work with valid data"
    (testing "User registration validation"
      (let [valid-user {:email "test@example.com" :name "John Doe" :phone "+1234567890"}]
        (is (users/valid-user-data? valid-user))))
    
    (testing "User search functionality exists"
      (is (fn? users/search-users))
      (is (fn? users/get-all-users)))))

(deftest test-preference-management
  (testing "User preference functions exist and are callable"
    (is (fn? users/update-preferences))
    (is (fn? users/get-user-profile))
    
    (testing "update-preferences handles invalid input"
      (let [result (users/update-preferences "invalid-id" [] [])]
        (is (contains? result :status))))))

(deftest test-error-handling
  (testing "Functions handle errors gracefully"
    (testing "register-user with invalid data"
      (let [result (users/register-user "" "")]
        (is (contains? result :error))
        (is (= :validation-error (:type result)))))
    
    (testing "register-user with invalid email"
      (let [result (users/register-user "invalid-email" "John Doe")]
        (is (contains? result :error))))))

(deftest test-data-specs
  (testing "Clojure specs are properly defined"
    (testing "Email spec validation"
      (is (true? (users/valid-email? "user@domain.com")))
      (is (false? (users/valid-email? "not-an-email"))))
    
    (testing "User data spec validation"
      (is (true? (users/valid-user-data? {:email "test@example.com" :name "John"})))
      (is (false? (users/valid-user-data? {:name "John"}))))  ; missing email
    
    (testing "Phone number validation in spec"
      (is (true? (users/valid-user-data? {:email "test@example.com" :name "John" :phone "+1-234-567-8900"})))
      (is (true? (users/valid-user-data? {:email "test@example.com" :name "John" :phone "1234567890"}))))))

;; Integration-style tests (would need database setup)
(deftest test-user-lifecycle
  (testing "Complete user lifecycle operations exist"
    (is (fn? users/register-user) "User registration function exists")
    (is (fn? users/find-user) "User lookup function exists")
    (is (fn? users/get-user-profile) "User profile function exists")
    (is (fn? users/update-preferences) "Preference update function exists")
    (is (fn? users/search-users) "User search function exists")))

(deftest test-service-architecture
  (testing "Service follows proper architecture patterns"
    (testing "Protocol-based design"
      (is (var? #'users/UserService) "UserService protocol is defined"))
    
    (testing "Factory pattern"
      (let [service (users/create-user-service)]
        (is (not (nil? service)) "Factory creates service instance")))
    
    (testing "Facade pattern"
      (is (fn? users/register-user) "Facade functions exist")
      (is (fn? users/find-user) "Facade functions exist")
      (is (fn? users/get-user-profile) "Facade functions exist"))))
