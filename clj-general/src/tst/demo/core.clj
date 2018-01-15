(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require 
    [tupelo.string :as ts] ))

(dotest
  (is= (spyx (+ 2 3)))
  (throws? (/ 5 0))
  (isnt false)
)

