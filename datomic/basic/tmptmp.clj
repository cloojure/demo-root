
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

