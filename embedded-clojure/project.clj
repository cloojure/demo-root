(defproject embedded-clojure "0.1.0-SNAPSHOT"
  :dependencies [
    [org.clojure/clojure "1.8.0"]
  ]
  :java-source-paths ["src-java"]
  :main embedded-clojure.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
