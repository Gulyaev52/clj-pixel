(ns pixel-art.promise2
  (:require
   [day8.re-frame.test :as rf-test]
   [cljs.test :refer [async is]]))

(defmacro promise+
  [& body]
  `(-> (.resolve js/Promise)
       ~@(map (fn [expr]
                `(.then (fn [] ~expr)))
              body)))

;; todo: rename to something like async-test
(defmacro async->
  [& body]
  (let [new-done (gensym)]
    `(cljs.test/async ~new-done
                      (rf-test/run-test-sync
                       (let [res# (promise+ ~@body)]
                         (.then res# ~new-done)
                         (.catch res# (fn [ex#]
                                        (is (not ex#))
                                        (~new-done))))))))

(defmacro wait-is
  ([form]
   `(wait-is ~form nil))
  ([[op expected actual] msg]
   `(.catch (rtl/waitFor (fn []
                           (when-not (~op ~expected (do ~actual))
                             (throw "error wait for2"))
                           js/undefined))
            (fn []
              (is (~op ~expected ~actual)
                  ~msg)))))
