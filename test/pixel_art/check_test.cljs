(ns pixel-art.check-test
  (:require
   ["@testing-library/react" :as rtl]
   ["@testing-library/user-event$default" :as user-event2]
   [cljs.test :refer [deftest run-tests test-vars is]]
   [pixel-art.drawing.events :as drawing.events]
   [pixel-art.events :as e]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.frame :as frame]
   [pixel-art.model.layer :as layer]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.views :as views]
   [pjstadig.humane-test-output]
   [re-frame.core :as re-frame]
   [re-frame.db :as db]
   [reagent.core :as r])
  (:require-macros [pixel-art.promise2 :refer [async-> wait-is promise+]]
                   [shadow.cljs.modern :refer [js-await]]))

;; todo: тест pixel size везде
;; test selection
;; todo: cut selection

(defn run-test [t]
  (.clear js/console)
  (.. (test-vars [t])
      (then (fn []
              (println "done")))
      (catch (fn [e]
               (println "error")))))

(defn delay-p [ms]
  (js/Promise. (fn [r]
                 (js/setTimeout r ms))))

(defn get-sprite
  ([sprite-size]
   (sprite/create {:size sprite-size
                   :layer (layer/create "Layer 1")
                   :frame (frame/create 100)
                   :cel (->> (cel/create sprite-size))
                   :title "Untitled"}))
  ([sprite-size pixels-map]
   (sprite/create {:size sprite-size
                   :layer (layer/create "Layer 1")
                   :frame (frame/create 100)
                   :cel (->> (cel/create sprite-size)
                             (cel/set-pixels-map pixels-map))
                   :title "Untitled"})))


(defn matrix->sprite [m]
  (let [size {:width (count (first m)) :height (count m)}]
    (sprite/create {:size size
                    :layer (layer/create "Layer 1")
                    :frame (frame/create 100)
                    :cel (->> (cel/create size)
                              (cel/set-pixels (js/Uint32Array. (flatten m))))
                    :title "Untitled"})))

(def initial-palettes
  [{:name "default"
    :current true
    :colors [(color/int 0 0 0) (color/int 255 0 0) (color/int 0 0 255) (color/int 0 128 0)]}
   {:name "palette1"
    :current false
    :colors [(color/int 0 0 0)
             (color/int 255 0 0)
             (color/int 0 0 255)
             (color/int 0 128 0)]}])

(defn mount [settings]
  (rtl/cleanup)
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [::e/start-app settings])
  (rtl/render (r/as-element [views/app])))

(defn mouse-down [pos]
  (re-frame/dispatch-sync [::drawing.events/handle-mouse-event :mouse-down pos (:right-button pos)]))

(defn mouse-move [poses]
  (when (seq poses)
    (doseq [pos poses]
      (re-frame/dispatch-sync [::drawing.events/handle-mouse-event :mouse-move pos (:right-button pos)]))))

(defn mouse-up [pos]
  (re-frame/dispatch-sync [::drawing.events/handle-mouse-event :mouse-up pos (:right-button pos)]))

;; todo: rename
(defn mouse-down->move->up [poses]
  (let [mouse-down-pos (first poses)
        mouse-move-poses (when (> (count poses) 1)
                           (subvec poses 1 (count poses)))
        mouse-up-pos (last poses)]
    (promise+
     (mouse-down mouse-down-pos)
     (mouse-move mouse-move-poses)
     (mouse-up mouse-up-pos))))

(defn current-layer->pixels-matrix []
  (let [canvas (. js/document (getElementById "current-layer"))
        size {:width (.-width canvas) :height (.-height canvas)}
        image-data (.. canvas (getContext "2d") (getImageData 0 0 (:width size) (:height size)))
        pixels (vec (js/Uint32Array. (. (.. image-data -data) -buffer)))]
    (->> pixels
         (partition (:width size)))))

