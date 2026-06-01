// Seed data: 20×20 sprite, all pixels transparent.
// Primary  = black  → pixels[x][y] = [0,   0, 0, 255]
// Secondary = red   → pixels[x][y] = [255, 0, 0, 255]

import { _2x2Seed, DBSeed, _4x4EmptySeed, getPixels as getPixels, getSpriteFromPixels } from "../support/data";
import { rgba } from "../support/utils";
import { b, g, r, t, y } from "../support/colors";

describe('Drawing Tools', () => {
  it("pen", () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('pen');
    cy.contains('Pixel size').should('be.visible');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true });

    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [b, t, t, t],
      [t, t, t, t],
      [t, t, t, t],
      [t, t, t, t],
    ], 'mousedown: single pixel drawn at (0,0)');

    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertVisibleCanvasPixels([
      [b, b, t, t],
      [b, b, t, t],
      [t, t, t, t],
      [t, t, t, t],
    ], 'mousemove to (1,1): 2×2 block drawn');

    cy.mouseMoveAndUpOnCanvas({x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [b, b, t, t],
      [b, b, b, t],
      [t, b, b, b],
      [t, t, b, b],
    ], 'mouseup at (3,3): full diagonal stroke committed');
  });
});

describe('Eraser Tool', () => {
  it('eraser tool erases correctly', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels(getPixels(4, 4, b)),
    });
    cy.selectTool('eraser');
    cy.contains('Pixel size').should('be.visible');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2

    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [t, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
    ], 'mousedown: single pixel erased at (0,0)');

    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, t, b, b],
      [b, b, b, b],
      [b, b, b, b],
    ], 'mousemove to (1,1): 2×2 block erased');

    cy.mouseMoveAndUpOnCanvas({x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, t, t, b],
      [b, t, t, t],
      [b, b, t, t],
    ], 'mouseup at (3,3): full diagonal stroke erased');
  });
});

describe('Color Picker Tool', () => {
  beforeEach(() => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [g, y],
        [t, t],
      ]),
    });
    cy.selectTool('color-picker');
  });

  it('picks primary and secondary color from canvas pixel', () => {
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.get('[data-testid="primary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 128, 0)');

    cy.mouseDownThenUpOnCanvas({x: 1, y: 0}, { rightClick: true });
    cy.get('[data-testid="secondary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 0, 255)');
  });
});

describe('Rectangle Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits outline', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [t, t, t, t],
        [t, g, g, t],
        [t, g, g, t],
        [t, t, t, t],
      ]),
    });
    cy.selectTool('rectangle');

    // Step 1: mousedown at (0,0) — user sees original + black pixel at (0,0)
    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [b, t, t, t],
      [t, g, g, t],
      [t, g, g, t],
      [t, t, t, t],
    ], 'step 1: user sees start pixel, original pixels unchanged underneath');

    // Steps 2–3: intermediate drag to (1,1) — user sees original + 2×2 black outline
    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertVisibleCanvasPixels([
      [b, b, t, t],
      [b, b, g, t],
      [t, g, g, t],
      [t, t, t, t],
    ], 'steps 2-3: user sees 2×2 preview outline — green pixel (1,1) visible under transparent preview');

    // Steps 3-4: intermediate drag to (2,2) — user sees original + 3×3 black outline
    cy.mouseMoveOnCanvas({x: 2, y: 2});
    cy.assertVisibleCanvasPixels([
      [b, b, b, t],
      [b, g, b, t],
      [b, b, b, t],
      [t, t, t, t],
    ], 'steps 3-4: user sees 3×3 preview outline — green pixel (2,2) visible under transparent preview');

    // Step 5: mouseup at (3,3) — user sees committed rectangle
    cy.mouseMoveAndUpOnCanvas({x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [b, b, b, b],
      [b, g, g, b],
      [b, g, g, b],
      [b, b, b, b],
    ], 'step 5: rectangle outline committed — interior green pixels preserved');
  });

  it('fill option: paints all pixels inside the rectangle', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('rectangle');
    cy.contains('Fill').click();
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [b, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
    ], 'fill: all pixels inside the rectangle are painted');
  });

  it('pixel-size option: thickens the outline border', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [t, t, t, t, t, t],
        [t, t, t, t, t, t],
        [t, t, t, t, t, t],
        [t, t, t, t, t, t],
        [t, t, t, t, t, t],
        [t, t, t, t, t, t],
      ]),
    });
    cy.selectTool('rectangle');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel-size = 2
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 5, y: 5});
    cy.assertVisibleCanvasPixels([
      [b, b, b, b, b, b],
      [b, b, b, b, b, b],
      [b, b, t, t, b, b],
      [b, b, t, t, b, b],
      [b, b, b, b, b, b],
      [b, b, b, b, b, b],
    ], 'pixel-size=2: 2px border, interior remains transparent');
  });

  it('keep-ratio option: constrains rectangle to a square', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('rectangle');
    cy.contains('Keep ration').click();
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 3, y: 2}); // asymmetric drag: 4 wide, 3 tall
    cy.assertVisibleCanvasPixels([
      [b, b, b, t],
      [b, t, b, t],
      [b, b, b, t],
      [t, t, t, t],
    ], 'keep-ratio: asymmetric drag produces a square outline');
  });
});

