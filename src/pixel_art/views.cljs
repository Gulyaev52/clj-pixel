(ns pixel-art.views
  (:require ["react-color" :as react-color]
            ["tinycolor2" :as tinycolor]
            [pixel-art.events :as events]
            [pixel-art.onion-skin :as onion-skin]
            [pixel-art.palette :as palette :refer [deletable-palette?]]
            [pixel-art.sprite-preview :as sprite-preview]
            [pixel-art.subs :as subs]
            [pixel-art.tool.core :as tool]
            [re-frame.core :as re-frame]
            [react :as react]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [sc.api]
            [clojure.string :as string]
            [pixel-art.model.cel :as cel]
            [pixel-art.sprite-import-export :as sprite-import-export]))

(def !last-mouse-pos (atom nil))

(defn get-mouse-offset-pos [e]
  (if (. e -nativeEvent)
    {:x (.. e -nativeEvent -offsetX)
     :y (.. e -nativeEvent -offsetY)}
    {:x (.. e -offsetX)
     :y (.. e -offsetY)}))

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

(defn slider [{:keys [value label min max step onChange]}]
  ;; todo: labels
  [:div {:style {:display :flex :align-items :center}}
   [:span (str label " (" value ")")]
   [:input {:type "range"
            :value value
            :min min
            :max max
            :step step
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
     (for [option-spec options-spec]
       (let [value (get options (:field option-spec))
             onChange #(re-frame/dispatch [::events/change-tool-option (:field option-spec) %])
             props (assoc option-spec
                          :value value
                          :onChange onChange)]
         ^{:key option-spec}
         [:div
          (case (:type option-spec)
            :slider (slider props)
            :checkbox (checkbox props))]))]))

(defn get-group-color [group-number]
  (nth (cycle ["green" "pink" "yellow" "red" "blue" "purple"]) group-number))

(defn timeline-panel []
  (let [{:keys [cels layers frames current-cel-opacity]} @(re-frame/subscribe [::subs/timeline])
        cels-by-layers (-> cels
                           (#(group-by (fn [c] (-> c :pos :layer-idx)) %))
                           (update-vals (fn [cels] (sort-by #(-> % :pos :frame-idx) cels))))]
    [:div {:style {:display "flex" :flex-direction "column" :gap "4px"}}
     [:div
      [:div {:style {:display :flex}} "frames:"
       [:button {:onClick (fn [] (re-frame/dispatch [::events/add-frame]))} "add"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/remove-frame]))} "remove"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/duplicate-frame]))} "duplicate"]]
      [:div {:style {:display :flex}} "layers:"
       [:button {:onClick (fn [] (re-frame/dispatch [::events/add-layer]))} "add"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/remove-layer]))} "remove"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/duplicate-layer]))} "duplicate"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/merge-layer-with-below]))} "merge"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/move-layer-up]))} "move up"]
       [:button {:onClick (fn [] (re-frame/dispatch [::events/move-layer-down]))} "move down"]]]
     [:div
      [slider {:label "Cell opacity"
               :min 0
               :max 1
               :value current-cel-opacity
               :step 0.1
               :onChange (fn [v] (re-frame/dispatch [::events/set-cel-opacity v]))}]]
     [:div {:style {:display :grid
                    :grid-template-rows "15px"
                    :grid-auto-rows "80px"
                    :grid-template-columns (str "100px " (->> (repeat (count frames) "100px") (string/join " ")))
                    :grid-column-gap "4px"
                    :grid-row-gap "4px"}}
      [:div "Layers"] (for [frame frames]
                        ^{:key frame}
                        [:div {:onClick (fn [] (re-frame/dispatch [::events/select-frame (:idx frame)]))
                               :style {:border-style "solid"
                                       :border-color (if (:current frame)
                                                       "green"
                                                       "black")
                                       :border-width (if (:current frame)
                                                       "2px"
                                                       "1px")
                                       :text-align "center"
                                       :cursor "pointer"}} (inc (:idx frame))])
      (for [layer layers]
        ^{:key layer}
        [:<>
         [:div {:onClick (fn [] (re-frame/dispatch [::events/select-layer (:idx layer)]))
                :style {:display "flex"
                        :border-style "solid"
                        :border-color (if (:current layer)
                                        "green"
                                        "black")
                        :border-width (if (:current layer)
                                        "2px"
                                        "1px")
                        :align-items "center"
                        :cursor "pointer"}}
          [:div
           [:button {:onClick (fn [e]
                                (. e stopPropagation)
                                (re-frame/dispatch [::events/toggle-layer-visibility (:idx layer)]))}
            (if (:visibile? layer) "v0" "v-")]
           [:div (:name layer)]]
          [:div
           [:button {:onClick (fn [e]
                                (. e stopPropagation)
                                (re-frame/dispatch [::events/toggle-layer-automatic-linking (:idx layer)]))}
            (if (:automatic-linking? layer) "a+" "a-")]
           [:div (:name layer)]]]
         (for [cel (cels-by-layers (:idx layer))]
           ^{:key cel}
           [:div {:onClick (fn [e]
                             (cond
                               (.. e -shiftKey)
                               (re-frame/dispatch [::events/add-cels-range-to-selection (:pos cel)])
                               (.. e -ctrlKey)
                               (re-frame/dispatch [::events/toggle-cel-to-selection (:pos cel)])
                               :else (re-frame/dispatch [::events/select-only-1-cel (:pos cel)])))
                  :style {:position "relative"
                          :border-style "solid"
                          :border-color (if (:selected cel)
                                          "green"
                                          "black")
                          :border-width (if (:selected cel)
                                          "2px"
                                          "1px")
                          :background-color (when (cel/emptyy? cel)
                                              "rgba(0, 0, 0, 0.2)")
                          :image-rendering "pixelated"
                          :background-image (str "url(" (:img cel) ")")
                          :background-size "100% 100%"
                          :cursor "pointer"
                          :font-weight "bold"
                          :font-size 18
                          :color (when-let [group-number (:group-number cel)]
                                   (get-group-color group-number))}}
            (when (:selected cel)
              [:div
               [:button {:onClick (fn [e]
                                    (. e stopPropagation)
                                    (re-frame/dispatch [::events/link-selected-cels (:pos cel)]))
                         :style {:position :absolute :top 0 :right 0}} "l"]
               [:button {:onClick (fn [e]
                                    (. e stopPropagation)
                                    (re-frame/dispatch [::events/unlink-selected-cels (:pos cel)]))
                         :style {:position :absolute :top 25 :right 0}} "u"]])
            (some-> (:group-number cel) inc)])])]]))

