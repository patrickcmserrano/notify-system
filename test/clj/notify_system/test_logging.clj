(ns notify-system.test-logging
  "Professional logging utilities for comprehensive test execution tracking.
   Provides structured logging for test verification and delivery audit trails."
  (:require [clojure.string :as str]))

(defn log-test-start 
  "Logs the beginning of a test with comprehensive metadata"
  [test-name description & {:keys [metadata]}]
  (let [timestamp (java.time.Instant/now)
        separator (str/join "" (repeat 80 "="))]
    (println separator)
    (println (format "[TEST-START] %s - %s" test-name description))
    (println (format "[TEST-TIMESTAMP] Started at: %s" timestamp))
    (when metadata
      (println (format "[TEST-METADATA] %s" metadata)))
    (println separator)))

(defn log-test-success 
  "Logs successful test completion with timing and validation details"
  [test-name & {:keys [duration assertions-count metadata]}]
  (let [timestamp (java.time.Instant/now)]
    (println (format "[TEST-SUCCESS] %s - Completed successfully" test-name))
    (println (format "[TEST-TIMESTAMP] Finished at: %s" timestamp))
    (when duration
      (println (format "[TEST-PERFORMANCE] Execution time: %d ms" duration)))
    (when assertions-count
      (println (format "[TEST-VALIDATION] Assertions verified: %d" assertions-count)))
    (when metadata
      (println (format "[TEST-METADATA] %s" metadata)))
    (println "")))

(defn log-test-failure 
  "Logs test failure with diagnostic information"
  [test-name error & {:keys [metadata]}]
  (let [timestamp (java.time.Instant/now)]
    (println (format "[TEST-FAILURE] %s - Test failed" test-name))
    (println (format "[TEST-TIMESTAMP] Failed at: %s" timestamp))
    (println (format "[TEST-ERROR] Error details: %s" error))
    (when metadata
      (println (format "[TEST-METADATA] %s" metadata)))
    (println "")))

(defn log-notification-delivery 
  "Logs notification delivery attempt with comprehensive audit information"
  [& {:keys [message-type notification-type user-data timestamp channel status error metadata]}]
  (println "[NOTIFICATION-DELIVERY] Notification delivery event")
  (println (format "[DELIVERY-MESSAGE-TYPE] %s" message-type))
  (println (format "[DELIVERY-NOTIFICATION-TYPE] %s" notification-type))
  (println (format "[DELIVERY-USER-DATA] %s" user-data))
  (println (format "[DELIVERY-TIMESTAMP] %s" (or timestamp (java.time.Instant/now))))
  (println (format "[DELIVERY-CHANNEL] %s" channel))
  (println (format "[DELIVERY-STATUS] %s" status))
  (when error
    (println (format "[DELIVERY-ERROR] %s" error)))
  (when metadata
    (println (format "[DELIVERY-METADATA] %s" metadata))))

(defn log-database-operation 
  "Logs database operations with performance metrics"
  [operation table & {:keys [duration record-count metadata]}]
  (println (format "[DB-OPERATION] %s on table: %s" operation table))
  (println (format "[DB-TIMESTAMP] %s" (java.time.Instant/now)))
  (when duration
    (println (format "[DB-PERFORMANCE] Operation time: %d ms" duration)))
  (when record-count
    (println (format "[DB-RECORD-COUNT] Records affected: %d" record-count)))
  (when metadata
    (println (format "[DB-METADATA] %s" metadata))))

(defn log-validation-result 
  "Logs validation results with detailed feedback"
  [validation-type input expected-result actual-result & {:keys [metadata]}]
  (println (format "[VALIDATION] %s validation" validation-type))
  (println (format "[VALIDATION-INPUT] %s" input))
  (println (format "[VALIDATION-EXPECTED] %s" expected-result))
  (println (format "[VALIDATION-ACTUAL] %s" actual-result))
  (println (format "[VALIDATION-STATUS] %s" (if (= expected-result actual-result) "PASSED" "FAILED")))
  (when metadata
    (println (format "[VALIDATION-METADATA] %s" metadata))))

(defn log-service-call 
  "Logs service method calls with parameters and results"
  [service-name method-name params result & {:keys [duration metadata]}]
  (println (format "[SERVICE-CALL] %s.%s" service-name method-name))
  (println (format "[SERVICE-PARAMS] %s" params))
  (println (format "[SERVICE-RESULT] %s" result))
  (println (format "[SERVICE-TIMESTAMP] %s" (java.time.Instant/now)))
  (when duration
    (println (format "[SERVICE-PERFORMANCE] Call duration: %d ms" duration)))
  (when metadata
    (println (format "[SERVICE-METADATA] %s" metadata))))

(defn log-channel-operation 
  "Logs notification channel operations with delivery details"
  [channel-name operation user message result & {:keys [duration metadata]}]
  (println (format "[CHANNEL-OPERATION] %s - %s" channel-name operation))
  (println (format "[CHANNEL-USER] %s" user))
  (println (format "[CHANNEL-MESSAGE] %s" message))
  (println (format "[CHANNEL-RESULT] %s" result))
  (println (format "[CHANNEL-TIMESTAMP] %s" (java.time.Instant/now)))
  (when duration
    (println (format "[CHANNEL-PERFORMANCE] Operation time: %d ms" duration)))
  (when metadata
    (println (format "[CHANNEL-METADATA] %s" metadata))))

(defn log-suite-summary 
  "Logs comprehensive test suite execution summary"
  [suite-name results & {:keys [total-duration metadata]}]
  (let [timestamp (java.time.Instant/now)
        separator (str/join "" (repeat 100 "="))]
    (println separator)
    (println (format "[SUITE-SUMMARY] %s - Execution Complete" suite-name))
    (println (format "[SUITE-TIMESTAMP] Completed at: %s" timestamp))
    (println (format "[SUITE-RESULTS] Tests: %d, Passed: %d, Failed: %d, Errors: %d" 
                     (:test results 0) (:pass results 0) (:fail results 0) (:error results 0)))
    (when total-duration
      (println (format "[SUITE-PERFORMANCE] Total execution time: %d ms" total-duration))
      (println (format "[SUITE-PERFORMANCE] Average time per test: %.2f ms" 
                       (double (/ total-duration (max (:test results) 1))))))
    (when metadata
      (println (format "[SUITE-METADATA] %s" metadata)))
    (println separator)))

(defmacro with-test-logging 
  "Macro to wrap test execution with comprehensive logging"
  [test-name description & body]
  `(let [start-time# (System/currentTimeMillis)]
     (log-test-start ~test-name ~description)
     (try
       (let [result# (do ~@body)
             end-time# (System/currentTimeMillis)
             duration# (- end-time# start-time#)]
         (log-test-success ~test-name :duration duration#)
         result#)
       (catch Exception e#
         (let [end-time# (System/currentTimeMillis)
               duration# (- end-time# start-time#)]
           (log-test-failure ~test-name (.getMessage e#) :metadata {:duration duration#})
           (throw e#))))))

(defmacro with-performance-logging 
  "Macro to wrap operations with performance logging"
  [operation-name & body]
  `(let [start-time# (System/currentTimeMillis)]
     (println (format "[PERFORMANCE-START] %s - Operation started" ~operation-name))
     (let [result# (do ~@body)
           end-time# (System/currentTimeMillis)
           duration# (- end-time# start-time#)]
       (println (format "[PERFORMANCE-END] %s - Completed in %d ms" ~operation-name duration#))
       result#)))
