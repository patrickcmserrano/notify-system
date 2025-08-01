(ns notify-system.frontend
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom-client]
            [clojure.string :as str]))

;; Application state
(defonce app-state
  (r/atom {:categories []
           :logs []
           :current-notification {:category "" :message ""}
           :loading false
           :error nil
           :websocket nil
           :websocket-connected false
           :theme (or (js/localStorage.getItem "theme") "light")
           :statistics {:total-notifications 0
                        :successful 0
                        :failed 0
                        :pending 0}}))

;; Forward declarations
(declare load-logs!)
(declare load-statistics!)

;; Theme management
(defn apply-theme! [theme]
  (let [html (.-documentElement js/document)]
    (if (= theme "dark")
      (.setAttribute html "data-theme" "dark")
      (.removeAttribute html "data-theme"))
    (js/localStorage.setItem "theme" theme)))

(defn toggle-theme! []
  (let [current-theme (:theme @app-state)
        new-theme (if (= current-theme "light") "dark" "light")]
    (swap! app-state assoc :theme new-theme)
    (apply-theme! new-theme)))

;; WebSocket connection management
(defn init-websocket! []
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        ws-url (str protocol "//" (.-host js/location) "/ws")]
    (try
      (let [ws (js/WebSocket. ws-url)]
        (set! (.-onopen ws) 
              (fn [_] 
                (js/console.log "WebSocket connected")
                (swap! app-state assoc :websocket-connected true)))
        (set! (.-onmessage ws) 
              (fn [event]
                (try
                  (let [data (js->clj (js/JSON.parse (.-data event)) :keywordize-keys true)]
                    (js/console.log "WebSocket message received:" data)
                    (when (= (:type data) "log-update")
                      (load-logs!)
                      (load-statistics!)))
                  (catch js/Error e
                    (js/console.error "Error parsing WebSocket message:" e)))))
        (set! (.-onclose ws) 
              (fn [_] 
                (js/console.log "WebSocket disconnected")
                (swap! app-state assoc :websocket-connected false)
                ;; Attempt to reconnect after 3 seconds
                (js/setTimeout init-websocket! 3000)))
        (set! (.-onerror ws) 
              (fn [error] 
                (js/console.error "WebSocket error:" error)))
        (swap! app-state assoc :websocket ws))
      (catch js/Error e
        (js/console.error "Failed to create WebSocket connection:" e)))))

;; API calls using fetch
(defn fetch-json [url options]
  (-> (js/fetch url (clj->js options))
      (.then (fn [response]
               (if (.-ok response)
                 (.json response)
                 (throw (js/Error. (str "HTTP " (.-status response)))))))
      (.then (fn [data]
               (js->clj data :keywordize-keys true)))))

(defn load-categories! []
  (-> (fetch-json "/api/categories" {:method "GET"})
      (.then (fn [response]
               (swap! app-state assoc :categories (:categories response))))
      (.catch (fn [error]
                (js/console.error "Error loading categories:" error)))))

(defn load-logs! []
  (-> (fetch-json "/api/logs" {:method "GET"})
      (.then (fn [response]
               (swap! app-state assoc :logs (:logs response))))
      (.catch (fn [error]
                (js/console.error "Error loading logs:" error)))))

(defn load-statistics! []
  (-> (fetch-json "/api/logs/statistics" {:method "GET"})
      (.then (fn [response]
               (swap! app-state assoc :statistics response)))
      (.catch (fn [error]
                (js/console.error "Error loading statistics:" error)))))

(defn send-notification! [category message]
  (swap! app-state assoc :loading true :error nil)
  (-> (fetch-json "/api/notifications" 
                  {:method "POST"
                   :headers {"Content-Type" "application/json"}
                   :body (js/JSON.stringify (clj->js {:category category :message message}))})
      (.then (fn [response]
               (swap! app-state assoc 
                      :loading false
                      :current-notification {:category "" :message ""})
               (load-logs!)
               (load-statistics!)
               (js/console.log "Notification sent:" response)))
      (.catch (fn [error]
                (swap! app-state assoc 
                       :loading false
                       :error (or (.-message error) "Failed to send notification"))
                (js/console.error "Error sending notification:" error)))))

