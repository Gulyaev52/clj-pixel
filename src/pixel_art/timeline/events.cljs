(ns pixel-art.timeline.events
  (:require
   [pixel-art.drawing.events]
   [pixel-art.history :as history]
   [pixel-art.model.frame :as frame]
   [pixel-art.model.layer :as layer]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.project-config :as project-config]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.utils :refer [commit-preview-and-init-tool]]
   [re-frame.core :as re-frame]))

(defn commit-preview-from-db-and-init-tool [db]
  (let [tool (tool/init (-> db :tool :type))]
    (commit-preview-and-init-tool db (:preview db) tool)))

(re-frame/reg-event-fx
 ::add-frame
 (fn [{:keys [db]}]
   (let [new-frame (-> (sprite/get-current-frame (:sprite db))
                       :duration
                       frame/create)]
     (-> db
         commit-preview-from-db-and-init-tool
         (update-in [:db :sprite] #(sprite/add-frame new-frame %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::remove-frame
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] sprite/remove-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::duplicate-frame
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] sprite/duplicate-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-frame
 (fn [{:keys [db]} [_ idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/select-frame idx %)))))

(re-frame/reg-event-fx
 ::move-frame
 (fn [{:keys [db]} [_ from-idx to-idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-frame from-idx to-idx %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-frame-left
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-frame-left (sprite/get-current-frame-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-frame-right
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-frame-right (sprite/get-current-frame-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::add-layer
 (fn [{:keys [db]}]
   (let [layer-name (project-config/get-layer-name :single (-> db :sprite :layers count))
         layer (layer/create layer-name)]
     (-> db
         commit-preview-from-db-and-init-tool
         (update-in [:db :sprite] #(sprite/add-layer layer %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::duplicate-layer
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] sprite/duplicate-layer)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::remove-layer
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/remove-layer (-> db :sprite sprite/get-current-layer-idx)
                                                      %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::merge-layer-with-below
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] sprite/merge-layer-with-below)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-up
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-layer-up (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-down
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-layer-down (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer
 (fn [{:keys [db]} [_ from-idx to-idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-layer from-idx to-idx %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::rename-layer
 (fn [{:keys [db]} [_ new-name]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer (sprite/get-current-layer-idx sprite) #(assoc % :name new-name) sprite))))))

(re-frame/reg-event-fx
 ::toggle-all-layers-visibility
 (fn [{:keys [db]} [_]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] (fn [sprite]
                                  (let [layers (:layers sprite)
                                        some-visible? (some :visible? layers)
                                        layers (mapv #(assoc % :visible? (not some-visible?)) (:layers sprite))]
                                    (assoc sprite :layers layers)))))))

(re-frame/reg-event-fx
 ::toggle-layer-visibility
 (fn [{:keys [db]} [_ idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :visible? not) sprite))))))

(re-frame/reg-event-fx
 ::toggle-all-layers-automatic-linking
 (fn [{:keys [db]} [_]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] (fn [sprite]
                                  (let [layers (:layers sprite)
                                        some-automatic-linking? (some :automatic-linking? layers)
                                        layers (mapv #(assoc % :automatic-linking? (not some-automatic-linking?)) (:layers sprite))]
                                    (assoc sprite :layers layers)))))))

(re-frame/reg-event-fx
 ::toggle-layer-automatic-linking
 (fn [{:keys [db]} [_ idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :automatic-linking? not) sprite))))))

(re-frame/reg-event-fx
 ::move-cel
 (fn [{:keys [db]} [_ from-pos to-pos]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/move-cel from-pos to-pos %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-layer
 (fn [{:keys [db]} [_ idx]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/select-layer idx %)))))

(re-frame/reg-event-fx
 ::select-only-1-cel
 (fn [{:keys [db]} [_ pos]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/select-only-1-cel pos %)))))

(re-frame/reg-event-fx
 ::toggle-cel-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/toggle-cel-to-selection pos %)))))

(re-frame/reg-event-fx
 ::add-cels-range-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] #(sprite/add-cels-range-to-selection pos %)))))

(re-frame/reg-event-fx
 ::link-selected-cels
 (fn [{:keys [db]} [_]]
   (let [main-cel-pos (sprite/get-current-cel-pos (:sprite db))]
     (-> db
         commit-preview-from-db-and-init-tool
         (update-in [:db :sprite] #(sprite/link-selected-cels main-cel-pos %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::unlink-selected-cels
 (fn [{:keys [db]}]
   (-> db
       commit-preview-from-db-and-init-tool
       (update-in [:db :sprite] sprite/unlink-selected-cels)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::set-frame-duration
 (fn [{:keys [db]} [_ idx duration]]
   (let [valid-duration (max duration 1)]
     {:db (-> db
              (update :sprite (fn [sprite]
                                (sprite/update-frame idx #(assoc % :duration valid-duration) sprite)))
              history/save-sprite)})))