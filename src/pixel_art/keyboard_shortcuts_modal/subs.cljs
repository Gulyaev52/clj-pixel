(ns pixel-art.keyboard-shortcuts-modal.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::opened
 (fn [db]
   (-> db :keyboard-shortcuts-modal :opened)))
