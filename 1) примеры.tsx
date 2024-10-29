13)  QuotaExceededError: Failed to execute 'setItem' on 'Storage': Setting the value of 'saved-project' exceeded the quota
  поправить тесты
  в файле и в бд сохранять не как строку 

оптимизация
  17) bucket too long for 256 grid
  18) после отрисовки зависает
  15) исп объек для цветов вместо строк?
  13) на создание перерендер происходит 4 раза или же на открытие 2 раза
  :resize-canvases
  на рисование происходит несколько ререндеров
  20) размер 256 на 256
  21) проверить произ бекапа

15) перенос прозрачного. если не будем делать то удалить из палетки
15) зум
  4) зум и большой размер (64)  
    3) на изменение размера нужно зумить что бы полностью был виден
    65) canvas/draw-frame-on-single-canvas
    17) пофиксить грид
18) черный цвет в гифке. иногда работает а иногда нет
13) применить изменения(selection) на действия (удаление копирование и прочее)
18) todos
5) при создание выбирать background как в aseprite
background. отдельный слой? 
можно просто сделать background как отдельный слой(скорее всего недоступный в ui) 
16) highlight cell иногда остаётсЯ
когда рисуешь и под ним уже есть что-то.
при выборе прозрачного цвета и рисование

17) дизайн
  8) сделать норм подсветку для активной ячейки

улучшения:
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