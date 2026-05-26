import { defaultDbSeed } from '../support/data';
import { rgba } from '../support/utils';

const T = rgba(0, 0, 0, 0);

describe('Project', () => {
  it('new project → inputs show set values; canvas, title, and timeline update on create', () => {
    cy.startApp(defaultDbSeed);
    cy.stubConfirm(true);

    cy.get('[data-testid="btn-new-project"]').click();

    cy.get('[data-testid="input-project-title"]').clear().type('My Project').blur();
    cy.get('[data-testid="input-project-title"]').should('have.value', 'My Project');

    cy.get('[data-testid="input-project-width"]').clear().type('2').blur();
    cy.get('[data-testid="input-project-width"]').should('have.value', '2');

    cy.get('[data-testid="input-project-height"]').clear().type('2').blur();
    cy.get('[data-testid="input-project-height"]').should('have.value', '2');

    cy.get('[data-testid="btn-create-project"]').click();

    cy.get('[data-testid="btn-create-project"]').should('not.exist');
    cy.contains('h3', 'My Project').should('exist');
    cy.assertTimelineCelsAndVisiblePixels(
      [[[[T, T], [T, T]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });
});
