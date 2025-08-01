(ns notify-system.channels-test
  "Comprehensive tests for notification channels"
  (:require [clojure.test :refer [deftest is testing]]
            [notify-system.channels :as channels]))

;; Test data
(def test-user
  {:id "user-1"
   :name "John Doe"
   :email "john@example.com"
   :phone "+1234567890"})

(def test-message
  {:category "Sports"
   :content  "Game tonight at 8 PM!"})

(deftest test-channel-creation
  (testing "Channel factory creates correct channel types"
    (println "[TEST-START] test-channel-creation - Testing notification channel factory")
    (testing "SMS channel creation"
      (println "[FACTORY-TEST] Creating SMS channel")
      (let [start-time (System/currentTimeMillis)
            channel (channels/create-channel "SMS")
            end-time (System/currentTimeMillis)]
        (println (format "[FACTORY-RESULT] SMS channel created in %d ms" (- end-time start-time)))
        (println (format "[FACTORY-VALIDATION] Channel type: %s, Name: %s" (type channel) (channels/channel-name channel)))
        (is (instance? notify_system.channels.SMSChannel channel))
        (is (= "SMS" (channels/channel-name channel)))))
    
    (testing "Email channel creation"
      (println "[FACTORY-TEST] Creating Email channel")
      (let [start-time (System/currentTimeMillis)
            channel (channels/create-channel "Email")
            end-time (System/currentTimeMillis)]
        (println (format "[FACTORY-RESULT] Email channel created in %d ms" (- end-time start-time)))
        (println (format "[FACTORY-VALIDATION] Channel type: %s, Name: %s" (type channel) (channels/channel-name channel)))
        (is (instance? notify_system.channels.EmailChannel channel))
        (is (= "Email" (channels/channel-name channel)))))
    
    (testing "Push channel creation"
      (println "[FACTORY-TEST] Creating Push channel")
      (let [start-time (System/currentTimeMillis)
            channel (channels/create-channel "Push")
            end-time (System/currentTimeMillis)]
        (println (format "[FACTORY-RESULT] Push channel created in %d ms" (- end-time start-time)))
        (println (format "[FACTORY-VALIDATION] Channel type: %s, Name: %s" (type channel) (channels/channel-name channel)))
        (is (instance? notify_system.channels.PushChannel channel))
        (is (= "Push" (channels/channel-name channel)))))
    
    (testing "Invalid channel type throws exception"
      (println "[FACTORY-ERROR-TEST] Testing invalid channel type creation")
      (is (thrown? Exception (channels/create-channel "Invalid"))))
    (println "[TEST-SUCCESS] Channel creation tests completed")))

(deftest test-sms-channel
  (testing "SMS channel functionality"
    (println "[TEST-START] test-sms-channel - Testing SMS channel operations")
    (let [channel (channels/create-channel "SMS")]
      (println (format "[CHANNEL-INFO] SMS channel instance: %s" (type channel)))
      
      (testing "Validates user with phone number"
        (println (format "[VALIDATION-TEST] Testing SMS validation with user: %s" test-user))
        (let [valid-result (channels/validate-user channel test-user)]
          (println (format "[VALIDATION-RESULT] User with phone validation: %s" valid-result))
          (is (true? valid-result)))
        
        (let [user-no-phone (dissoc test-user :phone)]
          (println (format "[VALIDATION-TEST] Testing SMS validation without phone: %s" user-no-phone))
          (let [invalid-result (channels/validate-user channel user-no-phone)]
            (println (format "[VALIDATION-RESULT] User without phone validation: %s" invalid-result))
            (is (false? invalid-result)))))
      
      (testing "Sends notification successfully"
        (println "[NOTIFICATION-SEND] Sending SMS notification to valid user")
        (println (format "[SEND-DETAILS] User: %s, Message: %s, Timestamp: %s" 
                         test-user test-message (java.time.Instant/now)))
        (let [start-time (System/currentTimeMillis)
              result (channels/send-notification channel test-user test-message)
              end-time (System/currentTimeMillis)]
          (println (format "[SEND-RESULT] SMS send completed in %d ms" (- end-time start-time)))
          (println (format "[SEND-STATUS] Channel: %s, User ID: %s, Status: %s" 
                           (:channel result) (:user-id result) (:status result)))
          (is (= "SMS" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result)))))
      
      (testing "Fails when user has no phone"
        (let [user-no-phone (dissoc test-user :phone)]
          (println (format "[NOTIFICATION-ERROR-TEST] Testing SMS send to user without phone: %s" user-no-phone))
          (let [result (channels/send-notification channel user-no-phone test-message)]
            (println (format "[SEND-ERROR-RESULT] SMS failure result: %s" result))
            (is (= "failed" (:status result)))
            (is (contains? result :error)))))
      (println "[TEST-SUCCESS] SMS channel tests completed"))))

