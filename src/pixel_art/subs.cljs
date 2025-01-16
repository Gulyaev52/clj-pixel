(ns pixel-art.subs
  (:require
   [pixel-art.model.sprite :as sprite]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.tool.utils :refer [get-tool-options]]
   [pixel-art.utils.coll :as coll]
   [re-frame.core :as re-frame]
   [pixel-art.onion-skin :as onion-skin]))

;; todo: добавить комментарий почему картинкой
(re-frame/reg-sub
 ::pixels-grid-cel-img
 (fn [db]
   (let [scale (:scale db)]
     (when (:pixels-grid-enabled db)
       (str "data:image/svg+xml,%3Csvg width='" scale "' height='" scale "' opacity='" (min (/ scale project-settings/max-scale) 1) "' xmlns='http://www.w3.org/2000/svg'%3E%3Crect width='" scale "' height='" scale "' vector-effect='non-scaling-stroke' fill='transparent' stroke='blue' /%3E%3C/svg%3E")))))

(re-frame/reg-sub
 ::initial-loading
 (fn [db] (-> db :initial-loading)))

(re-frame/reg-sub
 ::keyboard-shortcuts-modal-opened
 (fn [db]
   (-> db :keyboard-shortcuts-modal-opened)))

(re-frame/reg-sub
 ::layers
 (fn [db]
   (-> db :sprite :layers)))

(re-frame/reg-sub
 ::drawing-container-size
 (fn [db] (:drawing-container-size db)))

(re-frame/reg-sub
 ::viewport-scroll
 (fn [db] (:viewport-scroll db)))

(re-frame/reg-sub
 ::sprite-size
 (fn [db]
   (-> db :sprite :size)))

(re-frame/reg-sub
 ::sprite
 (fn [db]
   (:sprite db)))

(re-frame/reg-sub
 ::pixels-grid-enabled
 (fn [db]
   (:pixels-grid-enabled db)))

(re-frame/reg-sub
 ::scale
 (fn [db]
   (:scale db)))

(re-frame/reg-sub
 ::tool
 (fn [db]
   (:tool db)))

(re-frame/reg-sub
 ::tool-options
 (fn [db]
   (get-tool-options db)))

(re-frame/reg-sub
 ::sprite-preview
 (fn [db]
   (:sprite-preview db)))

(re-frame/reg-sub
 ::onion-skin
 (fn [db]
   (:onion-skin db)))

(re-frame/reg-sub
 ::onion-skin-enabled
 (fn [db]
   (:enabled (:onion-skin db))))

(re-frame/reg-sub
 ::primary-color
 (fn [db]
   (:primary-color db)))

(re-frame/reg-sub
 ::secondary-color
 (fn [db]
   (:secondary-color db)))

(re-frame/reg-sub
 ::current-palette
 (fn [db]
   (coll/find-first :current (:palettes db))))

(re-frame/reg-sub
 ::palettes
 (fn [db]
   (:palettes db)))

(re-frame/reg-sub
 ::panning
 (fn [db]
   (some? (:start-viewport-scroll db))))

(re-frame/reg-sub
 ::user-is-drawing
 (fn [db] (:user-is-drawing db)))

(re-frame/reg-sub
 ::mouse-pos
 (fn [db] (:mouse-pos db)))

;; selected
;; current
;; cel-img
;; pos
;; empty
(re-frame/reg-sub
 ::timeline
 (fn [db]
   (def db db)
   (let [{:keys [sprite]} db
         {:keys [layers frames]} sprite
         selected-cels-pos (sprite/get-selected-cels-pos sprite)
         current-cel-pos (sprite/get-current-cel-pos sprite)
         onion-skin (:onion-skin db)
         onion-skin-frames-idx (when (:enabled onion-skin)
                                 (onion-skin/get-onion-skin-frames-idx (:sprite db) (:frames-count onion-skin)))]
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
