(defproject helloworld "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [ [org.clojure/clojure "1.7.0"]
                  [com.datomic/datomic-free "0.9.5206" :exclusions [joda-time]]

                  [io.pedestal/pedestal.service      "0.4.0"]
                  [io.pedestal/pedestal.jetty        "0.4.0"]
                  [ch.qos.logback/logback-classic    "1.1.2" :exclusions [org.slf4j/slf4j-api]]
                  [org.slf4j/jul-to-slf4j            "1.7.7"]
                  [org.slf4j/jcl-over-slf4j          "1.7.7"]
                  [org.slf4j/log4j-over-slf4j        "1.7.7"]

                  [com.taoensso/timbre "3.4.0" :exclusions [org.clojure/tools.reader]]
                  [tupelo "0.1.42"]
                ]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "helloworld.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.3.1"]]}}
  :main ^{:skip-aot true} helloworld.server)

