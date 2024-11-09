(ns pixel-art.events-test
  (:require ["./jszip" :as jszip]
            ["gifuct-js" :as gifuct-js]
            [cljs.test :refer-macros [deftest testing is run-tests] :refer [async]]
            [day8.re-frame.test :as rf-test]
            [pixel-art.canvas :as canvas]
            [pixel-art.events :as events]
            [pixel-art.events.event-collector :as event-collector]
            [pixel-art.export :as export]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :as color]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.palette :as palette]
            [pixel-art.subs :as subs]
            [pixel-art.tool.rectangle-selection :as rectangle-selection]
            [pixel-art.utils.coll :as coll]
            [pjstadig.humane-test-output]
            [re-frame.core :as rf]
            [sc.api]))

(defn wait-for
  ([done pred on-success]
   (wait-for done pred on-success 1))
  ([done pred on-success times]
   (cond
     (> times 10)
     (do (is false "timeout")
         (done))

     (pred)
     (on-success)

     :else
     (js/setTimeout (fn [] (wait-for done pred on-success (inc times))) 100))))

(defn get-active-cel-pixels-with-pos [cel-pos sprite]
  (->> sprite
       (sprite/get-cel cel-pos)
       :pixels
       (map-indexed (fn [idx pixel]
                      (when (not= pixel color/transparent-color-int)
                        [(let [{:keys [x y]} (cel/idx->pos idx (sprite/get-size sprite))]
                           [x y])
                         pixel])))
       (filter some?)))

(def !done-counter (atom 0))
(def done (fn []
            (swap! !done-counter inc)
            (println "done" @!done-counter)))

(defn wait-for-event [done event on-success]
  (wait-for done
            (fn []
              (->> @event-collector/event-store
                   (take 10) ;; todo: refactore it
                   (map first)
                   (some #{event})))
            on-success))

(rf/reg-sub
 :db
 (fn [db]
   db))

(def !last-download-file (atom nil))
(rf/reg-fx
 :download-file
 (fn [file-desc]
   (reset! !last-download-file file-desc)))

(defn create-pixels [size]
  (vec (repeat (* (:width size) (:height size)) color/transparent-color-int)))

(def initial-sprite
  (let [sprite-size {:width 8 :height 8}]
    (sprite/create {:size sprite-size
                    :layer (layer/create "Layer 1")
                    :frame (frame/create 100)
                    :cel (->> (cel/create sprite-size)
                              (cel/set-pixels (->> (for [x (range 0 (:width sprite-size))
                                                         y (range 0 (:height sprite-size))]
                                                     [{:x x :y y} color/transparent-color-int])
                                                   (into {})))
                              (cel/set-pixels
                               {{:x 0 :y 0} (color/int 255 0 0)
                                {:x 0 :y 1} color/transparent-color-int
                                {:x 1 :y 1} (color/int 255 0 0)
                                {:x 3 :y 3} (color/int 255 0 0)
                                {:x 3 :y 4} (color/int 255 0 0)
                                {:x 4 :y 3} (color/int 255 0 0)
                                {:x 4 :y 4} (color/int 255 0 0)}))})))
(def initial-cel-pixels (-> initial-sprite :cels ffirst :pixels))

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
(def initial-current-palette (first initial-palettes))

(defn initialize-db
  ([]
   (rf/dispatch-sync [:initialize-db {:sprite initial-sprite
                                      :palettes initial-palettes
                                      :primary-color (color/int 0 0 0)
                                      :secondary-color (color/int 255 0 0)
                                      :initialized-canvas true}]))
  ([data]
   (rf/dispatch-sync [:initialize-db (merge {:sprite initial-sprite
                                             :palettes initial-palettes
                                             :primary-color (color/int 0 0 0)
                                             :secondary-color (color/int 255 0 0)
                                             :initialized-canvas true}
                                            data)])))

;; mouse-down-move-up
(defn apply-current-tool [poses]
  (let [mouse-down-pos (first poses)
        mouse-move-poses (when (> (count poses) 1)
                           (subvec poses 1 (dec (count poses))))
        mouse-up-pos (last poses)]
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-down mouse-down-pos (:right-button mouse-down-pos)])
    (when (seq mouse-move-poses)
      (doseq [pos mouse-move-poses]
        (rf/dispatch-sync [::events/handle-mouse-event :mouse-move pos (:right-button pos)])))
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-up mouse-up-pos (:right-button mouse-up-pos)])))

