(ns pixel-art.check-test
  (:require
   ["@testing-library/react" :as rtl]
   [cljs.test :refer [async deftest is test-vars run-tests]]
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
   [reagent.core :as r]
   [re-frame.db :as db]))

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
  (-> (r/as-element [views/app])
      (rtl/render)))

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
    (mouse-down mouse-down-pos)
    (mouse-move mouse-move-poses)
    (mouse-up mouse-up-pos)))

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
  (mount {:sprite sprite
          :tool-type tool-type
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})

  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (when (seq options)
                       (doseq [opt options]
                         (cond
                           (boolean? (:value opt))
                           (let [option-input (. rtl/screen (getByTestId (name (:field opt))))]
                             (. rtl/fireEvent (click option-input)))
                           (number? (:value opt))
                           (re-frame/dispatch-sync [::e/change-tool-option :pixel-size (:value opt)])))
                       (delay-p 10) ;; todo: replace with wait-for
                       )))
             (then (fn []
                     (mouse-down->move->up mouse-points)
                     (r/flush)
                     (is (= expected (current-layer->pixels-matrix)))
                     (done)))
             (catch (fn [e]
                      (is (nil? e)))))))

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
  (mount {:sprite (get-sprite {:width 2 :height 2})
          :tool-type :pen
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-move [{:x 0 :y 0}])
                     (delay-p 100)))
             (then (fn []
                     (is (= [[color/highlight-light-color t] [t t]] (get-canvas->pixels "visual-effects"))
                         "highlight on mouse move")))
             (then (fn []
                     (mouse-down {:x 0 :y 0})
                     (delay-p 100)))
             (then (fn []
                     (is (= [[t t] [t t]] (get-canvas->pixels "visual-effects"))
                         "no highlight on mouse down")))
             (then (fn []
                     (mouse-move [{:x 1 :y 0}])
                     (delay-p 100)))
             (then (fn []
                     (is (= [[t t] [t t]] (get-canvas->pixels "visual-effects"))
                         "no highlight on mouse down -> mouse move")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

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
    (mount {:sprite (matrix->sprite [[new-primary-color new-secondary-color]
                                     [t t]])
            :tool-type :color-picker
            :palettes initial-palettes
            :primary-color p
            :secondary-color s})

    (async done
           (.. (. rtl/screen (findByTestId "ready"))
               (then (fn []
                       (mouse-down->move->up [{:x 0 :y 0}])
                       (r/flush)
                       (is (= new-primary-color (:primary-color @db/app-db)))
                       (is (= s (:secondary-color @db/app-db)))

                       (mouse-down->move->up [{:x 1 :y 0 :right-button true}])
                       (r/flush)
                       (is (= new-primary-color (:primary-color @db/app-db)))
                       (is (= new-secondary-color (:secondary-color @db/app-db)))

                       (done)))
               (catch (fn [e]
                        (is (nil? e))))))))

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
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 1}])
                     (delay-p 50)))
             (then (fn []
                     (is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
                     (is (= [[color/highlight-light-color color/highlight-light-color t t]
                             [color/highlight-light-color color/highlight-light-color t t]
                             [t t t t]
                             [t t t t]]
                            (get-canvas->pixels "visual-effects"))
                         "highlight selection")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest rectangle-selection-no-changes-drop-selection
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 1}])
                     (mouse-down {:x 3 :y 3})
                     (delay-p 50)))
             (then (fn []
                     (is (= selection-pixels (current-layer->pixels-matrix)))
                     (is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects")))))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest rectangle-selection-move-selection-and-then-up
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 2 :y 1}])
                     (mouse-down {:x 0 :y 0})
                     (delay-p 50)))
             (then (fn []
                     (is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
                     (is (= [[color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                             [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                             [t t t t]
                             [t t t t]]
                            (get-canvas->pixels "visual-effects")))))
             (then (fn []
                     (mouse-move [{:x 0 :y 1}])
                     (delay-p 100)))
             (then (fn []
                     (is (= [[t t t t]
                             [p p t t]
                             [p p s s]
                             [t t s s]]
                            (get-canvas->pixels "current-layer")))
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "selection is not highlighted")))
             (then (fn []
                     (mouse-up {:x 0 :y 1})
                     (delay-p 10)))
             (then (fn []
                     (is (= [[t t t t]
                             [p p t t]
                             [p p s s]
                             [t t s s]]
                            (get-canvas->pixels "current-layer")))
                     (is (= [[t t t t]
                             [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                             [color/highlight-light-color color/highlight-light-color color/highlight-light-color t]
                             [t t t t]]
                            (get-canvas->pixels "visual-effects"))
                         "moved selection is highlighted")))
             (then (fn []
                     ;; check overlap
                     (mouse-down {:x 1 :y 1})
                     (mouse-move [{:x 2 :y 1}])
                     (delay-p 10)))
             (then (fn []
                     (is (= [[t t t t]
                             [t p p t]
                             [t p p s]
                             [t t s s]]
                            (get-canvas->pixels "current-layer"))
                         "overlap on mouse move")))
             (then (fn []
                     (mouse-up {:x 2 :y 1})
                     (delay-p 10)))
             (then (fn []
                     (is (= [[t t t t]
                             [t p p t]
                             [t p p s]
                             [t t s s]]
                            (get-canvas->pixels "current-layer"))
                         "overlap after mouse up")))
             (then (fn []
                     ;; check that after moving selection and mouse up changes are commited
                     (mouse-down {:x 2 :y 1})
                     (mouse-move [{:x 2 :y 0}])
                     (mouse-up {:x 2 :y 0})
                     (mouse-down {:x 0 :y 0})
                     (delay-p 10)))
             (then (fn []
                     (is (= [[t p p t]
                             [t p p t]
                             [t t s s]
                             [t t s s]]
                            (get-canvas->pixels "current-layer"))
                         "changes are commited")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "no highlight")
                     (is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest delete-selection-test
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 1}])
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 2 :y 2}])
                     (delay-p 50)))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "Backspace" "code" "Backspace" "keyCode" 8}))
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t t t t]
                             [t t t t]
                             [t t s s]
                             [t t s s]]
                            (current-layer->pixels-matrix))
                         "selection is removed from current-layer")
                     (is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "highlight selection")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest copy-past-selection-test
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     ;; make selection
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 1}])
                     ;; move it
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 0}])
                     (delay-p 50)))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "c" "code" "KeyC" "keyCode" 67 "ctrlKey" true}))
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t p p t]
                             [t p p t]
                             [t t s s]
                             [t t s s]]
                            (current-layer->pixels-matrix))
                         "current-layer is changed")
                     (is (= 1 (-> @db/app-db :history :current-idx)) "changes are commited")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "no highlight selection")))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "v" "code" "KeyV" "keyCode" 86 "ctrlKey" true}))
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t p p t]
                             [t p p t]
                             [t t s s]
                             [t t s s]]
                            (current-layer->pixels-matrix))
                         "copied selection is pasted in the same place")
                     (is (= 1 (-> @db/app-db :history :current-idx)) "no changes are commited")
                     (is (= [[t color/highlight-light-color color/highlight-light-color t]
                             [t color/highlight-light-color color/highlight-light-color t]
                             [t t t t]
                             [t t t t]] (get-canvas->pixels "visual-effects"))
                         "selection is highlighted")))
             (then (fn []
                     ;; check that underlaying pixels are not changed after past action
                     (mouse-down->move->up [{:x 1 :y 0}
                                            {:x 1 :y 1}])
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t p p t]
                             [t p p t]
                             [t p p s]
                             [t t s s]]
                            (current-layer->pixels-matrix))
                         "underlaying pixels are not changed")
                     (is (= [[t t t t]
                             [t color/highlight-light-color color/highlight-light-color t]
                             [t color/highlight-light-color color/highlight-light-color t]
                             [t t t t]] (get-canvas->pixels "visual-effects"))
                         "selection is highlighted")))
             (then (fn []
                     ;; commit selection
                     (mouse-down->move->up [{:x 0 :y 0}])
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t p p t]
                             [t p p t]
                             [t p p s]
                             [t t s s]]
                            (current-layer->pixels-matrix))
                         "changes are commited")
                     (is (= 2 (-> @db/app-db :history :current-idx)) "changes are commited")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "selection is highlighted")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest copy-past-delete-selection
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :rectangle-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 1}])
                     (delay-p 50)))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "c" "code" "KeyC" "keyCode" 67 "ctrlKey" true}))
                     (delay-p 50)))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "v" "code" "KeyV" "keyCode" 86 "ctrlKey" true}))
                     (delay-p 50)))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}
                                            {:x 1 :y 0}])))
             (then (fn []
                     (. rtl/fireEvent (keyDown js/document #js {"key" "Backspace" "code" "Backspace" "keyCode" 8}))
                     (delay-p 50)))
             (then (fn []
                     (is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
                     (is (= visual-effects-without-highlight (get-canvas->pixels "visual-effects"))
                         "highlight selection")
                     (is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest shape-selection-make-selection
  (mount {:sprite (matrix->sprite selection-pixels)
          :tool-type :shape-selection
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x 0 :y 0}])
                     (delay-p 50)))
             (then (fn []
                     (is (= selection-pixels (current-layer->pixels-matrix)) "current-layer is not changed")
                     (is (= [[color/highlight-light-color color/highlight-light-color t t]
                             [color/highlight-light-color color/highlight-light-color t t]
                             [t t t t]
                             [t t t t]]
                            (get-canvas->pixels "visual-effects"))
                         "highlight selection")))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(deftest draw-outside-current-layer
  (mount {:sprite (get-sprite {:width 2 :height 2})
          :tool-type :pen
          :palettes initial-palettes
          :primary-color p
          :secondary-color s})
  (async done
         (.. (. rtl/screen (findByTestId "ready"))
             (then (fn []
                     (mouse-down->move->up [{:x -1 :y -1}])
                     (delay-p 50)))
             (then (fn []
                     (is (= [[t t] [t t]] (current-layer->pixels-matrix)))
                     (is (= 0 (-> @db/app-db :history :current-idx)) "no changes are commited")
                     (is (= [[t t] [t t]] (get-canvas->pixels "visual-effects")))))
             (then done)
             (catch (fn [e]
                      (is (nil? e)))))))

(comment
  (do
    (.clear js/console)
    (.. (test-vars [#'pixel-art.check-test/rectangle-selection-make-selection])
        (then (fn []
                (println "done")))
        (catch (fn [e]
                 (println "error"))))))
(comment
  (do (.clear js/console)
      (run-tests)))
(comment
  (do (require '[reagent.dom :as rdom])
      (rdom/unmount-component-at-node (.getElementById js/document "app"))
      (. (.getElementById js/document "app") remove)))
