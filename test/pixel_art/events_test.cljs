(ns pixel-art.events-test
  (:require ["./jszip" :as jszip]
            ["gifuct-js" :as gifuct-js]
            [cljs.test :refer-macros [deftest testing is run-tests] :refer [async]]
            [day8.re-frame.test :as rf-test]
            [pixel-art.canvas :as canvas]
            [pjstadig.humane-test-output]
            [pixel-art.events :as events]
            [pixel-art.events.event-collector :as event-collector]
            [pixel-art.export :as export]
            [pixel-art.local-storage :as local-storage]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.palette :as palette]
            [pixel-art.sprite-import-export :as sprite-import-export]
            [pixel-art.subs :as subs]
            [pixel-art.utils.coll :as coll]
            [re-frame.core :as rf]
            [sc.api]))

(rf/reg-sub
 :db
 (fn [db]
   db))

(def !last-download-file (atom nil))
(rf/reg-fx
 :download-file
 (fn [file-desc]
   (reset! !last-download-file file-desc)))

(def !local-storage (atom {}))
(rf/reg-fx
 ::local-storage/set-item
 (fn [{:keys [key value]}]
   (reset! !local-storage (assoc @!local-storage key value))))

(defn create-pixels [size]
  (vec (repeat (* (:width size) (:height size)) transparent-color)))

(def initial-sprite
  (let [sprite-size {:width 8 :height 8}]
    (sprite/create {:size sprite-size
                    :layer (layer/create "Layer 1" nil)
                    :frame (frame/create 100)
                    :cel (->> (cel/create sprite-size)
                              (cel/set-pixels (->> (for [x (range 0 (:width sprite-size))
                                                         y (range 0 (:height sprite-size))]
                                                     [{:x x :y y} nil])
                                                   (into {})))
                              (cel/set-pixels
                               {{:x 0 :y 0} "red"
                                {:x 0 :y 1} transparent-color
                                {:x 1 :y 1} "red"
                                {:x 3 :y 3} "red"
                                {:x 3 :y 4} "red"
                                {:x 4 :y 3} "red"
                                {:x 4 :y 4} "red"}))})))
(def initial-cel-pixels (-> initial-sprite :cels ffirst :pixels))

(def initial-palettes
  [{:name "default"
    :current true
    :colors ["black" "red" "blue" "green"]}
   {:name "palette1"
    :current false
    :colors ["rgb(0, 0, 0)"
             "rgb(255, 0, 0)"
             "rgb(0, 0, 255)"
             "rgb(0, 128, 0)"]}])
(def initial-current-palette (first initial-palettes))

(defn initialize-db
  ([]
   (rf/dispatch-sync [::events/initialize-db {:sprite initial-sprite :palettes initial-palettes}]))
  ([data]
   (rf/dispatch-sync [::events/initialize-db (merge {:palettes initial-palettes} data)])))

(defn apply-current-tool [poses]
  (let [mouse-down-pos (first poses)
        mouse-move-poses (when (> (count poses) 1)
                           (subvec poses 1 (dec (count poses))))
        mouse-up-pos (last poses)]
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-down mouse-down-pos false])
    (when (seq mouse-move-poses)
      (doseq [pos mouse-move-poses]
        (rf/dispatch-sync [::events/handle-mouse-event :mouse-move pos false])))
    (rf/dispatch-sync [::events/handle-mouse-event :mouse-up mouse-up-pos false])))

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
     (is (= (assoc (create-pixels (sprite/get-size initial-sprite)) 0 "black")
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
              :locked? false,
              :automatic-linking? false,
              :name "Layer 1",
              :children nil,
              :current false,
              :idx 0}
             {:visibile? true,
              :locked? false,
              :automatic-linking? false,
              :name "Layer 2",
              :children nil,
              :current false,
              :idx 1}
             {:visibile? true,
              :locked? false,
              :automatic-linking? false,
              :name "Layer 3",
              :children nil,
              :current true,
              :idx 2}]
            (:layers timeline))
         "new layer created")
     (is (= [{:pixels (-> initial-timeline :cels first :pixels) :current false :selected false :pos {:frame-idx 0 :layer-idx 0}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current false :selected false :pos {:frame-idx 0 :layer-idx 1}}
             {:pixels (create-pixels (sprite/get-size initial-sprite)) :current true :selected true :pos {:frame-idx 0 :layer-idx 2}}]
            (map #(select-keys % [:current :selected :pos :pixels]) (:cels timeline))))

     (apply-current-tool [{:x 0 :y 0}])
     (is (= (assoc (create-pixels (sprite/get-size initial-sprite)) 0 "black")
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
            :locked? false,
            :automatic-linking? false,
            :name "Layer 1",
            :children nil,
            :current false,
            :idx 0}
           {:visibile? true,
            :locked? false,
            :automatic-linking? false,
            :name "Layer 2",
            :children nil,
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
     (rf/dispatch-sync [::events/remove-layer 1])

     (is (= (-> initial-db :sprite) (-> @!db :sprite))))))

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
                                                          (assoc 0 "black"))}
            {:pos {:frame-idx 1 :layer-idx 0} :pixels (-> (create-pixels (sprite/get-size initial-sprite))
                                                          (assoc 0 "black"))}]
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
                                 (sprite/set-current-cel-pixels {{:x 0 :y 0} "blue"
                                                                 {:x 0 :y 1} "blue"
                                                                 {:x 1 :y 0} "blue"}))})

    (rf/dispatch-sync [::events/add-frame])
    (apply-current-tool [{:x 0 :y 0}])

    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
    (apply-current-tool [{:x 0 :y 0} {:x 0 :y 1} {:x 0 :y 2} {:x 1 :y 0} {:x 2 :y 0} {:x 3 :y 0}])

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/merge-layer-with-below])))

