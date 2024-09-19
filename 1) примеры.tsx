на закрытие генер превью
показывать превью для всех фреймов

2) очень долго рисует 64 на 64. исп array-data?
3) на изменение размера нужно зумить что бы полностью был виден

19) трансформации?
13) shading clear-preview draw-preview
14) transparent цвет можно выбрать как цвет/
15) зум
65) canvas/draw-frame-on-single-canvas
16) блокировать редактирования на lock
12) хоткеи
13) history что-то странное
  14) esc актив rectangle-tool
  166) разные размеры + ресайз
  189) нужен ли nul? мб просто прозрачный цвет
  18) черный цвет в гифке. иногда работает а иногда нет
17) пофиксить грид
13) применить изменения(selection) на действия (удаление копирование и прочее)
9) опасити у слоя
8) отображение transparent color в ui(color picker например)
4) view для переноса
1) нужно обновить картинки
2) когда переносишь со слоя на слой и оно залинковано
12) eraser.
  сейчас eraser просто закрашивает белым. на самом верхнем слое
  11) нужно ли preview
  15) дизейблинг действий типо на remove
  5) при создание выбирать background как в aseprite
  background. отдельный слой? 
  можно просто сделать background как отдельный слой(скорее всего недоступный в ui) 
  8) сделать норм подсветку для активной ячейки
17) дизайн
20) сохранение проекта в локал сторадж
выбранные цвета, текущая палетка и прочее

улучшения:
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

рефакторинг
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





(defn duplicate-frame [sprite]
  (->> (get-selected-cels-pos sprite)
       (sort-by :frame-idx)
       (map (fn [{:keys [frame-idx]}]
              [frame-idx (nth (:frames sprite) frame-idx)]))
       (reduce (fn [res-sprite [frame-idx frame]]
                 (add-frame frame
                            (fn [pos] (get-cel {:frame-idx frame-idx
                                                :layer-idx (:layer-idx pos)}
                                               sprite))
                            res-sprite))
               sprite)))