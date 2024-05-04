(ns pixel-art.utils.interceptor
  (:require [re-frame.core :as re-frame]))

;; todo: refactor
(defn run-fx-on-changes
  [get-db-elem f]
  (re-frame/->interceptor
   :id    :run-fx-on-changes
   :after (fn on-change-after
            [context]
            (if (and (seq (re-frame/get-coeffect context :db))
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
                  (let [effects (f new-ins new-db)]
                    (update-in context [:effects :fx] #(concat % effects)))
                  context))
              context))))