(deftest test-add-frame
  (rf-test/run-test-sync
   (testing "add frames"
     (initialize-db)
     (def initial-timeline @(rf/subscribe [::subs/timeline]))
     (rf/dispatch-sync [::events/add-frame])
     (rf/dispatch-sync [::events/add-frame])

     (def timeline @(rf/subscribe [::subs/timeline]))
     (is (= (:layers initial-timeline) (:layers timeline)) "layers should not be changed")
     (is (= [{:duration 100 :current false :idx 0}
             {:duration 100 :current false :idx 1}
             {:duration 100 :current true :idx 2}]
            (:frames timeline))
         "new frame created")
     (is (= [{:pixels (-> initial-timeline :cels first :pixels) :current false :selected false :pos {:frame-idx 0 :layer-idx 0}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current false :selected false :pos {:frame-idx 1 :layer-idx 0}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current true :selected true :pos {:frame-idx 2 :layer-idx 0}}]
            (map #(select-keys % [:current :selected :pos :pixels]) (:cels timeline))))

     (apply-current-tool [{:x 0 :y 0}])
     (is (= (assoc (create-pixels (sprite/get-size initial-sprite)) 0 (color/int 0 0 0))
            (->> @(rf/subscribe [::subs/timeline])
                 :cels
                 (coll/find-first :current)
                 :pixels))
         "current cel should be updated"))))

(deftest test-add-layer
  (rf-test/run-test-sync
   (testing "add layers"
     (def !db (rf/subscribe [:db]))
     (initialize-db)
     (def initial-timeline @(rf/subscribe [::subs/timeline]))
     (rf/dispatch-sync [::events/add-layer])
     (rf/dispatch-sync [::events/add-layer])

     (def timeline @(rf/subscribe [::subs/timeline]))
     (is (= (:frames initial-timeline) (:frames initial-timeline)) "frames should not be changes")
     (is (= [{:visibile? true,
              :automatic-linking? false,
              :name "Layer 1",
              :current false,
              :idx 0}
             {:visibile? true,
              :automatic-linking? false,
              :name "Layer 2",
              :current false,
              :idx 1}
             {:visibile? true,
              :automatic-linking? false,
              :name "Layer 3",
              :current true,
              :idx 2}]
            (:layers timeline))
         "new layer created")
     (is (= [{:pixels (-> initial-timeline :cels first :pixels) :current false :selected false :pos {:frame-idx 0 :layer-idx 0}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current false :selected false :pos {:frame-idx 0 :layer-idx 1}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current true :selected true :pos {:frame-idx 0 :layer-idx 2}}]
            (map #(select-keys % [:current :selected :pos :pixels]) (:cels timeline))))

     (apply-current-tool [{:x 0 :y 0}])
     (is (= (assoc (create-pixels (sprite/get-size initial-sprite)) 0 (color/int 0 0 0))
            (->> @(rf/subscribe [::subs/timeline])
                 :cels
                 (coll/find-first :current)
                 :pixels))
         "current cel should be updated"))))

(deftest test-duplicate-frame
  (rf-test/run-test-sync
   (initialize-db)
   (def initial-db @(rf/subscribe [:db]))
   (def initial-timeline @(rf/subscribe [::subs/timeline]))

   (rf/dispatch-sync [::events/add-layer])
   (rf/dispatch-sync [::events/duplicate-frame])

   (def timeline @(rf/subscribe [::subs/timeline]))
   (is (= [{:duration 100,
            :current false,
            :idx 0}
           {:duration 100, :current true, :idx 1}]
          (:frames timeline)))
   (is (= [{:visibile? true,
            :automatic-linking? false,
            :name "Layer 1",
            :current false,
            :idx 0}
           {:visibile? true,
            :automatic-linking? false,
            :name "Layer 2",
            :current true,
            :idx 1}]
          (:layers timeline)))
   (is (= (nth [{:pos {:frame-idx 0, :layer-idx 0},
                 :current false,
                 :selected false
                 :pixels initial-cel-pixels}
                {:pos {:frame-idx 0, :layer-idx 1},
                 :current false,
                 :selected false
                 :pixels (create-pixels (sprite/get-size initial-sprite))}
                {:pos {:frame-idx 1, :layer-idx 0},
                 :current false,
                 :selected false
                 :pixels initial-cel-pixels}
                {:pos {:frame-idx 1, :layer-idx 1},
                 :current true,
                 :selected true
                 :pixels (create-pixels (sprite/get-size initial-sprite))}] 3)
          (nth (map #(select-keys % [:pos :current :selected :pixels]) (:cels timeline)) 3)))))

(deftest test-remove-frame
  (testing "remove frame"
    (rf-test/run-test-sync
     (initialize-db)
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))

     (rf/dispatch-sync [::events/add-frame])
     (rf/dispatch-sync [::events/remove-frame])

     (is (= (-> initial-db :sprite) (-> @!db :sprite))))))

(deftest test-remove-layer
  (testing "remove layer"
    (rf-test/run-test-sync
     (initialize-db)
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))

     (rf/dispatch-sync [::events/add-layer])
     (rf/dispatch-sync [::events/remove-layer])

     (is (= (-> initial-db :sprite) (-> @!db :sprite)))))

  (testing "remove first layer of 2"
    (rf-test/run-test-sync
     (let [sprite (->> initial-sprite
                       (sprite/add-layer (layer/create "new-layer"))
                       (sprite/select-layer 0))]
       (initialize-db {:sprite sprite})

       (rf/dispatch-sync [::events/remove-layer])

       (let [actual-sprite (:sprite @(rf/subscribe [:db]))]
         (is (= [{:visibile? true,
                  :automatic-linking? false,
                  :name "new-layer"}]
                (:layers actual-sprite)))
         (is (= (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 1} sprite))
                (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 0} actual-sprite)))))))))

(defn create-fixture []
  (initialize-db)
  (def initial-db @(rf/subscribe [:db]))
  (def !db (rf/subscribe [:db]))
  (rf/dispatch-sync [::events/add-frame])
  (rf/dispatch-sync [::events/add-frame])
  (rf/dispatch-sync [::events/add-layer])
  (rf/dispatch-sync [::events/add-layer]))

(defn get-selected-cels-from-timeline []
  (->> @(rf/subscribe [::subs/timeline])
       :cels
       (map #(select-keys % [:pos :current :selected]))
       (filter #(or (:selected %) (:current %)))))

(deftest test-selection
  (testing "select-only-1-cel"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (is (= [{:current true,
             :selected true,
             :pos {:frame-idx 0, :layer-idx 0}}]
           (get-selected-cels-from-timeline)))

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 1}])
    (is (= [{:current true,
             :selected true,
             :pos {:frame-idx 1, :layer-idx 1}}]
           (get-selected-cels-from-timeline))))

  (testing "select-frame"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/select-frame 2])

    (is (= [{:current true,
             :selected true,
             :pos {:frame-idx 2, :layer-idx 0}}]
           (get-selected-cels-from-timeline))))

  (testing "select-layer"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/select-layer 2])

    (is (= [{:current true,
             :selected true,
             :pos {:frame-idx 0, :layer-idx 2}}]
           (get-selected-cels-from-timeline))))

  (testing "toggle-cel-to-selection"
    (testing "add to selection"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])

      (is (= [{:selected true :current false :pos {:frame-idx 0 :layer-idx 0}}
              {:selected true :current true :pos {:frame-idx 1 :layer-idx 1}}]
             (get-selected-cels-from-timeline))))

    (testing "remove from selection"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])

      (is (= [{:selected true :current true :pos {:frame-idx 0 :layer-idx 0}}]
             (get-selected-cels-from-timeline))))

    (testing "when only 1 selected, it should not be removed"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])

      (is (= [{:selected true :current true :pos {:frame-idx 0 :layer-idx 0}}]
             (get-selected-cels-from-timeline))))

    (testing "when remove not current then current should not be changed"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 2 :layer-idx 2}]) ;; current
      (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])

      (is (= [{:pos {:frame-idx 1, :layer-idx 1},
               :current false,
               :selected true}
              {:pos {:frame-idx 2, :layer-idx 2},
               :current true,
               :selected true}]
             (get-selected-cels-from-timeline)))))

  (testing "add-cels-range-to-selection"
    (testing "add range from left to the right"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])

      (is (= [{:pos {:frame-idx 0, :layer-idx 0},
               :current false,
               :selected true}
              {:pos {:frame-idx 0, :layer-idx 1},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 0},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 1},
               :current true,
               :selected true}]
             (get-selected-cels-from-timeline))))

    (testing "add range from right to the left"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 1}])
      (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])

      (is (= [{:pos {:frame-idx 0, :layer-idx 0},
               :current true,
               :selected true}
              {:pos {:frame-idx 0, :layer-idx 1},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 0},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 1},
               :current false,
               :selected true}]
             (get-selected-cels-from-timeline))))

    (testing "when create range for already added then should select it"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
      (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])

      (is (= [{:pos {:frame-idx 0, :layer-idx 0},
               :current true,
               :selected true}
              {:pos {:frame-idx 0, :layer-idx 1},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 0},
               :current false,
               :selected true}
              {:pos {:frame-idx 1, :layer-idx 1},
               :current false,
               :selected true}]
             (get-selected-cels-from-timeline)))))

  (testing "selection after frame removing"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
    (rf/dispatch-sync [::events/remove-frame 1])

    (is (= [{:pos {:frame-idx 0, :layer-idx 0},
             :current false,
             :selected true}
            {:pos {:frame-idx 0, :layer-idx 1},
             :current true,
             :selected true}]
           (get-selected-cels-from-timeline))))

  (testing "selection after layer removing"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
    (rf/dispatch-sync [::events/remove-layer 1])

    (is (= [{:pos {:frame-idx 0, :layer-idx 0},
             :current false,
             :selected true}
            {:pos {:frame-idx 1, :layer-idx 0},
             :current true,
             :selected true}]
           (get-selected-cels-from-timeline)))))

