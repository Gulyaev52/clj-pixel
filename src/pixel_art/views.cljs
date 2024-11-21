(ns pixel-art.views
  (:require
   ["./colorPicker$default" :as color-picker-js]
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
   [sc.api]
   [stylefy.core :as stylefy :refer [use-style]]
   [clojure.string :as str]))

(set! *warn-on-infer* false)

(def drawing-container-color "#A0A0A0")

(def transparent-color-img "url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAABlBMVEVMTExVVVUnhsEkAAAAHUlEQVR4AWOAAUYoQOePEAUj3v9oYDQ9gMBoegAAJFwCAbLaTIMAAAAASUVORK5CYII=')")

(def drawing-border "1px solid black")

(defn form [items]
  (into [:div (use-style {:display "flex" :flex-direction "column" :gap "4px"})] items))

(defn form-item [{:keys [label control]}]
  [:div (use-style {:display "grid"
                    :grid-template-columns "1fr 1fr"
                    :align-items "center"})
   [:span label]
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
                 :background-color drawing-container-color
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
        [:div {:style {:padding "5px" :color "black"}}
         (inc idx)]])]))

(defn section [title children]
  [:div (use-style {:display "flex"
                    :align-items "center"
                    :gap "4px"
                    :background-color "#171717"
                    :padding "1px 8px"
                    :font-size "14px"
                    :color "white"
                    :border-radius "5px"})
   [:div (str title ":")]
   (into [:div (use-style {:display "flex" :align-items "center"})]
         children)])

(defn popper []
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
                 :onClick (fn []
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

(defn slider [{:keys [value label min max step style onChange]}]
  ;; todo: labels
  [:div (use-style {:display :flex
                    :align-items :center
                    :color "white"
                    :font-size "13px"})
   [:span (str label " (" value ")")]
   [:input {:type "range"
            :value value
            :min min
            :max max
            :step step
            :style (merge {:user-select "none"} style)
            :onChange (fn [e]
                        (let [value (parse-double (.. e -target -value))]
                          (onChange value)))}]])

(defn checkbox [{:keys [value onChange label]}]
  [:div (use-style {:display :flex
                    :align-items :center
                    :color "white"
                    :font-size "13px"})
   [:input {:type "checkbox"
            :checked value
            :onChange (fn [e] (onChange (.. e -target -checked)))}]
   [:span label]])

(defn icon-button [{:keys [src title active disabled size on-click]}]
  [:button (use-style (merge
                       {:border "none"
                        :outline "none"
                        :background-color (if active "rgba(255,255,255,.2)" "transparent")
                        :background-image (str "url(" src ")")
                        :background-repeat "no-repeat"
                        :background-position "50%"
                        :background-size "70%"
                        :border-radius "4px"
                        :opacity (if disabled "0.4" 1)
                        :cursor "pointer"
                        ::stylefy/mode {:hover {:background-color "rgba(255,255,255,.2)"}}}
                       (cond
                         (= size :sm)
                         {:width "28px" :height "28px" :min-height "28px" :min-width "28px"}
                         (= size :xs)
                         {:width "18px" :height "18px" :min-height "18px" :min-width "18px"}
                         :else
                         {:width "100%" :height "100%"}))
                      {:title title
                       :disabled disabled
                       :on-click on-click})])

(defn get-group-color [group-number]
  (nth (cycle ["green" "pink" "yellow" "red" "blue" "purple"]) group-number))

(def cel-height "50px")

(def current-color "yellow")
(def selected-color "green")

(defn get-border-color [{:keys [current selected]}]
  (cond
    current current-color
    selected selected-color
    :else "black"))

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

(defn droppable-layer-zone [to-idx styles]
  (droppable-zone {:accept "layer"
                   :on-drop (fn [layer]
                              (re-frame/dispatch [::events/move-layer (:idx layer) to-idx]))}
                  (merge {:height "20px" :width "100%"} styles)))

(defn layer-view [layer]
  (let [[_ ref] (react-dnd/useDrag (fn [] #js {"type" "layer" "item" layer}))]
    [:div (use-style {:display :flex
                      :align-items "center"
                      :position "relative"})
     (when (= (:idx layer) 0)
       [:f> droppable-layer-zone (:idx layer) {:top 0 :transform "translateY(-50%)"}])
     [:div {:ref ref
            :onClick (fn [] (re-frame/dispatch [::events/select-layer (:idx layer)]))
            :style {:display :flex
                    :align-items "center"
                    :padding "4px"
                    :width "150px"
                    :height cel-height
                    :border-style "solid"
                    :border-color (get-border-color layer)
                    :border-width (if (:current layer)
                                    "2px"
                                    "1px")
                    :cursor "pointer"
                    :color "white"
                    :background-color "#3B3B3B"}}
      (:name layer)]
     [:f> droppable-layer-zone (inc (:idx layer)) {:bottom 0 :transform "translateY(50%)"}]]))

(defn droppable-frame-zone [idx styles]
  (droppable-zone {:accept "frame"
                   :on-drop (fn [frame]
                              (re-frame/dispatch [::events/move-frame (:idx frame) idx]))}
                  (merge {:width "30px" :height "100%"} styles)))

(defn frame-view [frame]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "frame"
                                          "item" frame
                                          "collect" (fn [monitor]
                                                      {:dragging (.. monitor isDragging)})}))]
    [:div {:style {:position "sticky"
                   :top 0
                   :z-index 1
                   :background-color "#333"}}
     (when (= (:idx frame) 0)
       [:f> droppable-frame-zone (:idx frame) {:left 0
                                               :top 0
                                               :transform "translateX(-50%)"}])
     [:div {:onClick (fn [] (re-frame/dispatch [::events/select-frame (:idx frame)]))
            :ref ref
            :style {:display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :height "100%"
                    :border-style "solid"
                    :border-color (get-border-color frame)
                    :border-width (if (:current frame)
                                    "2px"
                                    "1px")
                    :text-align "center"
                    :cursor "pointer"
                    :color "white"}} (inc (:idx frame))]
     [:f> droppable-frame-zone (inc (:idx frame)) {:right 0
                                                   :top 0
                                                   :transform "translateX(50%)"}]]))

