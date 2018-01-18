(ns tst.demo.data-avl
  (:use tupelo.core tupelo.test)
  (:require
    [clojure.data.avl :as avl] ))

(dotest
  (let [data [ []
              [1]
              [1 :a]
              [1 :b]
              [3 0]
              [4]
              [5 :x]
              [5 :y]
              [5 :z] ]
        ss1  (apply avl/sorted-set-by lexical-compare data)]
    (is= data (vec ss1))
    (is= 5 (avl/rank-of ss1 [4]))
    (is= (vec (avl/subrange ss1 > [3]))         [[3 0] [4] [5 :x] [5 :y] [5 :z]] )
    (is= (vec (avl/subrange ss1 > [3] < [5]))   [[3 0] [4] ] )
    (is= (vec (avl/subrange ss1 > [4]))         [[5 :x] [5 :y] [5 :z]] )
    (is= (vec (avl/subrange ss1 >= [4]))        [[4] [5 :x] [5 :y] [5 :z]] )) )

(defn join-avl      ; #todo add type checking input/output
  [set-1 set-2]     ; #todo generalize to n-way join
  (assert (has-none? nil? (seq set-1)) "nil elements not allowed")
  (assert (has-none? nil? (seq set-2)) "nil elements not allowed")
  (let [[shorter longer] (sort-by count [set-1 set-2])]
    (forv [item-loop shorter
           :let [[ignore-lesser item-found ignore-greater] (avl/split-key item-loop longer)]
           :when (not-nil? item-found)]
      item-found)))

(dotest
  (let [data-all  (into (avl/sorted-set) (range 20))
        data-even (into (avl/sorted-set) (range 0 10 2))
        data-invalid (into (avl/sorted-set) [ 1 2 nil 3])]
    (is=
      (join-avl data-all data-even)
      (join-avl data-even data-all))
    (throws? (join-avl data-invalid data-even) )
    (throws? (join-avl data-even data-invalid) )
    ))
