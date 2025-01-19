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
      (let [current-cel (get-current-cel db)]
        (->> (geometry/get-rectange-points (:initial-mouse-down-pos db) (:pos event))
             (map (fn [p] [p (cel/get-pixel p current-cel)]))
             (into {}))))}))
