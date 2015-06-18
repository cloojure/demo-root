(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> ]]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

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


(deftest t1
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

(deftest t2
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

(deftest t2
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

(deftest t3
  (let [db-val              (d/db *conn*)

  ]

))

