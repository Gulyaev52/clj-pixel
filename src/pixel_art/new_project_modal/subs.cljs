(ns pixel-art.new-project-modal.subs
  (:require
   [re-frame.core :as re-frame]
   [clojure.string :as str]))

(re-frame/reg-sub
 ::opened
 (fn [db] (-> db :new-project-modal :opened)))

(re-frame/reg-sub
 ::settings
 (fn [db] (-> db :new-project-modal :settings)))

(re-frame/reg-sub
 ::settings-are-valid
 (fn [db]
   (let [settings (-> db :new-project-modal :settings)]
     (and (not (str/blank? (:title settings)))
          (some? (:width (:size settings)))
          (some? (:height (:size settings)))))))
