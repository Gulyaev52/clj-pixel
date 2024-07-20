(ns pixel-art.onion-skin
  (:require [pixel-art.canvas :as canvas]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.utils.interceptor :refer [on-changes]]
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

(re-frame/reg-event-fx
 ::set-frames-count
 (fn [{:keys [db]} [_ frames-count]]
   (let [onion-skin (:onion-skin db)]
     {:db (assoc-in db [:onion-skin :frames-count] frames-count)
      :fx (when (:enabled onion-skin)
            [[:draw-onion-skin {:sprite (:sprite db) :opacity (:opacity onion-skin)}]])})))

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

(re-frame/reg-event-fx
 ::set-position
 (fn [{:keys [db]} [_ position]]
   {:db (assoc-in db [:onion-skin :position] position)
    :fx (when (-> db :onion-skin :enabled)
          [[:draw-onion-skin {:sprite (:sprite db) :opacity (-> db :onion-skin :opacity)}]])}))

(re-frame/reg-global-interceptor
 (on-changes
  :redraw-onion-skin-on-sprite-change
  #(-> % :sprite)
  (fn [{:keys [db old new]}]
    (let [onion-skin (:onion-skin db)]
      (if (:enabled onion-skin) ;; todo: refactoring
        (let [need-redraw (if (not= (sprite/get-current-frame old) (sprite/get-current-frame new))
                            true
                            (let [old-frames-idx (get-onion-skin-frames-idx old (:frames-count onion-skin))
                                  new-frames-idx (get-onion-skin-frames-idx new (:frames-count onion-skin))]
                              (if (not= old-frames-idx new-frames-idx)
                                true
                                (->> (range 0 (max (count old-frames-idx) (count new-frames-idx))) ;; например, move или delete
                                     (some (fn [idx]
                                             (not (identical? (nth (:frames old) idx)
                                                              (nth (:frames new) idx)))))))))]
          {:db db
           :fx (when need-redraw
                 [[:draw-onion-skin {:sprite new :opacity (:opacity onion-skin)}]])})
        {:db db})))))

(re-frame/reg-fx
 :draw-onion-skin
 (fn [{:keys [sprite opacity]}]
   (let [canvas (. js/document (getElementById "onion-skin"))
         ctx (. canvas (getContext "2d"))
         db @db/app-db
         onion-frames-idx (get-onion-skin-frames-idx sprite (-> db :onion-skin :frames-count))]
     (. ctx (clearRect 0 0 (. canvas -width) (. canvas -height)))
     (.save ctx)
     (set! (.-globalAlpha ctx) opacity)
     (doseq [frame-idx onion-frames-idx]
       (canvas/draw-frame-on-single-canvas frame-idx sprite canvas))
     (.restore ctx))))

(re-frame/reg-fx
 :hide-onion-skin
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "onion-skin")))))
