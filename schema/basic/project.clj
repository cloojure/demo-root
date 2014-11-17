(defproject basic "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [ [org.clojure/clojure      "1.6.0"]
                  [cooljure                 "0.1.16"]
                  [prismatic/schema         "0.3.3"] ]
  :main ^:skip-aot basic.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
