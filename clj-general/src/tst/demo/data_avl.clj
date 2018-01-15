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
    (is= (vec (avl/subrange ss1 >= [4]))        [[4] [5 :x] [5 :y] [5 :z]] )
    ))
