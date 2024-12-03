(ns pixel-art.views
  (:require
   ["./colorPicker$default" :as color-picker-js]
   ["./react-dnd-scrolling" :as react-dnd-scrolling]
   ["antd" :as antd]
   ["react-dnd" :as react-dnd]
   ["react-dnd-html5-backend" :as react-dnd-html5-backend]
   [clojure.string :as string]
   [pixel-art.canvas :as canvas]
   [pixel-art.events :as events]
   [pixel-art.export :as export]
   [pixel-art.keyboard-shortcuts :as keyboard-shortcuts]
   [pixel-art.model.color :as color]
   [pixel-art.new-project-modal :as new-project-modal]
   [pixel-art.onion-skin :as onion-skin]
   [pixel-art.palette :as palette :refer [deletable-palette?]]
   [pixel-art.project-save-load :as project-save-load]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.sprite-preview :as sprite-preview]
   [pixel-art.sprite-resizer :as sprite-resizer]
   [pixel-art.subs :as subs]
   [pixel-art.tool.core :as tool]
   [pixel-art.utils.coll :as coll]
   [re-frame.core :as re-frame]
   [react :as react]
   [reagent.core :as r]
   [sc.api])
  (:require-macros [pixel-art.reagent :refer [def-func-component]]))

(set! *warn-on-infer* false)

(def preview-container-bg-color "#A0A0A0")

(def transparent-color-img "url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAABlBMVEVMTExVVVUnhsEkAAAAHUlEQVR4AWOAAUYoQOePEAUj3v9oYDQ9gMBoegAAJFwCAbLaTIMAAAAASUVORK5CYII=')")

(def drawing-border "1px solid black")

(defn use-theme-token []
  (.. antd/theme useToken -token))

(defn typography
  ([text] (typography {} text))
  ([props title] [:> antd/Typography props title]))

(defn space [& children]
  (let [props (first children)
        items (if (map? props)
                (rest children)
                children)]
    (into [:> antd/Space (if (map? props)
                           (merge (dissoc props :block)
                                  (when (:block props)
                                    {:style {:width "100%"}}))
                           {})]
          items)))

(defn title
  ([text] (title text {}))
  ([props title] [:> (. antd/Typography -Title)
                  (assoc-in props [:style :margin-top] 0) ;; todo: fix
                  title]))

(defn form [& items]
  (into [space {:direction "vertical" :style {:width "100%"}}] items))

(defn form-item [{:keys [label control]}]
  [:div {:style {:display "grid"
                 :grid-template-columns "1fr 1fr"
                 :align-items "center"}}
   [typography label]
   control])

(defn preview-image [src style]
  [:img {:src src
         :style (merge style
                       {:position "relative"
                        :image-rendering "pixelated"
                        :background-image transparent-color-img
                        :border drawing-border})}])

(defn previews-container [{:keys [loading]} items]
  [:div {:style {:display :flex
                 :flex-wrap "wrap"
                 :justify-content "center"
                 :justify-items "center"
                 :width "100%"
                 :height "200px"
                 :border "1px solid black"
                 :padding "2px"
                 :overflow "auto"
                 :background-color preview-container-bg-color
                 :opacity (when loading "0.6")}}
   items])

(defn previews-grid-items [previews]
  (if (= (count previews) 1)
    [preview-image (first previews)
     {:height "100%"
      :min-height "70px"}]

    [:<>
     (for [[idx data-url] (map-indexed vector previews)]
       ^{:key idx}
       [:div {:style {:display :flex
                      :flex-direction :column
                      :height "100%"
                      :align-items "center"
                      :min-width 0}}
        [preview-image data-url
         {:height "100%"
          :min-height "70px"}]
        [:div {:style {:padding "5px"}}
         [typography (inc idx)]]])]))

;; todo: comment
(defn popover-children [props]
  (r/as-element (. props (children (. props -onClick)))))

(defn popover [trigger content]
  [:> antd/Popover {"content" (r/as-element content)
                    "trigger" "click"
                    "placement" "bottom"}
   [:> popover-children {:children trigger}]])

(defn custom-popover []
  (let [!opened (r/atom false)]
    (fn [trigger over]
      [:div {:style {:position "relative"}}
       (trigger (fn [] (reset! !opened true)))
       (when @!opened
         [:div
          [:div {:style {:position "fixed"
                         :zIndex 100
                         :top "0px"
                         :right "0px"
                         :bottom "0px"
                         :left "0px"}
                 :on-click (fn []
                             (reset! !opened false))}]
          [:div {:style {:position "absolute" :zIndex 101 :bottom "calc(100% + 5px)"}}
           (over (fn [] (reset! !opened false)))]])])))

(defn parse-int [n]
  (let [res (. js/Number (parseInt n))]
    (if (js/isNaN res) nil res)))

(def !last-mouse-pos (atom nil))

(defn get-mouse-client-pos [e]
  {:x (. e -clientX)
   :y (. e -clientY)})

(defn is-right-button? [e] ;; todo: -which уже не поддерживается
  (or (= (. e -button) 2) (= (. e -which) 3)))

(defn is-middle-button? [e]
  (= (. e -button) 1))

(defn canvas-pos->frame-pos [event scale]
  (let [mouse-pos (get-mouse-client-pos event)
        canvas-layers-rect (.. js/document
                               (getElementById "canvas-layers")
                               (getBoundingClientRect))]
    {:x (. js/Math (floor (/ (- (:x mouse-pos)
                                (. canvas-layers-rect -left)) scale)))
     :y (. js/Math (floor (/ (- (:y mouse-pos)
                                (. canvas-layers-rect -top)) scale)))}))

