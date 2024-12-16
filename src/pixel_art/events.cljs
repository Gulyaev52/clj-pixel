(ns pixel-art.events
  (:require
   [pixel-art.backup :as backup]
   [pixel-art.canvas :as canvas]
   [pixel-art.db :as db]
   [pixel-art.fx]
   [pixel-art.history :as history]
   [pixel-art.keyboard-shortcuts :as keyboard-shortcuts]
   [pixel-art.model.cel :as cel]
   [pixel-art.model.color :as color]
   [pixel-art.model.frame :as frame]
   [pixel-art.model.layer :as layer]
   [pixel-art.model.sprite :as sprite]
   [pixel-art.project-save-load]
   [pixel-art.project-settings :as project-settings]
   [pixel-art.re-pressed.core :as rp]
   [pixel-art.tool.core :as tool]
   [pixel-art.tool.utils :refer [commit-changes-and-init-tool get-current-cel]]
   [pixel-art.utils.geometry :as geometry]
   [re-frame.core :as re-frame]
   [re-frame.db]
   [sc.api]))

;; todo: remove
(add-watch re-frame.db/app-db :def
           (fn [_ _ _ new]
             (def db new)))

(re-frame/reg-event-fx
 ::start-app
 (fn []
   {:db {:initial-loading true}
    :fx [[:load-initial-data]]}))

(re-frame/reg-fx
 :load-initial-data
 (fn []
   (.. (backup/init-db+)
       (then backup/get-backup+)
       (then (fn [backup]
               (re-frame/dispatch [:initialize-db backup])))
       (catch (fn []
                (re-frame/dispatch [:initialize-db]))))))

(def dispatch-set-keydown-rules
  (let [convert-shortcut-keys #(-> %
                                   (assoc :keyCode (keyboard-shortcuts/key->code (:key %)))
                                   (dissoc :key))]
    [:dispatch [::rp/set-keydown-rules
                {:event-keys (->> keyboard-shortcuts/shortcuts-by-types
                                  vals
                                  flatten
                                ;; Order matters, and the first matching key combination will consume the event. So for example, if you want to listen for both forward arrow ({:keyCode 37}) and control + forward arrow ({:keyCode 37 :ctrlKey true}), then you must put the combination before the singleton. 
                                  (sort-by (fn [{:keys [keys]}] (not (some :ctrlKey keys))))
                                  (map (fn [{:keys [action keys]}]
                                         (into [action] (->> keys
                                                             (map convert-shortcut-keys)
                                                             (map vector))))))
                 :prevent-default-keys (->> keyboard-shortcuts/shortcuts-by-types
                                            vals
                                            flatten
                                            (filter :prevent-default-keys)
                                            (mapcat (fn [{:keys [keys]}] keys))
                                            (map convert-shortcut-keys))}]]))

(re-frame/reg-cofx
 :viewport-size
 (fn [coeffects _]
   (let [viewport-rect (.. js/document (getElementById "viewport") (getBoundingClientRect))
         viewport-size {:width (. viewport-rect -width) :height (. viewport-rect -height)}]
     (assoc coeffects :viewport-size viewport-size))))

(re-frame/reg-event-fx
 :initialize-db
 [(re-frame/inject-cofx :viewport-size)]
 (fn [{:keys [viewport-size]} [_ settings]]
   (let [initial-db (if settings
                      (db/get-db settings viewport-size)
                      (db/get-db (assoc project-settings/default-palettes-and-current-colors
                                        :sprite (project-settings/create-empty-sprite {:width 64 :height 64})
                                        :new-project-modal-opened true)
                                 viewport-size))]
     {:db initial-db
      :fx [[:dispatch [::rp/add-keyboard-event-listener "keydown"]]
           dispatch-set-keydown-rules]})))

(re-frame/reg-event-fx
 ::select-tool
 (fn [{:keys [db]} [_ tool-type]]
   (let [tool (tool/init tool-type)]
     (commit-changes-and-init-tool db
                                   (get-in db [:tool :state :changes])
                                   tool))))

