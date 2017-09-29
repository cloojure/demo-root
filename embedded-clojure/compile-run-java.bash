#!/bin/bash  -v

lein clean
lein uberjar
# Use Java main()
java -cp /home/alan/demo/embedded-clojure/target/uberjar/embedded-clojure-0.1.0-SNAPSHOT-standalone.jar \
  mypkg.Main

# Use Clojure -main
java -cp /home/alan/demo/embedded-clojure/target/uberjar/embedded-clojure-0.1.0-SNAPSHOT-standalone.jar \
  embedded_clojure/core
