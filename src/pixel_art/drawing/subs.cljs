(ns pixel-art.drawing.subs
  (:require
   [pixel-art.project-settings :as project-settings]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::drawing-container-size
 (fn [db] (:drawing-container-size db)))

(re-frame/reg-sub
 ::viewport-scroll
 (fn [db] (:viewport-scroll db)))

(re-frame/reg-sub
 ::preview
 (fn [db] (:preview db)))

(re-frame/reg-sub
 ::visual-effects
 (fn [db] (:visual-effects db)))

(re-frame/reg-sub
 ::panning
 (fn [db]
   (some? (:start-viewport-scroll db))))

(re-frame/reg-sub
 ::mouse-was-down
 (fn [db]
   (some? (:initial-mouse-down-pos db))))

(re-frame/reg-sub
 ::mouse-pos
 (fn [db] (:mouse-pos db)))

(re-frame/reg-sub
 ::scale
 (fn [db]
   (:scale db)))

(re-frame/reg-sub
 ::pixels-grid-cel-img
 (fn [db]
   (let [scale (:scale db)]
     (when (:pixels-grid-enabled db)
       (str "data:image/svg+xml,%3Csvg width='" scale "' height='" scale "' opacity='" (min (/ scale project-settings/max-scale) 1) "' xmlns='http://www.w3.org/2000/svg'%3E%3Crect width='" scale "' height='" scale "' vector-effect='non-scaling-stroke' fill='transparent' stroke='blue' /%3E%3C/svg%3E")))))
