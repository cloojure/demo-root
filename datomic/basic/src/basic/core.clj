(ns basic.core
  (:require [datomic.api      :as d] 
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s] )
  (:use   clojure.pprint
          cooljure.core 
  )
  (import [java.util Set Map List])
  (:gen-class))

(s/validate {s/Any s/Any} (into (sorted-map) {:a 1 :b 2}))

(def Eid            (s/one Long "entity-id"))
(def ResultSet      #{ [s/Any] } )
(def EidResultSet   #{ [ Eid ] } )

(def uri "datomic:mem://seattle")

(d/create-database uri)
(def conn (d/connect uri))
(def schema-tx (read-string (slurp "samples/seattle/seattle-schema.edn")))
@(d/transact conn schema-tx)
(def data-tx (read-string (slurp "samples/seattle/seattle-data0.edn")))
; (spyx (first data-tx))
; (spyx (second data-tx))
; (spyx (nth data-tx 2))
@(d/transact conn data-tx)
(def db-val (d/db conn))
(spyxx db-val)

(def results (d/q '[:find ?c :where [?c :community/name]] db-val ))
; (s/validate #{s/Any} results)  ; fails
(spyx (class results))
(spyx (count results))
(s/validate Set results)
(s/validate #{s/Any}        (into #{} results))
(s/validate #{ [Long] }     (into #{} results))
(s/validate EidResultSet    (into #{} results))

(def eid-1   (ffirst results))
(spyxx eid-1)
(def entity (d/entity db-val eid-1))
(spyxx entity)
(spyx  (keys entity))
(spyx
  (:community/name entity)
)

(newline)
(spyxx results)
(spyxx (first results))

(newline)
(def x1  (ffirst results))
(spyxx  x1)
(def x2  (d/entity db-val x1))
(spyxx  x2)
(s/validate datomic.query.EntityMap x2)
; (s/validate Map x2)                     ; fails
; (s/validate {s/Any s/Any} x2)           ; fails
(s/validate {s/Any s/Any} (into {} x2))   ; ok

(newline)
(def x3  (:community/name x2))
(spyxx x3)
(spyxx x2)
(def x2b (into {} x2))
(spyxx x2b)
(newline)

(println "exiting")
(System/exit 0)


(def pull-results (d/q '[:find (pull ?c [*])
                         :where [?c :community/name]]
                       db-val))
(newline)
(spyx (class pull-results))
(spyx (count pull-results))

(newline)
(def pull-results-1st (first pull-results))
(spyxx pull-results-1st)

(newline)
(def pull-results-1st-1st (first pull-results-1st))
(spyxx pull-results-1st-1st)

(defn mystery-fn [] (into (sorted-map) {:b 2 :a 1}))
(spyxx (mystery-fn))

(defn -main []
  (println "main - enter")
  (shutdown-agents)
)
