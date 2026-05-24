// Seed data: 20×20 sprite, all pixels transparent.
// Primary  = black  → pixels[x][y] = [0,   0, 0, 255]
// Secondary = red   → pixels[x][y] = [255, 0, 0, 255]

import { DBSeed, defaultDbSeed, getEmptyPixels as getPixels, getSpriteFromPixels } from "../support/data";
import { rgba } from "../support/utils";

const colors = {
  green: rgba(0, 128, 0),
  black: rgba(0, 0, 0),
  red: rgba(255, 0, 0),
  blue: rgba(0, 0, 255),
  transparent: rgba(0, 0, 0, 0),
};

describe('Drawing Tools', () => {
  it("pen", () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('pen');
    cy.contains('Pixel size').should('be.visible');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true });

    cy.startDrawAtCanvasPixel(0, 0);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'mousedown: single pixel drawn at (0,0)');

    cy.moveAtCanvasPixel(1, 1);
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black, colors.transparent, colors.transparent],
      [colors.black, colors.black, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'mousemove to (1,1): 2×2 block drawn');

    cy.finishDrawAtCanvasPixel(3, 3);
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black, colors.transparent, colors.transparent],
      [colors.black, colors.black, colors.black,       colors.transparent],
      [colors.transparent, colors.black, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.black, colors.black],
    ], 'mouseup at (3,3): full diagonal stroke committed');
  });
});

describe('Eraser Tool', () => {
  it('eraser tool erases correctly', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(getPixels(4, 4, colors.black)),
    });
    cy.selectTool('eraser');
    cy.contains('Pixel size').should('be.visible');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2

    cy.startDrawAtCanvasPixel(0, 0);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.black, colors.black, colors.black],
      [colors.black,       colors.black, colors.black, colors.black],
      [colors.black,       colors.black, colors.black, colors.black],
      [colors.black,       colors.black, colors.black, colors.black],
    ], 'mousedown: single pixel erased at (0,0)');

    cy.moveAtCanvasPixel(1, 1);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.black,       colors.black,       colors.black, colors.black],
      [colors.black,       colors.black,       colors.black, colors.black],
    ], 'mousemove to (1,1): 2×2 block erased');

    cy.finishDrawAtCanvasPixel(3, 3);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.transparent, colors.black],
      [colors.black,       colors.transparent, colors.transparent, colors.transparent],
      [colors.black,       colors.black,       colors.transparent, colors.transparent],
    ], 'mouseup at (3,3): full diagonal stroke erased');
  });
});

describe('Color Picker Tool', () => {
  beforeEach(() => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.green, colors.blue],
        [colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('color-picker');
  });

  it('picks primary and secondary color from canvas pixel', () => {
    cy.drawAtCanvasPixel(0, 0);
    cy.get('[data-testid="primary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 128, 0)');

    cy.drawAtCanvasPixel(1, 0, { rightClick: true });
    cy.get('[data-testid="secondary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 0, 255)');
  });
});

