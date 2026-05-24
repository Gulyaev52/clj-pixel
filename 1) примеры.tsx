проверить перформанс на большом кол-ве фреймов
  1) сколько занимает бекап
  2) сколько занимает загрузка бекапа

5) переделать сериализацию?
18) объект или же массив?
19) долгая загрузка скорее всего когда много сохр
34) подсказки про линкинг и днд
8) perf на добавление фрейма\слоя(нужно toDataUrl кешировать)

16) переделать зум:?
17) дизайн
поправить тесты

мб вообще убрать превью?

----------------------


12) доп тулы

отрефакторить
  db_ event
  (run-events-handlers [:mouse-down :mouse-down-or-mouse-down-and-move] tool-events-handlers updated-db event)

;; todo: в drawing?
;; todo: в другие хелперы?

events -> db ?
17) подумать над разделением на слои



оптимизация
transdusers?
9) draw-preview
10) рисование на большом
  22) linked cels. оптимизация
  2) sprite-resizer
  3) в сериализации и в ячейках используются одни и те же картинки.
    их можно переиспользовать.
    3) on-changes :generate-cel-imgs не имеет смысла так как можно более эффективно это делать зная конкретную операцию
      например дублирование или создание.
    2) на изменение ячейки(добавление, удаление, рисование) происходят лишние отрисовки
      1) что бы отрисовать фин картинку
      2) что бы сгенерить превью для ячееке

  13) на создание перерендер происходит 4 раза или же на открытие 2 раза
  :resize-canvases
  на рисование происходит несколько ререндеров
  21) проверить произ бекапа
  1) долгая отрисовка.
    из-за конвертации кложа колл в image-data

18) todos
33) удалить .js


улучшения:
12) last-saved-history-idx
11) подсветка когда наводишь на слой ?????????
14) undo когда есть селектион
19) filled circle
18) zoom-out смещает
17) preview отдельное окно?
20) откат изменений на текущем фрейме если пред был прошлый
19) если изменения были на сером форне или скроле то они коммитятся и добавляются в историю
15) увеличение размера ячеек в таймлайне
13) минимап?
12) вернуть прозрачность ячеек и выбор прозрачного цвета в палетке
11)
при создание выбирать background как в aseprite
background. отдельный слой? 
можно просто сделать background как отдельный слой(скорее всего недоступный в ui) 
10) прозрачные цвета
11) undo/redo срабатывает когда открыты модалки
15) рисовать на превью сразу с опасити. но это сломает выделение в rectangle_selection. vfs layer?
14) lasso tool
13) ;; todo: поменять цвет выделений(может сделать как в aseprite) так как плохо видно. например на краснои
11) фокусом на инпут и шорткат
19) трансформации?
9) опасити у слоя
16) блокировать редактирования на lock
12) импорт из картинки
19) mirror
18) dithering ?
17) подсветка под курсором?
15) bucket, magic tool tolerance https://community.aseprite.org/t/tolerance-in-aseprite/3567
14) resize
65) рисовать на всех выбранных
65) подумать над init и ключами
43) esp выбирает rectangle select
98) init на уровне фич не знает в какой ключ встаёт
21) палетта плоская - неудобно
18) ошибка когда пытаешься рисовать на холсте 
7) подсветка текущего слоя на ховер ячейки?
13) разные курсоры?
14) ;; todo: move-layer; tests для рисования
15) ;; todo: bulk delete layers, bulk delete frames, buld remove cels and etc
19) теги для фреймов
20) повороты, растягивания
21) перенос цветов в палетке
23) выделять слои
24) импорт из колонки
17) теги?
10) переделать sprite preview
11) pixel perfect(aseprite pen)
12) bucket. pattern
16) eraser opacity
13) изменения на все выдел ячейки
https://www.aseprite.org/docs/move-tool/
16) иметь сохранение на каждый проект в локал сторадже