(defn droppable-cel-zone [pos direction-type styles]
  (droppable-zone {:accept "cel"
                   :can-drop (fn [cel]
                               (case direction-type
                                 :frame true
                                 :layer (not= (:layer-idx pos) (-> cel :pos :layer-idx))))
                   :on-drop (fn [cel]
                              (re-frame/dispatch [::events/move-cel (:pos cel) pos]))}
                  styles))

(defn cel-view [cel]
  (let [[_ ref] (react-dnd/useDrag (fn []
                                     #js {"type" "cel"
                                          "item" cel
                                          "collect" (fn [monitor]
                                                      {:dragging (.. monitor isDragging)})}))
        cel-preview (react/useMemo (fn []
                                     (canvas/generate-data-url #(canvas/draw-cel cel %)
                                                               (:size cel)))
                                   (array cel))]
    [:div {:style {:position "relative"}}
     (when (= (-> cel :pos :frame-idx) 0)
       [:f> droppable-cel-zone
        (:pos cel)
        :frame
        {:height "100%"
         :width "20px"
         :top 0
         :left 0
         :transform "translateX(-50%)"}])
     [:div {:onClick (fn [e]
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
                    :border-color (get-border-color cel)
                    :border-width (if (:selected cel)
                                    "2px"
                                    "1px")
                    :background-color drawing-container-color
                    :cursor "pointer"
                    :font-weight "bold"
                    :font-size 18
                    :color (when-let [group-number (:group-number cel)]
                             (get-group-color group-number))}}
      [preview-image cel-preview (merge {:max-width "100%"
                                         :max-height "100%"}
                                        (if (> (:width (:size cel))
                                               (:height (:size cel)))
                                          {:width "100%"}
                                          {:height "100%"}))]
      [:div {:style {:position "absolute" :top 0}}
       (some-> (:group-number cel) inc)]]
     [:f> droppable-cel-zone
      (:pos cel)
      :layer
      {:height "100%"
       :width "100%"
       :top 0
       :left 0}]
     [:f> droppable-cel-zone
      (update (:pos cel) :frame-idx inc)
      :frame
      {:height "100%"
       :width "20px"
       :top 0
       :right 0
       :transform "translateX(50%)"}]]))

(defn input-number-component [{:keys [value max on-blur]}]
  (let [[curr-value set-curr-value] (react/useState value)]
    (react/useEffect (fn [] (set-curr-value (str value))) #js [value])
    [:input {:value curr-value
             :type "number"
             :min 1
             :step 1
             :max max
             :onChange (fn [e]
                         (set-curr-value (.. e -target -value)))
             :onBlur (fn []
                       (let [width (parse-int curr-value)]
                         (set-curr-value (str width))
                         (on-blur width)))}]))

(defn input-number [props] [:f> input-number-component props])

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

(defn select [{:keys [value onChange block options]}]
  (let [selected-option-idx (ffirst (filter #(= (:value (second %)) value) (map-indexed vector options)))]
    [:select (use-style {:width (if block "100%" "auto")}
                        {:value (or selected-option-idx "")
                         :on-change (fn [event]
                                      (let [selected-option (nth options (parse-double (.. event -target -value)) nil)]
                                        (when selected-option
                                          (onChange (:value selected-option)))))})
     (for [[idx opt] (map-indexed vector options)]
       ^{:key idx}
       [:option {:value idx} (:label opt)])]))

(defn sprite-preview-modal-component []
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
      [:f> sprite-preview-modal-component])))

(defn onion-skin-settings []
  (let [onion-skin @(re-frame/subscribe [::subs/onion-skin])]
    [:div (use-style {:width "300px"
                      :padding "8px"
                      :background-color "#333333"
                      :border-radius "2px"
                      :border "2px solid #C0C0C0"
                      :color "white"})
     [form
      [[form-item {:label "Previous Frames"
                   :control [:input {:style {:width "100%"}
                                     :type "number"
                                     :min 0
                                     :value (:prev (:frames-count onion-skin))
                                     :onChange (fn [e]
                                                 (re-frame/dispatch [::onion-skin/set-frames-count {:prev (parse-double (.. e -target -value))
                                                                                                    :next (:next (:frames-count onion-skin))}]))}]}]
       [form-item {:label "Next Frames"
                   :control [:input {:style {:width "100%"}
                                     :type "number"
                                     :min 0
                                     :value (:next (:frames-count onion-skin))
                                     :onChange (fn [e]
                                                 (re-frame/dispatch [::onion-skin/set-frames-count {:prev (:prev (:frames-count onion-skin))
                                                                                                    :next (parse-double (.. e -target -value))}]))}]}]
       [form-item {:label "Opacity"
                   :control [slider {:min 0 :max 1 :step 0.1
                                     :value (:opacity onion-skin)
                                     :style {:width "100%"}
                                     :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-opacity v]))}]}]
       [form-item {:label "Position"
                   :control [select {:value (:position onion-skin)
                                     :options [{:value :front :label "in front of sprite"}
                                               {:value :behind :label "behind sprite"}]
                                     :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-position v]))}]}]]]]))