(deftest test-links
  (defn create-fixture []
    (initialize-db)
    (def initial-db @(rf/subscribe [:db]))
    (def !db (rf/subscribe [:db]))
    (rf/dispatch-sync [::events/add-frame])
    (rf/dispatch-sync [::events/add-frame])
    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/add-layer]))

  (testing "create group when cels are not in groups and replace cels by main cel"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 0}])

    (is (= [{:pixels initial-cel-pixels :pos {:frame-idx 0 :layer-idx 0} :current false :selected true :group-number 0}
            {:pixels initial-cel-pixels :pos {:frame-idx 1 :layer-idx 0} :current true :selected true :group-number 0}]
           (->> @(rf/subscribe [::subs/timeline])
                :cels
                (filter :group-number)
                (map #(select-keys % [:pixels :pos :current :selected :group-number])))))

    ;; different groups on the same layer
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 2 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 2 :layer-idx 0}])
    (is (= [{:pos {:frame-idx 0, :layer-idx 0},
             :current false,
             :selected false,
             :group-number 0}
            {:pos {:frame-idx 1, :layer-idx 0},
             :current false,
             :selected false,
             :group-number 0}
            {:pos {:frame-idx 2, :layer-idx 0},
             :current true,
             :selected true,
             :group-number 1}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter :group-number)
                (map #(select-keys % [:pos :current :selected :group-number])))))

    ;; on each layer group is the same

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 1 :layer-idx 1}])

    (is (= [{:pos {:frame-idx 0 :layer-idx 1} :current false :selected true :group-number 0}
            {:pos {:frame-idx 1 :layer-idx 1} :current true :selected true :group-number 0}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter #(and (:group-number %) (= (-> % :pos :layer-idx) 1)))
                (map #(select-keys % [:pos :current :selected :group-number])))))

    ;; add to existed group
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 2 :layer-idx 1}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 1 :layer-idx 1}])
    (is (= [{:pos {:frame-idx 0 :layer-idx 1} :current false :selected false :group-number 0}
            {:pos {:frame-idx 1 :layer-idx 1} :current true :selected true :group-number 0}
            {:pos {:frame-idx 2, :layer-idx 1},
             :current false,
             :selected true,
             :group-number 0}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter #(and (:group-number %) (= (-> % :pos :layer-idx) 1)))
                (map #(select-keys % [:pos :current :selected :group-number]))))))

  (testing "ignore layers that doesn't belong to the layer of main cel"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 1}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 1}])

    (is (= [{:pos {:frame-idx 0 :layer-idx 1} :current true :selected true :group-number 0}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter :group-number)
                (map #(select-keys % [:pos :current :selected :group-number]))))))

  (testing "remove from one group and move to another"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 1 :layer-idx 0}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 1 :layer-idx 0}])

    (is (= [{:pos {:frame-idx 0 :layer-idx 0} :current true :selected true :group-number 1}
            {:pos {:frame-idx 1 :layer-idx 0} :current false :selected true :group-number 1}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter :group-number)
                (map #(select-keys % [:pos :current :selected :group-number]))))))

  (testing "unlink should work for different groups and layers"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 1}])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 1}])
    (rf/dispatch-sync [::events/unlink-selected-cels])

    (is (= []
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter :group-number)))))

  (testing "update/remove linked cels"
    (create-fixture)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 0}])
    (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 1 :layer-idx 0}])
    (apply-current-tool [{:x 0 :y 0}])

    (is (= [{:pos {:frame-idx 0 :layer-idx 0} :pixels (-> (create-pixels (sprite/get-size initial-sprite))
                                                          (assoc 0 (color/int 0 0 0)))}
            {:pos {:frame-idx 1 :layer-idx 0} :pixels (-> (create-pixels (sprite/get-size initial-sprite))
                                                          (assoc 0 (color/int 0 0 0)))}]
           (->> (:cels @(rf/subscribe [::subs/timeline]))
                (filter :group-number)
                (map #(select-keys % [:pos :pixels]))))))

  (testing "autolinking"
    (testing "for cel without group should create a new group but for cel with group should add there"
      (create-fixture)

      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
      (rf/dispatch-sync [::events/toggle-layer-automatic-linking 0])
      (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
      (rf/dispatch-sync [::events/link-selected-cels {:frame-idx 0 :layer-idx 1}])
      (rf/dispatch-sync [::events/toggle-layer-automatic-linking 1])
      (rf/dispatch-sync [::events/add-frame])

      (is (= [{:pos {:frame-idx 0, :layer-idx 0},
               :group-number 0
               :current false
               :selected false}
              {:pos {:frame-idx 0, :layer-idx 1},
               :group-number 0
               :current false
               :selected false}
              {:pos {:frame-idx 1, :layer-idx 0},
               :group-number 0
               :current false
               :selected false}
              {:pos {:frame-idx 1, :layer-idx 1},
               :group-number 0
               :current true
               :selected true}]
             (->> (:cels @(rf/subscribe [::subs/timeline]))
                  (filter :group-number)
                  (map #(select-keys % [:pos :group-number :current :selected]))))))))

(deftest test-merge-layer-with-below
  (testing "when only 1")
  (testing "when last")
  (testing "when linked")
  (testing "should merge current layer with layer below"
    (initialize-db {:sprite (->> (sprite/clear-cel initial-sprite)
                                 (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 0 255)
                                                                 {:x 0 :y 1} (color/int 0 0 255)
                                                                 {:x 1 :y 0} (color/int 0 0 255)}))})

    (rf/dispatch-sync [::events/add-frame]) ;; todo: а зачем нам так конструировать объект? сложнее же
    (apply-current-tool [{:x 0 :y 0}])

    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
    (apply-current-tool [{:x 0 :y 0} {:x 0 :y 1} {:x 0 :y 2} {:x 1 :y 0} {:x 2 :y 0} {:x 3 :y 0}])

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/merge-layer-with-below])

    (let [sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [{:duration 100} {:duration 100}] (:frames sprite)))
      (is (= [{:visibile? true,
               :automatic-linking? false,
               :name "Layer 1"}]
             (:layers sprite)))
      (is (= [[[0 0] (color/int 0 0 255)]
              [[1 0] (color/int 0 0 255)]
              [[2 0] (color/int 0 0 0)]
              [[0 1] (color/int 0 0 255)]
              [[0 2] (color/int 0 0 0)]]
             (get-active-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite)))
      (is (= [[[0 0] (color/int 0 0 0)]]
             (get-active-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite))))))

(deftest test-move-layer-up
  (let [sprite (->> initial-sprite
                    (sprite/add-layer (layer/create "layer 2"))
                    (sprite/add-layer (layer/create "layer 3"))
                    (sprite/select-layer 2))]
    (initialize-db {:sprite sprite})

    (rf/dispatch-sync [::events/move-layer-up])

    (let [actual-sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [{:visibile? true,
               :automatic-linking? false,
               :name "Layer 1"}
              {:visibile? true,
               :automatic-linking? false,
               :name "layer 3"}
              {:visibile? true,
               :automatic-linking? false,
               :name "layer 2"}]
             (:layers actual-sprite)))
      (is (= (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite))
             (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 0} actual-sprite))))
      (is (= (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 2} sprite))
             (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 1} actual-sprite)))))))

(deftest test-move-layer-down
  (let [sprite (->> initial-sprite
                    (sprite/add-layer (layer/create "layer 2"))
                    (sprite/add-layer (layer/create "layer 3"))
                    (sprite/select-layer 0))]
    (initialize-db {:sprite sprite})

    (rf/dispatch-sync [::events/move-layer-down])

    (let [actual-sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [{:visibile? true,
               :automatic-linking? false,
               :name "layer 2"}
              {:visibile? true,
               :automatic-linking? false,
               :name "Layer 1"}
              {:visibile? true,
               :automatic-linking? false,
               :name "layer 3"}]
             (:layers actual-sprite)))
      (is (= (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 1} sprite))
             (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 0} actual-sprite))))
      (is (= (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite))
             (:pixels (sprite/get-cel {:frame-idx 0 :layer-idx 1} actual-sprite)))))))

