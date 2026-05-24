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

  it('left click selects primary color, right click selects secondary color', () => {
    cy.startApp(defaultDbSeed);

    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]').click({ force: true });
    cy.get('[data-testid="primary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 0, 255)', 'primary swatch updated to blue');
    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]')
      .should('contain.text', 'L', 'L marker on selected primary color');
    cy.get('[data-testid="palette-color-rgba(0,0,0,1)"]')
      .should('not.contain.text', 'L', 'L marker removed from previous primary color');

    cy.get('[data-testid="palette-color-rgba(0,128,0,1)"]').rightclick({ force: true });
    cy.get('[data-testid="secondary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 128, 0)', 'secondary swatch updated to green');
    cy.get('[data-testid="palette-color-rgba(0,128,0,1)"]')
      .should('contain.text', 'R', 'R marker on selected secondary color');
    cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]')
      .should('not.contain.text', 'R', 'R marker removed from previous secondary color');
  });

  it('same color as primary and secondary shows both L and R markers', () => {
    cy.startApp(defaultDbSeed);

    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]').click({ force: true });
    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]').rightclick({ force: true });

    cy.get('[data-testid="primary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 0, 255)', 'primary swatch is blue');
    cy.get('[data-testid="secondary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(0, 0, 255)', 'secondary swatch is blue');
    cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]')
      .should('contain.text', 'L', 'L marker present')
      .and('contain.text', 'R', 'R marker present');
  });
});
