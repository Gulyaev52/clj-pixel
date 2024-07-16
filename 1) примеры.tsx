;; todo: test merge-layer-with-below, move-layer
;; todo: bulk delete layers, bulk delete frames, buld remove cels and etc
;; todo: selected-cels -> map с {:current true}
;; todo: исп id?
4) view для переноса
5) groups для слоёв
6) место sprite в арг функций

рисование на preview
onion skin, когда opacity стоит

5) при создание выбирать background как в aseprite
6) image

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


