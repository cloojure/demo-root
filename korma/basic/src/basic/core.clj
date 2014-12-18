(ns basic.core
  (:gen-class)
  (:require [clojure.java.jdbc :as jdbc] )
  (:use korma.db
        korma.core)
)

(def db-ver         "k1")
(def host           "localhost")
(def port           15432)
(def make-pool?     true)

(def fdb-spec 
  { :classname "com.foundationdb.sql.jdbc.Driver"
    :subprotocol "fdbsql"
    :subname (str "//" host ":" port "/" db-ver)
    :make-pool? make-pool? } )

(defdb kdb fdb-spec)

(defentity posts
  (pk :id)
  (table :posts)
  (entity-fields :title :content))

(defn create-table-posts []
  (jdbc/create-table-ddl
    "posts"
    [:id :serial :primary :key]
    [:create_time "TIMESTAMP" "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
    [:title     "varchar(99)"]
    [:content   "text"] ))

(defn add-posts []
  (insert posts
    (values {:title "First Post"  :content "blah blah blah"} )))

(defn reset []
  (jdbc/db-do-commands fdb-spec (str "DROP SCHEMA IF EXISTS " db-ver " CASCADE;" )))


(defn -main []
  (println "main - enter")

  (reset) (reset) (reset)

  (jdbc/db-do-commands fdb-spec (create-table-posts) )

  (add-posts)

  (newline)
  (println "Select:")
  (println (select posts (limit 1)))

  (newline)
  (println "main - exit")
)
