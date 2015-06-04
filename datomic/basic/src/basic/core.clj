(ns basic.core
  (:require [datomic.api      :as d]
            [cooljure.core    :refer [spyx spyxx]]
            [schema.core      :as s]
            [schema.coerce    :as coerce]
  )
  (:use   clojure.pprint
          cooljure.core
  )
  (import [java.util Set Map List])
  (:gen-class))

(set! *print-length* 10)

; Prismatic Schema coersion testing
;-----------------------------------------------------------------------------
(def CommentRequest
  { (s/optional-key :parent-comment-id) long
    :text String
    :share-services [ (s/enum :twitter :facebook :google) ]
  } )
(def cr-parser (coerce/coercer CommentRequest coerce/json-coercion-matcher))
(def raw-comment-req
  { :parent-comment-id (int 1234)
    :text "This is awesome!"
    :share-services ["twitter" "facebook"] } )
(def parsed-comment-req
  { :parent-comment-id (long 1234)
    :text "This is awesome!"
    :share-services [:twitter :facebook] } )
(assert (= (cr-parser raw-comment-req) parsed-comment-req))

;-----------------------------------------------------------------------------
; load data
(s/validate {s/Any s/Any} (into (sorted-map) {:a 1 :b 2}))

(def Eid Long)

(def uri "datomic:mem://seattle")

(d/create-database uri)
(def conn (d/connect uri))
(def schema-tx (read-string (slurp "samples/seattle/seattle-schema.edn")))
@(d/transact conn schema-tx)
(def data-tx (read-string (slurp "samples/seattle/seattle-data0.edn")))
; (spyx (first data-tx))
; (spyx (second data-tx))
; (spyx (nth data-tx 2))
@(d/transact conn data-tx)
(def db-val (d/db conn))
(spyxx db-val)

; NOTE: the "entity" and the "entity ID" (EID) are the same thing
;-----------------------------------------------------------------------------
; entity api
(def e-results (d/q '[:find ?c :where [?c :community/name]] db-val ))
(spyx (count e-results))
(spyx (class e-results))
(s/validate  #{ [ (s/one Eid "x") ] }  (into #{} e-results))

(def eid-1 (ffirst e-results))
(spyxx eid-1)
(def entity (d/entity db-val eid-1))
(spyxx  entity)
(spyx   (keys entity))
(spyx   (:community/name entity))

(newline)
(spyxx e-results)
(spyxx (first e-results))

(newline)
(def x1  (ffirst e-results))
(spyxx  x1)
(s/def x2  :- datomic.query.EntityMap
  (d/entity db-val x1))
(spyxx  x2)
; (s/validate Map x2)                     ; fails
; (s/validate {s/Any s/Any} x2)           ; fails
(s/validate {s/Any s/Any} (into {} x2))   ; ok

(newline)
(def x3  (:community/name x2))
(spyxx x3)
(spyxx x2)
(def x2b (into {} x2))
(spyxx x2b)

;-----------------------------------------------------------------------------
; pull api
(def TupleMap     [ {s/Any s/Any} ] )

(s/def pull-results  :- [TupleMap]
  (d/q '[:find (pull ?c [*]) :where [?c :community/name]] 
       db-val))
(newline)
(spyx (count pull-results))
(spyxx pull-results)
(pprint (ffirst pull-results))

;-----------------------------------------------------------------------------
; back to entity api
(newline)
(println "Community & neighborhood names:")
(pprint (map #(let [entity      (s/validate datomic.query.EntityMap
                                  (d/entity db-val (first %)))
                    comm-name   (safe-> entity :community/name)
                    nbr-name    (safe-> entity :community/neighborhood :neighborhood/name) ]
                [comm-name nbr-name] )
          e-results ))

; for the first community, get its neighborhood, then for that neighborhood, get all its
; communities, and print out their names
(s/def community      :- datomic.query.EntityMap
        (d/entity db-val (ffirst e-results)))
(s/def neighborhood   :- datomic.query.EntityMap
        (safe-> community :community/neighborhood))
(s/def communities    :- #{datomic.query.EntityMap}
        (safe-> neighborhood :community/_neighborhood ))
(binding [*print-length* 20]
  (newline)
  (println "Community #1")
  (pprint community)
  (newline)
  (println "Community #2")
  (pprint (d/touch community))
  (newline)
  (println "Communities in same neighborhood as first:")
  (pprint (mapv :community/name communities))
)

