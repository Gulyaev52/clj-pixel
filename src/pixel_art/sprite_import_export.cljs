(ns pixel-art.sprite-import-export ;; todo: project instead of sprite?
  (:require [pixel-art.db :refer [get-db]]
            [pixel-art.utils.coll :as coll]
            [re-frame.core :as re-frame]))

(def sprite-file-ext "json")

(defn- sprite->file-desc [sprite]
  (let [exported-sprite (update sprite
                                :cels
                                #(coll/map-matrix (fn [c] (dissoc c :current :selected)) %))]
    {:file-name (str "pixel-project."  sprite-file-ext)
     :content (-> {:version "1" :sprite exported-sprite}
                  clj->js
                  (#(. js/JSON stringify %)))
     :content-type :json}))

(defn- parse-sprite [file-desc]
  (try
    (if-let [sprite (-> (. js/JSON (parse (:content file-desc)))
                        (js->clj :keywordize-keys true)
                        :sprite)]
      {:ok (update sprite
                   :cels
                   #(coll/map-matrix (fn [c pos] (assoc c
                                                        :current (= {:x 0 :y 0} pos)
                                                        :selected (= {:x 0 :y 0} pos))) %))}
      {:error "invalid format of file"})
    (catch js/SyntaxError _
      {:error "invalid format of file"})))

(re-frame/reg-event-fx
 ::export-sprite-as-file
 (fn [{:keys [db]}]
   (let [file-desc (sprite->file-desc (:sprite db))]
     {:fx [[:download-file file-desc]]})))

(re-frame/reg-event-fx
 ::import-sprite-from-file
 (fn [{:keys [db]} [_ file-desc]]
   (let [parse-result (parse-sprite file-desc)]
     (if-let [sprite (:ok parse-result)]
       {:db (get-db (merge {:sprite sprite}
                           (select-keys db [:palettes :primary-color :secondary-color])))}
       {:fx [[:show-alert (:error parse-result)]]}))))