(defn get-canvas->pixels [id]
  (let [canvas (. js/document (getElementById id))
        size {:width (.-width canvas) :height (.-height canvas)}
        image-data (.. canvas (getContext "2d") (getImageData 0 0 (:width size) (:height size)))
        pixels (vec (js/Uint32Array. (. (.. image-data -data) -buffer)))]
    (->> pixels
         (partition (:width size)))))

;l;---------------------------------------------------------------

(def p (color/int 0 0 0)) ;; default primary color
(def s (color/int 255 0 0)) ;; default secondary color
(def t color/transparent-color-int)

(def visual-effects-without-highlight
  [[t t t t]
   [t t t t]
   [t t t t]
   [t t t t]])

(defn run-drawing-tool [{:keys [sprite tool-type options mouse-points expected]}]
  (async->
   (mount {:sprite sprite
           :tool-type tool-type
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))
   (. js/Promise
      (all (or
            (when (seq options)
              (->
               (mapv (fn [opt]
                       (cond
                         (boolean? (:value opt))
                         (let [option-input (. rtl/screen (getByTestId (name (:field opt))))]
                           (. rtl/fireEvent (click option-input))
                           (rtl/waitFor (fn []
                                          (when-not (.-checked (. rtl/screen (getByTestId (name (:field opt)))))
                                            (throw "expected checked")))))
                         (number? (:value opt))
                         (re-frame/dispatch-sync [::e/change-tool-option :pixel-size (:value opt)])))
                     options)))
            [])))
   (mouse-down->move->up mouse-points)
   (wait-is (= expected (current-layer->pixels-matrix)))))

#_(promise+ (. rtl/fireEvent (click (rtl/screen.getByTestId "same-color")))
            (rtl/waitFor (fn []
                           (println (.-checked (rtl/screen.getByTestId "same-color"))))))

;;todo для simple-pen один набор тестов?

(deftest pen-tool
  (run-drawing-tool {:sprite (get-sprite {:width 4 :height 4})
                     :tool-type :pen
                     :mouse-points [{:x 0 :y 0}
                                    {:x 1 :y 1}
                                    {:x 2 :y 2}
                                    {:x 3 :y 3}]
                     :expected [[p t t t]
                                [t p t t]
                                [t t p t]
                                [t t t p]]}))

(deftest pen-tool-fast-move
  (run-drawing-tool {:sprite (get-sprite {:width 4 :height 4})
                     :tool-type :pen
                     :mouse-points [{:x 0 :y 0}
                                    {:x 3 :y 3}]
                     :expected [[p t t t]
                                [t p t t]
                                [t t p t]
                                [t t t p]]}))

(deftest pen-pixel-size
  (run-drawing-tool {:sprite (get-sprite {:width 4 :height 4})
                     :tool-type :pen
                     :mouse-points [{:x 1 :y 1}]
                     :options [{:field :pixel-size
                                :value 2}]
                     :expected [[p p t t]
                                [p p t t]
                                [t t t t]
                                [t t t t]]}))

(deftest no-hightlight-when-drawing
  (async->
   (mount {:sprite (get-sprite {:width 2 :height 2})
           :tool-type :pen
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))
   (mouse-move [{:x 0 :y 0}])
   (wait-is (= [[color/highlight-light-color t] [t t]] (get-canvas->pixels "visual-effects"))
            "highlight on mouse move")
   (mouse-down {:x 0 :y 0})
   (wait-is (= [[t t] [t t]] (get-canvas->pixels "visual-effects"))
            "no highlight on mouse down")
   (mouse-move [{:x 1 :y 0}])
   (wait-is (= [[t t] [t t]] (get-canvas->pixels "visual-effects"))
            "no highlight on mouse down -> mouse move")))

(deftest eraser-tool
  (run-drawing-tool {:sprite (matrix->sprite [[s p p]
                                              [t p t]
                                              [s s s]])
                     :tool-type :eraser
                     :mouse-points [{:x 1 :y 0}
                                    {:x 1 :y 1}]
                     :expected [[s t p]
                                [t t t]
                                [s s s]]}))

