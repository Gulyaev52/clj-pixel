(ns pixel-art.sprite-resizer
  (:require [pixel-art.canvas :as canvas]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.utils.coll :as coll]
            [re-frame.core :as re-frame]))

(defn init []
  {:opened false
   :settings {:target-size nil
              :resize-content false
              :anchor {:x :center :y :center}}})

(defn array-data->pixels [array-data size]
  (->> (for [x (range 0 (:width size))
             y (range 0 (:height size))]
         (let [index (* (+ x (* y (:width size))) 4)
               r (aget array-data index)
               g (aget array-data (+ index 1))
               b (aget array-data (+ index 2))
               a (aget array-data (+ index 3))]
           [[x y] ;; todo: remove it
            (when (not= [r g b a] [0 0 0 0])
              (str "rgb(" r "," g "," b ")"))]))
       (sort-by #(-> % first second))
       (map second)))

(defn canvas->cel [canvas]
  (let [size {:width (. canvas -width) :height (. canvas -height)}
        image-data (.. canvas (getContext "2d") (getImageData 0 0 (:width size) (:height size)))]
    (cel/create size (array-data->pixels (.. image-data -data) size))))

(defn translate-x [x width resized-width anchor-x]
  (case anchor-x
    :left x
    :right (- x (- width resized-width))
    :center (- x (. js/Math (round (/ (- width resized-width) 2))))))

(defn translate-y [y height resized-height anchor-y]
  (case anchor-y
    :top y
    :bottom (- y (- height resized-height))
    :center (- y (. js/Math (round (/ (- height resized-height) 2))))))

(defn translate-pos [pos size target-size anchor]
  {:x (translate-x (:x pos) (:width size) (:width target-size) (:x anchor))
   :y (translate-y (:y pos) (:height size) (:height target-size) (:y anchor))})

(defn resize-cel [cel {:keys [target-size resize-content anchor]}]
  (if resize-content
    (->> (canvas/create-canvas (:size cel))
         (canvas/draw-cel cel)
         (canvas/resize target-size)
         canvas->cel)
    (let [translated-pixels-map
          (->> (cel/pixels->coll cel)
               (keep (fn [[pos color]]
                       (when (not= color transparent-color)
                         [(translate-pos pos (:size cel) target-size anchor)
                          color]))))]
      (->> (cel/create target-size)
           (cel/set-pixels translated-pixels-map)))))

(defn resize-sprite [sprite settings]
  (let [resized-cels (coll/map-matrix #(resize-cel %1 settings) (:cels sprite))]
    (assoc sprite
           :cels resized-cels
           :size (:target-size settings))))

(re-frame/reg-event-fx
 ::set-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (-> db
            (assoc-in [:sprite-resizer :opened] opened)
            (assoc-in [:sprite-resizer :settings :target-size] (-> db :sprite :size)))}))

(re-frame/reg-event-fx
 ::set-settings-option
 (fn [{:keys [db]} [_ option value]]
   {:db (assoc-in db [:sprite-resizer :settings option] value)}))

(re-frame/reg-event-fx
 ::resize
 (fn [{:keys [db]}]
   (let [{:keys [sprite] {:keys [settings]} :sprite-resizer} db
         resized-sprite (resize-sprite sprite settings)]
     {:db (assoc db :sprite resized-sprite)})))