(defn slider [{:keys [value label block min max step style on-change]}]
  [:div {:style {:display :flex
                 :align-items :center
                 :width (if block "100%" "250px")
                 :gap "8px"
                 :font-size "13px"}}
   [typography (str label " (" value ")")]
   [:> antd/Slider {:value value
                    :min min
                    :max max
                    :step (or step 1)
                    :style (merge {:user-select "none" :flex-grow 1} style)
                    :onChange (fn [value]
                                (on-change value))}]])

(defn checkbox [{:keys [value on-change label]}]
  [:> antd/Checkbox {:checked value
                     :onChange (fn [e]
                                 (on-change (.. e -target -checked)))}
   label])

(defn button [{:keys [on-click]} text]
  [:> antd/Button {:onClick on-click}
   text])

(defn icon-button [{:keys [src icon-theme title active disabled size on-click]}]
  [:button (merge {:className "icon-button"
                   :style (merge
                           {:border "none"
                            :outline "none"
                            :padding 0
                            :background-color (if active "rgba(255,255,255,.2)" "transparent")
                            :border-radius "4px"
                            :opacity (if disabled "0.4" 1)
                            :cursor (if disabled "default" "pointer")}
                           (cond
                             (= size :sm)
                             {:width "28px" :height "28px" :min-height "28px" :min-width "28px"}
                             (= size :xs)
                             {:width "18px" :height "18px" :min-height "18px" :min-width "18px"}
                             :else
                             {:width "100%" :height "100%"}))
                   :title title
                   :disabled disabled
                   :on-click on-click})
   [:div {:style {:width "100%"
                  :height "100%"
                  :mask-image (str "url(./imgs/" (name src) ".svg)")
                  :mask-repeat "no-repeat"
                  :mask-position "center"
                  :mask-size "70%"
                  :background-color (case (or icon-theme :light)
                                      :light "white"
                                      :dark "black")}}]])

(defn get-group-color [group-number]
  (nth (cycle ["green" "pink" "yellow" "red" "blue" "purple"]) group-number))

(def cel-height "50px")

(def current-color "yellow")
(def selected-color "green")

(defn get-border-color [{:keys [current selected]} theme-token]
  (cond
    current current-color
    selected selected-color
    :else (.-colorBorder theme-token)))

