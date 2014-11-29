(ns helloworld.server
  (:gen-class)
  (:require [clojure.java.io                :as io]
            [io.pedestal.http               :as ped-http]
            [helloworld.service             :as service] 
            [taoensso.timbre                :as timbre]
            [taoensso.timbre.profiling      :as timbre-prof]
  ))

;--------------------------------------------------------------------------------
; Timbre config stuff

; Set up the name of the log output file and delete any contents from previous runs (the
; default is to continually append all runs to the file).
(def log-file-name "log.txt")
(io/delete-file log-file-name :quiet)

; The default setup is simple console logging only.  We wish to turn off console logging and
; turn on file logging to our chosen filename.
(timbre/set-config! [:appenders :standard-out   :enabled?] false)   
(timbre/set-config! [:appenders :spit           :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] log-file-name)

; end of Timbre stuff
;--------------------------------------------------------------------------------


(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env :dev
              ;; do not block thread that starts web server
              ::ped-http/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::ped-http/routes #(deref #'service/routes)
              ;; all origins are allowed in dev mode
              ::ped-http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      ped-http/default-interceptors
      ped-http/dev-interceptors
      ped-http/create-server
      ped-http/start))

;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call ped-http/start and ped-http/stop on this service
(defonce runnable-service (ped-http/create-server service/service))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (timbre/info  "\nCreating your server...")
  (println      "\nCreating your server...")
  (ped-http/start runnable-service))