(defn timeline-panel-toolbar [{:keys [disabled-actions all-frames-duration current-frame]}]
  (let [onion-skin-enabled @(re-frame/subscribe [::subs/onion-skin-enabled])]
    [:div (use-style {:display "flex" :justify-content "space-between"})
     [:div (use-style {:display "flex" :gap "20px"})

      [section "Frames" [[icon-button {:src "./imgs/add.svg"
                                       :title "add empty frame"
                                       :disabled (:add-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/add-frame]))}]
                         [icon-button {:src "./imgs/remove.svg"
                                       :title "remove frame"
                                       :disabled (:remove-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/remove-frame]))}]
                         [icon-button {:src "./imgs/duplicate.svg"
                                       :title "duplicate frame"
                                       :disabled (:duplicate-frame disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/duplicate-frame]))}]
                         [icon-button {:src "./imgs/arrow-left.svg"
                                       :title "move frame left"
                                       :disabled (:move-frame-left disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-frame-left]))}]
                         [icon-button {:src "./imgs/arrow-right.svg"
                                       :title "move frame right"
                                       :disabled (:move-frame-right disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-frame-right]))}]]]

      [section "Layers" [[icon-button {:src "./imgs/add.svg"
                                       :title "add layer"
                                       :disabled (:add-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/add-layer]))}]
                         [icon-button {:src "./imgs/remove.svg"
                                       :title "remove layer"
                                       :disabled (:remove-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/remove-layer]))}]
                         [icon-button {:src "./imgs/duplicate.svg"
                                       :title "duplicate layer"
                                       :disabled (:duplicate-layer disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/duplicate-layer]))}]
                         [icon-button {:src "./imgs/merge-down.svg"
                                       :title "merge layer with below"
                                       :disabled (:merge-layer-with-below disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/merge-layer-with-below]))}]
                         [icon-button {:src "./imgs/arrow-up.svg"
                                       :title "move layer up"
                                       :disabled (:move-layer-up disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-layer-up]))}]
                         [icon-button {:src "./imgs/arrow-down.svg"
                                       :title "move layer down"
                                       :disabled (:move-layer-down disabled-actions)
                                       :size :sm
                                       :on-click (fn [] (re-frame/dispatch [::events/move-layer-down]))}]
                         [icon-button {:src "./imgs/edit.svg"
                                       :title "rename layer"
                                       :size :sm
                                       :on-click (fn [e]
                                                   (. e stopPropagation)
                                                   (let [new-name (js/prompt)]
                                                     (when (seq (string/trim new-name))
                                                       (re-frame/dispatch [::events/rename-layer new-name]))))}]]]

      [section "Cels" [[icon-button {:src "./imgs/link.svg"
                                     :title "link cels"
                                     :disabled (:link-cels disabled-actions)
                                     :size :sm
                                     :on-click (fn [] (re-frame/dispatch [::events/link-selected-cels]))}]
                       [icon-button {:src "./imgs/link-off.svg"
                                     :title "unlink cels"
                                     :disabled (:unlink-cel disabled-actions)
                                     :size :sm
                                     :on-click (fn [] (re-frame/dispatch [::events/unlink-selected-cels]))}]]]

      [section "Onion skin"
       [[icon-button {:src (if onion-skin-enabled "./imgs/layers-off.svg" "./imgs/layers.svg")
                      :title (if onion-skin-enabled "disable onion skin" "enable onion skin")
                      :size :sm
                      :on-click (fn [] (re-frame/dispatch [::onion-skin/set-enabled (not onion-skin-enabled)]))}]
        [popper
         (fn [open]
           [icon-button {:src "./imgs/cog.svg"
                         :title "onion skin settings"
                         :size :sm
                         :on-click open}])
         (fn []
           [onion-skin-settings])]]]]

     [:div (use-style {:display :flex :color :white})
      [:<>
       [sprite-preview-modal]
       [:button {:onClick (fn [] (re-frame/dispatch [::sprite-preview/open]))} "show preview"]]
      [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
       [:span "Duration (ms)"]
       [input-number {:value (:duration current-frame)
                      :on-blur (fn [duration]
                                 (re-frame/dispatch [::events/set-frame-duration (:idx current-frame) duration]))}]]

      [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
       [:span "All frames duration (ms)"]
       [input-number {:value all-frames-duration
                      :on-blur (fn [duration]
                                 (re-frame/dispatch [::events/set-frame-duration-for-all duration]))}]]]]))

