(ns pixel-art.drawing.views
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.drawing.events :as events]
   [pixel-art.drawing.subs :as subs]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.onion-skin.subs :as onion-skin.subs]
   [pixel-art.onion-skin.views :refer [use-draw-onion-skin]]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.subs :as common-subs]
   [pixel-art.views.constants :refer [drawing-border
                                      preview-container-bg-color
                                      transparent-color-img]]
   [pixel-art.views.ui-kit :refer [space typography]]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]
   [react :as react]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(defn- use-zoom [viewport-ref]
  (let [view-render-after-zoom-ref (react/useRef true)]
    (react/useEffect (fn []
                       (set! (.-current view-render-after-zoom-ref) true)))
    (react/useEffect (fn []
                       (let [handler (fn [e]
                                       (. e preventDefault) ;; preventDefault doesn't work when bind onWheel event on tag
                                       (. e stopPropagation)
                                       (when (.-current view-render-after-zoom-ref)
                                         (let [viewport-rect (.. viewport-ref -current getBoundingClientRect)
                                               center-pos {:x (- (.. e -clientX) (.. viewport-rect -left))
                                                           :y (- (.. e -clientY) (.. viewport-rect -top))}
                                               mouse-offset-pos {:x (+ (- (.. e -clientX) (.. viewport-rect -left))
                                                                       (.. viewport-ref -current -scrollLeft))
                                                                 :y (+ (- (.. e -clientY) (.. viewport-rect -top))
                                                                       (.. viewport-ref -current -scrollTop))}
                                               prev-scale (:scale @db/app-db)
                                               delta (if (< (. e -deltaY) 0) 1.1 (/ 1 1.1))
                                               new-scale (-> (* prev-scale delta)
                                                             (min project-settings/max-scale)
                                                             (max project-settings/min-scale))]
                                           (when (not= prev-scale new-scale)
                                             (set! (.-current view-render-after-zoom-ref) false) ;; todo: add comment
                                             (re-frame/dispatch [::events/zoom delta new-scale center-pos mouse-offset-pos])))))]
                         (.. viewport-ref -current (addEventListener "wheel" handler))
                         (fn []
                           (when (. viewport-ref -current)
                             (.. viewport-ref -current (removeEventListener "wheel" handler))))))
                     (array viewport-ref))))

(defn- is-right-button? [e] ;; todo: -which уже не поддерживается
  (or (= (. e -button) 2) (= (. e -which) 3)))

(defn- is-middle-button? [e]
  (= (. e -button) 1))

(defn- get-mouse-client-pos [e]
  {:x (. e -clientX) :y (. e -clientY)})

(defn- canvas-pos->frame-pos [event scale]
  (let [mouse-pos (get-mouse-client-pos event)
        canvas-layers-rect (.. js/document
                               (getElementById "canvas-layers")
                               (getBoundingClientRect))]
    {:x (. js/Math (floor (/ (- (:x mouse-pos)
                                (. canvas-layers-rect -left)) scale)))
     :y (. js/Math (floor (/ (- (:y mouse-pos)
                                (. canvas-layers-rect -top)) scale)))}))

(def !last-mouse-pos (atom nil)) ;; todo: into component?

(def-func-component preview-comp []
  (let [sprite @(re-frame/subscribe [::common-subs/sprite])
        preview @(re-frame/subscribe [::subs/preview])
        sprite-size (:size sprite)]
    (react/useEffect (fn []
                       (when preview
                         (let [current-layer (. js/document (getElementById "current-layer"))]
                           (canvas/draw-cel {:size sprite-size :pixels preview} current-layer))))
                     (array preview sprite-size))
    [:div]))

(def-func-component sprite-layers-comp []
  (let [sprite @(re-frame/subscribe [::common-subs/sprite])
        preview @(re-frame/subscribe [::subs/preview])]
    (react/useEffect (fn []
                       (when (and sprite (not preview))
                         (canvas/clear-canvases (vec (. js/document (getElementsByClassName "layer"))))
                         (canvas/draw-frame (sprite/get-current-frame-idx sprite) sprite)))
                     (array sprite preview))
    [:div]))

