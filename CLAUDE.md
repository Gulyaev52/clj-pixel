# Описание проекта
Pixel это редактор для создания пиксельной графики, спрайтов и покадровой анимации.
Инструменты: pen, eraser, bucket, color picker, rectangle, circle, line, rectangle selection, shape selection, shading. Поведение этих инструментов аналогично любым другим из редактор для создания графики типа paint или https://www.piskelapp.com/.
Покадровая анимация (frames): add empty frame, remove frame, duplicate frame, move frame left, move frame right. Перемещение фреймов так же можно делать через drag and drop.
Работа со слоями: add layer, remove layer, duplicate layer, merge layer with below, move layer up, move layer down, rename layer. Перемещение слоёв так же можно делать через drag and drop.
Onion skin и его настройка.
Левая кнопка мышки для основного цвета(primary color). Правая кнопка мышки для вторичного(secondary color).
Рисование происходит на канвасе(canvas).
Работа с палетами(palettes): add color, remove color, add palette, remove palette, rename palette, add colors from current frame, export palette, import palette. Каждый цвет можно назначить либо как primary color или как secondary color.
Все изменения сохраняются в local storage что бы при перезапуске не потерять изменения.

# Используемые технологии
1) clojurescript
2) re-frame
3) react
4) cypress for e2e tests

# Комманды
1) для проверки e2e тестов используй
npm run watch
npm run cypress:run
после команды watch нужно подождать 10 секунд