(ns pixel-art.views
  (:require [pixel-art.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]
            [pixel-art.events :as events]
            [pixel-art.tool.pen :as pen]))

(defn canvas-pos->frame-pos [event scale canvas]
  (let [rect (. canvas getBoundingClientRect)]
    {:x (. js/Math (floor (/ (- (. event -clientX) (. rect -left))
                             scale)))
     :y (. js/Math (floor (/ (- (. event -clientY) (. rect -top))
                             scale)))}))

(def !last-mouse-pos (atom nil))

(defn slider [{:keys [value label min max onChange]}]
  ;; todo: labels
  [:div {:style {:display :flex :align-items :center}}
   [:span (str label " (" value ")")]
   [:input {:type "range"
            :value value
            :min min
            :max max
            :onChange (fn [e]
                        (let [value (parse-double (.. e -target -value))]
                          (onChange value)))}]])

(defn checkbox [{:keys [value onChange label]}]
  [:div {:style {:display :flex :align-items :center}}
   [:input {:type "checkbox"
            :checked value
            :onChange (fn [e] (onChange (.. e -target -checked)))}]
   [:span label]])

(defn get-tool-options-spec [tool-type]
  (case tool-type
    :pen pen/options-spec))

(defn options-toolbar [tool-type]
  (let [options @(re-frame/subscribe [::subs/tool-options])
        options-spec (get-tool-options-spec tool-type)]
    [:div {:style {:display :flex :align-items :center :gap "6px"}}
     (map (fn [option-spec]
            (let [value (get options (:field option-spec))
                  onChange #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
                  props (assoc option-spec
                               :value value
                               :onChange onChange)]
              (case (:type option-spec)
                :slider (slider props)
                :checkbox (checkbox props))))
          options-spec)]))

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        scale @(re-frame/subscribe [::subs/scale])]
    [:div
     [options-toolbar (:type tool)]
     [:select {:value (:type tool)
               :onChange (fn [event]
                           (re-frame/dispatch [::events/select-tool (keyword (.. event -target -value))]))}
      [:option {:value :pen} "pen"]
      [:option {:value :rectangle} "rectangle"]
      [:option {:value :rectangle-select} "rectangle-select"]]
     [:div {:style {:display :flex :justify-content :center}}
      [:div {:style {:position "relative"
                     :border "1px solid black"}
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
                            (println "bla")
                            (re-frame/dispatch [::events/handle-mouse-event :mouse-up mouse-pos])))
             :onMouseMove (fn [event]
                            (let [mouse-pos (canvas-pos->frame-pos event
                                                                   scale
                                                                   (. js/document (getElementById "tutorial")))]
                              (when (not= mouse-pos @!last-mouse-pos)
                                (do
                                  (reset! !last-mouse-pos mouse-pos)
                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos])))))}
       [:canvas {:id "tutorial"}]
       [:canvas {:id "preview"
                 :style {:position :absolute
                         :left 0
                         :top 0}}]]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
