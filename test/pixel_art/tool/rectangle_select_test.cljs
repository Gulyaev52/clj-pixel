(ns pixel-art.tool.rectangle-select-test
  (:require [cljs.test :as t]
            [pixel-art.model.frame :as frame]
            [pixel-art.tool.rectangle-select :as rectangle-select]
            [pixel-art.utils-test :as u]))

(def source-frame (->> (frame/create {:width 4 :height 4})
                       (frame/set-pixels (->> (for [x (range 0 4)]
                                                (for [y (range 0 4)]
                                                  {:pos {:x x :y y} :color "red"}))
                                              flatten))
                       (frame/set-pixels [{:pos {:x 0 :y 0} :color "black"}
                                          {:pos {:x 1 :y 0} :color "black"}
                                          {:pos {:x 0 :y 1} :color "black"}
                                          {:pos {:x 1 :y 1} :color "black"}])))

(defn display-results [results]
  (doseq [r results]
    (println (:event r))
    (frame/display-frame (->> (:result r) :overlay-frame))
    (println (:tool (:result r)))
    (println)))

(def transpare-color "t")

(t/deftest behaviour-test
  (t/testing "when mode is select"
    (u/run-test-cases
     rectangle-select/handle-mouse-event
     {:source-frame source-frame
      :overlay-frame source-frame
      :tool {:type :rectangle-select}
      :color "black"}
     [[{:type :mouse-down
        :pos {:x 0 :y 0}}
       (fn [{:keys [source-frame event]}]
         {:tool {:type :rectangle-select :state {:initial-mouse-down-pos {:x 0 :y 0}}}
          :overlay-frame (frame/set-pixels [{:pos (:pos event) :color transpare-color}] source-frame)})]
      [{:type :mouse-move
        :pos {:x 1 :y 1}}
       (fn [{:keys [source-frame]}]
         (let [selection [{:pos {:x 0, :y 0}, :color "black"}
                          {:pos {:x 0, :y 1}, :color "black"}
                          {:pos {:x 1, :y 0}, :color "black"}
                          {:pos {:x 1, :y 1}, :color "black"}]]
           {:tool {:type :rectangle-select
                   :state {:initial-mouse-down-pos {:x 0, :y 0}
                           :initial-selection selection
                           :selection selection}}
            :overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color transpare-color}
                                              {:pos {:x 0 :y 1} :color transpare-color}
                                              {:pos {:x 1 :y 0} :color transpare-color}
                                              {:pos {:x 1 :y 1} :color transpare-color}]
                                             source-frame)}))]
      [{:type :mouse-up
        :pos {:x 1 :y 1}}
       (fn [{:keys [source-frame]}]
         (let [selection [{:pos {:x 0, :y 0}, :color "black"}
                          {:pos {:x 0, :y 1}, :color "black"}
                          {:pos {:x 1, :y 0}, :color "black"}
                          {:pos {:x 1, :y 1}, :color "black"}]]
           {:tool {:type :rectangle-select
                   :state {:mode :move-selection
                           :initial-mouse-down-pos {:x 0, :y 0}
                           :initial-selection selection
                           :selection selection}}
            :overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color transpare-color}
                                              {:pos {:x 0 :y 1} :color transpare-color}
                                              {:pos {:x 1 :y 0} :color transpare-color}
                                              {:pos {:x 1 :y 1} :color transpare-color}]
                                             source-frame)}))]]))

  (t/testing "when mode is move-selection"
    (let [initial-selection [{:pos {:x 0 :y 0} :color "black"}
                             {:pos {:x 0 :y 1} :color "black"}
                             {:pos {:x 1 :y 0} :color "black"}
                             {:pos {:x 1 :y 1} :color "black"}]
          tool {:type :rectangle-select
                :state {:mode :move-selection
                        :selection initial-selection
                        :initial-selection initial-selection}}]
      (u/run-test-cases
       rectangle-select/handle-mouse-event
       {:source-frame source-frame
        :overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color transpare-color}
                                          {:pos {:x 0 :y 1} :color transpare-color}
                                          {:pos {:x 1 :y 0} :color transpare-color}
                                          {:pos {:x 1 :y 1} :color transpare-color}]
                                         source-frame)
        :tool tool
        :color "black"}
       [[{:type :mouse-down
          :pos {:x 0 :y 0}}
         (fn [{:keys [source-frame]}]
           {:tool (assoc-in tool [:state :initial-mouse-down-pos] {:x 0 :y 0})
            :overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color transpare-color}
                                              {:pos {:x 0 :y 1} :color transpare-color}
                                              {:pos {:x 1 :y 0} :color transpare-color}
                                              {:pos {:x 1 :y 1} :color transpare-color}]
                                             source-frame)})]
        [{:type :mouse-move
          :pos {:x 1 :y 1}}
         (fn [{:keys [source-frame]}]
           {:tool {:type :rectangle-select,
                   :state
                   {:mode :move-selection,
                    :selection
                    [{:pos {:x 1, :y 1}, :color "black"}
                     {:pos {:x 1, :y 2}, :color "black"}
                     {:pos {:x 2, :y 1}, :color "black"}
                     {:pos {:x 2, :y 2}, :color "black"}],
                    :initial-selection initial-selection,
                    :initial-mouse-down-pos {:x 0, :y 0}}}
            :overlay-frame (frame/set-pixels [{:pos {:x 0 :y 0} :color frame/transparent-color}
                                              {:pos {:x 0 :y 1} :color frame/transparent-color}
                                              {:pos {:x 1 :y 0} :color frame/transparent-color}

                                              {:pos {:x 1 :y 1} :color "black"}
                                              {:pos {:x 2 :y 1} :color "black"}
                                              {:pos {:x 1 :y 2} :color "black"}
                                              {:pos {:x 2 :y 2} :color "black"}]
                                             source-frame)})]]))))
