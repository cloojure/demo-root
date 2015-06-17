(ns tst.basic.seattle
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx]]
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
          _ (pprint schema-tx)
          tx-result     (s/validate t/TxResult @(d/transact conn schema-tx))
          _ (pprint tx-result)
          data-tx       (read-string (slurp "samples/seattle/seattle-data0.edn"))
          _ @(d/transact conn schema-tx)
          _ @(d/transact conn data-tx)

    ]
      (binding [*conn* conn]
        (tst-fn))
      (d/delete-database uri)
    )))


(deftest dummy
  (let [db-val    (d/db *conn*) ]
    (spyxx db-val)
    (is true)))

