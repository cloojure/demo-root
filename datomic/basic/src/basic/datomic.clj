(ns basic.datomic
  (:refer-clojure :exclude [update partition])
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx grab glue]]
            [schema.core      :as s] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

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

(def special-attrvals
 "A map that defines the set of permissible values for use in attribute definition.

  User-defined attributes are special entities in Datomic. They are stored in the :db.part/db
  partition, and are defined by special attributes that are built-in to Datomic (this is analgous to
  the special forms that are built-in to Clojure). The root attributes are named by the following
  keywords (all in the 'db' namespace):

    :db/id
    :db/ident
    :db/valueType
    :db/cardinality
    :db/unique
    :db/doc
    :db/index
    :db/fulltext
    :db/isComponent
    :db/noHistory

  For each of these special attributes, this map defines the permissible values used for specifying
  user-defined attributes. Most special attributes are defined by a set of permissible keyword
  values. Permissible values for other special attributes are defined by a predicate function.  "
  {
  ; :db/ident #(keyword? %)

    :db/valueType
      #{ :db.type/keyword :db.type/string :db.type/boolean :db.type/long :db.type/bigint :db.type/float
         :db.type/double :db.type/bigdec :db.type/ref :db.type/instant :db.type/uuid
         :db.type/uri :db.type/bytes }

    :db/cardinality   #{ :db.cardinality/one :db.cardinality/many }

    :db/unique        #{ :db.unique/value :db.unique/identity }

  ; :db/doc #(string? %)
  ; :db/index #{ true false }
  ; :db/fulltext #{ true false }
  ; :db/isComponent #{ true false }
  ; :db/noHistory #{ true false }
  }
)

