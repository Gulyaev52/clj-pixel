(ns pixel-art.export-modal.subs
  (:require
   [pixel-art.export-modal.events :as events]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::spritesheet-settings
 events/get-spritesheet-settings)

(re-frame/reg-sub
 ::image-settings
 events/get-image-settings)

(re-frame/reg-sub
 ::settings-valid?
 (fn [db]
   (not (empty? (-> db :export-modal :common-settings :file-name)))))

(re-frame/reg-sub
 ::preview
 (fn [db]
   (-> db :export-modal :preview)))

(re-frame/reg-sub
 ::exporting
 (fn [db]
   (-> db :export-modal :exporting)))

(re-frame/reg-sub
 ::current-tab
 (fn [db]
   (-> db :export-modal :current-tab)))

(re-frame/reg-sub
 ::modal-opened
 (fn [db]
   (-> db :export-modal :opened)))
