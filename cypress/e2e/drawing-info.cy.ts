import { defaultDbSeed, getSpriteFromPixels, DBSeed } from '../support/data';
import { t } from '../support/colors';

const size3x2Seed: DBSeed = {
  ...defaultDbSeed,
  sprite: {
    ...getSpriteFromPixels([
      [t, t, t],
      [t, t, t],
    ])
  }
};

describe('drawing-info', () => {
  it('shows sprite size, mouse pos, and scale', () => {
    // --- sprite size ---
    cy.startApp(defaultDbSeed); // 5×5
    cy.get('[data-testid="drawing-info-sprite-size"]')
      .should('have.text', '[5x5]', 'sprite size for 5x5 seed');

    cy.startApp(size3x2Seed); // 3×2
    cy.get('[data-testid="drawing-info-sprite-size"]')
      .should('have.text', '[3x2]', 'sprite size for 3x2 seed');

    // --- mouse pos ---
    cy.startApp(defaultDbSeed);
    cy.moveAtCanvasPixel(0, 0);
    cy.get('[data-testid="drawing-info-mouse-pos"]')
      .should('have.text', '0:0', 'mouse pos at (0,0)');
    cy.moveAtCanvasPixel(2, 3);
    cy.get('[data-testid="drawing-info-mouse-pos"]')
      .should('have.text', '2:3', 'mouse pos at (2,3)');

    // --- scale ---
    cy.get('[data-testid="drawing-info-scale"]')
      .invoke('text')
      .should('match', /^scale=\d+\.\d{2}$/, 'scale format');

    // zoom out → scale decreases
    cy.get('[data-testid="drawing-info-scale"]').then(($el) => {
      const before = parseFloat($el.text().replace('scale=', ''));
      cy.get('[data-testid="canvas-viewport"]')
        .trigger('wheel', { deltaY: 300, clientX: 400, clientY: 300 });
      cy.get('[data-testid="drawing-info-scale"]').should(($el) => {
        const after = parseFloat($el.text().replace('scale=', ''));
        expect(after, 'scale decreases on zoom out').to.be.lessThan(before);
      });
    });

    // zoom in → scale increases
    cy.get('[data-testid="drawing-info-scale"]').then(($el) => {
      const before = parseFloat($el.text().replace('scale=', ''));
      cy.get('[data-testid="canvas-viewport"]')
        .trigger('wheel', { deltaY: -300, clientX: 400, clientY: 300 });
      cy.get('[data-testid="drawing-info-scale"]').should(($el) => {
        const after = parseFloat($el.text().replace('scale=', ''));
        expect(after, 'scale increases on zoom in').to.be.greaterThan(before);
      });
    });
  });
});
