(ns basic.ex
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))


(def uri "datomic:mem://example")

(d/create-database uri)
(def conn (d/connect uri))

(def partition-tx (read-string (slurp "ex-partition.edn")))
@(d/transact conn partition-tx)

(def schema-tx (read-string (slurp "ex-schema.edn")))
@(d/transact conn schema-tx)

(def data-tx
  [
    {:db/id #db/id[:people -007]  :person/name      "James Bond"}
    {:db/id #db/id[:people -007]  :person/ssn-usa   "123-45-6789"}
    {:db/id #db/id[:people -007]  :person/ssn-uk    "123-45-6789"}

    {:db/id #db/id[:people -666]  :person/name      "Mephistopheles"}
    {:db/id #db/id[:people -666]  :person/ssn-hell  "123-45-6789"}
  ] )
@(d/transact conn data-tx)
(def db-val (d/db conn))
(spyxx db-val)

(def res-1 (d/q '{:find [?c]
                  :where [ [?c :person/name] 
                         ] 
                 }
                db-val ))
(spyx (count res-1))
(spyxx res-1)

(doseq [it res-1]
  (let [eid     (spyxx (first it))
        entity  (d/touch (d/entity db-val eid)) 
        map-val (into (sorted-map) entity)
       ]
    (newline)
    (spyxx entity)
    (pprint map-val)
  ))

(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
