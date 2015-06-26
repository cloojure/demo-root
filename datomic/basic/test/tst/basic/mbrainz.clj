(ns tst.basic.mbrainz
  (:require [datomic.api            :as d]
            [clojure.set            :as c.set]
            [schema.core            :as s]
            [tupelo.core            :refer [spy spyx spyxx it-> safe-> grab submap? matches? wild-match?] ]
            [tupelo.datomic         :as t]
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

; specify lookup-ref values for test entities. These are Lookup Ref's that uniquely identify an
; entity (use instead of EID).
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
    (let [res1          (d/pull db-val [:artist/name :artist/startYear]   ; The pattern is a vector (of keywords) indicating the attr-vals to retrieve
                                        led-zeppelin) ; entity spec
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
    (let [result              (d/pull db-val [:artist/_country]   ; pattern vec
                                              :country/GB)        ; entity spec
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
    (let [result  (d/pull db-val [:release/media]         ; desirec pattern vec
                                 dark-side-of-the-moon)  ; entity spec
    ]
      (s/validate {:release/media [s/Any]} result)
      (s/validate {:release/media [ { :db/id t/Eid
                                      :medium/format {:db/id t/Eid}
                                      :medium/position  s/Any   ; #todo 1
                                      :medium/trackCount s/Any  ; #todo 10
                                      :medium/tracks [s/Any] } ] }
                  result )
      (is (matches? result
            {:release/media
              [ { :db/id _ :medium/format {:db/id _} :medium/position 1 :medium/trackCount 10
                  :medium/tracks
                    [ {:db/id _ :track/duration  68346 :track/name "Speak to Me"                   :track/position  1 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 168720 :track/name "Breathe"                       :track/position  2 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 230600 :track/name "On the Run"                    :track/position  3 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 409600 :track/name "Time"                          :track/position  4 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 284133 :track/name "The Great Gig in the Sky"      :track/position  5 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 382746 :track/name "Money"                         :track/position  6 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 469853 :track/name "Us and Them"                   :track/position  7 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 206213 :track/name "Any Colour You Like"           :track/position  8 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 226933 :track/name "Brain Damage"                  :track/position  9 :track/artists [{:db/id _} ] }
                      {:db/id _ :track/duration 131546 :track/name "Eclipse"                       :track/position 10 :track/artists [{:db/id _} ] } ]}]}
          ))))

  (testing "reverse component lookup"
    (let [result        (d/pull db-val [:release/_media]    ; pattern vec
                                        dylan-harrison-cd)  ; entity spec
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
    (let [res-1       (d/pull db-val [:track/name :track/artists]        ; plain pattern spec
                                                ghost-riders)                     ; source eid
          res-2       (d/pull db-val [:track/name {:track/artists [:db/id :artist/name] } ]  ; nested map pattern spec
                                                ghost-riders)                     ; source eid
    ]
      (is (matches? res-1     { :track/artists    ; we ignore the :db/id EID value (long int)
                                [ {:db/id _ } 
                                  {:db/id _ } ]
                                :track/name "Ghost Riders in the Sky" } ))
      (is (matches? res-2     { :track/artists    ; we ignore the :db/id EID value (long int)
                                [ {:db/id _  :artist/name "Bob Dylan"}
                                  {:db/id _  :artist/name "George Harrison"} ]
                                :track/name "Ghost Riders in the Sky" } ))
    ))
  (testing "nested map specifications"
    (let [res   (d/pull db-val 
                    [ { :release/media        ; for each :release/media entity recurse to
                        [ { :medium/tracks      ; for each medium/tracks entity recurse to
                            [:track/name          ; simple value attr
                             {:track/artists      ; recurse through :track/artists
                              [:artist/name]        ; to :artist/name
                             } ] } ] } ]
                    concert-for-bangla-desh )
    ]
      (is (= res
             { :release/media
               [ { :medium/tracks
                   [ { :track/name "George Harrison / Ravi Shankar Introduction" 
                       :track/artists [ { :artist/name "Ravi Shankar"}
                                        { :artist/name "George Harrison"}]}
                     { :track/name "Bangla Dhun" 
                       :track/artists [ { :artist/name "Ravi Shankar"}]}]}
                 { :medium/tracks
                   [ { :track/name "Wah-Wah" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "My Sweet Lord" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "Awaiting on You All" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "That's the Way God Planned It" 
                       :track/artists [ { :artist/name "Billy Preston"}]}]}
                 { :medium/tracks
                   [ { :track/name "It Don't Come Easy" 
                       :track/artists [ { :artist/name "Ringo Starr"}]}
                     { :track/name "Beware of Darkness" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "Introduction of the Band" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "While My Guitar Gently Weeps" 
                       :track/artists [ { :artist/name "George Harrison"}]}]}
                 { :medium/tracks
                   [ { :track/name "Jumpin' Jack Flash / Youngblood" 
                       :track/artists [ { :artist/name "Leon Russell"}]}
                     { :track/name "Here Comes the Sun" 
                       :track/artists [ { :artist/name "George Harrison"}]}]}
                 { :medium/tracks
                   [ { :track/name "A Hard Rain's Gonna Fall" 
                       :track/artists [ { :artist/name "Bob Dylan"}]}
                     { :track/name "It Takes a Lot to Laugh / It Takes a Train to Cry" 
                       :track/artists [ { :artist/name "Bob Dylan"}]}
                     { :track/name "Blowin' in the Wind" 
                       :track/artists [ { :artist/name "Bob Dylan"}]}
                     { :track/name "Mr. Tambourine Man" 
                       :track/artists [ { :artist/name "Bob Dylan"}]}
                     { :track/name "Just Like a Woman" 
                       :track/artists [ { :artist/name "Bob Dylan"}]}]}
                 { :medium/tracks
                   [ { :track/name "Something" 
                       :track/artists [ { :artist/name "George Harrison"}]}
                     { :track/name "Bangla Desh" 
                       :track/artists [ { :artist/name "George Harrison"}]}]}]} ))))
)


(deftest t-wildcard-specs
  (testing "basic"
    (let [result          (d/pull db-val '[*] concert-for-bangla-desh)
          release-media   (:release/media result)
    ]
      (is (= 6 (count release-media)))
      (is (matches? result
              {:release/name "The Concert for Bangla Desh",
               :release/artists [{:db/id _}],
               :release/country  {:db/id _},
               :release/gid #uuid "f3bdff34-9a85-4adc-a014-922eef9cdaa5",
               :release/day 20,
               :release/status "Official",
               :release/month 12,
               :release/artistCredit "George Harrison",
               :db/id _,
               :release/year 1971,
               :release/media _ } ))
      (is (matches? (first release-media)
              {:db/id _,
                 :medium/format {:db/id _},
                 :medium/position 1,
                 :medium/trackCount 2,
                 :medium/tracks
                 [{:db/id _,
                   :track/duration 376000,
                   :track/name "George Harrison / Ravi Shankar Introduction",
                   :track/position 1,
                   :track/artists [{:db/id _} {:db/id _}]}
                  {:db/id _,
                   :track/duration 979000,
                   :track/name "Bangla Dhun",
                   :track/position 2,
                   :track/artists [{:db/id _}]}]}  ))
    #_(is (matches? result
              {:release/name "The Concert for Bangla Desh", :release/artists [{:db/id _}], :release/country {:db/id _}, :release/gid #uuid "f3bdff34-9a85-4adc-a014-922eef9cdaa5",
               :release/day 20, :release/status "Official", :release/month 12, :release/artistCredit "George Harrison", :db/id _, :release/year 1971,
               :release/media
               [{:db/id _, :medium/format {:db/id _}, :medium/position 1, :medium/trackCount 2,
                 :medium/tracks
                 [{:db/id _, :track/duration 376000, :track/name "George Harrison / Ravi Shankar Introduction", :track/position 1, :track/artists [{:db/id _} {:db/id _}]}
                  {:db/id _, :track/duration 979000, :track/name "Bangla Dhun", :track/position 2, :track/artists [{:db/id _}]}]}
                {:db/id _, :medium/format {:db/id _}, :medium/position 3, :medium/trackCount 4, :medium/tracks
                 [{:db/id _, :track/duration 195000, :track/name "Wah-Wah", :track/position 1, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 256000, :track/name "My Sweet Lord", :track/position 2, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 157000, :track/name "Awaiting on You All", :track/position 3, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 245000, :track/name "That's the Way God Planned It", :track/position 4, :track/artists [{:db/id _}]}]}
                {:db/id _, :medium/format {:db/id _}, :medium/position 5, :medium/trackCount 4, :medium/tracks
                 [{:db/id _, :track/duration 158000, :track/name "It Don't Come Easy", :track/position 1, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 206000, :track/name "Beware of Darkness", :track/position 2, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 180000, :track/name "Introduction of the Band", :track/position 3, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 279000, :track/name "While My Guitar Gently Weeps", :track/position 4, :track/artists [{:db/id _}]}]}
                {:db/id _, :medium/format {:db/id _}, :medium/position 6, :medium/trackCount 2, :medium/tracks
                 [{:db/id _, :track/duration 551000, :track/name "Jumpin' Jack Flash / Youngblood", :track/position 1, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 171000, :track/name "Here Comes the Sun", :track/position 2, :track/artists [{:db/id _}]}]}
                {:db/id _, :medium/format {:db/id _}, :medium/position 4, :medium/trackCount 5, :medium/tracks
                 [{:db/id _, :track/duration 304000, :track/name "A Hard Rain's Gonna Fall", :track/position 1, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 174000, :track/name "It Takes a Lot to Laugh / It Takes a Train to Cry", :track/position 2, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 214000, :track/name "Blowin' in the Wind", :track/position 3, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 246000, :track/name "Mr. Tambourine Man", :track/position 4, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 254000, :track/name "Just Like a Woman", :track/position 5, :track/artists [{:db/id _}]}]}
                {:db/id _, :medium/format {:db/id _}, :medium/position 2, :medium/trackCount 2, :medium/tracks
                 [{:db/id _, :track/duration 185000, :track/name "Something", :track/position 1, :track/artists [{:db/id _}]}
                  {:db/id _, :track/duration 254000, :track/name "Bangla Desh", :track/position 2, :track/artists [{:db/id _}]}]}]} ))
  )))

(deftest t-wild-map
  (testing "wildcard & map spec"
    (let [res-1         (d/pull db-val '[*] ghost-riders)
          res-2         (d/pull db-val '[* {:track/artists [:artist/name]} ] ghost-riders)
    ]
      (is (matches? res-1
              {:db/id _,
               :track/duration 218506,
               :track/name "Ghost Riders in the Sky",
               :track/position 11,
               :track/artists [{:db/id _} {:db/id _}]} ))
      (is (matches? res-2
              {:db/id _,
               :track/duration 218506,
               :track/name "Ghost Riders in the Sky",
               :track/position 11,
               :track/artists
               [{:artist/name "Bob Dylan"} {:artist/name "George Harrison"}]} ))
)))

(deftest t-defaults
  (testing "default expressions"
    (let [res-1     (d/pull db-val '[:artist/name (default :artist/endYear 0)]       mccartney)
          res-2     (d/pull db-val '[:artist/name (default :artist/endYear "N/A")]   mccartney)
          res-3     (d/pull db-val '[:artist/name (default :died-in-1966?)]          mccartney)
    ]
      (is (= res-1  {:artist/name "Paul McCartney", :artist/endYear 0} ))
      (is (= res-2  {:artist/name "Paul McCartney", :artist/endYear "N/A"} ))
      (is (= res-3  {:artist/name "Paul McCartney"} )))))

(deftest t-limit
  (testing "limit"
    (let [res-1   (d/pull db-val '[:artist/name (limit :track/_artists 10) ]        led-zeppelin)
          res-2   (d/pull db-val '[ { (limit :track/_artists 10) [:track/name] } ]  led-zeppelin)
    ]
      (is (matches?   res-1
                      { :artist/name "Led Zeppelin",
                        :track/_artists
                        [ {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _}
                          {:db/id _} ] } ))
      (is (= res-2  { :track/_artists
                      [ {:track/name "Whole Lotta Love"}
                        {:track/name "What Is and What Should Never Be"}
                        {:track/name "The Lemon Song"}
                        {:track/name "Thank You"}
                        {:track/name "Heartbreaker"}
                        {:track/name "Living Loving Maid (She's Just a Woman)"}
                        {:track/name "Ramble On"}
                        {:track/name "Moby Dick"}
                        {:track/name "Bring It on Home"}
                        {:track/name "Whole Lotta Love"}]} ))))
  (testing "nulllimit"
    (let [res-1   (d/pull db-val '[:artist/name (limit :track/_artists nil) ] led-zeppelin) 
    ]
      (is (matches? res-1 {:artist/name "Led Zeppelin", :track/_artists _ } ))
      (is (= 128 (count (grab :track/_artists res-1))))))
)

(deftest t-empty
  (testing "empty results"
    (let [res   (d/pull db-val '[:penguins] led-zeppelin) ]
      (is (nil? res))
    (let [res-1   (d/pull db-val '[ {:track/artists [:artist/name] } ] ghost-riders)
          res-2   (d/pull db-val '[ {:track/artists [:penguins]    } ] ghost-riders) ]
      (is (= res-1  { :track/artists
                      [ {:artist/name "Bob Dylan"} 
                        {:artist/name "George Harrison"} ] } ))
      (is (nil? res-2)))
)))

