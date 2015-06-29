(ns basic.tmp
  (:use   clojure.pprint
          tupelo.core)
  (:gen-class))
(newline)

(def cc '[1 2 (ff)] )
(println cc)
(println (eval `(let [~'ff (fn [] 9)] ~cc)))

(defn -main []
  (println "-main")
)
