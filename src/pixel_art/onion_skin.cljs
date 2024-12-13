(ns pixel-art.onion-skin
  (:require [pixel-art.canvas :as canvas]
            [pixel-art.model.sprite :as sprite]
            [re-frame.core :as re-frame]
            [re-frame.db :as db]))

(defn init []
  {:enabled false
   :position :front ;; front|behind
   :opacity 0.3
   :frames-count {:prev 1 :next 1}})

(defn get-onion-skin-frames-idx [sprite frames-count]
  (let [{:keys [frames]} sprite
        current-frame-idx (sprite/get-current-frame-idx sprite)
        prev-idxs (range (max (- current-frame-idx (:prev frames-count)) 0) current-frame-idx)
        next-idxs (range (inc current-frame-idx)
                         (inc (min (+ current-frame-idx (:next frames-count))
                                   (dec (count frames)))))]
    (set (concat prev-idxs next-idxs))))

(defn get-onion-skin-frames [sprite frames-count]
  (->> (get-onion-skin-frames-idx sprite frames-count)
       (map #(sprite/get-frame-cels % sprite))))

(re-frame/reg-event-fx
 ::set-frames-count
 (fn [{:keys [db]} [_ frames-count]]
   {:db (assoc-in db [:onion-skin :frames-count] frames-count)}))

(re-frame/reg-event-fx
 ::set-enabled
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc-in db [:onion-skin :enabled] enabled)}))

(re-frame/reg-event-fx
 ::set-opacity
 (fn [{:keys [db]} [_ opacity]]
   {:db (assoc-in db [:onion-skin :opacity] opacity)}))

(re-frame/reg-event-fx
 ::set-position
 (fn [{:keys [db]} [_ position]]
   {:db (assoc-in db [:onion-skin :position] position)}))

;; todo: rename this module to onion-skin settings
(defn draw-onion-skin [sprite]
  (let [canvas (. js/document (getElementById "onion-skin"))
        ctx (. canvas (getContext "2d"))
        db @db/app-db
        onion-frames-idx (get-onion-skin-frames-idx sprite (-> db :onion-skin :frames-count))]
    (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))
    (doseq [frame-idx onion-frames-idx]
      (canvas/draw-frame-on-single-canvas frame-idx sprite canvas))))

(defn hide-onion-skin []
  (canvas/clear-canvas (. js/document (getElementById "onion-skin"))))
