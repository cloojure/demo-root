(ns basic.seattle-schema
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> ]]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

(s/defn add-schema :- nil
  [conn :- s/Any] ; #todo
  (t/transact conn 
    ; community attributes
    (t/new-attribute :community/name            :db.type/string    :db/fulltext "A community's name" )
    (t/new-attribute :community/url             :db.type/string    "A community's url" )
    (t/new-attribute :community/neighborhood    :db.type/ref       "A community's neighborhood" )
    (t/new-attribute :community/category        :db.type/string    :db.cardinality/many :db/fulltext "All community categories" )
    (t/new-attribute :community/orgtype         :db.type/ref       "A community orgtype enum value" )
    (t/new-attribute :community/type            :db.type/ref       :db.cardinality/many "Community type enum values" )

    ; neighborhood attributes
    (t/new-attribute :neighborhood/name :db.type/string :db.unique/identity "A unique neighborhood name (upsertable)")
    (t/new-attribute :neighborhood/district :db.type/ref "A neighborhood's district")

    ; district attributes
    (t/new-attribute :district/name :db.type/string :db.unique/identity "A unique district name (upsertable)")
    (t/new-attribute :district/region :db.type/ref "A district region enum value")

    ; community/org-type enum values
    (t/new-enum :community.orgtype/community)
    (t/new-enum :community.orgtype/commercial)
    (t/new-enum :community.orgtype/nonprofit)
    (t/new-enum :community.orgtype/personal)
    
    ; community/type enum values
    (t/new-enum :community.type/email-list)
    (t/new-enum :community.type/twitter)
    (t/new-enum :community.type/facebook-page)
    (t/new-enum :community.type/blog)
    (t/new-enum :community.type/website)
    (t/new-enum :community.type/wiki)
    (t/new-enum :community.type/myspace)
    (t/new-enum :community.type/ning)
    
    ; district/region enum values
    (t/new-enum :region/n)
    (t/new-enum :region/ne)
    (t/new-enum :region/e)
    (t/new-enum :region/se)
    (t/new-enum :region/s)
    (t/new-enum :region/sw)
    (t/new-enum :region/w)
    (t/new-enum :region/nw)
  ))
