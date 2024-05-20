(ns pixel-art.tool.color-picker
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.tool.utils :refer [get-current-frame]]))

(defn init [] {:type :color-picker})

(def options-spec
  [])

(defn handle-mouse-event [db event]
  (cond
    (= (:type event) :mouse-down)
    (let [color (frame/get-pixel (:pos event) (get-current-frame db))]
      {:db (assoc db :color color)})

    (and (= (:type event) :mouse-move) (not (:user-is-drawing db)))
    {:db db
     :fx [[:clear-preview]
          [:highlight-pixels [(:pos event)]]]}

    :else {:db db}))
