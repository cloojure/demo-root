(ns basic.ex
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [basic.datomic    :as t]
            [schema.core      :as s]
            [schema.coerce    :as coerce] )
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

;---------------------------------------------------------------------------------------------------
; Partition definitions
(t/create-partition *conn* :people )  ; we could namespace like :db.part/people if we wanted

;---------------------------------------------------------------------------------------------------
; Attribute definitions.  The attribute name (it's :db/ident value) is an (optionally namespaced)
; keyword of the form <namespace>/<name> or just <name>.  This keyword-name can be anything (it is
; not predefined anywhere).  
(t/create-attribute *conn* :person/name        :db.type/string        :db.unique/value)
(t/create-attribute *conn* :person/secret-id   :db.type/long          :db.unique/value)
(t/create-attribute *conn* :person/ssn-usa     :db.type/string        :db.unique/value)
(t/create-attribute *conn* :person/ssn-uk      :db.type/string        :db.unique/value)
(t/create-attribute *conn* :person/ssn-hell    :db.type/string        :db.unique/value)
(t/create-attribute *conn* :data/src           :db.type/string)
(t/create-attribute *conn* :location           :db.type/string)

;-----------------------------------------------------------------------------
; enum values
(t/create-enum *conn* :weapon/gun )
(t/create-enum *conn* :weapon/knife )
(t/create-enum *conn* :weapon/guile )
(t/create-enum *conn* :weapon/curse )

;---------------------------------------------------------------------------------------------------
; load 2 antagonists into the db
(s/def james-eid :- t/Eid
  (t/create-entity *conn*   { :person/name      "James Bond"
                              :person/ssn-usa   "123-45-6789"
                              :person/ssn-uk    "123-45-6789" } ))

(t/create-entity *conn*   { :person/name      "Mephistopheles"
                            :person/ssn-hell  "123-45-6789" } )

; Add some new attributes. This must be done in a separate tx before we attempt to use the new
; attributes.  Having a namespace is optional for the attribute name (it's :db/ident value).
(t/create-attribute *conn* :weapon/type      :db.type/keyword :db.cardinality/many )
(t/create-attribute *conn* :favorite-weapon  :db.type/keyword ) 

; Give James some weapons.  Since the name is :db.unique/value, we can use that to update our
; entities instead of James' EID.  Since :weapon/type is :db.cardinality/many, we must use a a set
; to specify multiple values of a single attribute.
(t/update *conn* 
  [:person/name "James Bond"]
  { :weapon/type        #{ :weapon/gun :weapon/knife :weapon/guile }
    :favorite-weapon    :weapon/gun
    :person/secret-id   007  ; '007' is interpreted as octal -> still 7 (whew!)
  } )

; Give the Devil his due
(t/update *conn* 
  [:person/name "Mephistopheles"]
  { :weapon/type       #{ :weapon/curse :weapon/guile }
    :favorite-weapon   :weapon/curse
    :person/secret-id  666
  } )

(newline) (println "initial db")
(t/show-db (d/db *conn*))

(let [james-raw       (d/q '{:find [?eid  ]  :where [ [?eid :person/name "James Bond"] ] }  (d/db *conn*))
      james-scalar    (d/q '{:find [?eid .]  :where [ [?eid :person/name "James Bond"] ] }  (d/db *conn*))
      james-flat      (flatten (into [] james-raw))
  ]
    (spyxx james-raw)
    (spyxx james-scalar)
    (spyxx james-flat))

(let [weapon-holders-1    (d/q '{:find [  ?eid  ]  :where [ [?eid :weapon/type] ] }  (d/db *conn*))
      weapon-holders-2    (d/q '{:find [  ?eid .]  :where [ [?eid :weapon/type] ] }  (d/db *conn*))
      weapon-holders-3    (d/q '{:find [ [?eid] ]  :where [ [?eid :weapon/type] ] }  (d/db *conn*))
  ]
    (spyxx weapon-holders-1)    ; set of tuples
    (spyxx weapon-holders-2)    ; single-scalar, like (ffirst result))
    (spyxx weapon-holders-3)    ; single-tuple,  like ( first result)
)

(newline) (println "James 'e' value:")
(spyxx james-eid)
; verify we can find James by name
(let [result (s/validate t/Eid 
               (ffirst (d/q '{:find [?e]  :where [ [?e :person/name "James Bond"] ] }  
                            (d/db *conn*)))) ]
  (assert (= result james-eid)))

; Updated James' name. Note that we can use the current value of name for lookup, then add in the
; new name w/o conflict.
(t/update *conn* [:person/name "James Bond"] {  :person/name "Bond, James Bond" } )

; James drops his knife...
(t/retract *conn* [:person/name "Bond, James Bond"]  :weapon/type :weapon/knife)
(newline) (println "James dropped knife + new name")
(t/show-db (d/db *conn*))

; James changes his favorite weapon
(let [tx-result (t/update *conn* james-eid {:favorite-weapon :weapon/guile} ) ]
  (newline) (println "James changes his favorite weapon - db-before:")
  (t/show-db (:db-before tx-result))
  (newline) (println "James changes his favorite weapon - db-after:")
  (t/show-db (:db-after  tx-result))
  (newline) (println "James changes his favorite weapon - datoms:")
  (pprint  (:tx-data   tx-result))
  (newline) (println "James changes his favorite weapon - tempids")
  (pprint  (:tempids   tx-result))
)

; Set James' location, then change it
(newline)
(println "-----------------------------------------------------------------------------")
(println "James location -> HQ")
(t/update *conn* james-eid {:location "London"} )
(pprint (d/entity (d/db *conn*) james-eid))    ;=> {:db/id 277076930200558}
(pprint (t/entity (d/db *conn*) james-eid))
  ;=>   {:person/name "Bond, James Bond",
  ;      :person/ssn-usa "123-45-6789",
  ;      :person/ssn-uk "123-45-6789",
  ;      :location "London",
  ;      :weapon/type #{:weapon/guile :weapon/gun},
  ;      :favorite-weapon :weapon/guile}

(newline) (println "James location -> beach")
(t/update *conn* james-eid {:location "Tropical Beach"} )
(pprint (t/entity (d/db *conn*) james-eid))

(newline) (println "James location -> cave")
(t/update *conn* [:person/secret-id 007] {:location "Secret Cave"} )
(pprint (t/entity (d/db *conn*) james-eid))

; Add a new weapon type
(t/create-enum *conn* :weapon/feminine-charm)

; Add Honey Rider & annotate the tx
(newline)
(let [tx-tmpid            (d/tempid :db.part/tx)
        _ (spyxx tx-tmpid)
      honey-rider-tmpid   (d/tempid :people)
      tx-data             [ { :db/id  honey-rider-tmpid
                              :person/name    "Honey Rider"
                              :weapon/type    :weapon/feminine-charm }
                            { :db/id  tx-tmpid
                              :data/src "Dr. No" }
                          ]
      tx-result           @(d/transact *conn* tx-data)
      _ (spyxx tx-result)
      datoms    (safe-> :tx-data  tx-result)
      tempids   (safe-> :tempids  tx-result)
      tx-eid    (-> tx-tmpid tempids)
     ]
  (newline)
  (println "Tx Data:")
  (doseq [it datoms] (pprint it))

  (newline)
  (println "TXID:")
  (pprint (t/entity (d/db *conn*) (t/txid tx-result)))

  (newline)
  (println "Temp IDs:")
  (spyxx tempids)
  (println "tx-eid" tx-eid)
)
(newline) (println "added beauty")
(t/show-db (d/db *conn*))
(t/show-db-tx (d/db *conn*))

; Give Honey a knife
(t/update *conn* [:person/name "Honey Rider"] {:weapon/type :weapon/knife} )

; find honey by pull
(def honey-pull (d/q '[:find (pull ?e [*])
                       :where [?e :person/name "Honey Rider"]
                      ]
                    (d/db *conn*)))
(newline) (println "Honey pull")
(spyxx honey-pull)

(let [honey-eid (d/q  '{:find [?e .]
                        :in [$ ?n]
                        :where [ [?e :person/name ?n] ]
                       }
                     (d/db *conn*) "Honey Rider"  )
      _ (spyxx honey-eid)
      honey     (t/entity (d/db *conn*) honey-eid)
      _ (spyxx honey)
      tx-result (t/retract-entity *conn* honey-eid)
  ]
  (newline) (println "removed honey" )
  (spyxx tx-result)
  (t/show-db (d/db *conn*))

  ; try to find honey now
  (spyx honey-eid)
  (spy :msg "Honey is missing" (t/entity (d/db *conn*) honey-eid))
)

; #todo need a function like swap!, reset!
; #toto test "scalar get" [?e .] ensure throws if > 1 result (or write qone to do it)
; write qset -> (into #{} (d/q ...))

(def dr-no (t/create-entity *conn* :people
               { :person/name    "Dr No"
                 :weapon/type    :weapon/guile } ))
(newline) (println "Added dr-no" )
(spyxx dr-no)
(spyxx (d/ident (d/db *conn*) (d/part dr-no)))
(t/show-db (d/db *conn*))


; (println "exit")
; (System/exit 1)

(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