(defn select [{:keys [value onChange options]}]
  (let [selected-option-idx (ffirst (filter #(= (:value (second %)) value) (map-indexed vector options)))]
    [:select {:value (or selected-option-idx "")
              :onChange (fn [event]
                          (let [selected-option (nth options (parse-double (.. event -target -value)) nil)]
                            (when selected-option
                              (onChange (:value selected-option)))))}
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
                   :backgroundColor "rgba(37,37,37,.9)"}}
     [:div {:style {:backgroundImage (str "url(" frame-img ")")
                    :imageRendering "pixelated"
                    :backgroundSize "contain"
                    :width (:width image-size)
                    :height (:height image-size)}}]]))

(defn sprite-preview-modal []
  [:f> sprite-preview-modal-component])

(defn color-picker-component [{:keys [value onChange onCancel]}]
  (let [!temp-rgb (r/atom (or value "#FFFFFF"))] ;; use ratom
    (fn []
      [:> react-color/PhotoshopPicker
       {:color @!temp-rgb
        :onChange (fn [e] (let [rgb (. e -rgb)]
                            (reset! !temp-rgb (str "rgb(" (. rgb -r) ", " (. rgb -g) ", " (. rgb -b) ")"))))
        :onCancel (fn [] (onCancel))
        :onAccept (fn [] (onChange @!temp-rgb))}])))

(defn color-picker [props]
  [:f> color-picker-component props])

(defn color-picker-with-button [_ _]
  (let [!opened (r/atom false)]
    (fn [color-picker-props button-text]
      [:div {:style {:position "relative"}}
       [:button {:onClick (fn []
                            (reset! !opened true))} button-text]
       (when @!opened
         [:div
          [:div {:style {:position "fixed"
                         :top "0px"
                         :right "0px"
                         :bottom "0px"
                         :left "0px"}
                 :onClick (fn []
                            ((:onCancel color-picker-props))
                            (reset! !opened false))}]
          [:div {:style {:position "absolute" :zIndex 1 :bottom "calc(100% + 5px)"}}
           [color-picker (-> color-picker-props
                             (assoc :onCancel (fn []
                                                ((:onCancel color-picker-props))
                                                (reset! !opened false)))
                             (assoc :onChange (fn [res]
                                                ((:onChange color-picker-props) res)
                                                (reset! !opened false))))]]])])))

(defn file-uploader-comp [{:keys [onUpload accept]} label]
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
                                (. file-reader (readAsText file))))))}]
     [:button {:onClick (fn []
                          (.. input-ref -current click))}
      label]]))