(defn timeline-panel []
  (let [{:keys [cels layers frames disabled-actions some-layer-visible some-layer-automatic-linking]} @(re-frame/subscribe [::subs/timeline])
        current-frame (coll/find-first :current frames) ;; todo: to subs?
        all-frames-duration (when (apply = (map :duration frames))
                              (-> frames first :duration))
        cels-by-layers (-> cels
                           (#(group-by (fn [c] (-> c :pos :layer-idx)) %))
                           (update-vals (fn [cels] (sort-by #(-> % :pos :frame-idx) cels))))
        vertical-resizer-refs (vertical-resizer)]
    [:div (use-style {:display "flex"
                      :flex-direction "column"
                      :padding "4px"
                      :gap "4px"
                      :flex-shrink 0
                      :height "300px"
                      :min-height "16px"
                      :max-height "calc(100% - 30px)"
                      :border "2px solid #171717"
                      :background-color "#333"}
                     {:ref (:container-ref vertical-resizer-refs)})

     [:div (use-style {:min-height "4px"
                       :width "40px"
                       :background-color "gray"
                       :cursor "grab"
                       :align-self "center"}
                      {:ref (:handler-ref vertical-resizer-refs)})]

     [timeline-panel-toolbar {:current-frame current-frame
                              :disabled-actions disabled-actions
                              :all-frames-duration all-frames-duration}]

     [:> react-dnd/DndProvider {"backend" react-dnd-html5-backend/HTML5Backend}
      [:<>
       [:div {:style {:display :grid
                      :grid-template-rows "min-content"
                      :grid-auto-rows cel-height
                      :grid-template-columns (str "min-content min-content " (->> (repeat (count frames) "100px") (string/join " ")))
                      :grid-column-gap "4px"
                      :grid-row-gap "4px"
                      :margin-top "4px"
                      :overflow "auto"}}
        [:div (use-style {:display "flex"
                          :align-items "center"
                          :position "sticky"
                          :z-index 1
                          :top 0
                          :background-color "#333"})
         [icon-button {:src (if some-layer-visible
                              "./imgs/visibility.svg"
                              "./imgs/visibility-off.svg")
                       :title "toggle all layers visibility"
                       :size :sm
                       :on-click (fn []
                                   (re-frame/dispatch [::events/toggle-all-layers-visibility]))}]
         [icon-button {:src (if some-layer-automatic-linking
                              "./imgs/link.svg"
                              "./imgs/link-off.svg")
                       :title "toggle all layers automatic linking"
                       :size :sm
                       :on-click (fn []
                                   (re-frame/dispatch [::events/toggle-all-layers-automatic-linking]))}]]
        [:div (use-style {:position "sticky"
                          :z-index 1
                          :top 0
                          :background-color "#333"})]
        (for [frame frames] ^{:key (:idx frame)}
             [:f> frame-view frame])
        (for [layer layers]
          ^{:key (:idx layer)}
          [:<>
           [:div (use-style {:display "flex"
                             :align-items "center"})
            [icon-button {:src (if (:visible? layer)
                                 "./imgs/visibility.svg"
                                 "./imgs/visibility-off.svg")
                          :title "toggle layer's visibility"
                          :size :sm
                          :on-click (fn []
                                      (re-frame/dispatch [::events/toggle-layer-visibility (:idx layer)]))}]
            [icon-button {:src (if (:automatic-linking? layer)
                                 "./imgs/link.svg"
                                 "./imgs/link-off.svg")
                          :title "toggle layer's automatic linking"
                          :size :sm
                          :on-click (fn []
                                      (re-frame/dispatch [::events/toggle-layer-automatic-linking (:idx layer)]))}]]
           [:f> layer-view layer]
           (for [cel (cels-by-layers (:idx layer))]
             ^{:key (str (:frame-idx (:pos cel)) "-" (:layer-idx (:pos cel)))}
             [:f> cel-view cel])])]]]]))

(defn color-picker [{:keys [value presetColors actions onChange]}]
  [:> color-picker-js
   {:color (color/int->rgb-str value)
    :disableAlpha true
    :presetColors (clj->js (map #(update % :color color/int->rgb-str) presetColors))
    :actions (clj->js (map #(reagent.core/as-element %) actions))
    :onChange (fn [e] (let [rgba (. e -rgba)]
                        (onChange (color/int (. rgba -r) (. rgba -g) (. rgba -b) (. rgba -a)))))}])

(defn file-uploader-comp [{:keys [onUpload accept]} render-button]
  (let [input-ref (react/useRef)]
    [:span
     [:input {:type "file"
              :accept accept
              :ref input-ref
              :style {:display "none"}
              :onChange (fn [e]
                          (let [files (.. e -target -files)]
                            (when-first [file files]
                              (let [file-reader (js/FileReader.)]
                                (set! (. file-reader -onload) (fn [e]
                                                                (onUpload {:file-name (. file -name)
                                                                           :content (.. e -target -result)})))
                                (. file-reader (readAsText file)))))
                          (set! (.. e -target -value) "") ;; without this line onChange is not triggered when the same file is choosen twice
                          )}]
     [render-button (fn []
                      (.. input-ref -current click))]]))

(defn file-uploader [props render-button]
  [:f> file-uploader-comp props render-button])

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
    (fn [{:keys [onClose]}]
      [color-picker {:value @!temp-new-value
                     :onChange (fn [new-value]
                                 (reset! !temp-new-value new-value))
                     :actions [[:button
                                {:onClick (fn []
                                            (re-frame/dispatch [::palette/add-color @!temp-new-value])
                                            (onClose))}
                                "add"]]
                     :presetColors (coll/distinct-by :color [{:color primary-color}
                                                             {:color secondary-color}
                                                             {:color last-color}])
                     :onCancel (fn []
                                 (onClose))}])))

(defn palette-colors [{:keys [colors primary-color secondary-color]}]
  [:div {:style {:display :grid
                 :grid-template-columns "repeat(auto-fill, 35px)" ;; todo: dynamic
                 :grid-auto-rows "35px"
                 :grid-gap "2px"
                 :height "100%"
                 :overflow "auto"}}
   (println colors)
   (doall
    (for [[idx color] (map-indexed vector colors)]
      (let [dark? (.. (color/->tinycolor color) isDark)]
        ^{:key color}
        [:div (use-style {:background-color (color/int->rgb-str color)
                          :position "relative"
                          :cursor "pointer"
                          :color (if dark? "white" "black")
                          ::stylefy/manual [[:&:hover [:.remove-color {:opacity 1}]]]}
                         {:on-click (fn []
                                      (re-frame/dispatch [::palette/select-color idx false]))
                          :on-context-menu (fn [e]
                                             (. e preventDefault)
                                             (re-frame/dispatch [::palette/select-color idx true]))})
         (when (= color primary-color) "L")
         (when (= color secondary-color) "R")
         [:div (use-style {:position "absolute"
                           :right "1px"
                           :top "1px"
                           :opacity 0}
                          {:class "remove-color"})
          [icon-button {:src (if dark? "./imgs/close.svg" "./imgs/close-black.svg") ;; todo: fix
                        :title "remove color"
                        :size :xs
                        :on-click (fn [e]
                                    (.. e (stopPropagation))
                                    (re-frame/dispatch [::palette/remove-color idx]))}]]])))])

(defn palettes-section []
  (let [palettes @(re-frame/subscribe [::subs/palettes])
        current-palette-idx (coll/find-first-idx :current palettes)
        current-palette (coll/find-first :current palettes)
        primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div (use-style {:display "flex"
                      :flex-direction "column"
                      :height "300px"})
     [select {:value current-palette-idx
              :options (map-indexed (fn [idx p] {:value idx :label (:name p)}) palettes)
              :block true
              :onChange (fn [idx]
                          (re-frame/dispatch [::palette/select-palette idx]))}]

     [:div (use-style {:display "flex" :justify-content "space-between"})
      [popper
       (fn [close]
         [icon-button {:src "./imgs/add.svg"
                       :title "Add color"
                       :size :sm
                       :on-click close}])
       (fn [close]
         [add-new-color-picker {:onClose close}])]

      [:div
       [icon-button {:src "./imgs/new-palette.svg"
                     :title "Add palette"
                     :size :sm
                     :on-click (fn []
                                 (when-let [name (js/prompt)]
                                   (re-frame/dispatch [::palette/create-palette name])))}]
       [icon-button {:src "./imgs/remove.svg"
                     :title "Remove palette"
                     :size :sm
                     :disabled (not (deletable-palette? palettes))
                     :on-click (fn []
                                 (when (js/confirm "are you sure?")
                                   (re-frame/dispatch [::palette/remove-selected-palette])))}]
       [icon-button {:src "./imgs/edit.svg"
                     :title "Rename palette"
                     :size :sm
                     :disabled (not (deletable-palette? palettes))
                     :on-click (fn []
                                 (when-let [name (js/prompt)]
                                   (re-frame/dispatch [::palette/rename-selected-palette name])))}]
       [icon-button {:src "./imgs/adjust.svg"
                     :title "Add colors from current frame"
                     :size :sm
                     :on-click (fn []
                                 (re-frame/dispatch [::palette/add-colors-from-frame]))}]]

      [:div
       [icon-button {:src "./imgs/file-export.svg"
                     :title "Export palette"
                     :size :sm
                     :on-click (fn [] (re-frame/dispatch [::palette/export-palette]))}]
       [file-uploader {:onUpload (fn [file-desc]
                                   (re-frame/dispatch [::palette/import-palette file-desc]))}
        (fn [on-click]
          [icon-button {:src "./imgs/file-import.svg"
                        :title "Import palette"
                        :size :sm
                        :on-click on-click}])]]]

     [:div (use-style {:flex-grow 1 :min-height 0})
      [palette-colors {:colors (:colors current-palette)
                       :primary-color primary-color
                       :secondary-color secondary-color}]]]))

(defn drawing-info []
  (let [mouse-pos @(re-frame/subscribe [::subs/mouse-pos])
        scale @(re-frame/subscribe [::subs/scale])
        sprite-size @(re-frame/subscribe [::subs/sprite-size])]
    [:div {:style {:display :flex :gap "10px" :color "white"}}
     [:div (str "[" (:width sprite-size) "x" (:height sprite-size) "]")]
     [:div (str (:x mouse-pos) ":" (:y mouse-pos))]
     [:div (str "scale=" (. scale (toFixed 2)))]]))

(defn canvases-section-component []
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
                   :background-color drawing-container-color
                   :width "100%"
                   :flex-grow 1}
           :onContextMenu (fn [event]
                            (. event preventDefault))
           :onMouseDown (fn [event]
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
           :onMouseLeave (fn [event]
                           (when-not (or user-is-drawing panning)
                             (let [mouse-pos (canvas-pos->frame-pos event scale)]
                               (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (is-right-button? event)]))))
           :onMouseMove (fn [event]
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

;; todo: rename
(defn canvases-section []
  [:f> canvases-section-component])

(defn- current-color-selection-color-picker [{:keys [value]}]
  (let [initial-value value]
    (fn [{:keys [value onChange]} close]
      [color-picker {:value value
                     :onChange onChange
                     :presetColors (if (= initial-value color/transparent-color-int)
                                     [{:color initial-value}]
                                     [{:color initial-value}
                                      {:color color/transparent-color-int :title "transparent color"}])
                     :onCancel close}])))

(defn- current-color-selection [{:keys [value onChange]}]
  [popper
   (fn [close]
     [:div {:style {:width "45px"
                    :height "45px"
                    :border-radius "5px"
                    :background (if (= value color/transparent-color-int)
                                  transparent-color-img
                                  (color/int->rgb-str value))
                    :border "thin solid white"}
            :onClick close}])
   (fn [close]
     [current-color-selection-color-picker {:value value :onChange onChange} close])])

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
                                   :onChange (fn [value]
                                               (re-frame/dispatch [::export/set-settings-option :frames value]))}]}]
     [form-item {:label "Layers"
                 :control [select {:value (:layers common-settings)
                                   :options layer-options
                                   :onChange (fn [value]
                                               (re-frame/dispatch [::export/set-settings-option :layers value]))}]}]
     [form-item {:label "Direction"
                 :control [select {:value (:direction common-settings)
                                   :options [{:label "Forward" :value :forward}
                                             {:label "Backwards" :value :backwards}]
                                   :onChange (fn [value]
                                               (re-frame/dispatch [::export/set-settings-option :direction value]))}]}]
     [form-item {:label "Frame scale"
                 :control [slider {:value (:scale common-settings)
                                   :min export/min-scale
                                   :max export/max-scale
                                   :step 1
                                   :onChange (fn [value]
                                               (re-frame/dispatch [::export/set-settings-option :scale value]))}]}]
     [form-item {:label "Frame size"
                 :control [:<> (str (-> common-settings :scaled-frame-size :width)
                                    "x"
                                    (-> common-settings :scaled-frame-size :height))
                           [:br]]}]
     [form-item {:label "File"
                 :control [:input {:value (:file-name common-settings)
                                   :onChange (fn [e]
                                               (re-frame/dispatch [::export/set-settings-option :file-name (.. e -target -value)]))}]}]
     [form-item {:label "Type"
                 :control [select {:value (:file-type common-settings)
                                   :options type-options
                                   :onChange (fn [value]
                                               (re-frame/dispatch [::export/set-settings-option :file-type value]))}]}]
     [form-item {:label "Split layers"
                 :control [checkbox {:value (:split-layers common-settings)
                                     :onChange (fn [value]
                                                 (re-frame/dispatch [::export/set-settings-option :split-layers value]))}]}]]))

(defn modal [props & children]
  (let [{:keys [cancel-button ok-button]} props]
    [:div {:style {:position "fixed"
                   :display "flex"
                   :zIndex 1000
                   :alignItems "center"
                   :justifyContent "center"
                   :left 0
                   :right 0
                   :bottom 0
                   :top 0
                   :backgroundColor "rgba(37, 37, 37, .9)"}}
     [:div (use-style (merge
                       {:background-color "#333333"
                        :color "white"}
                       (case (:size props)
                         :lg {:width "50%"}
                         :md {:width "30%"}
                         :sm {:width "18%"})))
      [:div (use-style {:height "40px"
                        :padding "8px"
                        :font-size "18px"
                        :border "2px solid #C0C0C0"
                        :border-radius "2px"})
       (:title props)]
      [:div (use-style {:display :flex
                        :flex-direction :column
                        :padding "8px"
                        :border-radius "2px"
                        :border "2px solid #C0C0C0"
                        :border-top "none"
                        :border-top-left-radius 0
                        :border-top-right-radius 0})
       children
       [:div {:style {:display :flex
                      :margin-top "16px"}}
        (into [:div (use-style {:display "flex" :gap "6px" :margin-right :auto})]
              (:additional-buttons props))
        [:div (use-style {:display "flex" :gap "6px" :margin-left :auto})
         (when cancel-button
           [:button {:onClick (:onClick cancel-button) :disabled (:disabled cancel-button)} (:text cancel-button)])
         (when ok-button
           [:button {:onClick (:onClick ok-button) :disabled (:disabled ok-button)} (:text ok-button)])]]]]]))

(defn export-image-settings-form []
  (let [image-settings @(re-frame/subscribe [::subs/export-image-settings])]
    [form
     (into
      (export-common-settings-fields
       {:common-settings image-settings
        :type-options [{:label "png" :value :png}
                       {:label "gif" :value :gif}]})
      (when (= (:file-type image-settings) :gif)
        [[form-item
          "Never repeat" [checkbox {:value (not (:repeat image-settings))
                                    :onChange (fn [value]
                                                (re-frame/dispatch [::export/set-settings-option :repeat (not value)]))}]]]))]))

(defn export-spritesheet-settings-form []
  (let [settings @(re-frame/subscribe [::subs/export-spritesheet-settings])]
    [form
     (into
      [[form-item {:label "Columns:"
                   :control [:input {:value (:columns settings)
                                     :type "number"
                                     :onChange (fn [e]
                                                 (re-frame/dispatch [::export/set-settings-option :columns (parse-double (.. e -target -value))]))}]}]
       [form-item {:label "Rows:"
                   :control [:div (:rows settings)]}]]
      (export-common-settings-fields
       {:common-settings settings
        :type-options [{:label "png" :value :png}]}))]))

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
              :cancel-button {:text "Cancel"
                              :onClick (fn []
                                         (re-frame/dispatch [::export/set-opened false]))}
              :ok-button {:text "Export"
                          :disabled (or exporting (not export-settings-valid?))
                          :onClick (fn []
                                     (re-frame/dispatch [::export/export]))}}

       [:div (use-style {:display "flex"
                         :flex-direction "column"
                         :gap "4px"})
        [:div
         [:button {:style {:border (when (= current-tab :image)
                                     "1px solid blue")}
                   :onClick (fn []
                              (re-frame/dispatch [::export/select-tab :image]))}
          "image"]
         [:button {:style {:border (when (= current-tab :spritesheet)
                                     "1px solid blue")}
                   :onClick (fn []
                              (re-frame/dispatch [::export/select-tab :spritesheet]))}
          "spritesheet"]]

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

(defn sprite-resizer-modal []
  (when @(re-frame/subscribe [::subs/sprite-resizer-opened])
    (let [settings @(re-frame/subscribe [::subs/sprite-resizer-settings])
          previews @(re-frame/subscribe [::subs/sprite-resizer-previews])]
      (r/with-let
        [!width (r/atom (str (-> settings :target-size :width)))
         !height (r/atom (str (-> settings :target-size :height)))]
        [modal {:title "Resize canvas"
                :size :sm
                :cancel-button {:text "Cancel"
                                :onClick (fn []
                                           (re-frame/dispatch [::sprite-resizer/set-opened false]))}
                :ok-button {:text "Ok"
                            :onClick (fn []
                                       (re-frame/dispatch [::sprite-resizer/resize]))}}
         [form
          [[form-item {:label "Width"
                       :control [:input {:value @!width
                                         :type "number"
                                         :min 1
                                         :step 1
                                         :max project-settings/max-sprite-dim
                                         :onChange (fn [e]
                                                     (reset! !width (.. e -target -value)))
                                         :onBlur (fn []
                                                   (let [width (min (or (parse-int @!width) 1) project-settings/max-sprite-dim)]
                                                     (reset! !width (str width))
                                                     (re-frame/dispatch [::sprite-resizer/set-settings-option
                                                                         :target-size
                                                                         (assoc (:target-size settings) :width width)])))}]}]
           [form-item {:label "Height"
                       :control [:input {:value @!height
                                         :type "number"
                                         :min 1
                                         :step 1
                                         :max project-settings/max-sprite-dim
                                         :onChange (fn [e]
                                                     (reset! !height (.. e -target -value)))
                                         :onBlur (fn []
                                                   (let [height (min (or (parse-int @!height) 1) project-settings/max-sprite-dim)]
                                                     (reset! !height (str height))
                                                     (re-frame/dispatch [::sprite-resizer/set-settings-option
                                                                         :target-size
                                                                         (assoc (:target-size settings) :height height)])))}]}]
           [form-item {:label "Resize contents:"
                       :control [checkbox {:value (:resize-content settings)
                                           :onChange (fn [value]
                                                       (re-frame/dispatch [::sprite-resizer/set-settings-option :resize-content value]))}]}]
           [form-item {:label "Anchor"
                       :control [:div {:style {:display :grid
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
                                                                      "#2979ff"
                                                                      "#444")}
                                          :onClick (fn []
                                                     (re-frame/dispatch [::sprite-resizer/set-settings-option :anchor {:x x :y y}]))}])]}]
           [previews-container {}
            [previews-grid-items previews]]]]]))))

