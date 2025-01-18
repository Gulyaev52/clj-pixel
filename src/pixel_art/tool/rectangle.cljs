(ns pixel-art.tool.rectangle
  (:require
   [pixel-art.tool.options-spec :as options-spec]
   [pixel-art.tool.shape :as shape]
   [pixel-art.tool.utils :refer [get-current-color get-tool-options
                                 resize-pixel]]
   [pixel-art.utils.geometry :as geometry]))

(defn- get-filled-rectangle-points [initial-mouse-down-pos event-pos pixel-size]
  (let [ordered-points (geometry/get-ordered-rectangle-points
                        (concat (resize-pixel initial-mouse-down-pos pixel-size)
                                (resize-pixel event-pos pixel-size)))]
    (apply geometry/get-rectange-points (vals ordered-points))))

;; todo: naming; outline, borders, empty
(defn- get-outline-rectangle-points [initial-mouse-down-pos event-pos pixel-size]
  (->> (geometry/get-rectange-border-points initial-mouse-down-pos event-pos)
       (mapcat #(resize-pixel % pixel-size)) ;; todo: optimize
       dedupe))

(defn- draw [db event]
  (let [{:keys [initial-mouse-down-pos]} db
        current-color (get-current-color db event)
        {:keys [pixel-size fill]} (get-tool-options db)
        rectangle-points (if fill
                           (get-filled-rectangle-points initial-mouse-down-pos (:pos event) pixel-size)
                           (get-outline-rectangle-points initial-mouse-down-pos (:pos event) pixel-size))]
    (->> rectangle-points
         (map (fn [p] [p current-color])))))

(def tool
  (shape/make
   {:type :rectangle
    :options-spec [options-spec/pixel-size
                   (options-spec/make-checkbox {:field :fill :label "Fill"})
                   (options-spec/make-checkbox {:field :keep-ratio :label "Keep ration"})]
    :draw draw}))
