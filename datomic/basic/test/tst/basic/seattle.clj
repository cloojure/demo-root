(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> ]]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

(set! *warn-on-reflection* false)
(set! *print-length* 5)
;
;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

(def ^:dynamic *conn*)

(use-fixtures :once 
  (fn [tst-fn]
    ; Create the database & a connection to it
    (let [uri           "datomic:mem://seattle"
          _ (d/create-database uri)
          conn          (d/connect uri)
          schema-tx     (read-string (slurp "samples/seattle/seattle-schema.edn"))
          data-tx       (read-string (slurp "samples/seattle/seattle-data0.edn"))
    ]
      (s/validate t/TxResult @(d/transact conn schema-tx))
      (s/validate t/TxResult @(d/transact conn data-tx))
      (binding [*conn* conn]
        (tst-fn))
      (d/delete-database uri)
    )))


(deftest t-01
  (let [db-val  (d/db *conn*)
        rs1     (d/q '{:find  [?c]     ; always prefer the map-query syntax
                       :where [ [?c :community/name] ] } 
                     db-val)
        rs2     (s/validate  t/ResultSet  (t/result-set rs1))  ; convert to clojure #{ [...] }
        _ (is (= 150 (count rs1)))
        _ (is (= 150 (count rs2)))
        _ (is (= java.util.HashSet                (class rs1)))
        _ (is (= clojure.lang.PersistentHashSet   (class rs2)))
        _ (is (s/validate #{ [ t/Eid ] } rs2))

        eid-1   (s/validate t/Eid (ffirst rs2))
        entity  (s/validate t/KeyMap (t/entity-map db-val eid-1))
        _ (is (= (keys entity) [:community/category :community/name :community/neighborhood 
                                :community/orgtype  :community/type :community/url] ))
        entity-maps   (for [[eid] rs2]  ; destructure as we loop
                        (t/entity-map db-val eid))  ; return clojure map from eid
        first-3   (it-> entity-maps
                        (sort-by :community/name it)
                        (take 3 it))

        ; The value for :community/neighborhood is another entity (datomic.query.EntityMap) like {:db/id <eid>},
        ; where the <eid> is volatile.  Replace this degenerate & volatile value with a dummy value
        ; in a plain clojure map.
        sample-comm-nbr  (:community/neighborhood (first entity-maps))
        _ (is (= datomic.query.EntityMap (class sample-comm-nbr)))
        first-3   (map #(assoc % :community/neighborhood {:db/id -1}) first-3)
    ]
      (is (=  first-3
              [ {:community/category #{"15th avenue residents"},
                 :community/name "15th Ave Community",
                 :community/neighborhood {:db/id -1}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/15thAve_Community/"}

                {:community/category #{"neighborhood association"},
                 :community/name "Admiral Neighborhood Association",
                 :community/neighborhood {:db/id -1}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"}

                {:community/category #{"members of the Alki Community Council and residents of the Alki Beach neighborhood"},
                 :community/name "Alki News",
                 :community/neighborhood {:db/id -1}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"} ] ))))

(deftest t-02
  ; Find all communities (any entity with a :community/name attribute), then return a list of tuples
  ; like [ community-name & neighborhood-name ]
  (let [db-val            (d/db *conn*)
        rs                (d/q '{:find  [?c] 
                                 :where [ [?c :community/name] ] }
                               db-val)    ; we don't always need (t/result-set (d/q ...))
        entity-maps       (sort-by :community/name 
                            (for [[eid] rs]
                              (t/entity-map db-val eid)))
        comm-nbr-names    (map #(let [entity-map  %
                                      comm-name   (safe-> entity-map :community/name)
                                      nbr-name    (safe-> entity-map :community/neighborhood :neighborhood/name) ]
                                  [comm-name nbr-name] )
                               entity-maps )
        results           (take 5 comm-nbr-names)
        _ (is (= results  [ ["15th Ave Community"                 "Capitol Hill"            ]
                            ["Admiral Neighborhood Association"   "Admiral (West Seattle)"  ]
                            ["Alki News"                          "Alki"                    ]
                            ["Alki News/Alki Community Council"   "Alki"                    ]
                            ["All About Belltown"                 "Belltown"                ] ] ))

        ; for the first community, get its neighborhood, then for that neighborhood, get all its
        ; communities, and print out their names
        first-comm                (first entity-maps)
        _ (is (= (:community/name first-comm) "15th Ave Community"))
        neighborhood              (s/validate datomic.query.EntityMap
                                    (safe-> first-comm :community/neighborhood))
        _ (is (= (:neighborhood/name neighborhood) "Capitol Hill"))

        ; Get list of communities that reference this neighborhood
        communities               (s/validate #{datomic.query.EntityMap}    ; hash-set is not sorted
                                    (safe-> neighborhood :community/_neighborhood))
        ; Pull out their names
        communities-names         (s/validate [s/Str] (mapv :community/name communities))
        _ (is (= communities-names    ["Capitol Hill Community Council"     ; names from hash-set are not sorted
                                       "KOMO Communities - Captol Hill"
                                       "15th Ave Community"
                                       "Capitol Hill Housing"
                                       "CHS Capitol Hill Seattle Blog"
                                       "Capitol Hill Triangle"] ))
  ] ))

(deftest t-02
  (let [db-val              (d/db *conn*)

        ; Find all tuples of [community-eid community-name] and collect results into a regular
        ; Clojure set (the native Datomic return type is set-like but not a Clojure set, so it
        ; doesn't work right with Prismatic Schema specs)
        comms-and-names     (s/validate  #{ [ (s/one t/Eid "comm-eid") (s/one s/Str "comm-name") ] } ; verify expected shape
                              (t/result-set
                                (d/q '{:find  [?comm-eid ?comm-name] ; <- shape of output RS tuples
                                       :where [ [?comm-eid :community/name ?comm-name ] ] } 
                                     db-val )))
        _ (is (= 150 (count comms-and-names)))   ; all communities
        ; Pull out just the community names (w/o EID) & remove duplicate names.
        names-only          (s/validate  #{s/Str} ; verify expected shape
                              (into (sorted-set) (map second comms-and-names)))
        _ (is (= 132 (count names-only)))   ; unique names
        _ (is (= (take 5 names-only)  [ "15th Ave Community"
                                        "Admiral Neighborhood Association"
                                        "Alki News"
                                        "Alki News/Alki Community Council"
                                        "All About Belltown" ] ))

        ; ---------- Pull API ----------
        ; find all community names & pull their urls
        comm-names-urls     (s/validate   [ [ (s/one s/Str ":community/name")  
                                              { :community/category [s/Str]  ; pull not return set for cardinality/many
                                                :community/url s/Str }
                                            ] ]
                              (sort-by first 
                                (d/q '{:find  [ ?comm-name 
                                                (pull ?comm-eid [:community/category :community/url] ) ]
                                       :where [ [?comm-eid :community/name ?comm-name] ] }
                                     db-val )))
        _ (is (= 150 (count comm-names-urls)))

        ; We must normalize the Pull API results for :community/category from a vector of string [s/Str] to
        ; a hash-set so we can test for the expected results.
        normalized-results    (take 5
                                (for [entry comm-names-urls]
                                  (update-in entry [1 :community/category]  ; 1 -> get 2nd item (map) in result tuple
                                             #(into (sorted-set) %))))
        _ (is (= normalized-results 
                  [ ["15th Ave Community"
                     {:community/category #{"15th avenue residents"},
                      :community/url "http://groups.yahoo.com/group/15thAve_Community/"}]
                    ["Admiral Neighborhood Association"
                     {:community/category #{"neighborhood association"},
                      :community/url
                      "http://groups.yahoo.com/group/AdmiralNeighborhood/"}]
                    ["Alki News"
                     {:community/category #{"members of the Alki Community Council and residents of the Alki Beach neighborhood"},
                      :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"}]
                    ["Alki News/Alki Community Council"
                     {:community/category #{"council meetings" "news"},
                      :community/url "http://alkinews.wordpress.com/"}]
                    ["All About Belltown"
                     {:community/category #{"community council"},
                      :community/url "http://www.belltown.org/"}] ] ))
  ] ))

(deftest t-03
  (let [db-val              (d/db *conn*)
        ; find the names of all communities that are twitter feeds
        comm-names  (s/validate #{s/Str}
                      (into (sorted-set)
                        (for [ [name]   (d/q '{:find  [?name]
                                               :where [ [?comm-eid :community/name ?name]
                                                        [?comm-eid :community/type :community.type/twitter] ] }
                                             db-val ) ]
                          name)))
  ]
    (is (= comm-names   #{"Columbia Citizens" "Discover SLU" "Fremont Universe"
                          "Magnolia Voice" "Maple Leaf Life" "MyWallingford"} ))))

(deftest t-04
  (let [db-val              (d/db *conn*)
        ; find the names all communities in the NE region
        names-ne    (s/validate #{s/Str}
                      (into (sorted-set)
                        (for [ [name]   (d/q '{:find  [?name]
                                               :where [ [?com   :community/name         ?name]
                                                        [?com   :community/neighborhood ?nbr]
                                                        [?nbr   :neighborhood/district  ?dist]
                                                        [?dist  :district/region        :region/ne] ] }
                                             db-val ) ]
                          name)))
  ]
    (is (= names-ne   #{"Aurora Seattle" "Hawthorne Hills Community Website"
                        "KOMO Communities - U-District" "KOMO Communities - View Ridge"
                        "Laurelhurst Community Club" "Magnuson Community Garden"
                        "Magnuson Environmental Stewardship Alliance"
                        "Maple Leaf Community Council" "Maple Leaf Life"} ))
    (is (= 9 (count names-ne)))))

(deftest t-05
  (let [db-val (d/db *conn*)
; find the names and regions of all communities
    com-name-reg    (s/validate #{ [ (s/one s/Str      "comm-name") 
                                     (s/one s/Keyword  "region-id") ] }
                      (into (sorted-set)
                        (d/q '{:find [?com-name ?reg-id] ; <- displays shape of result tuple
                               :where [ [?com   :community/name           ?com-name]
                                        [?com   :community/neighborhood   ?nbr]
                                        [?nbr   :neighborhood/district    ?dist]
                                        [?dist  :district/region          ?reg]
                                        [?reg   :db/ident                 ?reg-id] ] }
                              db-val )))
  ]
    (is (= 132 (count com-name-reg)))
    (is (= (take 5 com-name-reg)
           [ ["15th Ave Community"                  :region/e]
             ["Admiral Neighborhood Association"    :region/sw]
             ["Alki News"                           :region/sw]
             ["Alki News/Alki Community Council"    :region/sw]
             ["All About Belltown"                  :region/w] ] ))))

(deftest t-06
  (let [db-val (d/db *conn*)
    ; find all communities that are either twitter feeds or facebook pages, by calling a single query with a
    ; parameterized type value
    query-map     '{:find [ [?com-name ...] ]  ; collection syntax
                    :in [$ ?type]
                    :where [ [?com   :community/name   ?com-name]
                             [?com   :community/type   ?type] ] }
    com-twitter   (s/validate [s/Str]
                    (d/q query-map db-val :community.type/twitter))
    com-facebook  (s/validate [s/Str]
                    (d/q query-map db-val :community.type/facebook-page ))
  ]
    (is (= 6 (count com-twitter)))
    (is (= 9 (count com-facebook)))

    (is (= (into #{} com-twitter)  #{ "Magnolia Voice"
                                      "Columbia Citizens"
                                      "Discover SLU"
                                      "Fremont Universe"
                                      "Maple Leaf Life"
                                      "MyWallingford" } ))


    (is (= (into #{} com-facebook)  #{ "Magnolia Voice"
                                       "Columbia Citizens"
                                       "Discover SLU"
                                       "Fauntleroy Community Association"
                                       "Eastlake Community Council"
                                       "Fremont Universe"
                                       "Maple Leaf Life"
                                       "MyWallingford"
                                       "Blogging Georgetown" } ))))

(deftest t-07
  (let [db-val (d/db *conn*)
    ; In a single query, find all communities that are twitter feeds or facebook pages, using a list
    ; of parameters
    rs      (s/validate #{ [ (s/one s/Str      "com-name")
                             (s/one t/Eid      "type-eid")
                             (s/one s/Keyword  "type-ident") ] }
              (into (sorted-set)
                (t/result-set
                  (d/q '{:find [?com-name ?type-eid ?type-ident]
                         :in   [$ [?type-ident ...]]
                         :where [ [?com        :community/name ?com-name]
                                  [?com        :community/type ?type-eid]
                                  [?type-eid   :db/ident       ?type-ident] ] }
                       db-val 
                       [:community.type/twitter :community.type/facebook-page] ))))

                ; Need a linter!  The errors below produces 12069 results!!!
                ; (d/q '[:find  ?com-name ?com-type ?comm-type-ident
                ;        :in    $ [?type ...]
                ;        :where   [?comm        :community/name ?com-name]
                ;                 [?comm        :community/type ?com-type]
                ;                 [?comm-type   :db/ident       ?comm-type-ident] ]
                ;      db-val 
                ;      [:community.type/twitter :community.type/facebook-page] )
  ]
    (is (= 15 (count rs)))
    (is (= rs  #{ ["Blogging Georgetown"                 17592186045423   :community.type/facebook-page]
                  ["Columbia Citizens"                   17592186045422   :community.type/twitter]
                  ["Columbia Citizens"                   17592186045423   :community.type/facebook-page]
                  ["Discover SLU"                        17592186045422   :community.type/twitter]
                  ["Discover SLU"                        17592186045423   :community.type/facebook-page]
                  ["Eastlake Community Council"          17592186045423   :community.type/facebook-page]
                  ["Fauntleroy Community Association"    17592186045423   :community.type/facebook-page]
                  ["Fremont Universe"                    17592186045422   :community.type/twitter]
                  ["Fremont Universe"                    17592186045423   :community.type/facebook-page]
                  ["Magnolia Voice"                      17592186045422   :community.type/twitter]
                  ["Magnolia Voice"                      17592186045423   :community.type/facebook-page]
                  ["Maple Leaf Life"                     17592186045422   :community.type/twitter]
                  ["Maple Leaf Life"                     17592186045423   :community.type/facebook-page]
                  ["MyWallingford"                       17592186045422   :community.type/twitter]
                  ["MyWallingford"                       17592186045423   :community.type/facebook-page] } ))))