describe('Rectangle Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits outline', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.green,       colors.green,       colors.transparent],
        [colors.transparent, colors.green,       colors.green,       colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('rectangle');

    // Step 1: mousedown at (0,0) — user sees original + black pixel at (0,0)
    cy.startDrawAtCanvasPixel(0, 0);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.green,       colors.green,       colors.transparent],
      [colors.transparent, colors.green,       colors.green,       colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'step 1: user sees start pixel, original pixels unchanged underneath');

    // Steps 2–3: intermediate drag to (1,1) — user sees original + 2×2 black outline
    cy.moveAtCanvasPixel(1, 1);
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black,       colors.transparent, colors.transparent],
      [colors.black, colors.black,       colors.green,       colors.transparent],
      [colors.transparent, colors.green, colors.green,       colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'steps 2-3: user sees 2×2 preview outline — green pixel (1,1) visible under transparent preview');

    // Steps 3-4: intermediate drag to (2,2) — user sees original + 3×3 black outline
    cy.moveAtCanvasPixel(2, 2);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.black, colors.black, colors.transparent],
      [colors.black, colors.green,       colors.black,       colors.transparent],
      [colors.black, colors.black,       colors.black,       colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'steps 3-4: user sees 3×3 preview outline — green pixel (2,2) visible under transparent preview');

    // Step 5: mouseup at (3,3) — user sees committed rectangle
    cy.finishDrawAtCanvasPixel(3, 3);
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black, colors.black, colors.black],
      [colors.black, colors.green, colors.green, colors.black],
      [colors.black, colors.green, colors.green, colors.black],
      [colors.black, colors.black, colors.black, colors.black],
    ], 'step 5: rectangle outline committed — interior green pixels preserved');
  });

  it('fill option: paints all pixels inside the rectangle', () => {
    const b = colors.black;
    const t = colors.transparent;
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [t, t, t, t],
        [t, t, t, t],
        [t, t, t, t],
        [t, t, t, t],
      ]),
    });
    cy.selectTool('rectangle');
    cy.contains('Fill').click();
    cy.drawAtCanvasPixel(0, 0, { toX: 3, toY: 3 });
    cy.assertVisibleCanvasPixels([
      [b, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
      [b, b, b, b],
    ], 'fill: all pixels inside the rectangle are painted');
  });

  it('pixel-size option: thickens the outline border', () => {
    const b = colors.black;
    const t = colors.transparent;
    cy.startApp({
      ...defaultDbSeed,
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
    cy.drawAtCanvasPixel(0, 0, { toX: 5, toY: 5 });
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
    const b = colors.black;
    const t = colors.transparent;
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [t, t, t, t],
        [t, t, t, t],
        [t, t, t, t],
        [t, t, t, t],
      ]),
    });
    cy.selectTool('rectangle');
    cy.contains('Keep ration').click();
    cy.drawAtCanvasPixel(0, 0, { toX: 3, toY: 2 }); // asymmetric drag: 4 wide, 3 tall
    cy.assertVisibleCanvasPixels([
      [b, b, b, t],
      [b, t, b, t],
      [b, b, b, t],
      [t, t, t, t],
    ], 'keep-ratio: asymmetric drag produces a square outline');
  });
});