;; -----------------

(defn keyboard-shortcuts-modal []
  (when @(re-frame/subscribe [::subs/keyboard-shortcuts-modal-opened])
    [modal {:title "Keyboard shortcuts"
            :size :lg
            :cancel-button {:text "Cancel"
                            :onClick (fn []
                                       (re-frame/dispatch [::events/set-keyboard-shortcuts-modal-opened false]))}}
     [:div {:style {:display :grid
                    :grid-template-columns "repeat(auto-fit, minmax(200px,1fr))"}}
      (for [[type shortcuts] keyboard-shortcuts/shortcuts-by-types]
        [:div
         [:h2 (str (str/capitalize (name type)) " shortcuts")]
         [:div
          (for [shortcut shortcuts]
            [:div (:label shortcut)
             " - "
             (keyboard-shortcuts/keys->string (:keys shortcut))])]])]]))

;; -----------------

(defn new-project-modal []
  (when @(re-frame/subscribe [::subs/new-project-modal-opened])
    (r/with-let
      [size @(re-frame/subscribe [::subs/new-project-modal-size])
       !width (r/atom (str (:width size)))
       !height (r/atom (str (:height size)))]
      [modal {:title "New project"
              :size :md
              :cancel-button {:text "Cancel"
                              :onClick (fn []
                                         (re-frame/dispatch [::new-project-modal/set-opened false]))}
              :ok-button {:text "Create"
                          :onClick (fn []
                                     (re-frame/dispatch [::new-project-modal/create]))}
              :additional-buttons [[file-uploader {:onUpload (fn [file-desc]
                                                               (re-frame/dispatch [::project-save-load/load-from-file file-desc]))}
                                    (fn [on-click]
                                      [:button {:onClick on-click}
                                       "open project"])]
                                   [:button {:onClick (fn []
                                                        (re-frame/dispatch [::new-project-modal/create-example-project]))}
                                    "create example project"]]}
       [form
        [[form-item {:label "Width"
                     :control [:input {:value @!width
                                       :type "number"
                                       :min 1
                                       :step 1
                                       :max project-settings/max-sprite-dim
                                       :onChange (fn [e]
                                                   (reset! !width (.. e -target -value)))
                                       :onBlur (fn []
                                                 (let [width (min (or (parse-int @!width) 1) project-settings/max-sprite-dim)]
                                                   (reset! !width (str width))
                                                   (re-frame/dispatch [::new-project-modal/set-width width])))}]}]
         [form-item {:label "Height"
                     :control [:input {:value @!height
                                       :type "number"
                                       :min 1
                                       :step 1
                                       :max project-settings/max-sprite-dim
                                       :onChange (fn [e]
                                                   (reset! !height (.. e -target -value)))
                                       :onBlur (fn []
                                                 (let [height (min (or (parse-int @!height) 1) project-settings/max-sprite-dim)]
                                                   (reset! !height (str height))
                                                   (re-frame/dispatch [::new-project-modal/set-height height])))}]}]]]])))

