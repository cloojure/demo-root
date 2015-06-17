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
        ; where the <eid> is volatile.  Replace this degenerate & volatile value with a plain clojure map like
        ; { :neighborhood/name <some-string> }
        sample-comm-nbr  (:community/neighborhood (first entity-maps))
        _ (is (= datomic.query.EntityMap (class sample-comm-nbr)))
        first-3   (map #(assoc % :community/neighborhood 
                                   {:neighborhood/name (-> % :community/neighborhood :neighborhood/name) } )
                       first-3)
    ]
      (is (=  first-3
              [ {:community/category #{"15th avenue residents"},
                 :community/name "15th Ave Community",
                 :community/neighborhood {:neighborhood/name "Capitol Hill"}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/15thAve_Community/"}

                {:community/category #{"neighborhood association"},
                 :community/name "Admiral Neighborhood Association",
                 :community/neighborhood {:neighborhood/name "Admiral (West Seattle)"}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"}

                {:community/category #{"members of the Alki Community Council and residents of the Alki Beach neighborhood"},
                 :community/name "Alki News",
                 :community/neighborhood {:neighborhood/name "Alki"}
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"} ] ))
  ))

(deftest t2
  ; Find all communities (any entity with a :community/name attribute), then return a list of tuples
  ; like [ community-name & neighborhood-name ]
  (let [db-val            (d/db *conn*)
        rs                (d/q '{:find  [?c] 
                                 :where [ [?c :community/name] ] }
                               db-val)    ; we don't always need (t/result-set (d/q ...))
        entity-maps       (for [[eid] rs]
                            (t/entity-map db-val eid))
        comm-nbr-names    (map #(let [entity-map  %
                                      comm-name   (safe-> entity-map :community/name)
                                      nbr-name    (safe-> entity-map :community/neighborhood :neighborhood/name) ]
                                  [comm-name nbr-name] )
                               entity-maps )
        results           (take 5 (sort comm-nbr-names))
    ]
      (is (= results  [ ["15th Ave Community" "Capitol Hill"]
                        ["Admiral Neighborhood Association" "Admiral (West Seattle)"]
                        ["Alki News" "Alki"]
                        ["Alki News/Alki Community Council" "Alki"]
                        ["All About Belltown" "Belltown"] ] ))
  ))