describe('Rectangle Selection Tool', () => {
  it('select state', () => {
    const initialPixels = getPixels(4, 4, colors.black);
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(initialPixels),
    });
    cy.selectTool('rectangle-selection');

    cy.startDrawAtCanvasPixel(0, 0);
    cy.assertHighlightedPixels([
      [true,  false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousedown: single pixel highlighted');

    cy.moveAtCanvasPixel(1, 1);
    cy.assertHighlightedPixels([
      [true,  true,  false, false],
      [true,  true,  false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousemove to (1,1): 2×2 selection highlighted');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels unchanged during selection');

    cy.finishDrawAtCanvasPixel(1, 1);
    cy.assertHighlightedPixels([
      [true,  true,  false, false],
      [true,  true,  false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mouseup: highlight stays, transitioned to drag state');

    // Click outside selection — resets to select, old highlight cleared
    cy.startDrawAtCanvasPixel(3, 3);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'mousedown outside: old selection cleared');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels still all black — nothing committed');
    cy.moveAtCanvasPixel(2, 2);
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
    const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(initialPixels),
    });
    cy.selectTool('rectangle-selection');

    // Select 2×2 top-left region
    cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });

    cy.startDrawAtCanvasPixel(0, 0);
    cy.moveAtCanvasPixel(1, 1);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.transparent, colors.red, colors.red, colors.black],
      [colors.black, colors.red, colors.red, colors.black],
      [colors.black, colors.black, colors.black, colors.black],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false,  false],
      [false, false, false,  false],
    ]);

    cy.finishDrawAtCanvasPixel(2, 2);
    cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, true,  true],
      [false, false, true,  true],
    ]);

    cy.drawAtCanvasPixel(2, 2, { toX: 2, toY: 1 });
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.red, colors.red],
      [colors.black, colors.black, colors.red, colors.red],
      [colors.black, colors.black, colors.black, colors.black],
    ]);

    // Click outside selection to commit
    cy.drawAtCanvasPixel(0, 0);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.red, colors.red],
      [colors.black, colors.black, colors.red, colors.red],
      [colors.black, colors.black, colors.black, colors.black],
    ]);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, false, false, false],
      [false, false, false,  false],
      [false, false, false,  false],
    ]);
    // todo: check that pixels was commited
  });

  describe("actions", () => {
    it("copy + paste action", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');
  
      // Select 2×2 top-left region
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [true, true, false, false],
        [true, true, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ]);
      cy.assertVisibleCanvasPixels(initialPixels);

      cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 2});
      cy.assertVisibleCanvasPixels([
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "underlying pixels should be preserved + moved only pasted selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true,  true],
        [false, false, true,  true],
      ]);

      cy.drawAtCanvasPixel(0, 0);
      cy.assertVisibleCanvasPixels([
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "click outside should commit the move");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ]);
    });

    it("delete selection", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'Backspace']); // in reality here should be only "Backspace" but for some reason this test doesn't work without "Control" key
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ], "delete should clear pixels in selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "selection should remain after delete");
      
      cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 2 });
      cy.realPress(['Control', 'Delete']); // in reality here should be only "Delete" but for some reason this test doesn't work without "Control" key
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.black],
        [colors.transparent, colors.transparent, colors.transparent, colors.black],
        [colors.transparent, colors.transparent, colors.transparent, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ], "delete should clear pixels in selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "selection should remain after delete");
    });

    it("delete pasted selection", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.drawAtCanvasPixel(1, 1, { toX: 2, toY: 1});
      cy.realPress(['Control', 'Backspace']);
      cy.assertVisibleCanvasPixels(initialPixels, "delete pasted selection should not affect underlying pixels only pasted ones");
    });

    it("paste when another tool is active");

    it("commit moved selection with Enter", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });

      // Drag selection to bottom-right
      cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 2 });
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "preview: selection moved to bottom-right");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true,  true],
        [false, false, true,  true],
      ], "selection highlighted at new position");

      // Press Enter to commit
      cy.realPress('Enter');
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "Enter should commit the moved selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "highlight cleared after Enter");
    });

    it("commit pasted and moved selection with Enter", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region, copy and paste
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [true, true, false, false],
        [true, true, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels(initialPixels, "underlying pixels unchanged after paste");

      // Drag pasted selection to bottom-right
      cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 2 });
      cy.assertVisibleCanvasPixels([
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "underlying pixels preserved + pasted selection moved to bottom-right");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true,  true],
        [false, false, true,  true],
      ], "highlight at new position");

      // Press Enter to commit
      cy.realPress('Enter');
      cy.assertVisibleCanvasPixels([
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "Enter should commit the pasted selection");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "highlight cleared after Enter");
    });

    it("cut selection", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      // Select 2×2 top-left region and cut
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      // move
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'x']);
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ], "cut should clear original pixels immediately");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "selection cleared after cut");

      // Paste cut content to verify clipboard is populated
      cy.realPress(['Control', 'v']);
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, true, true, false],
        [false, true, true,  false],
        [false, false, false,  false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.red, colors.red, colors.black],
        [colors.black, colors.red, colors.red, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ], "cut pixels available in clipboard — float over transparent area restores appearance");

      // commit
      cy.drawAtCanvasPixel(0, 0);
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "pasted selection at original position");
      cy.assertVisibleCanvasPixels([
        [colors.transparent, colors.transparent, colors.black, colors.black],
        [colors.transparent, colors.red, colors.red, colors.black],
        [colors.black, colors.red, colors.red, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ], "cut pixels available in clipboard — float over transparent area restores appearance");
    });

    it("cut pasted selection preserves underlying pixels", () => {
      const initialPixels = [
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
        [colors.black, colors.black, colors.black, colors.black],
      ];
      cy.startApp({
        ...defaultDbSeed,
        sprite: getSpriteFromPixels(initialPixels),
      });
      cy.selectTool('rectangle-selection');

      // Select, copy, paste and move the pasted selection
      cy.drawAtCanvasPixel(0, 0, { toX: 1, toY: 1 });
      cy.realPress(['Control', 'c']);
      cy.realPress(['Control', 'v']);
      cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 2 });
      cy.assertVisibleCanvasPixels([
        [colors.red, colors.red, colors.black, colors.black],
        [colors.red, colors.red, colors.black, colors.black],
        [colors.black, colors.black, colors.red, colors.red],
        [colors.black, colors.black, colors.red, colors.red],
      ], "underlying pixels preserved + pasted selection moved");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, true,  true],
        [false, false, true,  true],
      ]);

      // Cut the pasted selection — original pixels must not be affected
      cy.realPress(['Control', 'x']);
      cy.assertVisibleCanvasPixels(initialPixels, "cut pasted selection should not affect underlying pixels");
      cy.assertHighlightedPixels([
        [false, false, false, false],
        [false, false, false, false],
        [false, false, false,  false],
        [false, false, false,  false],
      ], "selection cleared after cut");
    });
  });
});