(def-func-component visual-effects-comp []
  (let [sprite @(re-frame/subscribe [::common-subs/sprite])
        visual-effects @(re-frame/subscribe [::subs/visual-effects])
        sprite-size (:size sprite)]
    (react/useEffect (fn []
                       (let [visual-effects-canvas (. js/document (getElementById "visual-effects"))]
                         (if visual-effects
                           (let [image-data (js/ImageData. (js/Uint8ClampedArray. (. visual-effects -buffer))
                                                           (:width sprite-size)
                                                           (:height sprite-size))
                                 ctx (. visual-effects-canvas (getContext "2d"))]
                             (. ctx (putImageData image-data 0 0)))
                           (canvas/clear-canvas visual-effects-canvas))))
                     (array visual-effects sprite-size))
    [:div]))

(defn debounce
  "Создает функцию, которая откладывает вызов функции `f` до тех пор, пока не пройдет `wait` миллисекунд после последнего вызова."
  [f wait]
  (let [timeout-id (atom nil)]
    (fn [& args]
      (when-let [tid @timeout-id]
        (js/clearTimeout tid))
      (reset! timeout-id
              (js/setTimeout
               (fn []
                 (apply f args))
               wait)))))

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
     [preview-comp]
     [sprite-layers-comp]
     [visual-effects-comp]
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

                                     mouse-move (debounce
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
                                 (re-frame/dispatch [::events/handle-mouse-event :mouse-down mouse-pos right-button])
                                 (.. js/document (addEventListener "mousemove" mouse-move))
                                 (.. js/document (addEventListener "mouseup" mouse-up)))))
            :on-mouse-leave (fn [event]
                              (when-not (or mouse-was-down panning)
                                (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                  (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (is-right-button? event)]))))
            :on-mouse-move (fn [event]
                             (when-not (or mouse-was-down panning)
                               (let [mouse-pos (canvas-pos->frame-pos event scale)]
                                 (when (not= mouse-pos @!last-mouse-pos)
                                   (reset! !last-mouse-pos mouse-pos)
                                   (re-frame/dispatch [::events/handle-mouse-event :mouse-move mouse-pos (is-right-button? event)])))))}
      [:div {:id "drawing-canvas-container"
             :style (merge {:position "relative"} drawing-container-size)}
       [:div {:id "canvas-layers"
              :style (merge
                      {:position "relative"
                       :left "50%"
                       :top "50%"
                       :transform "translate(-50%, -50%)"
                       :background-image transparent-color-img
                       :outline drawing-border ;; todo: why not border
                       }
                      scaled-sprite-size)}
        [:canvas (merge
                  {:id "layers-below"
                   :className "layer"
                   :style {:position :absolute
                           :left 0
                           :top 0
                           :image-rendering "pixelated"
                           :z-index 0
                           :width "100%"
                           :height "100%"}}
                  sprite-size)]
        [:canvas (merge
                  {:id "current-layer"
                   :data-testid "current-layer"
                   :className "layer"
                   :style {:position :absolute
                           :left 0
                           :top 0
                           :image-rendering "pixelated"
                           :z-index 1
                           :width "100%"
                           :height "100%"}}
                  sprite-size)]
        [:canvas (merge
                  {:id "visual-effects"
                   :style {:position :absolute
                           :left 0
                           :top 0
                           :image-rendering "pixelated"
                           :z-index 1
                           :width "100%"
                           :height "100%"}}
                  sprite-size)]
        [:canvas (merge
                  {:id "layers-above"
                   :className "layer"
                   :style {:position :absolute
                           :left 0
                           :top 0
                           :image-rendering "pixelated"
                           :z-index 2
                           :width "100%"
                           :height "100%"}}
                  sprite-size)]
        [:canvas (merge
                  {:id "onion-skin"
                   :ref onion-skin-ref
                   :style {:position :absolute
                           :left 0
                           :top 0
                           :opacity (:opacity onion-skin)
                           :image-rendering "pixelated"
                           :z-index (if (= (:position onion-skin) :front)
                                      (count layers)
                                      0);; todo: подумать тут
                           :width "100%"
                           :height "100%"}}
                  sprite-size)]
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
       [typography (str "[" (:width sprite-size) "x" (:height sprite-size) "]")]
       [typography (str (:x mouse-pos) ":" (:y mouse-pos))]
       [typography (str "scale=" (. scale (toFixed 2)))]])))
