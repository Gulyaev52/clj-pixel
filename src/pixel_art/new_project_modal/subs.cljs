(ns pixel-art.new-project-modal.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::opened
 (fn [db] (-> db :new-project-modal :opened)))

(re-frame/reg-sub
 ::size
 (fn [db] (-> db :new-project-modal :size)))
