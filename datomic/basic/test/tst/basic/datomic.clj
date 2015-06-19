(ns tst.basic.datomic
  (:use cooljure.core
        clojure.test )
  (:require [datomic.api      :as d]
            [basic.datomic    :as t]
            [schema.core      :as s]))

(set! *warn-on-reflection* false)
(set! *print-length* 5)
(set! *print-length* nil)

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

(def ^:dynamic *conn*)

(use-fixtures :each
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


(deftest t-vecs
  (is (= [1]   (s/validate t/Vec1 [1] )))      ; (s/validate ...) returns its arg if no errors
  (is (= [1 2] (s/validate t/Vec2 [1 2] )))
  (is (truthy? (s/validate t/Vec3 [1 2 3] )))
  (is (truthy? (s/validate t/Vec4 [1 2 3 4] )))
  (is (truthy? (s/validate t/Vec5 [1 2 3 4 5] ))))

(deftest t-new-attribute
  (let [result  (t/new-attribute :weapon/type :db.type/keyword 
                    :db.unique/value       :db.unique/identity 
                    :db.cardinality/one    :db.cardinality/many 
                    :db/index :db/fulltext :db/isComponent :db/noHistory ) ]
    (is (truthy? (:db/id result)))
    (is (=  (dissoc result :db/id)  ; remove volatile tempid
            {:db/index true  :db/unique :db.unique/identity  :db/valueType :db.type/keyword 
             :db/noHistory true  :db/isComponent true  :db.install/_attribute :db.part/db 
             :db/fulltext true  :db/cardinality :db.cardinality/many  :db/ident :weapon/type} )))
  (let [result  (t/new-attribute :weapon/type :db.type/keyword 
                    :db.unique/identity    :db.unique/value
                    :db.cardinality/many   :db.cardinality/one
                    :db/index :db/fulltext :db/isComponent :db/noHistory ) ]
    (is (truthy? (:db/id result)))
    (is (=  (dissoc result :db/id)  ; remove volatile tempid
            {:db/index true  :db/unique :db.unique/value  :db/valueType :db.type/keyword 
             :db/noHistory true  :db/isComponent true  :db.install/_attribute :db.part/db 
             :db/fulltext true  :db/cardinality :db.cardinality/one  :db/ident :weapon/type} ))
  ))
         
