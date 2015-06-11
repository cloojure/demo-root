(ns basic.tmp
  (:use   clojure.pprint
          cooljure.core)
  (:gen-class))
(newline)

(defprotocol EntityUpdate
  "Update attribute-value pairs for an existing entity"
  (update-entity [entity-spec attr-val-map] "do update"))

(extend-protocol EntityUpdate
  clojure.lang.PersistentVector
    (update-entity [entity-spec attr-val-map] "vec")
  clojure.lang.Keyword
    (update-entity [entity-spec attr-val-map] "kw")
  java.lang.Long
    (update-entity [entity-spec attr-val-map] "long"))

(spyx (update-entity [] {}))
(spyx (update-entity :a {}))
(spyx (update-entity 42 {}))


(newline)
(defn -main []
)
