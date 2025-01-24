(ns pixel-art.drawing.fx
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.tool.utils :refer [get-current-cel]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]))

(re-frame/reg-fx
 :draw-preview ;; todo: вынести в fx
 (fn [changes]
   (let [size (-> @db/app-db :sprite :size)
         {transparent-changes true rest-changes false}
         (->> changes
              (filter (fn [[pos]] (geometry/valid-point? pos size)))
              (group-by (fn [[_ color]] (= color color/transparent-color-int))))]
     (canvas/update-image-data (. js/document (getElementById "preview")) rest-changes)
     (when (seq transparent-changes)
       (canvas/update-image-data (. js/document (getElementById "current-layer")) transparent-changes)))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [current-cel (get-current-cel @re-frame.db/app-db)
         size (-> @re-frame.db/app-db :sprite :size)
         changes (->> poses
                      (filter (fn [pos] (geometry/valid-point? pos size)))
                      (map (fn [pos] [pos (color/get-highlight-color (cel/get-pixel pos current-cel))])))]
     (canvas/update-image-data (. js/document (getElementById "visual-effects"))
                               changes))))

(re-frame/reg-fx
 :clear-visual-effects
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "visual-effects")))))
