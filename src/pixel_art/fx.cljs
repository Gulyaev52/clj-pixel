(ns pixel-art.fx
  (:require [re-frame.core :as re-frame]))

(defn now [] (.getTime (js/Date.)))

(def registered-keys (atom nil))

(defn dispatch-if-not-superceded [{:keys [key delay event time-received]}]
  (when (= time-received (get @registered-keys key))
    ;; no new events on this key!
    (re-frame/dispatch event)))

(defn dispatch-later [{:keys [delay] :as debounce}]
  (js/setTimeout
   (fn [] (dispatch-if-not-superceded debounce))
   delay))

;; https://github.com/johnswanson/re-frame-debounce-fx/tree/develop

(re-frame/reg-fx
 :dispatch-debounce
 (fn dispatch-debounce [debounce]
   (let [ts (now)]
     (swap! registered-keys assoc (:key debounce) ts)
     (dispatch-later (assoc debounce :time-received ts)))))