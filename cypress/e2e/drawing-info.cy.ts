import { defaultDbSeed } from '../support/data';

describe('drawing-info', () => {
  it('shows sprite size, mouse pos, and scale', () => {
    // --- sprite size ---
    cy.startApp(defaultDbSeed); // 5×5
    cy.get('[data-testid="drawing-info-sprite-size"]')
      .should('have.text', '[5x5]', 'sprite size for 5x5 seed');

    // --- mouse pos ---
    cy.startApp(defaultDbSeed);
    cy.mouseMoveOnCanvas({x: 0, y: 0});
    cy.get('[data-testid="drawing-info-mouse-pos"]')
      .should('have.text', '0:0', 'mouse pos at (0,0)');
    cy.mouseMoveOnCanvas({x: 2, y: 3});
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
