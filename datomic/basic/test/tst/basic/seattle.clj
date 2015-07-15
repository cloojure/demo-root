(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [tupelo.core      :refer [spy spyx spyxx it-> safe-> matches? grab wild-match? ]]
            [tupelo.datomic   :as td]
            [tupelo.schema    :as ts]
            [basic.seattle-schema  :as b.ss]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

(set! *warn-on-reflection* false)
(set! *print-length* 5)
(set! *print-length* nil)
;
;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

(def ^:dynamic *conn*)

(def uri              "datomic:mem://seattle")
(def seattle-data-0   (read-string (slurp "samples/seattle/seattle-data0.edn")))
(def seattle-data-1   (read-string (slurp "samples/seattle/seattle-data1.edn")))

(use-fixtures :each
  (fn [tst-fn]
    ; Create the database & a connection to it
    (d/create-database uri)
    (let [conn (d/connect uri) ]
      (b.ss/add-schema conn)

      (s/validate ts/TxResult @(d/transact conn seattle-data-0))
      (binding [*conn* conn]
        (tst-fn))
      (d/delete-database uri))))

; Convenience function to keep syntax a bit more concise
(defn live-db [] (d/db *conn*))

(deftest t-01
  (let [eid-set (s/validate ts/Set  ; #{s/Any}
                  (td/query-set :let   [$ (live-db)]
                                :find  [?c]
                                :where [ [?c :community/name] ] ))
        _ (s/validate #{ts/Eid} eid-set)  ; verify it is a set of EIDs
        _ (is (= 150 (count eid-set)))
        _ (is (s/validate #{ts/Eid} eid-set))

        eid-1   (first eid-set)
        entity  (s/validate ts/KeyMap (td/entity-map (live-db) eid-1))
        _ (is (= (into #{} (keys entity))
                #{:community/category :community/name :community/neighborhood 
                  :community/orgtype  :community/type :community/url} ))
        entity-maps   (for [eid eid-set]
                        (td/entity-map (live-db) eid))  ; return clojure map from eid
        first-3   (it-> entity-maps
                        (sort-by :community/name it)
                        (take 3 it)
                        (vec it))
        ; *** WARNING ***  the print format {:db/id <eid>} for datomic.query.EntityMap is not its
        ; "true" contents, which looks like a ts/KeyMap of all attr-val pairs, and w/o :db/id.
        ; #todo: make sure this ^^^ gets into demo/blog post/Datomic docs.
        ;
        ; The value for :community/neighborhood is another entity (datomic.query.EntityMap) like {:db/id <eid>},
        ; where the <eid> is volatile.  We must use a wildcard to ignore this degenerate & volatile value for testing.
        ; Also, the datomic.query.EntityMap is an "active" record and will be replaced with its
        ; contents when attempt to use it: (into {} ...).
        sample-comm-nbr  (:community/neighborhood (first entity-maps))
        _ (is (= datomic.query.EntityMap (class sample-comm-nbr)))
  ]
    (is (wild-match? first-3
         [ {:community/name "15th Ave Community"
            :community/url "http://groups.yahoo.com/group/15thAve_Community/"
            :community/neighborhood :*  ; ignore the whole record (datomic.query.EntityMap)
            :community/category #{"15th avenue residents"}
            :community/orgtype :community.orgtype/community
            :community/type #{:community.type/email-list}}
           {:community/name "Admiral Neighborhood Association"
            :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"
            :community/neighborhood :*
            :community/category #{"neighborhood association"}
            :community/orgtype :community.orgtype/community
            :community/type #{:community.type/email-list}}
           {:community/name "Alki News"
            :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"
            :community/neighborhood :*
            :community/category #{"members of the Alki Community Council and residents of the Alki Beach neighborhood"}
            :community/orgtype :community.orgtype/community
            :community/type #{:community.type/email-list}} ] ))))

(deftest t-communities-1
  ; Find all communities (any entity with a :community/name attribute), then return a list of tuples
  ; like [ community-name & neighborhood-name ]
  (let [
        results           (td/query-set :let    [$ (live-db)]
                                        :find   [?c] 
                                        :where  [ [?c :community/name] ] )
        _ (s/validate #{ts/Eid} results)
        entity-maps       (sort-by :community/name 
                            (for [eid results]
                              (td/entity-map (live-db) eid)))
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
        communities-names         (into #{} (mapv :community/name communities))
        _ (is (= communities-names   #{"Capitol Hill Community Council"
                                       "KOMO Communities - Captol Hill"
                                       "15th Ave Community"
                                       "Capitol Hill Housing"
                                       "CHS Capitol Hill Seattle Blog"
                                       "Capitol Hill Triangle"} ))
  ] ))

(deftest t-communities-2
  (let [; Find all tuples of [community-eid community-name] and collect results into a regular
        ; Clojure set (the native Datomic return type is set-like but not a Clojure set, so it
        ; doesn't work right with Prismatic Schema specs)
        comms-and-names     (s/validate  #{ [ (s/one ts/Eid "comm-eid") (s/one s/Str "comm-name") ] } ; verify expected shape
                              (into (sorted-set)
                                (td/query   :let    [$ (d/db *conn*)]
                                            :find   [?comm-eid ?comm-name] ; <- shape of output RS tuples
                                            :where  [ [?comm-eid :community/name ?comm-name ] ] )))
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
        comm-names-urls     (s/validate   [ [ (s/one s/Str "?comm-name")  
                                              { :community/category [s/Str]  ; pull not return set for cardinality/many
                                                :community/url       s/Str }
                                            ] ]
                              (sort-by first 
                                (td/query-pull  :let    [$ (d/db *conn*)]
                                                :find   [ ?comm-name (pull ?comm-eid [:community/category :community/url] ) ]
                                                :where  [ [?comm-eid :community/name ?comm-name] ] )))
        _ (is (= 150 (count comm-names-urls)))

        ; We must normalize the Pull API results for :community/category from a vector of string [s/Str] to
        ; a hash-set of string #{s/Str} so we can test for the expected results.
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

(deftest t-twitter-feeds
  (testing "find the names of all communities that are twitter feeds"
    (let [comm-names  (td/query-set :let    [$ (d/db *conn*)]
                                    :find   [?name]
                                    :where  [ [?comm-eid :community/name ?name]
                                              [?comm-eid :community/type :community.type/twitter] ] )
    ]
      (is (= comm-names   #{"Columbia Citizens" "Discover SLU" "Fremont Universe"
                            "Magnolia Voice" "Maple Leaf Life" "MyWallingford"} )))))

(deftest t-ne-region
  (testing "find the names all communities in the NE region"
    (let [names-ne    (td/query-set :let    [$ (d/db *conn*)]
                                    :find   [?name]
                                    :where  [ [?com   :community/name         ?name]
                                              [?com   :community/neighborhood ?nbr]
                                              [?nbr   :neighborhood/district  ?dist]
                                              [?dist  :district/region        :region/ne] ] )
    ]
      (is (= 9 (count names-ne)))
      (is (= names-ne   #{ "Aurora Seattle" "Hawthorne Hills Community Website"
                           "KOMO Communities - U-District" "KOMO Communities - View Ridge"
                           "Laurelhurst Community Club" "Magnuson Community Garden"
                           "Magnuson Environmental Stewardship Alliance"
                           "Maple Leaf Community Council" "Maple Leaf Life" } )))))

(deftest t-all-com-name-reg
  (testing "find the names and regions of all communities"
    (let [com-name-reg  (s/validate #{ [ (s/one s/Str      "com-name") 
                                         (s/one s/Keyword  "reg-id") ] }
                          (td/query   :let    [$ (d/db *conn*)]
                                      :find   [?com-name ?reg-id] ; <- shape of result tuple
                                      :where  [ [?com   :community/name           ?com-name]
                                                [?com   :community/neighborhood   ?nbr]
                                                [?nbr   :neighborhood/district    ?dist]
                                                [?dist  :district/region          ?reg]
                                                [?reg   :db/ident                 ?reg-id] ] ))
    ]
      (is (= 132 (count com-name-reg)))
      (is (= (take 5 (into (sorted-set) com-name-reg))
             [ ["15th Ave Community"                  :region/e]
               ["Admiral Neighborhood Association"    :region/sw]
               ["Alki News"                           :region/sw]
               ["Alki News/Alki Community Council"    :region/sw]
               ["All About Belltown"                  :region/w] ] )))))

; find all communities that are either twitter feeds or facebook pages, by calling a single query
; with a parameterized type value.
(deftest t-twit-or-fb
  (let [query-fn      (fn [db-arg type-arg]
                        (td/query-set :let    [$       db-arg
                                               ?type   type-arg]
                                      :find   [?com-name]
                                      :where  [ [?com   :community/name   ?com-name]
                                                [?com   :community/type   ?type] ] ))

        com-twitter   (query-fn (d/db *conn*) :community.type/twitter)
        com-facebook  (query-fn (d/db *conn*) :community.type/facebook-page)
  ]
    (is (= 6 (count com-twitter)))
    (is (= com-twitter
           #{ "Magnolia Voice"
              "Columbia Citizens"
              "Discover SLU"
              "Fremont Universe"
              "Maple Leaf Life"
              "MyWallingford" } ))

    (is (= 9 (count com-facebook)))
    (is (= com-facebook
           #{ "Magnolia Voice"
              "Columbia Citizens"
              "Discover SLU"
              "Fauntleroy Community Association"
              "Eastlake Community Council"
              "Fremont Universe"
              "Maple Leaf Life"
              "MyWallingford"
              "Blogging Georgetown" } ))))

; In a single query, find all communities that are twitter feeds or facebook pages, using a list
; of parameters
(deftest t-list-of-params
  (let [type-ident-list   [:community.type/twitter :community.type/facebook-page]  

        result-set        (s/validate #{ [ (s/one s/Str      "com-name")
                                           (s/one s/Keyword  "type-ident") ] }
                            (td/query  :let     [ $                  (d/db *conn*)
                                                  [?type-ident ...]  type-ident-list ]
                                       :find    [?com-name ?type-ident]
                                       :where   [ [?com        :community/name ?com-name]
                                                  [?com        :community/type ?type-eid]
                                                  [?type-eid   :db/ident       ?type-ident] ] ))

                ; #todo: Need a linter!  The errors below produces 12069 results!!!
                ; (d/q '[:find  ?com-name ?com-type ?comm-type-ident
                ;        :in    $ [?type ...]
                ;        :where   [?comm        :community/name ?com-name]
                ;                 [?comm        :community/type ?com-type]
                ;                 [?comm-type   :db/ident       ?comm-type-ident] ]
                ;      (d/db *conn*)
                ;      [:community.type/twitter :community.type/facebook-page] )
  ]
    (is (= 15 (count result-set)))
    (is (= result-set  
           #{ ["Blogging Georgetown"                 :community.type/facebook-page]
              ["Columbia Citizens"                   :community.type/twitter]
              ["Columbia Citizens"                   :community.type/facebook-page]
              ["Discover SLU"                        :community.type/twitter]
              ["Discover SLU"                        :community.type/facebook-page]
              ["Eastlake Community Council"          :community.type/facebook-page]
              ["Fauntleroy Community Association"    :community.type/facebook-page]
              ["Fremont Universe"                    :community.type/twitter]
              ["Fremont Universe"                    :community.type/facebook-page]
              ["Magnolia Voice"                      :community.type/twitter]
              ["Magnolia Voice"                      :community.type/facebook-page]
              ["Maple Leaf Life"                     :community.type/twitter]
              ["Maple Leaf Life"                     :community.type/facebook-page]
              ["MyWallingford"                       :community.type/twitter]
              ["MyWallingford"                       :community.type/facebook-page] } ))))

; Find all communities that are non-commercial email-lists or commercial
; web-sites using a list of tuple parameters
(deftest t-email-commercial
  (let [rs        (s/validate #{ [ (s/one s/Str      "name") ; verify shape of output tuples
                                   (s/one s/Keyword  "type") 
                                   (s/one s/Keyword  "orgtype") ] }
                    (td/query   :let    [ $                    (d/db *conn*)
                                          [[?type ?orgtype]]   [ [:community.type/email-list  :community.orgtype/community] 
                                                                 [:community.type/website     :community.orgtype/commercial] ] ]
                                :find   [?name ?type ?orgtype] ; <- shape of output tuple
                                :where  [ [?com :community/name     ?name]
                                          [?com :community/type     ?type]
                                          [?com :community/orgtype  ?orgtype] ] ))
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

; find all community names coming before "C" in alphabetical order
(deftest t-before-letter-C
  (let [names-abc   (td/query-set :let    [$ (d/db *conn*)]
                                  :find   [?name]
                                  :where  [ [?com :community/name ?name]
                                            [(.compareTo ^String ?name "C") ?result]
                                            [(neg? ?result)] ] )
  ]
    (is (= 25 (count names-abc)))
    (is (= names-abc
          #{ "15th Ave Community"
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
             "Broadview Community Council" } ))))

(deftest t-fulltext
  (let [ ; find the community whose names includes the string "Wallingford"
    names-wall    (td/query-scalar  :let    [$ (d/db *conn*)]
                                    :find   [?com-name]
                                    :where  [ [ (fulltext  $   :community/name  "Wallingford")  [[?com  ?com-name            ]] ]   ; ignore last 2
          ;                  Usage: :where    [ (fulltext <db>  <attribute>       <val-str>)    [[?eid   ?value   ?tx  ?score]] ]
                                            ] )                                                   ;      optional--^^----^^
  ]
    (is (= names-wall "KOMO Communities - Wallingford" )))

  ; find all communities that are websites and that are about
  ; food, passing in type and search string as parameters
  (let [
    names-full-join     (s/validate #{ [ (s/one s/Str "com-name") 
                                         (s/one s/Str "com-cat") ] }
                          (td/query :let    [ $             (d/db *conn*)
                                              ?com-type     :community.type/website 
                                              ?search-word  "food" ]
                                    :find   [?com-name ?com-cat]
                                    :where  [ [?com-eid  :community/name  ?com-name]
                                              [?com-eid  :community/type  ?com-type]
                                              [ (fulltext $ :community/category ?search-word) [[?com-eid ?com-cat]] ] ] ))
  ]
    (is (= 2 (count names-full-join)))
    (is (=  names-full-join
            #{ ["Community Harvest of Southwest Seattle"  "sustainable food" ]
               ["InBallard"                               "food"             ] } ))))

(deftest t-rules-1
  (testing "find all names of all communities that are twitter feeds, using rules")
    (let [
      rules-twitter '[ ; list of all rules 
                       [ (is_comtype-twitter ?eid)                        ; rule #1: declaration (<name> & <args>)
                         [?eid :community/type :community.type/twitter]   ; match pattern 1
                       ] ; end #1
                     ]
      com-rules-tw  (s/validate #{s/Str}
                      (td/query-set   :let    [$ (d/db *conn*)    ; data srcs match $-named symbols
                                               % rules-twitter]   ; rule sets match %-named symbols
                                      :find   [?name]
                                      :where  [ [?eid :community/name ?name]      ; match pattern
                                                (is_comtype-twitter ?eid) ] ))    ; rule
    ]
      (is (= 6 (count com-rules-tw)))
      (is (= com-rules-tw   #{"Magnolia Voice" "Columbia Citizens" "Discover SLU" "Fremont Universe" 
                              "Maple Leaf Life" "MyWallingford"} ))))

(deftest t-rules-2
  (testing "find names of all communities in NE and SW regions, using rules to avoid repeating logic"
    (let [
      rules-list   '[ [ (com-region ?com-eid ?reg-ident) ; rule header
                        [?com-eid    :community/neighborhood   ?nbr]          ; rule clause
                        [?nbr        :neighborhood/district    ?dist]         ; rule clause
                        [?dist       :district/region          ?reg]          ; rule clause
                        [?reg        :db/ident                 ?reg-ident] ]  ; rule clause
                    ]
      com-ne      (s/validate #{s/Str}
                    (td/query-set :let    [$ (d/db *conn*)
                                           % rules-list ]
                                  :find   [?name]
                                  :where  [ [?com-eid :community/name ?name]
                                            (com-region ?com-eid :region/ne) ]
                           ))
      com-sw      (s/validate #{s/Str}
                    (td/query-set :let    [$ (d/db *conn*)
                                           % rules-list]
                                  :find   [?name]
                                  :where  [ [?com-eid :community/name ?name]
                                            (com-region ?com-eid :region/sw) ]
                                 ))
    ]
      (is (= 9  (count com-ne)))
      (is (=  com-ne
              #{"Aurora Seattle" "Hawthorne Hills Community Website"
                "KOMO Communities - U-District" "KOMO Communities - View Ridge"
                "Laurelhurst Community Club" "Magnuson Community Garden"
                "Magnuson Environmental Stewardship Alliance"
                "Maple Leaf Community Council" "Maple Leaf Life"} ))

      (is (= 34 (count com-sw)))
      (is (=  com-sw
              #{"Admiral Neighborhood Association" "Alki News"
                "Alki News/Alki Community Council" "ArtsWest" "Beach Drive Blog"
                "Broadview Community Council"
                "Community Harvest of Southwest Seattle"
                "Delridge Grassroots Leadership"
                "Delridge Neighborhoods Development Association"
                "Delridge Produce Cooperative" "Fauntleroy Community Association"
                "Friends of Green Lake" "Genesee-Schmitz Neighborhood Council"
                "Greenlake Community Council" "Greenlake Community Wiki"
                "Greenwood Aurora Involved Neighbors" "Greenwood Blog"
                "Greenwood Community Council"
                "Greenwood Community Council Announcements"
                "Greenwood Community Council Discussion"
                "Greenwood Phinney Chamber of Commerce"
                "Highland Park Action Committee" "Highland Park Improvement Club"
                "Junction Neighborhood Organization" "KOMO Communities - Green Lake"
                "KOMO Communities - Greenwood-Phinney"
                "KOMO Communities - Wallingford" "KOMO Communities - West Seattle"
                "Licton Springs Neighborhood " "Longfellow Creek Community Website"
                "Morgan Junction Community Association" "My Greenlake Blog"
                "MyWallingford" "Nature Consortium"} )))))

