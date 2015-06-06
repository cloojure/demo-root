(ns basic.ex
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))


(def Eid Long)
(def TxResult  {  :db-before    s/Any
                  :db-after     s/Any
                  :tx-data      s/Any
                  :tempids      s/Any } )

(def uri "datomic:mem://example")
(d/create-database uri)
(def conn (d/connect uri))
(def schema-tx (read-string (slurp "ex-schema.edn")))
@(d/transact conn schema-tx)

(defn show-db [db-val]
  (println "-----------------------------------------------------------------------------")
    (s/def res-1 :- #{ [Eid] }
      (into #{}
        (d/q '{:find [?c]
               :where [ [?c :person/name]
                      ]
              }
             db-val )))
  ; (spyx (count res-1))
  ; (spyxx res-1)

    (doseq [it res-1]
      (let [eid     (first it)
            entity  (d/touch (d/entity db-val eid))
            map-val (into (sorted-map) entity)
           ]
        (newline)
      ; (spyxx entity)
        (pprint map-val)
      )))


(defn show-db-tx [db-val]
  (newline)
  (println "-----------------------------------------------------------------------------")
  (println "Database Transactions")
  (let [
        res-1     (into #{}
                    (d/q '{:find [?c]
                           :where [ [?c :db/txInstant] ] }
                         db-val ))
        res-2     (for [it res-1]
                    (let [eid     (first it)
                          entity  (d/entity db-val eid)
                          map-val (into (sorted-map) entity) ]
                      map-val ))
        res-4     (into (sorted-set-by #(.compareTo (:db/txInstant %1) (:db/txInstant %2) ))
                    res-2)
  ]
    (doseq [it res-4]
      (pprint it))))

(def data-tx
  [
    ; map-format transaction data:
    {:db/id #db/id[:people -007]                                        ; entity specification
                                    :person/name      "James Bond"      ; multiple attribute/value pairs
                                    :person/ssn-usa   "123-45-6789"     ; multiple attribute/value pairs
                                    :person/ssn-uk    "123-45-6789"}    ; multiple attribute/value pairs

  ; list-format transaction data
  ; [<action>   <entity tmpid>      <attribute>        <value>  ]
    [:db/add  #db/id[:people -666]  :person/name      "Mephistopheles"]
    [:db/add  #db/id[:people -666]  :person/ssn-hell  "123-45-6789"]
              ; [<partition> <tmpid>]
              ; Note that <partition> could be namespaced like :beings.sentient/people
  ] )
@(d/transact conn data-tx)

; Add a new attribute. This must be done in a separate tx before we attempt to use the new attribute.
@(d/transact conn [
  { :db/id                  #db/id[:db.part/db]
    :db/ident               :weapon/type
    :db/valueType           :db.type/keyword
    :db/cardinality         :db.cardinality/many
    :db.install/_attribute  :db.part/db }
] )

; Since the name is :db.unique/value, we can use that to update our entities
@(d/transact conn [
  ; give James some weapons - map-format
  { :db/id  [:person/name "James Bond"]
        :weapon/type  #{:weapon/gun :weapon/knife :weapon/guile}  }
        ; must use a a set for multiple values of a single attr :weapon/type

  ; give the Devil his due - list-format (using a set for attr-value does not work here)
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type :weapon/curse  ]
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type :weapon/guile  ]
    ; list format is always one "fact" (EAV) per list
] )

; Updated James' name. Note that we can use the current value of name for lookup, then add in the
; new name w/o conflict.
@(d/transact conn [
  { :db/id  [:person/name "James Bond"]  :person/name "Bond, James Bond" }
] )

(newline) (println "initial db")
(show-db (d/db conn))

; James has dropped his knife...
@(d/transact conn [
  [:db/retract  [:person/name "Bond, James Bond"] :weapon/type  :weapon/knife]
] )
(newline) (println "dropped knife")
(show-db (d/db conn))

; Add a new weapon type
@(d/transact conn [
  [:db/add  #db/id[:db.part/user]  :weapon/type  :weapon/feminine-charm]
] )

(s/defn txid  :- TxResult
  "Returns the transaction EID given a tx-result"
  [tx-result]
  (let [datoms      (safe-> :tx-data tx-result)
        result  (as-> datoms it     ; since all datoms in tx have same txid
                      (first it)    ; we only need the first datom
                      (nth it 3))   ; tx EID is at index 3 in [e a v tx added] vector
  ]
    (when false  ; for testing
      (let [txids   (for [it datoms] (nth it 3)) ]
        (spyxx txids)
        (assert (apply = txids))))
    result ))


; Add Honey Rider & annotate the tx
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
(println "exit")
(System/exit 1)


(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
