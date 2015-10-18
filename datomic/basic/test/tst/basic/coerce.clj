(ns tst.basic.coerce
  (:require [datomic.api      :as d]
            [schema.core      :as s]
            [schema.coerce    :as coerce]
  )
  (:use   clojure.pprint
          tupelo.core
  )
  (import [java.util Set Map List])
  (:gen-class))

(set! *print-length* 10)

; Prismatic Schema coersion testing
;-----------------------------------------------------------------------------
(def CommentRequest
  { (s/optional-key :parent-comment-id) long
    :text String
    :share-services [ (s/enum :twitter :facebook :google) ]
  } )
(def cr-parser (coerce/coercer CommentRequest coerce/json-coercion-matcher))
(def raw-comment-req
  { :parent-comment-id (int 1234)
    :text "This is awesome!"
    :share-services ["twitter" "facebook"] } )
(def parsed-comment-req
  { :parent-comment-id (long 1234)
    :text "This is awesome!"
    :share-services [:twitter :facebook] } )
(assert (= (cr-parser raw-comment-req) parsed-comment-req))


