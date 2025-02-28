(ns pixel-art.tool.rectangle-selection
  (:require
   [clojure.set]
   [pixel-art.model.cel :as cel]
   [pixel-art.tool.selection :as selection]
   [pixel-art.tool.utils :refer [get-current-cel]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (selection/make
   {:type :rectangle-selection
    :get-selection
    (fn [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            current-cel (get-current-cel db)
            {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [initial-mouse-down-pos (:pos event)])
            x-top-left (:x top-left)
            x-bottom-right (:x bottom-right)
            y-top-left (:y top-left)
            y-bottom-right (:y bottom-right)]
        (->> (for [x (range x-top-left (inc x-bottom-right))
                   y (range y-top-left (inc y-bottom-right))]
               (let [p {:x x :y y}]
                 [p (cel/get-pixel p current-cel)]))
             (into {}))))}))
