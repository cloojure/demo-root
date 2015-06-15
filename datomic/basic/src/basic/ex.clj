(ns basic.ex
  (:require [datomic.api        :as d]
            [cooljure.core      :refer [spyx spyxx]]
            [cooljure.explicit  :as x]
            [basic.datomic      :as t]
            [schema.core        :as s]
            [schema.coerce      :as coerce] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

;---------------------------------------------------------------------------------------------------
; Create the database & a connection to it
(def uri "datomic:mem://example")
(d/create-database uri)
(def ^:dynamic *conn* (d/connect uri))

; Create a partition :people (we could namespace like :db.part/people if we wanted)
(d/transact *conn* [
  (t/new-partition :people )   
] )

; Attribute definitions.  The attribute name (it's :db/ident value) is an (optionally namespaced)
; keyword of the form <namespace>/<name> or just <name>.  This keyword-name can be anything (it is
; not predefined anywhere).  
(d/transact *conn* [
  (t/new-attribute :person/name        :db.type/string        :db.unique/value)
  (t/new-attribute :person/secret-id   :db.type/long          :db.unique/value)
  (t/new-attribute :person/ssn-usa     :db.type/string        :db.unique/value)
  (t/new-attribute :person/ssn-uk      :db.type/string        :db.unique/value)
  (t/new-attribute :person/ssn-hell    :db.type/string        :db.unique/value)
  (t/new-attribute :data/src           :db.type/string)
  (t/new-attribute :location           :db.type/string)
] )

; enum values
(d/transact *conn* [
  (t/new-enum :weapon/gun )
  (t/new-enum :weapon/knife )
  (t/new-enum :weapon/guile )
  (t/new-enum :weapon/curse )
] )

; load 2 antagonists into the db
(s/def james-eid :- t/Eid
  (it-> (t/new-entity { :person/name      "James Bond"
                        :person/ssn-usa   "123-45-6789"
                        :person/ssn-uk    "123-45-6789" } )
        @(d/transact *conn* [it] )
        (first (t/eids it))))

(d/transact *conn* [
  (t/new-entity { :person/name      "Mephistopheles"
                  :person/ssn-hell  "123-45-6789" } )
] )

; Add some new attributes. This must be done in a separate tx before we attempt to use the new
; attributes.  Having a namespace is optional for the attribute name (it's :db/ident value).
(d/transact *conn* [
  (t/new-attribute :weapon/type      :db.type/keyword :db.cardinality/many )
  (t/new-attribute :favorite-weapon  :db.type/keyword ) 
] )

; Since the name is :db.unique/value, we can use that to update our entities instead of the EID.
; Since :weapon/type is :db.cardinality/many, we must use a a set to specify multiple values of a
; single attribute.
(d/transact *conn* [
  (t/update ; Give James some weapons
    [:person/name "James Bond"]
    { :weapon/type        #{ :weapon/gun :weapon/knife :weapon/guile }
      :favorite-weapon    :weapon/gun
      :person/secret-id   007  } ) ; '007' is interpreted as octal -> still 7 (whew!)
  (t/update ; Give the Devil his due
    [:person/name "Mephistopheles"]
    { :weapon/type        #{ :weapon/curse :weapon/guile }
      :favorite-weapon    :weapon/curse
      :person/secret-id   666 } )
] )

(newline) (println "initial db")
(t/show-db (d/db *conn*))
(newline) (println "James 'e' value:")
(spyxx james-eid)

; verify we can find James by name
(let [db-val          (d/db *conn*)
      james-raw       (d/q '{:find [?eid]  :where [ [?eid :person/name "James Bond"] ] }  db-val)
      weapon-holders  (d/q '{:find [?eid]  :where [ [?eid :weapon/type] ] }  db-val)
      found-eid       (s/validate t/Eid (ffirst james-raw)) ] ; query result is a set of tuples
  (newline)
  (spyxx james-raw)
  (spyxx weapon-holders)
  (assert (= found-eid james-eid))
  (pprint (t/entity db-val james-eid))
)

; Updated James' name. Note that we can use the current value of name for lookup, then add in the
; new name w/o conflict.
(d/transact *conn* [
  (t/update [:person/name "James Bond"] { :person/name "Bond, James Bond" } )
] )

; James drops his knife...
(d/transact *conn* [
  (t/retraction [:person/name "Bond, James Bond"]  :weapon/type :weapon/knife)
] )
(newline) (println "James dropped knife + new name")
(t/show-db (d/db *conn*))

; James changes his favorite weapon
(let [tx-result @(d/transact *conn* [ 
                   (t/update james-eid {:favorite-weapon :weapon/guile} ) ] ) ]
  (newline) (println "James changes his favorite weapon - db-before:")
  (t/show-db (grab :db-before tx-result))
  (newline) (println "James changes his favorite weapon - db-after:")
  (t/show-db (grab :db-after  tx-result))
  (newline) (println "James changes his favorite weapon - datoms:")
  (pprint (grab :tx-data   tx-result))
  (newline) (println "James changes his favorite weapon - tempids")
  (pprint (grab :tempids   tx-result))
)

; Set James' location, then change it
(newline)
(println "-----------------------------------------------------------------------------")
(println "James location -> HQ")
(let [tx-result   @(d/transact *conn* [ (t/update james-eid {:location "London"} ) ] )
      tx-eid      (t/txid tx-result) ]
  (spyx (t/entity (d/db *conn*) tx-eid))
  @(d/transact *conn* [ (t/update tx-eid {:data/src "MI5"} ) ] ) ; annotate the tx
  (spyx (t/entity (d/db *conn*) tx-eid)))

(newline)
(println "d/entity vs t/entity:")
(pprint (d/entity (d/db *conn*) james-eid))    ;=> {:db/id 277076930200558}
(pprint (t/entity (d/db *conn*) james-eid))
  ;=>   {:person/name "Bond, James Bond",
  ;      :person/ssn-usa "123-45-6789",
  ;      :person/ssn-uk "123-45-6789",
  ;      :location "London",
  ;      :weapon/type #{:weapon/guile :weapon/gun},
  ;      :favorite-weapon :weapon/guile}

(newline) (println "James location -> beach -> cave")
(d/transact *conn* [ (t/update james-eid {:location "Tropical Beach"} ) ] )
(pprint (t/entity (d/db *conn*) james-eid))
(d/transact *conn* [ (t/update [:person/secret-id 007] {:location "Secret Cave"} ) ] )
(pprint (t/entity (d/db *conn*) james-eid))

; Add a new weapon type
(d/transact *conn* [ (t/new-enum :weapon/feminine-charm) ] )

; Add Honey Rider & annotate the tx
(newline) (println "adding Honey")
(let [tx-result   @(d/transact *conn* [ (t/new-entity {:person/name    "Honey Rider"
                                                       :weapon/type    :weapon/feminine-charm} ) ] )
      tx-eid      (t/txid tx-result) ]
  (d/transact *conn* [ (t/update tx-eid {:data/src "Dr. No"} ) ] )
  (newline)
  (spyxx tx-eid)
  (println "Tx Data:")
  (doseq [it (grab :tx-data tx-result)] (pprint it))
  (println "Temp IDs:")
  (spyxx (grab :tempids tx-result))
)
(t/show-db    (d/db *conn*))
(t/show-db-tx (d/db *conn*))

; Give Honey a knife
(d/transact *conn* [ (t/update [:person/name "Honey Rider"] {:weapon/type :weapon/knife} ) ] )

; find honey by pull
(def honey-pull (d/q '[:find (pull ?e [*])
                       :where [?e :person/name "Honey Rider"]
                      ]
                    (d/db *conn*)))
(newline) (println "Honey pull")
(spyxx honey-pull)

(let [honey-eid (d/q  '{:find [?e .]  ; scalar result
                        :in [$ ?n]
                        :where [ [?e :person/name ?n] ]
                       }
                     (d/db *conn*) "Honey Rider"  )
      _ (spyxx honey-eid)
      honey     (t/entity (d/db *conn*) honey-eid)
      _ (spyxx honey)
      tx-result @(d/transact *conn* [ (t/retraction-entity honey-eid) ] )
  ]
  (newline) (println "removed honey" )
  (spyxx tx-result)
  (t/show-db (d/db *conn*))

  ; try to find honey now
  (spyx honey-eid)
  (spy :msg "Honey is missing" (t/entity (d/db *conn*) honey-eid))
)

; write qset -> (into #{} (d/q ...))

(def dr-no  (it-> (t/new-entity :people { :person/name    "Dr No"
                                          :weapon/type    :weapon/guile } )
                  @(d/transact *conn* [it] )
                  (t/eids it)
                  (first it)))
(newline) 
(println "Added dr-no  EID:" dr-no "   partition:" (t/partition-name (d/db *conn*) dr-no))
(t/show-db (d/db *conn*))

; (println "exit")
; (System/exit 1)

(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
