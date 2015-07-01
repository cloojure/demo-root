(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [tupelo.core      :refer [spy spyx spyxx it-> safe-> matches? ]]
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

(defn result-set-sort [result-set]
  (into (sorted-set) result-set))

(deftest t-01
  (let [db-val  (d/db *conn*)
        rs1     (td/query :let   [$ db-val]
                          :find  [?c]
                          :where [ [?c :community/name] ] )
        _       (s/validate  ts/TupleSet rs1)
        rs2     (result-set-sort rs1)
        _ (is (= 150 (count rs1)))
        _ (is (s/validate #{ [ ts/Eid ] } rs1))

        eid-1   (s/validate ts/Eid (ffirst rs2))
        entity  (s/validate ts/KeyMap (td/entity-map db-val eid-1))
        _ (is (= (sort (keys entity))
                 [:community/category :community/name :community/neighborhood 
                  :community/orgtype  :community/type :community/url] ))
        entity-maps   (for [[eid] rs2]  ; destructure as we loop
                        (td/entity-map db-val eid))  ; return clojure map from eid
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
        rs                (td/query   :let    [$ db-val]
                                      :find   [?c] 
                                      :where  [ [?c :community/name] ] )
        _ (s/validate ts/TupleSet rs)
        entity-maps       (sort-by :community/name 
                            (for [[eid] rs]
                              (td/entity-map db-val eid)))
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
        comms-and-names     (s/validate  #{ [ (s/one ts/Eid "comm-eid") (s/one s/Str "comm-name") ] } ; verify expected shape
                              (into (sorted-set)
                                (td/query   :let    [$ db-val]
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
                                (td/query   :let    [$ db-val]
                                            :find   [ ?comm-name (pull ?comm-eid [:community/category :community/url] ) ]
                                            :where  [ [?comm-eid :community/name ?comm-name] ] )))
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
        comm-names  (td/query   :let    [$ db-val]
                                :find   [?name]
                                :where  [ [?comm-eid :community/name ?name]
                                          [?comm-eid :community/type :community.type/twitter] ] )
  ]
    (is (= comm-names   #{["Columbia Citizens"] ["Discover SLU"] ["Fremont Universe"]
                          ["Magnolia Voice"] ["Maple Leaf Life"] ["MyWallingford"]} ))))

(deftest t-04
  (testing "find the names all communities in the NE region"
    (let [names-ne    (td/query   :let    [$ (d/db *conn*)]
                                  :find   [?name]
                                  :where  [ [?com   :community/name         ?name]
                                            [?com   :community/neighborhood ?nbr]
                                            [?nbr   :neighborhood/district  ?dist]
                                            [?dist  :district/region        :region/ne] ] ) 
    ]
      (is (= 9 (count names-ne)))
      (is (= names-ne   #{ ["Aurora Seattle"] ["Hawthorne Hills Community Website"]
                           ["KOMO Communities - U-District"] ["KOMO Communities - View Ridge"]
                           ["Laurelhurst Community Club"] ["Magnuson Community Garden"]
                           ["Magnuson Environmental Stewardship Alliance"]
                           ["Maple Leaf Community Council"] ["Maple Leaf Life"] } )))))

(deftest t-05
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
; This is possible but ugly, since we must use eval, syntax-quote, and hard-coded symbol names
(deftest t-06
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
(deftest t-07
  (let [db-val            (d/db *conn*)
        type-ident-list   [:community.type/twitter :community.type/facebook-page]  

        result-set        (s/validate #{ [ (s/one s/Str      "com-name")
                                           (s/one s/Keyword  "type-ident") ] }
                            (td/query  :let     [ $                  db-val
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
                ;      db-val 
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
(deftest t-08
  (let [db-val    (d/db *conn*)
        rs        (s/validate #{ [ (s/one s/Str      "name")
                                   (s/one s/Keyword  "type") 
                                   (s/one s/Keyword  "orgtype") ] }
                    (td/query   :let    [ $ db-val
                                          [[?type ?orgtype]]   [ [:community.type/email-list  :community.orgtype/community] 
                                                                 [:community.type/website     :community.orgtype/commercial] ] ]
                                :find   [?name ?type ?orgtype]
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
(deftest t-09
  (let [
    names-abc     (s/validate #{s/Str}
                    (td/query-set :let    [$ (d/db *conn*)]
                                  :find   [?name]
                                  :where  [ [?com :community/name ?name]
                                            [(.compareTo ^String ?name "C") ?result]
                                            [(neg? ?result)] ] ))
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
             "Broadview Community Council" } )))

  (let [
    ; find the community whose names includes the string "Wallingford"
    names-wall    (s/validate [s/Str]
                    (td/result-only
                      (td/query   :let    [$ (d/db *conn*)] ; <db> is the only param that isn't a literal here
                                  :find   [?com-name]
                                  :where  [ [ (fulltext  $   :community/name  "Wallingford")  [[?com  ?com-name            ]] ]   ; ignore last 2
        ;                  Usage: :where    [ (fulltext <db>  <attribute>       <val-str>)    [[?eid   ?value   ?tx  ?score]] ]
                                          ] )))
  ]
    (is (= 1 (count names-wall)))
    (is (= names-wall ["KOMO Communities - Wallingford"] )))

  ; find all communities that are websites and that are about
  ; food, passing in type and search string as parameters
  (let [
    names-full-join     (s/validate #{ [ (s/one s/Str "com-name") 
                                         (s/one s/Str "com-cat") ] }
                          (td/query :let    [ $             (d/db *conn*)
                                              ?com-type     :community.type/website 
                                              ?search-word  "food" ]
                                    :find   [?com-name ?com-cat]   ; rename :find -> :select or :return???
                                    :where  [ [?com-eid  :community/name  ?com-name]
                                              [?com-eid  :community/type  ?com-type]
                                              [ (fulltext $ :community/category ?search-word) [[?com-eid ?com-cat]] ] ] ))
  ]
    (is (= 2 (count names-full-join)))
    (is (=  names-full-join
            #{ ["Community Harvest of Southwest Seattle" "sustainable food"]
               ["InBallard" "food"] } ))))

(deftest t-rules-1
  (testing "find all names of all communities that are twitter feeds, using rules")
    (let [
      db-val (d/db *conn*)
      rules-twitter '[ ; list of all rules 
                       [ (is_comtype-twitter ?eid)                                   ; rule #1: declaration (<name> & <args>)
                         [?eid :community/type :community.type/twitter]   ;          match pattern 1
                       ] ; end #1
                     ]
      com-rules-tw  (s/validate #{s/Str}
                      (td/query-set   :let    [$ db-val 
                                               % rules-twitter]
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
      db-val       (d/db *conn*)
      rules-list   '[ [ (com-region ?com-eid ?reg-ident) ; rule header
                        [?com-eid    :community/neighborhood   ?nbr]          ; rule clause
                        [?nbr        :neighborhood/district    ?dist]         ; rule clause
                        [?dist       :district/region          ?reg]          ; rule clause
                        [?reg        :db/ident                 ?reg-ident] ]  ; rule clause
                    ]
                  ; map-format query
      com-ne      (s/validate #{s/Str}
                    (td/query-set :let    [$ db-val 
                                           % rules-list ]
                                  :find   [?name]
                                  :where  [ [?com-eid :community/name ?name]
                                            (com-region ?com-eid :region/ne) ]
                           ))
                  ; list-format query
      com-sw      (s/validate #{s/Str}
                    (td/query-set :let    [$ db-val
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
  (testing "find names of all communities that are in any of the northern regions and are
            social-media, using rules for OR logic"
    (let [db-val (d/db *conn*)
      or-rulelist     '[  ; rule #1
                          [ (region ?com-eid ?reg-ident)
                            [?com-eid    :community/neighborhood   ?nbr-eid]
                            [?nbr-eid    :neighborhood/district    ?dist-eid]
                            [?dist-eid   :district/region          ?reg-eid]
                            [?reg-eid    :db/ident                 ?reg-ident] ]

                          ; rule #2
                          [ (social-media? ?com-eid) [?com-eid  :community/type  :community.type/twitter] ]
                          [ (social-media? ?com-eid) [?com-eid  :community/type  :community.type/facebook-page] ]

                          ; rule #3
                          [ (northern?  ?com-eid) (region ?com-eid :region/ne) ]
                          [ (northern?  ?com-eid) (region ?com-eid :region/e)  ]
                          [ (northern?  ?com-eid) (region ?com-eid :region/nw) ]

                          ; rule #4
                          [ (southern?  ?com-eid) (region ?com-eid :region/se) ]
                          [ (southern?  ?com-eid) (region ?com-eid :region/s)  ]
                          [ (southern?  ?com-eid) (region ?com-eid :region/sw) ] ]
      social-south    (s/validate #{s/Str}
                        (td/query-set   :let   [$ db-val 
                                                % or-rulelist]
                                        :find  [?name]
                                        :where [ [?com-eid :community/name ?name]
                                                 (southern? ?com-eid)
                                                 (social-media? ?com-eid) ] ))
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

      _ (is (=   0 (count (communities-query-fn db-asof-schema)))) ; all communities as of schema transaction
      _ (is (= 150 (count (communities-query-fn db-asof-data  )))) ; all communities as of seed data transaction

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
      db-val-new        (d/db *conn*)
      db-since-data2    (d/since db-val-new data1-tx-inst)

      _ (is (= 258 (count (communities-query-fn db-val-new     )))) ; find all communities currently in DB
      _ (is (= 108 (count (communities-query-fn db-since-data2 )))) ; find all communities since original seed data load transaction
    ] 
    )))


(deftest t-partitions
  (testing "adding & using a new partition"
    (td/transact *conn* (td/new-partition :communities) )                             ; create a new partition
    (td/transact *conn* (td/new-entity :communities {:community/name "Easton"} ) )    ; add Easton to new partition
    (let [
_ (println "#00")
      ; show format difference between query result-set and scalar output
      belltown-eid-rs       (s/validate ts/Eid 
                              (ffirst (td/query   :let    [$ (d/db *conn*) ]
                                                  :find   [?id]
                                                  :where  [ [?id :community/name "belltown"] ] )))
_ (println "#01")
      belltown-eid-scalar   (s/validate ts/Eid 
                              (td/query-scalar  :let    [$ (d/db *conn*) ]
                                                :find   [?id]
                                                :where  [ [?id :community/name "belltown"] ] ))
      _ (is (= belltown-eid-rs belltown-eid-scalar))

_ (println "#05")
      tx-1-result       @(td/transact *conn*   
                          (td/update belltown-eid-rs {:community/category "free stuff"} )) ; Add "free stuff"
      tx-1-datoms       (td/tx-datoms (d/db *conn*) tx-1-result)  ; #todo add to demo
      _ (is (matches? tx-1-datoms
              [ {:e _ :a :db/txInstant        :v _              :tx _ :added true} 
                {:e _ :a :community/category  :v "free stuff"   :tx _ :added true} ] ))

      freestuff-rs-1    (s/validate ts/Eid 
                          (td/query-scalar  :let    [$ (d/db *conn*)]
                                            :find   [?id] 
                                            :where  [ [?id :community/category "free stuff"] ] ))

_ (println "#10")
      tx-2-result       @(td/transact *conn* 
                          (td/retract-value belltown-eid-scalar :community/category "free stuff" )) ; Retract "free stuff"
      tx-2-datoms        (td/tx-datoms (d/db *conn*) tx-2-result)  ; #todo add to demo
      _ (is (matches? tx-2-datoms
              [ {:e _   :a :db/txInstant        :v _              :tx _ :added true} 
                {:e _   :a :community/category  :v "free stuff"   :tx _ :added false} ] ))

      freestuff-rs-2    (td/query-set   :let    [$ (d/db *conn*) ]
                                        :find   [?id]
                                        :where  [ [?id :community/category "free stuff"] ] )
      _ (is (= 0 (count freestuff-rs-2)))
_ (println "#20")
  ]
  )))

(deftest t-pull-1
  (let [db-val    (d/db *conn*)
        res-1     (s/validate [ts/TupleMap]  ; returns a vector of TupleMaps
                    (d/q '[:find  (pull ?c [*]) 
                           :where [?c :community/name] ]
                         db-val ))
  ]
    (println "res-1")
    (pprint (take 5 res-1))

    (is (= 150 (count res-1)))
  ; (is (= 150 (count res-2)))
  ; (is (=  (into #{} res-1)
  ;         (into #{} res-2)))
  ))

#_(deftest t-pull-1
  (println (macroexpand-1 
             '(td/query-pull  :let    [$ db-val]
                              :find   [ (pull ?c [*]) ]
                              :where  [ [?c :community/name] ] )))

  (let [db-val    (d/db *conn*)
        res-2     (s/validate [ts/TupleMap]  ; returns a vector of TupleMaps
                    (td/query-pull  :let    [$ db-val]
                                    :find   [ (pull ?c [*]) ]
                                    :where  [ [?c :community/name] ] ))
  ]
    (println "res-2")
    (pprint (take 5 res-2))
  ))

#_(deftest t-00
  (testing "xxx"
  (let [db-val (d/db *conn*)
  ]
)))