;; ----------------

(defn tool-view [{:keys [type selected]}]
  (let [image-src (str "./imgs/tools/" (name type) ".svg")
        title (string/replace (name type) "-" " ")]
    [:div (use-style {:width "50px" :height "50px"})
     [icon-button {:src image-src
                   :title title
                   :active selected
                   :size :auto
                   :on-click (fn []
                               (re-frame/dispatch [::events/select-tool type]))}]]))

(defn current-colors-selection []
  (let [primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:position "relative"}}
     [:div {:style {:position "relative" :z-index 1 :cursor "pointer"}}
      [current-color-selection {:value primary-color
                                :onChange (fn [new-primary-color]
                                            (re-frame/dispatch [::events/set-current-color :primary-color new-primary-color]))}]]
     [:div (use-style {:position "relative" :margin-top "-25px" :margin-left "32px" :cursor "pointer"})
      [current-color-selection {:value secondary-color
                                :onChange (fn [new-secondary-color]
                                            (re-frame/dispatch [::events/set-current-color :secondary-color new-secondary-color]))}]]
     [:img (use-style
            {:position "absolute"
             :width "25px"
             :transform "rotate(52deg)"
             :top "48px"
             :left "3px"
             :cursor "pointer"}
            {:src "./imgs/swap.svg"
             :title "Swap colors"
             :on-click (fn [] (re-frame/dispatch [::events/swap-current-colors]))})]]))

