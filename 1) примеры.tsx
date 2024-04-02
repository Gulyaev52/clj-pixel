1) как оптимизировать обновление

2) есть ли смысл от оверлея
+
    можно оптимизировать обновление.
        не нужно на каждое изменение перерисовывать весь фрейм, а только то что поменялось
            1) грид который каждый раз отрисовывается
            2) описывать изменения

перенос
    рисуешь выделение. бордер либо опасити
    переносишь
        из исходной зоны вырезаешь
        затем просто переносишь
        если же была вставка то всегда просто переносишь

https://quantumsoftgroup.slack.com/archives/C04VB4NTCKC/p1708932835040759

25
l l L l

50
l   l   L   l

150 * (20 / 50)

20 = 100
l l l l L l l l


150*0.4*x=100

0.4*x=0.6666666

1) примеры
https://github.com/jvalen/pixel-art-react
https://github.com/piskelapp/piskel
https://github.com/lospec/pixel-editor
https://github.com/jackschaedler/goya
https://github.com/1j01/jspaint

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

4) почитать статьи по пиксель арту. инструменты
https://eloquentjavascript.net/19_paint.html
https://lospec.com/pixel-art-tutorials
https://pixeljoint.com/forum/forum_posts.asp?TID=11299
https://medium.com/pixel-grimoire/how-to-start-making-pixel-art-2d1e31a5ceab
https://www.youtube.com/watch?v=kJ5kmkVb6as&ab_channel=ReeceGeofroy
https://www.adobe.com/creativecloud/design/discover/pixel-art.html
https://www.makeuseof.com/how-to-make-pixel-art-beginners-guide/
https://www.youtube.com/watch?v=kJ5kmkVb6as&ab_channel=ReeceGeofroy
https://www.youtube.com/watch?v=59Y6OTzNrhk&ab_channel=AdamCYounis

4) понятия
sprite. pixels grid. resulotion
  слои
  https://lospec.com/pixel-art-tutorials/how-to-use-aseprite-layers-by-jebbygd

5) 
стейт некоторых инструментов. overlay frame

6) zoom in. zoom out

7)
onion skin

sprite
  frame
    layers(пока опустим)
      pixels

current frame. overlay frame.
transparent color

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




https://www.youtube.com/watch?v=IalFqykJmi0&ab_channel=Backendartist

1229



original frame. preview(temporary) frame.
нужно ли отображать 2 канваса?
  если активирован грид то при рисование не нужен показывать гриды на превью?
    желательно без грида
обновленный фрейм либо описание изменений

простые инструменты (пен)
  нужно модиф один и тот же фрейм
фигуры
  нужно модиф исходный фрейм



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
отделить event передавать db в инструменты
background






#_(do
  (def canvas (. js/document (getElementById "tutorial")))

  (defn mouse-down-handler [e]
    (let [scale (:scale @re-frame.db/app-db)
          initial-pos (canvas-pos->frame-pos e scale canvas)
          !last-pos (atom initial-pos)

          mouse-move-handler
          (fn [e]
            (let [pos (canvas-pos->frame-pos e scale canvas)]
              (when (not= pos @!last-pos)
                (do
                  (reset! !last-pos pos)
                  (.log js/console "mousedownmove" initial-pos pos)))))

          mouse-up-handler
          (fn [e]
            (let [pos (canvas-pos->frame-pos e scale canvas)]
              (.log js/console "mouseup pos" pos)

              (. canvas (removeEventListener "mousemove" mouse-move-handler))
              (. canvas (removeEventListener "mouseup" mouse-up-handler))))]

      (.log js/console "mousedown" initial-pos)

      (. canvas (addEventListener "mousemove" mouse-move-handler))
      (. canvas (addEventListener "mouseup" mouse-up-handler))))

  (. canvas (addEventListener "mousedown" mouse-down-handler)))
















  библиотека или с нуля
html vs svg vs canvas


html
    bitrix https://b24-y0vwm2.bitrix24.ru/company/personal/user/1/tasks/?F_STATE=sVg0&b24new=Y
    dhtmlx https://snippet.dhtmlx.com/dk7bfw6u?_gl=1*3ns78e*_ga*MTc5OTYxNjE1LjE3MDc4MDg1ODk.*_ga_N87XPB4GSG*MTcxMDIxOTgyMS4yLjAuMTcxMDIxOTgyMS42MC4wLjA.
    https://bryntum.com/products/gantt/examples/calendars/
    https://webix.com/demos/gantt/

how to
https://bryntum.com/blog/creating-a-gantt-chart-with-vanilla-javascript/
https://www.smashingmagazine.com/2022/06/vanilla-javascript-gantt-chart-part-2/

echart
https://www.onlinegantt.com/#/gantt

https://blog.logrocket.com/gantt-chart-javascript-frappe-gantt/

https://netology.ru/blog/09-2020-javascript-biblioteki-dlya-ganta
https://bryntum.com/blog/creating-a-gantt-chart-with-vanilla-javascript/
https://blog.logrocket.com/gantt-chart-javascript-frappe-gantt/
https://github.com/MaTeMaTuK/gantt-task-react

https://www.react-google-charts.com/examples/gantt
https://react-virtual-gantt.vercel.app/
https://reactjsexample.com/a-gantt-component-build-with-react-js/   
https://dhtmlx.com/docs/products/dhtmlxGantt-for-React/
https://medium.com/bryntum/creating-a-gantt-chart-with-react-using-next-js-fc080ad8b938
https://github.com/bryntum/gantt-chart-nextjs-starter/tree/complete-chart

timeline



большинство библиотек сделана на html.
есть пара на svg.
во всех открытых либах которые нашёл сделанно вообще без библиотек. даже без d3.
так как по сути ты тут отрисовываешь календарь (сверху) и тебе просто нужно вычислить ширину и позицию для прямоугольников.
если делать на svg, то можно взять visx или react-vis 

chartjs https://www.youtube.com/watch?v=zOsZJEnYuGo&list=PLc1g3vwxhg1Xl9QVaLnLUaGzuWH-zEGZD&index=17&ab_channel=ChartJS
есть серия видео как создать. но тут канвас и из-за этого сложно получается. но мб благодаря react-chartjs-2 будет легче
ещё пример https://codesandbox.io/p/sandbox/chartjs-timeline-gantt-iwsbbu?file=%2Findex.tsx













1) general info драйверы. в плановом останове
2) несколько раз вызывается /frontapi-eam/v1/acceptance/acts/3/assets
3) полный путь до актива в таблице
<PathViewer
    path={[
    record.orgStructure.orgTreeRoot.value,
    record.orgStructure.orgTreeNode.value,
    record.asset.value,
    ]}
/>

4) https://quantumsoftgroup.slack.com/archives/C04TDCUE7PE/p1711708477730509?thread_ts=1711541785.826879&cid=C04TDCUE7PE
5) systemInput label preventClick
6) цвет состояний
7) фильтрация дефектов
8) размеры таблиц

1) zoom
    onWheel. ctrl+
3) недели
4) drag для q
5) день. конец не включая.