(deftest bucket-tool
  (run-drawing-tool {:sprite (matrix->sprite [[s t t]
                                              [t s t]
                                              [t t s]])
                     :tool-type :bucket
                     :mouse-points [{:x 0 :y 1}]
                     :expected [[s t t]
                                [p s t]
                                [p p s]]}))

(deftest bucket-tool-with-all-the-same-color-option
  (run-drawing-tool {:sprite (matrix->sprite [[s t t]
                                              [t s t]
                                              [t t s]])
                     :tool-type :bucket
                     :options [{:field :same-color
                                :value true}]
                     :mouse-points [{:x 0 :y 1}]
                     :expected [[s p p]
                                [p s p]
                                [p p s]]}))

(deftest color-picker-test
  (let [new-primary-color (color/int 100 0 0)
        new-secondary-color (color/int 0 0 100)]
    (async->
     (mount {:sprite (matrix->sprite [[new-primary-color new-secondary-color]
                                      [t t]])
             :tool-type :color-picker
             :palettes initial-palettes
             :primary-color p
             :secondary-color s})
     (. rtl/screen (findByTestId "ready"))
     (mouse-down->move->up [{:x 0 :y 0}])
     (delay-p 50)
     (wait-is (= new-primary-color (:primary-color @db/app-db)))
     (wait-is (= s (:secondary-color @db/app-db)))

     (mouse-down->move->up [{:x 1 :y 0 :right-button true}])
     (delay-p 50)
     (wait-is (= new-primary-color (:primary-color @db/app-db)))
     (wait-is (= new-secondary-color (:secondary-color @db/app-db))))))

(deftest rectangle-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]])
                     :tool-type :rectangle
                     :mouse-points [{:x 1 :y 1}
                                    {:x 2 :y 2}
                                    {:x 3 :y 3}
                                    {:x 3 :y 4}]
                     :expected [[t t t t t]
                                [t p p p t]
                                [t p t p t]
                                [t p t p t]
                                [t p p p t]]}))

(deftest rectangle-with-fill-option-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]])
                     :tool-type :rectangle
                     :options [{:field :fill
                                :value true}]
                     :mouse-points [{:x 1 :y 1}
                                    {:x 3 :y 4}]
                     :expected [[t t t t t]
                                [t p p p t]
                                [t p p p t]
                                [t p p p t]
                                [t p p p t]]}))

(deftest rectangle-with-keep-rotation-option-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]])
                     :tool-type :rectangle
                     :options [{:field :keep-ratio
                                :value true}]
                     :mouse-points [{:x 1 :y 1}
                                    {:x 3 :y 4}]
                     :expected [[t t t t t]
                                [t p p p t]
                                [t p t p t]
                                [t p p p t]
                                [t t t t t]]}))

(deftest circle-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]])
                     :tool-type :circle
                     :mouse-points [{:x 1 :y 1}
                                    {:x 2 :y 2}
                                    {:x 3 :y 3}
                                    {:x 4 :y 4}
                                    {:x 4 :y 5}]
                     :expected [[t t t t t t]
                                [t t p p t t]
                                [t p t t p t]
                                [t p t t p t]
                                [t p t t p t]
                                [t t p p t t]]}))

(deftest circle-with-keep-ration-option-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]
                                              [t t t t t t]])
                     :tool-type :circle
                     :options [{:field :keep-ratio
                                :value true}]
                     :mouse-points [{:x 1 :y 1}
                                    {:x 4 :y 5}]
                     :expected [[t t t t t t]
                                [t t p p t t]
                                [t p t t p t]
                                [t p t t p t]
                                [t t p p t t]
                                [t t t t t t]]}))

(deftest line-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]])
                     :tool-type :line
                     :mouse-points [{:x 1 :y 1}
                                    {:x 2 :y 2}
                                    {:x 3 :y 3}
                                    {:x 3 :y 4}]
                     :expected [[t t t t t]
                                [t p t t t]
                                [t t p t t]
                                [t t p t t]
                                [t t t p t]]}))

