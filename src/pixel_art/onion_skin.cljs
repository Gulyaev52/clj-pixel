(ns pixel-art.onion-skin
  (:require [pixel-art.model.frame :as frame]
            [pixel-art.utils.interceptor :refer [on-changes]]
            [re-frame.core :as re-frame]
            [re-frame.db :as db]))

;; спереди или сзади
;; кол-во

(defn init []
  {:enabled false
   :opacity 0.3})

(defn get-onion-skin-frames-idx [sprite]
  (let [{:keys [current-frame-idx frames]} sprite
        prev-idx (let [idx (dec current-frame-idx)]
                   (when (>= idx 0) idx))
        next-idx (let [idx (inc current-frame-idx)]
                   (when (<= idx (dec (count frames))) idx))]
    (set (keep identity [prev-idx next-idx]))))

(re-frame/reg-event-fx
 ::set-enabled
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc-in db [:onion-skin :enabled] enabled)
    :fx (if enabled
          [[:draw-onion-skin {:sprite (:sprite db) :opacity (-> db :onion-skin :opacity)}]]
          [[:hide-onion-skin]])}))

(re-frame/reg-event-fx
 ::set-opacity
 (fn [{:keys [db]} [_ opacity]]
   {:db (assoc-in db [:onion-skin :opacity] opacity)
    :fx (when (-> db :onion-skin :enabled)
          [[:draw-onion-skin {:sprite (:sprite db) :opacity opacity}]])}))

(re-frame/reg-global-interceptor
 (on-changes
  :redraw-onion-skin-on-sprite-change
  #(-> % :sprite)
  (fn [{:keys [db old new]}]
    (if (-> db :onion-skin :enabled)
      (let [need-redraw (if (not= (:current-frame-idx old) (:current-frame-idx new))
                          true
                          (let [old-frames-idx (get-onion-skin-frames-idx old)
                                new-frames-idx (get-onion-skin-frames-idx new)]
                            (if (not= old-frames-idx new-frames-idx)
                              true
                              (->> (range 0 (max (count old-frames-idx) (count new-frames-idx))) ;; например, move или delete
                                   (some (fn [idx]
                                           (not (identical? (nth (:frames old) idx)
                                                            (nth (:frames new) idx)))))))))]
        {:db db
         :fx (when need-redraw
               [[:draw-onion-skin {:sprite new :opacity (-> db :onion-skin :opacity)}]])})
      {:db db}))))

(re-frame/reg-fx
 :draw-onion-skin
 (fn [{:keys [sprite opacity]}]
   (let [onion-frames-idx (get-onion-skin-frames-idx sprite)
         frames (map (fn [idx] (nth (:frames sprite) idx)) onion-frames-idx)

         db @db/app-db
         canvas (. js/document (getElementById "onion-skin"))
         size (:size sprite)
         ctx (. canvas (getContext "2d"))
         scale (:scale db)]
     (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))
     (.save ctx)
     (set! (.-globalAlpha ctx) opacity)
     (doseq [frame frames]
       (doseq [x (range 0 (:width size))
               y (range 0 (:height size))]
         (when-let [color (frame/get-pixel {:x x :y y} frame)]
           (set! (. ctx -fillStyle) color)
           (. ctx (fillRect (* x scale) (* y scale) scale scale)))))
     (.restore ctx))))

(re-frame/reg-fx
 :hide-onion-skin
 (fn []
   (let [canvas (. js/document (getElementById "onion-skin"))
         ctx (. canvas (getContext "2d"))]
     (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height))))))