(deftest t-rules-or-logic
  (testing "find names of all communities that are in any of the southern regions and are
            social-media, using rules for OR logic"
    (let [
      or-rulelist     '[  ; rule #1: a normal chain-type rule
                          [ (region ?com-eid ?reg-ident)
                            [?com-eid    :community/neighborhood   ?nbr-eid]
                            [?nbr-eid    :neighborhood/district    ?dist-eid]
                            [?dist-eid   :district/region          ?reg-eid]
                            [?reg-eid    :db/ident                 ?reg-ident] ]

                          ; rule #2: an OR rule
                          [ (social-media? ?com-eid) [?com-eid  :community/type  :community.type/twitter] ]
                          [ (social-media? ?com-eid) [?com-eid  :community/type  :community.type/facebook-page] ]

                          ; rule #3: an OR rule
                          [ (northern?  ?com-eid) (region ?com-eid :region/ne) ]
                          [ (northern?  ?com-eid) (region ?com-eid :region/e)  ]
                          [ (northern?  ?com-eid) (region ?com-eid :region/nw) ]

                          ; rule #4: an OR rule
                          [ (southern?  ?com-eid) (region ?com-eid :region/se) ]
                          [ (southern?  ?com-eid) (region ?com-eid :region/s)  ]
                          [ (southern?  ?com-eid) (region ?com-eid :region/sw) ] ]
      social-south    (td/query-set   :let   [$ (d/db *conn*)
                                              % or-rulelist]
                                      :find  [?name]
                                      :where [ [?com-eid :community/name ?name]
                                               (southern? ?com-eid)
                                               (social-media? ?com-eid) ] )
    ]
      (is (= 4 (count social-south)))
      (is (=  social-south
              #{"Columbia Citizens"
                "Fauntleroy Community Association"
                "MyWallingford"
                "Blogging Georgetown"} )))))

