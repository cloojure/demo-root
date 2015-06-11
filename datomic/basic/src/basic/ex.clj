(ns basic.ex
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))


;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(def Eid 
  "Entity ID (EID) type definition"
  Long)

(def TxResult  
  "Transaction Result type definition"
  { :db-before    s/Any
    :db-after     s/Any
    :tx-data      s/Any
    :tempids      s/Any } )

(def EntitySpec (s/either long 
                          [ (s/one s/Keyword "attr")  (s/one s/Any "val") ] ))

; #todo delete?  
(def Vec1 [ (s/one s/Any "x1") ] )
(def Vec2 [ (s/one s/Any "x1") (s/one s/Any "x2") ] )
(def Vec3 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") ] )
(def Vec4 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") (s/one s/Any "x4") ] )
(def Vec5 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") (s/one s/Any "x4") (s/one s/Any "x5") ] )

;---------------------------------------------------------------------------------------------------
; Create the database & a connection to it
(def uri "datomic:mem://example")
(d/create-database    uri)
(def conn (d/connect  uri))

; Load the schema definition from file and insert into DB
(def schema-defs (read-string (slurp "ex-schema.edn")))
@(d/transact conn schema-defs)

(s/set-fn-validation! true)

;---------------------------------------------------------------------------------------------------
(defn show-db 
  "Display facts about all entities with a :person/name"
  [db-val]
  (println "-----------------------------------------------------------------------------")
    (s/def res-1 :- #{ [Eid] }
      (into #{}
        (d/q '{:find  [?e]
               :where [ [?e :person/name] ]
              }
             db-val )))
    (doseq [it res-1]
      (let [eid     (first it)
            map-val (into (sorted-map) (d/entity db-val eid))
           ]
        (newline)
        (pprint map-val))))


(defn show-db-tx 
  "Display all transactions in the DB"
  [db-val]
  (newline)
  (println "-----------------------------------------------------------------------------")
  (println "Database Transactions")
  (let [result-set      (d/q '{:find [?c]
                               :where [ [?c :db/txInstant] ] }
                              db-val )
        res-2           (for [ [eid] result-set]
                          (into (sorted-map) (d/entity db-val eid)))
        res-4       (into (sorted-set-by #(.compareTo (:db/txInstant %1) (:db/txInstant %2) ))
                          res-2)
  ]
    (doseq [it res-4]
      (pprint it))))

(s/defn txid  :- Eid
  "Returns the transaction EID given a tx-result"
  [tx-result]
  (let [datoms  (safe-> :tx-data tx-result)
        result  (it-> datoms        ; since all datoms in tx have same txid
                      (first it)    ; we only need the first datom
                      (nth it 3))   ; tx EID is at index 3 in [e a v tx added] vector
  ]
    (when false  ; for testing
      (let [txids   (for [it datoms] (nth it 3)) ]
        (spyxx txids)
        (assert (apply = txids))))
    result ))

(s/defn create-entity 
  "Create a new entity in the DB with the specified attribute-value pairs."
  ( [ attr-val-map    :- {s/Any s/Any} ]
   (create-entity :db.part/user attr-val-map))
  ( [ -partition      :- s/Keyword
      attr-val-map    :- {s/Any s/Any} ]
    (let [new-tempid   (d/tempid -partition)
          tx-result    @(d/transact conn [ (into {:db/id new-tempid} attr-val-map) ] )
          db-after  (safe-> :db-after   tx-result)
          tempids   (safe-> :tempids    tx-result)
          new-eid   (d/resolve-tempid db-after tempids new-tempid) ]
      new-eid ))  ; #todo:  maybe return a map of { :eid xxx   :tx-result yyy}
)

(s/defn update-entity 
  "Update an entity with new or changed attribute-value pairs"
  [entity-spec    :- EntitySpec
   attr-val-map   :- {s/Any s/Any} ] 
    (println "update-entity " entity-spec)
    (let [tx-data     (into {:db/id entity-spec} attr-val-map)
          tx-result   @(d/transact conn [ tx-data ] ) ]
      tx-result ))

; load 2 antagonists into the db
@(d/transact conn [
  ; map-format transaction data (the "add" command is implicit)
  { :db/id            (d/tempid :people -007)   ; entity specification
    :person/name      "James Bond"              ; multiple attribute/value pairs
    :person/ssn-usa   "123-45-6789"             ; multiple attribute/value pairs
    :person/ssn-uk    "123-45-6789"}            ; multiple attribute/value pairs

  ; list-format transaction data
  ; [<action>   <entity tmpid>      <attribute>        <value>  ]
    [:db/add  (d/tempid :people -666)  :person/name      "Mephistopheles"]
    [:db/add  (d/tempid :people -666)  :person/ssn-hell  "123-45-6789"]
              ; [<partition> <tmpid>]
              ; Note that <partition> could be namespaced like :beings.sentient/people
] )

; Add a new attribute. This must be done in a separate tx before we attempt to use the new attribute.
@(d/transact conn [
  { :db/id                  (d/tempid :db.part/db)
    :db/ident               :weapon/type
    :db/valueType           :db.type/keyword
    :db/cardinality         :db.cardinality/many
    :db.install/_attribute  :db.part/db }

  { :db/id                  (d/tempid :db.part/db)
    :db/ident               :favorite-weapon    ; ident doesn't need a namespace
    :db/valueType           :db.type/keyword
    :db/cardinality         :db.cardinality/one
    :db.install/_attribute  :db.part/db }
] )

; Since the name is :db.unique/value, we can use that to update our entities
@(d/transact conn [
  ; give James some weapons - map-format
  { :db/id  [:person/name "James Bond"]
        :weapon/type        #{:weapon/gun :weapon/knife :weapon/guile}  
        :favorite-weapon    :weapon/gun
        :person/secret-id   007  ; '007' is interpreted as octal -> still 7 (whew!)
   }
        ; must use a a set for multiple values of a single attr :weapon/type

  ; give the Devil his due - list-format (using a set for attr-value does not work here)
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type       :weapon/curse ]
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type       :weapon/guile ]
  [ :db/add  [:person/name "Mephistopheles"]   :favorite-weapon   :weapon/curse ]
  [ :db/add  [:person/name "Mephistopheles"]   :person/secret-id  666           ]
    ; list format is always one "fact" (EAV) per list
] )