(defn tools-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])]
    [:div (use-style {:width "100px"
                      :background-color "#333"})

     [:div (use-style {:display "grid"
                       :grid-template-columns "1fr 1fr"
                       :gap "1px"})
      (for [type tool/types]
        [tool-view {:type type :selected (= (:type tool) type)}])]

     [:div (use-style {:display "flex"
                       :justify-content "center"
                       :margin-top "15px"})
      [current-colors-selection]]]))

;;----

(defn tool-options-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        options @(re-frame/subscribe [::subs/tool-options]) ;; todo: объединить спеку и опции
        options-spec (tool/options-specs (:type tool))]
    [:div (use-style {:display :flex
                      :align-items :center
                      :height "30px"
                      :flex-shrink 0
                      :padding "0 10px"
                      :gap "12px"
                      :background-color "#222"})
     (for [[idx option-spec] (map-indexed vector options-spec)]
       (let [value (get options (:field option-spec))
             on-change #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
             props (assoc option-spec
                          :value value
                          :onChange on-change)]
         ^{:key idx}
         [:div
          (case (:type option-spec)
            :slider (slider props)
            :checkbox (checkbox props))]))]))

(defn right-sidebar []
  [:div (use-style {:display "flex"
                    :flex-direction "column"
                    :height "100%"
                    :background-color "#333"})
   [:div (use-style {:margin-top "auto"})
    [palettes-section]]])

