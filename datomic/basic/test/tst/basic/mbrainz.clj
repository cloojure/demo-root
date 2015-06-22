(ns tst.basic.mbrainz
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> ]]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

; following the samples from https://github.com/Datomic/mbrainz-sample/wiki/Queries

(set! *warn-on-reflection* false)
(set! *print-length* 5)
(set! *print-length* nil)

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

(def ^:dynamic *conn*)

; (def uri              "datomic:mem://seattle")
(def uri              "datomic:dev://localhost:4334/mbrainz-1968-1973")

(use-fixtures :once     ; #todo: what happens if more than 1 use-fixtures in test ns file ???
  (fn [tst-fn]
    (binding [*conn* (d/connect uri)]
      (tst-fn))
    ))

(deftest t-connection-verify
  (testing "can we connect to the Datomic db"
    (let [db-val    (d/db *conn*)
          rs        (t/result-set-sort
                      (d/q '[:find ?title
                             :in $ ?artist-name
                             :where
                             [?a :artist/name ?artist-name]
                             [?t :track/artists ?a]
                             [?t :track/name ?title] ]
                           db-val, "John Lennon" ))
    ]
      (is (= rs
              #{["Aisumasen (I'm Sorry)"] ["Amsterdam"] ["Angela"] ["Attica State"]
                ["Au"] ["Baby's Heartbeat"] ["Blue Suede Shoes"] ["Born in a Prison"]
                ["Bring On the Lucie (Freda Peeple)"] ["Cambridge 1969"]
                ["Cold Turkey"] ["Crippled Inside"] ["Dizzy Miss Lizzy"]
                ["Don't Worry Kyoko"]
                ["Don't Worry Kyoko (Mummy's Only Looking for Her Hand in the Snow)"]
                ["Gimme Some Truth"] ["Give Peace a Chance"] ["God"]
                ["Happy Xmas (War Is Over)"] ["Hold On"] ["How Do You Sleep?"]
                ["How?"] ["I Don't Wanna Be a Soldier Mama I Don't Wanna Die"]
                ["I Found Out"] ["I Know (I Know)"] ["Imagine"] ["Instant Karma!"]
                ["Intuition"] ["Isolation"] ["It's So Hard"] ["Jamrag"]
                ["Jealous Guy"] ["John & Yoko"] ["John John (Let's Hope for Peace)"]
                ["John Sinclair"] ["Listen the Snow Is Falling"]
                ["Listen, the Snow Is Falling"] ["Look at Me"] ["Love"] ["Meat City"]
                ["Mind Games"] ["Money"] ["Mother"] ["My Mummy's Dead"]
                ["New York City"] ["No Bed for Beatle John"]
                ["Nutopian International Anthem"] ["Oh My Love"] ["Oh Yoko!"]
                ["One Day (at a Time)"] ["Only People"] ["Open Your Box"]
                ["Out the Blue"] ["Power to the People"] ["Radio Play"] ["Remember"]
                ["Scumbag"] ["Sisters, O Sisters"] ["Sunday Bloody Sunday"]
                ["The Luck of the Irish"] ["Tight A$"] ["Two Minutes Silence"]
                ["We're All Water"] ["Well (Baby Please Don't Go)"]
                ["Well Well Well"] ["Who Has Seen the Wind?"]
                ["Woman Is the Nigger of the World"] ["Working Class Hero"]
                ["Yer Blues"] ["You Are Here"] } ))
    )))


#_(deftest t-00
  (testing "xxx"
  (let [db-val (d/db *conn*)
  ]
)))

