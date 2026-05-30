(ns pixel-art.project-save-load.sprite-serialization ;; todo: rename to project-serialization?
  (:require
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.model.sprite-canvas :as sprite-canvas]
   [pixel-art.utils.coll :as coll]))

(defn serialize [sprite]
  (let [cels (coll/map-matrix
              (fn [cel]
                ;; todo: add comment why we save data-url not pixels
                (let [data-url (canvas/generate-data-url #(sprite-canvas/draw-cel cel %) (:size sprite))]
                  (-> cel
                      (dissoc :pixels :current :selected)
                      (assoc :data-url data-url))))
              (:cels sprite))]
    (assoc sprite :cels cels)))

(defn- load-data-url [data-url]
  (js/Promise. (fn [resolve]
                 (let [img (js/Image.)]
                   (set! (. img -onload) (fn [] (resolve img)))
                   (set! (. img -src) data-url)))))

(defn- img->pixels [img size]
  (let [canvas (canvas/create-canvas size)]
    (.. canvas (getContext "2d") (drawImage img 0 0))
    (canvas/canvas->pixels canvas size)))

(defn deserialize+ [sprite]
  (.. js/Promise
      (all (->> (:cels sprite)
                (coll/map-matrix (fn [cel pos]
                                   (. (load-data-url (:data-url cel))
                                      (then (fn [img] [pos (img->pixels img (:size sprite))])))))
                flatten))
      (then #(into {} %))
      (then (fn [cels-pixels]
              (update sprite
                      :cels
                      #(coll/map-matrix (fn [c pos]
                                          (-> c
                                              (assoc :current (= {:x 0 :y 0} pos)
                                                     :selected (= {:x 0 :y 0} pos))
                                              (dissoc :data-url)
                                              (assoc :pixels (get cels-pixels pos)))) ;; todo: move this creation to cell/create
                                        %))))))