(deftest t-using-transaction-times
  (testing "searching for data before/after certain times"
    (let [
      curr-db      (d/db *conn*)
      tx-instants (s/validate [s/Any] ; all transaction times, sorted in reverse order
                    (reverse (sort 
                        (td/query-set   :let    [$ curr-db]
                                        :find   [?when] 
                                        :where  [ [_ :db/txInstant ?when] ] ))))
      data1-tx-inst     (first  tx-instants)  ; last tx instant
      schema-tx-inst    (second tx-instants)  ; next-to-last tx instant

      ; query to find all communities
      communities-query-fn  (fn [db-val]
                              (td/query-set :let    [$ db-val]
                                            :find   [?com]  
                                            :where  [ [?com :community/name] ] ))

      db-asof-schema  (d/as-of curr-db schema-tx-inst)
      db-asof-data    (d/as-of curr-db data1-tx-inst)
      _ (is (=   0 (count (communities-query-fn db-asof-schema))))  ; all communities as of schema transaction
      _ (is (= 150 (count (communities-query-fn db-asof-data  ))))  ; all communities as of seed data transaction

      db-since-schema (d/since curr-db schema-tx-inst)
      _ (is (= 150 (count (communities-query-fn db-since-schema)))) ; find all communities since schema transaction

      db-since-data1  (d/since curr-db data1-tx-inst)
      _ (is (=   0 (count (communities-query-fn db-since-data1 )))) ; find all communities since seed data transaction

      ; load additional seed data file
      db-if-new-data  (:db-after (d/with curr-db seattle-data-1))
      _ (is (= 258 (count (communities-query-fn db-if-new-data )))) ; find all communities if new data is loaded
      _ (is (= 150 (count (communities-query-fn curr-db        )))) ; find all communities currently in DB

      ; submit new data tx
      _ @(d/transact *conn* seattle-data-1)
      db-val-new        (live-db)
      db-since-data2    (d/since db-val-new data1-tx-inst)

      _ (is (= 258 (count (communities-query-fn db-val-new     )))) ; find all communities currently in DB
      _ (is (= 108 (count (communities-query-fn db-since-data2 )))) ; find all communities since original seed data load transaction
    ] 
    )))


