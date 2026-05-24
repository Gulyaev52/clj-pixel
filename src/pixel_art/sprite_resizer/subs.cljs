(ns pixel-art.sprite-resizer.subs
  (:require
   [pixel-art.canvas :as canvas]
   [pixel-art.sprite-resizer.utils :refer [resize-sprite]]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::previews
 (fn [db]
   (when (= (-> db :sprite-resizer :opened) true)
     (let [{:keys [sprite] {:keys [settings]} :sprite-resizer} db
           resized-sprite (resize-sprite sprite settings)
           previews (for [frame-idx (range 0 (count (:frames sprite)))]
                      (->> (canvas/create-canvas (:size resized-sprite))
                           (canvas/draw-frame-on-single-canvas frame-idx resized-sprite)
                           (#(canvas/to-data-url % "png"))))]
       previews))))

(re-frame/reg-sub
 ::settings
 (fn [db]
   (-> db :sprite-resizer :settings)))

(re-frame/reg-sub
 ::opened
 (fn [db]
   (-> db :sprite-resizer :opened)))
