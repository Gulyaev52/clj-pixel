import { defaultDbSeed, getSpriteFromCels, getSpriteFromPixels, DBSeed, _2x2Seed } from '../support/data';
import { r, t, y } from '../support/colors';

// 2 frames × 1 layer: frame0 = all r, frame1 = all y (2×2)
const twoFramesSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [
      [[[r, r], [r, r]]],
      [[[y, y], [y, y]]],
    ],
    [{ duration: 100 }, { duration: 100 }],
    [{ 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' }]
  ),
};

// 1 frame × 2 layers: solid-color layers — layer0 = all r, layer1 = all y (2×2)
// Note: draw-cels-on-single-canvas uses putImageData which replaces the full canvas per cel,
// so the last-drawn layer (layer0, drawn after reversal) always wins the composite.
// "Visible layers" → shows layer0 (all r).  "Layer 2 only" → shows layer1 (all y).
const twoLayersSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromCels(
    [
      [
        [[r, r], [r, r]],
        [[y, y], [y, y]],
      ],
    ],
    [{ duration: 100 }],
    [
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' },
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 2' },
    ]
  ),
};

// 1 frame × 1 layer, r at (0,0), rest transparent
const singleSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromPixels([[r, t], [t, t]]),
};

// 1 frame × 1 layer, all r (solid — deterministic in GIF encoding)
const solidRedSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromPixels([[r, r], [r, r]]),
};

describe('Export modal — Image tab', () => {
  it('Frames: all frames shows multiple previews, selected shows one', () => {
    cy.startApp(twoFramesSeed);
    cy.get('[data-testid="btn-export"]').click();

    // Default: All frames → 2 previews
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'all frames - preview 0');
    cy.assertResizePreviewPixels(1, [[y, y], [y, y]], 'all frames - preview 1');

    // Switch to "Selected frames" — active frame is frame 0 → 1 preview
    cy.get('[data-testid="export-frames"]').click();
    cy.contains('.ant-select-item-option', 'Selected frames').click();

    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'selected frames - preview');
    cy.get('[data-testid="preview-frame-1"]').should('not.exist');
  });

  it('Layers: selecting a specific layer changes the preview', () => {
    cy.startApp(twoLayersSeed);
    cy.get('[data-testid="btn-export"]').click();

    // Default: Visible layers — draw-cels-on-single-canvas draws layer1 then layer0 via
    // putImageData (full-canvas replace), so layer0 (all r) always wins.
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'visible layers - layer0 wins');

    // Switch to "Layer 2" (layer1, all y) → different preview
    cy.get('[data-testid="export-layers"]').click();
    cy.contains('.ant-select-item-option', 'Layer 2').click();
    cy.assertResizePreviewPixels(0, [[y, y], [y, y]], 'layer 2 only');

    // Switch back to "Layer 1" → r again
    cy.get('[data-testid="export-layers"]').click();
    cy.contains('.ant-select-item-option', 'Layer 1').click();
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'layer 1 only');
  });

  it('Direction: backwards reverses frame preview order', () => {
    cy.startApp(twoFramesSeed);
    cy.get('[data-testid="btn-export"]').click();

    // Default: Forward
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'forward - preview 0');
    cy.assertResizePreviewPixels(1, [[y, y], [y, y]], 'forward - preview 1');

    // Switch to Backwards
    cy.get('[data-testid="export-direction"]').click();
    cy.contains('.ant-select-item-option', 'Backwards').click();

    cy.assertResizePreviewPixels(0, [[y, y], [y, y]], 'backwards - preview 0');
    cy.assertResizePreviewPixels(1, [[r, r], [r, r]], 'backwards - preview 1');
  });

  it('Frame scale: changes the frame size display', () => {
    cy.startApp(singleSeed); // 2×2 sprite
    cy.get('[data-testid="btn-export"]').click();

    // Default scale=1 → frame size text is "2x2", preview at natural size
    cy.get('[data-testid="export-frame-size"]').should('contain.text', '2x2');
    cy.assertResizePreviewPixels(0, [[r, t], [t, t]], 'scale 1 preview');

    // Increment scale by 1 (1 → 2) via keyboard on slider handle
    cy.get('[data-testid="export-scale-slider"] .ant-slider-handle')
      .focus()
      .type('{rightarrow}');

    // Frame size should now be "4x4" (scale change does not regenerate the preview)
    cy.get('[data-testid="export-frame-size"]').should('contain.text', '4x4');
  });

  it('File: changing filename downloads file under the new name', () => {
    cy.exec('rm -f cypress/downloads/my-sprite.png', { failOnNonZeroExit: false });

    // namedSeed has title = 'export-test', so Export button is enabled from the start
    cy.startApp(_2x2Seed);
    cy.get('[data-testid="btn-export"]').click();

    cy.assertResizePreviewPixels(0, [[r, t], [t, t]], 'file test - preview');

    // Change the filename
    cy.get('[data-testid="export-file-name"]').clear().type('my-sprite').blur();
    cy.get('[data-testid="export-file-name"]').should('have.value', 'my-sprite');

    // Export and verify the downloaded file name and pixel content
    cy.get('[data-testid="btn-export-ok"]').click();
    cy.assertDownloadedPngPixels('cypress/downloads/my-sprite.png', [[r, t], [t, t]], 'downloaded png pixels');
  });

  it('Type: switching to gif shows "Never repeat" option and updates preview', () => {
    cy.startApp(solidRedSeed); // all r 2×2, no transparency
    cy.get('[data-testid="btn-export"]').click();

    // Default: PNG preview, no "Never repeat"
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'png preview');
    cy.contains('Never repeat').should('not.exist');

    // Switch to GIF
    cy.get('[data-testid="export-type"]').click();
    cy.contains('.ant-select-item-option', 'gif').click();

    // "Never repeat" checkbox appears
    cy.contains('Never repeat').should('be.visible');

    // Wait for GIF data-url (async worker generation, up to 15 s)
    cy.get('[data-testid="preview-frame-0"] img', { timeout: 15000 })
      .should(($img) => {
        expect(($img[0] as HTMLImageElement).src).to.match(/^data:image\/gif/);
      });

    // Solid-r sprite: GIF pixels should match PNG pixels (no transparency involved)
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'gif preview');
  });

  it('Split layers: splits each layer into a separate preview frame', () => {
    cy.startApp(twoLayersSeed);
    cy.get('[data-testid="btn-export"]').click();

    // Default: Split layers OFF → 1 composite preview (layer0 wins = all r)
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'split off - layer0 wins');
    cy.get('[data-testid="preview-frame-1"]').should('not.exist');

    // Enable Split layers
    cy.get('[data-testid="export-split-layers"]').click();

    // Now 2 previews: layer0 → frame0 (all r), layer1 → frame1 (all y)
    cy.assertResizePreviewPixels(0, [[r, r], [r, r]], 'split on - layer 0 frame');
    cy.assertResizePreviewPixels(1, [[y, y], [y, y]], 'split on - layer 1 frame');
  });
});