(deftest t-partitions
  (testing "adding & using a new partition"
    (td/transact *conn* (td/new-partition :communities) )                               ; create a new partition
    (td/transact *conn* (td/new-entity    :communities {:community/name "Easton"} ) )   ; add Easton to new partition
    (let [
      ; If you want a scalar result it is much better (& safer) to use td/query-scalar rather than
      ; getting the TupleSet returned by td/query and using (ffirst ...)
      belltown-eid-rs       (ffirst (td/query   :let    [$ (d/db *conn*) ]  ; returns #{ [ts/Eid] }
                                                :find   [?id]
                                                :where  [ [?id :community/name "belltown"] ] ))
      belltown-eid-scalar     (td/query-scalar  :let    [$ (d/db *conn*) ]  ; returns ts/Eid
                                                :find   [?id]
                                                :where  [ [?id :community/name "belltown"] ] )
      _ (is (= belltown-eid-rs belltown-eid-scalar))

      tx-1-result       @(td/transact *conn*   
                          (td/update belltown-eid-rs {:community/category "free stuff"} ))          ; Add "free stuff"
      tx-1-datoms       (td/tx-datoms (d/db *conn*) tx-1-result)  ; #todo add to demo
      _ (is (matches? tx-1-datoms
              [ {:e _ :a :db/txInstant        :v _              :tx _ :added true} 
                {:e _ :a :community/category  :v "free stuff"   :tx _ :added true} ] ))

      freestuff-rs-1    (td/query-scalar  :let    [$ (d/db *conn*)]
                                          :find   [?id] 
                                          :where  [ [?id :community/category "free stuff"] ] )
      _ (is (not (nil? freestuff-rs-1)) "freestuff-rs-1 (EID) must not be nil")

      tx-2-result       @(td/transact *conn* 
                          (td/retract-value belltown-eid-scalar :community/category "free stuff" )) ; Retract "free stuff"
      tx-2-datoms        (td/tx-datoms (d/db *conn*) tx-2-result)  ; #todo add to demo
      _ (is (matches? tx-2-datoms
              [ {:e _   :a :db/txInstant        :v _              :tx _ :added true} 
                {:e _   :a :community/category  :v "free stuff"   :tx _ :added false} ] ))

      freestuff-rs-2    (td/query-set   :let    [$ (d/db *conn*) ]
                                        :find   [?id]
                                        :where  [ [?id :community/category "free stuff"] ] )
      _ (is (zero? (count freestuff-rs-2)))
  ]
  )))

