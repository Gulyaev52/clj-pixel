import { _4x4EmptySeed, getSpriteFromPixels, DBSeed } from '../support/data';
import { r, t } from '../support/colors';

const diagonalSeed: DBSeed = {
  ..._4x4EmptySeed,
  sprite: getSpriteFromPixels([
    [r, t, t, r],
    [t, r, r, t],
    [t, r, r, t],
    [r, t, t, r],
  ])
};

const triggerZoom = (deltaY: number) =>
  cy.get('[data-testid="canvas-viewport"]')
    .trigger('wheel', { deltaY, clientX: 400, clientY: 300 });

// Рекурсивно зумит пока не достигнет нужного scale
const zoomToScale = (target: number, direction: 'in' | 'out') => {
  cy.get('[data-testid="drawing-info-scale"]').then(($s) => {
    const current = parseFloat($s.text().replace('scale=', ''));
    const reached = direction === 'out' ? current <= target : current >= target;
    if (!reached) {
      const prev = $s.text();
      triggerZoom(direction === 'out' ? 300 : -300);
      cy.get('[data-testid="drawing-info-scale"]').should('not.have.text', prev);
      zoomToScale(target, direction);
    }
  });
};

const screenshotCanvas = (name: string) => {
  cy.get('[data-testid="canvas-layers"]').scrollIntoView();
  cy.get('[data-testid="canvas-layers"]').screenshot(name);
};

describe('zoom', () => {
  it('scroll wheel zooms in/out; scale clamped at max=80; canvas size reflects scale', () => {
    cy.startApp(diagonalSeed); // 4×4 → initial scale = 80 (max)

    // --- max boundary: zoom in при scale=80 не превышает 80 ---
    triggerZoom(-300);
    triggerZoom(-300);
    triggerZoom(-300);
    cy.get('[data-testid="drawing-info-scale"]')
      .should('have.text', 'scale=80.00', 'max boundary: scale stays at 80');

    // --- drawing-info scale == canvas-layers.width / sprite_width ---
    cy.get('[data-testid="drawing-info-scale"]').then(($s) => {
      const displayedScale = parseFloat($s.text().replace('scale=', ''));
      cy.get('[data-testid="canvas-layers"]').then(($el) => {
        const actualWidth = $el[0].getBoundingClientRect().width;
        expect(actualWidth, 'canvas-layers width = scale × sprite width')
          .to.be.closeTo(displayedScale * 4, 1);
      });
    });

    // --- zoom out до ≈40 ---
    zoomToScale(37.32, 'out');
    cy.get('[data-testid="drawing-info-scale"]').should(($s) => {
      expect(parseFloat($s.text().replace('scale=', '')), 'scale ≤ 37.32 after zoom out')
        .to.be.at.most(37.32);
    });
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas ≈ 160px at scale≈37.32')
        .to.be.closeTo(4 * 37.32, 20);
    });

    screenshotCanvas('zoom-at-scale-37.32');

    // --- zoom in обратно до max 80 ---
    zoomToScale(80, 'in');
    cy.get('[data-testid="drawing-info-scale"]')
      .should('have.text', 'scale=80.00', 'scale restored to max 80');
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas = 320px at scale=80')
        .to.be.closeTo(4 * 80, 1);
    });

    // скриншот: X-паттерн при scale=80 (максимальный масштаб)
    screenshotCanvas('zoom-back-to-scale-80');
  });
});
