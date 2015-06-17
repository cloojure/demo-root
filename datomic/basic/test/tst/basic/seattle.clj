(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> ]]
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


(deftest basic ; entity api
  (let [db-val  (d/db *conn*)
        rs1     (d/q '[:find ?c :where [?c :community/name]] db-val)
        rs2     (s/validate  t/ResultSet  (t/result-set rs1))  ; convert to clojure #{ [...] }
        _ (is (= 150 (count rs1)))
        _ (is (= 150 (count rs2)))
        _ (is (= java.util.HashSet                (class rs1)))
        _ (is (= clojure.lang.PersistentHashSet   (class rs2)))
        _ (is (s/validate #{ [ t/Eid ] } rs2))
        eid-1 (s/validate t/Eid (ffirst rs2))
        _ (spyxx eid-1)
        entity (s/validate t/KeyMap (t/entity-map db-val eid-1))
        _ (spyx entity)
        _ (is (= (keys entity) [:community/category :community/name :community/neighborhood 
                                :community/orgtype  :community/type :community/url] ))
        entity-maps   (for [[eid] rs2]  ; destructure as we loop
                        (t/entity-map db-val eid))
        _ (spyx entity-maps)
        first-3   (it-> entity-maps
                        (sort-by :community/name it)
                        (take 3 it)
                        (map #(assoc % :community/neighborhood {:db/id -1}) it))  ; dummy EID 
        _ (spyx first-3)
    ]
      (is (=  first-3
              [ {:community/category #{"15th avenue residents"},
                 :community/name "15th Ave Community",
                 :community/neighborhood {:db/id -1},
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/15thAve_Community/"}

                {:community/category #{"neighborhood association"},
                 :community/name "Admiral Neighborhood Association",
                 :community/neighborhood {:db/id -1},
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/AdmiralNeighborhood/"}

                {:community/category #{"members of the Alki Community Council and residents of the Alki Beach neighborhood"},
                 :community/name "Alki News",
                 :community/neighborhood {:db/id -1},
                 :community/orgtype :community.orgtype/community,
                 :community/type #{:community.type/email-list},
                 :community/url "http://groups.yahoo.com/group/alkibeachcommunity/"} ] ))
  ))