;; Components
(defn notification-form []
  (let [current (:current-notification @app-state)
        categories (:categories @app-state)
        loading (:loading @app-state)
        error (:error @app-state)]
    [:div.notification-form
     [:h2 "Send Notification"]
     
     (when error
       [:div.alert.alert-error error])
     
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (when (and (not (str/blank? (:category current)))
                                     (not (str/blank? (:message current))))
                            (send-notification! (:category current) (:message current))))}
      
      [:div.form-group
       [:label "Category:"]
       [:select.form-control
        {:value (:category current)
         :on-change (fn [e]
                      (swap! app-state assoc-in [:current-notification :category] (.-value (.-target e))))}
        [:option {:value ""} "Select a category..."]
        (for [category categories]
          ^{:key category}
          [:option {:value category} category])]]
      
      [:div.form-group
       [:label "Message:"]
       [:textarea.form-control
        {:value (:message current)
         :on-change (fn [e]
                      (swap! app-state assoc-in [:current-notification :message] (.-value (.-target e))))
         :placeholder "Enter your notification message..."
         :rows 4}]]
      
      [:button.btn.btn-primary
       {:type "submit"
        :disabled (or loading
                      (str/blank? (:category current))
                      (str/blank? (:message current)))}
       (if loading "Sending..." "Send Notification")]]]))

(defn statistics-panel []
  (let [stats (:statistics @app-state)]
    [:div.statistics-panel
     [:h3 "Statistics"]
     [:div.stats-grid
      [:div.stat-card
       [:div.stat-number (:total-notifications stats)]
       [:div.stat-label "Total"]]
      [:div.stat-card.success
       [:div.stat-number (:successful stats)]
       [:div.stat-label "Successful"]]
      [:div.stat-card.failed
       [:div.stat-number (:failed stats)]
       [:div.stat-label "Failed"]]
      [:div.stat-card.pending
       [:div.stat-number (:pending stats)]
       [:div.stat-label "Pending"]]]]))

(defn connection-status []
  (let [connected (:websocket-connected @app-state)]
    [:div.connection-status
     [:span.status-indicator
      {:class (if connected "connected" "disconnected")}]
     (if connected "Connected" "Disconnected")]))

(defn theme-toggle []
  (let [theme (:theme @app-state)]
    [:button.theme-toggle
     {:on-click toggle-theme!}
     [:span (if (= theme "light") "🌙" "☀️")]
     [:span (if (= theme "light") "Dark" "Light")]]))

(defn header-controls []
  [:div.header-controls
   [theme-toggle]
   [connection-status]])

(defn log-entry [log]
  [:div.log-entry {:key (:id log)}
   [:div.log-header
    [:span.log-timestamp (.toLocaleString (js/Date. (:timestamp log)))]
    [:span.log-status {:class (:status log)} (:status log)]]
   [:div.log-content
    [:div.log-field
     [:strong "User: "] (str (get-in log [:user :name]) " (" (get-in log [:user :email]) ")")]
    [:div.log-field
     [:strong "Category: "] (:category log)]
    [:div.log-field
     [:strong "Channel: "] (:channel log)]
    [:div.log-field
     [:strong "Content: "] (:content log)]
    (when (:error-message log)
      [:div.log-field.error
       [:strong "Error: "] (:error-message log)])]])

(defn log-history []
  (let [logs (:logs @app-state)]
    [:div.log-history
     [:h2 "Log History"]
     [:div.log-count (str (count logs) " entries")]
     [:div.log-list
      (if (seq logs)
        (for [log logs]
          ^{:key (:id log)}
          [log-entry log])
        [:div.no-logs "No logs available"])]]))

(defn main-app []
  [:div.app
   [:header.app-header
    [:h1 "Notification System"]
    [header-controls]]
   
   [:div.app-content
    [:div.left-panel
     [notification-form]
     [statistics-panel]]
    
    [:div.right-panel
     [log-history]]]])

;; React root for React 18 compatibility
(defonce react-root (atom nil))

;; Initialization
(defn init []
  ;; Apply saved theme
  (apply-theme! (:theme @app-state))
  ;; Initialize app
  (init-websocket!)
  (load-categories!)
  (load-logs!)
  (load-statistics!)
  ;; Use React 18 createRoot API
  (when-not @react-root
    (reset! react-root (rdom-client/create-root (.getElementById js/document "app"))))
  (rdom-client/render @react-root [main-app]))

;; Export for Shadow CLJS
(defn ^:export main []
  (init))