; find all communities and specify returning their names into a collection
(newline)
(print "comms & names: ")   ; a set of tuples
(s/def com-and-names :- #{ [ (s/one long "eid") (s/one s/Str "name") ] }
  (into #{} (d/q '[:find ?c ?n :where [?c :community/name ?n]] 
                 db-val)))
(assert (= 150 (spyx (count com-and-names))))
(pprint com-and-names)

; Some name strings are used by more than one entity
(def names-set
  (reduce   (fn [accum newval] (conj accum (second newval)))
            #{}
            com-and-names))
(assert (= 132 (spyx (count names-set))))
(pprint names-set)

; find all communities and specify returning their names into a collection
(newline)
(print "All com. names: ")  ; a list of names (w/o duplicates)
(s/def com-names-coll  :- [s/Str]
  (d/q '[:find [?n ...] :where [_ :community/name ?n]] 
       db-val))
(assert (= 132 (spyx (count com-names-coll))))
(pprint com-names-coll)

; find all community names & pull their urls
(newline)
(print "com. names & urls: ")   ; a list of tuples like [ Str {} ]
(s/def comm-names-urls :- [ [ (s/one s/Str ":community/name")  
                              { :community/category [s/Str]  :community/url s/Str }
                            ] ]
  (d/q '[:find ?n (pull ?c [:community/category :community/url ]) 
         :where [?c :community/name ?n] ]
       db-val))
(assert (= 150 (spyx (count comm-names-urls))))
(pprint comm-names-urls)

;-----------------------------------------------------------------------------
; find all categories for the community named "belltown"
(newline)
(print "belltown-cats-1: ")
(s/def belltown-cats-1 :- #{ [s/Any] }  ; a set of tuples
  (into #{}
    (d/q '[:find ?com ?cat
           :where [?com :community/name "belltown"]
                  [?com :community/category ?cat] ]
         db-val )))
(spyx (count belltown-cats-1))
(pprint belltown-cats-1)

; use collection syntax
(newline)
(print "belltown-cats-2: ")
(s/def belltown-cats-2 :- [ s/Str ]  ; note it is not a list of tuples
  (d/q '[:find [?cat ...]
         :where [?com :community/name "belltown"]
                [?com :community/category ?cat] ]
       db-val ))
(spyx (count belltown-cats-2))
(pprint belltown-cats-2)

; use pull api    #awt #todo

;-----------------------------------------------------------------------------
; find the names of all communities that are twitter feeds
(newline)
(print "com-twitter: ")
(s/def com-twitter :- [ s/Str ]
  (d/q '[:find [?n ...]
         :where [?com :community/name ?n]
                [?com :community/type :community.type/twitter] ]
       db-val ))
(spyx (count com-twitter))
(pprint com-twitter)

;-----------------------------------------------------------------------------
; find the names all communities in the NE region
(newline)
(print "com-ne: ")
(s/def com-ne :- [ s/Str ]
  (d/q '[:find [?name ...]
         :where [?com   :community/name         ?name]
                [?com   :community/neighborhood ?nbr]
                [?nbr   :neighborhood/district  ?dist]
                [?dist  :district/region        :region/ne] ]
       db-val ))
(spyx (count com-ne))
(pprint com-ne)

;-----------------------------------------------------------------------------
; find the names and regions of all communities
(newline)
(print "com-name-reg ")
(s/def com-name-reg :- #{ [ (s/one s/Str      "comm-name") 
                            (s/one s/Keyword  "region-id") ] }
  ; #awt #todo: re-do using pull api
  (into #{}
    (d/q '[:find ?com-name ?reg-id 
           :where [?com   :community/name           ?com-name]
                  [?com   :community/neighborhood   ?nbr]
                  [?nbr   :neighborhood/district    ?dist]
                  [?dist  :district/region          ?reg]
                  [?reg   :db/ident                 ?reg-id] ] 
          db-val )))
(spyx (count com-name-reg))
(pprint com-name-reg)

;-----------------------------------------------------------------------------
; find all communities that are either twitter feeds or facebook pages, by calling a single query with a
; parameterized type value
(newline)
(print "com-type-1 (entity)")
(def q-com-type-1   '[:find [?com-name ...]
                      :in $ ?type
                      :where [?com   :community/name   ?com-name]
                             [?com   :community/type   ?type] ] )
(s/def com-type-1-twitter :- [ s/Str ]
  (d/q q-com-type-1 db-val :community.type/twitter ))
