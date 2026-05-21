declare global {
  namespace Cypress {
    interface Chainable {
      selectTool(toolName: string): Chainable<void>;
      drawOnCanvas(x: number, y: number): Chainable<void>;
      addFrame(): Chainable<void>;
      addLayer(): Chainable<void>;
      stubPrompt(returnValue: string | null): Chainable<void>;
      stubConfirm(returnValue: boolean): Chainable<void>;
      waitForAppReady(): Chainable<void>;
    }
  }
}

Cypress.Commands.add('waitForAppReady', () => {
  cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-testid="canvas-current-layer"]').should('be.visible');
});

Cypress.Commands.add('selectTool', (toolName: string) => {
  cy.get(`[data-testid="tool-${toolName}"]`).click();
});

Cypress.Commands.add('drawOnCanvas', (x: number, y: number) => {
  cy.get('[data-testid="canvas-viewport"]')
    .trigger('mousedown', x, y, { button: 0, force: true })
    .trigger('mousemove', x + 5, y + 5, { button: 0, force: true })
    .trigger('mouseup', x + 5, y + 5, { button: 0, force: true });
});

Cypress.Commands.add('addFrame', () => {
  cy.get('[title="add empty frame"]').click();
});

Cypress.Commands.add('addLayer', () => {
  cy.get('[title="add layer"]').click();
});

Cypress.Commands.add('stubPrompt', (returnValue: string | null) => {
  cy.window().then(win => {
    cy.stub(win, 'prompt').returns(returnValue);
  });
});

Cypress.Commands.add('stubConfirm', (returnValue: boolean) => {
  cy.window().then(win => {
    cy.stub(win, 'confirm').returns(returnValue);
  });
});
