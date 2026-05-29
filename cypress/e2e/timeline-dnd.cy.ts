import { defaultDbSeed, DBSeed, getSpriteFromCels } from '../support/data';
import { r, t, y } from '../support/colors';

const frameDragSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [
      [[[r,  t], [t, t]]],  // frame 0: r  at (0,0)
      [[[y, t], [t, t]]],  // frame 1: y at (0,0)
    ],
    [{ duration: 100 }, { duration: 100 }],
    [{ 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' }]
  )
};

const layerDragSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [[
      [[r, t],  [t, t]],  // layer 0 (Layer 1): r  at (0,0)
      [[t, y], [t, t]],  // layer 1 (Layer 2): y at (1,0)
    ]],
    [{ duration: 100 }],
    [
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' },
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 2' },
    ]
  )
};

describe('Timeline DnD', () => {
  it('drag frame to new position → frames reorder', () => {
    cy.startApp(frameDragSeed);

    cy.drag('[data-testid="frame-1"]', '[data-testid="frame-drop-0"]');

    cy.assertTimelineCelsAndVisiblePixels(
      [
        [[[y, t], [t, t]]],  // frame 0 ← was frame 1
        [[[r,  t], [t, t]]],  // frame 1 ← was frame 0
      ],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 1']
    );
  });

  it('drag layer to new position → layers reorder', () => {
    cy.startApp(layerDragSeed);

    cy.drag('[data-testid="layer-1"]', '[data-testid="layer-drop-0"]');

    cy.assertTimelineCelsAndVisiblePixels(
      [[
        [[t, y], [t, t]],  // layer 0 ← was layer 1 (Layer 2)
        [[r, t],  [t, t]],  // layer 1 ← was layer 0 (Layer 1)
      ]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 2', 'Layer 1']
    );
  });
});