(defn droppable-zone [{:keys [accept on-drop can-drop]} styles]
  (let [[{:keys [over can-drop]}, ref] (react-dnd/useDrop
                                        #js
                                         {"accept" accept
                                          "drop" on-drop
                                          "canDrop" can-drop
                                          "collect" (fn [monitor]
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
                                          "collect" (fn [monitor]
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
                    :border-style "solid"
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

(def-func-component cel-view [cel]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "cel"
                                          "item" cel
                                          "collect" (fn [monitor]
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

;; todo: integer input number
(def-func-component input-number [{:keys [value min max block on-blur]}]
  (let [[curr-value set-curr-value] (react/useState value)]
    (react/useEffect (fn []
                       (set-curr-value value))
                     (array value))
    [:> antd/InputNumber {:value curr-value
                          :min (or min 1)
                          :step 1
                          :max max
                          :style {:width (when block "100%")}
                          :onChange (fn [value]
                                      (set-curr-value value))
                          :onBlur (fn []
                                    (let [new-value (parse-int curr-value)]
                                      (set-curr-value new-value)
                                      (on-blur new-value)))}]))

(def-func-component input-text [{:keys [value on-blur]}]
  (let [[curr-value set-curr-value] (react/useState value)]
    (react/useEffect (fn []
                       (set-curr-value value))
                     (array value))
    [:> antd/Input {:value curr-value
                    :onChange (fn [e]
                                (set-curr-value (.. e -target -value)))
                    :onBlur (fn []
                              (on-blur curr-value))
                    :onPressEnter (fn []
                                    (on-blur curr-value))}]))

(defn vertical-resizer []
  (let [container-ref (react/useRef)
        handler-ref (react/useRef)
        initial-info-ref (react/useRef)]
    (react/useEffect (fn []
                       (when (and (. container-ref -current)
                                  (. handler-ref -current))
                         (let [mousemove-handler (fn [e]
                                                   (. e preventDefault)
                                                   (. e stopPropagation)
                                                   (set! (.. js/document -body -style -pointerEvents) "none")
                                                   (set! (.. js/document -body -style -userSelect) "none")
                                                   (let [{:keys [mousedown-pos container-height]} (. initial-info-ref -current)
                                                         height-diff (-> (merge-with -
                                                                                     {:x (. e -clientX) :y (. e -clientY)}
                                                                                     mousedown-pos)
                                                                         :y)]
                                                     (set! (.. container-ref -current -style -height)
                                                           (str (max (- container-height height-diff) 0) "px"))))
                               mousedown-handler (fn [e]
                                                   (set! (. initial-info-ref -current) {:mousedown-pos {:x (. e -clientX) :y (. e -clientY)}
                                                                                        :container-height (.. container-ref -current -offsetHeight)})
                                                   (. js/window (addEventListener "mousemove" mousemove-handler))
                                                   (. js/window (addEventListener "mouseup" (fn mouseup []
                                                                                              (set! (.. js/document -body -style -pointerEvents) "")
                                                                                              (set! (.. js/document -body -style -userSelect) "")
                                                                                              (. js/window (removeEventListener "mousemove" mousemove-handler))
                                                                                              (. js/window (removeEventListener "mouseup" mouseup))))))]
                           (.. handler-ref
                               -current
                               (addEventListener "mousedown" mousedown-handler))
                           (fn []
                             (.. handler-ref
                                 -current
                                 (removeEventListener "mousedown" mousedown-handler))))))
                     (array (. container-ref -current)
                            (. handler-ref -current)))
    {:handler-ref handler-ref
     :container-ref container-ref}))

(def-func-component select [{:keys [value size on-change block options]}]
  (let [ref (react/useRef)]
    [:> antd/Select {:value value
                     :ref ref
                     :options (clj->js options)
                     :size (case size
                             :sm "small"
                             :lg "large"
                             :md "middle"
                             nil)
                     :style {:width (when block "100%")}
                     :on-change (fn [value]
                                 ;; after select option, select has focus and pressing hotkeys doesn't work + any key lead to select opening
                                 ;; todo: find better way?
                                  (.. ref -current blur)
                                  (on-change value))}]))

(def-func-component sprite-preview-modal-component []
  (let [{:keys [size displayed-frame-idx frame-imgs]} @(re-frame/subscribe [::subs/sprite-preview])
        sprite-size @(re-frame/subscribe [::subs/sprite-size])
        frame-img (or (get frame-imgs displayed-frame-idx nil) (get frame-imgs 0))
        image-size (case size
                     :1x sprite-size
                     :2x (update-vals sprite-size #(* % 2))
                     :4x (update-vals sprite-size #(* % 4))
                     :default {:width 512 :height 512})
        _ (react/useEffect (fn []
                             (.. js/document (addEventListener "keydown" (fn [e]
                                                                           (when (= (.. e -code) "Escape")
                                                                             (re-frame/dispatch [::sprite-preview/close]))))))
                           (array))]
    [:div {:style {:position "fixed"
                   :display "flex"
                   :zIndex 1000
                   :alignItems "center"
                   :justifyContent "center"
                   :left 0
                   :right 0
                   :bottom 0
                   :top 0
                   :backgroundColor "rgba(37, 37, 37, 0.9)"}}
     [preview-image frame-img {:height (:height image-size)}]]))

(defn sprite-preview-modal []
  (let [opened (:opened @(re-frame/subscribe [::subs/sprite-preview]))]
    (when opened
      [sprite-preview-modal-component])))

(defn onion-skin-settings []
  (let [onion-skin @(re-frame/subscribe [::subs/onion-skin])]
    [form
     [form-item {:label "Previous Frames"
                 :control [input-number {:min 0
                                         :value (:prev (:frames-count onion-skin))
                                         :block true
                                         :on-blur (fn [value]
                                                    (re-frame/dispatch [::onion-skin/set-frames-count (assoc (:frames-count onion-skin)
                                                                                                             :prev
                                                                                                             value)]))}]}]
     [form-item {:label "Next Frames"
                 :control [input-number {:min 0
                                         :value (:next (:frames-count onion-skin))
                                         :block true
                                         :on-blur (fn [value]
                                                    (re-frame/dispatch [::onion-skin/set-frames-count (assoc (:frames-count onion-skin)
                                                                                                             :next
                                                                                                             value)]))}]}]
     [form-item {:label "Opacity"
                 :control [slider {:min 0 :max 1 :step 0.1
                                   :value (:opacity onion-skin)
                                   :block true
                                   :on-change (fn [v] (re-frame/dispatch [::onion-skin/set-opacity v]))}]}]
     [form-item {:label "Position"
                 :control [select {:value (:position onion-skin)
                                   :options [{:value :front :label "in front of sprite"}
                                             {:value :behind :label "behind sprite"}]
                                   :on-change (fn [v] (re-frame/dispatch [::onion-skin/set-position v]))}]}]]))

(defn timeline-panel-section [title children]
  [:> antd/Card {:size "small"}
   [space
    [typography title]
    (into [:div {:style {:display "flex" :align-items "center"}}]
          children)]])

(defn timeline-panel-toolbar [{:keys [disabled-actions all-frames-duration current-frame]}]
  (let [onion-skin-enabled @(re-frame/subscribe [::subs/onion-skin-enabled])]
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
                      :on-click (fn [] (re-frame/dispatch [::onion-skin/set-enabled (not onion-skin-enabled)]))}]
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
       [button {:on-click (fn [] (re-frame/dispatch [::sprite-preview/open]))} "Show preview"]]
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
        vertical-resizer-refs (vertical-resizer)

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

(defn color-picker [{:keys [value preset-colors actions on-change]}]
  [:> color-picker-js
   {:color (color/int->rgb-str value)
    :disableAlpha true
    :presetColors (clj->js (map #(update % :color color/int->rgb-str) preset-colors))
    :actions (clj->js (map #(reagent.core/as-element %) actions))
    :onChange (fn [e] (let [rgba (. e -rgba)]
                        (on-change (color/int (. rgba -r) (. rgba -g) (. rgba -b) (. rgba -a)))))}])

(defn file-uploader [{:keys [on-upload accept]} render-button]
  (r/with-let [!input-ref (r/atom nil)]
    [:span
     [:input {:type "file"
              :accept accept
              :ref (fn [ref] (reset! !input-ref ref))
              :style {:display "none"}
              :on-change (fn [e]
                           (let [files (.. e -target -files)]
                             (when-first [file files]
                               (let [file-reader (js/FileReader.)]
                                 (set! (. file-reader -onload) (fn [e]
                                                                 (on-upload {:file-name (. file -name)
                                                                             :content (.. e -target -result)})))
                                 (. file-reader (readAsText file)))))
                           (set! (.. e -target -value) "") ;; without this line onChange is not triggered when the same file is choosen twice
                           )}]
     [render-button (fn []
                      (.. @!input-ref click))]]))

;; todo: зачем-это?
(defn- replace-transparent-color [color]
  (if (= color color/transparent-color-int)
    (color/int 0 0 0)
    color))

(defn add-new-color-picker []
  (let [primary-color (replace-transparent-color @(re-frame/subscribe [::subs/primary-color]))
        secondary-color (replace-transparent-color @(re-frame/subscribe [::subs/secondary-color]))
        last-color (replace-transparent-color (last (:colors @(re-frame/subscribe [::subs/current-palette]))))
        !temp-new-value (r/atom primary-color)]
    (fn [{:keys [on-close]}]
      [color-picker {:value @!temp-new-value
                     :on-change (fn [new-value]
                                  (reset! !temp-new-value new-value))
                     :actions [[button
                                {:on-click (fn []
                                             (re-frame/dispatch [::palette/add-color @!temp-new-value])
                                             (on-close))}
                                "Add"]]
                     :preset-colors (coll/distinct-by :color [{:color primary-color}
                                                              {:color secondary-color}
                                                              {:color last-color}])
                     :on-cancel (fn []
                                  (on-close))}])))

(def-func-component palette-colors [{:keys [colors primary-color secondary-color]}]
  (let [theme-token (use-theme-token)]
    [:div {:style {:display :grid
                   :grid-template-columns "repeat(auto-fill, 35px)" ;; todo: dynamic
                   :grid-auto-rows "35px"
                   :grid-gap "2px"
                   :height "100%"
                   :overflow "auto"}}
     (doall
      (for [[idx color] (map-indexed vector colors)]
        (let [color-dark? (.. (color/->tinycolor color) isDark)]
          ^{:key color}
          [:div {:className "color-container"
                 :style {:background-color (color/int->rgb-str color)
                         :position "relative"
                         :cursor "pointer"
                         :color (if color-dark? (.-colorText theme-token) (.-colorBgBase theme-token))}
                 :on-click (fn []
                             (re-frame/dispatch [::palette/select-color idx false]))
                 :on-context-menu (fn [e]
                                    (. e preventDefault)
                                    (re-frame/dispatch [::palette/select-color idx true]))}
           (when (= color primary-color) "L")
           (when (= color secondary-color) "R")
           [:div {:className "remove-color-button"
                  :style {:position "absolute"
                          :right "1px"
                          :top "1px"
                          :opacity 0}}
            [icon-button {:src :close
                          :icon-theme (if color-dark? :light :dark)
                          :title "remove color"
                          :size :xs
                          :on-click (fn [e]
                                      (.. e (stopPropagation))
                                      (re-frame/dispatch [::palette/remove-color idx]))}]]])))]))

(defn palettes-section []
  (let [palettes @(re-frame/subscribe [::subs/palettes])
        current-palette-idx (coll/find-first-idx :current palettes)
        current-palette (coll/find-first :current palettes)
        primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :height "300px"}}
     [select {:value current-palette-idx
              :options (map-indexed (fn [idx p] {:value idx :label (:name p)}) palettes)
              :block true
              :size :sm
              :on-change (fn [idx]
                           (re-frame/dispatch [::palette/select-palette idx]))}]

     [:div {:style {:display "flex" :justify-content "space-between"}}
      [custom-popover
       (fn [close]
         [icon-button {:src :add
                       :title "Add color"
                       :size :sm
                       :on-click close}])
       (fn [close]
         [add-new-color-picker {:on-close close}])]

      [:div
       [icon-button {:src :new-palette
                     :title "Add palette"
                     :size :sm
                     :on-click (fn []
                                 (when-let [name (js/prompt)]
                                   (re-frame/dispatch [::palette/create-palette name])))}]
       [icon-button {:src :remove
                     :title "Remove palette"
                     :size :sm
                     :disabled (not (deletable-palette? palettes))
                     :on-click (fn []
                                 (when (js/confirm "are you sure?")
                                   (re-frame/dispatch [::palette/remove-selected-palette])))}]
       [icon-button {:src :edit
                     :title "Rename palette"
                     :size :sm
                     :disabled (not (deletable-palette? palettes))
                     :on-click (fn []
                                 (when-let [name (js/prompt)]
                                   (re-frame/dispatch [::palette/rename-selected-palette name])))}]
       [icon-button {:src :adjust
                     :title "Add colors from current frame"
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::palette/add-colors-from-frame]))}]]

      [:div
       [icon-button {:src :file-export
                     :title "Export palette"
                     :size :sm
                     :on-click (fn [] (re-frame/dispatch [::palette/export-palette]))}]
       [file-uploader {:on-upload (fn [file-desc]
                                    (re-frame/dispatch [::palette/import-palette file-desc]))}
        (fn [on-click]
          [icon-button {:src :file-import
                        :title "Import palette"
                        :size :sm
                        :on-click on-click}])]]]

     [:div {:style {:flex-grow 1 :min-height 0}}
      [palette-colors {:colors (:colors current-palette)
                       :primary-color primary-color
                       :secondary-color secondary-color}]]]))

(defn drawing-info []
  (let [mouse-pos @(re-frame/subscribe [::subs/mouse-pos])
        scale @(re-frame/subscribe [::subs/scale])
        sprite-size @(re-frame/subscribe [::subs/sprite-size])]
    [space
     [typography (str "[" (:width sprite-size) "x" (:height sprite-size) "]")]
     [typography (str (:x mouse-pos) ":" (:y mouse-pos))]
     [typography (str "scale=" (. scale (toFixed 2)))]]))

(def-func-component canvases-section []
  (let [viewport-ref (react/useRef)
        _ (react/useEffect (fn []
                             (let [handler (fn [e]
                                             (. e preventDefault) ;; preventDefault doesn't work when bind onWheel event on tag
                                             (. e stopPropagation)
                                             (let [viewport-rect (.. viewport-ref -current getBoundingClientRect)
                                                   center-pos {:x (- (.. e -clientX) (.. viewport-rect -left))
                                                               :y (- (.. e -clientY) (.. viewport-rect -top))}
                                                   mouse-offset-pos {:x (+ (- (.. e -clientX) (.. viewport-rect -left))
                                                                           (.. viewport-ref -current -scrollLeft))
                                                                     :y (+ (- (.. e -clientY) (.. viewport-rect -top))
                                                                           (.. viewport-ref -current -scrollTop))}]
                                               (re-frame/dispatch [::events/zoom
                                                                   (if (< (. e -deltaY) 0) 1.1 (/ 1 1.1))
                                                                   center-pos
                                                                   mouse-offset-pos])))]
                               (.. viewport-ref -current (addEventListener "wheel" handler))
                               (fn []
                                 (.. viewport-ref -current (removeEventListener "wheel" handler)))))
                           (array viewport-ref))

        onion-skin @(re-frame/subscribe [::subs/onion-skin])
        panning @(re-frame/subscribe [::subs/panning])
        user-is-drawing @(re-frame/subscribe [::subs/user-is-drawing])
        layers @(re-frame/subscribe [::subs/layers])
        pixels-grid-cel-img @(re-frame/subscribe [::subs/pixels-grid-cel-img])
        scale @(re-frame/subscribe [::subs/scale])]
    [:div {:id "viewport"
           :ref viewport-ref
           :style {:overflow "auto"
                   :position "relative"
                   :background-color preview-container-bg-color
                   :width "100%"
                   :flex-grow 1}
           :on-context-menu (fn [event]
                              (. event preventDefault))
           :on-mouse-down (fn [event]
                            (. event preventDefault)
                            (. event stopPropagation)
                            (if (is-middle-button? event)
                              (do
                                (re-frame/dispatch [::events/start-panning (get-mouse-client-pos event)])
                                (let [mouse-move (fn [event]
                                                   (re-frame/dispatch [::events/pan (get-mouse-client-pos event)]))]
                                  (.. js/document (addEventListener "mousemove" mouse-move))
                                  (.. js/document (addEventListener "mouseup"
                                                                    (fn []
                                                                      (re-frame/dispatch [::events/stop-panning])
                                                                      (.. js/document (removeEventListener "mousemove" mouse-move)))
                                                                    #js {"once" true}))))
                              (let [right-button (is-right-button? event)

                                    mouse-pos (canvas-pos->frame-pos event scale)

                                    mouse-move (fn [event]
                                                 (let [scale @(re-frame/subscribe [::subs/scale])
                                                       mouse-pos (canvas-pos->frame-pos event scale)]
                                                   (when (not= mouse-pos @!last-mouse-pos)
                                                     (reset! !last-mouse-pos mouse-pos)
                                                     (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos right-button]))))

                                    mouse-up (fn mouse-up [event]
                                               (let [scale @(re-frame/subscribe [::subs/scale])
                                                     mouse-pos (canvas-pos->frame-pos event scale)]
                                                 (reset! !last-mouse-pos mouse-pos)
                                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-up mouse-pos right-button])
                                                 (.. js/document (removeEventListener "mousemove" mouse-move))
                                                 (.. js/document (removeEventListener "mouseup" mouse-up))))]
                                (re-frame/dispatch [::events/handle-mouse-event :mouse-down mouse-pos right-button])
                                (.. js/document (addEventListener "mousemove" mouse-move))
                                (.. js/document (addEventListener "mouseup" mouse-up)))))
           :on-mouse-leave (fn [event]
                             (when-not (or user-is-drawing panning)
                               (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (is-right-button? event)]))))
           :on-mouse-move (fn [event]
                            (when-not (or user-is-drawing panning)
                              (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                (when (not= mouse-pos @!last-mouse-pos)
                                  (reset! !last-mouse-pos mouse-pos)
                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (is-right-button? event)])))))}
     [:div {:id "drawing-canvas-container" :style {:position "relative"}}
      [:div {:id "canvas-layers"
             :style {:position "relative"
                     :left "50%"
                     :top "50%"
                     :transform "translate(-50%, -50%)"
                     :background-image transparent-color-img
                     :outline drawing-border ;; todo: why not border
                     }}
       [:canvas {:id "layers-below"
                 :className "layer"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :imageRendering "pixelated"
                         :zIndex 0
                         :width "100%"
                         :height "100%"}}]
       [:canvas {:id "current-layer"
                 :className "layer"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :imageRendering "pixelated"
                         :zIndex 1
                         :width "100%"
                         :height "100%"}}]
       [:canvas {:id "preview"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :imageRendering "pixelated"
                         :zIndex 1
                         :width "100%"
                         :height "100%"}}]
       [:canvas {:id "layers-above"
                 :className "layer"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :imageRendering "pixelated"
                         :zIndex 2
                         :width "100%"
                         :height "100%"}}]
       [:canvas {:id "onion-skin"
                 :style {:position :absolute
                         :left 0
                         :top 0
                         :imageRendering "pixelated"
                         :zIndex (if (= (:position onion-skin) :front)
                                   (count layers) 0);; todo: подумать тут
                         :width "100%"
                         :height "100%"}}]
       (when pixels-grid-cel-img
         [:div {:style {:background-image (str "url(\"" pixels-grid-cel-img "\")")
                        :background-size scale
                        :width "100%"
                        :height "100%"
                        :position "relative"
                        :z-index 10}}])]]]))

(defn- current-color-selection-color-picker [{:keys [value]}]
  (let [initial-value value]
    (fn [{:keys [value on-change]} close]
      [color-picker {:value value
                     :on-change on-change
                     :preset-colors (if (= initial-value color/transparent-color-int)
                                      [{:color initial-value}]
                                      [{:color initial-value}
                                       {:color color/transparent-color-int :title "transparent color"}])
                     :on-cancel close}])))

(defn- current-color-selection [{:keys [value on-change]}]
  [custom-popover
   (fn [close]
     [:div {:style {:width "45px"
                    :height "45px"
                    :border-radius "5px"
                    :cursor "pointer"
                    :background (if (= value color/transparent-color-int)
                                  transparent-color-img
                                  (color/int->rgb-str value))
                    :border "thin solid white"}
            :on-click close}])
   (fn [close]
     [current-color-selection-color-picker {:value value :on-change on-change} close])])

;; export import

(defn export-common-settings-fields [{:keys [common-settings type-options]}]
  (let [layers @(re-frame/subscribe [::subs/layers])
        layer-options (concat
                       [{:label "Visible layers" :value {:type :visible}}
                        {:label "Selected layers" :value {:type :selected}}]
                       (map-indexed (fn [idx l] {:label (:name l) :value {:type :layer :idx idx}}) layers))]
    [[form-item {:label "Frames"
                 :control [select {:value (:frames common-settings)
                                   :options [{:label "All frames" :value :all}
                                             {:label "Selected frames" :value :selected}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :frames value]))}]}]
     [form-item {:label "Layers"
                 :control [select {:value (:layers common-settings)
                                   :options layer-options
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :layers value]))}]}]
     [form-item {:label "Direction"
                 :control [select {:value (:direction common-settings)
                                   :options [{:label "Forward" :value :forward}
                                             {:label "Backwards" :value :backwards}]
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :direction value]))}]}]
     [form-item {:label "Frame scale"
                 :control [slider {:value (:scale common-settings)
                                   :min export/min-scale
                                   :max export/max-scale
                                   :block true
                                   :step 1
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :scale value]))}]}]
     [form-item {:label "Frame size"
                 :control [:<> (str (-> common-settings :scaled-frame-size :width)
                                    "x"
                                    (-> common-settings :scaled-frame-size :height))
                           [:br]]}]
     [form-item {:label "File"
                 :control [input-text {:value (:file-name common-settings)
                                       :on-blur (fn [value]
                                                  (re-frame/dispatch [::export/set-settings-option :file-name value]))}]}]
     [form-item {:label "Type"
                 :control [select {:value (:file-type common-settings)
                                   :options type-options
                                   :on-change (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :file-type value]))}]}]
     [form-item {:label "Split layers"
                 :control [checkbox {:value (:split-layers common-settings)
                                     :on-change (fn [value]
                                                  (re-frame/dispatch [::export/set-settings-option :split-layers value]))}]}]]))

