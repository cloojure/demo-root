(ns tst.basic.datalog
  (:require [datomic.api            :as d]
            [clojure.set            :as c.set]
            [schema.core            :as s]
            [tupelo.datomic         :as td]
            [tupelo.schema          :as ts]
  )
  (:use clojure.pprint
        clojure.test
        tupelo.core)
  (:gen-class))

(set! *warn-on-reflection* false)
(set! *print-length* nil)

(deftest t-01
  (testing "tuple lists can use symbols for either datomic or tupelo API "
    (let [tuple-list    '[ [sally  :age    21] 
                           [fred   :age    42] 
                           [ethel  :age    42]
                           [fred   :likes  pizza] 
                           [sally  :likes  opera] 
                           [ethel  :likes  sushi] ]
          res-datomic   (d/q '[:find ?e :where [?e :age 42]] tuple-list)
          res-tupelo    (td/query-set :let [$ tuple-list]
                                      :find [?e] 
                                      :where [ [?e :age 42] ] )  
    ]
      (is (= java.util.HashSet (class res-datomic)))
      (is (= (into #{} res-datomic)   #{ '[ethel] '[fred] } ))
      (is (= res-tupelo               #{ 'ethel 'fred } ))))

  (testing "keyword entities might be easier for tuple lists"
    (let [tuple-list     [ [ :sally  :age     21] 
                           [ :fred   :age     42] 
                           [ :ethel  :age     42]
                           [ :fred   :likes  :pizza] 
                           [ :sally  :likes  :opera] 
                           [ :ethel  :likes  :sushi] ]
          result    (td/query-set :let [$ tuple-list]
                                  :find [?e] 
                                  :where [ [?e :age 42] ] )
    ]
      (is (= result #{:ethel :fred} )))))

