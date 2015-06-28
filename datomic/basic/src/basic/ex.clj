(ns basic.ex
  (:require [datomic.api        :as d]
            [schema.core        :as s]
            [schema.coerce      :as coerce] 
            [tupelo.datomic     :as td]
            [tupelo.schema      :as ts] )
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
  (let [result-set  (s/validate #{ [ts/Eid] }
                      (into #{} (d/q '{:find  [?e]
                                       :where [ [?e :person/name] ] }
                                     db-val ))) ]
    (doseq [ [eid] result-set]    ; destructure as we loop
      (newline)
      (pprint (td/entity-map db-val eid)))))

(defn show-transactions
  "Display all transactions in the DB"
  [db-val]
  (println "-----------------------------------------------------------------------------")
  (println "Database Transactions:")
  (let [all-tx      (td/transactions db-val)
        sorted-tx   (sort-by #(grab :db/txInstant %) all-tx) ]
    (doseq [it sorted-tx]
      (pprint it))))

;---------------------------------------------------------------------------------------------------
; Create the database & a connection to it
(def uri "datomic:mem://example")
(d/create-database uri)
(def ^:dynamic *conn* (d/connect uri))

; Create a partition named :people (we could namespace it like :db.part/people if we wished)
(td/transact *conn* 
  (td/new-partition :people )
)

; Attribute definitions.  The attribute name (it's :db/ident value) is an (optionally namespaced)
; keyword of the form <namespace>/<name> or just <name>.  This keyword-name can be anything (it is
; not predefined anywhere).
(td/transact *conn* 
  (td/new-attribute :person/name        :db.type/string        :db.unique/value)
  (td/new-attribute :person/secret-id   :db.type/long          :db.unique/value)
  (td/new-attribute :person/ssn-usa     :db.type/string        :db.unique/value)
  (td/new-attribute :person/ssn-uk      :db.type/string        :db.unique/value)
  (td/new-attribute :person/ssn-hell    :db.type/string        :db.unique/value)
  (td/new-attribute :data/src           :db.type/string)
  (td/new-attribute :location           :db.type/string)
)

; enum values
(td/transact *conn*
  (td/new-enum :weapon/gun)
  (td/new-enum :weapon/knife)
  (td/new-enum :weapon/guile)
  (td/new-enum :weapon/curse)
)

; load 2 antagonists into the db
(s/def james-eid :- ts/Eid
  (it-> (td/new-entity { :person/name      "James Bond"
                         :person/ssn-usa   "123-45-6789"
                         :person/ssn-uk    "123-45-6789" } )
        @(td/transact *conn* it )
        (first (td/eids it))))

(td/transact *conn* 
  (td/new-entity { :person/name      "Mephistopheles"
                  :person/ssn-hell  "666-66-6666" } )
)

; Add some new attributes. This must be done in a separate tx before we attempt to use the new
; attributes.  Having a namespace is optional for the attribute name (it's :db/ident value).
(td/transact *conn*
  (td/new-attribute :weapon/type      :db.type/keyword :db.cardinality/many )
  (td/new-attribute :favorite-weapon  :db.type/keyword )
)

; Since the :person/name attribute is :db.unique/value, we can use a lookup-ref [:person/name
; <name-str>] as the entity-spec to update our entities instead of the EID.  Since :weapon/type is
; :db.cardinality/many, we must use a set to specify multiple values in a single update.
(td/transact *conn* 
  (td/update ; Give James some weapons
    [:person/name "James Bond"]  ; a lookup-ref
    { :weapon/type        #{ :weapon/gun :weapon/knife :weapon/guile }
      :favorite-weapon    :weapon/gun
      :person/secret-id   007  } ) ; '007' is interpreted as octal -> still 7 (whew!)
  (td/update ; Give the Devil his due
    [:person/name "Mephistopheles"]
    { :weapon/type        #{ :weapon/curse :weapon/guile }
      :favorite-weapon    :weapon/curse
      :person/secret-id   666 } )
)

(newline) (println "initial db")
(show-people (d/db *conn*))
(newline) (println "James 'e' value:")
(spyxx james-eid)

; verify we can find James by name
(let [db-val          (d/db *conn*)
      james-raw       (d/q '{:find [?eid]  :where [ [?eid :person/name "James Bond"] ] }  db-val)
      weapon-holders  (d/q '{:find [?eid]  :where [ [?eid :weapon/type] ] }  db-val)
      found-eid       (s/validate ts/Eid (ffirst james-raw)) ] ; query result is a set of tuples
  (newline)
  (spyxx james-raw)
  (spyxx weapon-holders)
  (assert (= found-eid james-eid))
  (pprint (td/entity-map db-val james-eid))
)

; Updated James' name. Note that we can use the current value of name in a lookup-ref, then assert
; the new name w/o conflict.
(td/transact *conn*
  (td/update [:person/name "James Bond"] {:person/name "Bond, James Bond"} )
)

; James drops his knife...
(td/transact *conn*
  (td/retract-value [:person/name "Bond, James Bond"]  :weapon/type :weapon/knife)
)
(newline) (println "James dropped knife + new name")
(show-people (d/db *conn*))

; James changes his favorite weapon
(let [tx-result @(td/transact *conn*
                   (td/update james-eid {:favorite-weapon :weapon/guile} )) ]
  (newline) (println "James changes his favorite weapon - db-before:")
  (show-people (grab :db-before tx-result))
  (newline) (println "James changes his favorite weapon - db-after:")
  (show-people (grab :db-after  tx-result))
  (newline) (println "James changes his favorite weapon - datoms:")
  (pprint (grab :tx-data   tx-result))
  (newline) (println "James changes his favorite weapon - tempids")
  (pprint (grab :tempids   tx-result))
)

; Set James' location, then change it
(newline)
(println "-----------------------------------------------------------------------------")
(println "James location -> HQ")
(let [tx-result   @(td/transact *conn* (td/update james-eid {:location "London"} ))
      tx-eid      (td/txid tx-result) ]
  (spyx (td/entity-map (d/db *conn*) tx-eid))
  @(td/transact *conn* (td/update tx-eid {:data/src "MI5"} )) ; annotate the tx
  (spyx (td/entity-map (d/db *conn*) tx-eid)))

(newline)
(println "d/entity vs t/entity:")
(pprint (d/entity      (d/db *conn*) james-eid))    ;=> {:db/id 277076930200558}
(pprint (td/entity-map (d/db *conn*) james-eid))
  ;=>   {:person/name "Bond, James Bond",
  ;      :person/ssn-usa "123-45-6789",
  ;      :person/ssn-uk "123-45-6789",
  ;      :location "London",
  ;      :weapon/type #{:weapon/guile :weapon/gun},
  ;      :favorite-weapon :weapon/guile}

(newline) (println "James location -> beach -> cave")
(td/transact *conn* (td/update james-eid {:location "Tropical Beach"} ))
(pprint (td/entity-map (d/db *conn*) james-eid))
(td/transact *conn* (td/update [:person/secret-id 007] {:location "Secret Cave"} ))
(pprint (td/entity-map (d/db *conn*) james-eid))

; Add a new weapon type
(td/transact *conn* (td/new-enum :weapon/feminine-charm))

; Add Honey Rider & annotate the tx
(newline) (println "adding Honey")
(let [tx-result   @(td/transact *conn* (td/new-entity {:person/name    "Honey Rider"
                                                       :weapon/type    :weapon/feminine-charm} ))
      tx-eid      (td/txid tx-result) ]
  (td/transact *conn* (td/update tx-eid {:data/src "Dr. No"} ))
  (newline)
  (spyxx tx-eid)
  (println "Tx Data:")
  (doseq [it (grab :tx-data tx-result)] (pprint it))
  (println "Temp IDs:")
  (spyxx (grab :tempids tx-result))
)
(show-people        (d/db *conn*))
(show-transactions  (d/db *conn*))

; Give Honey a knife
(td/transact *conn* (td/update [:person/name "Honey Rider"] {:weapon/type :weapon/knife} ))

; find honey by pull
(def honey-pull (d/q '[:find (pull ?e [*])
                       :where [?e :person/name "Honey Rider"]
                      ]
                    (d/db *conn*)))
(newline) (println "Honey pull")
(spyxx honey-pull)

; vanquish the devil
(let [devil-eid (d/q  '{:find [?e .]  ; scalar result
                        :in [$ ?n]
                        :where [ [?e :person/name ?n] ] }
                     (d/db *conn*) "Mephistopheles" )
      _ (spyxx devil-eid)
      devil     (td/entity-map (d/db *conn*) devil-eid)
      _ (spyxx devil)
      tx-result @(td/transact *conn* (td/retract-entity devil-eid))
  ]
  (newline) (println "removed devil" )
  (spyxx tx-result)
  (show-people (d/db *conn*))

  ; try to find devil now
  (spyx devil-eid)
  (spy :msg "devil is missing" (td/entity-map (d/db *conn*) devil-eid))
)

(def dr-no  (it-> (td/new-entity :people { :person/name    "Dr No"
                                          :weapon/type    :weapon/guile } )
                  @(td/transact *conn* it)
                  (td/eids it)
                  (first it)))
(newline)
(println "Added dr-no  EID:" dr-no "   partition:" (td/partition-name (d/db *conn*) dr-no))
(show-people (d/db *conn*))

; (println "exit")
; (System/exit 1)

(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
