(ns tst.embedded-clojure.core
  (:use embedded-clojure.core
        clojure.test))

(deftest simple-add
  (is (= 13 (add 6 7))))
