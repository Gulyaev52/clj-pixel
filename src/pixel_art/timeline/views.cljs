(ns pixel-art.timeline.views
  (:require
   ["react-dnd" :as react-dnd]
   ["react-dnd-scrolling" :as react-dnd-scrolling]
   [clojure.string :as string]
   [pixel-art.model.sprite-canvas :as sprite-canvas]
   [pixel-art.timeline.events :as events]
   [pixel-art.timeline.subs :as subs]
   [pixel-art.timeline.view.dnd :refer [dndScrollingVerticalStrength
                                        droppable-cel-zone
                                        droppable-frame-zone
                                        droppable-layer-zone]]
   [pixel-art.timeline.view.toolbar :refer [toolbar]]
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.utils.coll :as coll]
   [pixel-art.views.constants :refer [preview-container-bg-color]]
   [pixel-art.views.preview :refer [preview-image]]
   [pixel-art.views.ui-kit :refer [icon-button typography use-theme-token]]
   [pixel-art.views.use-vertical-resizer :refer [use-vertical-resizer]]
   [re-frame.core :as re-frame]
   [react :as react]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def cel-height "80px")

(defn- get-border-color [{:keys [current selected]} theme-token]
  (cond
    current "yellow" ;; todo: use from theme?
    selected "green"
    :else (.-colorBorder theme-token)))

(def-func-component layer-view [layer]
  (let [[_ ref] (react-dnd/useDrag (fn [] #js {"type" "layer" "item" layer}))
        theme-token (use-theme-token)]
    [:div {:style {:display :flex
                   :align-items "center"
                   :position "relative"}}
     (when (= (:idx layer) 0)
       [droppable-layer-zone (:idx layer) {:top 0 :transform "translateY(-50%)"}])
     [:div {:ref ref
            :on-click (fn [] (re-frame/dispatch [::events/select-layer (:idx layer)]))
            :data-testid (str "layer-" (:idx layer))
            :data-current (str (:current layer))
            :style {:display :flex
                    :align-items "center"
                    :padding "4px"
                    :width "150px"
                    :height cel-height
                    :border-style "solid"
                    :border-color (get-border-color layer theme-token)
                    :border-width (if (:current layer)
                                    "2px"
                                    "1px")
                    :cursor "pointer"}}
      [typography (:name layer)]]
     [droppable-layer-zone (inc (:idx layer)) {:bottom 0 :transform "translateY(50%)"}]]))

(def-func-component frame-view [frame]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "frame"
                                          "item" frame
                                          "collect" (fn [^js monitor]
                                                      {:dragging (.. monitor isDragging)})}))
        theme-token (use-theme-token)]
    [:div {:style {:position "sticky"
                   :top 0
                   :background-color (.-colorBgContainer theme-token)
                   :z-index 1}}
     (when (= (:idx frame) 0)
       [droppable-frame-zone (:idx frame) {:left 0
                                           :top 0
                                           :transform "translateX(-50%)"}])
     [:div {:on-click (fn [] (re-frame/dispatch [::events/select-frame (:idx frame)]))
            :ref ref
            :data-testid (str "frame-" (:idx frame))
            :data-current (str (:current frame))
            :style {:display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :height "100%"
                    :border-style (if (:onion-skin frame) "dashed" "solid")
                    :border-color (get-border-color frame theme-token)
                    :border-width (if (:current frame)
                                    "2px"
                                    "1px")
                    :text-align "center"
                    :cursor "pointer"}}
      [typography (inc (:idx frame))]]
     [droppable-frame-zone (inc (:idx frame)) {:right 0
                                               :top 0
                                               :transform "translateX(50%)"}]]))

(defn- get-group-color [group-number]
  (nth (cycle ["green" "pink" "yellow" "red" "blue" "purple"]) group-number))

(def-func-component cel-view [cel]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "cel"
                                          "item" cel
                                          "collect" (fn [^js monitor]
                                                      {:dragging (.. monitor isDragging)})}))
        cel-preview (react/useMemo (fn []
                                     (canvas/generate-data-url #(sprite-canvas/draw-cel cel %)
                                                               (:size cel)))
                                   (array cel))
        theme-token (use-theme-token)]
    [:div {:style {:position "relative"}}
     (when (= (-> cel :pos :frame-idx) 0)
       [droppable-cel-zone
        (:pos cel)
        :frame
        {:height "100%"
         :width "20px"
         :top 0
         :left 0
         :transform "translateX(-50%)"}])
     [:div {:on-click (fn [e]
                        (cond
                          (.. e -shiftKey)
                          (re-frame/dispatch [::events/add-cels-range-to-selection (:pos cel)])
                          (.. e -ctrlKey)
                          (re-frame/dispatch [::events/toggle-cel-to-selection (:pos cel)])
                          :else (re-frame/dispatch [::events/select-only-1-cel (:pos cel)])))
            :ref ref
            :data-testid (str "cel-" (-> cel :pos :frame-idx) "-" (-> cel :pos :layer-idx))
            :data-selected (str (:selected cel))
            :style {:position "relative"
                    :display :flex
                    :align-items :center
                    :justify-content :center
                    :height "100%"
                    :border-style "solid"
                    :border-color (get-border-color cel theme-token)
                    :border-width (if (:selected cel)
                                    "2px"
                                    "1px")
                    :background-color preview-container-bg-color
                    :cursor "pointer"}}
      [preview-image cel-preview (merge {:max-width "100%"
                                         :max-height "100%"}
                                        (if (> (:width (:size cel))
                                               (:height (:size cel)))
                                          {:width "100%"}
                                          {:height "100%"}))]
      [:div {:data-testid (str "cel-group-" (-> cel :pos :frame-idx) "-" (-> cel :pos :layer-idx))
             :style {:position "absolute"
                     :top 0}}
       (some-> (:group-number cel)
               inc
               (#(typography {:style {:color (get-group-color (:group-number cel))
                                      :font-weight "bold"}}
                             %)))]]
     [droppable-cel-zone
      (:pos cel)
      :layer
      {:height "100%"
       :width "100%"
       :top 0
       :left 0}]
     [droppable-cel-zone
      (update (:pos cel) :frame-idx inc)
      :frame
      {:height "100%"
       :width "20px"
       :top 0
       :right 0
       :transform "translateX(50%)"}]]))

