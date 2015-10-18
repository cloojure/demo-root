(ns appfortesting.core-tests
  (:require [cljs.test :as t] 
            [appfortesting.core :as core] 
  ))

(enable-console-print!)

(t/deftest my-first-test
  (t/is (not= 1 2)))

(t/deftest my-second-test
  (t/is (core/leap? 1980))
  (t/is (not (core/leap? 1981))))

(t/deftest my-async-test
  (t/async done
           (core/async-leap? 1980 (fn [result] 
                                    (t/is (true? result))
                                    (done)))))
 
(set! *main-cli-fn* #(t/run-tests))

(defmethod t/report [::t/default :end-run-tests]
  [m]
  (if (t/successful? m)
    (set! (.-exitCode js/process) 0)
    (set! (.-exitCode js/process) 1)))