(defn modal [props & children]
  (let [{:keys [on-cancel cancel-text on-ok ok-text ok-disabled hide-footer title additional-buttons]} props]
    (into
     [:> antd/Modal (merge
                     {:title title
                      :open true
                      :closable true
                      :width (case (:size props)
                               :lg "50%"
                               :md "30%"
                               :sm "18%"
                               :else nil)
                      :onOk on-ok
                      :okText ok-text
                      :okButtonProps {:disabled ok-disabled}
                      :onCancel on-cancel
                      :cancelText cancel-text
                      :footer (fn [_ props]
                                (r/as-element
                                 (into [:div {:style {:display :flex :gap "6px"}}]
                                       (concat
                                        [(into [:div {:style {:display "flex" :gap "6px" :margin-right "auto"}}]
                                               additional-buttons)]
                                        [[:> (. props -CancelBtn)]
                                         [:> (. props -OkBtn)]]))))}
                     (when hide-footer {:footer nil}))]
     children)))

(defn export-image-settings-form []
  (let [image-settings @(re-frame/subscribe [::subs/export-image-settings])]
    (into [form]
          (into
           (export-common-settings-fields
            {:common-settings image-settings
             :type-options [{:label "png" :value :png}
                            {:label "gif" :value :gif}]})
           (when (= (:file-type image-settings) :gif)
             [[form-item
               "Never repeat" [checkbox {:value (not (:repeat image-settings))
                                         :on-change (fn [value]
                                                      (re-frame/dispatch [::export/set-settings-option :repeat (not value)]))}]]])))))

