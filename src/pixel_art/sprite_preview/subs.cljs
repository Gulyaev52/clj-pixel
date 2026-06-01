(ns pixel-art.sprite-preview.subs
  (:require
   [pixel-art.model.sprite :as sprite]
   [pixel-art.utils.canvas :as canvas]
   [pixel-art.model.sprite-canvas :as sprite-canvas]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::sprite-preview
 (fn [db]
   (let [sprite-preview (:sprite-preview db)
         frame-imgs (let [sprite (:sprite db)
                          frames (:frames sprite)]
                      (->> (for [frame-idx (range 0 (count frames))]
                             [frame-idx
                              (canvas/generate-data-url
                               #(sprite-canvas/draw-frame-on-single-canvas frame-idx sprite %)
                               (sprite/get-size sprite))])
                           (into {})))]
     (assoc sprite-preview :frame-imgs frame-imgs))))