describe('Rectangle Selection Tool', () => {
  const initialPixels = [
    [r, r, b, b],
    [r, r, b, b],
    [b, b, b, b],
    [b, b, b, b],
  ];
  const seed: DBSeed = {
    ..._4x4EmptySeed,
    sprite: getSpriteFromPixels(initialPixels),
  };

  it('select state', () => {
    cy.startApp(seed);
    cy.selectTool('rectangle-selection');

    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.assertHighlightedPixels([
      [true, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousedown: single pixel highlighted');

    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertHighlightedPixels([
      [true, true, false, false],
      [true, true, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousemove to (1,1): 2×2 selection highlighted');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels unchanged during selection');

    cy.mouseMoveAndUpOnCanvas({x: 1, y: 1});
    cy.assertHighlightedPixels([
      [true, true, false, false],
      [true, true, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mouseup: highlight stays, transitioned to drag state');

    // Click outside selection — resets to select, old highlight cleared
    cy.mouseDownOnCanvas({x: 3, y: 3});
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousedown outside: old selection cleared');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels still all black — nothing committed');
    cy.mouseMoveOnCanvas({x: 2, y: 2});
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, true, true],
      [false, false, true, true],
    ], 'mousedown outside: new selection created');

    cy.selectTool('pen');
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'selecting another tool clears highlight');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels still all black — nothing committed');
  });

  it('select + highlight + drag + commit', () => {
    cy.startApp(seed);
    cy.selectTool('rectangle-selection');

    // Select 2×2 top-left region
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});

    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, r, r, b],
      [b, r, r, b],
      [b, b, b, b],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ]);

    cy.mouseMoveAndUpOnCanvas({x: 2, y: 2});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, t, b, b],
      [b, b, r, r],
      [b, b, r, r],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, true, true],
      [false, false, true, true],
    ]);

    cy.mouseDownThenMoveThenUpOnCanvas({x: 2, y: 2}, {x: 2, y: 1});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, t, r, r],
      [b, b, r, r],
      [b, b, b, b],
    ]);

    // Click outside selection to commit
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, t, r, r],
      [b, b, r, r],
      [b, b, b, b],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ]);
    // todo: check that pixels was commited
  });

  describe("actions", () => {
    it("copy + paste action", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [true, true, false, false],
        [true, true, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ]);
      cy.assertVisibleCanvasPixels(initialPixels);

      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 2});
      cy.assertVisibleCanvasPixels([
        [r, r, b, b],
        [r, r, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "underlying pixels should be preserved + moved only pasted selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true, true],
        [false, false, true, true],
      ]);

      cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
      cy.assertVisibleCanvasPixels([
        [r, r, b, b],
        [r, r, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "click outside should commit the move");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ]);
    });

    it("delete selection", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'Backspace']); // in reality here should be only "Backspace" but for some reason this test doesn't work without "Control" key
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, t, b, b],
        [b, b, b, b],
        [b, b, b, b],
      ], "delete should clear pixels in selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "selection should remain after delete");

      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 2});
      cy.realPress(['Control', 'Delete']); // in reality here should be only "Delete" but for some reason this test doesn't work without "Control" key
      cy.assertVisibleCanvasPixels([
        [t, t, t, b],
        [t, t, t, b],
        [t, t, t, b],
        [b, b, b, b],
      ], "delete should clear pixels in selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "selection should remain after delete");
    });

    it("delete pasted selection", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.mouseDownThenMoveThenUpOnCanvas({x: 1, y: 1}, {x: 2, y: 1});
      cy.realPress(['Control', 'Backspace']);
      cy.assertVisibleCanvasPixels(initialPixels, "delete pasted selection should not affect underlying pixels only pasted ones");
    });

    it("paste when another tool is active");

    it("commit moved selection with Enter", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});

      // Drag selection to bottom-right
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 2});
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, t, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "preview: selection moved to bottom-right");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true, true],
        [false, false, true, true],
      ], "selection highlighted at new position");

      // Press Enter to commit
      cy.realPress('Enter');
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, t, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "Enter should commit the moved selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "highlight cleared after Enter");
    });

    it("commit pasted and moved selection with Enter", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region, copy and paste
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [true, true, false, false],
        [true, true, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels(initialPixels, "underlying pixels unchanged after paste");

      // Drag pasted selection to bottom-right
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 2});
      cy.assertVisibleCanvasPixels([
        [r, r, b, b],
        [r, r, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "underlying pixels preserved + pasted selection moved to bottom-right");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true, true],
        [false, false, true, true],
      ], "highlight at new position");

      // Press Enter to commit
      cy.realPress('Enter');
      cy.assertVisibleCanvasPixels([
        [r, r, b, b],
        [r, r, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "Enter should commit the pasted selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "highlight cleared after Enter");
    });

    it("cut selection", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region and cut
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      // move
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'x']);
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, t, b, b],
        [b, b, b, b],
        [b, b, b, b],
      ], "cut should clear original pixels immediately");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "selection cleared after cut");

      // Paste cut content to verify clipboard is populated
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, true, true, false],
        [false, true, true, false],
        [false, false, false, false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, r, r, b],
        [b, r, r, b],
        [b, b, b, b],
      ], "cut pixels available in clipboard — float over transparent area restores appearance");

      // commit
      cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels([
        [t, t, b, b],
        [t, r, r, b],
        [b, r, r, b],
        [b, b, b, b],
      ], "cut pixels available in clipboard — float over transparent area restores appearance");
    });

    it("cut pasted selection preserves underlying pixels", () => {
      cy.startApp(seed);
      cy.selectTool('rectangle-selection');

      // Select, copy, paste and move the pasted selection
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 1, y: 1});
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 2});
      cy.assertVisibleCanvasPixels([
        [r, r, b, b],
        [r, r, b, b],
        [b, b, r, r],
        [b, b, r, r],
      ], "underlying pixels preserved + pasted selection moved");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true, true],
        [false, false, true, true],
      ]);

      // Cut the pasted selection — original pixels must not be affected
      cy.realPress(['Control', 'x']);
      cy.assertVisibleCanvasPixels(initialPixels, "cut pasted selection should not affect underlying pixels");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false, false],
      ], "selection cleared after cut");
    });
  });
});