рефакторинг
0) где-то передаётся то что нужно удалить а где-то нет
1) где-то canvas где-то drawing
33) иниц канвасов перенести во вьюху
34) zoom нужно ли все эти данные хранить в базе
33) zoom, on-changes и прочее синх через вьюху или как-то ещё?
32) грид картинкой?
31) удалить on-changes?
12) какие-то действия на конкр а какие-то только на текущее
11) user-is-drawing -> mouse-is-down
15) отдельный слой для визуальных эффектов(выделение)
14) объекты для цветов
13) tinycolor -> только в toRgbString везде использовать rgba?
12) хранить цвета как объекты?
11) нужно ли preview
11) on-change не срабатывают на resize и там приходится вручную всё запускать
10) в тулах эксп объект или полиморфизм?
11) исп id? + плоский список?
13) есть опции настройки которых повторяются
10) рендерить фреймы во вьюхе
cel-imgs
6) место sprite в арг функций
7) удалить init-canvases и такую же логику из зума
8) переименовать preview так как есть клеш с sprite-preivew
9) для preview исп данные?
5) groups для слоёв
  
копирования +
рисование на preview +
onion skin, когда opacity стоит +
sprite-preivew +
рисование со слоями
слои накладываются друг на друга и когда ты рисуешь на слое,
то отображение должно быть с учётом слоёв ниже и выше.
preview должно быть на том же уровне что и текущий слой 
1) один из вариантов canvas#top-layers, canvas#current, canvas#bottom-layers. 
нет проблем с когда opacity стоит
не проблем с onion-skin(может быть foreground, background)
eraser?
+стат канвасы
+синх рисов
+перформанс может быть лучше при рендере всех слоёв так как можно дропать
-всегда надо перерисовывать на изменение выбранного
-будет ли корректное наложение цветов когда прозрачные
2) добавлять всегда все слои
+не нужно ничего никогда перерисовывать
+проще отрисовка
+наложение цветов простое
- рендер синх и проблема может быть что слой ещё не добавился в ui, если их добавлять через реакт
- производ?


2) инструменты
https://www.piskelapp.com/p/create/sprite
  simple tools
    pen
    vertical miror pen.
      модификаторы или опции
        1) ctrl
        2) shift
        https://www.pixilart.com/draw?ref=home-page
    paint bucket tool
    paint all pixels of the same color
    eraser tool

  shapes
    rectangle tool
    circle tool
    stroke tool
    1) shift

  move tool

  selection tooles
    rectangle
    lasso 
    https://www.pixilart.com/draw/edit-if-you-want-1d82cc2c2882967
      возможность менять размер
      крутить
    https://www.pixilart.com/draw

4) понятия
sprite. pixels grid. resulotion
  слои
  https://lospec.com/pixel-art-tutorials/how-to-use-aseprite-layers-by-jebbygd

5) 
стейт некоторых инструментов. overlay frame

7)
tools
  modificators. options.
  own state (selection).
  side effects (selection).

  разные курсоры

  events: mouseDown, mouseUp, mouseMove(mouseDownedMove)
  applyTool(mouseDown, mouseMove)
  moveTool
  releaseTool(mouseUp)

  стадии.
  абстрактные названия?

transforms ?

commit changes. решает сам инструмент


ВАЖНОЕ
pen size
разные курсоры?
подсвечивать ячейку под курсором






refactoring
один нейминг для pos и point
origin-frame и overlay-frame in rectangle
нейминг pixel color point. rectangle-select
nil and transparent color
поправить initialize на релоад
при изменение overlay-frame всегда нужно помнить что нужно отрендерить

опции или хоткеи для инструментов
mouse move. mouse up за пределами
release-tool при переключение
rectangle-select
ui
  изменение размера 
  вырезание
    нормальный опасити при rectangle-select ?
  ;; в истории сохраняется сразу?
  ;; при отпускание селектион нужно не сбрасывать
  ;; selection если выделен отсутс задний фон то он так же переносится

todo:
rectangle-select. transpare-color
удалить initial-mouse-down-pos из стейта