(deftest test-palettes
  (testing "select primary and secondary color"
    (initialize-db)

    (rf/dispatch-sync [::palette/select-color 2 false])
    (is (= (nth (:colors initial-current-palette) 2) @(rf/subscribe [::subs/primary-color])))
    (is (= (nth (:colors initial-current-palette) 1) @(rf/subscribe [::subs/secondary-color])))

    (rf/dispatch-sync [::palette/select-color 3 true])
    (is (= (nth (:colors initial-current-palette) 2) @(rf/subscribe [::subs/primary-color])))
    (is (= (nth (:colors initial-current-palette) 3) @(rf/subscribe [::subs/secondary-color]))))

  (testing "add color"
    (testing "add new color"
      (initialize-db)
      (let [new-color (color/int 100 100 0)]
        (rf/dispatch-sync [::palette/add-color new-color])
        (is (= new-color (last (:colors @(rf/subscribe [::subs/current-palette])))))))

    (testing "colors are unique"
      (initialize-db)
      (let [initial-selected-palette @(rf/subscribe [::subs/current-palette])
            new-color (color/int 0 0 0)]
        (rf/dispatch-sync [::palette/add-color new-color])
        (is (= new-color @(rf/subscribe [::subs/primary-color])))
        (is (= initial-selected-palette @(rf/subscribe [::subs/current-palette]))))))

  (testing "remove color"
    (initialize-db)
    (let [initial-selected-palette @(rf/subscribe [::subs/current-palette])]
      (rf/dispatch-sync [::palette/remove-color 0])
      (is (= (rest (:colors initial-selected-palette))
             (:colors @(rf/subscribe [::subs/current-palette]))))))

  (testing "select palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/select-palette 1])
    (is (= (assoc (nth initial-palettes 1)
                  :current true)
           @(rf/subscribe [::subs/current-palette]))))

  (testing "remove selected palette"
    (initialize-db)
    (let [current-second-palette (assoc (nth initial-palettes 1) :current true)]
      (rf/dispatch-sync [::palette/remove-selected-palette])
      (is (= current-second-palette
             @(rf/subscribe [::subs/current-palette])))
      (is (= [current-second-palette]
             @(rf/subscribe [::subs/palettes])))))

  (testing "create palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/create-palette "new-palette"])
    (is (= (conj (assoc-in initial-palettes [0 :current] false)
                 {:name "new-palette"
                  :current true
                  :colors []})
           @(rf/subscribe [::subs/palettes])))
    (is (= {:name "new-palette" :current true :colors []}
           @(rf/subscribe [::subs/current-palette]))))

  (testing "rename palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/rename-selected-palette "renamed"])
    (is (= (assoc initial-current-palette :name "renamed")
           @(rf/subscribe [::subs/current-palette]))))

  (testing "load/download palette"
    (initialize-db)

    (let [initial-palettes @(rf/subscribe [::subs/palettes])]
      (rf/dispatch-sync [::palette/select-palette 1])
      (rf/dispatch-sync [::palette/download-palette])
      (is (= {:file-name "palette1.gpl",
              :content
              "GIMP Palette
Name: palette1
Columns: 0
0 0 0 Untitled
255 0 0 Untitled
0 0 255 Untitled
0 128 0 Untitled"
              :content-type :json}
             @!last-download-file))

      (rf/dispatch-sync [::palette/remove-selected-palette])

      (rf/dispatch-sync [::palette/load-palette @!last-download-file])
      (is (= (-> initial-palettes
                 (assoc-in [0 :current] false)
                 (assoc-in [1 :current] true))
             @(rf/subscribe [::subs/palettes])))
      (is (= (:name (nth initial-palettes 1)) (:name @(rf/subscribe [::subs/current-palette]))))))

  (testing "add current frame colors to current palette"
    (initialize-db)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/set-current-color :primary-color (color/int 90 44 44)])
    (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 2 :y 0}])
    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/set-current-color :primary-color (color/int 167 46 46)])
    (apply-current-tool [{:x 5 :y 0} {:x 6 :y 0} {:x 7 :y 0}])

    (rf/dispatch-sync [::palette/add-colors-from-frame])
    (let [current-palette @(rf/subscribe [::subs/current-palette])]
      (is (= (into (:colors initial-current-palette) [(color/int 90 44 44) (color/int 167 46 46)])
             (:colors current-palette))))))

(deftest test-export
  (testing "open export modal"
    (rf-test/run-test-sync
     (testing "open"
       (initialize-db)

       (rf/dispatch-sync [::export/set-opened true])

       (is (= :image @(rf/subscribe [::subs/export-current-tab])))
       (is (= true @(rf/subscribe [::subs/export-modal-opened])))
       (is (= false @(rf/subscribe [::subs/exporting])))
       (is (= true @(rf/subscribe [::subs/export-settings-valid?]))))

     (testing "close"
       (initialize-db)

       (rf/dispatch-sync [::export/set-opened true])
       (rf/dispatch-sync [::export/set-opened false])

       (is (= false @(rf/subscribe [::subs/export-modal-opened])))))))

