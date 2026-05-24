import { defaultDbSeed } from '../support/data';

describe('Palette', () => {
  it('adds a color via Hex input', () => {
    cy.startApp(defaultDbSeed);

    cy.get('[title="Add color"]').click();
    cy.get('[data-testid="color-picker-hex-input"]').clear().type('FF8800');
    cy.get('[data-testid="add-color-confirm-button"]').click();

    cy.get('[data-testid="palette-color-rgba(255,136,0,1)"]').should('be.visible');
    cy.get('[data-testid="color-picker-hex-input"]').should('not.exist');
  });

  it('adds a color via R/G/B inputs', () => {
    cy.startApp(defaultDbSeed);

    cy.get('[title="Add color"]').click();
    cy.get('[data-testid="color-picker-r-input"]').clear().type('100');
    cy.get('[data-testid="color-picker-g-input"]').clear().type('200');
    cy.get('[data-testid="color-picker-b-input"]').clear().type('50');
    cy.get('[data-testid="add-color-confirm-button"]').click();

    cy.get('[data-testid="palette-color-rgba(100,200,50,1)"]').should('be.visible');
    cy.get('[data-testid="color-picker-hex-input"]').should('not.exist');
  });

  it('removes only the specified color', () => {
    cy.startApp(defaultDbSeed);

    cy.get('[data-testid="remove-palette-color-1"]').click({ force: true });

    cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]').should('not.exist');
    cy.get('[data-testid="palette-color-rgba(0,0,0,1)"]').should('be.visible');
    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]').should('be.visible');
    cy.get('[data-testid="palette-color-rgba(0,128,0,1)"]').should('be.visible');
  });
});
