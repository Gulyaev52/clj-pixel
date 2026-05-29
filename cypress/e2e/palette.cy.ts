import { defaultDbSeed, DBSeed, getSpriteFromPixels } from '../support/data';
import { rgbaToInt, rgba } from '../support/utils';

describe('Palette', () => {
  const addFromFrameSeed: DBSeed = {
    ...defaultDbSeed,
    palettes: [{ name: 'default', current: true, colors: [rgbaToInt(255, 0, 0)] }],
    sprite: getSpriteFromPixels([
      [rgba(255, 0, 0), rgba(0, 255, 0)],  // red (уже в палитре), green (новый)
      [rgba(0, 0, 255), rgba(0, 0, 0, 0)]  // blue (новый), transparent (пропускается)
    ])
  };

  const twopaletteSeed: DBSeed = {
    ...defaultDbSeed,
    palettes: [
      { name: 'default', current: false, colors: [] },
      { name: 'Second', current: true, colors: [] }
    ]
  };

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

  it('creates new palette, allows adding and selecting colors, and persists after reload', () => {
    cy.startApp(defaultDbSeed);

    cy.stubPrompt('My Palette');
    cy.get('[title="Add palette"]').click();
    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'My Palette', 'new palette is selected');
    cy.get('[data-testid^="palette-color-"]')
      .should('not.exist', 'new palette has no colors');

    cy.get('[data-testid="palette-select"]').click();
    cy.contains('.ant-select-item-option-content', 'default')
      .should('be.visible', 'default palette still in options');
    cy.contains('.ant-select-item-option-content', 'My Palette')
      .should('be.visible', 'My Palette still in options');

    cy.get('[title="Add color"]').click({ force: true });
    cy.get('[data-testid="color-picker-hex-input"]').clear().type('FF0000');
    cy.get('[data-testid="add-color-confirm-button"]').click();
    cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]')
      .should('be.visible', 'color added to new palette');

    cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]').click({ force: true });
    cy.get('[data-testid="primary-color-swatch"]')
      .should('have.css', 'background-color', 'rgb(255, 0, 0)', 'added color selected as primary');

    cy.reload();
    cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');

    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'My Palette', 'New added palette active after reload');
    cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]')
      .should('be.visible', 'added color preserved in My Palette after reload');
  });

  it('renames current palette and persists after reload', () => {
    cy.startApp(defaultDbSeed);

    cy.stubPrompt('Renamed Palette');
    cy.get('[data-testid="rename-palette-button"]').click();

    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'Renamed Palette', 'palette name updated in select');
    cy.get('[data-testid="palette-select"]')
      .should('not.contain.text', 'default', 'old name is gone');

    cy.reload();
    cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');

    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'Renamed Palette', 'new name persists after reload');
  });

  it('"Add colors from current frame" adds only new non-transparent colors from current frame to palette', () => {
    cy.startApp(addFromFrameSeed);

    cy.get('[title="Add colors from current frame"]').click();

    cy.get('[data-testid^="palette-color-"]')
      .should('have.length', 3, 'exactly 3 distinct non-transparent colors');

    cy.get('[data-testid^="palette-color-"]').eq(0)
      .should('have.attr', 'data-testid', 'palette-color-rgba(255,0,0,1)',
        'existing red color stays at index 0');
    cy.get('[data-testid^="palette-color-"]').eq(1)
      .should('have.attr', 'data-testid', 'palette-color-rgba(0,255,0,1)',
        'green appended after existing colors');
    cy.get('[data-testid^="palette-color-"]').eq(2)
      .should('have.attr', 'data-testid', 'palette-color-rgba(0,0,255,1)',
        'blue appended after existing colors');
    cy.contains('.ant-notification-notice-message', '2 colors were added!').should('be.visible');
  });

  it('removes current palette: button disables when one remains, first palette selected, does not persist after reload', () => {
    cy.startApp(twopaletteSeed);

    cy.get('[data-testid="remove-palette-button"]')
      .should('not.be.disabled', 'remove button enabled with 2 palettes');

    cy.stubConfirm(true);
    cy.get('[data-testid="remove-palette-button"]').click();

    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'default', 'first palette is selected after deletion');
    cy.get('[data-testid="remove-palette-button"]')
      .should('be.disabled', 'remove button disabled when only 1 palette remains');

    cy.reload();
    cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');

    cy.get('[data-testid="palette-select"]')
      .should('contain.text', 'default', 'first palette shown after reload');
    cy.get('[data-testid="palette-select"]').click();
    cy.contains('.ant-select-item-option-content', 'Second')
      .should('not.exist', 'removed palette absent from dropdown after reload');
  });

  describe('Import/Export', () => {
    const expectedExportContent = [
      'GIMP Palette',
      'Name: default',
      'Columns: 0',
      '0 0 0 Untitled',
      '255 0 0 Untitled',
      '0 0 255 Untitled',
      '0 128 0 Untitled'
    ].join('\n');

    const importedGplContent = [
      'GIMP Palette',
      'Name: Imported Palette',
      'Columns: 0',
      '255 0 0 Untitled',
      '0 255 0 Untitled',
      '0 0 255 Untitled'
    ].join('\n');

    it('exports current palette as GPL file', () => {
      cy.startApp(defaultDbSeed);
      cy.exec('rm -f cypress/downloads/default.gpl', { failOnNonZeroExit: false });

      cy.get('[data-testid="export-palette-button"]').click();

      cy.readFile('cypress/downloads/default.gpl', { timeout: 10000 }).then((content: string) => {
        expect(expectedExportContent).to.equal(content);
      });
    });

    it('imports valid GPL file, appends palette at end, and persists after reload', () => {
      cy.startApp(defaultDbSeed);

      cy.get('[data-testid="import-palette-input"]').selectFile({
        contents: Cypress.Buffer.from(importedGplContent),
        fileName: 'palette.gpl',
        mimeType: 'text/plain'
      }, { force: true });

      cy.get('[data-testid="palette-select"]')
        .should('contain.text', 'Imported Palette', 'imported palette is selected');
      cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]').should('be.visible', 'red imported');
      cy.get('[data-testid="palette-color-rgba(0,255,0,1)"]').should('be.visible', 'green imported');
      cy.get('[data-testid="palette-color-rgba(0,0,255,1)"]').should('be.visible', 'blue imported');

      cy.get('[data-testid="palette-select"]').click();
      cy.get('.ant-select-item-option-content').first()
        .should('have.text', 'default', 'original palette is first');
      cy.get('.ant-select-item-option-content').last()
        .should('have.text', 'Imported Palette', 'imported palette is last');

      cy.reload();
      cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-testid="palette-select"]')
        .should('contain.text', 'Imported Palette', 'imported palette persists after reload');
      cy.get('[data-testid="palette-color-rgba(255,0,0,1)"]')
        .should('be.visible', 'colors persist after reload');
    });

    it('shows error alert when importing invalid GPL file and does not change palette', () => {
      cy.startApp(defaultDbSeed);

      cy.window().then((win) => {
        cy.stub(win, 'alert').as('alertStub');
      });

      cy.get('[data-testid="import-palette-input"]').selectFile({
        contents: Cypress.Buffer.from('not a valid gpl content'),
        fileName: 'invalid.gpl',
        mimeType: 'text/plain'
      }, { force: true });

      cy.get('@alertStub').should('have.been.calledWith', 'invalid file content');
      cy.get('[data-testid="palette-select"]')
        .should('contain.text', 'default', 'palette unchanged after failed import');
    });
  });
});
