(ns notify-system.frontend.utils
  "Utility functions for the notification system frontend"
  (:require [clojure.string :as str]))

(defn format-timestamp
  "Format timestamp for display"
  [timestamp]
  (.toLocaleString (js/Date. timestamp)))

(defn not-blank?
  "Check if string is not blank"
  [s]
  (not (str/blank? s)))

(defn extract-target-value
  "Extract value from DOM event target"
  [event]
  (.-value (.-target event)))

(defn prevent-default
  "Prevent default event behavior"
  [event]
  (.preventDefault event))

(defn is-https?
  "Check if current protocol is HTTPS"
  []
  (= (.-protocol js/location) "https:"))

(defn get-websocket-url
  "Get WebSocket URL based on current location"
  []
  (let [protocol (if (is-https?) "wss:" "ws:")]
    (str protocol "//" (.-host js/location) "/ws")))

(defn safe-json-parse
  "Safely parse JSON with error handling"
  [json-str]
  (try
    (js->clj (js/JSON.parse json-str) :keywordize-keys true)
    (catch js/Error e
      (js/console.error "Error parsing JSON:" e)
      nil)))

(defn safe-json-stringify
  "Safely stringify to JSON"
  [data]
  (try
    (js/JSON.stringify (clj->js data))
    (catch js/Error e
      (js/console.error "Error stringifying JSON:" e)
      nil)))

(defn local-storage-get
  "Safely get item from localStorage"
  [key default-value]
  (try
    (or (js/localStorage.getItem key) default-value)
    (catch js/Error e
      (js/console.warn "Error accessing localStorage:" e)
      default-value)))

(defn local-storage-set
  "Safely set item in localStorage"
  [key value]
  (try
    (js/localStorage.setItem key value)
    (catch js/Error e
      (js/console.warn "Error setting localStorage:" e))))

(defn log-error
  "Log error with context"
  [context error]
  (js/console.error (str context ":") error))

(defn log-info
  "Log info message"
  [message & args]
  (apply js/console.log message args))

(defn debounce
  "Debounce function calls"
  [f delay]
  (let [timeout (atom nil)]
    (fn [& args]
      (when @timeout
        (js/clearTimeout @timeout))
      (reset! timeout
              (js/setTimeout
               #(apply f args)
               delay)))))

(defn throttle
  "Throttle function calls"
  [f delay]
  (let [last-call (atom 0)]
    (fn [& args]
      (let [now (js/Date.now)]
        (when (> (- now @last-call) delay)
          (reset! last-call now)
          (apply f args))))))