(def-func-component timeline-panel []
  (let [{:keys [cels layers frames disabled-actions some-layer-visible all-frames-duration some-layer-automatic-linking]} @(re-frame/subscribe [::subs/timeline])
        current-frame (coll/find-first :current frames)
        cels-by-layers (-> cels
                           (#(group-by (fn [c] (-> c :pos :layer-idx)) %))
                           (update-vals (fn [cels] (sort-by #(-> % :pos :frame-idx) cels))))
        vertical-resizer-refs (use-vertical-resizer)

        timeline-container-ref (react/useRef)
        theme-token (use-theme-token)
        _ (react-dnd-scrolling/useDndScrolling timeline-container-ref #js {"verticalStrength" dndScrollingVerticalStrength})]
    [:div {:ref (:container-ref vertical-resizer-refs)
           :style {:display "flex"
                   :flex-direction "column"
                   :padding "4px"
                   :gap "4px"
                   :flex-shrink 0
                   :height "300px"
                   :min-height "16px"}}

     [:div {:ref (:handler-ref vertical-resizer-refs)
            :style {:min-height "4px"
                    :width "40px"
                    :background-color "gray"
                    :cursor "grab"
                    :align-self "center"}}]

     [toolbar {:current-frame current-frame
               :disabled-actions disabled-actions
               :all-frames-duration all-frames-duration}]

     [:div {:ref timeline-container-ref
            :style {:display :grid
                    :grid-template-rows "min-content"
                    :grid-auto-rows cel-height
                    :grid-template-columns (str "min-content min-content " (->> (repeat (count frames) "100px") (string/join " ")))
                    :grid-column-gap "4px"
                    :grid-row-gap "4px"
                    :margin-top "4px"
                    :overflow "auto"}}
      [:div {:style {:display "flex"
                     :align-items "center"
                     :position "sticky"
                     :z-index 1
                     :background-color (.-colorBgContainer theme-token)
                     :top 0}}
       [icon-button {:src (if some-layer-visible
                            :visibility
                            :visibility-off)
                     :title "toggle all layers visibility"
                     :data-testid "btn-toggle-all-layers-visibility"
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::events/toggle-all-layers-visibility]))}]
       [icon-button {:src (if some-layer-automatic-linking
                            :link
                            :link-off)
                     :title "toggle all layers automatic linking"
                     :data-testid "btn-toggle-all-layers-automatic-linking"
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::events/toggle-all-layers-automatic-linking]))}]]
      [:div {:style {:position "sticky"
                     :z-index 1
                     :background-color (.-colorBgContainer theme-token)
                     :top 0}}]
      (for [frame frames]
        ^{:key (:idx frame)}
        [frame-view frame])
      (doall
       (for [layer layers]
         ^{:key (:idx layer)}
         [:<>
          [:div {:style {:display "flex"
                         :align-items "center"}}
           [icon-button {:src (if (:visible? layer)
                                :visibility
                                :visibility-off)
                         :title "toggle layer's visibility"
                         :data-testid (str "btn-toggle-layer-visibility-" (:idx layer))
                         :size :sm
                         :on-click (fn []
                                     (re-frame/dispatch [::events/toggle-layer-visibility (:idx layer)]))}]
           [icon-button {:src (if (:automatic-linking? layer)
                                :link
                                :link-off)
                         :title "toggle layer's automatic linking"
                         :data-testid (str "btn-toggle-layer-automatic-linking-" (:idx layer))
                         :size :sm
                         :on-click (fn []
                                     (re-frame/dispatch [::events/toggle-layer-automatic-linking (:idx layer)]))}]]
          [layer-view layer]
          (for [cel (cels-by-layers (:idx layer))]
            ^{:key (str (:frame-idx (:pos cel)) "-" (:layer-idx (:pos cel)))}
            [cel-view cel])]))]]))
