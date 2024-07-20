;; todo: test merge-layer-with-below, move-layer
;; todo: bulk delete layers, bulk delete frames, buld remove cels and etc
;; todo: исп id?
4) view для переноса
5) groups для слоёв
6) место sprite в арг функций
7) удалить init-canvases и такую же логику из зума
8) переименовать preview ак как есть клеш с sprite-preivew
9) cel-imgs
10) рендерить фреймы во вьюхе
12) eraser. background. отдельный слой? 
  можно просто сделать background как отдельный слой(скорее всего недоступный в ui) 
  сейчас eraser просто закрашивает белым и поэтому работает.
  11) нужно ли preview
5) при создание выбирать background как в aseprite
6) image
7) подсветка текущего слоя?


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


