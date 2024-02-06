(ns pixel-art.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))
