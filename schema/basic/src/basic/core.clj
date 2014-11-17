(ns basic.core
  (:gen-class)
  (:require [clojure.string                         :as str]
            [clojure.test.check                     :as tc]
            [clojure.test.check.generators          :as gen]
            [clojure.test.check.properties          :as prop]
            [clojure.test.check.clojure-test        :as tst]
            [schema.core                            :as s] )
  (:use cooljure.core
        clojure.test))

(defn -main []
  (println "Hello, World!"))
