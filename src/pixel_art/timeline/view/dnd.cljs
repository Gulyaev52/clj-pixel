(ns pixel-art.timeline.view.dnd 
  (:require
   ["react-dnd" :as react-dnd]
   ["react-dnd-scrolling" :as react-dnd-scrolling]
   [pixel-art.timeline.events :as events]
   [re-frame.core :as re-frame]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def dndScrollingVerticalStrength (react-dnd-scrolling/createVerticalStrength 50))

(defn droppable-zone [{:keys [accept on-drop can-drop attrs]} styles]
  (let [[{:keys [over can-drop]}, ref] (react-dnd/useDrop
                                        #js
                                         {"accept" accept
                                          "drop" on-drop
                                          "canDrop" can-drop
                                          "collect" (fn [^js monitor]
                                                      {:over (.. monitor isOver)
                                                       :can-drop (.. monitor canDrop)})})]
    (when can-drop
      [:div (merge {:ref ref
                    :style (merge {:position "absolute"
                                   :z-index 1
                                   :width "100%"
                                   :background-color (when (and can-drop over) "blue")}
                                  styles)}
                   attrs)])))

(def-func-component droppable-layer-zone [to-idx styles]
  (droppable-zone {:accept "layer"
                   :attrs {:data-testid (str "layer-drop-" to-idx)}
                   :on-drop (fn [layer]
                              (re-frame/dispatch [::events/move-layer (:idx layer) to-idx]))}
                  (merge {:height "20px" :width "100%"} styles)))

(def-func-component droppable-frame-zone [idx styles]
  (droppable-zone {:accept "frame"
                   :attrs {:data-testid (str "frame-drop-" idx)}
                   :on-drop (fn [frame]
                              (re-frame/dispatch [::events/move-frame (:idx frame) idx]))}
                  (merge {:width "30px" :height "100%"} styles)))

(def-func-component droppable-cel-zone [pos direction-type styles]
  (droppable-zone {:accept "cel"
                   :can-drop (fn [cel]
                               (case direction-type
                                 :frame true
                                 :layer (not= (:layer-idx pos) (-> cel :pos :layer-idx))))
                   :on-drop (fn [cel]
                              (re-frame/dispatch [::events/move-cel (:pos cel) pos]))}
                  styles))
