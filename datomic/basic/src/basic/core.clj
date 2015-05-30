(ns basic.core
  (:require [datomic.api      :as d] 
            [cooljure.core    :refer [spyx spyxt]]
            [schema.core      :as s] )
  (:use   clojure.pprint
          cooljure.core 
  )
  (import [java.util Set Map List])
  (:gen-class))

(s/validate {s/Any s/Any} (into (sorted-map) {:a 1 :b 2}))

(def Entity Long)
(def ResultSet #{ [s/Any] } )
(def EntityResultSet #{ [ (s/one Long "entity-id") ] } )

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
(spyxt db-val)

(def results (d/q '[:find ?c :where [?c :community/name]] db-val ))
(spyx (class results))
(spyx (count results))
(s/validate Set results)
; (s/validate #{s/Any} results)  ; fails
(s/validate #{s/Any}    (into #{} results))
(s/validate #{ [Long] } (into #{} results))
(s/validate EntityResultSet (into #{} results))

(def idf   (first results))
(spyxt idf)
(def id   (ffirst results))
(spyxt id)
(def entity (d/entity db-val id))
(spyxt entity)
(spyx  (keys entity))
(spyx
  (:community/name entity)
)

(newline)
(spyxt results)
(spyxt (first results))

(newline)
(def x1  (ffirst results))
(spyxt  x1)
(def x2  (d/entity db-val x1))
(spyxt  x2)
(s/validate datomic.query.EntityMap x2)
; (s/validate Map x2)                     ; fails
; (s/validate {s/Any s/Any} x2)           ; fails
(s/validate {s/Any s/Any} (into {} x2))   ; ok

(newline)
(def x3  (:community/name x2))
(spyxt x3)
(spyxt x2)
(def x2b (into {} x2))
(spyxt x2b)
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
(spyxt pull-results-1st)

(newline)
(def pull-results-1st-1st (first pull-results-1st))
(spyxt pull-results-1st-1st)

(defn mystery-fn [] (into (sorted-map) {:b 2 :a 1}))
(spyxt (mystery-fn))

(defn -main []
  (println "main - enter")
  (shutdown-agents)
)