(re-frame/reg-event-fx
 ::set-keyboard-shortcuts-modal-opened
 (fn [{:keys [db]} [_ opened]]
   {:db (assoc db :keyboard-shortcuts-modal-opened opened)}))

(re-frame/reg-event-fx
 ::change-tool-option ;; todo: set
 (fn [{:keys [db]} [_ field value]]
   (let [tool-type (-> db :tool :type)]
     {:db (assoc-in db [:tools-options tool-type field] value)})))

(re-frame/reg-event-fx
 ::enable-pixels-grid
 (fn [{:keys [db]} [_ enabled]]
   {:db (assoc db :pixels-grid-enabled enabled)}))

(re-frame/reg-event-fx
 ::add-frame
 (fn [{:keys [db]}]
   (let [new-frame (-> (sprite/get-current-frame (:sprite db))
                       :duration
                       frame/create)]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/add-frame new-frame %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::remove-frame
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/remove-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::duplicate-frame
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/duplicate-frame)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-frame
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-frame idx %)))))

(re-frame/reg-event-fx
 ::move-frame
 (fn [{:keys [db]} [_ from-idx to-idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-frame from-idx to-idx %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-frame-left
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-frame-left (sprite/get-current-frame-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-frame-right
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-frame-right (sprite/get-current-frame-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::add-layer
 (fn [{:keys [db]}]
   (let [layer-name (project-settings/get-layer-name :single (-> db :sprite :layers count))
         layer (layer/create layer-name)]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/add-layer layer %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::duplicate-layer
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/duplicate-layer)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::remove-layer
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/remove-layer (-> db :sprite sprite/get-current-layer-idx)
                                                      %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::merge-layer-with-below
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/merge-layer-with-below)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-up
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-layer-up (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer-down
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-layer-down (sprite/get-current-layer-idx %) %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::move-layer
 (fn [{:keys [db]} [_ from-idx to-idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-layer from-idx to-idx %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::rename-layer
 (fn [{:keys [db]} [_ new-name]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer (sprite/get-current-layer-idx sprite) #(assoc % :name new-name) sprite))))))

(re-frame/reg-event-fx
 ::toggle-all-layers-visibility
 (fn [{:keys [db]} [_]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (let [layers (:layers sprite)
                                        some-visible? (some :visible? layers)
                                        layers (mapv #(assoc % :visible? (not some-visible?)) (:layers sprite))]
                                    (assoc sprite :layers layers)))))))

(re-frame/reg-event-fx
 ::toggle-layer-visibility
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :visible? not) sprite))))))

(re-frame/reg-event-fx
 ::toggle-all-layers-automatic-linking
 (fn [{:keys [db]} [_]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (let [layers (:layers sprite)
                                        some-automatic-linking? (some :automatic-linking? layers)
                                        layers (mapv #(assoc % :automatic-linking? (not some-automatic-linking?)) (:layers sprite))]
                                    (assoc sprite :layers layers)))))))

(re-frame/reg-event-fx
 ::toggle-layer-automatic-linking
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type))) ;; todo: нужно ли?
       (update-in [:db :sprite] (fn [sprite]
                                  (sprite/update-layer idx #(update % :automatic-linking? not) sprite))))))

(re-frame/reg-event-fx
 ::move-cel
 (fn [{:keys [db]} [_ from-pos to-pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/move-cel from-pos to-pos %))
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::select-layer
 (fn [{:keys [db]} [_ idx]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-layer idx %)))))

(re-frame/reg-event-fx
 ::select-only-1-cel
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/select-only-1-cel pos %)))))

(re-frame/reg-event-fx
 ::toggle-cel-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/toggle-cel-to-selection pos %)))))