(deftest line-with-straight-option-test
  (run-drawing-tool {:sprite (matrix->sprite [[t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]
                                              [t t t t t]])
                     :tool-type :line
                     :options [{:field :straight
                                :value true}]
                     :mouse-points [{:x 1 :y 1}
                                    {:x 3 :y 4}]
                     :expected [[t t t t t]
                                [t p t t t]
                                [t t p t t]
                                [t t t p t]
                                [t t t t p]]}))

(deftest shading-test
  (run-drawing-tool {:sprite (matrix->sprite [[s p s]
                                              [s p t]])
                     :tool-type :shading
                     :mouse-points [{:x 0 :y 0}
                                    {:x 0 :y 1}
                                    {:x 1 :y 1}
                                    {:x 2 :y 1}]
                     :expected [[4278190304 p s]
                                [4278190304 p t]]}))

(deftest shading-with-lighten-option-test
  (run-drawing-tool {:sprite (matrix->sprite [[s p s]
                                              [s p t]])
                     :tool-type :shading
                     :options [{:field :lighten
                                :value true}]
                     :mouse-points [{:x 0 :y 0}
                                    {:x 1 :y 0}
                                    {:x 1 :y 1}
                                    {:x 2 :y 1}]
                     :expected [[4280229887 4279176975 s]
                                [4278190335 4279176975 t]]}))

(def selection-pixels
  [[p p t t]
   [p p t t]
   [t t s s]
   [t t s s]])

(deftest rectangle-selection-make-selection
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 1}])
   (delay-p 50)
   (wait-is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
   (wait-is (= [[color/highlight-light-color color/highlight-light-color t t]
                [color/highlight-light-color color/highlight-light-color t t]
                [t t t t]
                [t t t t]]
               (get-canvas->pixels "visual-effects"))
            "highlight selection")))

(deftest rectangle-selection-no-changes-drop-selection
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 1}])
   (mouse-down {:x 3 :y 3})
   (delay-p 50)
   (wait-is (= selection-pixels (current-layer->pixels-matrix)))
   (wait-is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects")))))

(deftest rectangle-selection-move-selection-and-then-up
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 2 :y 1}])
   (mouse-down {:x 0 :y 0})
   (delay-p 50)
   (wait-is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
   (wait-is (= [[color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                [t t t t]
                [t t t t]]
               (get-canvas->pixels "visual-effects")))

   (mouse-move [{:x 0 :y 1}])
   (delay-p 100)
   (wait-is (= [[t t t t]
                [p p t t]
                [p p s s]
                [t t s s]]
               (get-canvas->pixels "current-layer")))
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "selection is not highlighted")

   (mouse-up {:x 0 :y 1})
   (delay-p 10)
   (wait-is (= [[t t t t]
                [p p t t]
                [p p s s]
                [t t s s]]
               (get-canvas->pixels "current-layer")))
   (wait-is (= [[t t t t]
                [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                [t t t t]]
               (get-canvas->pixels "visual-effects"))
            "moved selection is highlighted")

   ;; check overlap
   (mouse-down {:x 1 :y 1})
   (mouse-move [{:x 2 :y 1}])
   (delay-p 10)
   (wait-is (= [[t t t t]
                [t p p t]
                [t p p s]
                [t t s s]]
               (get-canvas->pixels "current-layer"))
            "overlap on mouse move")

   (mouse-up {:x 2 :y 1})
   (delay-p 10)
   (wait-is (= [[t t t t]
                [t p p t]
                [t p p s]
                [t t s s]]
               (get-canvas->pixels "current-layer"))
            "overlap after mouse up")

   ;; check that after moving selection and mouse up changes are commited
   (mouse-down {:x 2 :y 1})
   (mouse-move [{:x 2 :y 0}])
   (mouse-up {:x 2 :y 0})
   (mouse-down {:x 0 :y 0})
   (delay-p 10)
   (wait-is (= [[t p p t]
                [t p p t]
                [t t s s]
                [t t s s]]
               (get-canvas->pixels "current-layer"))
            "changes are commited")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "no highlight")
   (wait-is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")))

(deftest delete-selection-test
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))

   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 1}])
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 2 :y 2}])
   (delay-p 50)
   (. rtl/fireEvent (keyDown js/document #js {"key" "Backspace" "code" "Backspace" "keyCode" 8}))
   (delay-p 50)
   (wait-is (= [[t t t t]
                [t t t t]
                [t t s s]
                [t t s s]]
               (current-layer->pixels-matrix))
            "selection is removed from current-layer")
   (wait-is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "highlight selection")))

