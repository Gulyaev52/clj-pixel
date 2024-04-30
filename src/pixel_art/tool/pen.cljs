(ns pixel-art.tool.pen
  (:require [pixel-art.tool.common :refer [commit-preview-changes
                                           get-tool-options
                                           update-preview-and-draw]]
            [sc.api :as api]))

;; todo: использовать полиморфизм?
(defn init [] {:type :pen})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}
   {:type :checkbox
    :field :pixel-perfect
    :initial-value false
    :label "Pixel perfect"}
   {:type :checkbox
    :field :mirror-x
    :initial-value false
    :label "Mirror-x"}])


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

(defn handle-mouse-event [event db]
  (def event event)
  (def db db)
  (let [{:keys [preview color]} db]
    (cond
      (#{:mouse-down :mouse-move} (:type event))
      (let [{:keys [pixel-size]} (get-tool-options db)
            new-preview (->> (resize-pixel (:pos event) pixel-size)
                             (map (fn [p] [p color]))
                             (into preview))]
        (api/spy)
        (update-preview-and-draw db new-preview {:clear false}))

      (= :mouse-up (:type event))
      (commit-preview-changes db))))