;; todo: refactoring
(defn array-data->pixels [array-data size]
  (->> (for [x (range 0 (. size -width))
             y (range 0 (. size -height))]
         (let [index (* (+ x (* y (. size -width))) 4)
               r (aget array-data index)
               g (aget array-data (+ index 1))
               b (aget array-data (+ index 2))
               a (aget array-data (+ index 3))]
           [[x y] (if (not= [r g b a] [0 0 0 0])
                    (color/int r g b (/ a 255))
                    color/transparent-color-int)]))
       (sort-by #(-> % first second)) ;; что бы был такой же порядок как и cel:pixels
       ))

(defn blob->pixels [blob]
  (.. (js/createImageBitmap blob)
      (then (fn [bitmap]
              (let [canvas (canvas/create-canvas {:width (. bitmap -width)
                                                  :height (. bitmap -height)})
                    _ (.. canvas (getContext "2d") (drawImage bitmap 0 0))
                    image-data (.. canvas (getContext "2d") (getImageData 0 0 (. bitmap -width) (. bitmap -height)))]
                (array-data->pixels (.. image-data -data) bitmap))))))

(defn zip-blob->pixels-map [blob]
  (. (jszip/loadAsync blob)
     (then (fn [zip]
             (.. js/Promise
                 (all
                  (for [[file-name content] (. js/Object (entries (. zip -files)))]
                    (.. content
                        (async "blob")
                        (then blob->pixels)
                        (then (fn [pixels]
                                [file-name pixels])))))
                 (then #(into {} %)))))))

(defn data-url->pixels [data-url]
  (.. (js/fetch data-url)
      (then (fn [res] (. res blob)))
      (then blob->pixels)))

(def sprite-for-export
  (let [sprite-size {:width 2 :height 2}]
    (->> (sprite/create {:size sprite-size
                         :layer (layer/create "Layer 1")
                         :frame (frame/create 200)
                         :cel (->> (cel/create sprite-size)
                                   (cel/set-pixels
                                    {{:x 0 :y 0} (color/int 255 0 0)
                                     {:x 1 :y 0} color/transparent-color-int
                                     {:x 0 :y 1} (color/int 255 0 0)
                                     {:x 1 :y 1} (color/int 0 0 0)}))})
         (sprite/add-frame (frame/create 100))
         (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 0 255)
                                         {:x 1 :y 0} color/transparent-color-int
                                         {:x 0 :y 1} (color/int 0 0 255)
                                         {:x 1 :y 1} (color/int 0 0 0)}) ;; todo: pass cell-pos?
         )))

(defn get-cel-pixels-with-pos [pos sprite]
  (->> sprite
       (sprite/get-cel pos)
       :pixels
       (map-indexed (fn [idx pixel]
                      [(let [{:keys [x y]} (cel/idx->pos idx (sprite/get-size sprite-for-export))]
                         [x y])
                       pixel]))))

(defn initialize-db-for-export [tab]
  (initialize-db {:sprite sprite-for-export})
  (rf/dispatch-sync [::export/set-opened true])
  (rf/dispatch-sync [::export/select-tab tab]))

(deftest test-export-zip
  (async done
         (do
           (initialize-db-for-export :image)

           (rf/dispatch-sync [::export/export])

           (wait-for-event done
                           ::export/download-generated-blob
                           (fn []
                             (is (= (:file-name @!last-download-file) "untitled")) ;; тут нужно добавить формат
                             (.. (zip-blob->pixels-map (:content @!last-download-file))
                                 (then (fn [pixels-map]
                                         (is (= {"untitled_1.png" (get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                                 "untitled_2.png" (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)}
                                                pixels-map))))
                                 (finally done)))))))

(deftest test-export-zip-preview
  (async done
         (do
           (initialize-db-for-export :image)

           (wait-for done
                     (fn []
                       (not (:generation @(rf/subscribe [::subs/export-preview]))))
                     (fn []
                       (let [data (:data @(rf/subscribe [::subs/export-preview]))]
                         (.. js/Promise
                             (all (map data-url->pixels data))
                             (then js->clj)
                             (then (fn [actual-previews-pixels]
                                     (is (= [(get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                             (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)]
                                            actual-previews-pixels))))
                             (finally done))))))))

(deftest test-export-gif
  (async done
         (do
           (initialize-db-for-export :image)

           (rf/dispatch-sync [::export/set-settings-option :file-type :gif])
           (rf/dispatch-sync [::export/export])

           (wait-for-event done
                           ::export/download-generated-blob
                           (fn []
                             (is (= (:file-name @!last-download-file) "untitled")) ;; тут нужно добавить формат
                             (.. (:content @!last-download-file)
                                 arrayBuffer
                                 (then (fn [array-buffer]
                                         (let [gif-frames (-> array-buffer
                                                              gifuct-js/parseGIF
                                                              (gifuct-js/decompressFrames true)
                                                              (#(for [frame %]
                                                                  {:duration (. frame -delay)
                                                                   :pixels (array-data->pixels (. frame -patch) (. frame -dims))})))]
                                           (is (= [{:duration 200
                                                    :pixels (-> (get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                                                vec
                                                                (assoc-in [3 1] color/transparent-color-int))}
                                                   {:duration 100
                                                    :pixels (-> (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)
                                                                vec
                                                                (assoc-in [3 1] color/transparent-color-int))}]
                                                  gif-frames)))))
                                 (finally done)))))))

(deftest test-export-gif-preview
  (async done
         (do
           (initialize-db-for-export :image)
           (rf/dispatch-sync [::export/set-settings-option :file-type :gif])

           (wait-for done
                     (fn []
                       (not (:generation @(rf/subscribe [::subs/export-preview]))))
                     (fn []
                       (let [data (:data @(rf/subscribe [::subs/export-preview]))]
                         (is (= 1 (count data)))
                         (.. (js/fetch (first data))
                             (then (fn [res] (.. res blob)))
                             (then (fn [res] (.. res arrayBuffer)))
                             (then (fn [array-buffer]
                                     (let [gif-frames (-> array-buffer
                                                          gifuct-js/parseGIF
                                                          (gifuct-js/decompressFrames true)
                                                          (#(for [frame %]
                                                              {:duration (. frame -delay)
                                                               :pixels (array-data->pixels (. frame -patch) (. frame -dims))})))]
                                       (is (= [{:duration 200
                                                :pixels (-> (get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                                            vec
                                                            (assoc-in [3 1] color/transparent-color-int))}
                                               {:duration 100
                                                :pixels (-> (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)
                                                            vec
                                                            (assoc-in [3 1] color/transparent-color-int))}]
                                              gif-frames)))))
                             (finally done))))))))

(deftest test-export-spritesheet
  (async done
         (do
           (initialize-db-for-export :spritesheet)

           (rf/dispatch-sync [::export/export])

           (wait-for-event done
                           ::export/download-generated-blob
                           (fn []
                             (is (= (:file-name @!last-download-file) "untitled")) ;; тут нужно добавить формат
                             (.. (blob->pixels (:content @!last-download-file))
                                 (then (fn [res]
                                         (is (= (concat (get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                                        (map (fn [[[x y] b]]
                                                               [[x (+ (:height (sprite/get-size sprite-for-export)) y)] b])
                                                             (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)))
                                                res))))
                                 (finally done)))))))

(deftest test-export-spritesheet-preview
  (async done
         (do
           (initialize-db-for-export :spritesheet)

           (wait-for done
                     (fn []
                       (not (:generation @(rf/subscribe [::subs/export-preview]))))
                     (fn []
                       (let [data (:data @(rf/subscribe [::subs/export-preview]))]
                         (.. (data-url->pixels data)
                             (then (fn [actual-previews-pixels]
                                     (is (= (concat (get-cel-pixels-with-pos {:frame-idx 0 :layer-idx 0} sprite-for-export)
                                                    (map (fn [[[x y] b]]
                                                           [[x (+ (:height (sprite/get-size sprite-for-export)) y)] b])
                                                         (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)))
                                            actual-previews-pixels))))
                             (finally done))))))))

(deftest test-settings
  (testing "image settings"
    (initialize-db-for-export :image)

    (rf/dispatch-sync [::export/set-settings-option :frames :selected])
    (is (= :selected (:frames @(rf/subscribe [::subs/export-image-settings]))))

    (is (= {:scale 1 :scaled-frame-size {:width 2 :height 2}}
           (select-keys @(rf/subscribe [::subs/export-image-settings]) [:scale :scaled-frame-size])))
    (rf/dispatch-sync [::export/set-settings-option :scale 4])
    (is (= {:scale 4 :scaled-frame-size {:width 8 :height 8}}
           (select-keys @(rf/subscribe [::subs/export-image-settings]) [:scale :scaled-frame-size])))

    (rf/dispatch-sync [::export/set-settings-option :file-type :gif])
    (is (= :gif (:file-type @(rf/subscribe [::subs/export-image-settings]))))
    (rf/dispatch-sync [::export/set-settings-option :file-type :png])
    (is (= :png (:file-type @(rf/subscribe [::subs/export-image-settings]))))

    (rf/dispatch-sync [::export/set-settings-option :repeat false])
    (is (= false (:repeat @(rf/subscribe [::subs/export-image-settings])))))

  (testing "spritesheet settings"
    (initialize-db-for-export :spritesheet)

    (rf/dispatch-sync [::export/set-settings-option :columns 3])
    (is (= {:columns 2 :rows 1}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings])
                        [:columns :rows])))

    (rf/dispatch-sync [::export/set-settings-option :frames :selected])
    (is (= :selected (:frames @(rf/subscribe [::subs/export-spritesheet-settings]))))

    (is (= {:scale 1 :scaled-frame-size {:width 2 :height 2}}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings]) [:scale :scaled-frame-size])))
    (rf/dispatch-sync [::export/set-settings-option :scale 4])
    (is (= {:scale 4 :scaled-frame-size {:width 8 :height 8}}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings]) [:scale :scaled-frame-size]))))

  (testing "spritesheet settings columns depends on frames and layers selectors"
    (initialize-db-for-export :spritesheet)

    (rf/dispatch-sync [::export/set-settings-option :frames :selected])

    (rf/dispatch-sync [::export/set-settings-option :columns 3])
    (is (= {:columns 1 :rows 1}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings])
                        [:columns :rows]))))

  (testing "spritesheet settings columns recalc when frames and layers selector is changed"
    (initialize-db-for-export :spritesheet)
    (rf/dispatch-sync [::export/set-settings-option :columns 2])

    (rf/dispatch-sync [::export/set-settings-option :frames :selected])

    (is (= {:columns 1 :rows 1}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings])
                        [:columns :rows])))))

(def empty-sprite
  (let [sprite-size {:width 8 :height 8}]
    (sprite/create {:size sprite-size
                    :layer (layer/create "Layer 1")
                    :frame (frame/create 100)
                    :cel (->> (cel/create sprite-size))})))

(defn get-active-colors [sprite pixels]
  (->> pixels
       (map-indexed (fn [idx pixel]
                      [(cel/idx->pos idx (sprite/get-size sprite)) pixel]))
       (filter (fn [[_ pixel]] (not= pixel color/transparent-color-int)))))

