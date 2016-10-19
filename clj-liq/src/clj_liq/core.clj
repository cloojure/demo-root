(ns clj-liq.core
  (:require
    [clj-dbcp.core        :as cp]
    [clj-liquibase.cli    :as cli] )
  (:use
    [clj-liquibase.core :refer [defparser]] ))

(defparser app-changelog "changelog.edn")
(def ds (cp/make-datasource :postgresql   { :host "localhost" :database "alan" } )) ; :user "" :password ""

(defn -main
  [& [cmd & args]]
  (apply cli/entry cmd {:datasource ds :changelog  app-changelog}
         args))

