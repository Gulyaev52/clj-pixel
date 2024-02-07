(ns pixel-art.views
  (:require [pixel-art.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]
            [pixel-art.events :as events]))

(defn canvas-pos->frame-pos [event scale canvas]
  (let [rect (. canvas getBoundingClientRect)]
    {:x (. js/Math (floor (/ (- (. event -clientX) (. rect -left))
                             scale)))
     :y (. js/Math (floor (/ (- (. event -clientY) (. rect -top))
                             scale)))}))

(def !last-mouse-pos (atom nil))

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        scale @(re-frame/subscribe [::subs/scale])]
    [:div
     [:select {:value (:type tool)
               :onChange (fn [event]
                           (re-frame/dispatch [::events/select-tool (keyword (.. event -target -value))]))}
      [:option {:value :pen} "pen"]
      [:option {:value :rectangle} "rectangle"]
      [:option {:value :rectangle-select} "rectangle-select"]]
     [:div {:style {:display :flex :justify-content :center}}
      [:canvas {:id "tutorial"
                :style {:border "1px solid black"}
                :onMouseDown (fn [event]
                               (let [mouse-pos (canvas-pos->frame-pos event
                                                                      scale
                                                                      (. js/document (getElementById "tutorial")))]
                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-down mouse-pos])))
                :onMouseUp (fn [event]
                             (let [mouse-pos (canvas-pos->frame-pos event
                                                                    scale
                                                                    (. js/document (getElementById "tutorial")))]
                               (reset! !last-mouse-pos mouse-pos)
                               (re-frame/dispatch [::events/handle-mouse-event :mouse-up mouse-pos])))
                :onMouseMove (fn [event]
                               (let [mouse-pos (canvas-pos->frame-pos event
                                                                      scale
                                                                      (. js/document (getElementById "tutorial")))]
                                 (when (not= mouse-pos @!last-mouse-pos)
                                   (do
                                     (reset! !last-mouse-pos mouse-pos)
                                     (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos])))))}]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
