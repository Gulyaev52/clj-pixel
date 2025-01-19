(ns pixel-art.tool.shape-selection
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.tool.selection :as selection]
   [pixel-art.tool.utils :refer [get-current-cel]]
   [pixel-art.utils.geometry :as geometry]))

(def tool
  (selection/make
   {:type :shape-selection
    :get-selection-only-on-mouse-down true
    :get-selection
    (fn [db event]
      (let [current-cel (get-current-cel db)
            color-under-mouse (cel/get-pixel (:pos event) current-cel)]
        (->> (geometry/flood-fill (:pos event)
                                  (:size current-cel)
                                  (:pixels current-cel)
                                  color-under-mouse)
             (map (fn [p] [p color-under-mouse]))
             (into {}))))}))