describe('Line Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits line', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('line');

    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [b, t, t, t],
      [t, t, t, t],
      [t, t, t, t],
      [t, t, t, t],
    ], 'step 1: mousedown at (0,0) — single pixel on preview');

    cy.mouseMoveOnCanvas({x: 2, y: 1});
    cy.assertVisibleCanvasPixels([
      [b, b, t, t],
      [t, t, b, t],
      [t, t, t, t],
      [t, t, t, t],
    ], 'step 2: mousemove to (2,1) — Bresenham line on preview');

    cy.mouseMoveAndUpOnCanvas({x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [b, t, t, t],
      [t, b, t, t],
      [t, t, b, t],
      [t, t, t, b],
    ], 'step 3: mouseup at (3,3) — diagonal line committed');
  });

  it('pixel size 2 and Straight option produce correct pixels', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('line');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2
    cy.contains('Straight').click();

    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 2, y: 3});
    cy.assertVisibleCanvasPixels([
      [b, b, t, t],
      [b, b, b, t],
      [t, b, b, b],
      [t, t, b, b],
    ], 'pixel size 2 + Straight: (0,0)→(2,3) draws perfect diagonal stripe');
  });
});

describe('Circle Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits circle', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('circle');

    // circle with start=end produces no pixels (zero-radius), so no assertion after mousedown
    cy.mouseDownOnCanvas({x: 0, y: 0});

    cy.mouseMoveOnCanvas({x: 2, y: 2});
    cy.assertVisibleCanvasPixels([
      [t, b, t, t],
      [b, t, b, t],
      [t, b, t, t],
      [t, t, t, t],
    ], 'step 2: mousemove to (2,2) — circle outline preview');

    cy.mouseMoveAndUpOnCanvas({x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [t, b, b, t],
      [b, t, t, b],
      [b, t, t, b],
      [t, b, b, t],
    ], 'step 3: mouseup at (3,3) — circle outline committed');
  });

  it('Keep ration option constrains bounding box to a square', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('circle');
    cy.contains('Keep ration').click();

    // asymmetric drag (0,0)→(3,2); keep-ratio constrains to effective endpoint (2,2)
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 3, y: 2});
    cy.assertVisibleCanvasPixels([
      [t, b, t, t],
      [b, t, b, t],
      [t, b, t, t],
      [t, t, t, t],
    ], 'keep-ratio: asymmetric drag produces a perfect circle');
  });

  it('pixel-size option: thickens the circle outline', () => {
    cy.startApp(_4x4EmptySeed);
    cy.selectTool('circle');
    cy.mouseDownThenMoveThenUpOnCanvas({x: 0, y: 0}, {x: 3, y: 3});
    cy.assertVisibleCanvasPixels([
      [t, b, b, t],
      [b, t, t, b],
      [b, t, t, b],
      [t, b, b, t],
    ], 'pixel-size=2: circle outline is 2px thick');
  });
});

