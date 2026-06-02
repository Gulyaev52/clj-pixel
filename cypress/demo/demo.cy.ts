/**
 * README demo screencast.
 *
 * This is NOT a test — it's a scripted walkthrough recorded to video and then sped up
 * (see scripts/record-demo.sh). Starting from a blank project, it paints the built-in
 * github-octopus sprite (all 4 animation frames) using several tools, then plays the preview.
 *
 * It lives outside cypress/e2e/ on purpose, so CI (specPattern cypress/e2e/**) never runs it.
 *
 * The pixel data comes from demo-out/octopus-plan.json (produced by scripts/gen-octopus-plan.js).
 * To keep the run feasible (~1200 strokes), the bulk drawing is dispatched as native mouse
 * events inside batched cy.then() loops rather than one Cypress command per stroke.
 */

import { _2x2Seed, getPixels, getSpriteFromPixels } from "../support/data";

// A screencast, not a correctness test — don't let a stray app-side error abort the recording.
Cypress.on('uncaught:exception', () => false);

interface ColorGroup { rgb: string; runs: [number, number, number][]; singles: [number, number][]; }
interface Plan {
  size: { w: number; h: number };
  bgRgb: string;
  palette: { rgb: string; hex: string }[];
  frames: { paint: ColorGroup[] }[];
}

const STROKE_DELAY = 1; // ms between strokes — paces the timelapse, lets the canvas repaint,
                         // and keeps rapid native events from being coalesced/dropped
const beat = (ms = 700) => cy.wait(ms);

const selectColor = (rgb: string) =>
  cy.get(`[data-testid="palette-color-rgba(${rgb},1)"]`, { log: false }).first().click({ force: true });

const addColor = (hex: string) => {
  cy.get('[data-testid="btn-add-color"]').click();
  cy.get('[data-testid="color-picker-hex-input"]').clear().type(hex);
  cy.get('[data-testid="add-color-confirm-button"]').click();
};

// Dispatch native mouse strokes on the canvas viewport for the *current* tool/layer.
// Each stroke is mousedown(x0,y0) -> mousemove(x1,y1) -> mouseup(x1,y1); a single pixel has x0==x1.
const drawStrokes = (strokes: { x0: number; y0: number; x1: number; y1: number }[]) =>
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    const vp = $vp[0];
    const cv = Cypress.$('[data-testid="current-layer"]')[0] as HTMLCanvasElement;
    const vpRect = vp.getBoundingClientRect();
    const cvRect = cv.getBoundingClientRect();
    const sx = cvRect.width / cv.width;
    const sy = cvRect.height / cv.height;
    const ox = cvRect.left - vpRect.left;
    const oy = cvRect.top - vpRect.top;
    const cx = (px: number) => vpRect.left + ox + (px + 0.5) * sx;
    const cyy = (py: number) => vpRect.top + oy + (py + 0.5) * sy;
    const fire = (type: string, x: number, y: number) =>
      vp.dispatchEvent(new MouseEvent(type, { clientX: x, clientY: y, button: 0, buttons: 1, bubbles: true, cancelable: true, view: window }));
    return new Cypress.Promise<void>((resolve) => {
      let i = 0;
      const step = () => {
        if (i >= strokes.length) { resolve(); return; }
        const s = strokes[i++];
        // Hover the start first: this resets the tool's prev-pos so the pen doesn't
        // interpolate a connecting line from the previous stroke's endpoint.
        fire('mousemove', cx(s.x0), cyy(s.y0));
        fire('mousedown', cx(s.x0), cyy(s.y0));
        fire('mousemove', cx(s.x1), cyy(s.y1));
        fire('mouseup', cx(s.x1), cyy(s.y1));
        setTimeout(step, STROKE_DELAY);
      };
      step();
    });
  });

