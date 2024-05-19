(ns pixel-art.utils.interceptor
  (:require [re-frame.core :as re-frame]
            [sc.api]))

(def !first-run (atom true))

;; todo: refactor
(defn on-changes
  [k get-db-elem f]
  (re-frame/->interceptor
   :id    k
   :after (fn on-change-after
            [context]
            (cond
              @!first-run
              (do
                (reset! !first-run false)
                (let [new-db   (re-frame/get-effect context :db)
                      res (f {:db new-db :old nil :new (get-db-elem new-db)})]
                  (update context :effects #(merge % res))))

              (and (seq (re-frame/get-coeffect context :db))
                   (re-frame/get-effect context :db))
              (let [new-db   (re-frame/get-effect context :db)
                    old-db   (re-frame/get-coeffect context :db)

                                 ;; work out if any "inputs" have changed
                    new-ins      (get-db-elem new-db)
                    old-ins      (get-db-elem old-db)
                                 ;; make sure the db is actually set in the effect
                    changed-ins? (and (contains? (re-frame/get-effect context) :db)
                                      (false? (identical? new-ins old-ins)))]

                             ;; if one of the inputs has changed, then run 'f'
                (if changed-ins?
                  (let [res (f {:db new-db :old old-ins :new new-ins})]
                    (update context :effects #(merge % res {:fx (concat (:fx %) (:fx res))})))
                  context))

              :else
              context))))
