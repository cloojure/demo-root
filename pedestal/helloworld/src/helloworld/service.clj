(ns helloworld.service
  (:require [io.pedestal.http                         :as ped-http]
            [io.pedestal.http.body-params             :as body-params]
            [io.pedestal.interceptor                  :as interceptor]
            [io.pedestal.http.route                   :as route]
            [io.pedestal.http.route.definition        :as route-def]
            [ring.util.response                       :as ring-resp]
            [taoensso.timbre                          :as timbre]
            [taoensso.timbre.profiling                :as timbre-prof]
          ; [helloworld.peer                          :as peer]
            )
  (:import [java.io ByteArrayOutputStream PrintWriter] ))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))
(defn error-page
  [request]
  (ring-resp/response (str "The secret value is: " (/ 1 0))))

(defn home-page
  [request]
  (timbre/info  "home-page: request=" request)
  (ring-resp/response "Hello World!" ))
    ; (timbre/spy :info "home-page result:" (peer/results) )

(interceptor/defmiddleware log-it 
  ([req]    (do (timbre/info  "#awt log-it enter: " req)
                req))
  ([resp]   (do (timbre/info  "#awt log-it exit: " resp)
                resp)))

(def catch-errors 
  (interceptor/interceptor 
      :error (fn [ctx excp]  
                (timbre/info  "#awt catch-excps ctx"    ctx)  
                (timbre/info  "#awt catch-excps excp"   excp)  
                (let [baos  (ByteArrayOutputStream.)
                      pw    (PrintWriter. baos)
                      *     (.fillInStackTrace excp)
                      *     (.printStackTrace excp pw)
                      *     (.flush pw)
                      err-str (.toString baos) ]
                  (timbre/error "catch-errors err-str" err-str )
                  (assoc ctx
                    :response { :status 500
                                :body  (str "Exception: " (.toString excp) \newline 
                                            "stacktrace: " err-str  ) } )))
  ))

(route-def/defroutes routes
  [[[ "/"   ^:interceptors [log-it catch-errors]
            {:get home-page}

      ; Set default interceptors for /about and any other paths under /
      ^:interceptors [(body-params/body-params) ped-http/html-body]
      [ "/about" {:get about-page} ]
      [ "/error" {:get error-page} ]

    ]]] )

; Consumed by helloworld.server/runnable-service
; See ped-http/default-interceptors for additional options you can configure
(def service {:env :prod
              ; You can bring your own non-default interceptors. Make sure you include
              ; routing and set it up right for dev-mode. If you do, many other keys for
              ; configuring default interceptors will be ignored.
              ; :ped-http/interceptors []
              ::ped-http/routes routes

              ; Uncomment next line to enable CORS support, add string(s) specifying
              ; scheme, host and port for allowed source(s):
              ;
              ; "http://localhost:8080"
              ;
              ;::ped-http/allowed-origins ["scheme://host:port"]

              ; Root for resource interceptor that is available by default.
              ::ped-http/resource-path "/public"

              ; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::ped-http/type :jetty
              ;::ped-http/host "localhost"
              ::ped-http/port 8080})

