(ns pixel-art.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::last-mouse-pos
 (fn [db]
   (:last-mouse-pos db)))

(re-frame/reg-sub
 ::scale
 (fn [db]
   (:scale db)))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::selection-manager
 (fn [db]
   (:tool db)))
