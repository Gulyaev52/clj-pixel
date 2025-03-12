(ns pixel-art.tool.rectangle-selection
  (:require
   [clojure.set]
   [pixel-art.tool.selection :as selection]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (selection/make
   {:type :rectangle-selection
    :get-selection
    (fn get-selection [db event]
      (let [{:keys [initial-mouse-down-pos]} db
            {:keys [top-left bottom-right]} (geometry/get-ordered-rectangle-points [initial-mouse-down-pos (:pos event)])]
        (for [x (range (:x top-left) (inc (:x bottom-right)))
              y (range (:y top-left) (inc (:y bottom-right)))]
          #js [x y])))}))
