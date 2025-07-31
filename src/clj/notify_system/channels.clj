(ns notify-system.channels
  "Notification channel implementations using Strategy pattern"
  (:require [clojure.string :as str]))

;; Protocol for notification channels (Interface abstraction)
(defprotocol NotificationChannel
  "Protocol defining the interface for all notification channels"
  (send-notification [this user message] "Send notification to user through this channel")
  (channel-name [this] "Get the name of this channel")
  (validate-user [this user] "Validate if user can receive notifications through this channel"))

;; Abstract base for validation (Best practices - reusable validation)
(defn validate-message
  "Validates message content before sending"
  [message]
  (when (or (nil? message) (empty? (:content message)))
    (throw (ex-info "Message content cannot be empty" {:type :validation-error})))
  (when (or (nil? (:category message)) (empty? (:category message)))
    (throw (ex-info "Message category is required" {:type :validation-error}))))

;; SMS Channel Implementation
(defrecord SMSChannel []
  NotificationChannel
  (send-notification [_ user message]
    (try
      (validate-message message)
      (when-not (:phone user)
        (throw (ex-info "User phone number is required for SMS" 
                       {:type :validation-error :user-id (:id user)})))
      
      ;; Simulate SMS sending (would integrate with SMS service)
      (let [result {:channel "SMS"
                   :user-id (:id user)
                   :phone (:phone user)
                   :message (:content message)
                   :message-category (:category message)
                   :category (:category message)
                   :status "sent"
                   :timestamp (java.time.Instant/now)}]
        (println (format "SMS sent to %s (%s): %s" 
                        (:name user) (:phone user) (:content message)))
        result)
      (catch Exception e
        {:channel "SMS"
         :user-id (:id user)
         :message-category (:category message)
         :status "failed"
         :error (.getMessage e)
         :timestamp (java.time.Instant/now)})))
  
  (channel-name [_] "SMS")
  
  (validate-user [_ user]
    (boolean (and (:phone user) (seq (str (:phone user)))))))

;; Email Channel Implementation
(defrecord EmailChannel []
  NotificationChannel
  (send-notification [_ user message]
    (try
      (validate-message message)
      (when-not (:email user)
        (throw (ex-info "User email is required for Email notifications" 
                        {:type    :validation-error
                         :user-id (:id user)})))
      
      ;; Simulate email sending (would integrate with email service)
      (let [result {:channel "Email"
                   :user-id (:id user)
                   :email (:email user)
                   :message (:content message)
                   :message-category (:category message)
                   :category (:category message)
                   :status "sent"
                   :timestamp (java.time.Instant/now)}]
        (println (format "Email sent to %s (%s): %s" 
                        (:name user) (:email user) (:content message)))
        result)
      (catch Exception e
        {:channel "Email"
         :user-id (:id user)
         :message-category (:category message)
         :status "failed"
         :error (.getMessage e)
         :timestamp (java.time.Instant/now)})))
  
  (channel-name [_] "Email")
  
  (validate-user [_ user]
    (boolean (and (:email user) (seq (str (:email user)))))))

;; Push Notification Channel Implementation
(defrecord PushChannel []
  NotificationChannel
  (send-notification [_ user message]
    (try
      (validate-message message)
      ;; For push notifications, we assume device registration exists
      ;; In real implementation, would check for device tokens
      
      ;; Simulate push notification sending
      (let [result {:channel "Push"
                   :user-id (:id user)
                   :message (:content message)
                   :message-category (:category message)
                   :category (:category message)
                   :status "sent"
                   :timestamp (java.time.Instant/now)}]
        (println (format "Push notification sent to %s: %s" 
                        (:name user) (:content message)))
        result)
      (catch Exception e
        {:channel "Push"
         :user-id (:id user)
         :message-category (:category message)
         :status "failed"
         :error (.getMessage e)
         :timestamp (java.time.Instant/now)})))
  
  (channel-name [_] "Push")
  
  (validate-user [_ _user]
    ;; In real implementation, would check for device registration
    true))

;; Factory pattern for creating channels (Design Pattern)
(defn create-channel
  "Factory method to create notification channels"
  [channel-type]
  (case (str/lower-case channel-type)
    "sms" (->SMSChannel)
    "email" (->EmailChannel)
    "push" (->PushChannel)
    (throw (ex-info (str "Unknown channel type: " channel-type) 
                   {:type :invalid-channel :channel channel-type}))))

;; Strategy pattern implementation for channel selection
(defn get-available-channels
  "Get all available notification channels"
  []
  ["SMS" "Email" "Push"])

(defn select-channels-for-user
  "Select appropriate channels for a user based on their preferences"
  [user preferred-channels]
  (->> preferred-channels
       (filter #(let [channel (create-channel %)]
                  (validate-user channel user)))
       (map create-channel)))