import { defaultDbSeed, getSpriteFromPixels, DBSeed } from '../support/data';
import { rgba } from '../support/utils';

const T   = rgba(0, 0, 0, 0);
const RED = rgba(255, 0, 0);

const anchorSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromPixels([[RED, T], [T, T]])
};

const solidSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromPixels([[RED, RED], [RED, RED]])
};

describe('Resize canvas', () => {
  it('anchor positions correctly translate pixels', () => {
    cy.startApp(anchorSeed);
    cy.get('[data-testid="btn-resize-canvas"]').click();

    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();

    cy.get('[data-testid="anchor-top-left"]').click();
    cy.assertResizePreviewPixels(0,
      [[RED, T, T, T], [T, T, T, T], [T, T, T, T], [T, T, T, T]],
      'top-left preview');

    cy.get('[data-testid="anchor-bottom-right"]').click();
    cy.assertResizePreviewPixels(0,
      [[T, T, T, T], [T, T, T, T], [T, T, RED, T], [T, T, T, T]],
      'bottom-right preview');

    cy.get('[data-testid="anchor-center-center"]').click();
    cy.assertResizePreviewPixels(0,
      [[T, T, T, T], [T, RED, T, T], [T, T, T, T], [T, T, T, T]],
      'center-center preview');

    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[T, T, T, T], [T, RED, T, T], [T, T, T, T], [T, T, T, T]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  it('resize → undo reverts size and pixels', () => {
    cy.startApp(anchorSeed); // 2×2, RED at (0,0)
    cy.get('[data-testid="btn-resize-canvas"]').click();
    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();
    cy.get('[data-testid="anchor-top-left"]').click();
    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[RED, T, T, T], [T, T, T, T], [T, T, T, T], [T, T, T, T]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );

    cy.undo();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[RED, T], [T, T]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  it('resize contents scales pixels', () => {
    cy.startApp(solidSeed);
    cy.get('[data-testid="btn-resize-canvas"]').click();

    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();

    cy.get('[data-testid="checkbox-resize-content"]').click();

    cy.assertResizePreviewPixels(0,
      [[RED, RED, RED, RED], [RED, RED, RED, RED], [RED, RED, RED, RED], [RED, RED, RED, RED]],
      'resize contents preview');

    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[RED, RED, RED, RED], [RED, RED, RED, RED], [RED, RED, RED, RED], [RED, RED, RED, RED]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });
});