(defn export-spritesheet-settings-form []
  (let [settings @(re-frame/subscribe [::subs/export-spritesheet-settings])]
    (into [form]
          (into
           [[form-item {:label "Columns"
                        :control [input-number {:value (:columns settings)
                                                :block true
                                                :on-blur (fn [value]
                                                           (re-frame/dispatch [::export/set-settings-option :columns value]))}]}]
            [form-item {:label "Rows"
                        :control [:div (:rows settings)]}]]
           (export-common-settings-fields
            {:common-settings settings
             :type-options [{:label "png" :value :png}]})))))

(defn radio-group [{:keys [value options on-change]}]
  (into
   [:> (.-Group antd/Radio) {:value (name value)
                             :onChange (fn [e]
                                         (on-change (keyword (.. e -target -value))))}]
   (map (fn [opt] [:> (.-Button antd/Radio) {:value (name (:value opt))} (:label opt)]) options)))

(defn export-modal []
  (let [current-tab @(re-frame/subscribe [::subs/export-current-tab])
        opened @(re-frame/subscribe [::subs/export-modal-opened])
        exporting @(re-frame/subscribe [::subs/exporting])
        export-settings-valid? @(re-frame/subscribe [::subs/export-settings-valid?])
        spritesheet-settings @(re-frame/subscribe [::subs/export-spritesheet-settings])
        preview @(re-frame/subscribe [::subs/export-preview])]
    (when opened
      [modal {:title "Export"
              :size :lg
              :on-cancel (fn []
                           (re-frame/dispatch [::export/set-opened false]))
              :ok-text "Export"
              :ok-disabled (or exporting (not export-settings-valid?))
              :on-ok (fn []
                       (re-frame/dispatch [::export/export]))}

       [space {:direction "vertical" :block true}
        [radio-group {:value current-tab
                      :options [{:value :image :label "Image"}
                                {:value :spritesheet :label "Spritesheet"}]
                      :on-change (fn [tab]
                                   (re-frame/dispatch [::export/select-tab tab]))}]

        [previews-container {:loading (:generation preview)}
         (case current-tab
           :spritesheet [preview-image (:data preview) {:height (* (:rows spritesheet-settings) 100)
                                                        :margin "auto"}]
           :image [previews-grid-items (:data preview)])]

        [:div {:style {:display :grid}}
         (case current-tab
           :image [export-image-settings-form]
           :spritesheet [export-spritesheet-settings-form])]]])))

