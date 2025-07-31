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
    (testing "SMS channel creation"
      (let [channel (channels/create-channel "SMS")]
        (is (instance? notify_system.channels.SMSChannel channel))
        (is (= "SMS" (channels/channel-name channel)))))
    
    (testing "Email channel creation"
      (let [channel (channels/create-channel "Email")]
        (is (instance? notify_system.channels.EmailChannel channel))
        (is (= "Email" (channels/channel-name channel)))))
    
    (testing "Push channel creation"
      (let [channel (channels/create-channel "Push")]
        (is (instance? notify_system.channels.PushChannel channel))
        (is (= "Push" (channels/channel-name channel)))))
    
    (testing "Invalid channel type throws exception"
      (is (thrown? Exception (channels/create-channel "Invalid"))))))

(deftest test-sms-channel
  (testing "SMS channel functionality"
    (let [channel (channels/create-channel "SMS")]
      
      (testing "Validates user with phone number"
        (is (true? (channels/validate-user channel test-user)))
        (is (false? (channels/validate-user channel (dissoc test-user :phone)))))
      
      (testing "Sends notification successfully"
        (let [result (channels/send-notification channel test-user test-message)]
          (is (= "SMS" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result)))))
      
      (testing "Fails when user has no phone"
        (let [user-no-phone (dissoc test-user :phone)
              result (channels/send-notification channel user-no-phone test-message)]
          (is (= "failed" (:status result)))
          (is (contains? result :error)))))))

(deftest test-email-channel
  (testing "Email channel functionality"
    (let [channel (channels/create-channel "Email")]
      
      (testing "Validates user with email"
        (is (true? (channels/validate-user channel test-user)))
        (is (false? (channels/validate-user channel (dissoc test-user :email)))))
      
      (testing "Sends notification successfully"
        (let [result (channels/send-notification channel test-user test-message)]
          (is (= "Email" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result)))))
      
      (testing "Fails when user has no email"
        (let [user-no-email (dissoc test-user :email)
              result (channels/send-notification channel user-no-email test-message)]
          (is (= "failed" (:status result)))
          (is (contains? result :error)))))))

(deftest test-push-channel
  (testing "Push channel functionality"
    (let [channel (channels/create-channel "Push")]
      
      (testing "Always validates user (assumes device registration)"
        (is (true? (channels/validate-user channel test-user)))
        (is (true? (channels/validate-user channel {}))))
      
      (testing "Sends notification successfully"
        (let [result (channels/send-notification channel test-user test-message)]
          (is (= "Push" (:channel result)))
          (is (= (:id test-user) (:user-id result)))
          (is (= "sent" (:status result))))))))

(deftest test-message-validation
  (testing "Message validation works across all channels"
    (let [channels-to-test [(channels/create-channel "SMS")
                           (channels/create-channel "Email")
                           (channels/create-channel "Push")]]
      
      (doseq [channel channels-to-test]
        (testing (str "Channel " (channels/channel-name channel))
          
          (testing "Rejects empty content"
            (let [invalid-msg {:category "Sports" :content ""}
                  result (channels/send-notification channel test-user invalid-msg)]
              (is (= "failed" (:status result)))))
          
          (testing "Rejects nil content"
            (let [invalid-msg {:category "Sports" :content nil}
                  result (channels/send-notification channel test-user invalid-msg)]
              (is (= "failed" (:status result)))))
          
          (testing "Rejects missing category"
            (let [invalid-msg {:content "Test message"}
                  result (channels/send-notification channel test-user invalid-msg)]
              (is (= "failed" (:status result))))))))))

(deftest test-channel-selection
  (testing "Channel selection strategy works correctly"
    (testing "Selects valid channels for user"
      (let [user-with-all {:id "user-1"
                          :name "John Doe"
                          :email "john@example.com"
                          :phone "+1234567890"}
            channels (channels/select-channels-for-user user-with-all ["SMS" "Email" "Push"])]
        (is (= 3 (count channels)))))
    
    (testing "Filters out invalid channels"
      (let [user-no-phone {:id "user-1"
                          :name "John Doe"
                          :email "john@example.com"}
            channels (channels/select-channels-for-user user-no-phone ["SMS" "Email" "Push"])]
        (is (= 2 (count channels)))
        (is (every? #(not= "SMS" (channels/channel-name %)) channels))))
    
    (testing "Returns empty for user with no valid channels"
      (let [user-no-contact {:id "user-1" :name "John Doe"}
            channels (channels/select-channels-for-user user-no-contact ["SMS" "Email"])]
        (is (= 0 (count channels)))))))

(deftest test-available-channels
  (testing "Returns all available channel types"
    (let [available (channels/get-available-channels)]
      (is (= 3 (count available)))
      (is (contains? (set available) "SMS"))
      (is (contains? (set available) "Email"))
      (is (contains? (set available) "Push")))))