; For using the Datomic Pull API, it is best to user td/query-pull as it ensures you receive a List
; of Tuples (a Clojure vector-of-vectors).  This allows for repeating values, unlike all other
; functions which return a Set with no duplicates allowed.
(deftest t-pull-1
  (let [result    (td/query-pull  :let    [$ (d/db *conn*)]
                                  :find   [ (pull ?c [*]) ]
                                  :where  [ [?c :community/name] ] )
        first-5   (take 5 (sort-by #(grab :community/name (first %)) result))
        first-5-1 (first first-5)
  ]
    (is (= 150 (count result)))

    ; #todo core.match fails for this case!  Why?
    (is (wild-match? first-5
            [[{:db/id :*
               :community/name "15th Ave Community"
               :community/url "http://groups.yahoo.com/group/15thAve_Community/"
               :community/neighborhood {:db/id :*}
               :community/category ["15th avenue residents"]
               :community/orgtype {:db/id :*}
               :community/type [{:db/id :*}]}]
             [{:db/id :*
               :community/name "Admiral Neighborhood Association"
               :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"
               :community/neighborhood {:db/id :*}
               :community/category ["neighborhood association"]
               :community/orgtype {:db/id :*}
               :community/type [{:db/id :*}]}]
             [{:db/id :*
               :community/name "Alki News"
               :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"
               :community/neighborhood {:db/id :*}
               :community/category
               ["members of the Alki Community Council and residents of the Alki Beach neighborhood"]
               :community/orgtype {:db/id :*}
               :community/type [{:db/id :*}]}]
             [{:db/id :*
               :community/name "Alki News/Alki Community Council"
               :community/url "http://alkinews.wordpress.com/"
               :community/neighborhood {:db/id :*}
               :community/category ["council meetings" "news"]
               :community/orgtype {:db/id :*}
               :community/type [{:db/id :*}]}]
             [{:db/id :*
               :community/name "All About Belltown"
               :community/url "http://www.belltown.org/"
               :community/neighborhood {:db/id :*}
               :community/category ["community council"]
               :community/orgtype {:db/id :*}
               :community/type [{:db/id :*}]}]]
           ))
  
  ; This fails:
  ; (println "first-5") (pprint first-5)
  #_(is (matches? first-5
            [[{:db/id _
               :community/name "15th Ave Community"
               :community/url "http://groups.yahoo.com/group/15thAve_Community/"
               :community/neighborhood _
               :community/category ["15th avenue residents"]
               :community/orgtype _
               :community/type _}]
             [{:db/id _
               :community/name "Admiral Neighborhood Association"
               :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"
               :community/neighborhood _
               :community/category ["neighborhood association"]
               :community/orgtype _
               :community/type _}]
             [{:db/id _
               :community/name "Alki News"
               :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"
               :community/neighborhood _
               :community/category
               ["members of the Alki Community Council and residents of the Alki Beach neighborhood"]
               :community/orgtype _
               :community/type _}]
             [{:db/id _
               :community/name "Alki News/Alki Community Council"
               :community/url "http://alkinews.wordpress.com/"
               :community/neighborhood _
               :community/category ["council meetings" "news"]
               :community/orgtype _
               :community/type _}]
             [{:db/id _
               :community/name "All About Belltown"
               :community/url "http://www.belltown.org/"
               :community/neighborhood _
               :community/category ["community council"]
               :community/orgtype _
               :community/type _}]]
           ))

    ; this works
    (is (matches? first-5-1
             [{:db/id _
               :community/name "15th Ave Community"
               :community/url "http://groups.yahoo.com/group/15thAve_Community/"
               :community/neighborhood _
               :community/category ["15th avenue residents"]
               :community/orgtype _
               :community/type _}] ))
  ))

