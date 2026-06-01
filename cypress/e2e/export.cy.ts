import { twoFramesTwoLayersSeed } from '../support/data';
import { r, g, t } from '../support/colors';
import { rgba } from '../support/utils';

// White: GIF export fills transparent pixels with a white background, so transparent
// pixels in the source read back as opaque white in the GIF preview.
const w = rgba(255, 255, 255);

describe('Export modal — Image tab', () => {
  it('Frames: all frames shows multiple previews, selected shows one', () => {
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default: All frames → 2 previews
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'all frames - preview 0');
    cy.assertResizePreviewPixels(1, [[g, t], [t, g]], 'all frames - preview 1');

    // Switch to "Selected frames" — active frame is frame 0 → 1 preview
    cy.get('[data-testid="export-frames"]').click();
    cy.contains('.ant-select-item-option', 'Selected frames').click();

    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'selected frames - preview');
    cy.get('[data-testid="preview-frame-1"]').should('not.exist');
  });

  it('Layers: selecting a specific layer changes the preview', () => {
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default: Visible layers — both layers are composited with alpha blending.
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'visible layers - composite');

    // Switch to "Layer 2" (layer1) → only its pixels
    cy.get('[data-testid="export-layers"]').click();
    cy.contains('.ant-select-item-option', 'Layer 2').click();
    cy.assertResizePreviewPixels(0, [[t, t], [t, r]], 'layer 2 only');

    // Switch back to "Layer 1" (layer0)
    cy.get('[data-testid="export-layers"]').click();
    cy.contains('.ant-select-item-option', 'Layer 1').click();
    cy.assertResizePreviewPixels(0, [[r, t], [t, t]], 'layer 1 only');
  });

  it('Direction: backwards reverses frame preview order', () => {
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default: Forward
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'forward - preview 0');
    cy.assertResizePreviewPixels(1, [[g, t], [t, g]], 'forward - preview 1');

    // Switch to Backwards
    cy.get('[data-testid="export-direction"]').click();
    cy.contains('.ant-select-item-option', 'Backwards').click();

    cy.assertResizePreviewPixels(0, [[g, t], [t, g]], 'backwards - preview 0');
    cy.assertResizePreviewPixels(1, [[r, t], [t, r]], 'backwards - preview 1');
  });

  it('Frame scale: changes the frame size display', () => {
    cy.startApp(twoFramesTwoLayersSeed); // 2×2 sprite
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default scale=1 → frame size text is "2x2", preview at natural size
    cy.get('[data-testid="export-frame-size"]').should('contain.text', '2x2');
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'scale 1 preview');

    // Increment scale by 1 (1 → 2) via keyboard on slider handle
    cy.get('[data-testid="export-scale-slider"] .ant-slider-handle')
      .focus()
      .type('{rightarrow}');

    // Frame size should now be "4x4" (scale change does not regenerate the preview)
    cy.get('[data-testid="export-frame-size"]').should('contain.text', '4x4');
  });

  it('File: changing filename downloads file under the new name', () => {
    cy.exec('rm -f cypress/downloads/my-sprite.png', { failOnNonZeroExit: false });

    // twoFramesTwoLayersSeed has title 'ProjectTitle', so Export is enabled from the start
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Use "Selected frames" (frame 0) so PNG export stays a single file (>1 frame → zip)
    cy.get('[data-testid="export-frames"]').click();
    cy.contains('.ant-select-item-option', 'Selected frames').click();

    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'file test - preview');

    // Change the filename
    cy.get('[data-testid="export-file-name"]').clear().type('my-sprite').blur();
    cy.get('[data-testid="export-file-name"]').should('have.value', 'my-sprite');

    // Export and verify the downloaded file name and pixel content
    cy.get('[data-testid="btn-open-export-modal-ok"]').click();
    cy.assertDownloadedPngPixels('cypress/downloads/my-sprite.png', [[r, t], [t, r]], 'downloaded png pixels');
  });

  it('Type: switching to gif shows "Never repeat" option and updates preview', () => {
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default: PNG preview, no "Never repeat"
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'png preview');
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

    // GIF fills transparent pixels white, so the frame-0 composite reads back with
    // white where it was transparent.
    cy.assertResizePreviewPixels(0, [[r, w], [w, r]], 'gif preview');
  });

  it('Split layers: splits each layer into a separate preview frame', () => {
    cy.startApp(twoFramesTwoLayersSeed);
    cy.get('[data-testid="btn-open-export-modal"]').click();

    // Default: Split layers OFF → 2 composite previews (one per frame)
    cy.assertResizePreviewPixels(0, [[r, t], [t, r]], 'split off - frame 0 composite');
    cy.assertResizePreviewPixels(1, [[g, t], [t, g]], 'split off - frame 1 composite');
    cy.get('[data-testid="preview-frame-2"]').should('not.exist');

    // Enable Split layers
    cy.get('[data-testid="export-split-layers"]').click();

    // Now 4 previews: frame×layer flattened
    cy.assertResizePreviewPixels(0, [[r, t], [t, t]], 'split on - frame 0 layer 1');
    cy.assertResizePreviewPixels(1, [[t, t], [t, r]], 'split on - frame 0 layer 2');
    cy.assertResizePreviewPixels(2, [[t, t], [t, g]], 'split on - frame 1 layer 1');
    cy.assertResizePreviewPixels(3, [[g, t], [t, t]], 'split on - frame 1 layer 2');
  });
});