;; -----------------

(def-func-component anchor [settings]
  (let [theme-token (use-theme-token)]
    [:div {:style {:display :grid
                   :grid-template-columns "min-content min-content min-content"
                   :gap "1px"
                   :opacity (when (:resize-content settings) "0.6")}}
     (for [y [:top :center :bottom]
           x [:left :center :right]]
       ^{:key (str y "-y-" x "-x")}
       [:div {:title (str (name y) "/" (name x))
              :style {:border-radius "4px"
                      :width "24px"
                      :height "24px"
                      :background-color (if (and (not (:resize-content settings))
                                                 (= {:x x :y y} (:anchor settings)))
                                          (.-colorPrimaryActive theme-token)
                                          "#444")}
              :on-click (fn []
                          (re-frame/dispatch [::sprite-resizer/set-settings-option :anchor {:x x :y y}]))}])]))

(defn sprite-resizer-modal []
  (when @(re-frame/subscribe [::subs/sprite-resizer-opened])
    (let [settings @(re-frame/subscribe [::subs/sprite-resizer-settings])
          previews @(re-frame/subscribe [::subs/sprite-resizer-previews])]
      [modal {:title "Resize canvas"
              :size :sm
              :on-cancel (fn []
                           (re-frame/dispatch [::sprite-resizer/set-opened false]))
              :ok-text "Resize"
              :on-ok (fn []
                       (re-frame/dispatch [::sprite-resizer/resize]))}
       [form
        [form-item {:label "Width"
                    :control [input-number {:value (-> settings :target-size :width)
                                            :type "number"
                                            :max project-settings/max-sprite-dim
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::sprite-resizer/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :width value)]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (-> settings :target-size :height)
                                            :type "number"
                                            :max project-settings/max-sprite-dim
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::sprite-resizer/set-settings-option
                                                                           :target-size
                                                                           (assoc (:target-size settings) :height value)]))}]}]
        [form-item {:label "Resize contents"
                    :control [checkbox {:value (:resize-content settings)
                                        :on-change (fn [value]
                                                     (re-frame/dispatch [::sprite-resizer/set-settings-option :resize-content value]))}]}]
        [form-item {:label "Anchor"
                    :control [anchor settings]}]
        [previews-container {}
         [previews-grid-items previews]]]])))