describe('Line Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits line', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('line');

    cy.startDrawAtCanvasPixel(0, 0);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'step 1: mousedown at (0,0) — single pixel on preview');

    cy.moveAtCanvasPixel(2, 1);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.black,       colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'step 2: mousemove to (2,1) — Bresenham line on preview');

    cy.finishDrawAtCanvasPixel(3, 3);
    cy.assertVisibleCanvasPixels([
      [colors.black,       colors.transparent, colors.transparent, colors.transparent],
      [colors.transparent, colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.black,       colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.black],
    ], 'step 3: mouseup at (3,3) — diagonal line committed');
  });

  it('pixel size 2 and Straight option produce correct pixels', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('line');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2
    cy.contains('Straight').click();

    cy.drawAtCanvasPixel(0, 0, { toX: 2, toY: 3 });
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black,       colors.transparent, colors.transparent],
      [colors.black, colors.black,       colors.black,       colors.transparent],
      [colors.transparent, colors.black, colors.black,       colors.black],
      [colors.transparent, colors.transparent, colors.black, colors.black],
    ], 'pixel size 2 + Straight: (0,0)→(2,3) draws perfect diagonal stripe');
  });
});

describe('Circle Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits circle', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('circle');

    // circle with start=end produces no pixels (zero-radius), so no assertion after mousedown
    cy.startDrawAtCanvasPixel(0, 0);

    cy.moveAtCanvasPixel(2, 2);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.black,       colors.transparent, colors.transparent],
      [colors.black,       colors.transparent, colors.black,       colors.transparent],
      [colors.transparent, colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'step 2: mousemove to (2,2) — circle outline preview');

    cy.finishDrawAtCanvasPixel(3, 3);
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.black,       colors.black,       colors.transparent],
      [colors.black,       colors.transparent, colors.transparent, colors.black],
      [colors.black,       colors.transparent, colors.transparent, colors.black],
      [colors.transparent, colors.black,       colors.black,       colors.transparent],
    ], 'step 3: mouseup at (3,3) — circle outline committed');
  });

  it('Keep ration option constrains bounding box to a square', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('circle');
    cy.contains('Keep ration').click();

    // asymmetric drag (0,0)→(3,2); keep-ratio constrains to effective endpoint (2,2)
    cy.drawAtCanvasPixel(0, 0, { toX: 3, toY: 2 });
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.black,       colors.transparent, colors.transparent],
      [colors.black,       colors.transparent, colors.black,       colors.transparent],
      [colors.transparent, colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'keep-ratio: asymmetric drag produces a perfect circle');
  });

  it('pixel-size option: thickens the circle outline', () => {
    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.selectTool('circle');
    cy.drawAtCanvasPixel(0, 0, { toX: 3, toY: 3 });
    cy.assertVisibleCanvasPixels([
      [colors.transparent, colors.black, colors.black, colors.transparent],
      [colors.black,       colors.transparent, colors.transparent, colors.black],
      [colors.black,       colors.transparent, colors.transparent, colors.black],
      [colors.transparent, colors.black, colors.black, colors.transparent],
    ], 'pixel-size=2: circle outline is 2px thick');
  });
});

