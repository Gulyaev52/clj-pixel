(ns pixel-art.utils-test
  (:require [cljs.test :as t]))

(defn run-test-cases-without [behaviour initial-data test-cases]
  (->> test-cases
       (reduce (fn [[res data] [event expected]]
                 (let [result (behaviour (assoc data :event event))
                       new-data (merge-with (fn [a b] (and a b))
                                            data
                                            (select-keys result [:overlay-frame :tool]))]
                   [(conj res {:event event
                               :result result
                               :expected (expected (assoc data :event event))})
                    new-data]))
               [[]
                initial-data])
       first))

(defn run-test-cases [behaviour initial-data test-cases]
  (let [res (->> test-cases
                 (reduce (fn [[res data] [event expected]]
                           (let [result (behaviour (assoc data :event event))
                                 new-data (merge-with (fn [a b] (and a b))
                                                      data
                                                      (select-keys result [:overlay-frame :tool]))]
                             [(conj res {:event event
                                         :result result
                                         :expected (expected (assoc data :event event))})
                              new-data]))
                         [[]
                          initial-data])
                 first)]
    (doseq [{:keys [event result expected]} res]
      (t/is (= expected result) event))))