(deftest test-import-export-sprite-as-file
  (do
    (initialize-db)
    (let [initial-db @(rf/subscribe [:db])]
      (rf/dispatch-sync [::sprite-import-export/export-sprite-as-file])
      (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 2 :y 0} {:x 3 :y 0}])
      (rf/dispatch-sync [::sprite-import-export/import-sprite-from-file @!last-download-file])

      (is (= initial-db (dissoc @(rf/subscribe [:db]) :user-is-drawing :mouse-pos))))))

(defn check-current-palettes-info-saved-in-local-storage []
  (is (= (:palettes @(rf/subscribe [:db]))
         (:palettes @!local-storage))))

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
      (let [new-color "rgb(100, 100, 0)"]
        (rf/dispatch-sync [::palette/add-color new-color])
        (is (= new-color (last (:colors @(rf/subscribe [::subs/current-palette]))))))
      (check-current-palettes-info-saved-in-local-storage))

    (testing "colors are unique"
      (initialize-db)
      (let [initial-selected-palette @(rf/subscribe [::subs/current-palette])
            new-color "black"]
        (rf/dispatch-sync [::palette/add-color new-color])
        (is (= new-color @(rf/subscribe [::subs/primary-color])))
        (is (= initial-selected-palette @(rf/subscribe [::subs/current-palette]))))
      (check-current-palettes-info-saved-in-local-storage)))

  (testing "remove color"
    (initialize-db)
    (let [initial-selected-palette @(rf/subscribe [::subs/current-palette])]
      (rf/dispatch-sync [::palette/remove-color 0])
      (is (= (rest (:colors initial-selected-palette))
             (:colors @(rf/subscribe [::subs/current-palette]))))
      (check-current-palettes-info-saved-in-local-storage)))

  (testing "select palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/select-palette 1])
    (is (= (assoc (nth initial-palettes 1)
                  :current true)
           @(rf/subscribe [::subs/current-palette])))
    (check-current-palettes-info-saved-in-local-storage))

  (testing "remove selected palette"
    (initialize-db)
    (let [current-second-palette (assoc (nth initial-palettes 1) :current true)]
      (rf/dispatch-sync [::palette/remove-selected-palette])
      (is (= current-second-palette
             @(rf/subscribe [::subs/current-palette])))
      (is (= [current-second-palette]
             @(rf/subscribe [::subs/palettes])))
      (check-current-palettes-info-saved-in-local-storage)))

  (testing "create palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/create-palette "new-palette"])
    (is (= (conj (assoc-in initial-palettes [0 :current] false)
                 {:name "new-palette"
                  :current true
                  :colors []})
           @(rf/subscribe [::subs/palettes])))
    (is (= {:name "new-palette" :current true :colors []}
           @(rf/subscribe [::subs/current-palette])))
    (check-current-palettes-info-saved-in-local-storage))

  (testing "rename palette"
    (initialize-db)
    (rf/dispatch-sync [::palette/rename-selected-palette "renamed"])
    (is (= (assoc initial-current-palette :name "renamed")
           @(rf/subscribe [::subs/current-palette])))
    (check-current-palettes-info-saved-in-local-storage))

  (testing "load/download palette"
    (initialize-db)

    (let [initial-palettes @(rf/subscribe [::subs/palettes])]
      (rf/dispatch-sync [::palette/select-palette 1])
      (rf/dispatch-sync [::palette/download-palette])
      (is (= {:file-name "palette1.gpl",
              :content
              "GIMP Palette\nName: palette1\nColumns: 0\n0 0 0 Untitled\n255 0 0 Untitled\n0 0 255 Untitled\n0 128 0 Untitled"
              :content-type :json}
             @!last-download-file))

      (rf/dispatch-sync [::palette/remove-selected-palette])

      (rf/dispatch-sync [::palette/load-palette @!last-download-file])
      (is (= (-> initial-palettes
                 (assoc-in [0 :current] false)
                 (assoc-in [1 :current] true))
             @(rf/subscribe [::subs/palettes])))
      (is (= (:name (nth initial-palettes 1)) (:name @(rf/subscribe [::subs/current-palette]))))
      (check-current-palettes-info-saved-in-local-storage)))

  (testing "add current frame colors to current palette"
    (initialize-db)

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/set-current-color :primary-color "rgb(90, 44, 44)"])
    (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 2 :y 0}])
    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/set-current-color :primary-color "rgb(167, 46, 46)"])
    (apply-current-tool [{:x 5 :y 0} {:x 6 :y 0} {:x 7 :y 0}])

    (rf/dispatch-sync [::palette/add-colors-from-frame])
    (let [current-palette @(rf/subscribe [::subs/current-palette])]
      (is (= (into (:colors initial-current-palette) ["rgb(90, 44, 44)" "rgb(167, 46, 46)"])
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

(defn wait-for-event [done event on-success]
  (wait-for done
            (fn []
              (let [[last-event] (first @event-collector/event-store)]
                (= event last-event)))
            on-success))

(def !done-counter (atom 0))
(def done (fn []
            (swap! !done-counter inc)
            (println "done" @!done-counter)))

(defn array-data->pixels [array-data size]
  (->> (for [x (range 0 (. size -width))
             y (range 0 (. size -height))]
         (let [index (* (+ x (* y (. size -width))) 4)]
           (let [r (aget array-data index)
                 g (aget array-data (+ index 1))
                 b (aget array-data (+ index 2))
                 a (aget array-data (+ index 3))]
             [[x y]
              (when (not= [r g b a] [0 0 0 0])
                (str "rgb(" r "," g "," b ")"))])))
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
                         :layer (layer/create "Layer 1" nil)
                         :frame (frame/create 200)
                         :cel (->> (cel/create sprite-size)
                                   (cel/set-pixels
                                    {{:x 0 :y 0} "rgb(255,0,0)"
                                     {:x 1 :y 0} nil
                                     {:x 0 :y 1} "rgb(255,0,0)"
                                     {:x 1 :y 1} "rgb(0,0,0)"}))})
         (sprite/add-frame (frame/create 100))
         (sprite/set-current-cel-pixels {{:x 0 :y 0} "rgb(0,0,255)"
                                         {:x 1 :y 0} nil
                                         {:x 0 :y 1} "rgb(0,0,255)"
                                         {:x 1 :y 1} "rgb(0,0,0)"}) ;; todo: pass cell-pos?
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

           (rf/dispatch-sync [::export/set-common-settings-option :file-type :gif])
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
                                                                (assoc-in [3 1] nil))}
                                                   {:duration 100
                                                    :pixels (-> (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)
                                                                vec
                                                                (assoc-in [3 1] nil))}]
                                                  gif-frames)))))
                                 (finally done)))))))

