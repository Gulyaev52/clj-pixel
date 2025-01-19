(ns pixel-art.tool.color-picker
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.tool.utils :refer [get-current-cel get-current-color-type
                                 make-default-handle-mouse-event]]))

(defn- init [] {:type :color-picker})

(def tool
  {:type :color-picker
   :init init
   :options-spec []
   :handle-mouse-event
   (make-default-handle-mouse-event
    {:mouse-down (fn [db event]
                   (let [color (->> (get-current-cel db)
                                    (cel/get-pixel (:pos event)))]
                     {:db (assoc db (get-current-color-type (:right-button event)) color)}))})})
