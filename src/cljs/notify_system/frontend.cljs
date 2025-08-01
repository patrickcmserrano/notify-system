(ns notify-system.frontend
  "Notification system frontend - main entry point"
  (:require [notify-system.frontend.core :as core]))

(defn init
  "Initialize the frontend application"
  []
  (core/initialize!))

(defn ^:export main
  "Main entry point for the frontend application"
  []
  (init))

;; Auto-start on load  
(init)