(spyx (count com-type-1-twitter))
(pprint com-type-1-twitter)
(s/def com-type-1-fb :- [ s/Str ]
  (d/q q-com-type-1 db-val :community.type/facebook-page ))
(spyx (count com-type-1-fb))
(pprint com-type-1-fb)

(newline)
(print "com-type-2 (pull)")
(def q-com-type-2   '[:find (pull ?com [:community/name])
                      :in $ ?type
                      :where [?com :community/type ?type] ] )
(s/def com-type-2-twitter :- [ TupleMap ]
  (d/q q-com-type-2 db-val :community.type/twitter ))
(spyx (count com-type-2-twitter))
(pprint com-type-2-twitter)
(s/def com-type-2-fb :- [ TupleMap ]
  (d/q q-com-type-2 db-val :community.type/facebook-page ))
(spyx (count com-type-2-fb))
(pprint com-type-2-fb)

;-----------------------------------------------------------------------------
; In a single query, find all communities that are twitter feeds or facebook pages, using a list of
; parameters
(newline)
(print "com-twfb")    ; a set of tuples
(s/def com-twfb :- #{ [ (s/one s/Str      "com-name")
                        (s/one Eid        "comtype-eid")
                        (s/one s/Keyword  "comtype-id") 
                      ] }
  (into #{}
    (d/q '[:find ?com-name ?com-type ?type-id
           :in $ [?type ...]
           :where [?com :community/name ?com-name]
                  [?com :community/type ?com-type]
                  [?com-type :db/ident ?type-id] ]
         db-val [:community.type/twitter :community.type/facebook-page] )))
(spyx (count com-twfb))
(pprint com-twfb)

;-----------------------------------------------------------------------------
; Find all communities that are non-commercial email-lists or commercial
; web-sites using a list of tuple parameters
(newline)
(print "com-ntot")    ; a set of tuples
(s/def com-ntot :- #{ [ (s/one s/Str      "name")
                        (s/one s/Keyword  "type") 
                        (s/one s/Keyword  "orgtype") 
                      ] }
  (into #{} 
    (d/q '[:find ?name ?type ?orgtype
           :in $  [[?type ?orgtype]]
           :where [?com :community/name     ?name]
                  [?com :community/type     ?type]
                  [?com :community/orgtype  ?orgtype] ]
         db-val
         [ [:community.type/email-list  :community.orgtype/community] 
           [:community.type/website     :community.orgtype/commercial] ] )))
(spyx (count com-ntot))
(pprint com-ntot)

; find all community names coming before "C" in alphabetical order
(newline)
(print "names-abc")
(s/def names-abc :- [s/Str]
  (d/q  '[:find [?name ...]
          :where  [?com :community/name ?name]
                  [(.compareTo ?name "C") ?result]
                  [(neg? ?result)] ]
        db-val))
(spyx (count names-abc))
(pprint names-abc)

; find the community whose names includes the string "Wallingford"
(newline)
(print "names-wall")
(s/def names-wall :- [s/Str]
  (d/q '[:find [?name ...]
         :where [(fulltext $ :community/name "Wallingford") [[?com ?name]]]]
       db-val ))
(spyx (count names-wall))
(pprint names-wall)

; find all communities that are websites and that are about
; food, passing in type and search string as parameters
(newline)
(print "names-full-join")
(s/def names-full-join :- #{ [s/Str] }
  (into #{}
    (d/q '[:find ?name ?cat
           :in $ ?type ?search-word
           :where   [?com :community/name       ?name]
                    [?com :community/type       ?type]
                    [(fulltext $ :community/category ?search-word) [[?com ?cat]]]]
         db-val :community.type/website "food" )))
(spyx (count names-full-join))
(pprint names-full-join)

;-----------------------------------------------------------------------------
; find all names of all communities that are twitter feeds, using rules
(newline)
(print "com-rules-tw")
(def rules-twitter '[ [(twitter ?eid)
                       [?eid :community/type :community.type/twitter]] ] )
(s/def com-rules-tw  :- [s/Str]
  (d/q '[:find [?name ...]
         :in $ %
         :where [?eid :community/name ?name]
                (twitter ?eid) ]
       db-val
       rules-twitter ))
(spyx (count com-rules-tw))
(pprint com-rules-tw)