;; -----------------

(defn keyboard-shortcuts-modal []
  (when @(re-frame/subscribe [::subs/keyboard-shortcuts-modal-opened])
    [modal {:title "Keyboard shortcuts"
            :size :lg
            :hide-footer true
            :on-cancel (fn []
                         (re-frame/dispatch [::events/set-keyboard-shortcuts-modal-opened false]))}
     [:div {:style {:display :grid
                    :grid-template-columns "repeat(auto-fit, minmax(200px,1fr))"}}
      (for [[type shortcuts] keyboard-shortcuts/shortcuts-by-types]
        ^{:key (name type)}
        [:div
         [title {:level 4} (str (string/capitalize (name type)) " shortcuts")]
         [:div
          (for [shortcut shortcuts]
            ^{:key (:label shortcut)}
            [:div (string/capitalize (:label shortcut))
             " - "
             (keyboard-shortcuts/keys->string (:keys shortcut))])]])]]))

;; -----------------

(defn new-project-modal []
  (when @(re-frame/subscribe [::subs/new-project-modal-opened])
    (let [size @(re-frame/subscribe [::subs/new-project-modal-size])]
      [modal {:title "New project"
              :size :md
              :on-cancel (fn []
                           (re-frame/dispatch [::new-project-modal/set-opened false]))
              :ok-text "Create"
              :on-ok (fn []
                       (re-frame/dispatch [::new-project-modal/create]))
              :additional-buttons [[file-uploader {:on-upload (fn [file-desc]
                                                                (re-frame/dispatch [::project-save-load/load-from-file file-desc]))}
                                    (fn [on-click]
                                      [button {:on-click on-click}
                                       "Open project"])]
                                   [button {:on-click (fn []
                                                        (re-frame/dispatch [::new-project-modal/create-example-project]))}
                                    "Create example project"]]}
       [form
        [form-item {:label "Width"
                    :control [input-number {:value (:width size)
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::new-project-modal/set-width value]))}]}]
        [form-item {:label "Height"
                    :control [input-number {:value (:height size)
                                            :block true
                                            :on-blur (fn [value]
                                                       (re-frame/dispatch [::new-project-modal/set-height value]))}]}]]])))

;; ----------------

