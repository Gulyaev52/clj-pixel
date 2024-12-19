(ns pixel-art.tool.utils
  (:require
   [pixel-art.history :as history]
   [pixel-art.model.sprite :as sprite]
   [sc.api]))

(defn get-tool-options [db]
  (get (db :tools-options) (-> db :tool :type)))

(defn get-current-cel [db]
  (-> db :sprite sprite/get-current-cel))

(defn get-current-color-type [right-button]
  (if right-button :secondary-color :primary-color))

(defn get-current-color [db event]
  ((get-current-color-type (:right-button event)) db))

(defn commit-changes-and-init-tool [db changes tool-init]
  (-> (if (seq changes)
        {:db (-> db
                 (update :sprite #(sprite/set-current-cel-pixels changes %))
                 (history/save-sprite))}
        {:db db})
      (assoc-in [:db :tool] tool-init)
      (assoc :fx [[:clear-preview]
                  [:clear-visual-effects]])))

;; Resize the pixel at {col, row} for the provided size. Will return the array of pixels centered
;; * around the original pixel, forming a pixel square of side=size
(defn resize-pixel [point size]
  (when point
    (for [j (range 0 size)
          i (range 0 size)]
      {:x (+ (- (:x point) (. js/Math (floor (/ size 2)))) i)
       :y (+ (- (:y point) (. js/Math (floor (/ size 2)))) j)})))
(comment
  (resize-pixel {:x 3 :y 3} 1)
  (resize-pixel {:x 3 :y 3} 2)
  (resize-pixel {:x 3 :y 3} 3))
