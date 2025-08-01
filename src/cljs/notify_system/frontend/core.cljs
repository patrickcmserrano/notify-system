(ns notify-system.frontend.core
  "Core application lifecycle management"
  (:require [reagent.dom.client :as rdom-client]
            [notify-system.frontend.websocket :as websocket]
            [notify-system.frontend.theme :as theme]
            [notify-system.frontend.events :as events]
            [notify-system.frontend.components :as components]))

;; React root for React 18 compatibility
(defonce react-root (atom nil))

(defn initialize!
  "Initialize the notification system frontend"
  []
  (js/console.log "Initializing Notification System Frontend")
  
  ;; Initialize theme
  (theme/initialize-theme!)
  
  ;; Initialize event listeners
  (events/initialize-event-listeners!)
  
  ;; Load initial data
  (events/dispatch! :data/load-categories)
  (events/dispatch! :data/load-logs)
  (events/dispatch! :data/load-statistics)
  
  ;; Initialize WebSocket connection
  (websocket/connect-websocket!)
  
  ;; Initialize React root and render
  (when-not @react-root
    (reset! react-root (rdom-client/create-root (.getElementById js/document "app"))))
  (rdom-client/render @react-root [components/main-app]))

(defn shutdown!
  "Shutdown the application and clean up resources"
  []
  (js/console.log "Shutting down Notification System Frontend")
  
  ;; Disconnect WebSocket
  (websocket/disconnect-websocket!)
  
  ;; Clear React root
  (when @react-root
    (.unmount @react-root)
    (reset! react-root nil)))
