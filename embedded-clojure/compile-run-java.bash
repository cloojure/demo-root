#!/bin/bash  -v

lein clean
lein uberjar

java -cp /home/alan/demo/embedded-clojure/target/uberjar/embedded-clojure-0.1.0-SNAPSHOT-standalone.jar \
  mypkg.Main

