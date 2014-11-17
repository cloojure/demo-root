(ns basic.core-test
  (:require [clojure.string                         :as str]
            [clojure.test.check                     :as tc]
            [clojure.test.check.generators          :as gen]
            [clojure.test.check.properties          :as prop]
            [clojure.test.check.clojure-test        :as tst]
            [schema.core                            :as s]
            [schema.coerce                          :as coerce]
            [schema.test                            :as s-tst]
  )
  (:use cooljure.core
        clojure.test)
  (:gen-class))

(use-fixtures :once schema.test/validate-schemas)

;--------------------------------------------------------------------------------
; from README
;
(def Data
  "A schema for a nested data type"
  {:a {:b s/Str
       :c s/Int}
   :d [ { :e s/Keyword
          :f [s/Num] } ] } )

(deftest readme-1-t
  ; s/Any, s/Bool, s/Num, s/Keyword, s/Symbol, s/Int, and s/Str are cross-platform schemas.  

  ; When validation succeeds, the value itself is returned
  (is (= 42     (s/validate s/Num       42)))
  (is (= true   (s/validate s/Bool      true)))
  (is (= :hi    (s/validate s/Keyword   :hi)))
  (is (= 42     (s/validate s/Int       42)))
  (is (= "yo!"  (s/validate s/Str       "yo!")))
  (is (= 'map   (s/validate s/Symbol    'map)))

  ; Can use s/Any to match any value.  Note that for truthy values, can simplify to
  ; (is (s/validate...))
  (is (s/validate s/Any  42))
  (is (s/validate s/Any  true))
  (is (s/validate s/Any  :hi))
  (is (s/validate s/Any  "yo!"))
  (is (s/validate s/Any  'map))

  (is (thrown? Exception
    (s/validate s/Num "42"))))
    ; RuntimeException: Value does not match schema: (not (instance java.lang.Number "42"))

(deftest readme-2-t
  (is (map?   
    (s/validate Data    ; on success, s/validate returns its argument
      { :a {:b "abc"
            :c 123}
        :d [ {:e :bc
              :f [12.2 13 100]}
             {:e :bc
              :f [-1] } 
           ] } )))

  (is (thrown? Exception
    (s/validate Data    ; on failure, s/validate throws an Exception
      {:a {:b 123
           :c "ABC"}} )))

  (is (= :whoa (s/validate s/Keyword :whoa)))

  (is (thrown? Exception
    (s/validate s/Keyword 123)))
    ; RuntimeException: Value does not match schema: (not (keyword? 123))

  ; On the JVM, you can use classes for instance? checks
  (is (s/validate java.lang.String "schema"))

  ; On JS, you can use prototype functions
  ; (s/validate Element (js/document.getElementById "some-div-id"))

  ; list of strings
  (is (s/validate [s/Str] ["a" "b" "c"]))

  ; nested map from long to String to double
  (is (s/validate {long {String double}}    {1 {"2" 3.0 "4" 5.0}} ))
)

(def StringList     [s/Str] )               ; any sequence of Strings
(def StringScores   {String double})        ; a map of String keys and double values
(def StringScoreMap {long StringScores})    ; a map from long keys to StringScores values

(deftest readme-3-t
  (try
    (s/validate StringList ["a" :b "c"])
    (catch Exception ex
      (newline)
      (println "--------------------------------------------------------------------------------")
      (println "Exception #1:  " ex)))
      ; RuntimeException: Value does not match schema:
      ;  [nil (not (instance? java.lang.String :b)) nil]
    

  (try
    (spyx (s/validate StringScoreMap {1 {"2" 3.0 "3" [5.0]} 4.0 {}}))
    (catch Exception ex
      (newline)
      (println "--------------------------------------------------------------------------------")
      (println "Exception #2:  " ex)))
      ; RuntimeException: Value does not match schema:
      ;  {1 {"3" (not (instance? java.lang.Double [5.0]))},
      ;   (not (instance? java.lang.Long 4.0)) invalid-key}
)


(s/defrecord StampedNames
  [date     :- Long
   names    :- [s/Str]] )

; Error case
(s/defn stamped-names-bad     :- StampedNames
  [names :- [s/Str]]
  (StampedNames. (str (System/currentTimeMillis)) names))

; Correct case
(s/defn stamped-names-good    :- StampedNames
  [names :- [s/Str]]
  (StampedNames.      (System/currentTimeMillis)  names))

; You can inspect the schemas of the record and function
(deftest readme-4-t
  (newline)
  (is (spyx (s/explain StampedNames)))
  ; ==> (record user.StampedNames {:date java.lang.Long, :names [java.lang.String]})

  (newline)
  (is (spyx (s/explain (s/fn-schema stamped-names-good))))
  ; ==> (=> (record user.StampedNames {:date java.lang.Long, :names [java.lang.String]}) [java.lang.String])

  ; And you can turn on validation to catch bugs in your functions and schemas
  (is (thrown? Exception
    (s/with-fn-validation (stamped-names-bad ["bob"]))))
    ; ==> RuntimeException: Output of stamped-names-bad does not match schema:
    ;      {:date (not (instance? java.lang.Long "1378267311501"))}
    ;
    ; Oops, I guess we should remove that `str` from `stamped-names`.

  (newline)
  (spyx (s/with-fn-validation (stamped-names-good ["bob"])))
)


(def FooBar {(s/required-key :foo) s/Str (s/required-key :bar) s/Keyword})

(deftest readme-5-t
  (is (s/validate FooBar {:foo "f" :bar :b}))
  ; {:foo "f" :bar :b}

  (is (thrown? Exception
    (s/validate FooBar {:foo :f})))
    ; RuntimeException: Value does not match schema:
    ;  {:foo (not (instance? java.lang.String :f)),
    ;   :bar missing-required-key}
)


(def FancyMap
  "If foo is present, it must map to a Keyword.  Any number of additional
   String-String mappings are allowed as well."
  { (s/optional-key :foo)   s/Keyword
     s/Str                  s/Str } )

(deftest readme-6-t
  (is (s/validate FancyMap {"a" "b"} ))
  (is (s/validate FancyMap {:foo :f "c" "d" "e" "f"} )))


(def FancySeq
  "A sequence that starts with a String, followed by an optional Keyword,
   followed by any number of Numbers."
  [ (s/one      s/Str       "s")
    (s/optional s/Keyword   "k")
    s/Num ] )

(deftest readme-7-t
  (is (s/validate FancySeq ["test"]))
  (is (s/validate FancySeq ["test" :k]))
  (is (s/validate FancySeq ["test" :k 1 2 3]))

  (is (thrown? Exception
    (s/validate FancySeq [1 :k 2 3 "4"])))
    ; RuntimeException: Value does not match schema:
    ;  [(named (not (instance? java.lang.String 1)) "s")
    ;   nil nil nil
    ;   (not (instance? java.lang.Number "4"))]
)


; both and pred
(def OddLong (s/both  long  (s/pred odd? 'odd?)))

; both & pred can be used for schemas of seqs with at least one element:
(def SetOfAtLeastOneOddLong (s/both #{OddLong} (s/pred seq 'seq)))

(deftest readme-8-t
  ; maybe
  (is (= :a (s/validate (s/maybe s/Keyword) :a)))
    ; remember, successful validation just returns the value

  (is (nil? (s/validate (s/maybe s/Keyword) nil)))
    ; since nil is not a truthy value, we cannot just use (is (s/validate ...)) syntax

  ; enum
  (is (s/validate (s/enum :a :b :c) :a))

  (is (s/validate OddLong 1))
  ; Note that since failed validations throw an Exception, we could just call
  ; (s/validate...) without the (is...) syntax.  However, this will not update the
  ; assertion count printed at the end of "lein test".
  (newline)
  (spyx (s/validate OddLong 1))

  (is (thrown? Exception
    (s/validate OddLong 2)))
    ; RuntimeException: Value does not match schema: (not (odd? 2))
  (is (thrown? Exception
    (s/validate OddLong (int 3))))
    ; RuntimeException: Value does not match schema: (not (instance? java.lang.Long 3))

  (is (= #{3} (s/validate SetOfAtLeastOneOddLong #{3})))
  (is (= #{7 3 5} (s/validate SetOfAtLeastOneOddLong #{3 5 7})))

  (is (thrown? Exception
    (s/validate SetOfAtLeastOneOddLong #{})))
    ; RuntimeException: Value does not match schema: (not (seq #{}))
  (is (thrown? Exception
    (s/validate SetOfAtLeastOneOddLong #{2})))
    ; RuntimeException: Value does not match schema: #{(not (odd? 2))}
)


(def CommentRequest
  { (s/optional-key :parent-comment-id) long
    :text String
    :share-services [(s/enum :twitter :facebook :google)] } )

(def parse-comment-request
  (coerce/coercer CommentRequest coerce/json-coercion-matcher))
 
(deftest blog-020-readme-t
  (let [good-request    { :parent-comment-id  2128123123
                          :text               "This is awesome!"
                          :share-services     [:twitter :facebook]}

        bad-request     { :parent-comment-id  (int 2128123123)
                          :text               "This is awesome!"
                          :share-services     ["twitter" "facebook"]} 
  ]
    (is (s/validate CommentRequest good-request))
        ; passes validation
 
    (is (thrown? Exception
      (s/validate CommentRequest bad-request)))
      ; Exception -- Value does not match schema:
      ;  {:parent-comment-id (not (instance? java.lang.Long 2128123123)),
      ;   :share-services [(not (#{:facebook :google :twitter} "twitter"))
      ;                    (not (#{:facebook :google :twitter} "facebook"))]}

    (is (= good-request (parse-comment-request bad-request)))
        ; ==> true
  ))


(deftest map-demos
  ; This schema defines a map with only one entry, having key :a and a numeric value.  No
  ; other entries are allowed.  So a "superset" map fails. 
  (is (thrown? Exception
    (s/validate {:a s/Num}  {:a 1 :b 2})))

  ; We allow supersets of the minimum using s/Any.  Note that s/Any in the key position is
  ; like a wildcard, interpreted as "zero or more".
  (is (s/validate {:a s/Num,  s/Any s/Any}  {:a 1} ))
  (is (s/validate {:a s/Num,  s/Any s/Any}  {:a 1,  :b 2,  3 "four"} ))
)


;--------------------------------------------------------------------------------
; from Schema for Clojure(Script) blog article
;   http://blog.getprismatic.com/schema-for-clojurescript-data-shape-declaration-and-validation/

(defn with-full-name-plain [m]
  (assoc m :name (str (:first-name m) " " (:last-name m))))

(s/defn with-full-name 
  [m :- { :first-name   s/Str 
          :last-name    s/Str 
          s/Any s/Any } ]
  ; Blows up if not given a map without a string under
  ; :first-name or :last-name keys
  (assoc m :name (str (:first-name m) " " (:last-name m))))

; Leaf schema values that work on JVM and JS
(deftest schema-for-clojure
  (is (s/validate s/Num 42))

  (is (thrown? Exception    (s/validate s/Num "42")))
    ; RuntimeException Value does not match schema: (not (instance java.lang.Number "42")) 

  (is (s/validate s/Keyword     :key) )
  (is (s/validate s/Int         42) )
  (is (s/validate s/Str         "hello"))

  (is (thrown? Exception    
    (s/validate s/Keyword "hello")))
    ; RuntimeException: Value does not match schema: (not (keyword? "hello"))
   
  ; On the JVM, you can use classes for instance? checks
  (is (s/validate java.lang.String "schema"))
  ; On JS, you can use prototype functions 
  ; (s/validate Element document.getElementById("some-div-id"))
   
  ; Schemas on Sequences
  ; [elem-schema] encodes a sequence where each elem matches the elem-schema
  (is (s/validate [s/Num] [1 2 3.0] ))

  (is (thrown? Exception
    (s/validate [s/Int] [1 2 3.0])))
    ; RuntimeException Value does not match schema: [nil nil (not (integer? 3.0))]
   
  ; Enum Schemas 
  (is (s/validate (s/enum :a :b :c) :a))
  (is (thrown? Exception
    (s/validate (s/enum :a :b :c) :d)))
    ; throws, ":d not in enum (:a :b :C)"
   
  ; Schemas on Maps
  ; {:key1 val1-schema, :key2 val2-schema}
  ; encodes map must have :key1 and :key2 (and no other keys)
  ; and the respective values must match val1-schema & val2-schema
  (is (s/validate {:name s/Str :id s/Int} {:name "Bob" :id 42}))
  (is (thrown? Exception
    (s/validate {:type s/Keyword :id s/Int} {:type :rss :id "42"})))
    ; RuntimeException Value does not match schema: {:id (not (integer? "42"))} 
   
  ; You can also encode generic requirements on maps
  ; For instance, the schema below encodes a map with
  ; keys in an enum mapped to Num
  (is (s/validate {(s/enum :a :b :c) s/Num} {:a 1 :b 2 :c 3}))
  (is (thrown? Exception
    (s/validate {(s/enum :a :b :c) s/Num} {:x 1 :b 2 :c 3})))
   
  ; General Schemas on Functions
  ; (s/pred fn?) is a schema that is valid when data passes fn?
  ; (s/both a b) is valid when data passes the a and b schemas
  (is (s/validate [ (s/both s/Str 
                            (s/pred (comp odd? count))) ]
    ; A schema for sequences of strings, each string of odd length
    ["a" "aaa" "aaaaa"] ))
)


;--------------------------------------------------------------------------------
; from Schema 0.2.0 blog article (already incorporated into README section)
;   http://blog.getprismatic.com/schema-0-2-0-back-with-clojurescript-data-coercion/


;--------------------------------------------------------------------------------
; Misc

(def SetOfStr
  #{ s/Str } )

(deftest t1 
  (is (= (s/validate SetOfStr   #{ "a" "b" "c"} )
                                #{ "a" "b" "c"} ))
  (is (thrown? Exception 
        (s/validate SetOfStr  #{ 1 "a" "b" "c"} ))))