(defn tool-view [{:keys [type selected]}]
  (let [title (string/replace (name type) "-" " ")]
    [:div {:style {:width "50px" :height "50px"}}
     [icon-button {:src type
                   :title title
                   :active selected
                   :size :auto
                   :on-click (fn []
                               (re-frame/dispatch [::events/select-tool type]))}]]))

(defn current-colors-selection []
  (let [primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:width "min-content" :position "relative"}}
     [:div {:style {:width "min-content" :position "relative" :z-index 1}}
      [current-color-selection {:value primary-color
                                :on-change (fn [new-primary-color]
                                             (re-frame/dispatch [::events/set-current-color :primary-color new-primary-color]))}]]
     [:div {:style {:margin-top "-25px" :margin-left "32px"}}
      [current-color-selection {:value secondary-color
                                :on-change (fn [new-secondary-color]
                                             (re-frame/dispatch [::events/set-current-color :secondary-color new-secondary-color]))}]]
     [:div {:style {:position "absolute"
                    :top "48px"
                    :left "3px"
                    :cursor "pointer"}}
      [icon-button {:src :swap
                    :title "Swap colors"
                    :size :sm
                    :on-click (fn [] (re-frame/dispatch [::events/swap-current-colors]))}]]]))

(def-func-component tools-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        theme-token (use-theme-token)]
    [:div {:style {:width "100px"
                   :border-right (str "1px solid " (.-colorBorder theme-token))}}

     [:div {:style {:display "grid"
                    :grid-template-columns "1fr 1fr"
                    :gap "1px"
                    :padding "1px"}}
      (for [type tool/types]
        ^{:key (name type)}
        [tool-view {:type type :selected (= (:type tool) type)}])]

     [:div {:style {:display "flex"
                    :justify-content "center"
                    :margin-top "15px"}}
      [current-colors-selection]]]))

;;----

(defn tool-options-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        options @(re-frame/subscribe [::subs/tool-options]) ;; todo: объединить спеку и опции
        options-spec (tool/options-specs (:type tool))]
    [:div {:style {:display :flex
                   :align-items :center
                   :height "30px"
                   :flex-shrink 0
                   :padding "0 10px"
                   :gap "12px"}}
     (doall
      (for [[idx option-spec] (map-indexed vector options-spec)]
        (let [value (get options (:field option-spec))
              on-change #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
              props (assoc option-spec
                           :value value
                           :on-change on-change)]
          ^{:key idx}
          [:div
           (case (:type option-spec)
             :slider [:div {:style {:width "300px"}}
                      (slider props)]
             :checkbox (checkbox props))])))]))

(def-func-component right-sidebar []
  (let [theme-token (use-theme-token)]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :height "100%"
                   :padding "1px"
                   :border-left (str "1px solid " (.-colorBorder theme-token))}}
     [:div {:style {:margin-top "auto"}}
      [palettes-section]]]))

(def-func-component header []
  (let [pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])
        theme-token (use-theme-token)]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :padding "5px"
                   :gap "4px"
                   :border-bottom (str "1px solid " (.-colorBorder theme-token))}}
     [:<>
      [new-project-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::new-project-modal/set-opened true]))}
       "New project"]]
     [button {:on-click (fn [] (re-frame/dispatch [::project-save-load/save-as-file]))}
      "Save project as file"]
     [file-uploader {:on-upload (fn [file-desc]
                                  (re-frame/dispatch [::project-save-load/load-from-file file-desc]))}
      (fn [on-click]
        [button {:on-click on-click}
         "Load project from file"])]
     [:<>
      [button {:on-click (fn [] (re-frame/dispatch [::export/set-opened true]))}
       "Open project export panel"]
      [export-modal]]
     [:<>
      [sprite-resizer-modal]
      [button {:on-click (fn [] (re-frame/dispatch [::sprite-resizer/set-opened true]))} "Resize canvas"]]
     [:<>
      [button {:on-click (fn [] (re-frame/dispatch [::events/set-keyboard-shortcuts-modal-opened true]))} "Keyboard shortcuts"]
      [keyboard-shortcuts-modal]]
     [checkbox {:value pixels-grid-enabled
                :label "Grid"
                :on-change (fn [checked] (re-frame/dispatch [::events/enable-pixels-grid checked]))}]

     [:div {:style {:margin-left "auto"}}
      [drawing-info]]]))

(def-func-component main-panel []
  (let [colorBgContainer (.. antd/theme useToken -token -colorBgContainer)]
    [:> react-dnd/DndProvider {"backend" react-dnd-html5-backend/HTML5Backend}
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :height "100%"
                    :width "100%"
                    :max-height "100%"
                    :max-width "100%"
                    :background-color colorBgContainer}}
      [header]
      [:div {:style {:display :grid
                     :grid-template-columns "100px 1fr 250px"
                     :flex-grow 1
                     :min-height 0
                     :width "100%"}}
       [tools-panel]
       [:div {:style {:display :flex
                      :flex-direction :column
                      :min-width 0
                      :min-height 0}}
        [tool-options-panel]
        [canvases-section]
        [timeline-panel]]
       [right-sidebar]]]]))

(def-func-component app-loading-view []
  (let [colorBgContainer (.. antd/theme useToken -token -colorBgContainer)]
    [:div {:style {:display "flex"
                   :align-items "center"
                   :justify-content "center"
                   :width "100%"
                   :height "100%"
                   :background-color colorBgContainer}}
     [:> antd/Spin {:size "large"}]]))

(defn app []
  [:> antd/ConfigProvider {"theme" {"token" {"motion" false}
                                    "algorithm" (. antd/theme -darkAlgorithm)}}
   (if @(re-frame/subscribe [::subs/initial-loading])
     [app-loading-view]
     [main-panel])])
