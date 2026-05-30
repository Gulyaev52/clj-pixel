(ns pixel-art.drawing.views
  (:require
   [pixel-art.drawing.events :as events]
   [pixel-art.drawing.subs :as subs]
   [pixel-art.drawing.view.hooks :refer [use-preview-comp
                                         use-sprite-layers-comp
                                         use-visual-effects-comp use-zoom]]
   [pixel-art.onion-skin.subs :as onion-skin.subs]
   [pixel-art.onion-skin.views :refer [use-draw-onion-skin]]
   [pixel-art.subs :as common-subs]
   [pixel-art.utils.view :as utils.view]
   [pixel-art.views.constants :refer [drawing-border
                                      preview-container-bg-color
                                      transparent-color-img]]
   [pixel-art.views.ui-kit :refer [space typography]]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]
   [react :as react]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(defn- canvas-pos->frame-pos [event scale]
  (let [mouse-pos (utils.view/get-mouse-client-pos event)
        canvas-layers-rect (.. js/document
                               (getElementById "canvas-layers")
                               (getBoundingClientRect))]
    {:x (. js/Math (floor (/ (- (:x mouse-pos)
                                (. canvas-layers-rect -left)) scale)))
     :y (. js/Math (floor (/ (- (:y mouse-pos)
                                (. canvas-layers-rect -top)) scale)))}))

(def !last-mouse-pos (atom nil))

(defn- drawing-canvas [attrs style sprite-size]
  [:canvas (merge
            attrs
            {:style (merge {:position :absolute
                            :left 0
                            :top 0
                            :image-rendering "pixelated"
                            :width "100%"
                            :height "100%"}
                           style)}
            sprite-size)])

(def-func-component drawing []
  (let [viewport-ref (react/useRef)
        onion-skin-ref (react/useRef)
        panning @(re-frame/subscribe [::subs/panning])
        mouse-was-down @(re-frame/subscribe [::subs/mouse-was-down])
        layers @(re-frame/subscribe [::common-subs/layers])
        pixels-grid-cel-img @(re-frame/subscribe [::subs/pixels-grid-cel-img])
        scale @(re-frame/subscribe [::subs/scale])
        sprite @(re-frame/subscribe [::common-subs/sprite])
        sprite-size (:size sprite)
        scaled-sprite-size (update-vals sprite-size #(* scale %))
        drawing-container-size @(re-frame/subscribe [::subs/drawing-container-size])
        viewport-scroll @(re-frame/subscribe [::subs/viewport-scroll])
        onion-skin @(re-frame/subscribe [::onion-skin.subs/onion-skin])]
    ;; todo: optimize 
    (react/useLayoutEffect (fn []
                             (when-let [current (.-current viewport-ref)]
                               (set! (.. current -scrollTop) (:y viewport-scroll))
                               (set! (.. current -scrollLeft) (:x viewport-scroll)))
                             (fn []))
                           (array viewport-scroll viewport-ref))
    (use-zoom viewport-ref)
    (use-draw-onion-skin sprite onion-skin onion-skin-ref)
    [:<>
     ;; use-something-comp should be just hooks but they are components because only there subscription can be used
     [use-preview-comp]
     [use-sprite-layers-comp]
     [use-visual-effects-comp]
     [:div {:id "canvas-viewport"
            :data-testid "canvas-viewport"
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
                             (if (utils.view/is-middle-button? event)
                               (do
                                 (re-frame/dispatch [::events/start-panning (utils.view/get-mouse-client-pos event)])
                                 (let [mouse-move (fn [event]
                                                    (re-frame/dispatch [::events/pan (utils.view/get-mouse-client-pos event)]))]
                                   (.. js/document (addEventListener "mousemove" mouse-move))
                                   (.. js/document (addEventListener "mouseup"
                                                                     (fn []
                                                                       (re-frame/dispatch [::events/stop-panning])
                                                                       (.. js/document (removeEventListener "mousemove" mouse-move)))
                                                                     #js {"once" true}))))
                               (let [right-button (utils.view/is-right-button? event)
                                     mouse-pos (canvas-pos->frame-pos event scale)
                                     mouse-move (utils.view/debounce
                                                 (fn [event]
                                                   (let [scale (:scale @db/app-db)
                                                         mouse-pos (canvas-pos->frame-pos event scale)]
                                                     (when (not= mouse-pos @!last-mouse-pos)
                                                       (reset! !last-mouse-pos mouse-pos)
                                                       (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos right-button]))))
                                                 1)
                                     mouse-up (fn mouse-up [event]
                                                (let [scale (:scale @db/app-db)
                                                      mouse-pos (canvas-pos->frame-pos event scale)]
                                                  (reset! !last-mouse-pos mouse-pos)
                                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-up mouse-pos right-button])
                                                  (.. js/document (removeEventListener "mousemove" mouse-move))
                                                  (.. js/document (removeEventListener "mouseup" mouse-up))))]
                                 (reset! !last-mouse-pos mouse-pos)
                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-down mouse-pos right-button])
                                 (.. js/document (addEventListener "mousemove" mouse-move))
                                 (.. js/document (addEventListener "mouseup" mouse-up)))))
            :on-mouse-leave (fn [event]
                              (when-not (or mouse-was-down panning)
                                (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (utils.view/is-right-button? event)]))))
            :on-mouse-move (fn [event]
                             (when-not (or mouse-was-down panning)
                               (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                 (when (not= mouse-pos @!last-mouse-pos)
                                   (reset! !last-mouse-pos mouse-pos)
                                   (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (utils.view/is-right-button? event)])))))}
      [:div {:id "drawing-canvas-container"
             :style (merge {:position "relative"} drawing-container-size)}
       [:div {:id "canvas-layers"
              :data-testid "canvas-layers"
              :style (merge
                      {:position "relative"
                       :left "50%"
                       :top "50%"
                       :transform "translate(-50%, -50%)"
                       :background-image transparent-color-img
                       :outline drawing-border}
                      scaled-sprite-size)}
        [drawing-canvas
         {:id "layers-below"
          :className "layer"}
         {:z-index 0}
         sprite-size]
        [drawing-canvas
         {:id "current-layer"
          :data-testid "current-layer"
          :className "layer"}
         {:z-index 1}
         sprite-size]
        [drawing-canvas
         {:id "visual-effects"}
         {:z-index 1}
         sprite-size]
        [drawing-canvas
         {:id "layers-above"
          :className "layer"}
         {:z-index 2}
         sprite-size]
        [drawing-canvas
         {:id "onion-skin"
          :ref onion-skin-ref}
         {:opacity (:opacity onion-skin)
          :image-rendering "pixelated"
          :z-index (if (= (:position onion-skin) :front)
                     (count layers)
                     0)}
         sprite-size] 
        (when pixels-grid-cel-img
          [:div {:style {:background-image (str "url(\"" pixels-grid-cel-img "\")")
                         :background-size scale
                         :width "100%"
                         :height "100%"
                         :position "relative"
                         :z-index 1000}}])]]]]))

(defn drawing-info []
  (when-not @(re-frame/subscribe [::common-subs/initial-loading])
    (let [mouse-pos @(re-frame/subscribe [::subs/mouse-pos])
          scale @(re-frame/subscribe [::subs/scale])
          sprite-size @(re-frame/subscribe [::common-subs/sprite-size])]
      [space
       [typography {:data-testid "drawing-info-sprite-size"} (str "[" (:width sprite-size) "x" (:height sprite-size) "]")]
       [typography {:data-testid "drawing-info-mouse-pos"} (str (:x mouse-pos) ":" (:y mouse-pos))]
       [typography {:data-testid "drawing-info-scale"} (str "scale=" (. scale (toFixed 2)))]])))
