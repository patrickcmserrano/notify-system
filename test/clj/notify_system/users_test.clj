(ns notify-system.users-test
  "Comprehensive tests for user management"
  (:require [clojure.test :refer [deftest is testing]]
            [notify-system.users :as users]))

(deftest test-user-validation
  (testing "User data validation works correctly"
    (println "[TEST-START] test-user-validation - Testing user data validation logic")
    (testing "Valid user data"
      (let [valid-users [{:email "test@example.com" :name "John Doe"}
                         {:email "test@example.com" :name "John Doe" :phone "+1234567890"}]]
        (doseq [user valid-users]
          (println (format "[USER-VALIDATION] Testing valid user: %s" user))
          (let [result (users/valid-user-data? user)]
            (println (format "[VALIDATION-RESULT] Valid user result: %s" result))
            (is (true? result))))))
    
    (testing "Invalid email formats"
      (let [invalid-cases [{:user {:email "invalid-email" :name "John Doe"} :desc "invalid email format"}
                          {:user {:email "" :name "John Doe"} :desc "empty email"}
                          {:user {:name "John Doe"} :desc "missing email"}]]
        (doseq [{:keys [user desc]} invalid-cases]
          (println (format "[USER-VALIDATION] Testing invalid user - %s: %s" desc user))
          (let [result (users/valid-user-data? user)]
            (println (format "[VALIDATION-RESULT] Invalid user result for %s: %s" desc result))
            (is (false? result))))))
    
    (testing "Invalid names"
      (let [invalid-cases [{:user {:email "test@example.com" :name ""} :desc "empty name"}
                          {:user {:email "test@example.com" :name "   "} :desc "whitespace name"}
                          {:user {:email "test@example.com"} :desc "missing name"}]]
        (doseq [{:keys [user desc]} invalid-cases]
          (println (format "[USER-VALIDATION] Testing invalid user - %s: %s" desc user))
          (let [result (users/valid-user-data? user)]
            (println (format "[VALIDATION-RESULT] Invalid user result for %s: %s" desc result))
            (is (false? result))))))
    (println "[TEST-SUCCESS] User validation tests completed")))

(deftest test-email-validation
  (testing "Email validation helper works correctly"
    (println "[TEST-START] test-email-validation - Testing email validation helper")
    (testing "Valid emails"
      (let [valid-emails ["test@example.com" "user.name+tag@domain.co.uk" "123@test.org"]]
        (doseq [email valid-emails]
          (println (format "[EMAIL-VALIDATION] Testing valid email: %s" email))
          (let [result (users/valid-email? email)]
            (println (format "[VALIDATION-RESULT] Valid email result for %s: %s" email result))
            (is (true? result))))))
    
    (testing "Invalid emails"
      (let [invalid-emails [["invalid-email" "no @ symbol"]
                           ["@example.com" "missing local part"]
                           ["test@" "missing domain"]
                           ["" "empty string"]
                           [nil "nil value"]]]
        (doseq [[email desc] invalid-emails]
          (println (format "[EMAIL-VALIDATION] Testing invalid email - %s: %s" desc email))
          (let [result (users/valid-email? email)]
            (println (format "[VALIDATION-RESULT] Invalid email result for %s: %s" desc result))
            (is (false? result))))))
    (println "[TEST-SUCCESS] Email validation tests completed")))

(deftest test-user-service-interface
  (testing "User service can be created"
    (println "[TEST-START] test-user-service-interface - Testing user service interface")
    (let [start-time (System/currentTimeMillis)
          service (users/create-user-service)
          end-time (System/currentTimeMillis)]
      (println (format "[SERVICE-CREATION] User service created in %d ms" (- end-time start-time)))
      (println (format "[SERVICE-TYPE] Service type: %s" (type service)))
      (is (not (nil? service)))
      (is (satisfies? users/UserService service))
      (println "[SERVICE-VALIDATION] User service interface compliance verified")
      (println "[TEST-SUCCESS] User service interface test completed"))))

(deftest test-facade-functions
  (testing "Facade functions provide convenient access"
    (println "[TEST-START] test-facade-functions - Testing user facade functions")
    (testing "find-user function"
      ;; This would normally test against a database
      ;; Here we just test that the function exists and handles inputs
      (println "[FACADE-TEST] Testing find-user function existence")
      (is (fn? users/find-user))
      (println "[FACADE-VALIDATION] find-user function verified as callable"))
    
    (testing "register-user function"
      ;; Test validation without database
      (println "[FACADE-TEST] Testing register-user validation")
      (let [result (users/register-user "invalid-email" "")]
        (println (format "[FACADE-RESULT] Register user with invalid data result: %s" result))
        (is (contains? result :error))))
    
    (testing "valid-email? helper"
      (println "[FACADE-TEST] Testing email validation helper")
      (let [valid-result (users/valid-email? "test@example.com")
            invalid-result (users/valid-email? "invalid")]
        (println (format "[FACADE-RESULT] Valid email test: %s" valid-result))
        (println (format "[FACADE-RESULT] Invalid email test: %s" invalid-result))
        (is (true? valid-result))
        (is (false? invalid-result))))
    (println "[TEST-SUCCESS] Facade functions test completed")))

;; Mock tests (would normally use test database)
(deftest test-user-operations-mock
  (testing "User operations work with valid data"
    (println "[TEST-START] test-user-operations-mock - Testing mock user operations")
    (testing "User registration validation"
      (let [valid-user {:email "test@example.com" :name "John Doe" :phone "+1234567890"}]
        (println (format "[MOCK-TEST] Testing user registration validation with: %s" valid-user))
        (let [result (users/valid-user-data? valid-user)]
          (println (format "[MOCK-RESULT] Registration validation result: %s" result))
          (is result))))
    
    (testing "User search functionality exists"
      (println "[MOCK-TEST] Verifying user search functions exist")
      (is (fn? users/search-users))
      (is (fn? users/get-all-users))
      (println "[MOCK-VALIDATION] User search functions verified"))
    (println "[TEST-SUCCESS] Mock user operations test completed")))

