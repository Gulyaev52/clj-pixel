(ns pixel-art.tool.circle
  (:require [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                          get-current-color get-tool-options
                                          resize-pixel]]
            [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :circle})

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

(defn get-filled-circle-points [pos1 pos2 pixel-size]
  (->> (geometry/get-circle-pixels pos1 pos2 pixel-size)
       (partition-all 2)
       (filter (fn [[x1 x2]] (not= x1 x2)))
       (mapcat (fn [[x1 x2]]
                 (geometry/get-rectange-points x1 x2)))))

(defn- get-circle-image [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size fill keep-ratio]} (get-tool-options db)
        current-pos (if keep-ratio
                      (geometry/get-scaled-points initial-mouse-down-pos (:pos event))
                      (:pos event))
        circle-points (if fill
                        (get-filled-circle-points initial-mouse-down-pos current-pos pixel-size)
                        (geometry/get-circle-pixels initial-mouse-down-pos current-pos pixel-size))]
    (->> circle-points
         (map (fn [p] [p current-color]))
         (into {}))))

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      {:db db
       :fx [[:clear-visual-effects]
            [:clear-preview]
            [:draw-preview (get-circle-image db event)]]}

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-visual-effects]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [rectangle-image (get-circle-image db event)]
        (commit-changes-and-init-tool db rectangle-image (init))))))
