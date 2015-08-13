(ns helloworld.service-test
  (:require [clojure.test           :refer :all]
            [tupelo.core            :refer :all ]
            [io.pedestal.test       :refer :all]
            [io.pedestal.http       :as ped-http]
            [helloworld.service     :as service]))

(def service
  (::ped-http/service-fn (ped-http/create-servlet service/service)))

(deftest home-page-test
  (is (=
       (:body (response-for service :get "/"))
       "Hello World!"))
  (is (= (:headers (response-for service :get "/"))
         {"Content-Type" "text/html;charset=UTF-8"
          "Strict-Transport-Security" "max-age=31536000; includeSubdomains"
          "X-Frame-Options" "DENY"
          "X-Content-Type-Options" "nosniff"
          "X-XSS-Protection" "1; mode=block"})))


(deftest about-page-test
  (is (re-find #"Clojure 1.8.0"
       (spyx
         (:body (response-for service :get "/about")))))
  (is (= (:headers (response-for service :get "/about"))
         {"Content-Type" "text/html;charset=UTF-8"
          "Strict-Transport-Security" "max-age=31536000; includeSubdomains"
          "X-Frame-Options" "DENY"
          "X-Content-Type-Options" "nosniff"
          "X-XSS-Protection" "1; mode=block"})))

