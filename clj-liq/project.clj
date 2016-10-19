(defproject clj-liq "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [ [org.clojure/clojure "1.7.0"]
                  [clj-dbcp      "0.8.1"]  ; to create connection-pooling DataSource
                  [clj-liquibase "0.6.0"]  ; for this library
                  [oss-jdbc      "0.8.0"]  ; for Open Source JDBC drivers
                ]

  :main ^:skip-aot clj-liq.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
