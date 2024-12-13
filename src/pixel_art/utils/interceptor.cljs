(ns pixel-art.utils.interceptor
  (:require [re-frame.core :as re-frame]
            [sc.api]))

(def !first-run (atom true))

(defn on-paths-change
  [k paths f]
  (re-frame/->interceptor
   :id    k
   :after (fn on-change-after
            [context]
            (let [new-db   (re-frame/get-effect context :db)
                  old-db   (re-frame/get-coeffect context :db)

                     ;; work out if any "inputs" have changed
                  new-ins      (map #(get new-db %) paths)
                  old-ins      (map #(get old-db %) paths)
                     ;; make sure the db is actually set in the effect
                  changed-ins? (and (contains? (re-frame/get-effect context) :db)
                                    (some false? (map identical? new-ins old-ins)))]

                 ;; if one of the inputs has changed, then run 'f'
              (if changed-ins?
                (let [res (f {:db new-db :fields (into {} (map vector paths new-ins))})]
                  (update context :effects #(merge % res {:fx (concat (:fx %) (:fx res))})))
                context)))))
