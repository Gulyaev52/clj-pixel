(ns pixel-art.sprite-resizer.events
  (:require
   [pixel-art.db :as db]
   [pixel-art.history :as history]
   [pixel-art.sprite-resizer.utils :refer [resize-sprite]]
   [re-frame.core :as re-frame]))

(defn init []
  {:opened false
   :settings {:target-size nil
              :resize-content false
              :anchor {:x :center :y :center}}})

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (-> db
            (assoc-in [:sprite-resizer :opened] opened)
            (assoc-in [:sprite-resizer :settings :target-size] (-> db :sprite :size)))}))

(re-frame/reg-event-fx
 ::set-settings-option
 (fn [{:keys [db]} [_ option value]]
   {:db (assoc-in db [:sprite-resizer :settings option] value)}))

(re-frame/reg-event-fx
 ::resize
 [(re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [db viewport-size]}]
   (let [{:keys [sprite] {:keys [settings]} :sprite-resizer} db
         resized-sprite (resize-sprite sprite settings)]
     {:db (-> db
              (db/set-sprite resized-sprite {:prev-sprite (:sprite db)
                                             :viewport-size viewport-size})
              (assoc-in [:sprite-resizer :opened] false)
              history/save-sprite)})))
