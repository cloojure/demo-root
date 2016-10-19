(ns embedded-clojure.core
  (:require 
    [tupelo.core :as t]
  )
  (:gen-class))
(t/refer-tupelo)

(defn add [x y] (+ x y))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
