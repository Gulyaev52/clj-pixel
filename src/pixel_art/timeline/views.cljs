(ns pixel-art.timeline.views
  (:require
   ["../views/react-dnd-scrolling" :as react-dnd-scrolling]
   ["antd" :as antd]
   ["react-dnd" :as react-dnd]
   [clojure.string :as string]
   [pixel-art.canvas :as canvas]
   [pixel-art.timeline.subs :as subs]
   [pixel-art.timeline.events :as events]
   [pixel-art.onion-skin.events :as onion-skin.events]
   [pixel-art.onion-skin.subs :as onion-skin.subs]
   [pixel-art.onion-skin.views :refer [onion-skin-settings]]
   [pixel-art.sprite-preview.events :as sprite-preview.events]
   [pixel-art.sprite-preview.views :refer [sprite-preview-modal]]
   [pixel-art.utils.coll :as coll]
   [pixel-art.views.constants :refer [preview-container-bg-color]]
   [pixel-art.views.preview :refer [preview-image]]
   [pixel-art.views.ui-kit :refer [button icon-button input-number popover
                                   space typography use-theme-token]]
   [pixel-art.views.use-vertical-resizer :refer [use-vertical-resizer]]
   [re-frame.core :as re-frame]
   [react :as react]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(def cel-height "50px")

(defn- get-border-color [{:keys [current selected]} theme-token]
  (cond
    current "yellow" ;; todo: use from theme?
    selected "green"
    :else (.-colorBorder theme-token)))

(defn- droppable-zone [{:keys [accept on-drop can-drop]} styles]
  (let [[{:keys [over can-drop]}, ref] (react-dnd/useDrop
                                        #js
                                         {"accept" accept
                                          "drop" on-drop
                                          "canDrop" can-drop
                                          "collect" (fn [^js monitor]
                                                      {:over (.. monitor isOver)
                                                       :can-drop (.. monitor canDrop)})})]
    (when can-drop
      [:div {:ref ref
             :style (merge {:position "absolute"
                            :z-index 1
                            :width "100%"
                            :background-color (when (and can-drop over) "blue")}
                           styles)}])))

(def-func-component droppable-layer-zone [to-idx styles]
  (droppable-zone {:accept "layer"
                   :on-drop (fn [layer]
                              (re-frame/dispatch [::events/move-layer (:idx layer) to-idx]))}
                  (merge {:height "20px" :width "100%"} styles)))

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

(def-func-component droppable-frame-zone [idx styles]
  (droppable-zone {:accept "frame"
                   :on-drop (fn [frame]
                              (re-frame/dispatch [::events/move-frame (:idx frame) idx]))}
                  (merge {:width "30px" :height "100%"} styles)))

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

(def-func-component droppable-cel-zone [pos direction-type styles]
  (droppable-zone {:accept "cel"
                   :can-drop (fn [cel]
                               (case direction-type
                                 :frame true
                                 :layer (not= (:layer-idx pos) (-> cel :pos :layer-idx))))
                   :on-drop (fn [cel]
                              (re-frame/dispatch [::events/move-cel (:pos cel) pos]))}
                  styles))

(defn- get-group-color [group-number]
  (nth (cycle ["green" "pink" "yellow" "red" "blue" "purple"]) group-number))

(def-func-component cel-view [cel]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "cel"
                                          "item" cel
                                          "collect" (fn [^js monitor]
                                                      {:dragging (.. monitor isDragging)})}))
        cel-preview (react/useMemo (fn []
                                     (canvas/generate-data-url #(canvas/draw-cel cel %)
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
      [:div {:style {:position "absolute" :top 0}}
       (some-> (:group-number cel)
               inc
               (#(typography {:style {:color (when-let [group-number (:group-number cel)]
                                               (get-group-color group-number))}}
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

(defn- timeline-panel-section [title children]
  [:> antd/Card {:size "small"}
   [space
    [typography title]
    (into [:div {:style {:display "flex" :align-items "center"}}]
          children)]])

(defn- timeline-panel-toolbar [{:keys [disabled-actions all-frames-duration current-frame]}]
  (let [onion-skin-enabled @(re-frame/subscribe [::onion-skin.subs/enabled])]
    [:div {:style {:display "flex" :justify-content "space-between"}}
     [space
      [timeline-panel-section "Frames" [[icon-button {:src :add
                                                      :title "add empty frame"
                                                      :disabled (:add-frame disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/add-frame]))}]
                                        [icon-button {:src :remove
                                                      :title "remove frame"
                                                      :disabled (:remove-frame disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/remove-frame]))}]
                                        [icon-button {:src :duplicate
                                                      :title "duplicate frame"
                                                      :disabled (:duplicate-frame disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/duplicate-frame]))}]
                                        [icon-button {:src :arrow-left
                                                      :title "move frame left"
                                                      :disabled (:move-frame-left disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/move-frame-left]))}]
                                        [icon-button {:src :arrow-right
                                                      :title "move frame right"
                                                      :disabled (:move-frame-right disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/move-frame-right]))}]]]

      [timeline-panel-section "Layers" [[icon-button {:src :add
                                                      :title "add layer"
                                                      :disabled (:add-layer disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/add-layer]))}]
                                        [icon-button {:src :remove
                                                      :title "remove layer"
                                                      :disabled (:remove-layer disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/remove-layer]))}]
                                        [icon-button {:src :duplicate
                                                      :title "duplicate layer"
                                                      :disabled (:duplicate-layer disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/duplicate-layer]))}]
                                        [icon-button {:src :merge-down
                                                      :title "merge layer with below"
                                                      :disabled (:merge-layer-with-below disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/merge-layer-with-below]))}]
                                        [icon-button {:src :arrow-up
                                                      :title "move layer up"
                                                      :disabled (:move-layer-up disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/move-layer-up]))}]
                                        [icon-button {:src :arrow-down
                                                      :title "move layer down"
                                                      :disabled (:move-layer-down disabled-actions)
                                                      :size :sm
                                                      :on-click (fn [] (re-frame/dispatch [::events/move-layer-down]))}]
                                        [icon-button {:src :edit
                                                      :title "rename layer"
                                                      :size :sm
                                                      :on-click (fn [e]
                                                                  (. e stopPropagation)
                                                                  (let [new-name (js/prompt)]
                                                                    (when (seq (string/trim new-name))
                                                                      (re-frame/dispatch [::events/rename-layer new-name]))))}]]]

      [timeline-panel-section "Cels" [[icon-button {:src :link
                                                    :title "link cels"
                                                    :disabled (:link-cels disabled-actions)
                                                    :size :sm
                                                    :on-click (fn [] (re-frame/dispatch [::events/link-selected-cels]))}]
                                      [icon-button {:src :link-off
                                                    :title "unlink cels"
                                                    :disabled (:unlink-cel disabled-actions)
                                                    :size :sm
                                                    :on-click (fn [] (re-frame/dispatch [::events/unlink-selected-cels]))}]]]

      [timeline-panel-section "Onion skin"
       [[icon-button {:src (if onion-skin-enabled :layers-off :layers)
                      :title (if onion-skin-enabled "disable onion skin" "enable onion skin")
                      :size :sm
                      :on-click (fn [] (re-frame/dispatch [::onion-skin.events/set-enabled (not onion-skin-enabled)]))}]
        [popover
         (fn [open]
           [icon-button {:src :cog
                         :title "onion skin settings"
                         :size :sm
                         :on-click open}])
         [onion-skin-settings]]]]]

     [:div {:style {:display :flex}}
      [:<>
       [sprite-preview-modal]
       [button {:on-click (fn [] (re-frame/dispatch [::sprite-preview.events/open]))} "Show preview"]]
      [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
       [typography "Duration (ms)"]
       [input-number {:value (:duration current-frame)
                      :on-blur (fn [duration]
                                 (re-frame/dispatch [::events/set-frame-duration (:idx current-frame) duration]))}]]

      [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
       [typography "All frames duration (ms)"]
       [input-number {:value all-frames-duration
                      :on-blur (fn [duration]
                                 (re-frame/dispatch [::events/set-frame-duration-for-all duration]))}]]]]))