(deftest test-preference-management
  (testing "User preference functions exist and are callable"
    (println "[TEST-START] test-preference-management - Testing preference management")
    (println "[PREFERENCE-TEST] Verifying preference management functions exist")
    (is (fn? users/update-preferences))
    (is (fn? users/get-user-profile))
    (println "[PREFERENCE-VALIDATION] Preference functions verified as callable")
    
    (testing "update-preferences handles invalid input"
      (println "[PREFERENCE-ERROR-TEST] Testing preference update with invalid input")
      (let [result (users/update-preferences "invalid-id" [] [])]
        (println (format "[PREFERENCE-ERROR-RESULT] Invalid input result: %s" result))
        (is (contains? result :status))))
    (println "[TEST-SUCCESS] Preference management test completed")))

(deftest test-error-handling
  (testing "Functions handle errors gracefully"
    (println "[TEST-START] test-error-handling - Testing error handling mechanisms")
    (testing "register-user with invalid data"
      (println "[ERROR-TEST] Testing user registration with completely invalid data")
      (let [result (users/register-user "" "")]
        (println (format "[ERROR-RESULT] Empty data registration result: %s" result))
        (is (contains? result :error))
        (is (= :validation-error (:type result)))))
    
    (testing "register-user with invalid email"
      (println "[ERROR-TEST] Testing user registration with invalid email")
      (let [result (users/register-user "invalid-email" "John Doe")]
        (println (format "[ERROR-RESULT] Invalid email registration result: %s" result))
        (is (contains? result :error))))
    (println "[TEST-SUCCESS] Error handling test completed")))

(deftest test-data-specs
  (testing "Clojure specs are properly defined"
    (println "[TEST-START] test-data-specs - Testing Clojure spec definitions")
    (testing "Email spec validation"
      (println "[SPEC-TEST] Testing email specification validation")
      (let [valid-result (users/valid-email? "user@domain.com")
            invalid-result (users/valid-email? "not-an-email")]
        (println (format "[SPEC-RESULT] Valid email spec result: %s" valid-result))
        (println (format "[SPEC-RESULT] Invalid email spec result: %s" invalid-result))
        (is (true? valid-result))
        (is (false? invalid-result))))
    
    (testing "User data spec validation"
      (println "[SPEC-TEST] Testing user data specification validation")
      (let [valid-user {:email "test@example.com" :name "John"}
            invalid-user {:name "John"}]  ; missing email
        (println (format "[SPEC-TEST] Testing valid user data: %s" valid-user))
        (let [valid-result (users/valid-user-data? valid-user)]
          (println (format "[SPEC-RESULT] Valid user data result: %s" valid-result))
          (is (true? valid-result)))
        
        (println (format "[SPEC-TEST] Testing invalid user data: %s" invalid-user))
        (let [invalid-result (users/valid-user-data? invalid-user)]
          (println (format "[SPEC-RESULT] Invalid user data result: %s" invalid-result))
          (is (false? invalid-result)))))
    
    (testing "Phone number validation in spec"
      (println "[SPEC-TEST] Testing phone number specification validation")
      (let [users-with-phone [{:email "test@example.com" :name "John" :phone "+1-234-567-8900"}
                             {:email "test@example.com" :name "John" :phone "1234567890"}]]
        (doseq [user users-with-phone]
          (println (format "[SPEC-TEST] Testing user with phone: %s" user))
          (let [result (users/valid-user-data? user)]
            (println (format "[SPEC-RESULT] Phone validation result: %s" result))
            (is (true? result))))))
    (println "[TEST-SUCCESS] Data specs test completed")))

;; Integration-style tests (would need database setup)
(deftest test-user-lifecycle
  (testing "Complete user lifecycle operations exist"
    (println "[TEST-START] test-user-lifecycle - Testing user lifecycle operations")
    (println "[LIFECYCLE-TEST] Verifying user lifecycle functions exist")
    (is (fn? users/register-user) "User registration function exists")
    (is (fn? users/find-user) "User lookup function exists")
    (is (fn? users/get-user-profile) "User profile function exists")
    (is (fn? users/update-preferences) "Preference update function exists")
    (is (fn? users/search-users) "User search function exists")
    (println "[LIFECYCLE-VALIDATION] All user lifecycle functions verified")
    (println "[TEST-SUCCESS] User lifecycle test completed")))

(deftest test-service-architecture
  (testing "Service follows proper architecture patterns"
    (println "[TEST-START] test-service-architecture - Testing service architecture patterns")
    (testing "Protocol-based design"
      (println "[ARCHITECTURE-TEST] Verifying protocol-based design")
      (is (var? #'users/UserService) "UserService protocol is defined")
      (println "[ARCHITECTURE-VALIDATION] UserService protocol verified"))
    
    (testing "Factory pattern"
      (println "[ARCHITECTURE-TEST] Testing factory pattern implementation")
      (let [service (users/create-user-service)]
        (println (format "[FACTORY-RESULT] Service created: %s" (type service)))
        (is (not (nil? service)) "Factory creates service instance")))
    
    (testing "Facade pattern"
      (println "[ARCHITECTURE-TEST] Verifying facade pattern implementation")
      (is (fn? users/register-user) "Facade functions exist")
      (is (fn? users/find-user) "Facade functions exist")
      (is (fn? users/get-user-profile) "Facade functions exist")
      (println "[FACADE-VALIDATION] All facade functions verified"))
    (println "[TEST-SUCCESS] Service architecture test completed")))
