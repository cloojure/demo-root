(ns tst.embedded-clojure.core
  (:use embedded-clojure.core
        clojure.test)
)

(deftest a-test
  (is (= 5 (add 2 3))))
