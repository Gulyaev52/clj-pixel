import { twoFramesTwoLayersSeed } from '../support/data';
import { r, g, t } from '../support/colors';

describe('Sprite preview', () => {
  it('opens on the preview button and animates through every frame', () => {
    cy.startApp(twoFramesTwoLayersSeed);

    cy.get('[data-testid="btn-show-preview"]').click();

    // Both composited frames are shown as the animation cycles
    cy.assertSpritePreviewPixels([[r, t], [t, r]], 'shows frame 0');
    cy.assertSpritePreviewPixels([[g, t], [t, g]], 'shows frame 1');
  });

  it('closes on Escape', () => {
    cy.startApp(twoFramesTwoLayersSeed);

    cy.get('[data-testid="btn-show-preview"]').click();
    cy.get('[data-testid="sprite-preview"]').should('be.visible');

    cy.realPress('Escape');
    cy.get('[data-testid="sprite-preview"]').should('not.exist');
  });
});
