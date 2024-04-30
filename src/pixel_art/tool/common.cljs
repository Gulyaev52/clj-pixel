(ns pixel-art.tool.common
  (:require [pixel-art.model.frame :as frame]
            [re-frame.db :as db]))

(defn update-preview-and-draw [db preview {:keys [clear]}]
  {:db (assoc db :preview preview)
   :draw-preview [preview {:clear clear}]})

(defn commit-preview-changes [db]
  (let [source-frame (frame/set-pixels-map (:preview db) (:source-frame db))]
    {:db (assoc db
                :preview nil
                :source-frame source-frame)
     :draw-preview [nil {:clear true}]
     :draw-frame source-frame}))

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
