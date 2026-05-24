(ns pixel-art.tool.shape-selection
  (:require
   [pixel-art.model.cel :as cel]
   [pixel-art.model.preview :as preview]
   [pixel-art.tool.selection :as selection]
   [pixel-art.tool.utils :refer [get-current-cel get-preview-from-current-cel]]
   [pixel-art.utils.geometry :as geometry]))
;; todo: переименовать в wand?
(def tool
  (selection/make
   {:type :shape-selection
    :iter-selection-points
    (fn iter-selection-points [f db event]
      (let [{:keys [width]} (-> db :sprite :size)
            current-cel (get-current-cel db)
            color-under-mouse (cel/get-pixel (:pos event) current-cel)
            preview (get-preview-from-current-cel db)

            visited #js {}]
        ;; iter connected pixels with the same color that is under mouse
        (geometry/visit-connected-pixels (:pos event)
                                         (fn [x y]
                                           (let [idx (+ x (* width y))]
                                             (when (and (not (js-in idx visited))
                                                        (= (preview/get-color preview x y) color-under-mouse))
                                               (f x y)
                                               (aset visited idx true)
                                               true))))))}))
