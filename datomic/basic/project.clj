(defproject basic "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies   [ [org.clojure/clojure      "1.7.0-RC1"]
                    [cooljure                 "0.1.26"]
                    [com.datomic/datomic-pro  "0.9.5173" :exclusions [joda-time]]
                  ]
; :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
;                                  :creds :gpg}}

  :main ^:skip-aot basic.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
)