(deftest test-email-channel
  (testing "Email channel functionality"
    (println "[TEST-START] test-email-channel - Testing Email channel operations")
    (let [channel (channels/create-channel "Email")]
      (println (format "[CHANNEL-INFO] Email channel instance: %s" (type channel)))
      
      (testing "Validates user with email"
        (println (format "[VALIDATION-TEST] Testing Email validation with user: %s" test-user))
        (let [valid-result (channels/validate-user channel test-user)]
          (println (format "[VALIDATION-RESULT] User with email validation: %s" valid-result))
          (is (true? valid-result)))
        
        (let [user-no-email (dissoc test-user :email)]
          (println (format "[VALIDATION-TEST] Testing Email validation without email: %s" user-no-email))
          (let [invalid-result (channels/validate-user channel user-no-email)]
            (println (format "[VALIDATION-RESULT] User without email validation: %s" invalid-result))
            (is (false? invalid-result)))))
      
      (testing "Sends notification successfully"
        (println "[NOTIFICATION-SEND] Sending Email notification to valid user")
        (println (format "[SEND-DETAILS] User: %s, Message: %s, Timestamp: %s" 
                         test-user test-message (java.time.Instant/now)))
        (let [start-time (System/currentTimeMillis)
              result (channels/send-notification channel test-user test-message)
              end-time (System/currentTimeMillis)]
          (println (format "[SEND-RESULT] Email send completed in %d ms" (- end-time start-time)))
          (println (format "[SEND-STATUS] Channel: %s, User ID: %s, Status: %s" 
                           (:channel result) (:user-id result) (:status result)))
          (is (= "Email" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result)))))
      
      (testing "Fails when user has no email"
        (let [user-no-email (dissoc test-user :email)]
          (println (format "[NOTIFICATION-ERROR-TEST] Testing Email send to user without email: %s" user-no-email))
          (let [result (channels/send-notification channel user-no-email test-message)]
            (println (format "[SEND-ERROR-RESULT] Email failure result: %s" result))
            (is (= "failed" (:status result)))
            (is (contains? result :error)))))
      (println "[TEST-SUCCESS] Email channel tests completed"))))

(deftest test-push-channel
  (testing "Push channel functionality"
    (println "[TEST-START] test-push-channel - Testing Push channel operations")
    (let [channel (channels/create-channel "Push")]
      (println (format "[CHANNEL-INFO] Push channel instance: %s" (type channel)))
      
      (testing "Always validates user (assumes device registration)"
        (println (format "[VALIDATION-TEST] Testing Push validation with complete user: %s" test-user))
        (let [result1 (channels/validate-user channel test-user)]
          (println (format "[VALIDATION-RESULT] Complete user validation: %s" result1))
          (is (true? result1)))
        
        (println "[VALIDATION-TEST] Testing Push validation with empty user")
        (let [result2 (channels/validate-user channel {})]
          (println (format "[VALIDATION-RESULT] Empty user validation: %s" result2))
          (is (true? result2))))
      
      (testing "Sends notification successfully"
        (println "[NOTIFICATION-SEND] Sending Push notification")
        (println (format "[SEND-DETAILS] User: %s, Message: %s, Timestamp: %s" 
                         test-user test-message (java.time.Instant/now)))
        (let [start-time (System/currentTimeMillis)
              result (channels/send-notification channel test-user test-message)
              end-time (System/currentTimeMillis)]
          (println (format "[SEND-RESULT] Push send completed in %d ms" (- end-time start-time)))
          (println (format "[SEND-STATUS] Channel: %s, User ID: %s, Status: %s" 
                           (:channel result) (:user-id result) (:status result)))
          (is (= "Push" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result)))))
      (println "[TEST-SUCCESS] Push channel tests completed"))))

