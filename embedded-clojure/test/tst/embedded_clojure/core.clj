(ns tst.embedded-clojure.core
  (:use embedded-clojure.core
        clojure.test)
  (:require [tupelo.core :as t]
))
(t/refer-tupelo)

(deftest a-test
    (is= 5 (add 2 3)))
