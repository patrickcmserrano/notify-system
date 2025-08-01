(ns notify-system.frontend.events
  "Event handling system for notification frontend"
  (:require [notify-system.frontend.state :as state]
            [notify-system.frontend.api :as api]
            [notify-system.frontend.utils :as utils]))

;; Event handlers using multimethod dispatch
(defmulti handle-event
  "Handle different types of events"
  (fn [event-type & _] event-type))

(defmethod handle-event :data/load-categories
  [_]
  (-> (api/get-categories)
      (.then state/update-categories!)
      (.catch #(state/set-error! "Failed to load categories"))))

(defmethod handle-event :data/load-logs
  [_]
  (-> (api/get-logs)
      (.then state/update-logs!)
      (.catch #(state/set-error! "Failed to load logs"))))

(defmethod handle-event :data/load-statistics
  [_]
  (-> (api/get-statistics)
      (.then state/update-statistics!)
      (.catch #(state/set-error! "Failed to load statistics"))))

(defmethod handle-event :data/refresh-all
  [_]
  (handle-event :data/load-logs)
  (handle-event :data/load-statistics))

(defmethod handle-event :notification/send
  [_ category message]
  (when (and (utils/not-blank? category) (utils/not-blank? message))
    (state/set-notification-loading! true)
    (state/reset-error!)
    (-> (api/send-notification category message)
        (.then (fn [response]
                 (state/set-notification-loading! false)
                 (state/reset-notification!)
                 (handle-event :data/refresh-all)
                 response))
        (.catch (fn [error]
                  (let [error-msg (or (.-message error) "Failed to send notification")]
                    (state/set-notification-loading! false error-msg)))))))

(defmethod handle-event :notification/update-field
  [_ field value]
  (state/update-notification-field! field value))

(defmethod handle-event :notification/reset
  [_]
  (state/reset-notification!))

(defmethod handle-event :ui/clear-error
  [_]
  (state/reset-error!))

(defmethod handle-event :ui/set-loading
  [_ loading?]
  (state/set-loading! loading?))

(defmethod handle-event :websocket/data-update
  [_]
  (handle-event :data/refresh-all))

(defmethod handle-event :default
  [event-type & args]
  (utils/log-error "Unknown event type" {:type event-type :args args}))

;; Public API
(defn dispatch!
  "Dispatch an event"
  [event-type & args]
  (apply handle-event event-type args))

;; Initialize event listeners
(defn initialize-event-listeners!
  "Set up global event listeners"
  []
  ;; Listen for WebSocket data updates
  (.addEventListener js/window "notification-data-update"
                     #(dispatch! :websocket/data-update)))
