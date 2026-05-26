(ns pixel-art.timeline.subs
  (:require
   [pixel-art.model.sprite :as sprite]
   [pixel-art.onion-skin.subs :as onion-skin.subs]
   [pixel-art.subs :as subs]
   [re-frame.core :as re-frame]
   [sc.api :as api]))

(re-frame/reg-sub
 ::timeline
 :<- [::subs/sprite]
 :<- [::onion-skin.subs/frames-idx]
 (fn [[sprite onion-skin-frames-idx]] 
   (let [{:keys [layers frames]} sprite
         selected-cels-pos (sprite/get-selected-cels-pos sprite)
         current-cel-pos (sprite/get-current-cel-pos sprite)]
     {:cels (sprite/get-cels-with-pos-as-coll sprite)
      :layers (map-indexed (fn [idx layer]
                             (merge layer {:current (= idx (:layer-idx current-cel-pos))
                                           :selected (some? ((set (map :layer-idx selected-cels-pos)) idx)) ;; todo: почему current
                                           :idx idx}))
                           layers)
      :some-layer-visible (some :visible? layers)
      :some-layer-automatic-linking (some :automatic-linking? layers)
      :frames (map-indexed (fn [idx frame]
                             (merge frame {:current (= idx (:frame-idx current-cel-pos))
                                           :selected (some? ((set (map :frame-idx selected-cels-pos)) idx))
                                           :idx idx
                                           :onion-skin (contains? onion-skin-frames-idx idx)}))
                           frames)
      :disabled-actions {:remove-layer (not (sprite/remove-layer-available? sprite))
                         :merge-layer-with-below (not (sprite/merge-layer-with-below-available? sprite))
                         :move-layer-up (not (sprite/move-layer-up-available? (:layer-idx current-cel-pos)))
                         :move-layer-down (not (sprite/move-layer-down-available? (:layer-idx current-cel-pos) sprite))
                         :remove-frame (not (sprite/remove-frame-available? sprite))
                         :move-frame-left (not (sprite/move-frame-left-available? (:frame-idx current-cel-pos)))
                         :move-frame-right (not (sprite/move-frame-right-available? (:frame-idx current-cel-pos) sprite))
                         :link-cels (not (sprite/can-cels-be-linked? sprite))
                         :unlink-cel (not (sprite/can-cel-be-unlinked? sprite))}})))
