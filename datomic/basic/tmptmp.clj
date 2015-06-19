;-----------------------------------------------------------------------------
; pull api
(def TupleMap     [ {s/Any s/Any} ] )

(s/def pull-results  :- [TupleMap]
  (d/q '[:find (pull ?c [*]) :where [?c :community/name]] 
       db-val))
(newline)
(spyx (count pull-results))
(spyxx pull-results)
(pprint (ffirst pull-results))

