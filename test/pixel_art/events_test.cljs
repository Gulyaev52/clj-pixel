(ns pixel-art.events-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [day8.re-frame.test :as rf-test]
            [pixel-art.events :as events]
            [pixel-art.model.cel :as cel]
            [pixel-art.model.color :refer [transparent-color]]
            [pixel-art.model.frame :as frame]
            [pixel-art.model.layer :as layer]
            [pixel-art.model.sprite :as sprite]
            [pixel-art.subs :as subs]
            [re-frame.core :as rf]))

(rf/reg-sub
 :db
 (fn [db]
   db))

(defn get-selected-cels-from-timeline [timeline]
  (filter #(or (:selected %) (:current %)) (map #(select-keys % [:pos :current :selected]) (:cels timeline))))

#_(deftest test-init
    (rf-test/run-test-sync
     (rf/dispatch-sync [::events/initialize-db])
     #_(def db @(rf/subscribe [:db]))
     (is (= 1 2))))

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
   (testing "add 2 frame"
     (def !db (rf/subscribe [:db]))
     (rf/dispatch-sync [::events/initialize-db])
     (def initial-db @(rf/subscribe [:db]))
     (def initial-sprite (-> initial-db :sprite))
     (rf/dispatch-sync [::events/add-frame])
     (def sprite (-> @!db :sprite))
     (is (= (:layers sprite) (:layers initial-sprite)) "layers should not be changed")
     (is (= [(frame/create 100) (frame/create 100)] (:frames sprite)) "new frame created")
     (is (= (frame/create 100) (sprite/get-current-frame sprite)) "get-current-frame -> new frame selected")
     (is (= {:frame-idx 1 :layer-idx 0} (sprite/get-current-cel-pos sprite)) "cel of new layer should be current")
     (is (= (cel/create (sprite/get-size sprite) {:frame-idx 1 :layer-idx 0})
            (sprite/get-current-cel sprite)) "new cel should be created")
     (is (= (sprite/get-cel {:frame-idx 0 :layer-idx 0} initial-sprite)
            (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite)) "cel from first frame should not be changed")

     (apply-current-tool [{:x 0 :y 0}])
     (def updated-sprite (-> @!db :sprite))
     (is (= (->> (cel/create (sprite/get-size sprite)
                             {:frame-idx 1 :layer-idx 0})
                 (cel/set-pixels {{:x 0 :y 0} "black"}))
            (sprite/get-current-cel updated-sprite))
         "current cel should be updated"))

   (testing "add 3 frame"
     (def !db (rf/subscribe [:db]))
     (rf/dispatch-sync [::events/initialize-db])
     (def initial-db @(rf/subscribe [:db]))
     (def initial-sprite (-> initial-db :sprite))
     (rf/dispatch-sync [::events/add-frame])
     (rf/dispatch-sync [::events/add-frame])
     (def sprite (-> @!db :sprite))
     (is (= [(frame/create 100) (frame/create 100) (frame/create 100)] (:frames sprite)) "new frame created")
     (is (= (frame/create 100) (sprite/get-current-frame sprite)) "get-current-frame -> new frame selected")
     (is (= {:frame-idx 2 :layer-idx 0} (sprite/get-current-cel-pos sprite)) "cel of new layer should be current")
     (is (= (cel/create (sprite/get-size sprite)
                        {:frame-idx 2 :layer-idx 0})
            (sprite/get-current-cel sprite)) "new cel should be created")
     (is (= (sprite/get-cel {:frame-idx 0 :layer-idx 0} initial-sprite)
            (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite)) "cel from 1 frame should not be changed")
     (is (= (cel/create (sprite/get-size sprite)
                        {:frame-idx 1 :layer-idx 0})
            (sprite/get-cel {:frame-idx 1 :layer-idx 0} sprite)) "cel from 2 frame should not be changed")

     (apply-current-tool [{:x 0 :y 0}])
     (def updated-sprite (-> @!db :sprite))
     (is (= (->> (cel/create (sprite/get-size sprite)
                             {:frame-idx 2 :layer-idx 0})
                 (cel/set-pixels {{:x 0 :y 0} "black"}))
            (sprite/get-current-cel updated-sprite))
         "current cel should be updated"))))

