(ns pixel-art.subs
  (:require [pixel-art.tool.utils :refer [get-tool-options]]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::frames-size
 (fn [db]
   (-> db :sprite :size)))

(re-frame/reg-sub
 ::sprite
 (fn [db]
   (:sprite db)))

(re-frame/reg-sub
 ::pixels-grid-enabled
 (fn [db]
   (:pixels-grid-enabled db)))

(re-frame/reg-sub
 ::scale
 (fn [db]
   (:scale db)))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::tool-options
 (fn [db]
   (get-tool-options db)))

(re-frame/reg-sub
 ::selection-manager
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::frame-imgs
 (fn [db]
   (:frame-imgs db)))

(re-frame/reg-sub
 ::sprite-preview
 (fn [db]
   (:sprite-preview db)))