(deftest copy-past-selection-test
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))

   ;; make selection
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 1}])
   ;; move it
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 0}])
   (delay-p 50)
   (. rtl/fireEvent (keyDown js/document #js {"key" "c" "code" "KeyC" "keyCode" 67 "ctrlKey" true}))
   (delay-p 50)
   (wait-is (= [[t p p t]
                [t p p t]
                [t t s s]
                [t t s s]]
               (current-layer->pixels-matrix))
            "current-layer is changed")
   (wait-is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "no highlight selection")

   (. rtl/fireEvent (keyDown js/document #js {"key" "v" "code" "KeyV" "keyCode" 86 "ctrlKey" true}))
   (delay-p 50)
   (wait-is (= [[t p p t]
                [t p p t]
                [t t s s]
                [t t s s]]
               (current-layer->pixels-matrix))
            "copied selection is pasted in the same place")
   (wait-is (= 1 (-> @db/app-db :history :current-idx)) "no changes are commited")
   (wait-is (= [[t color/highlight-light-color color/highlight-light-color t]
                [t color/highlight-light-color color/highlight-light-color t]
                [t t t t]
                [t t t t]] (get-canvas->pixels "visual-effects"))
            "selection is highlighted")

   ;; check that underlaying pixels are not changed after past action
   (mouse-down->move->up [{:x 1 :y 0}
                          {:x 1 :y 1}])
   (delay-p 50)
   (wait-is (= [[t p p t]
                [t p p t]
                [t p p s]
                [t t s s]]
               (current-layer->pixels-matrix))
            "underlaying pixels are not changed")
   (wait-is (= [[t t t t]
                [t color/highlight-light-color color/highlight-light-color t]
                [t color/highlight-light-color color/highlight-light-color t]
                [t t t t]] (get-canvas->pixels "visual-effects"))
            "selection is highlighted")

   ;; commit selection
   (mouse-down->move->up [{:x 0 :y 0}])
   (delay-p 50)
   (wait-is (= [[t p p t]
                [t p p t]
                [t p p s]
                [t t s s]]
               (current-layer->pixels-matrix))
            "changes are commited")
   (wait-is (= 2 (-> @db/app-db :history :current-idx)) "changes are commited")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "selection is highlighted")))

(deftest copy-past-delete-selection
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :rectangle-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))

   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 1}])
   (delay-p 50)
   (. rtl/fireEvent (keyDown js/document #js {"key" "c" "code" "KeyC" "keyCode" 67 "ctrlKey" true}))
   (delay-p 50)
   (. rtl/fireEvent (keyDown js/document #js {"key" "v" "code" "KeyV" "keyCode" 86 "ctrlKey" true}))
   (delay-p 50)
   (mouse-down->move->up [{:x 0 :y 0}
                          {:x 1 :y 0}])
   (. rtl/fireEvent (keyDown js/document #js {"key" "Backspace" "code" "Backspace" "keyCode" 8}))
   (delay-p 50)
   (wait-is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
   (wait-is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
            "highlight selection")
   (wait-is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")))

(deftest shape-selection-make-selection
  (async->
   (mount {:sprite (matrix->sprite selection-pixels)
           :tool-type :shape-selection
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))

   (mouse-down->move->up [{:x 0 :y 0}])
   (delay-p 50)
   (wait-is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
   (wait-is (= [[color/highlight-light-color color/highlight-light-color t t]
                [color/highlight-light-color color/highlight-light-color t t]
                [t t t t]
                [t t t t]]
               (get-canvas->pixels "visual-effects"))
            "highlight selection")))

(deftest draw-outside-current-layer
  (async->
   (mount {:sprite (get-sprite {:width 2 :height 2})
           :tool-type :pen
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (. rtl/screen (findByTestId "ready"))

   (mouse-down->move->up [{:x -1 :y -1}])
   (delay-p 50)
   (wait-is (= [[t t] [t t]] (current-layer->pixels-matrix)))
   (wait-is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")
   (wait-is (= [[t t] [t t]] (get-canvas->pixels "visual-effects")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-database []
  (. js/Promise
     (resolve
      (fn [resolve reject]
        (let [req (. js/indexedDB (deleteDatabase "pixel-database"))]
          (set! (. req -onsuccess) resolve)
          (set! (. req -onerror) reject))))))

(def registered-dispatch-interval (atom {}))
(re-frame/reg-fx
 :dispatch-interval
 (fn [{:keys [:dispatch :ms :id]}]
   (swap! registered-dispatch-interval assoc id dispatch)))

#_(deftest backup-for-new-project
    (async done
           (.. (remove-database)
               (then (fn []
                       (mount {:sprite (get-sprite {:width 2 :height 2})
                               :tool-type :pen
                               :palettes initial-palettes
                               :primary-color p
                               :secondary-color s})))
               (then (fn []
                       (. rtl/screen (findByTestId "ready")))))))

#_(deftest no-backup-when-no-changes
    (mount {:sprite (get-sprite {:width 2 :height 2})
            :tool-type :pen
            :palettes initial-palettes
            :primary-color p
            :secondary-color s})
    (async done
           (.. (. rtl/screen (findByTestId "ready"))
               (then (fn []
                       ;; дернуть бекап
                       ;; первый раз сохранится
                       ;; дернуть бекап
                       ;; проверить что ничего не сохранилось
                       )))))

#_(deftest backup-when-changes-exists
    (mount {:sprite (get-sprite {:width 2 :height 2})
            :tool-type :pen
            :palettes initial-palettes
            :primary-color p
            :secondary-color s})
    (async done
           (.. (. rtl/screen (findByTestId "ready"))
               (then (fn []
                       ;; сделать изменения
                       ;; дернуть бекап
                       ;; проверить что ничего не сохранилось
                       )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest resize-test
  (async->
   (mount {:sprite (get-sprite {:width 2 :height 2})
           :tool-type :pen
           :palettes initial-palettes
           :primary-color p
           :secondary-color s})
   (js-await [button (rtl/screen.findByText "Resize canvas")]
             (. rtl/fireEvent (click button)))

   (js-await [width-input (rtl/screen.findByTestId "resize-width")]
             (is (= (.-value width-input) "2")
                 "initial width should be equal sprite width"))

   (js-await
    [width-input (rtl/screen.findByTestId "resize-width")
     height-input (rtl/screen.findByTestId "resize-height")]
    (is (= (.-value width-input) "2")
        "initial width should be equal sprite width")
    (is (= (.-value height-input) "2")
        "initial height should be equal sprite height")

    #_(promise+ (user-event2/type width-input "{selectall}4")
                (user-event2/type height-input "{selectall}5")
                (is (= (.-value width-input) "4")
                    "new width should be equal sprite width")
                (is (= (.-value height-input) "5")
                    "new height should be equal sprite height")))))

(comment
  (do
    (.clear js/console)
    (.. (test-vars [#'pixel-art.check-test/resize-test])
        (then (fn []
                (println "done")))
        (catch (fn [e]
                 (println "error" e))))))
(comment
  (do (.clear js/console)
      (run-tests)))
(comment
  (do (require '[reagent.dom :as rdom])
      (rdom/unmount-component-at-node (.getElementById js/document "app"))
      (. (.getElementById js/document "app") remove)))
