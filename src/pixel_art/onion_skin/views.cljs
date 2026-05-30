(ns pixel-art.onion-skin.views
  (:require
   [pixel-art.onion-skin.events :as events]
   [pixel-art.onion-skin.subs :as subs]
   [pixel-art.onion-skin.utils :refer [get-onion-skin-frames-idx]]
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.utils.sprite-canvas :as sprite-canvas]
   [pixel-art.views.ui-kit :refer [form form-item input-number select slider]]
   [re-frame.core :as re-frame]
   [react :as react]))

(defn- draw-onion-skin [canvas sprite frames-count]
  (let [ctx (. canvas (getContext "2d"))
        onion-frames-idx (get-onion-skin-frames-idx sprite frames-count)]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))
    (doseq [frame-idx onion-frames-idx]
      (sprite-canvas/draw-frame-on-single-canvas frame-idx sprite canvas))))

(defn- hide-onion-skin [canvas]
  (canvas/clear-canvas canvas))

(defn use-draw-onion-skin [sprite onion-skin onion-skin-ref]
  (react/useEffect (fn []
                     (when (and (:enabled onion-skin) (.-current onion-skin-ref))
                       (draw-onion-skin (.-current onion-skin-ref) sprite (:frames-count onion-skin)))
                     (fn []
                       (when (.-current onion-skin-ref)
                         (hide-onion-skin (.-current onion-skin-ref)))))
                   #js [onion-skin onion-skin-ref sprite (:frames-count onion-skin)]))

(defn onion-skin-settings []
  (let [onion-skin @(re-frame/subscribe [::subs/onion-skin])]
    [form
     [form-item {:label "Previous Frames"
                 :control [input-number {:min 0
                                         :value (:prev (:frames-count onion-skin))
                                         :block true
                                         :testid "input-prev-frames"
                                         :on-blur (fn [value]
                                                    (re-frame/dispatch [::events/set-frames-count (assoc (:frames-count onion-skin)
                                                                                                         :prev
                                                                                                         value)]))}]}]
     [form-item {:label "Next Frames"
                 :control [input-number {:min 0
                                         :value (:next (:frames-count onion-skin))
                                         :block true
                                         :testid "input-next-frames"
                                         :on-blur (fn [value]
                                                    (re-frame/dispatch [::events/set-frames-count (assoc (:frames-count onion-skin)
                                                                                                         :next
                                                                                                         value)]))}]}]
     [form-item {:label "Opacity"
                 :control [:div {:data-testid "slider-opacity"}
                            [slider {:min 0 :max 1 :step 0.1
                                     :value (:opacity onion-skin)
                                     :block true
                                     :on-change (fn [v] (re-frame/dispatch [::events/set-opacity v]))}]]}]
     [form-item {:label "Position"
                 :control [select {:value (:position onion-skin)
                                   :data-testid "select-position"
                                   :options [{:value :front :label "in front of sprite"}
                                             {:value :behind :label "behind sprite"}]
                                   :on-change (fn [v]
                                                (re-frame/dispatch [::events/set-position v]))}]}]]))