(def dndScrollingVerticalStrength (react-dnd-scrolling/createVerticalStrength 50))

(def-func-component timeline-panel []
  (let [{:keys [cels layers frames disabled-actions some-layer-visible some-layer-automatic-linking]} @(re-frame/subscribe [::subs/timeline])
        current-frame (coll/find-first :current frames) ;; todo: to subs?
        all-frames-duration (when (apply = (map :duration frames))
                              (-> frames first :duration))
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

     [timeline-panel-toolbar {:current-frame current-frame
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
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::events/toggle-all-layers-visibility]))}]
       [icon-button {:src (if some-layer-automatic-linking
                            :link
                            :link-off)
                     :title "toggle all layers automatic linking"
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
                         :size :sm
                         :on-click (fn []
                                     (re-frame/dispatch [::events/toggle-layer-visibility (:idx layer)]))}]
           [icon-button {:src (if (:automatic-linking? layer)
                                :link
                                :link-off)
                         :title "toggle layer's automatic linking"
                         :size :sm
                         :on-click (fn []
                                     (re-frame/dispatch [::events/toggle-layer-automatic-linking (:idx layer)]))}]]
          [layer-view layer]
          (for [cel (cels-by-layers (:idx layer))]
            ^{:key (str (:frame-idx (:pos cel)) "-" (:layer-idx (:pos cel)))}
            [cel-view cel])]))]]))
