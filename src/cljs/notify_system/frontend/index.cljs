;; Notification System Frontend Module Organization
;; ===============================================
;;
;; This module provides a notification system frontend with real-time updates,
;; theme management, and comprehensive logging capabilities. The architecture
;; follows clean separation of concerns with the following organization:

(ns notify-system.frontend.index
  "Frontend module index and documentation"
  (:require [notify-system.frontend.core :as core]
            [notify-system.frontend.events :as events]))

;; ## Namespace Organization
;;
;; ### Core Layer (`core.cljs`)
;; - Application initialization and lifecycle management
;; - React root management and rendering
;; - Resource cleanup and shutdown procedures
;;
;; ### State Management (`state.cljs`)
;; - Centralized application state using Reagent atoms
;; - State update functions with proper encapsulation
;; - Read-only accessors for state data
;;
;; ### Event System (`events.cljs`)
;; - Event-driven architecture using multimethod dispatch
;; - Decouples UI components from business logic
;; - Handles data loading, form submissions, and UI interactions
;;
;; ### Communication Layer
;; - `websocket.cljs`: Real-time WebSocket communication with auto-reconnect
;; - `api.cljs`: HTTP API client for REST endpoints
;;
;; ### Presentation Layer
;; - `components.cljs`: Reagent UI components with reusable design
;; - `theme.cljs`: Theme management and persistence
;;
;; ### Utilities (`utils.cljs`)
;; - Common utility functions for formatting, validation, and DOM manipulation
;; - Safe JSON parsing and localStorage access
;; - Debounce and throttle utilities

;; ## Public API

(defn initialize!
  "Initialize the frontend application"
  []
  (core/initialize!))

(defn shutdown!
  "Shutdown the application and clean up resources"
  []
  (core/shutdown!))

(defn dispatch-event!
  "Dispatch an event through the event system"
  [event-type & args]
  (apply events/dispatch! event-type args))

;; ## Event Types Available
;; 
;; ### Data Events
;; - `:data/load-categories` - Load notification categories
;; - `:data/load-logs` - Load notification logs
;; - `:data/load-statistics` - Load statistics
;; - `:data/refresh-all` - Refresh logs and statistics
;;
;; ### Notification Events  
;; - `:notification/send` - Send a notification
;; - `:notification/update-field` - Update form field
;; - `:notification/reset` - Reset notification form
;;
;; ### UI Events
;; - `:ui/clear-error` - Clear error state
;; - `:ui/set-loading` - Set loading state
;;
;; ### WebSocket Events
;; - `:websocket/data-update` - Handle real-time data updates

;; ## Module Status
;; - ✅ Core functionality implemented
;; - ✅ Event-driven architecture with multimethod dispatch
;; - ✅ State management with proper encapsulation
;; - ✅ Real-time WebSocket communication with auto-reconnect
;; - ✅ HTTP API integration with error handling
;; - ✅ Theme management with persistence
;; - ✅ Responsive UI components
;; - ✅ Comprehensive utility functions
