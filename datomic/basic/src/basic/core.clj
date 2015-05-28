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
(def db-val (d/db conn))
(spyxt db-val)
(def result-set (d/q '[:find ?c :where [?c :community/name]] db-val ))
(spyx (class result-set))
(spyx (count result-set))
(def idf   (first result-set))
(def id   (ffirst result-set))
(spyxt id)
(def entity (d/entity db-val id))
(spyxt entity)
(keys entity)
(spyx
  (:community/name entity)
)

(defn -main []
  (println "main - enter")
  (shutdown-agents)
)