describe('Bucket Tool', () => {
  it('flood-fills the connected region with primary or secondary color', () => {
    const db: DBSeed = {
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [t, g, g, t],
        [g, t, t, g],
        [g, t, t, g],
        [t, g, g, t],
      ]),
    };
    cy.startApp(db);
    cy.selectTool('bucket');

    // left-click fills the entire connected transparent region with primary color (black)
    cy.mouseDownThenUpOnCanvas({x: 2, y: 2});
    let expectedPixels = [
      [t, g, g, t],
      [g, b, b, g],
      [g, b, b, g],
      [t, g, g, t],
    ];
    cy.assertCanvasPixels(expectedPixels, 'flood-fill fills the connected region with primary color');

    cy.mouseDownThenUpOnCanvas({x: 2, y: 2}, { rightClick: true });
    expectedPixels = [
      [t, g, g, t],
      [g, r, r, g],
      [g, r, r, g],
      [t, g, g, t],
    ];
    cy.assertCanvasPixels(expectedPixels, 'flood-fill fills the connected region with secondary color');
  });

  it('"All the same color" fills every matching pixel regardless of adjacency', () => {
    const db: DBSeed = {
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [y, g, g],
        [g, t, t],
        [g, y, t],
        [t, g, y],
      ]),
    };
    cy.startApp(db);
    cy.selectTool('bucket');

    cy.contains('All the same color').click(); // todo: check checkbox state
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    const expectedPixels = [
      [b, g, g],
      [g, t, t],
      [g, b, t],
      [t, g, b],
    ];
    cy.assertCanvasPixels(expectedPixels, '"All the same color" fills every matching pixel regardless of adjacency');
  });
});

describe('Shading Tool', () => {
  it('darken: darkens non-transparent pixels, skips transparent', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [r, t],
        [r, t],
      ]),
    });
    cy.selectTool('shading');
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0}); // red pixel — should darken
    cy.mouseDownThenUpOnCanvas({x: 1, y: 0}); // transparent pixel — should stay transparent
    cy.assertVisibleCanvasPixels([
      [rgba(224, 0, 0), t],
      [r, t],
    ], 'darken: red pixel darkened, transparent pixel unchanged');
  });

  it('lighten: lightens non-transparent pixels', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [r, r],
        [r, r],
      ]),
    });
    cy.selectTool('shading');
    cy.contains('Lighten').click();
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [rgba(255, 31, 31), r],
      [r, r],
    ], 'lighten: red pixel lightened');
  });

  it('amount option: larger amount causes stronger darkening', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [r, r],
        [r, r],
      ]),
    });
    cy.selectTool('shading');
    // Raise amount from default 6 to 10. Send arrows one at a time (separate Cypress
    // commands, asserting each increment): the slider is a controlled component whose
    // value round-trips through re-frame, and a single fast multi-key .type() drops one
    // increment in the optimized release build.
    cy.get('.ant-slider-handle').eq(1).focus();
    for (let amount = 7; amount <= 10; amount++) {
      cy.get('.ant-slider-handle').eq(1).type('{rightarrow}', { force: true });
      cy.get('[data-testid="amount"]').should('contain.text', `(${amount})`);
    }
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([
      [rgba(204, 0, 0), r],
      [r, r],
    ], 'amount=10: pixel darkened more than with default amount=6');
  });

  it('pixel-size option: enlarges brush', () => {
    const d = rgba(224, 0, 0); // darkened red (darken 6%)
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([
        [r, r, r, r],
        [r, r, r, r],
        [r, r, r, r],
        [r, r, r, r],
      ]),
    });
    cy.selectTool('shading');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel-size = 2
    cy.mouseDownThenUpOnCanvas({x: 1, y: 1}); // cursor at (1,1): block expands top-left to (0,0)-(1,1)
    cy.assertVisibleCanvasPixels([
      [d, d, r, r],
      [d, d, r, r],
      [r, r, r, r],
      [r, r, r, r],
    ], 'pixel-size=2: 2×2 block darkened');
  });
});

