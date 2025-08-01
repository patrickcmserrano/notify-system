(ns notify-system.frontend.components
  "UI components for notification system"
  (:require [notify-system.frontend.state :as state]
            [notify-system.frontend.utils :as utils]
            [notify-system.frontend.events :as events]
            [notify-system.frontend.theme :as theme]))

;; Form components
(defn form-group
  "Reusable form group component"
  [label & content]
  [:div.form-group
   [:label label]
   (into [:div] content)])

(defn select-field
  "Select dropdown component"
  [value options on-change & {:keys [placeholder disabled]}]
  [:select.form-control
   {:value value
    :on-change #(on-change (utils/extract-target-value %))
    :disabled disabled}
   (when placeholder
     [:option {:value ""} placeholder])
   (for [option options]
     ^{:key option}
     [:option {:value option} option])])

(defn textarea-field
  "Textarea component"
  [value on-change & {:keys [placeholder rows disabled]}]
  [:textarea.form-control
   {:value value
    :on-change #(on-change (utils/extract-target-value %))
    :placeholder placeholder
    :rows (or rows 4)
    :disabled disabled}])

(defn button
  "Button component"
  [text on-click & {:keys [type disabled class]}]
  [:button
   {:type (or type "button")
    :class (str "btn " (or class "btn-primary"))
    :disabled disabled
    :on-click on-click}
   text])

(defn alert
  "Alert component"
  [message & {:keys [type]}]
  [:div.alert
   {:class (str "alert-" (or type "error"))}
   message])

;; Notification form
(defn notification-form
  "Form for sending notifications"
  []
  (let [state-data (state/get-state)
        current (:current-notification state-data)
        categories (:categories state-data)
        loading (:loading state-data)
        error (:error state-data)]
    [:div.notification-form
     [:h2 "Send Notification"]
     
     (when error
       [alert error :type "error"])
     
     [:form {:on-submit (fn [e]
                          (utils/prevent-default e)
                          (events/dispatch! :notification/send 
                                           (:category current) 
                                           (:message current)))}
      
      [form-group "Category:"
       [select-field (:category current) 
                     categories
                     #(events/dispatch! :notification/update-field :category %)
                     :placeholder "Select a category..."
                     :disabled loading]]
      
      [form-group "Message:"
       [textarea-field (:message current)
                       #(events/dispatch! :notification/update-field :message %)
                       :placeholder "Enter your notification message..."
                       :disabled loading]]
      
      [button (if loading "Sending..." "Send Notification")
              nil
              :type "submit"
              :disabled (or loading
                           (not (utils/not-blank? (:category current)))
                           (not (utils/not-blank? (:message current))))]]]))

;; Statistics panel
(defn stat-card
  "Individual statistic card"
  [title value & {:keys [class]}]
  [:div.stat-card
   {:class class}
   [:div.stat-number (str value)]
   [:div.stat-label title]])

(defn statistics-panel
  "Statistics overview panel"
  []
  (let [stats (state/get-statistics)]
    [:div.statistics-panel
     [:h3 "Statistics"]
     [:div.stats-grid
      [stat-card "Total" (:total_notifications stats)]
      [stat-card "Successful" (:successful stats) :class "success"]
      [stat-card "Failed" (:failed stats) :class "failed"]
      [stat-card "Pending" (:pending stats) :class "pending"]]]))

;; Connection status
(defn connection-status
  "WebSocket connection status indicator"
  []
  (let [connected (state/websocket-connected?)]
    [:div.connection-status
     [:span.status-indicator
      {:class (if connected "connected" "disconnected")}]
     (if connected "Connected" "Disconnected")]))

;; Theme toggle
(defn theme-toggle
  "Theme toggle button"
  []
  (let [current-theme (state/get-theme)]
    [:button.theme-toggle
     {:on-click theme/toggle-theme!}
     [:span (theme/get-theme-icon current-theme)]
     [:span (theme/get-theme-label current-theme)]]))

;; Navigation links
(defn nav-link
  "Navigation link component"
  [href text & {:keys [external icon]}]
  [:a.nav-link
   {:href href
    :target (when external "_blank")
    :rel (when external "noopener noreferrer")}
   (when icon [:span.nav-icon icon])
   text])

;; Header controls
(defn header-controls
  "Header control panel"
  []
  [:div.header-controls
   [:div.nav-links
    #_[nav-link "/stress-test.html" "Stress Test Dashboard" :external true :icon "🚀"]]
   [theme-toggle]
   [connection-status]])

;; Log components
(defn log-field
  "Individual log field display"
  [label value & {:keys [class]}]
  [:div.log-field
   {:class class}
   [:strong (str label " ")] value])

(defn log-entry
  "Individual log entry component"
  [log]
  [:div.log-entry
   [:div.log-header
    [:span.log-timestamp (utils/format-timestamp (:timestamp log))]
    [:span.log-status {:class (:status log)} (:status log)]]
   [:div.log-content
    [log-field "User:" (str (get-in log [:user :name]) " (" (get-in log [:user :email]) ")")]
    [log-field "Category:" (:category log)]
    [log-field "Channel:" (:channel log)]
    [log-field "Content:" (:content log)]
    (when (:error-message log)
      [log-field "Error:" (:error-message log) :class "error"])]])

(defn log-history
  "Log history panel"
  []
  (let [logs (state/get-logs)]
    [:div.log-history
     [:h2 "Log History"]
     [:div.log-count (str (count logs) " entries")]
     [:div.log-list
      (if (seq logs)
        (for [log logs]
          ^{:key (:id log)}
          [log-entry log])
        [:div.no-logs "No logs available"])]]))

;; Main layout
(defn main-app
  "Main application component"
  []
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
