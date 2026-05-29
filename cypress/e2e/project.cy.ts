import { defaultDbSeed, twoFramesTwoLayersSeed } from '../support/data';
import { g, r, t } from '../support/colors';

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
      [[[[t, t], [t, t]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  describe('Save and Load', () => {
    it('save as file → downloads JSON with correct sprite data', () => {
      cy.exec(`rm -f "cypress/downloads/${defaultDbSeed.sprite.title}.json"`, { failOnNonZeroExit: false });
      cy.startApp(defaultDbSeed);

      cy.get('[data-testid="btn-save-as-file"]').click();

      cy.readFile(`cypress/downloads/${defaultDbSeed.sprite.title}.json`, { timeout: 10000 }).then((content) => {
        const expected = {
          version: '1',
          project: {
            sprite: {
              title: defaultDbSeed.sprite.title,
              size: { width: 5, height: 5 },
              frames: [{ duration: 100 }],
              layers: [{ name: 'Layer 1', 'visible?': true, 'automatic-linking?': false }],
              cels: [[{ size: { width: 5, height: 5 }, 'data-url': defaultDbSeed.sprite.cels[0][0]['data-url'] }]]
            }
          }
        };

        expect(content).to.deep.equal(expected);
      });
    });

    it('load from file → restores project name, pixels, and timeline', () => {
      cy.exec(`rm -f "cypress/downloads/${defaultDbSeed.sprite.title}.json"`, { failOnNonZeroExit: false });
      cy.startApp(defaultDbSeed);
      cy.get('[data-testid="btn-save-as-file"]').click();

      cy.readFile(`rm -f "cypress/downloads/${defaultDbSeed.sprite.title}.json"`, { timeout: 10000 }).then((fileContent) => {
        cy.startApp(twoFramesTwoLayersSeed);
        cy.stubConfirm(true);

        cy.get('[data-testid="input-load-from-file"]').selectFile({
          contents: Cypress.Buffer.from(JSON.stringify(fileContent)),
          fileName: `${defaultDbSeed.sprite.title}.json`,
          mimeType: 'application/json'
        }, { force: true });

        cy.get('h3').should('have.text', defaultDbSeed.sprite.title);
        cy.assertTimelineCelsAndVisiblePixels(
          [
            [[[r, t], [t, t]], [[t, t], [t, r]]],
            [[[t, t], [t, g]], [[g, t], [t, t]]],
          ],
          { activeFrameIdx: 0, activeLayerIdx: 0 },
          ['Layer 1', "Layer 2"]
        );
      });
    });
  });

  it('create project clears history — undo after create has no effect', () => {
    cy.startApp(defaultDbSeed);
    cy.stubConfirm(true);

    cy.get('[data-testid="btn-new-project"]').click();
    cy.get('[data-testid="input-project-title"]').clear().type('NewProject');
    cy.get('[data-testid="input-project-width"]').clear().type('3').blur();
    cy.get('[data-testid="input-project-height"]').clear().type('3').blur();
    cy.get('[data-testid="btn-create-project"]').click();
    cy.contains('h3', 'NewProject').should('exist');

    // create project resets undo history — Ctrl+Z has no effect
    cy.undo();
    cy.contains('h3', 'NewProject').should('exist');
    cy.assertTimelineLabels(1, ['Layer 1']);
  });

  describe('Edit title', () => {
    it('enter new title → header updates to new title', () => {
      cy.startApp(defaultDbSeed);
      cy.stubPrompt('NewTitle');

      cy.get('[data-testid="btn-edit-title"]').click();

      cy.contains('h3', 'NewTitle').should('exist');
    });
  });
});