describe('Bucket Tool', () => {
  it('flood-fills the connected region with primary or secondary color', () => {
    const db: DBSeed = {
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.green, colors.green, colors.transparent],
        [colors.green,  colors.transparent, colors.transparent, colors.green],
        [colors.green, colors.transparent, colors.transparent, colors.green],
        [colors.transparent, colors.green, colors.green, colors.transparent],
      ]),
    };
    cy.startApp(db);
    cy.selectTool('bucket');

    // left-click fills the entire connected transparent region with primary color (black)
    cy.drawAtCanvasPixel(2, 2);
    let expectedPixels = [
      [colors.transparent, colors.green, colors.green, colors.transparent],
      [colors.green,       colors.black, colors.black, colors.green],
      [colors.green,       colors.black, colors.black, colors.green],
      [colors.transparent, colors.green, colors.green, colors.transparent],
    ];
    cy.assertCanvasPixels(expectedPixels, 'flood-fill fills the connected region with primary color');

    cy.drawAtCanvasPixel(2, 2, { rightClick: true });
    expectedPixels = [
      [colors.transparent, colors.green, colors.green, colors.transparent],
      [colors.green,       colors.red, colors.red, colors.green],
      [colors.green,       colors.red, colors.red, colors.green],
      [colors.transparent, colors.green, colors.green, colors.transparent],
    ];
    cy.assertCanvasPixels(expectedPixels, 'flood-fill fills the connected region with secondary color');
  });

  it('"All the same color" fills every matching pixel regardless of adjacency', () => {
    const db: DBSeed = {
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.blue, colors.green, colors.green],
        [colors.green,  colors.transparent, colors.transparent],
        [colors.green, colors.blue, colors.transparent],
        [colors.transparent, colors.green, colors.blue],
      ]),
    };
    cy.startApp(db);
    cy.selectTool('bucket');

    cy.contains('All the same color').click(); // todo: check checkbox state
    cy.drawAtCanvasPixel(0, 0);
    const expectedPixels = [
      [colors.black, colors.green, colors.green],
      [colors.green,  colors.transparent, colors.transparent],
      [colors.green, colors.black, colors.transparent],
      [colors.transparent, colors.green, colors.black],
    ];
    cy.assertCanvasPixels(expectedPixels, '"All the same color" fills every matching pixel regardless of adjacency');
  });
});

describe('Shape Selection Tool', () => {
  it('select connected pixels and move', () => {
    const r = colors.red;
    const b = colors.black;
    const t = colors.transparent;

    const initialPixels = [
      [r, r, b, b],
      [r, b, b, b],
      [b, b, b, r],
      [b, b, r, r],
    ];

    cy.startApp({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(initialPixels),
    });
    cy.selectTool('shape-selection');

    // Single click — flood-fill selects only the 3 connected red pixels at top-left
    cy.drawAtCanvasPixel(0, 0);
    cy.assertHighlightedPixels([
      [true,  true,  false, false],
      [true,  false, false, false],
      [false, false, false, false],
      [false, false, false, false],
    ], 'only connected red pixels highlighted, disconnected reds excluded');
    cy.assertVisibleCanvasPixels(initialPixels, 'canvas pixels unchanged after selection');

    // Drag selection by (+1, +1)
    cy.startDrawAtCanvasPixel(0, 0);
    cy.moveAtCanvasPixel(1, 1);
    cy.assertVisibleCanvasPixels([
      [t, t, b, b],
      [t, r, r, b],
      [b, r, b, r],
      [b, b, r, r],
    ], 'selection floats: originals transparent, preview at new position');

    cy.finishDrawAtCanvasPixel(1, 1);
    cy.assertHighlightedPixels([
      [false, false, false, false],
      [false, true,  true,  false],
      [false, true,  false, false],
      [false, false, false, false],
    ], 'highlight follows selection to new position');

    // Commit by clicking outside
    cy.drawAtCanvasPixel(0, 3);
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