(defn get-active-current-cel-pixels [sprite]
  (->> @(rf/subscribe [::subs/sprite])
       (sprite/get-cel {:frame-idx 0 :layer-idx 0})
       :pixels
       (#(get-active-colors sprite %))))

(deftest test-eraser-tool
  (testing "should remove color"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 0 0)
                                                                 {:x 1 :y 0} (color/int 0 0 255)}))})
    (rf/dispatch-sync [::events/select-tool :eraser])

    (apply-current-tool [{:x 0 :y 0}])
    (is (= [[{:x 1, :y 0} (color/int 0 0 255)]] [[{:x 1 :y 0} (color/int 0 0 255)]]))))

(deftest test-color-picker-tool
  (testing "test"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 0 0)
                                                                 {:x 1 :y 0} (color/int 0 0 255)}))})
    (rf/dispatch-sync [::events/set-current-color :primary-color (color/int 255 255 0)])
    (rf/dispatch-sync [::events/set-current-color :secondary-color (color/int 255 255 0)])
    (rf/dispatch-sync [::events/select-tool :color-picker])

    (apply-current-tool [{:x 0 :y 0}])
    (is (= (color/int 0 0 0) @(rf/subscribe [::subs/primary-color])))
    (is (= (color/int 255 255 0) @(rf/subscribe [::subs/secondary-color])))

    (apply-current-tool [{:x 1 :y 0 :right-button true}])
    (is (= (color/int 0 0 0) @(rf/subscribe [::subs/primary-color])))
    (is (= (color/int 0 0 255) @(rf/subscribe [::subs/secondary-color])))))

(deftest test-circle-tool
  (testing "base"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :circle])

    (apply-current-tool [{:x 2 :y 0} {:x 3 :y 1} {:x 6 :y 2}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 3, :y 0} current-color]
                           [{:x 4, :y 0} current-color]
                           [{:x 5, :y 0} current-color]
                           [{:x 2, :y 1} current-color]
                           [{:x 6, :y 1} current-color]
                           [{:x 3, :y 2} current-color]
                           [{:x 4, :y 2} current-color]
                           [{:x 5, :y 2} current-color]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite)))))

  (testing "pixel size"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :circle])
    (rf/dispatch-sync [::events/change-tool-option :pixel-size 2])

    (apply-current-tool [{:x 2 :y 0} {:x 3 :y 1} {:x 6 :y 2}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 3, :y 0} current-color]
                           [{:x 4, :y 0} current-color]
                           [{:x 5, :y 0} current-color]
                           [{:x 2, :y 1} current-color]
                           [{:x 3, :y 1} current-color]
                           [{:x 5, :y 1} current-color]
                           [{:x 6, :y 1} current-color]
                           [{:x 3, :y 2} current-color]
                           [{:x 4, :y 2} current-color]
                           [{:x 5, :y 2} current-color]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite)))))

  (testing "fill"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :circle])
    (rf/dispatch-sync [::events/change-tool-option :fill true])

    (apply-current-tool [{:x 2 :y 0} {:x 3 :y 1} {:x 6 :y 2}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 3, :y 0} current-color]
                           [{:x 4, :y 0} current-color]
                           [{:x 5, :y 0} current-color]
                           [{:x 2, :y 1} current-color]
                           [{:x 3, :y 1} current-color]
                           [{:x 4, :y 1} current-color]
                           [{:x 5, :y 1} current-color]
                           [{:x 6, :y 1} current-color]
                           [{:x 3, :y 2} current-color]
                           [{:x 4, :y 2} current-color]
                           [{:x 5, :y 2} current-color]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite)))))

  (testing "keep ratio"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :circle])
    (rf/dispatch-sync [::events/change-tool-option :keep-ratio true])

    (apply-current-tool [{:x 2 :y 0} {:x 3 :y 1} {:x 6 :y 2}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 3, :y 0} current-color]
                           [{:x 2, :y 1} current-color]
                           [{:x 4, :y 1} current-color]
                           [{:x 3, :y 2} current-color]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite))))))

(deftest test-bucket-tool
  (testing "same color"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels [[{:x 0 :y 0} (color/int 0 0 255)]
                                                                 [{:x 1 :y 0} (color/int 0 0 255)]
                                                                 [{:x 2 :y 0} (color/int 255 255 0)]
                                                                 [{:x 3 :y 0} (color/int 0 0 255)]]))})
    (rf/dispatch-sync [::events/select-tool :bucket])
    (rf/dispatch-sync [::events/change-tool-option :same-color true])

    (apply-current-tool [{:x 0 :y 0} {:x 2 :y 0}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 0, :y 0} current-color]
                           [{:x 1, :y 0} current-color]
                           [{:x 2, :y 0} (color/int 255 255 0)]
                           [{:x 3, :y 0} current-color]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite)))))

  (testing "nearby colors"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels [[{:x 0 :y 0} (color/int 0 0 255)]
                                                                 [{:x 1 :y 0} (color/int 0 0 255)]
                                                                 [{:x 2 :y 0} (color/int 255 255 0)]
                                                                 [{:x 3 :y 0} (color/int 0 0 255)]]))})
    (rf/dispatch-sync [::events/select-tool :bucket])

    (apply-current-tool [{:x 0 :y 0} {:x 2 :y 0}])

    (let [current-color @(rf/subscribe [::subs/primary-color])
          expected-pixels [[{:x 0 :y 0} current-color]
                           [{:x 1 :y 0} current-color]
                           [{:x 2 :y 0} (color/int 255 255 0)]
                           [{:x 3 :y 0} (color/int 0 0 255)]]]
      (is (= expected-pixels (get-active-current-cel-pixels empty-sprite))))))

(deftest test-shading-tool
  (testing "lighten trans"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels [[{:x 0 :y 0} (color/int 0 0 255)]
                                                                 [{:x 1 :y 0} (color/int 255 255 0)]
                                                                 [{:x 2 :y 0} (color/int 255 0 0)]]))})
    (rf/dispatch-sync [::events/select-tool :shading])

    (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 0 :y 0} {:x 1 :y 0}]) ;; applied only once
    (is (= [[{:x 0, :y 0} (color/int 31 31 255)]
            [{:x 1, :y 0} (color/int 255 255 31)]
            [{:x 2, :y 0} (color/int 255 0 0)]]
           (get-active-current-cel-pixels empty-sprite))))

  (testing "darken trans"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels [[{:x 0 :y 0} (color/int 0 0 255)]
                                                                 [{:x 1 :y 0} (color/int 255 255 0)]
                                                                 [{:x 2 :y 0} (color/int 255 0 0)]]))})
    (rf/dispatch-sync [::events/select-tool :shading])
    (rf/dispatch-sync [::events/change-tool-option :lighten false])

    (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 0 :y 0} {:x 1 :y 0}]) ;; applied only once
    (is (= [[{:x 0, :y 0} (color/int 0 0 224)]
            [{:x 1, :y 0} (color/int 224 224 0)]
            [{:x 2, :y 0} (color/int 255 0 0)]]
           (get-active-current-cel-pixels empty-sprite))))

  (testing "pixel size and amount"
    (initialize-db {:sprite (->> empty-sprite
                                 (sprite/set-current-cel-pixels [[{:x 0 :y 0} (color/int 0 0 255)]
                                                                 [{:x 1 :y 0} (color/int 255 255 0)]
                                                                 [{:x 2 :y 0} (color/int 255 0 0)]]))})
    (rf/dispatch-sync [::events/select-tool :shading])
    (rf/dispatch-sync [::events/change-tool-option :pixel-size 2])
    (rf/dispatch-sync [::events/change-tool-option :amount 20])

    (apply-current-tool [{:x 1 :y 1}])
    (is (= [[{:x 0, :y 0} (color/int 102 102 255)]
            [{:x 1, :y 0} (color/int 255 255 102)]
            [{:x 2, :y 0} (color/int 255 0 0)]]
           (get-active-current-cel-pixels empty-sprite)))))