; #todo delete?
(def Vec1 [ (s/one s/Any "x1") ] )
(def Vec2 [ (s/one s/Any "x1") (s/one s/Any "x2") ] )
(def Vec3 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") ] )
(def Vec4 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") (s/one s/Any "x4") ] )
(def Vec5 [ (s/one s/Any "x1") (s/one s/Any "x2") (s/one s/Any "x3") (s/one s/Any "x4") (s/one s/Any "x5") ] )

;---------------------------------------------------------------------------------------------------
(s/defn new-partition :- {s/Keyword s/Any}
  "Returns the tx-data to create a new partition in the DB. Usage:

    (d/transact *conn* [
      (partition ident)
    ] )
  "
  [ident]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  { :db/id                    (d/tempid :db.part/db) ; The partition :db.part/db is built-in to Datomic
    :db.install/_partition    :db.part/db   ; ceremony so Datomic "installs" our new partition
    :db/ident                 ident } )     ; the "name" of our new partition

(s/defn new-attribute    :- {s/Keyword s/Any}
  "Returns the tx-data to create a new attribute in the DB.  Usage:

    (d/transact *conn* [
      (attribute ident value-type & options)
    ] )

   The first 2 params are required. Other params are optional and will use normal Datomic default
   values (false or nil) if omitted. An attribute is assumed to be :db.cardinality/one unless
   otherwise specified.  Optional values are:

      :db.unique/value
      :db.unique/identity
      :db.cardinality/one     <- assumed by default
      :db.cardinality/many
      :db/index
      :db/fulltext
      :db/isComponent
      :db/noHistory
      :db/doc                 <- *** currently unimplemented ***
  " 
  [ident value-type & options ]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  (when-not (truthy? (safe-> special-attrvals :db/valueType value-type))
    (throw (IllegalArgumentException. (str "attribute value-type invalid: " ident ))))
  (let [base-specs    { :db/id                  (d/tempid :db.part/db)
                        :db.install/_attribute  :db.part/db ; Datomic ceremony to "install" the new attribute
                        :db/cardinality         :db.cardinality/one   ; default value for most attrs
                        :db/ident               ident
                        :db/valueType           value-type }
        option-specs  (into (sorted-map)
                        (for [it options]
                          (condp = it
                            :db.unique/value       {:db/unique :db.unique/value}
                            :db.unique/identity    {:db/unique :db.unique/identity}
                            :db.cardinality/one    {:db/cardinality :db.cardinality/one}
                            :db.cardinality/many   {:db/cardinality :db.cardinality/many}
                            :db/index              {:db/index true}
                            :db/fulltext           {:db/fulltext true}
                            :db/isComponent        {:db/isComponent true}
                            :db/noHistory          {:db/noHistory true}
                            :db/doc
                              (throw (IllegalArgumentException. ":db/doc not yet implemented"))
                      )))
        tx-specs      (glue base-specs option-specs)
  ]
    tx-specs
  ))

; #todo need test
(s/defn new-entity  :- { s/Any s/Any }
  "Returns the tx-data to create a new entity in the DB. Usage:

    (d/transact *conn* [
      (new-entity attr-val-map)                 ; default partition -> :db.part/user 
      (new-entity partition attr-val-map)       ; user-specified partition
    ] )

   where attr-val-map is a Clojure map containing attribute-value pairs to be added to the new
   entity."
  ( [ attr-val-map    :- {s/Any s/Any} ]
   (new-entity :db.part/user attr-val-map))
  ( [ -partition      :- s/Keyword
      attr-val-map    :- {s/Any s/Any} ]
    (into {:db/id (d/tempid -partition) } attr-val-map)))

; #todo need test
(s/defn new-enum :- { s/Any s/Any }   ; #todo add namespace version
  "Returns the tx-data to create a new enumeration entity in the DB. Usage:

    (d/transact *conn* [
      (new-entity ident)
    ] )

  where ident is the (keyword) name for the new enumeration entity.  "
  [ident :- s/Keyword]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  (new-entity {:db/ident ident} ))

; #todo  -  document entity-spec as EID or refspec in all doc-strings
; #todo  -  document use of "ident" in all doc-strings
(s/defn update :- { s/Any s/Any }
  "Returns the tx-data to update an existing entity  Usage:

    (d/transact *conn* [
      (update entity-spec attr-val-map)
    ] )

   where attr-val-map is a Clojure map containing attribute-value pairs to be added to the new
   entity.  For attributes with :db.cardinality/one, the previous value will be (automatically)
   retracted prior to the insertion of the new value. For attributes with :db.cardinality/many, the
   new value will be accumulated into the current set of values.  "
  [entity-spec    :- EntitySpec
   attr-val-map   :- {s/Any s/Any} ]
    (into {:db/id entity-spec} attr-val-map))

(s/defn retraction :- Vec4
  "Returns the tx-data to retract an attribute-value pair for an entity. Usage:

    (d/transact *conn* [
      (retraction entity-spec attribute value)
    ] )

   where the attribute-value pair must exist for the entity or the retraction will fail.  " ; #todo verify
  [entity-spec  :- EntitySpec
   attribute    :- s/Keyword
   value        :- s/Any ]
  [:db/retract entity-spec attribute value] )

(s/defn retraction-entity :- Vec2
  "Returns the tx-data to retract all attribute-value pairs for an entity.  
   
    (d/transact *conn* [
      (retraction-entity entity-spec)
    ] )
   
  For any of the entity's attributes with :db/isComponent=true, the corresponding entities 
  will be recursively retracted as well."
  [entity-spec  :- EntitySpec ]
  [:db.fn/retractEntity entity-spec] )

(s/defn entity :- {s/Keyword s/Any}
  "Eagerly returns an entity's attribute-value pairs in a clojure map.  A simpler, eager version of
   datomic/entity."
  [db-val         :- s/Any  ; #todo
   entity-spec    :- EntitySpec ]
  (into (sorted-map) (d/entity db-val entity-spec)))

; #todo need test
(s/defn eids :- [long]
  "Returns a collection of the EIDs created in a transaction."
  [tx-result :- TxResult]
  (vals (grab :tempids tx-result)))

(s/defn txid  :- Eid
  "Returns the EID of a transaction"
  [tx-result]
  (let [datoms  (grab :tx-data tx-result)
        result  (it-> datoms        ; since all datoms in tx have same txid
                      (first it)    ; we only need the first datom
                      (nth it 3))   ; tx EID is at index 3 in [e a v tx added] vector
  ]
    (when false  ; for testing
      (let [txids   (for [it datoms] (nth it 3)) ]
        (spyxx txids)
        (assert (apply = txids))))
    result ))

(s/defn partition-name :- s/Keyword
  "Returns the name of a DB partition (its :db/ident value)"
  [db-val       :- s/Any  ; #todo
   entity-spec  :- EntitySpec ]
  (d/ident db-val (d/part entity-spec)))

(s/defn transactions :- [ {s/Keyword s/Any} ]
  "Returns a collection of all DB transactions"
  [db-val :- s/Any ]
  (let [result-set    (d/q  '{:find  [?eid]
                              :where [ [?eid :db/txInstant] ] } 
                            db-val)
        tx-ents       (for [[eid] result-set]
                        (entity db-val eid)) ]
        tx-ents))

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
            map-val (entity db-val eid)
           ]
        (newline)
        (pprint map-val))))

(defn show-db-tx
  "Display all transactions in the DB"
  [db-val]
  (println "-----------------------------------------------------------------------------")
  (println "Database Transactions")
  (let [result    (into (sorted-set-by #(.compareTo (grab :db/txInstant %1) (grab :db/txInstant %2) ))
                        (transactions db-val)) ]
    (doseq [it result]
      (pprint it))))

