(t/transact *conn* 
 ;;-----------------------------------------------------------------------------
 ;; community
  (t/new-attribute :community/name            :db.type/string    :db/fulltext "A community's name" )
  (t/new-attribute :community/url             :db.type/string    "A community's url" )
  (t/new-attribute :community/neighborhood    :db.type/ref       "A community's neighborhood" )
  (t/new-attribute :community/category        :db.type/string    :db.cardinality/many :db/fulltext "All community categories" )
  (t/new-attribute :community/orgtype         :db.type/ref       "A community orgtype enum value" )
  (t/new-attribute :community/type            :db.type/ref       :db.cardinality/many "Community type enum values" )

 ;;-----------------------------------------------------------------------------
 ;; neighborhood
 {:db/id                        #db/id[:db.part/db]
  :db/ident                     :neighborhood/name
  :db/valueType                 :db.type/string
  :db/cardinality               :db.cardinality/one
  :db/unique                    :db.unique/identity
  :db/doc                       "A unique neighborhood name (upsertable)"
  :db.install/_attribute        :db.part/db}

 {:db/id                        #db/id[:db.part/db]
  :db/ident                     :neighborhood/district
  :db/valueType                 :db.type/ref
  :db/cardinality               :db.cardinality/one
  :db/doc                       "A neighborhood's district"
  :db.install/_attribute        :db.part/db}

 ;;-----------------------------------------------------------------------------
 ;; district
 {:db/id                        #db/id[:db.part/db]
  :db/ident                     :district/name
  :db/valueType                 :db.type/string
  :db/cardinality               :db.cardinality/one
  :db/unique                    :db.unique/identity
  :db/doc                       "A unique district name (upsertable)"
  :db.install/_attribute        :db.part/db}

 {:db/id                        #db/id[:db.part/db]
  :db/ident                     :district/region
  :db/valueType                 :db.type/ref
  :db/cardinality               :db.cardinality/one
  :db/doc                       "A district region enum value"
  :db.install/_attribute        :db.part/db}

 ;;-----------------------------------------------------------------------------
 ;; community/org-type enum values
 [:db/add #db/id[:db.part/user]   :db/ident :community.orgtype/community]
 [:db/add #db/id[:db.part/user]   :db/ident :community.orgtype/commercial]
 [:db/add #db/id[:db.part/user]   :db/ident :community.orgtype/nonprofit]
 [:db/add #db/id[:db.part/user]   :db/ident :community.orgtype/personal]

 ;; community/type enum values
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/email-list]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/twitter]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/facebook-page]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/blog]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/website]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/wiki]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/myspace]
 [:db/add #db/id[:db.part/user]   :db/ident :community.type/ning]

 ;; district/region enum values
 [:db/add #db/id[:db.part/user]   :db/ident :region/n]
 [:db/add #db/id[:db.part/user]   :db/ident :region/ne]
 [:db/add #db/id[:db.part/user]   :db/ident :region/e]
 [:db/add #db/id[:db.part/user]   :db/ident :region/se]
 [:db/add #db/id[:db.part/user]   :db/ident :region/s]
 [:db/add #db/id[:db.part/user]   :db/ident :region/sw]
 [:db/add #db/id[:db.part/user]   :db/ident :region/w]
 [:db/add #db/id[:db.part/user]   :db/ident :region/nw]
 ]
