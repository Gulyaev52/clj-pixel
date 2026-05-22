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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(getPixels(4, 4, colors.black)),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.green, colors.blue],
        [colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.green,       colors.green,       colors.transparent],
        [colors.transparent, colors.green,       colors.green,       colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
});

describe('Line Tool', () => {
  it('step-by-step: mousedown → intermediate drag → mouseup commits line', () => {
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
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
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels([
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
        [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
      ]),
    });
    cy.waitForAppReady();
    cy.selectTool('circle');
    cy.contains('Keep ration').click();
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2

    // asymmetric drag (0,0)→(3,2); keep-ratio constrains to effective endpoint (2,2)
    cy.drawAtCanvasPixel(0, 0, { toX: 3, toY: 2 });
    cy.assertVisibleCanvasPixels([
      [colors.black, colors.black, colors.black, colors.transparent],
      [colors.black, colors.transparent, colors.black, colors.transparent],
      [colors.black, colors.black, colors.black, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'keep-ratio: asymmetric drag produces a perfect circle');
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
    cy.seedDatabase(db);
    cy.waitForAppReady();
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
    cy.seedDatabase(db);
    cy.waitForAppReady();
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