(deftest t-08
  (let [db-val (d/db *conn*)
    ; Find all communities that are non-commercial email-lists or commercial
    ; web-sites using a list of tuple parameters
    rs    (s/validate #{ [ (s/one s/Str      "name")
                           (s/one s/Keyword  "type") 
                           (s/one s/Keyword  "orgtype") 
                         ] }
            (into (sorted-set)
              (d/q '{:find  [?name ?type ?orgtype]
                     :in    [$ [[?type ?orgtype]] ]
                     :where [ [?com :community/name     ?name]
                              [?com :community/type     ?type]
                              [?com :community/orgtype  ?orgtype] ] }
                   db-val
                   [ [:community.type/email-list  :community.orgtype/community] 
                     [:community.type/website     :community.orgtype/commercial] ] )))
  ]
    (is (= 15 (count rs)))
    (is (= rs
           #{ ["15th Ave Community"                           :community.type/email-list  :community.orgtype/community]
              ["Admiral Neighborhood Association"             :community.type/email-list  :community.orgtype/community]
              ["Alki News"                                    :community.type/email-list  :community.orgtype/community]
              ["Ballard Moms"                                 :community.type/email-list  :community.orgtype/community]
              ["Ballard Neighbor Connection"                  :community.type/email-list  :community.orgtype/community]
              ["Beacon Hill Burglaries"                       :community.type/email-list  :community.orgtype/community]
              ["Broadview Community Council"                  :community.type/email-list  :community.orgtype/community]
              ["Discover SLU"                                 :community.type/website     :community.orgtype/commercial]
              ["Fremont Arts Council"                         :community.type/email-list  :community.orgtype/community]
              ["Georgetown Seattle"                           :community.type/email-list  :community.orgtype/community]
              ["Greenwood Community Council Announcements"    :community.type/email-list  :community.orgtype/community]
              ["Greenwood Community Council Discussion"       :community.type/email-list  :community.orgtype/community]
              ["InBallard"                                    :community.type/website     :community.orgtype/commercial]
              ["Leschi Community Council"                     :community.type/email-list  :community.orgtype/community]
              ["Madrona Moms"                                 :community.type/email-list  :community.orgtype/community] } ))))

