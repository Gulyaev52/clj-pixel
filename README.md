# Pixel Art

A browser-based **pixel-art and sprite-animation editor** built with ClojureScript and
[re-frame](https://github.com/day8/re-frame). Draw sprites on a pixel grid, organize your
work with layers, animate it frame by frame, and export the result as a PNG or an animated
GIF — all in the browser, with your work saved automatically.

<!-- TODO: add a screenshot at docs/screenshot.png and embed it here:
![Pixel Art editor](docs/screenshot.png) -->

## Features

### Drawing tools
- **Pen**, **Eraser** — with adjustable pixel size (1–64).
- **Bucket** — flood fill, with an "all the same color" option.
- **Color picker** — sample a color from the canvas.
- **Line** — with optional straight (axis/45°) constraint.
- **Rectangle** and **Circle** — with fill and keep-ratio options.
- **Shading** — lighten/darken with an adjustable amount.
- **Rectangle selection** `s` and **Shape selection** — move, copy/paste, cut and
  delete pixel regions.

### Colors & palettes
- **Primary** and **secondary** colors — paint with the **left** mouse button for primary
  and the **right** button for secondary.
- Manage palettes: add/remove colors, create/remove/rename palettes, and pull all colors
  from the current frame.
- Import and export palettes in **GIMP `.gpl`** format.

### Animation & layers
- **Timeline** with frame-by-frame animation: add, remove, duplicate and reorder frames
  (including drag-and-drop), with per-frame duration.
- **Layers**: add, remove, duplicate, merge-down, move up/down, rename, toggle
  visibility, and reorder via drag-and-drop.
- **Onion skin** with configurable settings to see neighbouring frames while drawing.
- **Sprite preview** with animation playback.

### Project & export
- **New project**, **resize sprite**, and **save / load** a project to a file.
- **Export** to **PNG** or animated **GIF**, with control over frames (all/selected),
  layers (visible/selected/specific), playback direction, frame scale, split-layers and
  GIF repeat.
- **Undo / redo** history (`Ctrl+Z` / `Ctrl+Y`).
- **Autosave** — your work is persisted in the browser IndexedDB and restored on reload.
- **Zoom & pan** the canvas.

## Tech stack

- **Language:** [ClojureScript](https://clojurescript.org/)
- **UI framework:** [re-frame](https://github.com/day8/re-frame) →
  [Reagent](https://github.com/reagent-project/reagent) →
  [React](https://reactjs.org/)
- **Components:** [Ant Design](https://ant.design/) v5
- **Drag & drop:** [react-dnd](https://github.com/react-dnd/react-dnd),
  [@dnd-kit](https://dndkit.com/)
- **Build / dev tooling:** [shadow-cljs](https://github.com/thheller/shadow-cljs),
  [shadow-css](https://github.com/thheller/shadow-css)
- **E2E tests:** [Cypress](https://www.cypress.io/) 15 (TypeScript)

## Getting started

### Prerequisites

- [JDK 11+](https://adoptium.net/)
- [Node.js 20.1+](https://nodejs.org/) with npm

### Install

```sh
npm install
```

### Run in development

Start shadow-cljs in watch mode (builds the app and the test runners with hot reload):

```sh
npm run watch
```

The first build can take ~10–20 seconds. Once it reports `[:app] Build completed`, open:

> http://localhost:8280/

Code changes are pushed to the browser automatically on save. An nREPL server is also
available on port `8777` for editor integration.

### Production build

```sh
npm run release          # compile an optimized build to resources/public/js/compiled
npm run css              # build the release CSS
npm run build-report     # generate a bundle size report at target/build-report.html
```

## Testing

### End-to-end tests (Cypress)

The dev server must be running first. Start it **once**:

```sh
npm run watch
```

Then, in another terminal, run the E2E suite:

```sh
npm run cypress:run      # headless
npm run cypress          # interactive Cypress UI
```