(deftest test-line-tool
  (testing "simple line"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :line])

    (apply-current-tool [{:x 2 :y 2} {:x 6 :y 3}])

    (let [current-color @(rf/subscribe [::subs/primary-color])]
      (is (= [[{:x 2, :y 2} current-color]
              [{:x 3, :y 2} current-color]
              [{:x 4, :y 2} current-color]
              [{:x 5, :y 3} current-color]
              [{:x 6, :y 3} current-color]]
             (get-active-current-cel-pixels empty-sprite)))))

  (testing "simple line + pixel-size"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :line])
    (rf/dispatch-sync [::events/change-tool-option :pixel-size 2])

    (apply-current-tool [{:x 2 :y 2} {:x 6 :y 3}])

    (let [current-color @(rf/subscribe [::subs/primary-color])]
      (is (= [[{:x 1, :y 1} current-color]
              [{:x 2, :y 1} current-color]
              [{:x 3, :y 1} current-color]
              [{:x 4, :y 1} current-color]
              [{:x 1, :y 2} current-color]
              [{:x 2, :y 2} current-color]
              [{:x 3, :y 2} current-color]
              [{:x 4, :y 2} current-color]
              [{:x 5, :y 2} current-color]
              [{:x 6, :y 2} current-color]
              [{:x 4, :y 3} current-color]
              [{:x 5, :y 3} current-color]
              [{:x 6, :y 3} current-color]]
             (get-active-current-cel-pixels empty-sprite)))))

  (testing "straight line"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :line])
    (rf/dispatch-sync [::events/change-tool-option :straight true])

    (apply-current-tool [{:x 2 :y 2} {:x 6 :y 3}])

    (let [current-color @(rf/subscribe [::subs/primary-color])]
      (is (= [[{:x 2, :y 2} current-color]
              [{:x 3, :y 2} current-color]
              [{:x 4, :y 2} current-color]
              [{:x 5, :y 2} current-color]
              [{:x 6, :y 2} current-color]
              [{:x 7, :y 2} current-color]]
             (get-active-current-cel-pixels empty-sprite)))))

  (testing "simple line + pixel-size"
    (initialize-db {:sprite empty-sprite})
    (rf/dispatch-sync [::events/select-tool :line])
    (rf/dispatch-sync [::events/change-tool-option :straight true])
    (rf/dispatch-sync [::events/change-tool-option :pixel-size 2])

    (apply-current-tool [{:x 2 :y 2} {:x 6 :y 3}])

    (let [current-color @(rf/subscribe [::subs/primary-color])]
      (is (= [[{:x 1, :y 1} current-color]
              [{:x 2, :y 1} current-color]
              [{:x 3, :y 1} current-color]
              [{:x 4, :y 1} current-color]
              [{:x 5, :y 1} current-color]
              [{:x 6, :y 1} current-color]
              [{:x 7, :y 1} current-color]
              [{:x 1, :y 2} current-color]
              [{:x 2, :y 2} current-color]
              [{:x 3, :y 2} current-color]
              [{:x 4, :y 2} current-color]
              [{:x 5, :y 2} current-color]
              [{:x 6, :y 2} current-color]
              [{:x 7, :y 2} current-color]]
             (get-active-current-cel-pixels empty-sprite))))))

(defn canvas->pixels-map [canvas-id]
  (let [canvas (. js/document (getElementById canvas-id))
        size (-> @re-frame.db/app-db :sprite :size)
        image-data (.. canvas (getContext "2d") (getImageData 0 0 (:width size) (:height size)))]
    (array-data->pixels (.. image-data -data) (clj->js size))))

(defn mouse-down-and-move [poses]
  (let [mouse-down-pos (first poses)
        mouse-move-poses (drop 1 poses)]
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-down mouse-down-pos (:right-button mouse-down-pos)])
    (doseq [pos mouse-move-poses]
      (rf/dispatch-sync [::events/handle-mouse-event :mouse-move pos (:right-button pos)]))))

(defn mouse-down [mouse-down-pos]
  (rf/dispatch-sync [::events/handle-mouse-event :mouse-down mouse-down-pos (:right-button mouse-down-pos)]))

(defn mouse-move [poses]
  (doseq [pos poses]
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-move pos (:right-button pos)])))

(defn mouse-up [mouse-up-pos]
  (rf/dispatch-sync [::events/handle-mouse-event :mouse-up mouse-up-pos (:right-button mouse-up-pos)]))

(defn click [mouse-pos]
  (mouse-down mouse-pos)
  (mouse-up mouse-pos))

(defn initialize-db-and-clear-preview [initialize-db-data]
  (canvas/clear-canvas (. js/document (getElementById "preview")))
  (initialize-db initialize-db-data))

(def sprite-for-selection-pixels {[0 0] (color/int 255 0 0)
                                  [1 0] (color/int 0 255 0)
                                  [0 1] (color/int 0 0 255)
                                  [1 1] (color/int 170 0 0)})
(def sprite-for-selection
  (let [sprite-size {:width 2 :height 2}]
    (->> (sprite/create {:size sprite-size
                         :layer (layer/create "Layer 1")
                         :frame (frame/create 200)
                         :cel (->> (cel/create sprite-size)
                                   (cel/set-pixels
                                    {{:x 0 :y 0} (color/int 255 0 0)
                                     {:x 1 :y 0} (color/int 0 255 0)
                                     {:x 0 :y 1} (color/int 0 0 255)
                                     {:x 1 :y 1} (color/int 170 0 0)}))}))))

(defn get-preview [pixels-map]
  (for [pos [[0 0]
             [1 0]
             [0 1]
             [1 1]]]
    [pos (get pixels-map pos color/transparent-color-int)]))

(def selection-color (color/int 255 255 255 0.4))

