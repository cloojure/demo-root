(ns basic.ex
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce] )
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))


(def uri "datomic:mem://example")

(d/create-database uri)
(def conn (d/connect uri))

(def schema-tx (read-string (slurp "ex-schema.edn")))
@(d/transact conn schema-tx)

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
  ] )
@(d/transact conn data-tx)

; Since the name is :db.unique/value, we can use that to update our entities 
@(d/transact conn [
  ; add a new attribute
  { :db/id                        #db/id[:db.part/db]
    :db/ident                     :weapon/type
    :db/valueType                 :db.type/keyword
    :db/cardinality               :db.cardinality/many
    :db.install/_attribute        :db.part/db }
] )
@(d/transact conn [
  ; give James some weapons - map-format
  { :db/id  [:person/name "James Bond"]        :weapon/type :weapon/gun    }
  { :db/id  [:person/name "James Bond"]        :weapon/type :weapon/knife  }
  { :db/id  [:person/name "James Bond"]        :weapon/type :weapon/guile  }
    ; must use a separate maps, since cannot have multiple :weapon/type keys in one map

  ; give the Devil his due - list-format
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type :weapon/curse  ]
  [ :db/add  [:person/name "Mephistopheles"]   :weapon/type :weapon/guile  ]
    ; list format is always one "fact" (EAV) per list
] )

(def db-val (d/db conn))
(spyxx db-val)

(def res-1 (d/q '{:find [?c]
                  :where [ [?c :person/name] 
                         ] 
                 }
                db-val ))
(spyx (count res-1))
(spyxx res-1)

(doseq [it res-1]
  (let [eid     (spyxx (first it))
        entity  (d/touch (d/entity db-val eid)) 
        map-val (into (sorted-map) entity)
       ]
    (newline)
    (spyxx entity)
    (pprint map-val)
  ))

(defn -main []
  (newline)
  (println "main - enter")
  (println "main - exit")
  (shutdown-agents)
)
