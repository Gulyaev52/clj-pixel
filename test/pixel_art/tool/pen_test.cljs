(ns pixel-art.tool.pen-test
  (:require [cljs.test :as t]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.pen :as pen]
            [pixel-art.utils-test :as u]))

(def source-frame (frame/create {:width 2 :height 3}))

(t/deftest behaviour-test
  (u/run-test-cases pen/behaviour
                    {:source-frame source-frame
                     :overlay-frame source-frame
                     :tool {:type :pen}
                     :color "black"}
                    [[{:type :mouse-down
                       :pos {:x 0 :y 0}}
                      (fn [{:keys [overlay-frame event color]}]
                        {:overlay-frame (frame/set-pixels [{:pos (:pos event) :color color}] overlay-frame)})]
                     [{:type :mouse-move
                       :pos {:x 1 :y 1}}
                      (fn [{:keys [overlay-frame event color]}]
                        {:overlay-frame (frame/set-pixels [{:pos (:pos event) :color color}] overlay-frame)})]
                     [{:type :mouse-up
                       :pos {:x 1 :y 1}}
                      (fn [{:keys [overlay-frame]}]
                        {:overlay-frame overlay-frame
                         :commit-changes true})]]))
