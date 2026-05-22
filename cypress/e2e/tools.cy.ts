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
  beforeEach(() => {
    cy.seedDatabase(defaultDbSeed);
    cy.waitForAppReady();
    cy.selectTool('pen');
  });

  it("pen", () => {
    cy.contains('Pixel size').should('be.visible');
    // pixel size = 2
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true });

    cy.drawAtCanvasPixel(1, 1, { toX: 3, toY: 3 });
    cy.assertCanvasPixels([
      [colors.black, colors.black, colors.transparent, colors.transparent, colors.transparent],
      [colors.black, colors.black, colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.black, colors.black, colors.black,       colors.transparent],
      [colors.transparent, colors.transparent, colors.black, colors.black, colors.transparent],
      [colors.transparent, colors.transparent, colors.transparent, colors.transparent, colors.transparent],
    ], 'pen draws a diagonal stroke with primary color');

    cy.drawAtCanvasPixel(4, 4, { rightClick: true });
    cy.assertCanvasPixels([
      [colors.black, colors.black, colors.transparent, colors.transparent, colors.transparent],
      [colors.black, colors.black, colors.black,       colors.transparent, colors.transparent],
      [colors.transparent, colors.black, colors.black, colors.black,       colors.transparent],
      [colors.transparent, colors.transparent, colors.black, colors.red,   colors.red],
      [colors.transparent, colors.transparent, colors.transparent, colors.red, colors.red],
    ], 'right-click draws with secondary color');
  });
});

describe('Eraser Tool', () => {
  beforeEach(() => {
    cy.seedDatabase({
      ...defaultDbSeed,
      sprite: getSpriteFromPixels(getPixels(5, 5, colors.black)),
    });
    cy.waitForAppReady();
    cy.selectTool('eraser');
  });

  it('eraser tool erases correctly', () => {
    // options panel shows Pixel size slider (same spec as pen)
    cy.contains('Pixel size').should('be.visible');
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true }); // pixel size = 2

    cy.drawAtCanvasPixel(1, 1, { toX: 2, toY: 2 });
    cy.drawAtCanvasPixel(1, 1, { toX: 3, toY: 3, rightClick: true });
    const expectedPixels = [
      [colors.transparent, colors.transparent, colors.black, colors.black, colors.black],
      [colors.transparent, colors.transparent, colors.transparent, colors.black, colors.black],
      [colors.black, colors.transparent, colors.transparent, colors.transparent, colors.black],
      [colors.black, colors.black, colors.transparent, colors.transparent, colors.black],
      [colors.black, colors.black, colors.black, colors.black, colors.black],
    ];
    cy.assertCanvasPixels(expectedPixels, 'eraser turns pixels back to transparent');
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