(ns basic.datomic
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
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

(s/defn create-entity  ; #todo add conn
  "Create a new entity in the DB with the specified attribute-value pairs."
  ( [ conn            :- s/Any  ; #todo
      attr-val-map    :- {s/Any s/Any} ]
   (create-entity :db.part/user attr-val-map))
  ( [ conn            :- s/Any  ; #todo
      -partition      :- s/Keyword
      attr-val-map    :- {s/Any s/Any} ]
    (let [new-tempid   (d/tempid -partition)
          tx-data      (into {:db/id new-tempid} attr-val-map)
          tx-result    @(d/transact conn [ tx-data ] )
          db-after  (safe-> :db-after   tx-result)
          tempids   (safe-> :tempids    tx-result)
          new-eid   (d/resolve-tempid db-after tempids new-tempid) ]
      new-eid )))  ; #todo:  maybe return a map of { :eid xxx   :tx-result yyy}

(s/defn update-entity ; #todo add conn
  "Update an entity with new or changed attribute-value pairs"
  [conn           :- s/Any  ; #todo
   entity-spec    :- EntitySpec
   attr-val-map   :- {s/Any s/Any} ] 
    (let [tx-data     (into {:db/id entity-spec} attr-val-map)
          tx-result   @(d/transact conn [ tx-data ] ) ]
      tx-result ))

(defn create-attribute-map    ; :- Eid #todo add schema
  "Creates a new attribute in the DB"
  [ident value-type & options ]
  (when-not (keyword? ident)
    (throw (IllegalArgumentException. (str "attribute ident must be keyword: " ident ))))
  (when-not (truthy? (safe-> special-attribute-values :db/valueType value-type))
    (throw (IllegalArgumentException. (str "attribute value-type invalid: " ident ))))
  (let [base-specs    { :db/id                  (d/tempid :db.part/db)
                        :db/cardinality         :db.cardinality/one
                        :db.install/_attribute  :db.part/db 
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
        tx-specs      (into base-specs option-specs)
  ]
    tx-specs
  ))

(defn create-attribute    ; :- Eid  #todo add schema
  "Creates a new attribute in the DB"
  [conn & args]
  (spy :msg "create-attribute" args)
  (let [tx-specs (apply create-attribute-map args) ]
    @(d/transact conn [tx-specs] )
    nil ; #todo what to return?
  ))