const paintGroup = (g: ColorGroup) => {
  selectColor(g.rgb);
  if (g.runs.length) {
    cy.selectTool('line'); // straight runs of >= 2 px
    drawStrokes(g.runs.map(([y, x0, x1]) => ({ x0, y0: y, x1, y1: y })));
  }
  if (g.singles.length) {
    cy.selectTool('pen'); // single pixels
    drawStrokes(g.singles.map(([x, y]) => ({ x0: x, y0: y, x1: x, y1: y })));
  }
};

describe('Pixel Art — demo', () => {
  it('paints the github-octopus sprite on a blank project, then previews it', () => {
    cy.readFile('demo-out/octopus-plan.json').then((plan: Plan) => {
      // --- Blank project (50x50) ---
      cy.stubConfirm(true);
      cy.startApp({
        ..._2x2Seed,
        sprite: getSpriteFromPixels(getPixels(50, 50))
      });
      cy.get('[data-testid="btn-new-project"]').click();
      cy.get('[data-testid="btn-create-project"]').click();
      cy.get('[data-testid="canvas-viewport"]').should('be.visible');
      cy.get('[data-testid="current-layer"]').should('be.visible');
      beat(300);

      // --- Build the palette: add background + every octopus color via the picker ---
      plan.palette.forEach((c) => addColor(c.hex));
      beat(250);

      // --- Background: a separate layer below the octopus, filled and auto-linked so every
      //     new frame inherits it. add-layer inserts below the current (top) layer. ---
      cy.get('[data-testid="btn-add-layer"]').click();         // -> layer-1 = Background (below)
      cy.get('[data-testid="btn-toggle-layer-automatic-linking-1"]').click();
      selectColor(plan.bgRgb);
      cy.selectTool('bucket');
      cy.mouseDownThenUpOnCanvas({ x: 25, y: 25 });            // fill the Background layer

      // --- Frame 0: paint the octopus on the top layer ---
      cy.get('[data-testid="layer-0"]').click();               // Octopus layer
      plan.frames[0].paint.forEach(paintGroup);

      // --- Frames 1..3: new empty frame (background inherited), paint that frame's octopus ---
      for (let f = 1; f < plan.frames.length; f++) {
        cy.get('[data-testid="btn-add-empty-frame"]').click();
        cy.get('[data-testid="layer-0"]').click();
        plan.frames[f].paint.forEach(paintGroup);
      }

      // --- Scrub the frames, then play the preview (finale) ---
      plan.frames.forEach((_, f) => {
        cy.get(`[data-testid="frame-${f}"]`).click();
        beat(200);
      });


      cy.get("[data-testid='btn-open-export-modal']").click(); // enable looping so the timelapse video looks smoother

      cy.get('[data-testid="export-frames"]').click();
      beat(150);
      cy.contains('.ant-select-item-option', 'Selected frames').click();
      beat(150);
      cy.get('[data-testid="export-frames"]').click();
      beat(150);
      cy.contains('.ant-select-item-option', 'All frames').click();
      beat(150);

      cy.get('[data-testid="export-layers"]').click();
      beat(150);
      cy.contains('.ant-select-item-option', 'Layer 1').click();
      beat(150);
      cy.get('[data-testid="export-layers"]').click();
      cy.contains('.ant-select-item-option', 'Visible layers').click();

      cy.get('[data-testid="export-split-layers"]').click();
      cy.get('[data-testid="btn-export-ok"]').click();

      cy.contains("Cancel").click(); // close the export modal

      cy.get('[data-testid="btn-open-resize-modal"]').click();
      beat(300);
      cy.get('[data-testid="resize-width"]').clear().type('25').blur();
      beat(300);
      
      cy.get('[data-testid="resize-height"]').clear().type('25').blur();
      beat(300);
      
      cy.get('[data-testid="checkbox-resize-content"]').click();
      beat(300);
      cy.get('[data-testid="checkbox-resize-content"]').click();

      cy.get('[data-testid="btn-resize"]').click();
      beat(300);

      cy.get('[data-testid="btn-show-preview"]').click();
      cy.get('[data-testid="sprite-preview"]').should('be.visible');
      beat(2000);
    });
  });
});
