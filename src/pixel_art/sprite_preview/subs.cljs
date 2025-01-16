(ns pixel-art.sprite-preview.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::sprite-preview
 (fn [db]
   (:sprite-preview db)))
