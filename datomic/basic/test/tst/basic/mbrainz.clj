(ns tst.basic.mbrainz
  (:require [datomic.api      :as d]
            [clojure.set      :as c.set]
            [clojure.core.match     :refer [match] ]
            [schema.core      :as s]
            [cooljure.core    :refer [spyx spyxx it-> safe-> grab submap? match?] ]
            [basic.datomic    :as t]
  )
  (:use clojure.pprint
        clojure.test)
  (:gen-class))

; following the samples from https://github.com/Datomic/mbrainz-sample/wiki/Queries

(set! *warn-on-reflection* false)
(set! *print-length* 9)
(set! *print-length* nil)

;---------------------------------------------------------------------------------------------------
; Prismatic Schema type definitions
(s/set-fn-validation! true)   ; #todo add to Schema docs

; (def uri              "datomic:mem://seattle")
(def uri        "datomic:dev://localhost:4334/mbrainz-1968-1973")
(def conn       (d/connect uri))
(def db-val     (d/db conn))

; specify lookup-ref values for test entities
(def led-zeppelin               [:artist/gid  #uuid "678d88b2-87b0-403b-b63d-5da7465aecc3"])
(def mccartney                  [:artist/gid  #uuid "ba550d0e-adac-4864-b88b-407cab5e76af"])
(def dark-side-of-the-moon      [:release/gid #uuid "24824319-9bb8-3d1e-a2c5-b8b864dafd1b"])
(def concert-for-bangla-desh    [:release/gid #uuid "f3bdff34-9a85-4adc-a014-922eef9cdaa5"])
(def dylan-harrison-sessions    [:release/gid #uuid "67bbc160-ac45-4caf-baae-a7e9f5180429"])

; test entitie EID values found by entity query
(def dylan-harrison-cd    (d/q  '[:find ?medium .
                                  :in $ ?release
                                  :where
                                  [?release :release/media ?medium]]
                                db-val
                                dylan-harrison-sessions))
(s/validate t/Eid dylan-harrison-cd)
(def ghost-riders       (d/q '{:find [?track .]
                               :in [$ ?release ?trackno]
                               :where  [ [?release :release/media ?medium]
                                         [?medium :medium/tracks ?track]
                                         [?track :track/position ?trackno] ] }
                             db-val dylan-harrison-sessions 11 ))
(s/validate t/Eid ghost-riders)

;---------------------------------------------------------------------------------------------------
; (use-fixtures :once     ; #todo: what happens if more than 1 use-fixtures in test ns file ???
;   (fn [tst-fn]
;       (tst-fn))
;     ))
;---------------------------------------------------------------------------------------------------
(deftest t-connection-verify
  (testing "can we connect to the Datomic db"
    (let [rs        (t/result-set-sort
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


(deftest t-attribute-name
  (testing "scalar value lookup"
    (let [res1          (d/pull db-val [:artist/name :artist/startYear]   led-zeppelin)
    ]
      (is (= res1       {:artist/name "Led Zeppelin", :artist/startYear 1968} ))))
  (testing "entity ref lookup"
    (let [res2          (d/pull db-val [:artist/country]                  led-zeppelin)

          ; since :artist/country is a nested entity, we convert the EID (long) value to the
          ; :db/ident (keyword) value
          res-ident     (update-in res2  [:artist/country :db/id] #(t/eid->ident db-val %) )
    ]
      (is (= res-ident  {:artist/country {:db/id :country/GB}} ))))
  (testing "reverse lookup"
    (let [result              (d/pull db-val [:artist/_country] :country/GB)
          _                   (s/validate {:artist/_country [ {:db/id t/Eid} ] } result )
          eid-map-list        (:artist/_country result)
          artist-ents         (for [eid-map eid-map-list
                                    :let [eid (grab :db/id eid-map)] ]
                                (do
                                  (s/validate t/Eid eid)
                                  (t/entity-map-sort db-val eid)))
          _                   (s/validate [t/KeyMap] artist-ents)
          artist-countries    (mapv :artist/country artist-ents)
    ]
      (is (=  1 (count result)))
      (is (=  482
              (count eid-map-list)
              (count artist-ents)
              (count artist-countries)))
      (is (apply = :country/GB artist-countries))))
)

; #todo need a comparison like Prismatic Schema coercion:
;   allow explicit matches like {:a 1} instead of {:a s/Any} or {:a Long}
;   maybe pprint error messages
;     get rid of 3rd param for (s/one ... "x") ?
;   any way to allow glob-like counts:
;     Eid?    - 0/1         (maybe/zero-or-one/0-or-1/0-1)
;     Eid*    - 0 or more   (zero-plus/zero-up/min-zero/0-or-more/0-plus)
;     Eid+    - 1 or more   (one-plus/one-up/min-one/1-or-more/1-plus)

; shape of pull result:
;     {:release/media
;       [
;         { :db/id 17592186121277,
;           :medium/format {:db/id 17592186045741},
;           :medium/position 1,
;           :medium/trackCount 10,
;           :medium/tracks
;             [
;               { :db/id 17592186121278,
;                 :track/duration 68346,
;                 :track/name "Speak to Me",
;                 :track/position 1,
;                 :track/artists [  {:db/id 17592186046909} ] }
;               { :db/id 17592186121279,
;                 :track/duration 168720,
;                 :track/name "Breathe",
;                 :track/position 2,
;                 :track/artists [  {:db/id 17592186046909} ] }
(deftest t-components
  (testing "component defaults - horrible name!"  ; #todo
    (let [result              (d/pull db-val [:release/media] dark-side-of-the-moon)
          tracks-partial      (vec (sort-by :track/position
                                (for [track-map (get-in result [:release/media 0 :medium/tracks] ) ]
                                  (select-keys track-map [:track/duration :track/name :track/position]))))
    ]
      (s/validate {:release/media [s/Any]} result)
      (s/validate {:release/media [ { :db/id t/Eid
                                      :medium/format {:db/id t/Eid}
                                      :medium/position  s/Any   ; #todo 1
                                      :medium/trackCount s/Any  ; #todo 10
                                      :medium/tracks [s/Any] } ] }
                  result )
      (is (= tracks-partial
              [ {:track/duration 68346  :track/name "Speak to Me"                   :track/position 1}
                {:track/duration 168720 :track/name "Breathe"                       :track/position 2}
                {:track/duration 230600 :track/name "On the Run"                    :track/position 3}
                {:track/duration 409600 :track/name "Time"                          :track/position 4}
                {:track/duration 284133 :track/name "The Great Gig in the Sky"      :track/position 5}
                {:track/duration 382746 :track/name "Money"                         :track/position 6}
                {:track/duration 469853 :track/name "Us and Them"                   :track/position 7}
                {:track/duration 206213 :track/name "Any Colour You Like"           :track/position 8}
                {:track/duration 226933 :track/name "Brain Damage"                  :track/position 9}
                {:track/duration 131546 :track/name "Eclipse"                       :track/position 10} ] ))))
  (testing "reverse component lookup"
    (let [result        (d/pull db-val [:release/_media] dylan-harrison-cd)
          _             (s/validate {:release/_media {:db/id t/Eid}} result)
          res-entity    (t/entity-map      db-val (safe-> result :release/_media :db/id))
          ;  fails -->  (t/entity-map-sort db-val (safe-> result :release/_media :db/id))  #todo Schema

          _             (s/validate   {:release/artistCredit s/Str
                                       :release/artists #{ s/Any }
                                       :release/country s/Keyword     ; #todo :country/US
                                       :release/gid s/Uuid
                                       :release/media #{ s/Any }  ; #todo {:db/id t/Eid} does not nest! ???
                                       :release/name s/Str
                                       :release/status s/Str
                                       :release/year Long}
                                    res-entity )
    ]
      (is (submap?
              {:release/artistCredit "Bob Dylan & George Harrison"
               :release/country :country/US
               :release/gid #uuid "67bbc160-ac45-4caf-baae-a7e9f5180429"
               :release/name "Dylan–Harrison Sessions"
               :release/status "Bootleg"
               :release/year 1970}
              res-entity ))
    ))
)


(deftest t-map-spec
  (testing "map specifications"
    (let [res-1       (vec (sort (d/pull db-val [:track/name :track/artists] ghost-riders)))
          res-2       (vec (sort (d/pull db-val [:track/name {:track/artists [:db/id :artist/name] } ] ghost-riders)))
    ]
      ; res-1 = [ [ :track/artists 
      ;             [ {:db/id 17592186048186} 
      ;               {:db/id 17592186049854} ] ]
      ;           [ :track/name "Ghost Riders in the Sky"] ]
      ; res-2 = [ [ :track/artists
      ;             [ {:db/id 17592186048186, :artist/name "Bob Dylan"}
      ;               {:db/id 17592186049854, :artist/name "George Harrison"} ] ]
      ;             [:track/name "Ghost Riders in the Sky"]]
      ;
      (is (match    res-1   [ [ :track/artists 
                                [ {:db/id _ } 
                                  {:db/id _ } ] ]
                              [ :track/name "Ghost Riders in the Sky"] ] true
                            :else false))
      (is (match  res-2     [ [ :track/artists
                                [ {:db/id _  :artist/name "Bob Dylan"}
                                  {:db/id _  :artist/name "George Harrison"} ] ]
                              [:track/name "Ghost Riders in the Sky"] ]   true
                            :else false))
    )))


#_(deftest t-00
  (testing "xxx"
  (let [
  ]
)))

#_(deftest t-00
  (testing "xxx"
  (let [
  ]
)))

#_(deftest t-00
  (testing "xxx"
  (let [
  ]
)))

#_(deftest t-00
  (testing "xxx"
  (let [
  ]
)))

#_(deftest t-00
  (testing "xxx"
  (let [db-val (d/db conn)
  ]
)))

