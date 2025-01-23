(ns pixel-art.tool.color-picker
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.tool.utils :refer [get-current-cel get-current-color-type
                                 with-highlight-cel-under-cursor]]))

(def tool
  {:type :color-picker
   :init (fn [] {:type :color-picker})
   :options-spec []
   :get-events-handlers
   (fn []
     (with-highlight-cel-under-cursor
       {:mouse-down (fn [db event]
                      (let [color (->> (get-current-cel db)
                                       (cel/get-pixel (:pos event)))]
                        {:db (assoc db (get-current-color-type (:right-button event)) color)}))}))})
