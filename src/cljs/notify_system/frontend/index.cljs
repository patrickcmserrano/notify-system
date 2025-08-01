;; Frontend entry point and module coordination
(ns notify-system.frontend.index
  "Main frontend module - handles app initialization and event coordination"
  (:require [notify-system.frontend.core :as core]
            [notify-system.frontend.events :as events]))

;; Module structure:
;; core.cljs - app lifecycle, react setup
;; state.cljs - app state management 
;; events.cljs - event handling with multimethods
;; websocket.cljs - real-time updates
;; api.cljs - http client
;; components.cljs - ui components
;; theme.cljs - theme switching
;; utils.cljs - helper functions

(defn initialize!
  "Start the app"
  []
  (core/initialize!))

(defn shutdown!
  "Stop the app and cleanup"
  []
  (core/shutdown!))

(defn dispatch-event!
  "Send event to event system"
  [event-type & args]
  (apply events/dispatch! event-type args))

;; Available events:
;; :data/load-categories, :data/load-logs, :data/load-statistics, :data/refresh-all
;; :notification/send, :notification/update-field, :notification/reset  
;; :ui/clear-error, :ui/set-loading
;; :websocket/data-update
