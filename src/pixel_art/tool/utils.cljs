(ns pixel-art.tool.utils
  (:require
   [pixel-art.history :as history]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.utils.geometry :as geometry]
   [sc.api]
   [pixel-art.model.preview :as preview]))

(defn get-tool-options [db]
  (get (db :tools-options) (-> db :tool :type)))

;; todo: db utils
(defn get-current-cel [db]
  (-> db :sprite sprite/get-current-cel))

(defn get-preview-from-current-cel [db]
  (let [size (-> db :sprite :size)
        pixels (:pixels (get-current-cel db))]
    (preview/create size pixels)))

(defn get-preview-or-create-from-current-cel [db]
  (or (when-let [preview (:preview db)]
        (preview/create (-> db :sprite :size) preview))
      (get-preview-from-current-cel db)))

(defn get-empty-visual-effects [db]
  (let [size (-> db :sprite :size)]
    (js/Uint32Array. (* (:width size) (:height size)))))

(defn get-current-color-type [right-button]
  (if right-button :secondary-color :primary-color))

(defn get-current-color [db event]
  ((get-current-color-type (:right-button event)) db))

(defn commit-preview-and-init-tool [db preview tool-init]
  (-> {:db (-> db
               (assoc :preview nil)
               (assoc :visual-effects nil)
               ((fn [db]
                  (let [preview-vec (preview/->vec preview)
                        current-pixels (-> (get-current-cel db) :pixels vec)]
                    (if (and (seq preview-vec) (not= preview-vec current-pixels))
                      (-> db
                          (update :sprite #(sprite/set-current-cel-pixels preview %))
                          history/save-sprite)
                      db)))))}
      (assoc-in [:db :tool] tool-init)))

;; Resize the pixel at {col, row} for the provided size. Will return the array of pixels centered
;; * around the original pixel, forming a pixel square of side=size
(defn resize-pixel [point size]
  (for [j (range 0 size)
        i (range 0 size)]
    #js [(+ (- (aget point 0) (. js/Math (floor (/ size 2)))) i)
         (+ (- (aget point 1) (. js/Math (floor (/ size 2)))) j)]))

(defn with-highlight-cel-under-cursor [events-handlers]
  (merge
   events-handlers
   {:mouse-move
    (fn [db event]
      (let [handler-res (if-let [f (:mouse-move events-handlers)]
                          (f db event)
                          {:db db})]
        (if-not (:initial-mouse-down-pos db)
          (let [{:keys [pixel-size]} (get-tool-options db)
                points (if pixel-size
                         (resize-pixel (:pos event) pixel-size)
                         [(:pos event)])
                size (-> db :sprite :size)

                visual-effects (get-empty-visual-effects db)
                current-cel (get-current-cel db)]
            (doseq [pos points]
              (let [color (color/get-highlight-color (cel/get-pixel pos current-cel))]
                (aset visual-effects (geometry/pos->idx (:x pos) (:y pos) size) color)))
            {:db (-> (or (:db handler-res) db)
                     (assoc :visual-effects visual-effects))})
          {:db db})))
    :mouse-down
    (fn [db event]
      (let [handler-res (when-let [f (:mouse-down events-handlers)]
                          (f db event))]
        {:db (-> (or (:db handler-res) db)
                 (assoc :visual-effects nil))}))}))
