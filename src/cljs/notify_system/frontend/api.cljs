(ns notify-system.frontend.api
  "HTTP API client for notification system"
  (:require [notify-system.frontend.utils :as utils]))

(defn fetch-json
  "Fetch JSON data with error handling"
  [url options]
  (-> (js/fetch url (clj->js options))
      (.then (fn [response]
               (if (.-ok response)
                 (.json response)
                 (throw (js/Error. (str "HTTP " (.-status response)))))))
      (.then (fn [data]
               (js->clj data :keywordize-keys true)))))

(defn get-categories
  "Fetch categories from API"
  []
  (-> (fetch-json "/api/categories" {:method "GET"})
      (.then (fn [response]
               (:categories response)))
      (.catch (fn [error]
                (utils/log-error "Error loading categories" error)
                (throw error)))))

(defn get-logs
  "Fetch logs from API"
  []
  (-> (fetch-json "/api/logs" {:method "GET"})
      (.then (fn [response]
               (:logs response)))
      (.catch (fn [error]
                (utils/log-error "Error loading logs" error)
                (throw error)))))

(defn get-statistics
  "Fetch statistics from API"
  []
  (-> (fetch-json "/api/logs/statistics" {:method "GET"})
      (.catch (fn [error]
                (utils/log-error "Error loading statistics" error)
                (throw error)))))

(defn send-notification
  "Send notification via API"
  [category message]
  (let [payload {:category category :message message}
        body (utils/safe-json-stringify payload)]
    (-> (fetch-json "/api/notifications"
                    {:method "POST"
                     :headers {"Content-Type" "application/json"}
                     :body body})
        (.then (fn [response]
                 (utils/log-info "Notification sent:" response)
                 response))
        (.catch (fn [error]
                  (utils/log-error "Error sending notification" error)
                  (throw error))))))

(defn health-check
  "Check API health"
  []
  (-> (fetch-json "/api/health" {:method "GET"})
      (.catch (fn [error]
                (utils/log-error "Health check failed" error)
                (throw error)))))
