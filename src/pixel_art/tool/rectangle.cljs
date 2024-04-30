(ns pixel-art.tool.rectangle
  (:require [pixel-art.tool.utils :refer [commit-preview-changes
                                          get-tool-options resize-pixel
                                          update-preview-and-draw]]
            [pixel-art.utils.geometry :as geometry]
            [sc.api :as api]))

(defn init [] {:type :rectangle})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}
   {:type :checkbox
    :field :fill
    :initial-value false
    :label "Fill"}
   {:type :checkbox
    :field :keep-ratio
    :initial-value false
    :label "Keep ration"}])

(defn- get-filled-rectangle-points [initial-mouse-down-pos event-pos pixel-size]
  (let [ordered-points (geometry/get-ordered-rectangle-points
                        (concat (resize-pixel initial-mouse-down-pos pixel-size)
                                (resize-pixel event-pos pixel-size)))]
    (apply geometry/get-rectange-points (vals ordered-points))))

;; todo: naming; outline, borders, empty
(defn- get-outline-rectangle-points [initial-mouse-down-pos event-pos pixel-size]
  (->> (geometry/get-rectange-border-points initial-mouse-down-pos event-pos)
       (mapcat #(resize-pixel % pixel-size)) ;; todo: optimize
       dedupe))

(defn handle-mouse-event [event db]
  (let [{:keys [color initial-mouse-down-pos user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [{:keys [pixel-size fill]} (get-tool-options db)
            rectangle-points (if fill
                               (get-filled-rectangle-points initial-mouse-down-pos (:pos event) pixel-size)
                               (get-outline-rectangle-points initial-mouse-down-pos (:pos event) pixel-size))
            preview (->> rectangle-points
                         (map (fn [p] [p color]))
                         (into {}))]
        (update-preview-and-draw db preview {:clear true}))

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :highlight-pixels points})

      (= :mouse-up (:type event))
      (commit-preview-changes db))))
