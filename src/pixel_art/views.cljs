(ns pixel-art.views
  (:require [pixel-art.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]
            [pixel-art.events :as events]))

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])]
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
                               (re-frame/dispatch [::events/handle-mouse-event :mouse-down event]))
                :onMouseUp (fn [event]
                             (re-frame/dispatch [::events/handle-mouse-event :mouse-up event]))
                :onMouseMove (fn [event]
                               (re-frame/dispatch [::events/handle-mouse-event :mouse-move event]))}]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