(re-frame/reg-event-fx
 ::add-cels-range-to-selection
 (fn [{:keys [db]} [_ pos]]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] #(sprite/add-cels-range-to-selection pos %)))))

(re-frame/reg-event-fx
 ::link-selected-cels
 (fn [{:keys [db]} [_]]
   (let [main-cel-pos (sprite/get-current-cel-pos (:sprite db))]
     (-> db
         (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                       (tool/init (-> db :tool :type)))
         (update-in [:db :sprite] #(sprite/link-selected-cels main-cel-pos %))
         (update-in [:db] history/save-sprite)))))

(re-frame/reg-event-fx
 ::unlink-selected-cels
 (fn [{:keys [db]}]
   (-> db
       (commit-changes-and-init-tool (get-in db [:tool :state :changes])
                                     (tool/init (-> db :tool :type)))
       (update-in [:db :sprite] sprite/unlink-selected-cels)
       (update-in [:db] history/save-sprite))))

(re-frame/reg-event-fx
 ::set-frame-duration
 (fn [{:keys [db]} [_ idx duration]]
   (let [valid-duration (max duration 1)]
     {:db (-> db
              (update :sprite (fn [sprite]
                                (sprite/update-frame idx #(assoc % :duration valid-duration) sprite)))
              history/save-sprite)})))

(re-frame/reg-event-fx
 ::set-frame-duration-for-all
 (fn [{:keys [db]} [_ duration]]
   (when duration
     {:db (-> db
              (update :sprite (fn [sprite]
                                (->> (range 0 (count (:frames sprite)))
                                     (reduce (fn [res-sprite idx]
                                               (sprite/update-frame idx #(assoc % :duration duration) res-sprite))
                                             sprite))))
              history/save-sprite)})))

(re-frame/reg-event-fx
 ::zoom
 (fn [{:keys [db]} [_ delta center-pos mouse-offset-pos]]
   (let [prev-scale (:scale db)
         new-scale (-> (-> db :scale (* delta))
                       (min project-settings/max-scale)
                       (max project-settings/min-scale))]
     (if (not= prev-scale new-scale)
       (let [delta (if (#{project-settings/max-scale project-settings/min-scale} new-scale)
                     (/ new-scale prev-scale)
                     delta)
             old-canvas-size (-> db :sprite sprite/get-size (update-vals #(* % (:scale db))))
             new-canvas-size (-> db :sprite sprite/get-size (update-vals #(* % new-scale)))
             old-drawing-container-size (:drawing-container-size db)
             canvas-size-diff (merge-with - new-canvas-size old-canvas-size)
             new-drawing-container-size (merge-with + old-drawing-container-size canvas-size-diff)
             old-canvas-pos {:x (- (/ (:width old-drawing-container-size) 2)
                                   (/ (:width old-canvas-size) 2))
                             :y (- (/ (:height old-drawing-container-size) 2)
                                   (/ (:height old-canvas-size) 2))}
             new-canvas-pos {:x (- (/ (:width new-drawing-container-size) 2)
                                   (/ (:width new-canvas-size) 2))
                             :y (- (/ (:height new-drawing-container-size) 2)
                                   (/ (:height new-canvas-size) 2))}
             new-viewport-scroll {:x (- (+ (* (- (:x mouse-offset-pos) (:x old-canvas-pos)) delta) (:x new-canvas-pos))
                                        (:x center-pos))
                                  :y (- (+ (* (- (:y mouse-offset-pos) (:y old-canvas-pos)) delta) (:y new-canvas-pos))
                                        (:y center-pos))}]
         {:db (-> db
                  (assoc :scale new-scale)
                  (assoc :viewport-scroll new-viewport-scroll)
                  (assoc :drawing-container-size new-drawing-container-size))})
       {:db db}))))

(re-frame/reg-event-fx
 ::start-panning
 (fn [{:keys [db]} [_ pos]]
   {:db (assoc db
               :start-panning-pos pos
               :initial-viewport-scroll (:viewport-scroll db))}))

(re-frame/reg-event-fx
 ::pan
 (fn [{:keys [db]} [_ mouse-pos]]
   (let [delta-pos (merge-with - (:start-panning-pos db) mouse-pos)
         new-viewport-scroll (merge-with + (:initial-viewport-scroll db) delta-pos)]
     {:db (assoc db :viewport-scroll new-viewport-scroll)})))

(re-frame/reg-event-fx
 ::stop-panning
 (fn [{:keys [db]} [_]]
   {:db (assoc db
               :start-panning-pos nil
               :initial-viewport-scroll nil)}))

(re-frame/reg-event-fx
 ::swap-current-colors ;; todo: current
 (fn [{:keys [db]}]
   {:db (assoc db
               :primary-color (:secondary-color db)
               :secondary-color (:primary-color db))}))

(re-frame/reg-event-fx
 ::set-current-color
 (fn [{:keys [db]} [_ type color]]
   {:db (assoc db type color)}))

(re-frame/reg-event-fx
 ::handle-mouse-event
 (fn [{:keys [db]} [_ event-type mouse-pos right-button]]
   (let [event {:type event-type :pos mouse-pos :right-button right-button}]
     (case event-type
       :mouse-down
       (-> (assoc db
                  :user-is-drawing true ;;todo: нужен ли если ли есть initial-mouse-down-pos 
                  :initial-mouse-down-pos (:pos event)
                  :mouse-pos (:pos event)) ;; todo: удалить? пока нужно только в select'ах
           (tool/handle-mouse-event event))

       :mouse-move
       (-> (assoc db
                  :user-is-drawing (:user-is-drawing db)
                  :mouse-pos (:pos event))
           (tool/handle-mouse-event event))

       :mouse-up
       (-> (assoc db
                  :user-is-drawing false
                  :mouse-pos (:pos event))
           (tool/handle-mouse-event event)
           (assoc-in [:db :initial-mouse-down-pos] nil))))))

(re-frame/reg-fx
 :highlight-pixels
 (fn [poses]
   (let [current-cel (get-current-cel @re-frame.db/app-db)
         size (-> @re-frame.db/app-db :sprite sprite/get-size)
         changes (->> poses
                      (filter (fn [pos] (geometry/valid-point? pos size)))
                      (map (fn [pos] [pos (color/get-highlight-color (cel/get-pixel pos current-cel))])))]
     (canvas/update-image-data (. js/document (getElementById "visual-effects"))
                               changes))))

(re-frame/reg-fx
 :clear-visual-effects
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "visual-effects")))))

(re-frame/reg-fx
 :clear-preview
 (fn []
   (canvas/clear-canvas (. js/document (getElementById "preview")))))

(re-frame/reg-fx
 :draw-preview
 (fn [changes]
   (let [size (-> @re-frame.db/app-db :sprite sprite/get-size)
         {transparent-changes true rest-changes false}
         (->> changes
              (filter (fn [[pos]] (geometry/valid-point? pos size)))
              (group-by (fn [[_ color]] (= color color/transparent-color-int))))]
     (canvas/update-image-data (. js/document (getElementById "preview")) rest-changes)
     (when (seq transparent-changes)
       (canvas/update-image-data (. js/document (getElementById "current-layer")) transparent-changes)))))

(re-frame/reg-fx
 :show-alert
 (fn [message]
   (js/alert message)))

(re-frame/reg-fx
 :show-confirm
 (fn [message on-confirm]
   (when (js/confirm message)
     (re-frame/dispatch on-confirm))))

(re-frame/reg-fx
 :download-file
 (fn [{:keys [file-name content content-type]}]
   (let [data-blob (if (= content-type :json) ;; todo: remove json
                     (js/Blob. #js [content] #js {:type "application/json"})
                     content)
         link (.createElement js/document "a")]
     (set! (.-href link) (.createObjectURL js/URL data-blob))
     (.setAttribute link "download" file-name)
     (.appendChild (.-body js/document) link)
     (.click link)
     (.removeChild (.-body js/document) link))))
