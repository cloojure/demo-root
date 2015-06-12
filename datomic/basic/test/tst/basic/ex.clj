(ns tst.basic.ex
  (:use basic.ex
        cooljure.core
        clojure.test ))

(deftest t-create-attribute-map
  (let [result  (create-attribute-map :weapon/type :db.type/keyword 
                    :db.unique/value       :db.unique/identity 
                    :db.cardinality/one    :db.cardinality/many 
                    :db/index :db/fulltext :db/isComponent :db/noHistory ) ]
    (spyx result)
    (is (truthy? (:db/id result)))
    (is (=  (dissoc result :db/id)  ; remove volatile tempid
            {:db/index true  :db/unique :db.unique/identity  :db/valueType :db.type/keyword 
             :db/noHistory true  :db/isComponent true  :db.install/_attribute :db.part/db 
             :db/fulltext true  :db/cardinality :db.cardinality/many  :db/ident :weapon/type} )))
  (let [result  (create-attribute-map :weapon/type :db.type/keyword 
                    :db.unique/identity    :db.unique/value
                    :db.cardinality/many   :db.cardinality/one
                    :db/index :db/fulltext :db/isComponent :db/noHistory ) ]
    (spyx result)
    (is (truthy? (:db/id result)))
    (is (=  (dissoc result :db/id)  ; remove volatile tempid
            {:db/index true  :db/unique :db.unique/value  :db/valueType :db.type/keyword 
             :db/noHistory true  :db/isComponent true  :db.install/_attribute :db.part/db 
             :db/fulltext true  :db/cardinality :db.cardinality/one  :db/ident :weapon/type} ))
  ))
         