(re-frame/reg-fx
  :draw-preview
  (fn [changes]
    (let [preview-ctx (canvas/get-canvas-context "preview")
          current-layer-ctx (canvas/get-canvas-context "current-layer")
          sprite (-> @re-frame.db/app-db :sprite)
          cel-opacity (-> sprite sprite/get-current-cel :opacity)]
      (doseq [[pos color] changes]
        (when (geometry/valid-point? pos (sprite/get-size sprite))
          (if (= color color/transparent-color)
            (do
              (. current-layer-ctx (clearRect (:x pos) (:y pos) 1 1))
              (. preview-ctx (clearRect (:x pos) (:y pos) 1 1)))
            (do
              (. preview-ctx save)
              (set! (. preview-ctx -globalAlpha) cel-opacity)
              (set! (. preview-ctx -fillStyle) color)
              (when (not= cel-opacity 1) ;; todo: fix
                (. preview-ctx (clearRect (:x pos) (:y pos) 1 1)))
              (. preview-ctx (fillRect (:x pos) (:y pos) 1 1))
              (. preview-ctx restore))))))))






              (ns pixel-art.tool.selection
                (:require
                 [pixel-art.model.cel :as cel]
                 [pixel-art.model.color :as color]
                 [pixel-art.model.preview :as preview]
                 [pixel-art.tool.utils :refer [commit-preview-and-init-tool get-current-cel
                                               get-empty-visual-effects
                                               get-preview-from-current-cel
                                               with-highlight-cel-under-cursor]]
                 [pixel-art.utils.geometry :as geometry]
                 [re-frame.core :as re-frame]
                 [sc.api :as api]))
              
              (defn- init [type] {:type type :state {:mode :select
                                                     :user-is-making-selection false}})
              
              (defn- highlight-selection [db get-selection]
                (let [{:keys [width height]} (-> db :sprite :size)
                      current-pixels (or (:preview db) (:pixels (get-current-cel db)))
                      visual-effects (get-empty-visual-effects db)]
                  (get-selection (fn [x y]
                                   (when-let [idx (geometry/pos->idx x y width height)]
                                     (aset visual-effects idx
                                           (color/get-highlight-color (aget current-pixels idx))))))
                  (-> db
                      (assoc :visual-effects visual-effects))))
              
              (defn- commit-moved-selection [db]
                (let [type (-> db :tool :type)]
                  (commit-preview-and-init-tool db (:preview db) (init type))))
              
              (defn cut-initial-selection-from-preview-if-need [preview initial-selection pasted?]
                (if (not pasted?)
                  (do
                    (doseq [tuple initial-selection]
                      (preview/set-color! preview (aget tuple 0) color/transparent-color-int))
                    preview)
                  preview))
              
              (defn- move-selection [db tool prev-pos event]
                (let [offset-pos (cel/pos->idx (merge-with - (:pos event) prev-pos) (-> db :sprite :size :width))
                      {:keys [initial-selection-image pasted?]} (:state tool)
                      preview-with-deleted-initial-selection (or (:preview-with-deleted-initial-selection (:state tool))
                                                                 (cut-initial-selection-from-preview-if-need (get-preview-from-current-cel db)
                                                                                                             initial-selection-image
                                                                                                             pasted?))
                      preview (preview/create (-> db :sprite :size) preview-with-deleted-initial-selection)]
                  (doseq [tuple initial-selection-image]
                    (when (not= (aget tuple 1) color/transparent-color-int)
                      (preview/set-color! preview (aset tuple 0 (+ (aget tuple 0) offset-pos)) (aget tuple 1))))
                  {:preview preview
                   :preview-with-deleted-initial-selection preview-with-deleted-initial-selection}))
              
              (defn make [{:keys [type get-selection get-selection-only-on-mouse-down]}]
                {:type type
                 :init (fn [] (init type))
                 :get-events-handlers
                 (fn [db_]
                   (let [{:keys [tool prev-pos]} db_]
                     (case (-> db_ :tool :state :mode)
                       :select
                       (with-highlight-cel-under-cursor
                         {:mouse-down-or-mouse-down-and-move
                          (fn [db event]
                            (if (not get-selection-only-on-mouse-down)
                              (let [updated-tool (assoc-in (:tool db) [:state :user-is-making-selection] true) ;; без этого когда меняется мод с move-selection -> select, то происходит up event и снова создаётся селектион
                                    ]
                                {:db (-> db
                                         (assoc :tool updated-tool)
                                         (highlight-selection #(get-selection % db event)))})
                              {:db db}))
                          :mouse-up
                          (fn [db event]
                            (if (-> db :tool :state :user-is-making-selection)
                              (let [selection-image-js #js []
                                    current-cel (get-current-cel db)
                                    width (-> db :sprite :size :width)
                                    _ (get-selection (fn [x y] (. selection-image-js (push #js [(cel/pos->idx x y width)
                                                                                                (cel/get-pixel x y current-cel)]))) db event)
                                    tool (assoc tool :state {:mode :move-selection
                                                             :initial-selection-image selection-image-js
                                                             :selection-image selection-image-js
                                                             :changes []})]
                                {:db (assoc db :tool tool)})
                              {:db db}))})
              
                       :move-selection
                       {:mouse-down
                        (fn [db event]
                          (let [points (set (map (fn [[point]] point) (-> tool :state :selection-image)))]
                            (if (not (contains? points (cel/pos->idx (:x (:pos event)) (:y (:pos event)) (-> db :sprite :size :width))))
                              (commit-moved-selection db)
                              {:db db})))
                        :mouse-down-and-move
                        (fn [db event]
                          (let [{:keys [preview-with-deleted-initial-selection preview]} (move-selection db tool prev-pos event)]
                            {:db (-> db
                                     (assoc-in [:tool :state :preview-with-deleted-initial-selection] preview-with-deleted-initial-selection)
                                     (assoc :preview preview)
                                     (assoc :visual-effects nil))}))
                        :mouse-up
                        (fn [db event]
                          (let [{:keys [preview-with-deleted-initial-selection preview]} (move-selection db tool prev-pos event)]
                            {:db (-> db
                                     (assoc :preview preview)
                                     (assoc-in [:tool :state :preview-with-deleted-initial-selection] preview-with-deleted-initial-selection)
                                     #_(highlight-selection moved-selection-image))}))})))})
              
              (defn copy-selection [db]
                (let [{:keys [selection-image]} (-> db :tool :state)]
                  (-> db
                      (assoc
                       :selection-manager
                       {:selection-image selection-image
                        :tool-type (-> db :tool :type)}))))
              
              (defn delete-selection-and-commit [db]
                (let [tool-type (-> db :tool :type)
                      {:keys [initial-selection-image pasted?]} (-> db :tool :state)
                      deleted-initial-selection (if pasted?
                                                  {}
                                                  (update-vals initial-selection-image (fn [_] color/transparent-color-int)))
                      preview (get-preview-from-current-cel db)]
                  (doseq [[{:keys [x y]} color] deleted-initial-selection]
                    (preview/set-color! preview x y color))
                  (commit-preview-and-init-tool db preview {:type tool-type :state {:mode :select}})))
              
              (defn tool-has-selection? [db]
                (-> db :tool :state :selection-image))
              
              (re-frame/reg-event-fx
               ::delete-selection
               (fn [{:keys [db]} _]
                 (if (tool-has-selection? db)
                   (delete-selection-and-commit db)
                   {:db db})))
              
              (re-frame/reg-event-fx
               ::cut-selection
               (fn [{:keys [db]} _]
                 (if (tool-has-selection? db)
                   (-> (copy-selection db)
                       delete-selection-and-commit)
                   {:db db})))
              
              (re-frame/reg-event-fx
               ::commit-selection
               (fn [{:keys [db]} _]
                 (if (tool-has-selection? db)
                   (commit-moved-selection db)
                   {:db db})))
              
              (re-frame/reg-event-fx
               ::copy-selection
               (fn [{:keys [db]} _]
                 (if (tool-has-selection? db)
                   (-> (copy-selection db)
                       commit-moved-selection)
                   {:db db})))
              
              (re-frame/reg-event-fx
               ::past-selection
               (fn [{:keys [db]} _]
                 (if (-> db :selection-manager :selection-image) ;; todo: copied-selection? оно сущ только когда есть копирование
                   (let [{:keys [selection-image tool-type]} (:selection-manager db)
                         changes selection-image
                         new-tool {:type tool-type
                                   :state {:mode :move-selection
                                           :initial-selection-image selection-image
                                           :selection-image selection-image
                                           :changes changes
                                           :pasted? true}}
                         preview (get-preview-from-current-cel db)]
                     (doseq [[{:keys [x y]} color] changes]
                       (preview/set-color! preview x y color))
                     (-> db
                         commit-moved-selection
                         (assoc-in [:db :tool] new-tool)
                         (update :db #(-> %
                                          (assoc :preview preview)
                                          (highlight-selection selection-image)))))
                   {:db db})))
              