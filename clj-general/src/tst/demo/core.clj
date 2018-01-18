(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require
    [tupelo.string :as ts]))

(dotest
  ; #todo need xtake and xdrop
  (let [data-3 (range 3)]
    (is= (take 1 data-3) [0    ])
    (is= (take 2 data-3) [0 1  ])
    (is= (take 3 data-3) [0 1 2])
    (is= (take 4 data-3) [0 1 2])
    (is= (take 5 data-3) [0 1 2])

    (is= (drop 1 data-3) [1 2])
    (is= (drop 2 data-3) [  2])
    (is= (drop 3 data-3) [   ])
    (is= (drop 4 data-3) [   ])
    (is= (drop 5 data-3) [   ])
  ))

(dotest
  (let [data-1d (forv [x [:a :b]
                       y [1 2 3]]
                  [x y])
        data-2d (forv [x [:a :b]]
                  (forv [y [1 2 3]]
                    [x y]))]
    ; 1-D list of 2-vectors
    (is= data-1d
      [[:a 1] [:a 2] [:a 3]
       [:b 1] [:b 2] [:b 3]])
    ; a 2-D matrix of 2-vectors (note extra level of nesting)
    (is= data-2d
      [[[:a 1] [:a 2] [:a 3]]
       [[:b 1] [:b 2] [:b 3]]])
    ; can glue matrix rows together into a 1-dim list using (apply glue <matrix>)
    (is= data-1d (apply glue data-2d)))

  ; a primitive "join"
  (is= (forv [x [0 1 2 3 4 5]
              y [0 2 4]
              :when (= x y)]
         x)
    [0 2 4])

  ; "manual" way to do a join. Note that success and failure must both be wrapped
  ; in a vector, then wrap loop in (apply glue ...)
  (is= (apply glue
         (forv [x [0 1 2 3 4 5]
                y [0 2 4]]
           (if (= x y)
             [x]
             [])))
    [0 2 4])

  ; "manual" way to join with nested `for` loops. Note that success and failure must both be wrapped
  ; in a vector, then wrap both loops in (apply glue ...)
  (is= (apply glue
         (forv [x [0 1 2 3 4 5]]
           (apply glue
             (forv [y [0 2 4]]
               (if (= x y)
                 [x]
                 [])))))
    [0 2 4])

  ; nested "join" using lazy-gen to undo nesting
  (is= (lazy-gen
         (doseq [x [0 1 2 3 4 5]]
           (doseq [y [0 2 4]]
             (if (= x y)
               (yield x)))))
    [0 2 4])

  )