(deftest t-09
  (let [db-val (d/db *conn*)
    ; find all community names coming before "C" in alphabetical order
    names-abc     (s/validate [s/Str]
                    (sort
                      (d/q  '{:find [ [?name ...] ] ; <- collection (vector) result
                              :where [ [?com :community/name ?name]
                                       [(.compareTo ?name "C") ?result]
                                       [(neg? ?result)] ] }
                            db-val)))
  ]
    (is (= 25 (count names-abc)))
    (is (= names-abc
           [ "15th Ave Community"
             "Admiral Neighborhood Association"
             "Alki News"
             "Alki News/Alki Community Council"
             "All About Belltown"
             "All About South Park"
             "ArtsWest"
             "At Large in Ballard"
             "Aurora Seattle"
             "Ballard Avenue"
             "Ballard Blog"
             "Ballard Chamber of Commerce"
             "Ballard District Council"
             "Ballard Gossip Girl"
             "Ballard Historical Society"
             "Ballard Moms"
             "Ballard Neighbor Connection"
             "Beach Drive Blog"
             "Beacon Hill Alliance of Neighbors"
             "Beacon Hill Blog"
             "Beacon Hill Burglaries"
             "Beacon Hill Community Site"
             "Bike Works!"
             "Blogging Georgetown"
             "Broadview Community Council" ] )))

  (let [db-val (d/db *conn*)
    ; find the community whose names includes the string "Wallingford"
    names-wall    (s/validate [s/Str]
                    (d/q '{:find  [ [?com-name ...] ]
                           :where [ [ (fulltext  $   :community/name  "Wallingford")  [[?com  ?com-name            ]] ]   ; ignore last 2
;                  Usage:  :where   [ (fulltext <db>  <attribute>       <val-str>)    [[?eid   ?value   ?tx  ?score]] ]
                                  ] }
                         db-val ; <db> is the only param that isn't a literal here
                    ))
  ]
    (is (= 1 (count names-wall)))
    (is (= names-wall ["KOMO Communities - Wallingford"] )))

  (let [db-val (d/db *conn*)
    ; find all communities that are websites and that are about
    ; food, passing in type and search string as parameters
    names-full-join     (s/validate #{ [s/Str] }
                          (t/result-set
                            (d/q '{:find [?com-name ?com-cat]   ; rename :find -> :select or :return???
                                   :where [ [?com-eid  :community/name  ?com-name]
                                            [?com-eid  :community/type  ?com-type]
                                            [ (fulltext $ :community/category ?search-word) [[?com-eid ?com-cat]]] ]
                                   :in   [$ ?com-type ?search-word] }
                                 db-val :community.type/website "food" )))
  ]
    (is (= 2 (count names-full-join)))
    (is (=  names-full-join
            #{ ["Community Harvest of Southwest Seattle" "sustainable food"]
               ["InBallard" "food"] } ))))

#_(deftest t-00
  (let [db-val (d/db *conn*)
  ]
  #_(binding [*print-length* nil] xxxxxxx)
))

#_(deftest t-00
  (let [db-val (d/db *conn*)
  ]
  #_(binding [*print-length* nil] xxxxxxx)
))

#_(deftest t-00
  (let [db-val (d/db *conn*)
  ]
  #_(binding [*print-length* nil] xxxxxxx)
))

#_(deftest t-00
  (let [db-val (d/db *conn*)
  ]
  #_(binding [*print-length* nil] xxxxxxx)
))

#_(deftest t-00
  (let [db-val (d/db *conn*)
  ]
  #_(binding [*print-length* nil] xxxxxxx)
))

