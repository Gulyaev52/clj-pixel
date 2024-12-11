(ns pixel-art.tool.color-picker
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.tool.utils :refer [get-current-color-type
                                          get-current-cel]]))

(defn init [] {:type :color-picker})

(def options-spec
  [])

(defn handle-mouse-event [db event]
  (cond
    (= (:type event) :mouse-down)
    (let [color (->> (get-current-cel db)
                     (cel/get-pixel (:pos event)))]
      {:db (assoc db (get-current-color-type (:right-button event)) color)})

    (and (= (:type event) :mouse-move) (not (:user-is-drawing db)))
    {:db db
     :fx [[:clear-visual-effects]
          [:highlight-pixels [(:pos event)]]]}

    :else {:db db}))
