(require '[notify-system.db :as db])
(require '[migratus.core :as migratus])

(println "=== Database Configuration Debug ===")
(println "migration-config:" db/migration-config)

(try
  (println "Testing migrations...")
  (migratus/migrate db/migration-config)
  (println "Migrations successful!")
  (catch Exception e
    (println "ERROR: Migration failed:" (.getMessage e))
    (.printStackTrace e)))
