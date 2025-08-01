(ns notify-system.web
  "Web server and routing for the notification system"
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :refer [response status]]
            [org.httpkit.server :as http-kit]
            [notify-system.service :as service]
            [notify-system.logging :as logging]
            [notify-system.db :as db]
            [clojure.data.json :as json]
            [cheshire.generate :as cheshire-gen]))

;; Conditionally require stress test namespace for development
(def stress-test-available?
  (try
    (require 'notify-system.stress-test)
    true
    (catch Exception _
      false)))

(def stress-test-ns 
  (when stress-test-available?
    (find-ns 'notify-system.stress-test)))

;; Configure Cheshire to handle java.time.Instant objects
(cheshire-gen/add-encoder java.time.Instant
                          (fn [instant jsonGenerator]
                            (.writeString jsonGenerator (.toString instant))))

;; Global state for WebSocket connections
(defonce websocket-connections (atom #{}))

(defn broadcast-to-websockets
  "Broadcast a message to all connected WebSocket clients"
  [message]
  (let [json-message (json/write-str message)]
    (doseq [channel @websocket-connections]
      (try
        (http-kit/send! channel json-message)
        (catch Exception e
          ;; Remove closed connections
          (swap! websocket-connections disj channel)
          (println "Removed closed WebSocket connection:" (.getMessage e)))))))

;; Controllers

(defn health-controller
  "Health check endpoint for production monitoring"
  [_request]
  (try
    (let [db-status (if (db/test-connection) "connected" "disconnected")
          ; More defensive approach to getting start time
          start-time-value (try
                             (when-let [start-time-var (resolve 'notify-system.main/start-time)]
                               (when-let [start-time-atom (var-get start-time-var)]
                                 @start-time-atom))
                             (catch Exception _ nil))
          uptime-ms (- (System/currentTimeMillis) 
                       (or start-time-value (System/currentTimeMillis)))
          uptime-hours (int (/ uptime-ms 3600000))
          uptime-minutes (int (/ (mod uptime-ms 3600000) 60000))
          uptime-seconds (int (/ (mod uptime-ms 60000) 1000))]
      (response {:status "healthy"
                 :timestamp (java.time.Instant/now)
                 :database db-status
                 :uptime (format "%dh %dm %ds" uptime-hours uptime-minutes uptime-seconds)
                 :version "1.0.0"}))
    (catch Exception e
      (-> (response {:status "unhealthy"
                     :error (.getMessage e)
                     :timestamp (java.time.Instant/now)})
          (status 503)))))

(defn metrics-controller
  "Prometheus metrics endpoint"
  [_request]
  (try
    (let [stats (logging/get-notification-statistics (logging/create-logging-service))
          metrics-text (str 
                        "# HELP notification_total Total number of notifications sent\n"
                        "# TYPE notification_total counter\n"
                        "notification_total " (:total-notifications stats) "\n\n"
                        
                        "# HELP notification_successful_total Total number of successful notifications\n"
                        "# TYPE notification_successful_total counter\n"
                        "notification_successful_total " (:successful-notifications stats) "\n\n"
                        
                        "# HELP notification_failed_total Total number of failed notifications\n"
                        "# TYPE notification_failed_total counter\n"
                        "notification_failed_total " (:failed-notifications stats) "\n\n"
                        
                        "# HELP application_uptime_seconds Application uptime in seconds\n"
                        "# TYPE application_uptime_seconds gauge\n"
                        "application_uptime_seconds " (int (/ (- (System/currentTimeMillis) 
                                                                 (try
                                                                   (when-let [start-time-var (resolve 'notify-system.main/start-time)]
                                                                     (when-let [start-time-atom (var-get start-time-var)]
                                                                       @start-time-atom))
                                                                   (catch Exception _ (System/currentTimeMillis)))) 1000)) "\n")]
      {:status 200
       :headers {"Content-Type" "text/plain; version=0.0.4; charset=utf-8"}
       :body metrics-text})
    (catch Exception e
      (-> (response {:error (.getMessage e)})
          (status 500)))))
(defn send-notification-controller
  "Controller for sending notifications"
  [request]
  (try
    (let [body (:body request)
          category (:category body)
          content (:message body)]
      
      ;; Validate input
      (when (or (empty? category) (empty? content))
        (throw (ex-info "Category and message are required" {:type :validation-error})))
      
      ;; Send notification
      (let [result (service/safe-send-notification category content)]
        
        ;; Broadcast to WebSocket clients for real-time updates
        (broadcast-to-websockets {:type "notification-sent" 
                                  :data result 
                                  :timestamp (.toString (java.time.Instant/now))})
        
        (response result)))
    (catch Exception e
      (-> (response {:error (.getMessage e) 
                     :type (or (:type (ex-data e)) :server-error)})
          (status 400)))))

(defn get-logs-controller
  "Controller for retrieving notification logs"
  [request]
  (try
    (let [query-params (:query-params request)
          page (Integer/parseInt (get query-params "page" "0"))
          limit (Integer/parseInt (get query-params "limit" "50"))
          offset (* page limit)
          status-filter (get query-params "status")
          category-filter (get query-params "category")
          user-id-filter (get query-params "user-id")
          
          ;; Get logs based on filters
          logs (cond
                 status-filter 
                 (logging/get-logs-by-status (logging/create-log-repository) status-filter)
                 
                 category-filter 
                 (logging/get-logs-by-category (logging/create-log-repository) category-filter)
                 
                 user-id-filter 
                 (logging/get-logs-by-user (logging/create-log-repository) 
                                           (java.util.UUID/fromString user-id-filter))
                 
                 :else 
                 (logging/get-logs-paginated (logging/create-log-repository) offset limit))
          
          formatted-logs (logging/format-logs logs)]
      
      (response {:logs formatted-logs
                 :pagination {:page page
                              :limit limit
                              :total (count logs)}}))
    (catch Exception e
      (-> (response {:error (.getMessage e)})
          (status 500)))))

(defn get-log-statistics-controller
  "Controller for retrieving log statistics"
  [_request]
  (try
    (let [stats (logging/get-notification-statistics (logging/create-logging-service))]
      (response stats))
    (catch Exception e
      (-> (response {:error (.getMessage e)})
          (status 500)))))

(defn get-categories-controller
  "Controller for retrieving available categories"
  [_request]
  (try
    (let [categories (db/get-all-categories)]
      (response {:categories (map :categories/name categories)}))
    (catch Exception e
      (-> (response {:error (.getMessage e)})
          (status 500)))))

(defn websocket-handler
  "WebSocket handler for real-time log updates"
  [request]
  (http-kit/as-channel request
    {:on-open (fn [ch]
                (swap! websocket-connections conj ch)
                (http-kit/send! ch (json/write-str {:type "connected" 
                                                   :message "WebSocket connected"})))
     :on-close (fn [ch status]
                 (swap! websocket-connections disj ch)
                 (println "WebSocket connection closed:" status))
     :on-receive (fn [ch data]
                   (try
                     (let [message (json/read-str data :key-fn keyword)]
                       (case (:type message)
                         "ping" (http-kit/send! ch (json/write-str {:type "pong"}))
                         "get-logs" (let [logs (logging/format-logs 
                                                (logging/get-logs (logging/create-log-repository)))]
                                      (http-kit/send! ch (json/write-str {:type "logs-update" 
                                                                          :data logs})))
                         (println "Unknown message type:" (:type message))))
                     (catch Exception e
                       (println "Error handling WebSocket message:" (.getMessage e)))))}))

;; Routes
(defroutes app-routes
  ;; Health and monitoring endpoints
  (GET "/health" [] health-controller)
  (GET "/metrics" [] metrics-controller)
  
  ;; API Routes
  (POST "/api/notifications" [] send-notification-controller)
  (GET "/api/logs" [] get-logs-controller)
  (GET "/api/logs/statistics" [] get-log-statistics-controller)
  (GET "/api/categories" [] get-categories-controller)
  
  ;; WebSocket endpoint
  (GET "/ws" [] websocket-handler)
  
  ;; Static files
  (route/resources "/")
  (route/files "/" {:root "resources/public"})
  
  ;; Default route - serve index.html for SPA
  (GET "*" [] 
       (fn [_request]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :body (slurp "resources/public/index.html")}))
  
  ;; 404 handler
  (route/not-found {:error "Not found"}))

;; Stress test routes (added separately if available)
(def stress-test-routes
  (when stress-test-available?
    (compojure.core/routes
      (POST "/api/stress-test/start" request 
            (try
              (println "Full request keys:" (keys request))
              (println "Request body:" (get request :body))
              (println "Request params:" (get request :params))
              (println "Request query-params:" (get request :query-params))
              (println "Request form-params:" (get request :form-params))
              (let [config (or (get-in request [:body]) 
                               (get-in request [:params])
                               (get-in request [:form-params])
                               {})]
                (println "Received stress test config:" config)
                (println "Config type:" (type config))
                (let [start-fn (ns-resolve stress-test-ns 'start-stress-test!)
                      result (start-fn config)]
                  (if result
                    (response {:status "started" :message "Stress test started successfully"})
                    (response {:status "error" :message "Stress test is already running"}))))
              (catch Exception e
                (println "Error starting stress test:" (.getMessage e))
                (-> (response {:status "error" :message (.getMessage e)})
                    (status 500)))))
      
      (POST "/api/stress-test/stop" []
            (try
              (let [stop-fn (ns-resolve stress-test-ns 'stop-stress-test!)
                    result (stop-fn)]
                (if result
                  (response {:status "stopped" :message "Stress test stopped successfully"})
                  (response {:status "info" :message "No stress test was running"})))
              (catch Exception e
                (-> (response {:status "error" :message (.getMessage e)})
                    (status 500)))))
      
      (GET "/api/stress-test/metrics" []
           (try
             (let [metrics-fn (ns-resolve stress-test-ns 'get-current-metrics)
                   metrics (metrics-fn)]
               (response (or metrics {:message "No stress test running"})))
             (catch Exception e
               (-> (response {:status "error" :message (.getMessage e)})
                   (status 500)))))
      
      (GET "/api/stress-test/report" []
           (try
             (let [report-fn (ns-resolve stress-test-ns 'generate-final-report!)
                   report (report-fn)]
               (response (or report {:message "No stress test data available"})))
             (catch Exception e
               (-> (response {:status "error" :message (.getMessage e)})
                   (status 500))))))))

;; Middleware stack
(def app
  (-> (if stress-test-routes
        (compojure.core/routes app-routes stress-test-routes)
        app-routes)
      (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-methods [:get :post :put :delete :options]
                :access-control-allow-headers ["Content-Type" "Authorization"])
      (wrap-json-body {:keywords? true})
      wrap-json-response))

;; Server management
(defonce server (atom nil))

(defn start-server!
  "Start the web server"
  ([] (start-server! 3000))
  ([port]
   (when @server
     (println "Stopping existing server...")
     (@server))
   
   ;; Initialize the broadcast callback for stress test if available
   (when stress-test-available?
     (let [set-broadcast-fn! (ns-resolve stress-test-ns 'set-broadcast-fn!)]
       (set-broadcast-fn! broadcast-to-websockets)))
   
   (println (str "Starting server on port " port "..."))
   (reset! server (http-kit/run-server app {:port port}))
   (println (str "Server started on http://localhost:" port))))

(defn stop-server!
  "Stop the web server"
  []
  (when @server
    (println "Stopping server...")
    (@server)
    (reset! server nil)
    (println "Server stopped")))

;; Development helpers
(defn restart-server!
  "Restart the server"
  ([] (restart-server! 3000))
  ([port]
   (stop-server!)
   (Thread/sleep 1000)
   (start-server! port)))

;; Logging integration - update service to use new logging system
(defn enhanced-send-notification
  "Enhanced notification sending with integrated logging"
  [category content]
  (try
    (db/init-db!)
    (let [service (service/create-notification-service)
          message {:category category :content content}
          result (service/send-message service message)]
      
      ;; Broadcast real-time update
      (broadcast-to-websockets {:type "notification-processed" 
                                :data result 
                                :timestamp (.toString (java.time.Instant/now))})
      
      result)
    (catch Exception e
      (println "Error in enhanced notification sending:" (.getMessage e))
      {:status "error" :message (.getMessage e)})))
