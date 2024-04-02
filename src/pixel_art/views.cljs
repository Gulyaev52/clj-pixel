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

(defn drag-control-view [style]
  [:div {:style (merge {:position "absolute"
                        :border-radius "100%"
                        :width "25px"
                        :height "25px"
                        :border "1px solid black"
                        :background-color "white"}
                       style)}])

(defn selection-controls-view []
  (let [tool @(re-frame/subscribe [::subs/tool])
        scale @(re-frame/subscribe [::subs/scale])
        selection-manager @(re-frame/subscribe [::subs/selection-manager])]
    (def tool tool)
    (def scale scale)
    (def selection-manager selection-manager)
    (if (= (:type tool) :rectangle-select)
      (let [{{:keys [selection show-selection-controls]} :state} tool
            top-left (->> (sort-by (fn [{:keys [pos]}] [(:x pos) (:y pos)]) selection)
                          first
                          :pos
                          ((fn [{:keys [x y]}] {:x (* scale x) :y (* scale y)})))
            bottom-right (->> (sort-by (fn [{:keys [pos]}] [(:x pos) (:y pos)]) selection)
                              last
                              :pos
                              ((fn [{:keys [x y]}] {:x (* scale (inc x)) :y (* scale (inc y))})))
            width (- (:x bottom-right) (:x top-left))
            height (- (:y bottom-right) (:y top-left))]
        (when show-selection-controls
          [:div {:style {:position "absolute"
                         :width width
                         :height height
                         :left (:x top-left)
                         :top (:y top-left)
                         :pointer-events "none"}}
           [drag-control-view {:top 0 :left 0 :transform "translateX(-50%) translateY(-50%)"}]
           [drag-control-view {:top 0 :left "100%" :transform "translateX(-50%) translateY(-50%)"}]
           [drag-control-view {:top 0 :left "50%" :transform "translateX(-50%) translateY(-50%)"}]
           [drag-control-view {:left 0 :top "50%" :transform "translateX(-50%) translateY(-50%)"}]
           [drag-control-view {:right 0 :top "50%" :transform "translateX(50%) translateY(-50%)"}]
           [drag-control-view {:bottom 0 :left 0 :transform "translateX(-50%) translateY(50%)"}]
           [drag-control-view {:bottom 0 :left "100%" :transform "translateX(-50%) translateY(50%)"}]
           [drag-control-view {:bottom 0 :left "50%" :transform "translateX(-50%) translateY(50%)"}]]))
      [:div])))

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
      [:div {:style {:position "relative"}}
       [selection-controls-view]
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
                                      (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos])))))}]]]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
