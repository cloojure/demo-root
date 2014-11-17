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

(def SetOfStr
  #{ s/Str } )

(deftest t1 
  (is (= (s/validate SetOfStr   #{ "a" "b" "c"} )
                                #{ "a" "b" "c"} ))
  (is (thrown? Exception 
        (s/validate SetOfStr  #{ 1 "a" "b" "c"} ))))

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
  (is (map?  
    (s/validate Data    ; Success!
      { :a {:b "abc"
            :c 123}
        :d [ {:e :bc
              :f [12.2 13 100]}
             {:e :bc
              :f [-1] } 
           ] } )))

  (is (thrown? Exception
    (s/validate Data    ; Failure!
      {:a {:b 123
           :c "ABC"}} )))

  ; s/Any, s/Bool, s/Num, s/Keyword, s/Symbol, s/Int, and s/Str are cross-platform schemas.  

  ; When validation succeeds, the value itself is returned
  (is (= 42 (s/validate s/Num 42)))

  (is (thrown? Exception
    (s/validate s/Num "42")))
    ; RuntimeException: Value does not match schema: (not (instance java.lang.Number "42"))

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

(deftest readme-1-t
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
(deftest readme-2-t
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


;--------------------------------------------------------------------------------
; from Schema for Clojure(Script) blog article
; http://blog.getprismatic.com/schema-for-clojurescript-data-shape-declaration-and-validation/

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
; from Schema 0.2.0 blog article
; http://blog.getprismatic.com/schema-0-2-0-back-with-clojurescript-data-coercion/

(def CommentRequest
  { (s/optional-key :parent-comment-id) long
    :text String
    :share-services [(s/enum :twitter :facebook :google)] } )

(def parse-comment-request
  (coerce/coercer CommentRequest coerce/json-coercion-matcher))
 
(deftest blog-020
  (let [+good-request+  { :parent-comment-id  2128123123
                          :text               "This is awesome!"
                          :share-services     [:twitter :facebook]}

        +bad-request+   { :parent-comment-id  (int 2128123123)
                          :text               "This is awesome!"
                          :share-services     ["twitter" "facebook"]} 
  ]
    (is (s/validate CommentRequest +good-request+))
        ; passes validation
 
    (is (thrown? Exception
      (s/validate CommentRequest +bad-request+)))
      ; Exception -- Value does not match schema:
      ;  {:parent-comment-id (not (instance? java.lang.Long 2128123123)),
      ;   :share-services [(not (#{:facebook :google :twitter} "twitter"))
      ;                    (not (#{:facebook :google :twitter} "facebook"))]}

    (is (= +good-request+ (parse-comment-request +bad-request+)))
        ; ==> true
  ))

