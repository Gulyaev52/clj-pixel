(ns pixel-art.views.use-vertical-resizer
  (:require
   [react :as react]))

(defn use-vertical-resizer []
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
                             (when (. handler-ref -current)
                               (.. handler-ref
                                   -current
                                   (removeEventListener "mousedown" mousedown-handler)))))))
                     (array (. container-ref -current)
                            (. handler-ref -current)))
    {:handler-ref handler-ref
     :container-ref container-ref}))