(deftest apply-rectangle-selection
  (testing "no selection"
    (testing "when user mouse down, move it and up it should draw transparent selection on preview"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (mouse-down {:x 0 :y 0})
      (is (= (get-preview {[0 0] selection-color}) (canvas->pixels-map "preview"))
          "draw selection on mouse down")
      (mouse-move [{:x 1 :y 0}])
      (is (= (get-preview {[0 0] selection-color
                           [1 0] selection-color})
             (canvas->pixels-map "preview"))
          "draw selection on mouse move")
      (mouse-up {:x 1 :y 0})
      (is (= (get-preview {[0 0] selection-color
                           [1 0] selection-color})
             (canvas->pixels-map "preview"))
          "draw selection on mouse up")))

  (testing "selection"
    (testing "when user mouse down outside selection should drop selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (mouse-down {:x 1 :y 0})
      (is (= (get-preview {}) (canvas->pixels-map "preview"))))

    (testing "when user mouse down in selection should not drop selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (mouse-down {:x 0 :y 0})
      (is (= (get-preview {[0 0] selection-color}) (canvas->pixels-map "preview"))))
    (testing "when user mouse down in selection and move it should hide selection and move selected pixels"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (mouse-down {:x 0 :y 0})
      (mouse-move [{:x 1 :y 0}])
      (is (= (get-preview {[1 0] (color/int 255 0 0)}) (canvas->pixels-map "preview")))
      (is (= (seq (assoc sprite-for-selection-pixels [0 0] color/transparent-color-int))
             (canvas->pixels-map "current-layer")))
      (is (= (-> @(rf/subscribe [::subs/sprite]) sprite/get-current-cel)
             (sprite/get-current-cel sprite-for-selection))
          "sprite should not be changed, only preview and current-layer"))
    (testing "should draw selection after releasing moved selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0}])
      (is (= (get-preview {[1 0] (color/int 255 102 102)}) (canvas->pixels-map "preview")))
      (is (= (-> @(rf/subscribe [::subs/sprite]) sprite/get-current-cel)
             (sprite/get-current-cel sprite-for-selection))
          "sprite should not be changed, only preview and current-layer"))
    (testing "should commit changes when click outside selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0}])
      (click {:x 0 :y 1})
      (is (= (get-preview {}) (canvas->pixels-map "preview")))
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] color/transparent-color-int
                         [1 0] (color/int 255 0 0)))
             (canvas->pixels-map "current-layer")))
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] color/transparent-color-int
                         [1 0] (color/int 255 0 0)))
             (->> @(rf/subscribe [::subs/sprite])
                  sprite/get-current-cel
                  cel/pixels->coll
                  (map (fn [[pos color]] [[(:x pos) (:y pos)] color]))))))

    (testing "copy+paste+move+commit"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (rf/dispatch-sync [::rectangle-selection/copy-selection])
      (is (= (get-preview {}) (canvas->pixels-map "preview"))
          "should remove selection after copy selection")
      (rf/dispatch-sync [::rectangle-selection/past-selection])

      (is (= (get-preview {[0 0] (color/int 255 102 102)}) (canvas->pixels-map "preview"))
          "should past selection")
      (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0}])
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] (color/int 255 0 0)))
             (canvas->pixels-map "current-layer"))
          "when move pasted selection should not cut underlayed pixels")
      (is (= (get-preview {[0 0] color/transparent-color-int
                           [1 0] (color/int 255 102 102)}) (canvas->pixels-map "preview")))

      (click {:x 0 :y 0})
      (is (= (get-preview {}) (canvas->pixels-map "preview"))
          "preview after commit pasted selection")
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] (color/int 255 0 0)
                         [1 0] (color/int 255 0 0)))
             (canvas->pixels-map "current-layer"))
          "current-layer after commit pasted selection")
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] (color/int 255 0 0)
                         [1 0] (color/int 255 0 0)))
             (->> @(rf/subscribe [::subs/sprite])
                  sprite/get-current-cel
                  cel/pixels->coll
                  (map (fn [[pos color]] [[(:x pos) (:y pos)] color]))))
          "current-cel changed after commit pasted selection"))

    (testing "copy+paste+paste"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (rf/dispatch-sync [::rectangle-selection/copy-selection])
      (rf/dispatch-sync [::rectangle-selection/past-selection])
      (rf/dispatch-sync [::rectangle-selection/past-selection])
      (is (= (get-preview {[0 0] (color/int 255 102 102)}) (canvas->pixels-map "preview"))))

    (testing "delete selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (rf/dispatch-sync [::rectangle-selection/cut-selection])
      (is (= (get-preview {}) (canvas->pixels-map "preview")))
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] color/transparent-color-int))
             (canvas->pixels-map "current-layer")))
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] color/transparent-color-int))
             (->> @(rf/subscribe [::subs/sprite])
                  sprite/get-current-cel
                  cel/pixels->coll
                  (map (fn [[pos color]] [[(:x pos) (:y pos)] color]))))))

    (testing "cut selection"
      (initialize-db-and-clear-preview {:sprite sprite-for-selection})
      (rf/dispatch-sync [::events/select-tool :rectangle-selection])

      (apply-current-tool [{:x 0 :y 0}])
      (rf/dispatch-sync [::rectangle-selection/cut-selection])
      (is (= (get-preview {}) (canvas->pixels-map "preview")) "preview")
      (is (= (seq (assoc sprite-for-selection-pixels
                         [0 0] color/transparent-color-int))
             (canvas->pixels-map "current-layer"))
          "current-layer")
      (rf/dispatch-sync [::rectangle-selection/past-selection])
      (is (= (get-preview {[0 0] (color/int 255 102 102)})
             (canvas->pixels-map "preview"))
          "should past selection"))))

(def sprite-for-test-move-cel ;; todo: refactor
  (let [sprite-size {:width 1 :height 1}]
    (->> (sprite/create {:size sprite-size
                         :layer (layer/create "Layer 1")
                         :frame (frame/create 200)
                         :cel (->> (cel/create sprite-size)
                                   (cel/set-pixels
                                    {{:x 0 :y 0} (color/int 255 0 0)}))})
         (sprite/add-frame (frame/create 100))
         (sprite/add-layer (layer/create "Layer 2"))

         (sprite/select-only-1-cel {:frame-idx 1 :layer-idx 0})
         (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 255 0)})

         (sprite/select-only-1-cel {:frame-idx 0 :layer-idx 1})
         (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 0 0 255)})

         (sprite/select-only-1-cel {:frame-idx 1 :layer-idx 1})
         (sprite/set-current-cel-pixels {{:x 0 :y 0} (color/int 255 0 255)})

         (sprite/select-only-1-cel {:frame-idx 0 :layer-idx 0}))))

(deftest test-move-cel
  (testing "move cel on different frame on the same layer"
    (initialize-db {:sprite sprite-for-test-move-cel})

    (rf/dispatch-sync [::events/move-cel {:frame-idx 0 :layer-idx 0} {:frame-idx 2 :layer-idx 0}])

    (let [sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [[{:pixels [(color/int 0 255 0)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}
               {:pixels [(color/int 0 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}]
              [{:pixels [(color/int 255 0 0)],
                :size {:width 1, :height 1},
                :current true,
                :selected true}
               {:pixels [(color/int 255 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}]]
             (:cels sprite)))))

  (testing "move cel on different layer and different frame"
    (initialize-db {:sprite sprite-for-test-move-cel})

    (rf/dispatch-sync [::events/move-cel {:frame-idx 0 :layer-idx 0} {:frame-idx 1 :layer-idx 1}])

    (let [sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [[{:pixels [(color/int 255 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}
               {:pixels [(color/int 0 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}]
              [{:pixels [(color/int 0 255 0)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}
               {:pixels [(color/int 255 0 0)],
                :size {:width 1, :height 1},
                :current true,
                :selected true}]]
             (:cels sprite)))))

  (testing "when cels are in groups and move cel on different layer and different frame, should remove cels from groups"
    (initialize-db {:sprite (->> sprite-for-test-move-cel
                                 (sprite/link-layer-cels [{:frame-idx 0 :layer-idx 0}
                                                          {:frame-idx 1 :layer-idx 0}]
                                                         {:frame-idx 0 :layer-idx 0})
                                 (sprite/link-layer-cels [{:frame-idx 0 :layer-idx 1}
                                                          {:frame-idx 1 :layer-idx 1}]
                                                         {:frame-idx 0 :layer-idx 1}))})

    (rf/dispatch-sync [::events/move-cel {:frame-idx 0 :layer-idx 0} {:frame-idx 1 :layer-idx 1}])

    (let [sprite (:sprite @(rf/subscribe [:db]))]
      (is (= [[{:pixels [(color/int 0 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false}
               {:pixels [(color/int 0 0 255)],
                :size {:width 1, :height 1},
                :current false,
                :selected false,
                :group-number 0}]
              [{:pixels [(color/int 255 0 0)],
                :size {:width 1, :height 1},
                :current false,
                :selected false,
                :group-number 0}
               {:pixels [(color/int 255 0 0)],
                :size {:width 1, :height 1},
                :current true,
                :selected true}]]
             (:cels sprite))))))

#_(enable-console-print!)
#_(run-tests)

;; todo
;; имя файла формировать
;; у gif протестить repeat
;; у image когда 1 картинка то загружаться должна только 1 картинка
;; preview

;; тесты для resizer

;; {{:x 0 :y 0} "rgb(0,0,255)"
;;  {:x 1 :y 0} "rgb(0,0,255)"
;;  {:x 2 :y 0} "rgb(255,255,0)"
;;  {:x 3 :y 0} "rgb(0,0,255)"} vs [0 0]
