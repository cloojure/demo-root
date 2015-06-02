(ns basic.core
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce]
  )
  (:use   clojure.pprint
          cooljure.core
  )
  (import [java.util Set Map List])
  (:gen-class))

(set! *print-length* 5)

; Prismatic Schema coersion testing
;-----------------------------------------------------------------------------
(def CommentRequest
  { (s/optional-key :parent-comment-id) long
    :text String
    :share-services [ (s/enum :twitter :facebook :google) ]
  } )
(def cr-parser (coerce/coercer CommentRequest coerce/json-coercion-matcher))
(def raw-comment-req
  { :parent-comment-id (int 1234)
    :text "This is awesome!"
    :share-services ["twitter" "facebook"] } )
(def parsed-comment-req
  { :parent-comment-id (long 1234)
    :text "This is awesome!"
    :share-services [:twitter :facebook] } )
(assert (= (cr-parser raw-comment-req) parsed-comment-req))

;-----------------------------------------------------------------------------
; load data
(s/validate {s/Any s/Any} (into (sorted-map) {:a 1 :b 2}))

(def Eid            (s/one Long "entity-id"))

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

;-----------------------------------------------------------------------------
; entity api
(def e-results (d/q '[:find ?c :where [?c :community/name]] db-val ))
(spyx (count e-results))
(spyx (class e-results))
(s/validate  #{ [ Eid ] }  (into #{} e-results))

(def eid-1 (ffirst e-results))
(spyxx eid-1)
(def entity (d/entity db-val eid-1))
(spyxx  entity)
(spyx   (keys entity))
(spyx   (:community/name entity))

(newline)
(spyxx e-results)
(spyxx (first e-results))

(newline)
(def x1  (ffirst e-results))
(spyxx  x1)
(s/def x2  :- datomic.query.EntityMap
  (d/entity db-val x1))
(spyxx  x2)
; (s/validate Map x2)                     ; fails
; (s/validate {s/Any s/Any} x2)           ; fails
(s/validate {s/Any s/Any} (into {} x2))   ; ok

(newline)
(def x3  (:community/name x2))
(spyxx x3)
(spyxx x2)
(def x2b (into {} x2))
(spyxx x2b)

;-----------------------------------------------------------------------------
; pull api
(def ResultVec     [ {s/Any s/Any} ] )
(def ResultVecSeq  [ResultVec] )

(s/def pull-results  :- ResultVecSeq
  (d/q '[:find (pull ?c [*]) :where [?c :community/name]] db-val))
(newline)
(spyx (count pull-results))
(spyxx pull-results)
(pprint (ffirst pull-results))

;-----------------------------------------------------------------------------
; back to entity api
(newline)
(println "Community & neighborhood names:")
(pprint (map #(let [entity      (s/validate datomic.query.EntityMap
                                  (d/entity db-val (first %)))
                    comm-name   (safe-> entity :community/name)
                    nbr-name    (safe-> entity :community/neighborhood :neighborhood/name) ]
                [comm-name nbr-name] )
          e-results ))

; for the first community, get its neighborhood, then for that neighborhood, get all its
; communities, and print out their names
(s/def community      :- datomic.query.EntityMap
        (d/entity db-val (ffirst e-results)))
(s/def neighborhood   :- datomic.query.EntityMap
        (safe-> community :community/neighborhood))
(s/def communities    :- #{datomic.query.EntityMap}
        (safe-> neighborhood :community/_neighborhood ))
(binding [*print-length* 20]
  (newline)
  (println "Community #1")
  (pprint community)
  (newline)
  (println "Community #2")
  (pprint (d/touch community))
  (newline)
  (println "Communities in same neighborhood as first:")
  (pprint (mapv :community/name communities))
)

; find all communities and specify returning their names into a collection
(newline)
(print "comms & names: ")   ; a set of tuples
(s/def com-and-names :- #{ [ (s/one long "eid") (s/one s/Str "name") ] }
  (into #{} (d/q '[:find ?c ?n :where [?c :community/name ?n]] db-val)))
(spyx (count com-and-names))
(pprint com-and-names)

; find all communities and specify returning their names into a collection
(newline)
(print "All com. names: ")  ; a list of names
(s/def com-names-coll  :- [s/Str]
  (d/q '[:find [?n ...] :where [_ :community/name ?n]] db-val))
(spyx (count com-names-coll))
(pprint com-names-coll)

; find all community names & pull their urls
(newline)
(print "com. names & urls: ")   ; a list of tuples like [ Str {} ]
(s/def comm-names-urls :- [ [ (s/one s/Str ":community/name")  
                              {:community/url s/Str} ] ]
  (d/q '[:find ?n (pull ?c [:community/url]) :where [?c :community/name ?n]]  db-val))
(spyx (count comm-names-urls))
(pprint comm-names-urls)

(println "exiting")
(System/exit 0)

(defn -main []
  (println "main - enter")
  (shutdown-agents)
)
