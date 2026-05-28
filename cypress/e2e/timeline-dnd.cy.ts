import { defaultDbSeed, DBSeed, getSpriteFromCels } from '../support/data';
import { rgba } from '../support/utils';

const T    = rgba(0, 0, 0, 0);
const RED  = rgba(255, 0, 0);
const BLUE = rgba(0, 0, 255);

const frameDragSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [
      [[[RED,  T], [T, T]]],  // frame 0: RED  at (0,0)
      [[[BLUE, T], [T, T]]],  // frame 1: BLUE at (0,0)
    ],
    [{ duration: 100 }, { duration: 100 }],
    [{ 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' }]
  )
};

const layerDragSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [[
      [[RED, T],  [T, T]],  // layer 0 (Layer 1): RED  at (0,0)
      [[T, BLUE], [T, T]],  // layer 1 (Layer 2): BLUE at (1,0)
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
        [[[BLUE, T], [T, T]]],  // frame 0 ← was frame 1
        [[[RED,  T], [T, T]]],  // frame 1 ← was frame 0
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
        [[T, BLUE], [T, T]],  // layer 0 ← was layer 1 (Layer 2)
        [[RED, T],  [T, T]],  // layer 1 ← was layer 0 (Layer 1)
      ]],
      { activeFrameIdx: 0, activeLayerIdx: 0 },
      ['Layer 2', 'Layer 1']
    );
  });
});
