(ns pixel-art.onion-skin.subs
  (:require
   [pixel-art.onion-skin.utils :refer [get-onion-skin-frames-idx]]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::onion-skin
 (fn [db]
   (:onion-skin db)))

(re-frame/reg-sub
 ::enabled
 (fn [db]
   (:enabled (:onion-skin db))))

(re-frame/reg-sub
 ::frames-idx
 (fn [db]
   (let [onion-skin (:onion-skin db)]
     (when (:enabled onion-skin)
       (get-onion-skin-frames-idx (:sprite db) (:frames-count onion-skin))))))
