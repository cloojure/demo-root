(ns basic.seattle-schema
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> pp-str forv]]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

; A map that defines the datomic schema.  Format for each entry is [ :kw-fn-name <args>* ]
(def schema-attributes
  [
    ; community attributes
    [:new-attribute :community/name            :db.type/string    :db/fulltext "A community's name" ]
    [:new-attribute :community/url             :db.type/string    "A community's url" ]
    [:new-attribute :community/neighborhood    :db.type/ref       "A community's neighborhood" ]
    [:new-attribute :community/category        :db.type/string    :db.cardinality/many :db/fulltext "All community categories" ]
    [:new-attribute :community/orgtype         :db.type/ref       "A community orgtype enum value" ]
    [:new-attribute :community/type            :db.type/ref       :db.cardinality/many "Community type enum values" ]

    ; neighborhood attributes
    [:new-attribute :neighborhood/name :db.type/string :db.unique/identity "A unique neighborhood name (upsertable)"]
    [:new-attribute :neighborhood/district :db.type/ref "A neighborhood's district"]

    ; district attributes
    [:new-attribute :district/name :db.type/string :db.unique/identity "A unique district name (upsertable)"]
    [:new-attribute :district/region :db.type/ref "A district region enum value"]
  ] )

(def schema-enums
  [
    ; community/org-type enum values
    [:new-enum :community.orgtype/community]
    [:new-enum :community.orgtype/commercial]
    [:new-enum :community.orgtype/nonprofit]
    [:new-enum :community.orgtype/personal]
    
    ; community/type enum values
    [:new-enum :community.type/email-list]
    [:new-enum :community.type/twitter]
    [:new-enum :community.type/facebook-page]
    [:new-enum :community.type/blog]
    [:new-enum :community.type/website]
    [:new-enum :community.type/wiki]
    [:new-enum :community.type/myspace]
    [:new-enum :community.type/ning]
    
    ; district/region enum values
    [:new-enum :region/n]
    [:new-enum :region/ne]
    [:new-enum :region/e]
    [:new-enum :region/se]
    [:new-enum :region/s]
    [:new-enum :region/sw]
    [:new-enum :region/w]
    [:new-enum :region/nw]
  ] )

(s/defn transact-data :- t/TxResult
  [conn         :- s/Any
   tuple-list   :- [ [s/Any] ]  ; #todo -> TupleList ?
  ]
  (let [tx-data     (forv [ [kw-fn & fn-args] tuple-list] ; destructure to fn & args
                      (condp  = kw-fn
                        :new-attribute    (apply t/new-attribute  fn-args)
                        :new-enum         (apply t/new-enum fn-args)
                        :default
                          (throw (IllegalArgumentException. (str "unknown function kw: " kw-fn)))))
        tx-result   @(d/transact conn tx-data)
  ]
    tx-result ))

(s/defn add-schema :- nil
  [conn :- s/Any] ; #todo
  (transact-data conn schema-attributes)
  (transact-data conn schema-enums)
)


