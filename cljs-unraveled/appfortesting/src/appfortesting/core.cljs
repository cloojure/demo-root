(ns appfortesting.core
  (:require [clojure.browser.repl :as repl]))


(enable-console-print!)

(defn leap?
  [year]
  (and (zero? (js-mod year 4))
       (pos? (js-mod year 100))
       (pos? (js-mod year 400))))

