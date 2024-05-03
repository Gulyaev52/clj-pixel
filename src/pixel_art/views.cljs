(ns pixel-art.views
  (:require [pixel-art.events :as events]
            [pixel-art.subs :as subs]
            [pixel-art.tool.core :as tool]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

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

(defn options-toolbar [tool-type]
  (let [options @(re-frame/subscribe [::subs/tool-options])
        options-spec (tool/options-specs tool-type)]
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

(defn frames []
  (letfn [(box [style onClick children]
            [:div {:onClick onClick
                   :style (merge {:position "absolute"
                                  :display "flex"
                                  :align-items "center"
                                  :justify-content "center"
                                  :width "30px"
                                  :height "30px"
                                  :font-size "14px"
                                  :font-weight "bold"}
                                 style)}
             children])]
    (let [sprite @(re-frame/subscribe [::subs/sprite])
          {:keys [frames current-frame-idx]} sprite]
      [:div {:style {:display :flex :gap "10px"}}
       (for [[idx frame] (map-indexed vector frames)]
         [:div {:onClick (fn [_] (re-frame/dispatch [::events/select-frame idx]))
                :style {:position "relative"
                        :width 96
                        :height 96
                        :border "3px solid"
                        :border-color (if (= idx current-frame-idx)
                                        "gold" "#444")
                        :border-radius "3px"}}
          [box {:left 0
                :top 0
                :background-color "gold"}
           (fn [_])
           (inc idx)]
          [box {:right 0
                :top 0
                :background-color "rgba(100, 100, 100, 0.6)"}
           (fn [_] (re-frame/dispatch [::events/remove-frame idx]))
           "DE"]
          [box {:right 0
                :bottom 0
                :background-color "rgba(100, 100, 100, 0.6)"}
           (fn [_] (re-frame/dispatch [::events/duplicate-frame idx]))
           "DU"]
          [box {:left 0
                :bottom 0
                :background-color "rgba(100, 100, 100, 0.6)"}
           (fn [_])
           "M"]])
       [:button {:onClick (fn [_] (re-frame/dispatch [::events/add-frame]))} "new frame"]])))

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        scale @(re-frame/subscribe [::subs/scale])
        pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])]
    [:div {:style {:display :flex :flex-direction :column :gap "10px"}}
     [options-toolbar (:type tool)]
     [:div {:style {:display :flex :gap "8px"}}
      [:select {:value (:type tool)
                :onChange (fn [event]
                            (re-frame/dispatch [::events/select-tool (keyword (.. event -target -value))]))}
       (map (fn [t] [:option {:value t} (name t)]) tool/types)]
      [checkbox {:value pixels-grid-enabled
                 :label "grid"
                 :onChange (fn [checked] (re-frame/dispatch [::events/enable-pixels-grid checked]))}]]
     [:div
      [frames]]
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
                         :top 0}}]
       [:canvas {:id "grid"
                 :style {:position :absolute
                         :left 0
                         :top 0}}]]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
