(ns pixel-art.tool.rectangle-test
  (:require [cljs.test :as t]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.rectangle :as rectangle]
            [pixel-art.utils-test :as u]))

(def source-frame (frame/create {:width 2 :height 3}))

(t/deftest behaviour-test
  (u/run-test-cases rectangle/handle-mouse-event
                    {:source-frame source-frame
                     :overlay-frame source-frame
                     :tool {:type :rectangle}
                     :color "black"}
                    [[{:type :mouse-down
                       :pos {:x 0 :y 0}}
                      (fn [{:keys [source-frame event color]}]
                        {:tool {:type :rectangle :state {:mouse-down-pos {:x 0 :y 0}}}
                         :overlay-frame (frame/set-pixels [{:pos (:pos event) :color color}] source-frame)})]
                     [{:type :mouse-move
                       :pos {:x 1 :y 1}}
                      (fn [{:keys [source-frame color]}]
                        {:overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color color}
                                                           {:pos {:x 0 :y 1} :color color}
                                                           {:pos {:x 1 :y 0} :color color}
                                                           {:pos {:x 1 :y 1} :color color}]
                                                          source-frame)})]
                     [{:type :mouse-move
                       :pos {:x 2 :y 2}}
                      (fn [{:keys [source-frame color]}]
                        {:overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color color}
                                                           {:pos {:x 1 :y 0} :color color}
                                                           {:pos {:x 2 :y 0} :color color}
                                                           {:pos {:x 0 :y 1} :color color}
                                                           {:pos {:x 2 :y 1} :color color}
                                                           {:pos {:x 0 :y 2} :color color}
                                                           {:pos {:x 1 :y 2} :color color}
                                                           {:pos {:x 2 :y 2} :color color}]
                                                          source-frame)})]
                     [{:type :mouse-up
                       :pos {:x 2 :y 2}}
                      (fn [{:keys [source-frame color]}]
                        {:overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color color}
                                                           {:pos {:x 1 :y 0} :color color}
                                                           {:pos {:x 2 :y 0} :color color}
                                                           {:pos {:x 0 :y 1} :color color}
                                                           {:pos {:x 2 :y 1} :color color}
                                                           {:pos {:x 0 :y 2} :color color}
                                                           {:pos {:x 1 :y 2} :color color}
                                                           {:pos {:x 2 :y 2} :color color}]
                                                          source-frame)
                         :commit-changes true})]]))
