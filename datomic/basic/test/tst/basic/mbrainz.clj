(ns tst.basic.mbrainz
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
(set! *print-length* nil)
;
;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

(def ^:dynamic *conn*)

; (def uri              "datomic:mem://seattle")
(def uri              "datomic:dev://localhost:4334/mbrainz-1968-1973")

(use-fixtures :once     ; #todo: what happens if more than 1 use-fixtures in test ns file ???
  (fn [tst-fn]
    (binding [*conn* (d/connect uri)]
      (tst-fn))
    ))

(deftest t-connection-verify
  (testing "can we connect to the Datomic db"
    (let [db-val    (d/db *conn*)
          rs        (t/result-set-sort
                      (d/q '[:find ?title
                             :in $ ?artist-name
                             :where
                             [?a :artist/name ?artist-name]
                             [?t :track/artists ?a]
                             [?t :track/name ?title] ]
                           db-val, "John Lennon" ))
    ]
      (newline)
      (pprint rs)
      (newline)
    )))


#_(deftest t-00
  (testing "xxx"
  (let [db-val (d/db *conn*)
  ]
)))

