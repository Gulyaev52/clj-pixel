(ns pixel-art.subs
  (:require [pixel-art.model.cel :as cel]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.tool.utils :refer [get-tool-options]]
            [pixel-art.utils.coll :as coll]
            [re-frame.core :as re-frame]
            [sc.api :as api]
            [pixel-art.export :as export]))

(re-frame/reg-sub
 ::layers
 (fn [db]
   (-> db :sprite :layers)))

(re-frame/reg-sub
 ::export-spritesheet-settings
 export/get-spritesheet-settings)

(re-frame/reg-sub
 ::export-image-settings
 export/get-image-settings)

(re-frame/reg-sub
 ::export-settings-valid?
 (fn [db]
   (not (empty? (-> db :export :common-settings :file-name)))))

(re-frame/reg-sub
 ::exporting
 (fn [db]
   (-> db :export :exporting)))

(re-frame/reg-sub
 ::export-current-tab
 (fn [db]
   (-> db :export :current-tab)))

(re-frame/reg-sub
 ::export-modal-opened
 (fn [db]
   (-> db :export :opened)))

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
 ::cel-imgs
 (fn [db]
   (:cel-imgs db)))

(re-frame/reg-sub
 ::sprite-preview
 (fn [db]
   (:sprite-preview db)))

(re-frame/reg-sub
 ::onion-skin
 (fn [db]
   (:onion-skin db)))

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
 ::viewport-size
 (fn [db] (:viewport-size db)))

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
   (let [{:keys [cel-imgs sprite]} db
         {:keys [layers frames]} sprite
         selected-cels-pos (sprite/get-selected-cels-pos sprite)
         current-cel (sprite/get-current-cel sprite)
         cels-coll (sprite/get-cels-with-pos-as-coll sprite)]
     {:cels (->> cels-coll
                 (map (fn [cel] (merge cel {:img (cel-imgs (:pos cel))
                                            :empty (cel/emptyy? cel)}))))
      :layers (map-indexed (fn [idx layer]
                             (merge layer {:current (some? ((set (map :layer-idx selected-cels-pos)) idx)) ;; todo: почему current
                                           :idx idx}))
                           layers)
      :frames (map-indexed (fn [idx frame]
                             (merge frame {:current (some? ((set (map :frame-idx selected-cels-pos)) idx))
                                           :idx idx}))
                           frames)
      :current-cel-opacity (:opacity current-cel)})))
