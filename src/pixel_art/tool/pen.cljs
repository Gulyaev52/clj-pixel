(ns pixel-art.tool.pen
  (:require
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool
                                 get-current-color get-tool-options
                                 resize-pixel]]
   [pixel-art.utils.geometry :as geometry]))

(defn init [] {:type :pen
               :state {:changes {}
                       :prev-pos nil}})

(def options-spec
  [{:type :slider
    :field :pixel-size
    :label "Pixel size"
    :initial-value 1
    :min 1
    :max 64}])

(defn- get-interpolated-pixels
  "The pen movement is too fast for the mousemove frequency, there is a gap between the
   current point and the previously drawn one.
   We fill the gap by calculating missing dots (simple linear interpolation) and draw them."
  [prev-pos pos]
  (if (and prev-pos
           (or (> (. js/Math (abs (- (:x pos) (:x prev-pos)))) 1)
               (> (. js/Math (abs (- (:y pos) (:y prev-pos)))) 1)))
    (geometry/get-line-pixels prev-pos pos)
    [pos]))

(defn handle-mouse-event [db event]
  (let [{:keys [user-is-drawing tool]} db]
    (cond
      (or (= (:type event) :mouse-down)
          (and (= (:type event) :mouse-move) user-is-drawing))
      (let [current-color (get-current-color db event)
            {:keys [pixel-size]} (get-tool-options db)
            pos (:pos event)
            state (:state tool)
            new-pixels (->>
                        (get-interpolated-pixels (-> tool :state :prev-pos) pos)
                        (mapcat #(resize-pixel % pixel-size))
                        (map (fn [p] [p current-color])))]
        {:db (assoc-in db [:tool :state] {:changes (merge (:changes state) new-pixels)
                                          :prev-pos pos})
         :fx [[:clear-visual-effects]
              [:draw-preview new-pixels]]})

      (and (= (:type event) :mouse-move) (not user-is-drawing))
      (let [{:keys [pixel-size]} (get-tool-options db)
            points (resize-pixel (:pos event) pixel-size)]
        {:db db
         :fx [[:clear-visual-effects]
              [:highlight-pixels points]]})

      (= :mouse-up (:type event))
      (let [changes (-> db :tool :state :changes)]
        (commit-changes-and-init-tool db changes (init))))))
