(ns notify-system.frontend.state
  "Application state management for notification system frontend"
  (:require [reagent.core :as r]))

;; Application state atom
(defonce app-state
  (r/atom {:categories []
           :logs []
           :current-notification {:category "" :message ""}
           :loading false
           :error nil
           :websocket nil
           :websocket-connected false
           :theme (or (js/localStorage.getItem "theme") "light")
           :statistics {:total_notifications 0
                        :successful 0
                        :failed 0
                        :pending 0}}))

;; State update functions
(defn reset-error!
  "Clear any error state"
  []
  (swap! app-state assoc :error nil))

(defn set-loading!
  "Set loading state"
  [loading?]
  (swap! app-state assoc :loading loading?))

(defn set-error!
  "Set error message"
  [error-msg]
  (swap! app-state assoc :error error-msg))

(defn update-categories!
  "Update categories list"
  [categories]
  (swap! app-state assoc :categories categories))

(defn update-logs!
  "Update logs list"
  [logs]
  (swap! app-state assoc :logs logs))

(defn update-statistics!
  "Update statistics"
  [statistics]
  (swap! app-state assoc :statistics statistics))

(defn update-websocket-connection!
  "Update WebSocket connection status"
  [connected? websocket]
  (swap! app-state assoc 
         :websocket-connected connected?
         :websocket websocket))

(defn update-theme!
  "Update theme setting"
  [theme]
  (swap! app-state assoc :theme theme))

(defn update-notification-field!
  "Update a field in current notification"
  [field value]
  (swap! app-state assoc-in [:current-notification field] value))

(defn reset-notification!
  "Reset current notification form"
  []
  (swap! app-state assoc :current-notification {:category "" :message ""}))

(defn set-notification-loading!
  "Set loading state for notification sending"
  [loading? & [error]]
  (swap! app-state assoc 
         :loading loading?
         :error error))

;; State accessors
(defn get-state
  "Get current application state"
  []
  @app-state)

(defn get-categories
  "Get categories list"
  []
  (:categories @app-state))

(defn get-logs
  "Get logs list"
  []
  (:logs @app-state))

(defn get-statistics
  "Get statistics"
  []
  (:statistics @app-state))

(defn get-current-notification
  "Get current notification form data"
  []
  (:current-notification @app-state))

(defn get-theme
  "Get current theme"
  []
  (:theme @app-state))

(defn loading?
  "Check if app is in loading state"
  []
  (:loading @app-state))

(defn has-error?
  "Check if there's an error"
  []
  (some? (:error @app-state)))

(defn get-error
  "Get current error message"
  []
  (:error @app-state))

(defn websocket-connected?
  "Check if WebSocket is connected"
  []
  (:websocket-connected @app-state))

(defn get-websocket
  "Get WebSocket instance"
  []
  (:websocket @app-state))