(deftest test-add-layer
  (rf-test/run-test-sync
   (testing "add 2 layer"
     (rf/dispatch-sync [::events/initialize-db])
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))
     (def initial-sprite (-> initial-db :sprite))

     (rf/dispatch-sync [::events/add-layer])
     (def sprite (-> @!db :sprite))
     (def new-layer (layer/create "Layer 2" nil))
     (is (= (:frames sprite) (:frames initial-sprite)) "frames should not be changes")
     (is (= [(layer/create "Layer 1" nil) (layer/create "Layer 2" nil)] (:layers sprite)) "new layer created")
     (is (= {:frame-idx 0 :layer-idx 1} (sprite/get-current-cel-pos sprite)) "cel of new layer should be current")
     (is (= new-layer (sprite/get-current-layer sprite)) "get-current-layer -> new layer selected")
     (is (= (cel/create (sprite/get-size sprite) {:frame-idx 0 :layer-idx 1}) (sprite/get-current-cel sprite)) "new cel should be created")
     (is (= (sprite/get-cel {:frame-idx 0 :layer-idx 0} initial-sprite)
            (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite)) "cel from previous layer should not be changed")

     (apply-current-tool [{:x 0 :y 0}])
     (def updated-sprite (-> @!db :sprite))
     (is (= (->> (cel/create (sprite/get-size sprite) {:frame-idx 0 :layer-idx 1})
                 (cel/set-pixels {{:x 0 :y 0} "black"}))
            (sprite/get-current-cel updated-sprite))
         "current cel should be updated"))

   (testing "add 3 layer"
     (rf/dispatch-sync [::events/initialize-db])
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))
     (def initial-sprite (-> initial-db :sprite))

     (rf/dispatch-sync [::events/add-layer])
     (rf/dispatch-sync [::events/add-layer])
     (def sprite (-> @!db :sprite))
     (def new-layer (layer/create "Layer 3" nil))
     (is (= (:frames sprite) (:frames initial-sprite)) "frames should not be changes")
     (is (= [(layer/create "Layer 1" nil) (layer/create "Layer 2" nil) new-layer] (:layers sprite)) "new layer created")
     (is (= {:frame-idx 0 :layer-idx 2} (sprite/get-current-cel-pos sprite)) "cel of new layer should be current")
     (is (= new-layer (sprite/get-current-layer sprite)) "get-current-layer -> new layer selected")
     (is (= (cel/create (sprite/get-size sprite) {:frame-idx 0 :layer-idx 2}) (sprite/get-current-cel sprite)) "new cel should be created")
     (is (= (sprite/get-cel {:frame-idx 0 :layer-idx 0} initial-sprite)
            (sprite/get-cel {:frame-idx 0 :layer-idx 0} sprite)) "cel from 1 layer should not be changed")
     (is (= (cel/create (sprite/get-size sprite) {:frame-idx 0 :layer-idx 1})
            (sprite/get-cel {:frame-idx 0 :layer-idx 1} sprite)) "cel from 2 layer should not be changed")

     (apply-current-tool [{:x 0 :y 0}])
     (def updated-sprite (-> @!db :sprite))
     (is (= (->> (cel/create (sprite/get-size sprite) {:frame-idx 0 :layer-idx 2})
                 (cel/set-pixels {{:x 0 :y 0} "black"}))
            (sprite/get-current-cel updated-sprite))
         "current cel should be updated"))))

(deftest test-selection
  (defn create-fixture []
    (rf/dispatch-sync [::events/initialize-db])
    (def initial-db @(rf/subscribe [:db]))
    (def !db (rf/subscribe [:db]))
    (rf/dispatch-sync [::events/add-frame])
    (rf/dispatch-sync [::events/add-frame])
    (rf/dispatch-sync [::events/add-layer])
    (rf/dispatch-sync [::events/add-layer]))

  (rf-test/run-test-async
   (testing "select-only-1-cel"
     (create-fixture)

     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
     (is (= {:frame-idx 0 :layer-idx 0} (-> @!db :sprite sprite/get-current-cel-pos)))
     (is (= (-> initial-db :sprite sprite/get-current-cel) (-> @!db :sprite sprite/get-current-cel)))

     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 1}])
     (is (= {:frame-idx 1 :layer-idx 1} (-> @!db :sprite sprite/get-current-cel-pos)))
     (is (= (cel/create (-> @!db :sprite sprite/get-size) {:frame-idx 1 :layer-idx 1}) (-> @!db :sprite sprite/get-current-cel))))

   (testing "select-frame"
     (create-fixture)
     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
     (rf/dispatch-sync [::events/select-frame 2])
     (is (= {:frame-idx 2 :layer-idx 0} (-> @!db :sprite sprite/get-current-cel-pos))))

   (testing "select-layer"
     (create-fixture)
     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
     (rf/dispatch-sync [::events/select-layer 2])
     (is (= {:frame-idx 0 :layer-idx 2} (-> @!db :sprite sprite/get-current-cel-pos))))

   (testing "toggle-cel-to-selection"
     (testing "add to selection"
       (create-fixture)
       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
       (is (= [{:selected true :current false :pos {:frame-idx 0 :layer-idx 0}}
               {:selected true :current true :pos {:frame-idx 1 :layer-idx 1}}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

     (testing "remove from selection"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])

       (is (= [{:selected true :current true :pos {:frame-idx 0 :layer-idx 0}}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

     (testing "when only 1 selected, it should not be removed"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])

       (is (= [{:selected true :current true :pos {:frame-idx 0 :layer-idx 0}}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

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
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline]))))))

   (testing "add-cels-range-to-selection"
     (testing "add range from left to the right"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])

       (is (= [{:pos {:frame-idx 0, :layer-idx 0},
                :current false,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 0},
                :current false,
                :selected true}
               {:pos {:frame-idx 0, :layer-idx 1},
                :current false,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 1},
                :current true,
                :selected true}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

     (testing "add range from right to the left"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])

       (is (= [{:pos {:frame-idx 0, :layer-idx 0},
                :current true,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 0},
                :current false,
                :selected true}
               {:pos {:frame-idx 0, :layer-idx 1},
                :current false,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 1},
                :current false,
                :selected true}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

     (testing "when create range for already added then should select it"
       (create-fixture)
       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])

       (is (= [{:pos {:frame-idx 0, :layer-idx 0},
                :current true,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 0},
                :current false,
                :selected true}
               {:pos {:frame-idx 0, :layer-idx 1},
                :current false,
                :selected true}
               {:pos {:frame-idx 1, :layer-idx 1},
                :current false,
                :selected true}]
              (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline]))))))

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
            (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

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
            (get-selected-cels-from-timeline @(rf/subscribe [::subs/timeline])))))

   (testing "selection after frame moving")

   (testing "selection after layer moving")))

