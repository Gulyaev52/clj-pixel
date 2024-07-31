(ns pixel-art.events-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [day8.re-frame.test :as rf-test]
            [pixel-art.events :as events]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.sprite-import-export :as sprite-import-export]
            [pixel-art.subs :as subs]
            [re-frame.core :as rf]
            [pixel-art.utils.coll :as coll]))

(rf/reg-sub
 :db
 (fn [db]
   db))

(def initial-size {:width 8 :height 8})

(defn create-pixels [size]
  (vec (repeat (* (:width size) (:height size)) transparent-color)))

(def initial-cel-pixels-map {{:x 0 :y 0} "black"
                             {:x 0 :y 1} transparent-color
                             {:x 1 :y 1} "black"
                             {:x 3 :y 3} "black"
                             {:x 3 :y 4} "black"
                             {:x 4 :y 3} "black"
                             {:x 4 :y 4} "black"})

(defn pos->idx [{:keys [x y]} {:keys [width]}]
  (+ x (* width y)))

(def initial-cel-pixels (-> (create-pixels initial-size)
                            (#(reduce (fn [pixels [pos color]]
                                        (assoc pixels (pos->idx pos initial-size) color))
                                      %
                                      initial-cel-pixels-map))))

(defn initialize-db
  ([]
   (rf/dispatch-sync [::events/initialize-db initial-cel-pixels-map]))
  ([pixels-map]
   (rf/dispatch-sync [::events/initialize-db pixels-map])))

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
             {:pixels (create-pixels initial-size) :current false :selected false :pos {:frame-idx 1 :layer-idx 0}}
             {:pixels (create-pixels initial-size) :current true :selected true :pos {:frame-idx 2 :layer-idx 0}}]
            (map #(select-keys % [:current :selected :pos :pixels]) (:cels timeline))))

     (apply-current-tool [{:x 0 :y 0}])
     (is (= (assoc (create-pixels initial-size) 0 "black")
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
             {:pixels (create-pixels initial-size) :current false :selected false :pos {:frame-idx 0 :layer-idx 1}}
             {:pixels (create-pixels initial-size) :current true :selected true :pos {:frame-idx 0 :layer-idx 2}}]
            (map #(select-keys % [:current :selected :pos :pixels]) (:cels timeline))))

     (apply-current-tool [{:x 0 :y 0}])
     (is (= (assoc (create-pixels initial-size) 0 "black")
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
                 :pixels (create-pixels initial-size)}
                {:pos {:frame-idx 1, :layer-idx 0},
                 :current false,
                 :selected false
                 :pixels initial-cel-pixels}
                {:pos {:frame-idx 1, :layer-idx 1},
                 :current true,
                 :selected true
                 :pixels (create-pixels initial-size)}] 3)
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

    (is (= [{:pos {:frame-idx 0 :layer-idx 0} :pixels (-> (create-pixels initial-size)
                                                          (assoc 0 "black"))}
            {:pos {:frame-idx 1 :layer-idx 0} :pixels (-> (create-pixels initial-size)
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
    (initialize-db {{:x 0 :y 0} "blue"
                    {:x 0 :y 1} "blue"
                    {:x 1 :y 0} "blue"})

    (rf/dispatch-sync [::events/add-frame])
    (apply-current-tool [{:x 0 :y 0}])

    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 1}])
    (apply-current-tool [{:x 0 :y 0} {:x 0 :y 1} {:x 0 :y 2} {:x 1 :y 0} {:x 2 :y 0} {:x 3 :y 0}])

    (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
    (rf/dispatch-sync [::events/merge-layer-with-below])))

(def !last-download-file-desc (atom nil))
(rf/reg-fx
 :download-file
 (fn [file-desc]
   (println "bla")
   (reset! !last-download-file-desc file-desc)))

(deftest test-import-export-sprite-as-file
  (do
    (initialize-db)
    (let [initial-db @(rf/subscribe [:db])]
      (rf/dispatch-sync [::sprite-import-export/export-sprite-as-file])
      (apply-current-tool [{:x 0 :y 0} {:x 1 :y 0} {:x 2 :y 0} {:x 3 :y 0}])
      (rf/dispatch-sync [::sprite-import-export/import-sprite-from-file @!last-download-file-desc])

      (is (= initial-db (dissoc @(rf/subscribe [:db]) :user-is-drawing :mouse-pos))))))

#_(enable-console-print!)
#_(run-tests)
