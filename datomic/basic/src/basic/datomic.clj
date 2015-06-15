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

(def special-attribute-values
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
(s/defn create-partition :- TxResult
  "Creates a new partition in the DB"
  [conn ident]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  @(d/transact conn
    [ { :db/id                    (d/tempid :db.part/db) ; The partition :db.part/db is built-in to Datomic
        :db.install/_partition    :db.part/db
        :db/ident                 ident } ] ))

(s/defn attribute    :- {s/Keyword s/Any}
  "Returns tx-data for a attribute.  Usage:

      (create-attribute [ident value-type & options ] )

   The first 2 params are required. Others are optional and will use normal Datomic default
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
  (when-not (truthy? (safe-> special-attribute-values :db/valueType value-type))
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
  "#todo"
  ( [ attr-val-map    :- {s/Any s/Any} ]
   (new-entity :db.part/user attr-val-map))
  ( [ -partition      :- s/Keyword
      attr-val-map    :- {s/Any s/Any} ]
    (into {:db/id (d/tempid -partition) } attr-val-map)))

; #todo need test
(s/defn new-enum :- { s/Any s/Any }   ; #todo add namespace version
  "Create an enumerated-type entity"
  [ident :- s/Keyword]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  (new-entity {:db/ident ident} ))

; #todo need test
(s/defn eids :- [long]
  [tx-result :- TxResult]
  (vals (grab :tempids tx-result)))

;--------------------------------------------

(s/defn update :- { s/Any s/Any }
  "Update an entity with new or changed attribute-value pairs"
  [entity-spec    :- EntitySpec
   attr-val-map   :- {s/Any s/Any} ]
    (into {:db/id entity-spec} attr-val-map))

(s/defn retraction :- Vec4
  "Constructs & returns a tuple to retract a fact (attribute-value pair) for an entity"
  [entity-spec  :- EntitySpec
   attribute    :- s/Keyword
   value        :- s/Any ]
  [:db/retract entity-spec attribute value] )

(s/defn retraction-entity :- Vec2
  "Constructs & returns a tuple to retract all attribute-value pairs for an entity.  If any of its
   attributes have :db/isComponent=true, then the entities corresponding to that attribute will be
   recursively retracted as well."
  [entity-spec  :- EntitySpec ]
  [:db.fn/retractEntity entity-spec] )

(s/defn entity :- {s/Keyword s/Any}
  "Like datomic/entity, but eagerly copies results into a plain clojure map."
  [db-val         :- s/Any  ; #todo
   entity-spec    :- EntitySpec ]
  (into (sorted-map) (d/entity db-val entity-spec)))

(s/defn txid  :- Eid
  "Returns the transaction EID given a tx-result"
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

(s/defn partition :- s/Keyword
  [db-val       :- s/Any  ; #todo
   entity-spec  :- EntitySpec ]
  (d/ident db-val (d/part entity-spec)))

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
  (let [result-set      (d/q '{:find  [?eid]
                               :where [ [?eid :db/txInstant] ] }
                              db-val )
        res-2           (for [ [eid] result-set]
                          (entity db-val eid))
        res-4       (into (sorted-set-by #(.compareTo (grab :db/txInstant %1) (grab :db/txInstant %2) ))
                          res-2)
  ]
    (doseq [it res-4]
      (pprint it))))

