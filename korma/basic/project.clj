(defproject basic "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [ [com.foundationdb/fdb-java "2.0.4"]
                  [com.foundationdb/fdb-sql-layer-jdbc "1.9-3-jdbc41"]
                  [org.clojure/clojure "1.6.0"]
                  [korma "0.3.0"] ]

  :offline? false
  :main ^:skip-aot basic.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