(newline) (println "initial db")
(show-db (d/db conn))

(let [james-raw       (d/q '{:find [?e  ]  :where [ [?e :person/name "James Bond"] ] }  (d/db conn))
      james-scalar    (d/q '{:find [?e .]  :where [ [?e :person/name "James Bond"] ] }  (d/db conn))
      james-flat      (flatten (into [] james-raw))
  ]
    (spyxx james-raw)
    (spyxx james-scalar)
    (spyxx james-flat))

(let [weapon-holders-1    (d/q '{:find [?e  ]  :where [ [?e :weapon/type] ] }  (d/db conn))
      weapon-holders-2    (d/q '{:find [?e .]  :where [ [?e :weapon/type] ] }  (d/db conn))
      weapon-holders-3    (d/q '{:find [[?e]]  :where [ [?e :weapon/type] ] }  (d/db conn))
  ]
    (spyxx weapon-holders-1)
    (spyxx weapon-holders-2)
    (spyxx weapon-holders-3)
)


(newline) (println "James 'e' value:")
(s/def james-eid :- Eid
  (ffirst (d/q '{:find [?e]  :where [ [?e :person/name "James Bond"] ] }  (d/db conn))))
(spyxx james-eid)

; Updated James' name. Note that we can use the current value of name for lookup, then add in the
; new name w/o conflict.
@(d/transact conn [
  { :db/id  [:person/name "James Bond"]  :person/name "Bond, James Bond" }
] )

; James has dropped his knife...
@(d/transact conn [
  [:db/retract  [:person/name "Bond, James Bond"] :weapon/type  :weapon/knife]
] )
(newline) (println "James dropped knife + new name")
(show-db (d/db conn))

; James changes his favorite weapon
(let [tx-result   @(d/transact conn [
                    { :db/id  james-eid  :favorite-weapon :weapon/guile }
                  ] )
]
  (newline) (println "James changes his favorite weapon - db-before:")
  (show-db (:db-before tx-result))
  (newline) (println "James changes his favorite weapon - db-after:")
  (show-db (:db-after  tx-result))
  (newline) (println "James changes his favorite weapon - datoms:")
  (pprint  (:tx-data   tx-result))
  (newline) (println "James changes his favorite weapon - tempids")
  (pprint  (:tempids   tx-result))
)

; Set James' location, then change it
(newline) 
(println "-----------------------------------------------------------------------------")
(println "James location -> HQ")
(update-entity james-eid {:location "London"} )
(pprint          (d/entity (d/db conn) james-eid))    ;=> {:db/id 277076930200558}
(pprint (into {} (d/entity (d/db conn) james-eid)))
  ;=>   {:person/name "Bond, James Bond",
  ;      :person/ssn-usa "123-45-6789",
  ;      :person/ssn-uk "123-45-6789",
  ;      :location "London",
  ;      :weapon/type #{:weapon/guile :weapon/gun},
  ;      :favorite-weapon :weapon/guile}

(newline) (println "James location -> beach")
(update-entity james-eid {:location "Tropical Beach"} )
(pprint (into {} (d/entity (d/db conn) james-eid)))

(newline) (println "James location -> cave")
(update-entity [:person/secret-id 007] {:location "Secret Cave"} )
(pprint (into {} (d/entity (d/db conn) james-eid)))

; Add a new weapon type
@(d/transact conn [
  [:db/add  (d/tempid :db.part/user)  :weapon/type  :weapon/feminine-charm]
] )

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
      tx-result           @(d/transact conn tx-data)
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
  (pprint (into (sorted-map) (d/entity (d/db conn) (txid tx-result))))

  (newline)
  (println "Temp IDs:")
  (spyxx tempids)
  (println "tx-eid" tx-eid)
)
(newline) (println "added beauty")
(show-db (d/db conn))
(show-db-tx (d/db conn))

; find honey by pull
(def honey-pull (d/q '[:find (pull ?e [*])
                       :where [?e :person/name "Honey Rider"]
                      ] 
                    (d/db conn)))
(spyxx honey-pull)

(let [honey-eid (d/q  '{:find [?e .]
                        :in [$ ?n]
                        :where [ [?e :person/name ?n]
                               ]
                       }
                     (d/db conn) "Honey Rider"  ) 
      _ (spyxx honey-eid)
      honey     (into {} (d/entity (d/db conn) honey-eid))
      _ (spyxx honey)
      tx-result     @(d/transact conn [ [:db.fn/retractEntity honey-eid] ] )
  ]
  (newline) (println "removed honey" )
  (spyxx tx-result)
  (show-db (d/db conn))

  ; try to find honey now
  (spyxx honey-eid)
  (spyxx (into {} (d/entity (d/db conn) honey-eid)))
)

; #todo need a function like swap!, reset!
; #toto test "scalar get" [?e .] ensure throws if > 1 result (or write qone to do it)
; write qset -> (into #{} (d/q ...))

(def dr-no (create-entity 
             :people
             { :person/name    "Dr No"
               :weapon/type    :weapon/guile } ))
(newline) (println "Added dr-no" )
(spyxx dr-no)
(spyxx (d/ident (d/db conn) (d/part dr-no)))
(show-db (d/db conn))


(println "exit")
(System/exit 1)


(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
