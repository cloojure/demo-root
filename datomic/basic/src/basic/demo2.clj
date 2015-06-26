(ns basic.demo2
  (:require [datomic.api        :as d]
            [schema.core        :as s]
            [schema.coerce      :as coerce] 
            [tupelo.datomic      :as t] )
  (:use   clojure.pprint
          tupelo.core)
  (:gen-class))

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

;---------------------------------------------------------------------------------------------------
; helper functions

(defn show-people
  "Display facts about all entities with a :person/name"
  [db-val]
  (println "-----------------------------------------------------------------------------")
  (println "Database people:")
  (let [result-set  (s/validate #{ [t/Eid] }
                      (into #{} (d/q '{:find  [?e]
                                       :where [ [?e :person/name] ] }
                                     db-val ))) ]
    (doseq [ [eid] result-set]    ; destructure as we loop
      (newline)
      (pprint (t/entity-map db-val eid)))))

(defn show-transactions
  "Display all transactions in the DB"
  [db-val]
  (println "-----------------------------------------------------------------------------")
  (println "Database Transactions:")
  (let [all-tx      (t/transactions db-val)
        sorted-tx   (sort-by #(grab :db/txInstant %) all-tx) ]
    (doseq [it sorted-tx]
      (pprint it))))

;---------------------------------------------------------------------------------------------------
; Create the database & a connection to it
(def uri "datomic:mem://example")
(d/create-database uri)
(def ^:dynamic *conn* (d/connect uri))

; Create a partition named :people (we could namespace it like :db.part/people if we wished)
(t/transact *conn* 
  (t/new-partition :people )
)

; Attribute definitions.  The attribute name (it's :db/ident value) is an (optionally namespaced)
; keyword of the form <namespace>/<name> or just <name>.  This keyword-name can be anything (it is
; not predefined anywhere).
(t/transact *conn* 
  (t/new-attribute :person/name         :db.type/string         :db.unique/value)
  (t/new-attribute :person/secret-id    :db.type/long           :db.unique/value)
  (t/new-attribute :location            :db.type/string)
  (t/new-attribute :favorite-weapon     :db.type/keyword )
  (t/new-attribute :weapon/type         :db.type/keyword        :db.cardinality/many )
)     ; Note that :weapon type is like an untyped set. Anything (any keyword) can be added here.
      ; Example error is to add the keyword :location or :person/secret-id or :there.is/no-such-kw
      ; It is really just like a set of strings, where any string is accepted.

; enum values
(t/transact *conn* 
  (t/new-enum :weapon/gun)
  (t/new-enum :weapon/knife)
  (t/new-enum :weapon/guile)
  (t/new-enum :weapon/curse)
)

; load 2 antagonists into the db
@(t/transact *conn* 
  (t/new-entity { :person/name "James Bond" :location "London" :weapon/type :weapon/gun } )
  (t/new-entity { :person/name "Dr No"      :location "Caribbean" :weapon/type :weapon/gun } )
  (t/new-entity { :person/name "M"          :location "London" :weapon/type #{ :weapon/guile } } )
)

(t/transact *conn* 
  (t/update
    [:person/name "James Bond"]
    { :weapon/type #{ :weapon/gun :weapon/knife :weapon/guile } 
      :person/secret-id 007 } )
  (t/update
    [:person/name "Dr No"]
    { :weapon/type #{ :weapon/gun :weapon/knife :weapon/guile } } )
)

(newline) (println "db 01")
(show-people (d/db *conn*))

(newline)
(println "basic usage")

; result is a set - discards duplicates
(def find-loc-entity
  (d/q  '{:find [?loc]
          :where [ [?eid :location ?loc] ] }
       (d/db *conn*)))
(spyxx find-loc-entity)

; result is a list made from a set - discards duplicates
(def find-loc-coll
  (d/q  '{:find [ [?loc ...] ]
          :where [ [?eid :location ?loc] ] }
       (d/db *conn*)))
(spyxx find-loc-coll)

; result is a list - retains duplicates
(def find-pull
  (d/q  '{:find   [ (pull ?eid [:location]) ]
          :where  [ [?eid :location] ] }
       (d/db *conn*)))
(spyxx find-pull)

(def find-pull2 (into #{} find-pull))
(spyxx find-pull2)

; shows some problems

; silently discards all but first location
(let [single-tuple    (d/q  '{:find [ [?loc] ]
                              :where [ [?eid :location ?loc] ] }
                           (d/db *conn*)) ]
  (spyxx single-tuple))
;
; silently discards all but first location
(let [single-scalar   (d/q  '{:find [?loc .]
                              :where [ [?eid :location ?loc] ] }
                           (d/db *conn*)) ]
  (spyxx single-scalar))

(newline)
(println "show problems")

; silently discards all but first location
(let [single-tuple    (d/q  '{:find [ [?loc] ]
                              :where [ [?eid :location ?loc] ] }
                           (d/db *conn*)) ]
  (spyxx single-tuple))
;
; silently discards all but first location
(let [single-scalar   (d/q  '{:find [?loc .]
                              :where [ [?eid :location ?loc] ] }
                           (d/db *conn*)) ]
  (spyxx single-scalar))

(newline)
(println "finding name & loc")
; result is a set - discards duplicates
(let [find-name-loc-entity
            (d/q  '{:find [?name ?loc]
                    :where [ [?eid :location    ?loc] 
                             [?eid :person/name ?name] ] }
                 (d/db *conn*)) ]
  (spyxx find-name-loc-entity))

; result is a list - retains duplicates
(let [find-name-loc-pull
            (d/q  '{:find   [ (pull ?eid [:person/name :location]) ]
                    :where [ [?eid :location] ] }
                 (d/db *conn*)) 
      find-name-loc-pull2 (into #{} find-name-loc-pull) ]
  (spyxx find-name-loc-pull)
  (spyxx find-name-loc-pull2))

(newline)
(println "pulling with defaults")
(let [result    (d/q  '{:find   [ (pull ?eid [:person/name (default :person/secret-id -1) ] ) ]
                        :where  [ [?eid :person/name ?name] ] }
                  (d/db *conn*))
]
  (pprint result))
(println "pulling without defaults")
(let [result    (d/q  '{:find   [ (pull ?eid [:person/name :person/secret-id] ) ]
                        :where  [ [?eid :person/name ?name] ] }
                  (d/db *conn*))
]
  (pprint result))

(newline)
(println "update error")
; nothing will stop nonsense actions like this
(def error-tx-result
  (t/transact *conn* 
    (t/update
      [:person/name "James Bond"]
      { :weapon/type #{ 99 } } )))

(spyxx
  @(t/transact *conn* 
    (t/update
      [:person/name "James Bond"]
      { :weapon/type #{ :person/secret-id :there.is/no-such-kw } } )))

(newline) (println "db 02")
(show-people (d/db *conn*))

(newline) 
(println "---------------------------------------------------------------------------------------------------")
(println " we can see the exception if we print the tx-result, but it won't halt execution")
(newline) 
(println error-tx-result)

(newline) 
(println "---------------------------------------------------------------------------------------------------")
(println "Here we try to retrive the result. Now the Exception is thrown.")
(newline) 
(try
  (spyxx @error-tx-result)
  (catch Exception ex (println "Caught exception: " ex)))

; (println "exit")
; (System/exit 1)
(defn -main []
  (newline)
  (println "---------------------------------------------------------------------------------------------------")
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
