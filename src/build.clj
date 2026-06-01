(ns build
  (:require
   [clojure.java.io :as io]
   [shadow.css.build :as cb]))

(defn css-release [& _args]
  (let [build-state
        (-> (cb/start)
            (dissoc :preflight-src)
            (cb/index-path (io/file "src") {})
            (cb/generate
             '{:ui
               {:entries [pixel-art.core]}})
            (cb/write-outputs-to (io/file "resources" "public" "css")))]
    (doseq [mod    (vals (:chunks build-state))
            {:keys [warning-type] :as warning} (:warnings mod)]
      (prn [:CSS (name warning-type) (dissoc warning :warning-type)]))))
