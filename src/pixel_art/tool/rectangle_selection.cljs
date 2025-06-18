(ns pixel-art.tool.rectangle-selection
  (:require
   ["./t.js" :as t]
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
        (t/test (:x top-left) (:y top-left)
                (:x bottom-right) (:y bottom-right))))}))