(defn file-uploader [props label]
  [:f> file-uploader-comp props label])

(defn palettes-section []
  (let [palettes @(re-frame/subscribe [::subs/palettes])
        selected-palette-idx @(re-frame/subscribe [::subs/selected-palette-idx])
        selected-palette (nth palettes selected-palette-idx)
        primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div
     [:div {:style {:display :flex}}
      [select {:value selected-palette-idx
               :options (map-indexed (fn [idx p] {:value idx :label (:name p)}) palettes)
               :onChange (fn [idx]
                           (re-frame/dispatch [::palette/select-palette idx]))}]
      [:button {:onClick (fn []
                           (when-let [name (js/prompt)]
                             (re-frame/dispatch [::palette/create-palette name])))}
       "add palette"]
      [:button {:onClick (fn []
                           (when (js/confirm "are you sure?")
                             (re-frame/dispatch [::palette/remove-selected-palette])))
                :disabled (not (deletable-palette? palettes))}
       "remove palette"]
      [:button {:onClick (fn []
                           (when-let [name (js/prompt)]
                             (re-frame/dispatch [::palette/rename-selected-palette name])))}
       "rename palette"]]
     [:div {:style {:display :grid
                    :grid-template-columns "repeat(auto-fill, 33px)"
                    :grid-gap "2px"
                    :width "200px"}}
      (for [[idx color] (map-indexed vector (:colors selected-palette))]
        ^{:key color}
        [:div {:onClick (fn []
                          (re-frame/dispatch [::palette/select-color idx false]))
               :onContextMenu (fn [e]
                                (. e preventDefault)
                                (re-frame/dispatch [::palette/select-color idx true]))
               :style {:width "33px"
                       :height "33px"
                       :background-color color
                       :position "relative"
                       :cursor "pointer"
                       :color (if (.. (tinycolor color) isDark)
                                "white" "black")}}
         (when (= color primary-color) "L")
         (when (= color secondary-color) "R")
         [:div {:onClick (fn [e]
                           (.. e (stopPropagation))
                           (re-frame/dispatch [::palette/remove-color idx]))
                :style {:position "absolute"
                        :right 1
                        :top 1}}
          "X"]])]
     [:button {:onClick (fn [] (re-frame/dispatch [::palette/download-palette]))}
      "download"]
     [file-uploader {:onUpload (fn [file-desc]
                                 (re-frame/dispatch [::palette/load-palette file-desc]))}
      "load"]
     [color-picker-with-button {:value primary-color ;; todo: ?
                                :onChange (fn [color]
                                            (re-frame/dispatch [::palette/add-color color]))
                                :onCancel (fn [])}
      "add color"]]))

(defn sprite-preview-section []
  (let [sprite-preview @(re-frame/subscribe [::subs/sprite-preview])]
    [:div
     (when (:opened sprite-preview)
       [sprite-preview-modal])
     [:div {:style {:display :flex :gap "4px"}}
      "preview size"
      [select {:value (:size sprite-preview)
               :options (map (fn [s] {:value s :label (name s)}) [:1x :2x :4x :custom])
               :onChange (fn [s] (re-frame/dispatch [::sprite-preview/change-size s]))}]]
     [:button {:onClick (fn [] (re-frame/dispatch [::sprite-preview/open]))} "show preview"]]))

(defn onion-skin-section []
  (let [onion-skin @(re-frame/subscribe [::subs/onion-skin])]
    [:div
     [:button {:onClick (fn [] (re-frame/dispatch [::onion-skin/set-enabled (not (:enabled onion-skin))]))}
      (if (:enabled onion-skin)
        "disable onion skin"
        "enable onion skin")]
     [:div
      [:div "frames count"]
      [:div {:style {:display :flex :gap "8px"}}
       [:div
        [:span {:style {:margin-right "4px"}} "prev"]
        [:input {:style {:width "50px"}
                 :type "number"
                 :min 0
                 :value (:prev (:frames-count onion-skin))
                 :onChange (fn [e]
                             (re-frame/dispatch [::onion-skin/set-frames-count {:prev (parse-double (.. e -target -value))
                                                                                :next (:next (:frames-count onion-skin))}]))}]]
       [:div
        [:span {:style {:margin-right "4px"}} "next"]
        [:input {:style {:width "50px"}
                 :type "number"
                 :min 0
                 :value (:next (:frames-count onion-skin))
                 :onChange (fn [e]
                             (re-frame/dispatch [::onion-skin/set-frames-count {:prev (:prev (:frames-count onion-skin))
                                                                                :next (parse-double (.. e -target -value))}]))}]]]]
     [slider {:min 0 :max 1 :step 0.1
              :value (:opacity onion-skin)
              :label "Opacity"
              :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-opacity v]))}]
     [select {:value (:position onion-skin)
              :options [{:value :front :label "in front of sprite"}
                        {:value :behind :label "behind sprite"}]
              :onChange (fn [v] (re-frame/dispatch [::onion-skin/set-position v]))}]]))

