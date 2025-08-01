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
            [clojure.data.json :as json]))

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
  (GET "*" [] (slurp "resources/public/index.html"))
  
  ;; 404 handler
  (route/not-found {:error "Not found"}))

;; Middleware stack
(def app
  (-> app-routes
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
