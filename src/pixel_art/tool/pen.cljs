(ns pixel-art.tool.pen
  (:require [pixel-art.tool.utils :refer [commit-preview-changes
                                          get-tool-options resize-pixel
                                          update-preview-and-draw]]))

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

(defn handle-mouse-event [event db]
  (let [{:keys [preview color user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            new-preview (->> (resize-pixel (:pos event) pixel-size)
                             (map (fn [p] [p color]))
                             (into preview))]
        (update-preview-and-draw db new-preview {:clear false}))

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :highlight-pixels points})

      (= :mouse-up (:type event))
      (commit-preview-changes db))))
