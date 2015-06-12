(ns basic.tmp
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))
(newline)

(def result
  (cond-> #{}
    true    (conj 1)
    false   (conj 2)
    :a      (conj :a)))
(spyx result)

(newline)
(defn -main []
)
