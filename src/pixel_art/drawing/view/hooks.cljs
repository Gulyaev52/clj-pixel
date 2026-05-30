(ns pixel-art.drawing.view.hooks
  (:require
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.model.sprite-canvas :as sprite-canvas]
   [pixel-art.drawing.events :as events]
   [pixel-art.drawing.subs :as subs]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.project-config :as project-config]
   [pixel-art.subs :as common-subs]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]
   [react :as react]
   [sc.api])
  (:require-macros [pixel-art.views.reagent :refer [def-func-component]]))

(defn use-zoom [viewport-ref]
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
                                                             (min project-config/max-zoom-scale)
                                                             (max project-config/min-zoom-scale))]
                                           (when (not= prev-scale new-scale)
                                             (set! (.-current view-render-after-zoom-ref) false)
                                             (re-frame/dispatch [::events/zoom delta new-scale center-pos mouse-offset-pos])))))]
                         (.. viewport-ref -current (addEventListener "wheel" handler))
                         (fn []
                           (when (. viewport-ref -current)
                             (.. viewport-ref -current (removeEventListener "wheel" handler))))))
                     (array viewport-ref))))

;; use-something-comp should be just hooks but they are components because only there subscription can be used

(def-func-component use-preview-comp []
  (let [sprite @(re-frame/subscribe [::common-subs/sprite])
        preview @(re-frame/subscribe [::subs/preview])
        sprite-size (:size sprite)]
    (react/useEffect (fn []
                       (when preview
                         (let [current-layer (. js/document (getElementById "current-layer"))]
                           (sprite-canvas/draw-cel {:size sprite-size :pixels preview} current-layer))))
                     (array preview sprite-size))
    [:div]))

(def-func-component use-sprite-layers-comp []
  (let [sprite @(re-frame/subscribe [::common-subs/sprite])
        preview @(re-frame/subscribe [::subs/preview])]
    (react/useEffect (fn []
                       (when (and sprite (not preview))
                         (canvas/clear-canvases (vec (. js/document (getElementsByClassName "layer"))))
                         (sprite-canvas/draw-frame (sprite/get-current-frame-idx sprite) sprite)))
                     (array sprite preview))
    [:div]))

(def-func-component use-visual-effects-comp []
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
