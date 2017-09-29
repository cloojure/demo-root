(ns embedded-clojure.core
  (:gen-class))

(defn add [x y] (+ x y))

(defn -main [& args]
  (println "Clojure -main:  (add 4 5) =>" (add 4 5)))
