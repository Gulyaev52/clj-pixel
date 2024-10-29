(ns pixel-art.project-serialization
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.sprite-resizer :refer [canvas->pixels]]
   [pixel-art.utils.coll :as coll]))
;; todo: rename ns

(defn serialize [project]
  (let [cels (->> (-> project :sprite :cels)
                  (coll/map-matrix
                   (fn [cel]
                       ;; todo: add comment why we save data-url not pixels
                     (let [data-url (canvas/generate-data-url #(canvas/draw-cel cel %) (-> project :sprite :size))]
                       (-> cel
                           (dissoc :pixels :current :selected)
                           (assoc :data-url data-url))))))]
    (-> project
        (assoc-in [:sprite :cels] cels)
        clj->js
        (#(. js/JSON (stringify %))))))

(defn- load-data-url [data-url]
  (js/Promise. (fn [resolve]
                 (let [img (js/Image.)]
                   (set! (. img -onload) (fn [] (resolve img)))
                   (set! (. img -src) data-url)))))

(defn- img->pixels [img size]
  (let [canvas (canvas/create-canvas size)]
    (.. canvas (getContext "2d") (drawImage img 0 0))
    (canvas->pixels canvas size)))

(defn deserialize+ [project-str]
  (when project-str
    (let [parsed-project (-> (. js/JSON (parse project-str))
                             (js->clj :keywordize-keys true))
          sprite-size (-> parsed-project :sprite :size)]
      (.. js/Promise
          (all (->> (-> parsed-project :sprite :cels)
                    (coll/map-matrix (fn [cel pos]
                                       (. (load-data-url (:data-url cel))
                                          (then (fn [img] [pos (img->pixels img sprite-size)])))))
                    flatten))
          (then #(into {} %))
          (then (fn [cels-pixels]
                  (update-in parsed-project
                             [:sprite :cels]
                             #(coll/map-matrix (fn [c pos]
                                                 (-> c
                                                     (assoc :current (= {:x 0 :y 0} pos)
                                                            :selected (= {:x 0 :y 0} pos))
                                                     (dissoc :data-url)
                                                     (assoc :pixels (get cels-pixels pos))))
                                               %))))))))
