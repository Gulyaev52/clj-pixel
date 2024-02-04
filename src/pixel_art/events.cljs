(ns pixel-art.events
  (:require
   [re-frame.core :as re-frame]
   [pixel-art.db :as db]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn-traced [_ _]
   db/default-db))
