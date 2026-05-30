import { _4x4EmptySeed, getSpriteFromPixels, DBSeed, _2x2Seed } from '../support/data';
import { r, t } from '../support/colors';

describe('Resize canvas', () => {
  it('anchor positions correctly translate pixels', () => {
    cy.startApp(_2x2Seed);
    cy.get('[data-testid="btn-resize-canvas"]').click();

    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();

    cy.get('[data-testid="anchor-top-left"]').click();
    cy.assertResizePreviewPixels(0,
      [[r, t, t, t], [t, t, t, t], [t, t, t, t], [t, t, t, t]],
      'top-left preview');

    cy.get('[data-testid="anchor-bottom-right"]').click();
    cy.assertResizePreviewPixels(0,
      [[t, t, t, t], [t, t, t, t], [t, t, r, t], [t, t, t, t]],
      'bottom-right preview');

    cy.get('[data-testid="anchor-center-center"]').click();
    cy.assertResizePreviewPixels(0,
      [[t, t, t, t], [t, r, t, t], [t, t, t, t], [t, t, t, t]],
      'center-center preview');

    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[t, t, t, t], [t, r, t, t], [t, t, t, t], [t, t, t, t]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  it('resize → undo reverts size and pixels', () => {
    cy.startApp(_2x2Seed);
    cy.get('[data-testid="btn-resize-canvas"]').click();
    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();
    cy.get('[data-testid="anchor-top-left"]').click();
    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[r, t, t, t], [t, t, t, t], [t, t, t, t], [t, t, t, t]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );

    cy.undo();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[r, t], [t, t]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  it('resize contents scales pixels', () => {
    cy.startApp({
      ..._4x4EmptySeed,
      sprite: getSpriteFromPixels([[r, r], [r, r]])
    });
    cy.get('[data-testid="btn-resize-canvas"]').click();

    cy.get('[data-testid="resize-width"]').clear().type('4').blur();
    cy.get('[data-testid="resize-height"]').clear().type('4').blur();

    cy.get('[data-testid="checkbox-resize-content"]').click();

    cy.assertResizePreviewPixels(0,
      [[r, r, r, r], [r, r, r, r], [r, r, r, r], [r, r, r, r]],
      'resize contents preview');

    cy.get('[data-testid="btn-resize"]').click();

    cy.assertTimelineCelsAndVisiblePixels(
      [[[[r, r, r, r], [r, r, r, r], [r, r, r, r], [r, r, r, r]]]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });
});
