(ns pixel-art.subs
  (:require
   [pixel-art.db.utils :refer [check-unsaved-changes-exist]]
   [pixel-art.tool.utils :refer [get-tool-options]]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::initial-loading
 (fn [db] (-> db :initial-loading)))

(re-frame/reg-sub
 ::layers
 (fn [db]
   (-> db :sprite :layers)))

(re-frame/reg-sub
 ::sprite-size
 (fn [db]
   (-> db :sprite :size)))

(re-frame/reg-sub
 ::sprite
 (fn [db]
   (:sprite db)))

(re-frame/reg-sub
 ::sprite-title
 (fn [db] (or (-> db :sprite :title) "")))

(re-frame/reg-sub
 ::pixels-grid-enabled
 (fn [db]
   (:pixels-grid-enabled db)))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::tool-options
 (fn [db]
   (get-tool-options db)))

(re-frame/reg-sub
 ::primary-color
 (fn [db]
   (:primary-color db)))

(re-frame/reg-sub
 ::secondary-color
 (fn [db]
   (:secondary-color db)))

(re-frame/reg-sub
 ::unsaved-changes-exist
 check-unsaved-changes-exist)