(ns basic.core
  (:require [datomic.api      :as d] )
  (:use   clojure.pprint 
          cooljure.core )
  (:gen-class))

(def uri "datomic:mem://seattle")

(d/create-database uri)
(def conn (d/connect uri))
(def schema-tx (read-string (slurp "samples/seattle/seattle-schema.edn")))
@(d/transact conn schema-tx)
(def data-tx (read-string (slurp "samples/seattle/seattle-data0.edn")))
; (pprint (first data-tx))
; (pprint (second data-tx))
; (pprint (nth data-tx 2))
@(d/transact conn data-tx)
(def results (d/q '[:find ?c :where [?c :community/name]] (d/db conn) ))
(spyx
  (count results)
)

(defn -main []
  (println "main - enter")
  (shutdown-agents)
  (println "main - exit")
)