(deftest test-export-gif-preview
  (async done
         (do
           (initialize-db-for-export :image)
           (rf/dispatch-sync [::export/set-common-settings-option :file-type :gif])

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
                                                            (assoc-in [3 1] nil))}
                                               {:duration 100
                                                :pixels (-> (get-cel-pixels-with-pos {:frame-idx 1 :layer-idx 0} sprite-for-export)
                                                            vec
                                                            (assoc-in [3 1] nil))}]
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

    (rf/dispatch-sync [::export/set-settings-option :frames :selected])
    (is (= :selected (:frames @(rf/subscribe [::subs/export-spritesheet-settings]))))

    (is (= {:scale 1 :scaled-frame-size {:width 2 :height 2}}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings]) [:scale :scaled-frame-size])))
    (rf/dispatch-sync [::export/set-settings-option :scale 4])
    (is (= {:scale 4 :scaled-frame-size {:width 8 :height 8}}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings]) [:scale :scaled-frame-size])))

    (rf/dispatch-sync [::export/set-settings-option :columns 3])
    (is (= {:columns 2 :rows 1}
           (select-keys @(rf/subscribe [::subs/export-spritesheet-settings])
                        [:columns :rows])))))

#_(enable-console-print!)
#_(run-tests)

;; имя файла формировать
;; у gif протестить repeat
;; у image когда 1 картинка то загружаться должна только 1 картинка
;; preview