(defn drawing-info []
  (let [mouse-pos @(re-frame/subscribe [::subs/mouse-pos])
        scale @(re-frame/subscribe [::subs/scale])
        sprite-size @(re-frame/subscribe [::subs/sprite-size])]
    [:div {:style {:display :flex :gap "10px"}}
     [:div (str "[" (:width sprite-size) "x" (:height sprite-size) "]")]
     [:div (str (:x mouse-pos) ":" (:y mouse-pos))]
     [:div (str "scale=" scale)]]))

(defn canvases-section-component []
  (let [ref (react/useRef)
        viewport-ref (react/useRef)
        scale @(re-frame/subscribe [::subs/scale])
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
                                                                   (if (< (. e -deltaY) 0) 1.4 (/ 1 1.4))
                                                                   center-pos
                                                                   mouse-offset-pos])))]
                               (.. ref -current (addEventListener "wheel" handler))
                               (fn []
                                 (.. ref -current (removeEventListener "wheel" handler)))))
                           (array ref viewport-ref scale))
        viewport-size @(re-frame/subscribe [::subs/viewport-size])
        onion-skin @(re-frame/subscribe [::subs/onion-skin])
        panning @(re-frame/subscribe [::subs/panning])
        user-is-drawing @(re-frame/subscribe [::subs/user-is-drawing])
        layers @(re-frame/subscribe [::subs/layers])]
    [:div {:style {:display :flex :flex-direction :column :gap "10px" :align-items "center"}}
     [:div {:style {:display :flex :justify-content :center :align-items "center"}}
      [:div {:style {:position "relative"
                     :border "1px solid black"}
             :ref ref
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
       [:div {:id "viewport" :ref viewport-ref :style {:overflow "auto" :width (:width viewport-size) :height (:height viewport-size)}}
        [:div {:id "drawing-canvas-container" :style {:position "relative"}}
         [:div {:id "canvas-layers" :style {:position "relative" :left "50%" :top "50%" :transform "translate(-50%, -50%)"}}
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
          [:canvas {:id "grid"
                    :style {:position :absolute
                            :left 0
                            :top 0
                            :zIndex (+ (count layers) 1)
                            :width "100%"
                            :height "100%"}}]]]]]]
     [drawing-info]]))

(defn canvases-section []
  [:f> canvases-section-component])

(defn import-export-section []
  [:div
   [:button {:onClick (fn [] (re-frame/dispatch [::sprite-import-export/export-sprite-as-file]))}
    "save as file"]
   [file-uploader {:onUpload (fn [file-desc]
                               (re-frame/dispatch [::sprite-import-export/import-sprite-from-file file-desc]))}
    "load from file"]])

(defn main-panel []
  (let [tool @(re-frame/subscribe [::subs/tool])
        pixels-grid-enabled @(re-frame/subscribe [::subs/pixels-grid-enabled])
        primary-color @(re-frame/subscribe [::subs/primary-color])
        secondary-color @(re-frame/subscribe [::subs/secondary-color])]
    [:div {:style {:display :grid :grid-template-columns "490px 1fr"}}
     [:div {:style {:display :flex :flex-direction :column :gap "10px"}}
      [options-toolbar (:type tool)]
      [:div {:style {:display :flex :gap "8px"}}
       (select {:value (:type tool)
                :onChange (fn [tool]
                            (re-frame/dispatch [::events/select-tool tool]))
                :options (map (fn [t] {:value t :label (name t)}) tool/types)})
       [checkbox {:value pixels-grid-enabled
                  :label "grid"
                  :onChange (fn [checked] (re-frame/dispatch [::events/enable-pixels-grid checked]))}]
       [:div (str "primary-color=" primary-color)]
       [:div (str "secondary-color=" secondary-color)]]
      [:div [timeline-panel]]
      [sprite-preview-section]
      [onion-skin-section]
      [palettes-section]
      [import-export-section]]
     [canvases-section]]))

(defn mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/render [main-panel] root-el)))