;-----------------------------------------------------------------------------
; find names of all communities in NE and SW regions, using rules
; to avoid repeating logic
(newline)
(println "com-rules-region")
(def rules-region '[  [(region ?com-eid ?reg-id)
                       [?com-eid    :community/neighborhood   ?nbr]
                       [?nbr        :neighborhood/district    ?dist]
                       [?dist       :district/region          ?reg]
                       [?reg        :db/ident                 ?reg-id]] ])
(s/def com-ne :- [s/Str]
  (d/q '[:find [?name ...]
         :in $ %
         :where   [?com-eid :community/name ?name]
                  (region ?com-eid :region/ne) ]
       db-val
       rules-region ))
(spyx (count com-ne))
(pprint com-ne)
(s/def com-sw :- [s/Str]
  (d/q '[:find [?name ...]
         :in $ %
         :where   [?com-eid :community/name ?name]
                  (region ?com-eid :region/sw) ]
       db-val
       rules-region ))
(spyx (count com-sw))
(pprint com-sw)

;-----------------------------------------------------------------------------
; find names of all communities that are in any of the northern
; regions and are social-media, using rules for OR logic
(newline)
(println "com-rules-region")
(def big-ruleset '[ [(region ?com-eid ?reg-ident)
                     [?com-eid    :community/neighborhood   ?nbr-eid]
                     [?nbr-eid    :neighborhood/district    ?dist-eid]
                     [?dist-eid   :district/region          ?reg-eid]
                     [?reg-eid    :db/ident                 ?reg-ident]]
                    [(social-media? ?com-eid)
                     [?com-eid    :community/type           :community.type/twitter]]
                    [(social-media? ?com-eid)
                     [?com-eid    :community/type           :community.type/facebook-page]]
                    [(northern?  ?com-eid) (region ?com-eid :region/ne) ]
                    [(northern?  ?com-eid) (region ?com-eid :region/e) ]
                    [(northern?  ?com-eid) (region ?com-eid :region/nw) ]
                    [(southern?  ?com-eid) (region ?com-eid :region/se) ]
                    [(southern?  ?com-eid) (region ?com-eid :region/s) ]
                    [(southern?  ?com-eid) (region ?com-eid :region/sw) ]
                  ] )
(s/def com-south :- [s/Str]
  (d/q  '[:find [?name ...]
          :in $ %
          :where [?com-eid :community/name ?name]
                 (southern? ?com-eid)
                 (social-media? ?com-eid) ]
        db-val big-ruleset ))
(spyx (count com-south))
(pprint com-south)

;-----------------------------------------------------------------------------
; Find all transaction times, sort them in reverse order
(newline)
(println "tx-instants")
(s/def tx-instants :- [s/Any]
  (reverse (sort 
    (d/q '[:find [?when ...] 
           :where [_ :db/txInstant ?when] ]
         db-val ))))
(spyx (count tx-instants))
(pprint tx-instants)
(def data-tx-date   (first tx-instants))
(def schema-tx-date (second tx-instants))

; make query to find all communities
(def communities-query '[:find [?com ...]  :where [?com :community/name] ] )

; find all communities as of schema transaction
(let [db-asof-schema (d/as-of db-val schema-tx-date) ]
  (spyx (count (d/q communities-query db-asof-schema))))

; find all communities as of seed data transaction
(let [db-asof-data (d/as-of db-val data-tx-date) ]
  (spyx (count (d/q communities-query db-asof-data))))


; find all communities since schema transaction
(let [db-since-schema (d/since db-val schema-tx-date) ]
  (spyx (count (d/q communities-query db-since-schema))))

; find all communities since seed data transaction
(let [db-since-data (d/since db-val data-tx-date) ]
  (spyx (count (d/q communities-query db-since-data))))


; parse additional seed data file
(def new-data-tx (read-string (slurp "samples/seattle/seattle-data1.edn")))

; find all communities if new data is loaded
(let [db-if-new-data (:db-after (d/with db-val new-data-tx)) ]
  (spyx (count (d/q communities-query db-if-new-data))))

; find all communities currently in DB
(spyx (count (d/q communities-query db-val)))

; submit new data tx
@(d/transact conn new-data-tx)
(def db-val-new (d/db conn))

; find all communities currently in DB
(spyx (count (d/q communities-query db-val-new)))

; find all communities since original seed data load transaction
(let [db-since-data (d/since db-val-new data-tx-date)]
  (spyx (count (d/q communities-query db-since-data))))

(println "exiting")
(System/exit 0)

(defn -main []
  (println "main - enter")
  (shutdown-agents)
)