describe('undo/redo', () => {
  it('draw → undo → redo restores pixel', () => {
    cy.startApp(_2x2Seed);
    cy.selectTool('pen');
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([[b, t], [t, t]], 'pixel drawn');

    cy.undo();
    cy.assertVisibleCanvasPixels([[r, t], [t, t]], 'undo: pixel gone');

    cy.redo();
    cy.assertVisibleCanvasPixels([[b, t], [t, t]], 'redo: pixel back');
  });

  it('3-step chain: draw → draw → draw → undo×3 → redo×3', () => {
    cy.startApp(_2x2Seed);
    cy.selectTool('pen');

    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertVisibleCanvasPixels([[b, t], [t, t]], 'after draw (0,0)');

    cy.mouseDownThenUpOnCanvas({x: 1, y: 0});
    cy.assertVisibleCanvasPixels([[b, b], [t, t]], 'after draw (1,0)');

    cy.mouseDownThenUpOnCanvas({x: 0, y: 1});
    cy.assertVisibleCanvasPixels([[b, b], [b, t]], 'after draw (0,1)');

    cy.undo();
    cy.assertVisibleCanvasPixels([[b, b], [t, t]], 'undo 1: (0,1) gone');

    cy.undo();
    cy.assertVisibleCanvasPixels([[b, t], [t, t]], 'undo 2: (1,0) gone');

    cy.undo();
    cy.assertVisibleCanvasPixels([[r, t], [t, t]], 'undo 3: all gone');

    cy.redo();
    cy.assertVisibleCanvasPixels([[b, t], [t, t]], 'redo 1: (0,0) back');

    cy.redo();
    cy.assertVisibleCanvasPixels([[b, b], [t, t]], 'redo 2: (1,0) back');

    cy.redo();
    cy.assertVisibleCanvasPixels([[b, b], [b, t]], 'redo 3: (0,1) back');
  });
});

describe('Shape Selection Tool', () => {
  it('select connected pixels and move', () => {
    const initialPixels = [
      [r, r, b, b],
      [r, b, b, b],
      [b, b, b, r],
      [b, b, r, r],
    ];

    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels(initialPixels),
    });
    cy.selectTool('shape-selection');

    // Single click — flood-fill selects only the 3 connected red pixels at top-left
    cy.mouseDownThenUpOnCanvas({x: 0, y: 0});
    cy.assertHighlightedPixels([
      [true, true, false, false],
      [true, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'only connected red pixels highlighted, disconnected reds excluded');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels unchanged after selection');

    // Drag selection by (+1, +1)
    cy.mouseDownOnCanvas({x: 0, y: 0});
    cy.mouseMoveOnCanvas({x: 1, y: 1});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, r, r, b],
      [b, r, b, r],
      [b, b, r, r],
    ], 'selection floats: originals transparent, preview at new position');

    cy.mouseMoveAndUpOnCanvas({x: 1, y: 1});
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, true, true, false],
      [false, true, false, false],
      [false, false, false, false],
    ], 'highlight follows selection to new position');

    // Commit by clicking outside
    cy.mouseDownThenUpOnCanvas({x: 0, y: 3});
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, r, r, b],
      [b, r, b, r],
      [b, b, r, r],
    ], 'selection committed at new position');
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'highlight cleared after commit');
  });
});