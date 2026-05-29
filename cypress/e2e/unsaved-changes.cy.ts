import { defaultDbSeed, DBSeed } from '../support/data';
import { b, t } from '../support/colors';

const TITLE = 'TestProject';
const E = Array(5).fill(t); // empty row

const seed: DBSeed = {
  ...defaultDbSeed,
  sprite: { ...defaultDbSeed.sprite, title: TITLE }
};

// похожие тесты в project
describe('Unsaved changes', () => {
  it('* appears next to title after a change', () => {
    cy.startApp(seed);
    cy.get('[data-testid="btn-add-layer"]').click();
    cy.contains('h3', TITLE + '*').should('exist');
  });

  it('* disappears after save as file', () => {
    cy.exec('rm -f "cypress/downloads/TestProject.json"', { failOnNonZeroExit: false });
    cy.startApp(seed);
    cy.get('[data-testid="btn-add-layer"]').click();
    cy.contains('h3', TITLE + '*').should('exist', '* appears after change');
    cy.get('[data-testid="btn-save-as-file"]').click();
    cy.contains('h3', TITLE + '*').should('not.exist', '* disappears after save');
  });

  it('warns on page close when there are unsaved changes', () => {
    cy.on('window:before:load', (win) => {
      const orig = win.addEventListener.bind(win);
      (win as any).addEventListener = (type: string, listener: any, options?: any) => {
        if (type === 'beforeunload') (win as any).__beforeunloadHandler = listener;
        return orig(type, listener, options);
      };
    });

    cy.startApp(seed);
    cy.get('[data-testid="btn-add-layer"]').click();

    cy.window().then(win => {
      const handler = (win as any).__beforeunloadHandler;
      const e: any = { returnValue: '' };
      handler(e);
      expect(e.returnValue).to.equal('Your current sprite has unsaved changes. Are you sure you want to quit?');
    });
  });

  it('save in browser → * disappears, notification shown, backup applied after reload', () => {
    cy.startApp(seed);
    cy.drawAtCanvasPixel(0, 0);
    cy.get('[data-testid="btn-add-layer"]').click();
    cy.contains('h3', TITLE + '*').should('exist', '* appears after change');
    cy.get('[data-testid="btn-save-in-browser"]').click();
    cy.contains('h3', TITLE + '*').should('not.exist', '* disappears after save');
    cy.contains('.ant-notification-notice-message', 'Successfully saved!').should('be.visible');

    cy.visit('/index.html');
    cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-testid="current-layer"]').should('be.visible');

    cy.assertTimelineCelsAndVisiblePixels(
      [[
        [[b, t, t, t, t], E, E, E, E],
        [E, E, E, E, E],
      ]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1', 'Layer 2']
    );
  });

  it('auto-backup (5 min) → * disappears, notification shown, backup applied after reload', () => {
    cy.clock(Date.now(), ['setInterval', 'clearInterval']);
    cy.startApp(seed);
    cy.drawAtCanvasPixel(0, 0);
    cy.get('[data-testid="btn-add-layer"]').click();
    cy.contains('h3', TITLE + '*').should('exist', '* appears after change');
    cy.tick(300000);
    cy.contains('h3', TITLE + '*').should('not.exist', '* disappears after backup');
    cy.contains('.ant-notification-notice-message', 'Auto-backup').should('be.visible');

    cy.visit('/index.html');
    cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-testid="current-layer"]').should('be.visible');

    cy.assertTimelineCelsAndVisiblePixels(
      [[
        [[b, t, t, t, t], E, E, E, E],
        [E, E, E, E, E],
      ]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1', 'Layer 2']
    );
  });
});
