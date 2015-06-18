;-----------------------------------------------------------------------------
; Find all transaction times, sort them in reverse order
(newline)
(println "tx-instants")
(s/def tx-instants :- [s/Any]
  (reverse (sort 
    (d/q '[:find [?when ...] 
           :where [_ :db/txInstant ?when] ]
         db-val ))))
(spyx (count tx-instants))
(pprint tx-instants)
(def data-tx-date   (first tx-instants))
(def schema-tx-date (second tx-instants))

; make query to find all communities
(def communities-query '[:find [?com ...]  :where [?com :community/name] ] )

; find all communities as of schema transaction
(let [db-asof-schema (d/as-of db-val schema-tx-date) ]
  (spyx (count (d/q communities-query db-asof-schema))))

; find all communities as of seed data transaction
(let [db-asof-data (d/as-of db-val data-tx-date) ]
  (spyx (count (d/q communities-query db-asof-data))))


; find all communities since schema transaction
(let [db-since-schema (d/since db-val schema-tx-date) ]
  (spyx (count (d/q communities-query db-since-schema))))

; find all communities since seed data transaction
(let [db-since-data (d/since db-val data-tx-date) ]
  (spyx (count (d/q communities-query db-since-data))))


; parse additional seed data file
(def new-data-tx (read-string (slurp "samples/seattle/seattle-data1.edn")))

; find all communities if new data is loaded
(let [db-if-new-data (:db-after (d/with db-val new-data-tx)) ]
  (spyx (count (d/q communities-query db-if-new-data))))

; find all communities currently in DB
(spyx (count (d/q communities-query db-val)))

; submit new data tx
@(d/transact *conn* new-data-tx)
(def db-val-new (d/db *conn*))

; find all communities currently in DB
(spyx (count (d/q communities-query db-val-new)))

; find all communities since original seed data load transaction
(let [db-since-data (d/since db-val-new data-tx-date)]
  (spyx (count (d/q communities-query db-since-data))))

;-----------------------------------------------------------------------------

(newline)
(println "making :communities partition")
@(d/transact *conn* [ {:db/id (d/tempid :db.part/db)
                       :db/ident  :communities
                       :db.install/_partition   :db.part/db} ] )

(newline)
(println "making Easton community")
@(d/transact *conn* [ {:db/id (d/tempid :communities)
                       :community/name "Easton"} ] )

;get id for a community, use to transact data
(newline)
(def belltown-id-entity (d/q '[:find ?id
                               :where [?id :community/name "belltown"] ]
                               db-val-new ))
(spyxx belltown-id-entity)
(def belltown-id-dot (d/q '[:find ?id .
                            :where [?id :community/name "belltown"] ]
                            db-val-new ))
(spyxx belltown-id-dot)

(newline)
(println "Adding 'free stuff' for belltown")
@(d/transact *conn* [ {:db/id belltown-id-dot
                       :community/category "free stuff"} ] )    ; map syntax
(println "Retracting 'free stuff' for belltown")
@(d/transact *conn* [ [:db/retract belltown-id-dot
                       :community/category "free stuff"] ] )    ; tuple syntax

;-----------------------------------------------------------------------------
; pull api
(def TupleMap     [ {s/Any s/Any} ] )

(s/def pull-results  :- [TupleMap]
  (d/q '[:find (pull ?c [*]) :where [?c :community/name]] 
       db-val))
(newline)
(spyx (count pull-results))
(spyxx pull-results)
(pprint (ffirst pull-results))

