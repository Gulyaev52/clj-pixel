import { defaultDbSeed, getSpriteFromPixels, DBSeed } from '../support/data';
import { r, t } from '../support/colors';

const diagonalSeed: DBSeed = {
  ...defaultDbSeed,
  sprite: getSpriteFromPixels([
    [r, t, t, t, r],
    [t, r, t, r, t],
    [t, t, r, t, t],
    [t, r, t, r, t],
    [r, t, t, t, r],
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
    cy.startApp(diagonalSeed); // 5×5 → initial scale = 80 (max)

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
          .to.be.closeTo(displayedScale * 5, 1);
      });
    });

    // --- zoom out до ≈40 ---
    zoomToScale(40, 'out');
    cy.get('[data-testid="drawing-info-scale"]').should(($s) => {
      expect(parseFloat($s.text().replace('scale=', '')), 'scale ≤ 40 after zoom out')
        .to.be.at.most(40);
    });
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas ≈ 200px at scale≈40')
        .to.be.closeTo(5 * 40, 20);
    });

    // скриншот: X-паттерн при scale≈40 (canvas ≈200px)
    screenshotCanvas('zoom-at-scale-40');

    // --- zoom out до минимума scale=1 ---
    zoomToScale(1, 'out');
    cy.get('[data-testid="drawing-info-scale"]')
      .should('have.text', 'scale=1.00', 'min boundary reached');
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas = 5px at scale=1')
        .to.be.closeTo(5, 1);
    });

    // скриншот: X-паттерн при scale=1 (canvas 5×5px)
    screenshotCanvas('zoom-at-scale-1');

    // --- zoom in обратно до ≈40 ---
    zoomToScale(40, 'in');
    cy.get('[data-testid="drawing-info-scale"]').should(($s) => {
      expect(parseFloat($s.text().replace('scale=', '')), 'scale ≥ 40 after zoom in')
        .to.be.at.least(40);
    });
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas ≈ 200px after zoom in')
        .to.be.closeTo(5 * 40, 20);
    });

    // скриншот: X-паттерн при scale≈40 снова (совпадает с первым)
    screenshotCanvas('zoom-back-to-scale-40');

    // --- zoom in обратно до max 80 ---
    zoomToScale(80, 'in');
    cy.get('[data-testid="drawing-info-scale"]')
      .should('have.text', 'scale=80.00', 'scale restored to max 80');
    cy.get('[data-testid="canvas-layers"]').should(($el) => {
      expect($el[0].getBoundingClientRect().width, 'canvas = 400px at scale=80')
        .to.be.closeTo(5 * 80, 1);
    });

    // скриншот: X-паттерн при scale=80 (максимальный масштаб)
    screenshotCanvas('zoom-back-to-scale-80');
  });
});
