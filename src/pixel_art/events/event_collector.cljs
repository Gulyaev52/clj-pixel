(ns pixel-art.events.event-collector
  (:require [re-frame.core :as re-frame]))

(def event-store (atom (list)))

(defn keep-last-20
  [existing new-one]
  (take 20 (conj existing new-one)))

(defn repeat-last-event
  ([]
   (re-frame/dispatch (first @event-store)))
  ([n]
   (re-frame/dispatch (filter (fn [[event-name]] (= (str n) (name event-name)))
                              @event-store))))

;; this interceptor will collect events and add them to the atom above
(def event-collector
  (re-frame/->interceptor
   :id      :event-collector
   :before  (fn [context]
              (swap! event-store keep-last-20 (re-frame/get-coeffect context :event))
              context)))

;; register this global interceptor early in program's boot process,
;; using re-frame's API
(re-frame/reg-global-interceptor event-collector)