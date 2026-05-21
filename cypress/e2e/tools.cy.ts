// Seed data: 20×20 sprite, all pixels transparent.
// Primary  = black  → pixels[x][y] = [0,   0, 0, 255]
// Secondary = red   → pixels[x][y] = [255, 0, 0, 255]

const getEmptyPixels = (width: number, height: number, colors?: Array<[[number, number], [number, number, number, number]]>) => {
  const pixels = Array(height).fill(null).map(() => Array(width).fill([0, 0, 0, 0]));
  colors?.forEach(([[x1, y1], [r, g, b, a]]) => {
    pixels[x1][y1] = [r, g, b, a];
  });
  return pixels;
};

const colors = {
  black: [0, 0, 0, 255],
  red: [255, 0, 0, 255],
  transparent: [0, 0, 0, 0],
};

describe('Drawing Tools', () => {
  beforeEach(() => {
    cy.seedDatabase();
    cy.waitForAppReady();
    cy.selectTool('pen');
  });

  it('pen tool draws correctly', () => {
    // options panel shows Pixel size slider
    cy.contains('Pixel size').should('be.visible');

    const expectedPixels = getEmptyPixels(5, 5);
    cy.drawAtCanvasPixel(0, 1);
    cy.getCanvasPixels((pixels) => {
      expectedPixels[0][1] = colors.black;
      expect(pixels, 'only pixel (0,1) painted black').to.deep.equal(expectedPixels);
    });

    // draws a stroke when dragged — all pixels between endpoints are painted
    cy.drawAtCanvasPixel(2, 0, { toX: 4, toY: 2 });
    cy.getCanvasPixels((pixels) => {
      expectedPixels[2][0] = colors.black;
      expectedPixels[3][1] = colors.black;
      expectedPixels[4][2] = colors.black;
      expect(pixels, 'stroke pixels painted black').to.deep.equal(expectedPixels);
    });

    // right-click draws with secondary color (red)
    cy.drawAtCanvasPixel(2, 2, { rightClick: true });
    cy.getCanvasPixels((pixels) => {
      expectedPixels[2][2] = colors.red;
      expect(pixels).to.deep.equal(expectedPixels);
    });

    // pixel-size=3: 3×3 brush — direct neighbor is also painted
    cy.get('.ant-slider-handle').first().focus().type('{rightarrow}', { force: true });
    cy.drawAtCanvasPixel(1, 4);
    cy.getCanvasPixels((pixels) => {
      expectedPixels[1][4] = colors.black;
      expectedPixels[1][3] = colors.black;
      expectedPixels[0][4] = colors.black;
      expectedPixels[0][3] = colors.black;
      expect(pixels).to.deep.equal(expectedPixels);
    });
  });
});
