(require '[datomic.api :as d])

(def tuples-qq  [[ 'sally  :age     21] 
                 [ 'fred   :age     42] 
                 [ 'ethel  :age     42]
                 [ 'fred   :likes  'pizza] 
                 [ 'sally  :likes  'opera] 
                 [ 'ethel  :likes  'sushi]])
(d/q '[:find ?e :where [?e :age 42]] tuples-qq)

(def tuples-q1 '[[  sally  :age     21] 
                 [  fred   :age     42] 
                 [  ethel  :age     42]
                 [  fred   :likes   pizza] 
                 [  sally  :likes   opera] 
                 [  ethel  :likes   sushi]])
(d/q '[:find ?e :where [?e :age 42]] tuples-q1)

(def tuples-kw  [[ :sally  :age    21] 
                 [ :fred   :age    42] 
                 [ :ethel  :age    42]
                 [ :fred   :likes  :pizza] 
                 [ :sally  :likes  :opera] 
                 [ :ethel  :likes  :sushi]])
(d/q '[:find ?e :where [?e :age 42]] tuples-kw)