(deftest test-message-validation
  (testing "Message validation works across all channels"
    (println "[TEST-START] test-message-validation - Testing message validation across channels")
    (let [channels-to-test [(channels/create-channel "SMS")
                           (channels/create-channel "Email")
                           (channels/create-channel "Push")]]
      
      (doseq [channel channels-to-test]
        (let [channel-name (channels/channel-name channel)]
          (println (format "[VALIDATION-SUITE] Testing channel: %s" channel-name))
          (testing (str "Channel " channel-name)
            
            (testing "Rejects empty content"
              (let [invalid-msg {:category "Sports" :content ""}]
                (println (format "[MESSAGE-VALIDATION] Testing empty content for %s: %s" channel-name invalid-msg))
                (let [result (channels/send-notification channel test-user invalid-msg)]
                  (println (format "[VALIDATION-RESULT] Empty content result for %s: %s" channel-name (:status result)))
                  (is (= "failed" (:status result))))))
            
            (testing "Rejects nil content"
              (let [invalid-msg {:category "Sports" :content nil}]
                (println (format "[MESSAGE-VALIDATION] Testing nil content for %s: %s" channel-name invalid-msg))
                (let [result (channels/send-notification channel test-user invalid-msg)]
                  (println (format "[VALIDATION-RESULT] Nil content result for %s: %s" channel-name (:status result)))
                  (is (= "failed" (:status result))))))
            
            (testing "Rejects missing category"
              (let [invalid-msg {:content "Test message"}]
                (println (format "[MESSAGE-VALIDATION] Testing missing category for %s: %s" channel-name invalid-msg))
                (let [result (channels/send-notification channel test-user invalid-msg)]
                  (println (format "[VALIDATION-RESULT] Missing category result for %s: %s" channel-name (:status result)))
                  (is (= "failed" (:status result)))))))))
    (println "[TEST-SUCCESS] Message validation tests completed"))))

(deftest test-channel-selection
  (testing "Channel selection strategy works correctly"
    (println "[TEST-START] test-channel-selection - Testing channel selection strategy")
    (testing "Selects valid channels for user"
      (let [user-with-all {:id "user-1"
                          :name "John Doe"
                          :email "john@example.com"
                          :phone "+1234567890"}]
        (println (format "[CHANNEL-SELECTION] Testing user with all contact methods: %s" user-with-all))
        (let [channels (channels/select-channels-for-user user-with-all ["SMS" "Email" "Push"])]
          (println (format "[SELECTION-RESULT] Selected channels count: %d, Channels: %s" 
                           (count channels) (map channels/channel-name channels)))
          (is (= 3 (count channels))))))
    
    (testing "Filters out invalid channels"
      (let [user-no-phone {:id "user-1"
                          :name "John Doe"
                          :email "john@example.com"}]
        (println (format "[CHANNEL-SELECTION] Testing user without phone: %s" user-no-phone))
        (let [channels (channels/select-channels-for-user user-no-phone ["SMS" "Email" "Push"])]
          (println (format "[SELECTION-RESULT] Selected channels count: %d, Channels: %s" 
                           (count channels) (map channels/channel-name channels)))
          (is (= 2 (count channels)))
          (is (every? #(not= "SMS" (channels/channel-name %)) channels)))))
    
    (testing "Returns empty for user with no valid channels"
      (let [user-no-contact {:id "user-1" :name "John Doe"}]
        (println (format "[CHANNEL-SELECTION] Testing user without contact methods: %s" user-no-contact))
        (let [channels (channels/select-channels-for-user user-no-contact ["SMS" "Email"])]
          (println (format "[SELECTION-RESULT] Selected channels count: %d" (count channels)))
          (is (= 0 (count channels))))))
    (println "[TEST-SUCCESS] Channel selection tests completed")))

(deftest test-available-channels
  (testing "Returns all available channel types"
    (println "[TEST-START] test-available-channels - Testing available channels enumeration")
    (let [start-time (System/currentTimeMillis)
          available (channels/get-available-channels)
          end-time (System/currentTimeMillis)]
      (println (format "[CHANNELS-RETRIEVAL] Available channels retrieved in %d ms" (- end-time start-time)))
      (println (format "[CHANNELS-LIST] Available channels: %s" available))
      (println (format "[CHANNELS-COUNT] Total available channels: %d" (count available)))
      (is (= 3 (count available)))
      (is (contains? (set available) "SMS"))
      (is (contains? (set available) "Email"))
      (is (contains? (set available) "Push"))
      (println "[TEST-SUCCESS] Available channels test completed"))))


