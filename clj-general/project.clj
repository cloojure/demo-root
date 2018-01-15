(defproject demo  "0.1.0-SNAPSHOT"
  :description    "FIXME: write description"
  :url            "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.9.0"]
    [org.clojure/test.check "0.9.0"]
    [org.clojure/data.avl "0.0.17"]
    [prismatic/schema "1.1.7"]
    [tupelo "0.9.71"]
  ]
  :profiles {:dev {:dependencies []
                   :plugins [
                     [com.jakemccrary/lein-test-refresh "0.22.0"] ] }
             :uberjar {:aot :all}}
  :global-vars {*warn-on-reflection* false}
  :main ^:skip-aot demo.core

  :source-paths       ["src"]
  :test-paths         ["src"]
  :java-source-paths  ["src-java"]
  :target-path        "target/%s"
  :jvm-opts           ["-Xms500m" "-Xmx2g"]
)