(deftest test-duplicate-frame
  (rf-test/run-test-sync
   (rf/dispatch-sync [::events/initialize-db])
   (def !db (rf/subscribe [:db]))
   (def initial-db @(rf/subscribe [:db]))

   (rf/dispatch-sync [::events/add-layer])
   (rf/dispatch-sync [::events/duplicate-frame])

   (def sprite (-> @!db :sprite))
   (is (= [(frame/create 100) (frame/create 100)] (:frames sprite)))
   (is (= [(layer/create "Layer 1" nil) (layer/create "Layer 2" nil)] (:layers sprite)))
   (is (= (dissoc (sprite/get-cel {:layer-idx 0 :frame-idx 0} sprite)
                  :pos)
          (dissoc (sprite/get-cel {:layer-idx 0 :frame-idx 1} sprite)
                  :pos)))
   (is (= (dissoc (sprite/get-cel {:layer-idx 1 :frame-idx 0} sprite)
                  :pos)
          (dissoc (sprite/get-cel {:frame-idx 1 :layer-idx 1} sprite)
                  :pos)))
   (is (= [(sprite/get-cel {:layer-idx 0 :frame-idx 0} sprite)
           (sprite/get-cel {:layer-idx 1 :frame-idx 0} sprite)]
          (sprite/get-frame-cels 0 sprite)))))

(deftest test-remove-frame
  (testing "remove frame"
    (rf-test/run-test-sync
     (rf/dispatch-sync [::events/initialize-db])
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))

     (rf/dispatch-sync [::events/add-frame])
     (rf/dispatch-sync [::events/remove-frame])

     (is (= (-> initial-db :sprite) (-> @!db :sprite))))))

(deftest test-remove-layer
  (testing "remove layer"
    (rf-test/run-test-sync
     (rf/dispatch-sync [::events/initialize-db])
     (def !db (rf/subscribe [:db]))
     (def initial-db @(rf/subscribe [:db]))

     (rf/dispatch-sync [::events/add-layer])
     (rf/dispatch-sync [::events/remove-layer 1])

     (is (= (-> initial-db :sprite) (-> @!db :sprite))))))

(deftest test-links
  (defn create-fixture []
    (rf/dispatch-sync [::events/initialize-db])
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

    (def initial-cel (->> initial-db :sprite (sprite/get-cel {:frame-idx 0 :layer-idx 0})))
    (is (= [{:pixels (:pixels initial-cel) :pos {:frame-idx 0 :layer-idx 0} :current false :selected true :group-number 0}
            {:pixels (:pixels initial-cel) :pos {:frame-idx 1 :layer-idx 0} :current true :selected true :group-number 0}]
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

    (is (= [{:pos {:frame-idx 0 :layer-idx 0} :pixels (-> (vec (repeat (* 8 8) transparent-color))
                                                          (assoc 0 "black"))}
            {:pos {:frame-idx 1 :layer-idx 0} :pixels (-> (vec (repeat (* 8 8) transparent-color))
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

      (def initial-cel (->> initial-db :sprite (sprite/get-cel {:frame-idx 0 :layer-idx 0})))
      (is (= [{:pos {:frame-idx 0, :layer-idx 0},
               :group-number 0}
              {:pos {:frame-idx 0, :layer-idx 1},
               :group-number 0}
              {:pos {:frame-idx 1, :layer-idx 0},
               :group-number 0}
              {:pos {:frame-idx 1, :layer-idx 1},
               :group-number 0}]
             (->> (:cels @(rf/subscribe [::subs/timeline]))
                  (filter :group-number)
                  (map #(select-keys % [:pos :group-number]))))))))

#_(enable-console-print!)
#_(run-tests)
