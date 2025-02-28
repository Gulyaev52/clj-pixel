(ns pixel-art.drawing.fx
  (:require
   [pixel-art.canvas :as canvas]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]))

(re-frame/reg-fx
 :draw-preview
 (fn [preview]
   (let [size (-> @db/app-db :sprite :size)
         current-layer (. js/document (getElementById "current-layer"))]
     (if preview
       (canvas/draw-cel {:size size :pixels preview} current-layer)
       (canvas/clear-canvas current-layer)))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   #_(let [current-cel (get-current-cel @re-frame.db/app-db)
           size (-> @re-frame.db/app-db :sprite :size)
           changes (->> poses
                        (filter (fn [pos] (geometry/valid-point? pos size)))
                        (map (fn [pos] [pos (color/get-highlight-color (cel/get-pixel pos current-cel))])))]
       (canvas/update-image-data (. js/document (getElementById "visual-effects"))
                                 changes))))

(re-frame/reg-fx
 :clear-visual-effects
 (fn []
   #_(canvas/clear-canvas (. js/document (getElementById "visual-effects")))))
