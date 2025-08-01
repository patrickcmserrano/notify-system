(ns notify-system.frontend.websocket
  "WebSocket connection management"
  (:require [notify-system.frontend.state :as state]
            [notify-system.frontend.utils :as utils]))

;; WebSocket management
(defonce websocket-atom (atom nil))
(defonce reconnect-timeout (atom nil))

;; Forward declarations
(declare schedule-reconnect! connect-websocket!)

(defn handle-websocket-message
  "Handle incoming WebSocket messages"
  [event]
  (when-let [data (utils/safe-json-parse (.-data event))]
    (utils/log-info "WebSocket message received:" data)
    (when (= (:type data) "log-update")
      ;; Trigger data refresh - this will be handled by events system
      (js/dispatchEvent (js/CustomEvent. "notification-data-update")))))

(defn handle-websocket-open
  "Handle WebSocket connection open"
  []
  (utils/log-info "WebSocket connected")
  (state/update-websocket-connection! true @websocket-atom)
  ;; Clear any pending reconnection
  (when @reconnect-timeout
    (js/clearTimeout @reconnect-timeout)
    (reset! reconnect-timeout nil)))

(defn handle-websocket-close
  "Handle WebSocket connection close"
  []
  (utils/log-info "WebSocket disconnected")
  (state/update-websocket-connection! false nil)
  (reset! websocket-atom nil)
  ;; Schedule reconnection
  (schedule-reconnect!))

(defn handle-websocket-error
  "Handle WebSocket errors"
  [error]
  (utils/log-error "WebSocket error" error)
  (state/update-websocket-connection! false nil))

(defn schedule-reconnect!
  "Schedule WebSocket reconnection"
  []
  (when-not @reconnect-timeout
    (reset! reconnect-timeout
            (js/setTimeout
             (fn []
               (reset! reconnect-timeout nil)
               (connect-websocket!))
             3000))))

(defn connect-websocket!
  "Establish WebSocket connection"
  []
  (when-not @websocket-atom
    (try
      (let [ws-url (utils/get-websocket-url)
            ws (js/WebSocket. ws-url)]
        
        (set! (.-onopen ws) handle-websocket-open)
        (set! (.-onmessage ws) handle-websocket-message)
        (set! (.-onclose ws) handle-websocket-close)
        (set! (.-onerror ws) handle-websocket-error)
        
        (reset! websocket-atom ws)
        (utils/log-info "Connecting WebSocket to:" ws-url))
      
      (catch js/Error e
        (utils/log-error "Failed to create WebSocket connection" e)
        (schedule-reconnect!)))))

(defn disconnect-websocket!
  "Disconnect WebSocket"
  []
  (when @websocket-atom
    (.close @websocket-atom)
    (reset! websocket-atom nil))
  (when @reconnect-timeout
    (js/clearTimeout @reconnect-timeout)
    (reset! reconnect-timeout nil))
  (state/update-websocket-connection! false nil))

(defn send-message!
  "Send message through WebSocket"
  [message]
  (when-let [ws @websocket-atom]
    (when (= (.-readyState ws) js/WebSocket.OPEN)
      (let [json-str (utils/safe-json-stringify message)]
        (when json-str
          (.send ws json-str))))))
