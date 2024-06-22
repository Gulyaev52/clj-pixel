(ns pixel-art.events-test
  (:require
   [pixel-art.events :as events]
   [day8.re-frame.test :as rf-test]
   [cljs.test :refer-macros [deftest testing is run-tests]]
   [re-frame.core :as rf]
   [pixel-art.model.frame :as frame]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.layer :as layer]))

(rf/reg-sub
 :db
 (fn [db]
   db))

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
       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 0 :layer-idx 0} {:frame-idx 1 :layer-idx 1}}
              (:selected-cels-pos sprite))
           "should add cel to selection if it wasn't there")
       (is (= {:frame-idx 1 :layer-idx 1} (sprite/get-current-cel-pos sprite))
           "added cel should be selected"))

     (testing "remove from selection"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])

       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 0 :layer-idx 0}}
              (:selected-cels-pos sprite))
           "should remove cel that there is in selection")
       (is (= {:frame-idx 0 :layer-idx 0} (sprite/get-current-cel-pos sprite))
           "previous cel should be current after removing"))

     (testing "when only 1 selected, it should not be removed"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])

       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 0 :layer-idx 0}}
              (:selected-cels-pos sprite))))

     (testing "when remove not current then current should not be changed"
       (create-fixture)

       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 2 :layer-idx 2}]) ;; current
       (rf/dispatch-sync [::events/toggle-cel-to-selection {:frame-idx 0 :layer-idx 0}])

       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 1 :layer-idx 1}
                {:frame-idx 2 :layer-idx 2}}
              (:selected-cels-pos sprite)))
       (is (= {:frame-idx 2 :layer-idx 2}
              (sprite/get-current-cel-pos sprite)))))

   (testing "add-cels-range-to-selection"
     (testing "add range from left to the right"
       (create-fixture)
       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 0 :layer-idx 0}
                {:frame-idx 0 :layer-idx 1}
                {:frame-idx 1 :layer-idx 0}
                {:frame-idx 1 :layer-idx 1}}
              (:selected-cels-pos sprite)))
       (is (= {:frame-idx 1 :layer-idx 1} (sprite/get-current-cel-pos sprite))))

     (testing "add range from right to the left"
       (create-fixture)
       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])
       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 1 :layer-idx 1}
                {:frame-idx 0 :layer-idx 0}
                {:frame-idx 0 :layer-idx 1}
                {:frame-idx 1 :layer-idx 0}}
              (:selected-cels-pos sprite)))
       (is (= {:frame-idx 0 :layer-idx 0} (sprite/get-current-cel-pos sprite))))

     (testing "when create range for already added then should select it"
       (create-fixture)
       (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
       (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 0 :layer-idx 0}])
       (def sprite (-> @!db :sprite))
       (is (= #{{:frame-idx 0 :layer-idx 0}
                {:frame-idx 0 :layer-idx 1}
                {:frame-idx 1 :layer-idx 0}
                {:frame-idx 1 :layer-idx 1}}
              (:selected-cels-pos sprite)))
       (is (= {:frame-idx 0 :layer-idx 0} (sprite/get-current-cel-pos sprite)))))

   (testing "selection after frame removing"
     (create-fixture)

     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
     (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
     (rf/dispatch-sync [::events/remove-frame 1])

     (is (= #{{:frame-idx 0 :layer-idx 0}
              {:frame-idx 0 :layer-idx 1}}
            (:selected-cels-pos (:sprite @!db))))
     (is (= {:frame-idx 0 :layer-idx 1}
            (sprite/get-current-cel-pos (:sprite @!db)))))

   (testing "selection after layer removing"
     (create-fixture)

     (rf/dispatch-sync [::events/select-only-1-cel {:frame-idx 0 :layer-idx 0}])
     (rf/dispatch-sync [::events/add-cels-range-to-selection {:frame-idx 1 :layer-idx 1}])
     (rf/dispatch-sync [::events/remove-layer 1])

     (is (= #{{:frame-idx 0 :layer-idx 0}
              {:frame-idx 1 :layer-idx 0}}
            (:selected-cels-pos (:sprite @!db))))
     (is (= {:frame-idx 1 :layer-idx 0}
            (sprite/get-current-cel-pos (:sprite @!db)))))

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
  #_(testing "remove frame"
      (rf-test/run-test-sync
       (rf/dispatch-sync [::events/initialize-db])
       (def !db (rf/subscribe [:db]))
       (def initial-db @(rf/subscribe [:db]))

       (rf/dispatch-sync [::events/add-frame])
       (rf/dispatch-sync [::events/remove-frame])

       (is (= (-> initial-db :sprite) (-> @!db :sprite))))))

(deftest test-remove-layer
  #_(testing "remove layer"
      (rf-test/run-test-sync
       (rf/dispatch-sync [::events/initialize-db])
       (def !db (rf/subscribe [:db]))
       (def initial-db @(rf/subscribe [:db]))

       (rf/dispatch-sync [::events/add-layer])
       (rf/dispatch-sync [::events/remove-layer 1])

       (is (= (:selected-cels-pos (-> initial-db :sprite)) (:selected-cels-pos (-> @!db :sprite)))))))

#_(enable-console-print!)
#_(run-tests)
