(ns pixel-art.tool.circle
  (:require ["../shapeTool.js" :as shape-tool]
            [pixel-art.tool.utils :refer [commit-changes-and-init-tool
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

(defn get-scaled-points
  "Transform the coordinates to preserve a square 1:1 ratio from the origin of the shape"
  [initial-pos pos]
  (-> (shape-tool/getScaledCoords (:x initial-pos) (:y initial-pos)
                                  (:x pos) (:y pos))
      (js->clj :keywordize-keys true)
      ((fn [{:keys [col row]}] {:x col :y row}))))
(comment (get-scaled-points {:x 0 :y 0} {:x 2 :y 2}))

(defn get-outline-circle-points [pos1 pos2 pixel-size]
  (->> (shape-tool/getCirclePixels (:x pos1) (:y pos1) (:x pos2) (:y pos2) pixel-size)
       js->clj
       (map (fn [[x y]] {:x x :y y}))))

(defn get-filled-circle-points [pos1 pos2 pixel-size]
  (->> (get-outline-circle-points pos1 pos2 pixel-size)
       (partition-all 2)
       (filter (fn [[x1 x2]] (not= x1 x2)))
       (mapcat (fn [[x1 x2]]
                 (geometry/get-rectange-points x1 x2)))))

(defn- get-circle-image [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size fill keep-ratio]} (get-tool-options db)
        current-pos (if keep-ratio
                      (get-scaled-points initial-mouse-down-pos (:pos event))
                      (:pos event))
        circle-points (if fill
                        (get-filled-circle-points initial-mouse-down-pos current-pos pixel-size)
                        (get-outline-circle-points initial-mouse-down-pos current-pos pixel-size))]
    (->> circle-points
         (map (fn [p] [p current-color]))
         (into {}))))

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      {:db db
       :fx [[:clear-preview]
            [:draw-preview (get-circle-image db event)]]}

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-preview]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [rectangle-image (get-circle-image db event)]
        (commit-changes-and-init-tool db rectangle-image (init))))))
