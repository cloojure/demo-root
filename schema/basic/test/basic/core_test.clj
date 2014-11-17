(ns basic.core-test
  (:require [clojure.string                         :as str]
            [clojure.test.check                     :as tc]
            [clojure.test.check.generators          :as gen]
            [clojure.test.check.properties          :as prop]
            [clojure.test.check.clojure-test        :as tst]
            [schema.core                            :as s] )
  (:use cooljure.core
        clojure.test)
  (:gen-class))

(def SetOfStr
  #{ s/Str } )

(deftest t1 []
  (= (s/validate SetOfStr   #{ "a" "b" "c"} )
                            #{ "a" "b" "c"} )
  (is (thrown? Exception    (s/validate SetOfStr #{ 1 "a" "b" "c"} )))
)

