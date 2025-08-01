(ns notify-system.frontend.theme
  "Theme management functionality"
  (:require [notify-system.frontend.state :as state]
            [notify-system.frontend.utils :as utils]))

(defn apply-theme!
  "Apply theme to document and save to localStorage"
  [theme]
  (let [html (.-documentElement js/document)]
    (if (= theme "dark")
      (.setAttribute html "data-theme" "dark")
      (.removeAttribute html "data-theme"))
    (utils/local-storage-set "theme" theme)
    (state/update-theme! theme)))

(defn toggle-theme!
  "Toggle between light and dark themes"
  []
  (let [current-theme (state/get-theme)
        new-theme (if (= current-theme "light") "dark" "light")]
    (apply-theme! new-theme)))

(defn initialize-theme!
  "Initialize theme from localStorage or default"
  []
  (let [saved-theme (utils/local-storage-get "theme" "light")]
    (apply-theme! saved-theme)))

(defn get-theme-icon
  "Get icon for current theme"
  [theme]
  (if (= theme "light") "🌙" "☀️"))

(defn get-theme-label
  "Get label for theme toggle"
  [theme]
  (if (= theme "light") "Dark" "Light"))