(defn header []
  (let [pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])]
    [:div (use-style {:display "flex"
                      :gap "4px"
                      :padding "5px"
                      :background-color "#333"
                      :border-bottom "2px solid #171717"})
     [:<>
      [new-project-modal]
      [:button {:onClick (fn [] (re-frame/dispatch [::new-project-modal/set-opened true]))}
       "new project"]]
     [:button {:onClick (fn [] (re-frame/dispatch [::project-save-load/save-as-file]))}
      "save project as file"]
     [file-uploader {:onUpload (fn [file-desc]
                                 (re-frame/dispatch [::project-save-load/load-from-file file-desc]))}
      (fn [on-click]
        [:button {:onClick on-click}
         "load project from file"])]
     [:<>
      [:button {:onClick (fn [] (re-frame/dispatch [::export/set-opened true]))}
       "open project export panel"]
      [export-modal]]
     [:<>
      [sprite-resizer-modal]
      [:button {:onClick (fn [] (re-frame/dispatch [::sprite-resizer/set-opened true]))} "resize canvas"]]
     [:<>
      [:button {:onClick (fn [] (re-frame/dispatch [::events/set-keyboard-shortcuts-modal-opened true]))} "keyboard shortcuts"]
      [keyboard-shortcuts-modal]]
     [checkbox {:value pixels-grid-enabled
                :label "grid"
                :onChange (fn [checked] (re-frame/dispatch [::events/enable-pixels-grid checked]))}]

     [:div (use-style {:margin-left "auto"})
      [drawing-info]]]))

(defn main-panel []
  [:div (use-style {:height "100%"
                    :width "100%"
                    :max-height "100%"
                    :max-width "100%"})
   [header]
   [:div (use-style {:display :grid
                     :grid-template-columns "100px 1fr 250px"
                     :height "100%"
                     :width "100%"
                     :max-height "100%"
                     :max-width "100%"})
    [tools-panel]
    [:div (use-style {:display :flex
                      :flex-direction :column
                      :min-width 0
                      :min-height 0})
     [tool-options-panel]
     [canvases-section]
     [:f> timeline-panel]]
    [right-sidebar]]])

(defn app []
  (if @(re-frame/subscribe [::subs/initial-loading])
    [:div
     "LOADING"]
    [main-panel]))
