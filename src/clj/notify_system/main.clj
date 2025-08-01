(ns notify-system.main
  "Main application entry point with web server"
  (:require [notify-system.web :as web]
            [notify-system.db :as db])
  (:gen-class))

;; Track application start time for health checks
(defonce start-time (atom nil))

(defn -main [& args]
  (println "=== Starting Notification System ===")
  
  ;; Record start time
  (reset! start-time (System/currentTimeMillis))
  
  ;; Initialize database
  (println "Initializing database...")
  (db/init-and-seed!)
  
  ;; Start web server
  (let [port (if (seq args) (Integer/parseInt (first args)) 3000)]
    (println (str "Starting web server on port " port "..."))
    (web/start-server! port)
    
    (println "=== Notification System Started ===")
    (println (str "Frontend: http://localhost:" port))
    (println (str "API: http://localhost:" port "/api"))
    (println (str "WebSocket: ws://localhost:" port "/ws"))
    (println (str "Health Check: http://localhost:" port "/health"))
    (println (str "Metrics: http://localhost:" port "/metrics"))
    (println "Press Ctrl+C to stop")))
