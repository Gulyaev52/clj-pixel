(ns pixel-art.tool.utils
  (:require [pixel-art.model.frame :as frame]))

(defn commit-changes [db pixels-m]
  (let [source-frame (frame/set-pixels-map pixels-m (:source-frame db))]
    {:db (assoc db :source-frame source-frame)
     :fx [[:clear-preview]
          [:draw-frame source-frame]]}))

(defn get-tool-options [db]
  (get (db :tools-options) (-> db :tool :type)))

;; Resize the pixel at {col, row} for the provided size. Will return the array of pixels centered
;; * around the original pixel, forming a pixel square of side=size
(defn resize-pixel [point size]
  (for [j (range 0 size)
        i (range 0 size)]
    {:x (+ (- (:x point) (. js/Math (floor (/ size 2)))) i)
     :y (+ (- (:y point) (. js/Math (floor (/ size 2)))) j)}))
(comment
  (resize-pixel {:x 3 :y 3} 1)
  (resize-pixel {:x 3 :y 3} 2)
  (resize-pixel {:x 3 :y 3} 3))
