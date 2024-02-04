(ns pixel-art.model.frame-test
  (:require [cljs.test :as t]
            [pixel-art.model.frame :as frame]))

(t/deftest create-test
  (t/is (= {:size {:width 2 :height 3}
            :pixels [frame/transparent-color frame/transparent-color frame/transparent-color frame/transparent-color frame/transparent-color frame/transparent-color]}
           (frame/create {:width 2 :height 3}))))

(t/deftest set-pixels-test
  (let [res-frame (->> (frame/create {:width 2 :height 3})
                       (frame/set-pixels [{:pos {:x 0 :y 0} :color "black"}
                                          {:pos {:x 1 :y 1} :color "red"}
                                          {:pos {:x 0 :y 2} :color "yellow"}]))]
    (t/is (= res-frame {:pixels
                        ["black" frame/transparent-color frame/transparent-color "red" "yellow" frame/transparent-color],
                        :size {:width 2, :height 3}}))))

(t/deftest get-pixel-test
  (let [fram (->> (frame/create {:width 2 :height 3})
                  (frame/set-pixels [{:pos {:x 0 :y 0} :color "black"}
                                     {:pos {:x 1 :y 1} :color "red"}]))]
    (t/is (= "black"
             (frame/get-pixel {:x 0 :y 0} fram)))
    (t/is (= "red"
             (frame/get-pixel {:x 1 :y 1} fram)))
    (t/is (= frame/transparent-color
             (frame/get-pixel {:x 0 :y 1} fram)))))
