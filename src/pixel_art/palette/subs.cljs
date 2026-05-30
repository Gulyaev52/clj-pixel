(ns pixel-art.palette.subs 
  (:require
    [pixel-art.utils.coll :as coll]
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::current-palette
 (fn [db]
   (coll/find-first :current (:palettes db))))

(re-frame/reg-sub
 ::palettes
 (fn [db]
   (:palettes db)))

