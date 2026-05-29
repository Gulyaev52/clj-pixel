import { twoFramesTwoLayersSeed } from '../support/data';
import { g, r, t } from '../support/colors';

describe('Timeline DnD', () => {
  it('drag frame to new position → frames reorder', () => {
    cy.startApp(twoFramesTwoLayersSeed);

    cy.drag('[data-testid="frame-1"]', '[data-testid="frame-drop-0"]');

    cy.assertTimelineCelsAndVisiblePixels(
      [
        [[[t, t], [t, g]], [[g, t], [t, t]]],
        [[[r, t], [t, t]], [[t, t], [t, r]]],
      ],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1', "Layer 2"]
    );
  });

  it('drag layer to new position → layers reorder', () => {
    cy.startApp(twoFramesTwoLayersSeed);

    cy.drag('[data-testid="layer-1"]', '[data-testid="layer-drop-0"]');

    cy.assertTimelineCelsAndVisiblePixels(
      [
        [[[t, t], [t, r]], [[r, t], [t, t]]],   // frame-0: layer-0 red(0,0) | layer-1 red(1,1)
        [[[g, t], [t, t]], [[t, t], [t, g]]], // frame-1: layer-0 green(1,1) | layer-1 empty
      ],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 2', 'Layer 1']
    );
  });
});
