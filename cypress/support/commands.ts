import { DBSeed } from "./data";
import { rgba, rgbaToInt } from "./utils";

// pixels[x][y] = [r, g, b, a]
type Rgba = [number, number, number, number];
export type CanvasPixels = Array<{ pos: [number, number], color: string }>;

function readDrawedCanvasPixels(canvas: HTMLCanvasElement) {
  const { width, height } = canvas;
  const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
  return Array.from({ length: width }, (_, x) =>
    Array.from({ length: height }, (_, y) => {
      const i = (y * width + x) * 4;
      const colors = [data[i], data[i + 1], data[i + 2], data[i + 3]] as const;
      if (colors.every(x => x === 0)) {
        return undefined;
      }
      return {pos: [x, y], color: rgba(...colors)} satisfies { pos: [number, number], color: string };
    }).filter(Boolean)
  ).flat();
}

function readCanvasPixels(canvas: HTMLCanvasElement) {
  const { width, height } = canvas;
  const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
  return Array.from({ length: width }, (_, x) =>
    Array.from({ length: height }, (_, y) => {
      const i = (y * width + x) * 4;
      const colors = [data[i], data[i + 1], data[i + 2], data[i + 3]] as const;
      return {pos: [x, y], color: rgba(...colors)} satisfies { pos: [number, number], color: string };
    }).filter(Boolean)
  ).flat();
}


function readTransparentCanvasPixels(canvas: HTMLCanvasElement) {
  const { width, height } = canvas;
  const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
  return Array.from({ length: width }, (_, x) =>
    Array.from({ length: height }, (_, y) => {
      const i = (y * width + x) * 4;
      const colors = [data[i], data[i + 1], data[i + 2], data[i + 3]] as const;
      if (!colors.every(x => x === 0)) {
        return undefined;
      }
      return {pos: [x, y], color: rgba(0, 0, 0, 0)} satisfies { pos: [number, number], color: string };
    }).filter(Boolean)
  ).flat();
}

declare global {
  namespace Cypress {
    interface Chainable {
      seedDatabase(dbSeed: DBSeed): Chainable<void>;
      selectTool(toolName: string): Chainable<void>;
      selectColor(idx: number, colorType?: 'primary' | 'secondary'): Chainable<void>;
      drawOnCanvas(x: number, y: number): Chainable<void>;
      drawAtCanvasPixel(px: number, py: number, options?: { rightClick?: boolean; toX?: number; toY?: number }): Chainable<void>;
      startDrawAtCanvasPixel(px: number, py: number, options?: { rightClick?: boolean }): Chainable<void>;
      moveAtCanvasPixel(px: number, py: number, options?: { rightClick?: boolean }): Chainable<void>;
      finishDrawAtCanvasPixel(px: number, py: number, options?: { rightClick?: boolean }): Chainable<void>;
      assertPreviewCanvasPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      assertVisibleCanvasPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      // Reads all canvas pixels into pixels[x][y]=[r,g,b,a] and runs check inside
      // should() so Cypress retries until all assertions inside pass.
      getDrawedCanvasPixels(check: (pixels: CanvasPixels) => void): Chainable<void>;
      assertDrawedCanvasPixels(pixels: CanvasPixels, assertMessage?: string): Chainable<void>;
      assertCanvasPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      assertTransparentCanvasPixels(pixels: CanvasPixels, assertMessage?: string): Chainable<void>;
      assertHighlightedPixels(grid: boolean[][], assertMessage?: string): Chainable<void>;
      addFrame(): Chainable<void>;
      addLayer(): Chainable<void>;
      stubPrompt(returnValue: string | null): Chainable<void>;
      stubConfirm(returnValue: boolean): Chainable<void>;
      waitForAppReady(): Chainable<void>;
    }
  }
}

// Visits the page, seeds IndexedDB with a fresh 20×20 sprite, then reloads so the
// app initializes from our data (no "New Project" modal).
Cypress.Commands.add('seedDatabase', (dbSeed: DBSeed) => {
  cy.visit('/index.html', { log: false });
  cy.window({ log: false }).then((win) => {
    return new Promise<void>((resolve, reject) => {
      const openReq = (win as any).indexedDB.open('pixel-database', 1);
      openReq.onupgradeneeded = (e: any) => {
        (e.target.result as IDBDatabase).createObjectStore('pixel', { keyPath: 'id' });
      };
      openReq.onsuccess = (e: any) => {
        const db = e.target.result as IDBDatabase;
        const tx = db.transaction('pixel', 'readwrite');
        tx.objectStore('pixel').put({ id: 'backup', backup: dbSeed });
        tx.oncomplete = () => { db.close(); resolve(); };
        tx.onerror = () => reject((tx as any).error);
      };
      openReq.onerror = () => reject((openReq as any).error);
    });
  });
  cy.reload();
  Cypress.log({ name: 'seedDatabase', message: 'Database seeded with default sprite' });
});

Cypress.Commands.add('waitForAppReady', () => {
  cy.get('[data-testid="canvas-viewport"]', { timeout: 10000, log: false }).should('be.visible');
  cy.get('[data-testid="current-layer"]', { log: false }).should('be.visible');
  Cypress.log({ name: 'waitForAppReady', message: 'App is ready' });
});

Cypress.Commands.add('selectTool', (toolName: string) => {
  cy.get(`[data-testid="tool-${toolName}"]`, { log: false }).click();
  Cypress.log({ name: 'selectTool', message: `Selected tool: ${toolName}` });
});

// Left-click selects the color as primary; right-click selects it as secondary.
Cypress.Commands.add('selectColor', (idx: number, colorType: 'primary' | 'secondary' = 'primary') => {
  const el = cy.get(`[data-testid="palette-color-${idx}"]`);
  if (colorType === 'secondary') {
    el.rightclick();
  } else {
    el.click();
  }
});

Cypress.Commands.add('drawOnCanvas', (x: number, y: number) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false })
    .trigger('mousedown', x, y, { button: 0, force: true, log: false })
    .trigger('mousemove', x + 5, y + 5, { button: 0, force: true, log: false })
    .trigger('mouseup', x + 5, y + 5, { button: 0, force: true, log: false });
  Cypress.log({ name: 'drawOnCanvas', message: `Drew on canvas at (${x}, ${y})` });
});

// Triggers mouse events at a specific canvas buffer pixel (px, py).
// Coordinates are computed dynamically from the canvas element's bounding rect.
Cypress.Commands.add('drawAtCanvasPixel', (px: number, py: number, options: { rightClick?: boolean; toX?: number; toY?: number } = {}) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    cy.get('[data-testid="current-layer"]', { log: false }).then(($canvas) => {
      const vpRect = $vp[0].getBoundingClientRect();
      const cvRect = ($canvas[0] as HTMLCanvasElement).getBoundingClientRect();
      const bufferW = ($canvas[0] as HTMLCanvasElement).width;
      const bufferH = ($canvas[0] as HTMLCanvasElement).height;
      const scaleX = cvRect.width / bufferW;
      const scaleY = cvRect.height / bufferH;
      const x = cvRect.left - vpRect.left + (px + 0.5) * scaleX;
      const y = cvRect.top - vpRect.top + (py + 0.5) * scaleY;
      const toX = options.toX !== undefined
        ? cvRect.left - vpRect.left + (options.toX + 0.5) * scaleX
        : x + 1;
      const toY = options.toY !== undefined
        ? cvRect.top - vpRect.top + (options.toY + 0.5) * scaleY
        : y + 1;
      const btn = options.rightClick ? 2 : 0;
      cy.get('[data-testid="canvas-viewport"]', { log: false })
        .trigger('mousedown', x, y, { button: btn, force: true,})
        .trigger('mousemove', toX, toY, { button: btn, force: true })
        .trigger('mouseup', toX, toY, { button: btn, force: true, });
    });
  });
  if (options.toX !== undefined && options.toY !== undefined) {
    Cypress.log({ name: 'drawAtCanvasPixel', message: `Drew at canvas pixels (${px}, ${py}) to (${options.toX}, ${options.toY}) (${options.rightClick ? "right-click" : "left-click"})` });
  } else {
    Cypress.log({ name: 'drawAtCanvasPixel', message: `Drew at canvas pixel (${px}, ${py}) (${options.rightClick ? "right-click" : "left-click"})` });
  }
});

Cypress.Commands.add('startDrawAtCanvasPixel', (px: number, py: number, options: { rightClick?: boolean } = {}) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    cy.get('[data-testid="current-layer"]', { log: false }).then(($canvas) => {
      const vpRect = $vp[0].getBoundingClientRect();
      const cvRect = ($canvas[0] as HTMLCanvasElement).getBoundingClientRect();
      const bufferW = ($canvas[0] as HTMLCanvasElement).width;
      const bufferH = ($canvas[0] as HTMLCanvasElement).height;
      const scaleX = cvRect.width / bufferW;
      const scaleY = cvRect.height / bufferH;
      const x = cvRect.left - vpRect.left + (px + 0.5) * scaleX;
      const y = cvRect.top - vpRect.top + (py + 0.5) * scaleY;
      const btn = options.rightClick ? 2 : 0;
      cy.get('[data-testid="canvas-viewport"]', { log: false })
        .trigger('mousedown', x, y, { button: btn, force: true, log: false });
    });
  });
  Cypress.log({ name: 'startDrawAtCanvasPixel', message: `mousedown at (${px}, ${py})` });
});

Cypress.Commands.add('finishDrawAtCanvasPixel', (px: number, py: number, options: { rightClick?: boolean } = {}) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    cy.get('[data-testid="current-layer"]', { log: false }).then(($canvas) => {
      const vpRect = $vp[0].getBoundingClientRect();
      const cvRect = ($canvas[0] as HTMLCanvasElement).getBoundingClientRect();
      const bufferW = ($canvas[0] as HTMLCanvasElement).width;
      const bufferH = ($canvas[0] as HTMLCanvasElement).height;
      const scaleX = cvRect.width / bufferW;
      const scaleY = cvRect.height / bufferH;
      const x = cvRect.left - vpRect.left + (px + 0.5) * scaleX;
      const y = cvRect.top - vpRect.top + (py + 0.5) * scaleY;
      const btn = options.rightClick ? 2 : 0;
      cy.get('[data-testid="canvas-viewport"]', { log: false })
        .trigger('mousemove', x, y, { button: btn, force: true, log: false })
        .wait(1) // почему то без ожидания mouseup срабатывает раньше чем mousemove
        .trigger('mouseup', x, y, { button: btn, force: true, log: false });
    });
  });
  Cypress.log({ name: 'finishDrawAtCanvasPixel', message: `mousemove + mouseup at (${px}, ${py})` });
});

Cypress.Commands.add('moveAtCanvasPixel', (px: number, py: number, options: { rightClick?: boolean } = {}) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    cy.get('[data-testid="current-layer"]', { log: false }).then(($canvas) => {
      const vpRect = $vp[0].getBoundingClientRect();
      const cvRect = ($canvas[0] as HTMLCanvasElement).getBoundingClientRect();
      const bufferW = ($canvas[0] as HTMLCanvasElement).width;
      const bufferH = ($canvas[0] as HTMLCanvasElement).height;
      const scaleX = cvRect.width / bufferW;
      const scaleY = cvRect.height / bufferH;
      const x = cvRect.left - vpRect.left + (px + 0.5) * scaleX;
      const y = cvRect.top - vpRect.top + (py + 0.5) * scaleY;
      const btn = options.rightClick ? 2 : 0;
      cy.get('[data-testid="canvas-viewport"]', { log: false })
        .trigger('mousemove', x, y, { button: btn, force: true, log: false });
    });
  });
  Cypress.log({ name: 'moveAtCanvasPixel', message: `mousemove at (${px}, ${py})` });
});

Cypress.Commands.add('assertPreviewCanvasPixels', (expectedPixels: string[][], assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const actualPixels = readCanvasPixels($canvas[0] as HTMLCanvasElement);
    const actualObj = Object.fromEntries(actualPixels.map(p => [p.pos.join(','), p.color]));
    const expectedObj = Object.fromEntries(expectedPixels.flatMap((row, y) =>
      row.map((color, x) => [`${x},${y}`, color])
    ));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({
    name: 'assertPreviewCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

Cypress.Commands.add('assertVisibleCanvasPixels', (expectedPixels: string[][], assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const actualPixels = readCanvasPixels($canvas[0] as HTMLCanvasElement);
    const actualObj = Object.fromEntries(actualPixels.map(p => [p.pos.join(','), p.color]));
    const expectedObj = Object.fromEntries(expectedPixels.flatMap((row, y) =>
      row.map((color, x) => [`${x},${y}`, color])
    ));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({
    name: 'assertVisibleCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

// Reads all canvas pixels inside a should() callback so Cypress retries until
// all assertions inside check() pass. pixels[x][y] = [r, g, b, a].
Cypress.Commands.add('getDrawedCanvasPixels', (check: (pixels: CanvasPixels) => void) => {
  cy.get('[data-testid="current-layer"]').should(($canvas) => {
    check(readDrawedCanvasPixels($canvas[0] as HTMLCanvasElement));
  });
});

Cypress.Commands.add('assertDrawedCanvasPixels', (expectedPixels: CanvasPixels, assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const actualPixels = readDrawedCanvasPixels($canvas[0] as HTMLCanvasElement);
    const actualObj = Object.fromEntries(actualPixels.map(p => [p.pos.join(','), p.color]));
    const expectedObj = Object.fromEntries(expectedPixels.map(p => [p.pos.join(','), p.color]));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({
    name: 'assertDrawedCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

Cypress.Commands.add('assertCanvasPixels', (expectedPixels: string[][], assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const actualPixels = readCanvasPixels($canvas[0] as HTMLCanvasElement);
    const actualObj = Object.fromEntries(actualPixels.map(p => [p.pos.join(','), p.color]));
    const expectedObj = Object.fromEntries(expectedPixels.flatMap((row, y) =>
      row.map((color, x) => [`${x},${y}`, color])
    ));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({
    name: 'assertCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

Cypress.Commands.add('assertHighlightedPixels', (expectedGrid: boolean[][], assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const canvas = $canvas[0] as HTMLCanvasElement;
    const visualEffects = canvas.ownerDocument.getElementById('visual-effects') as HTMLCanvasElement;
    const { width, height } = canvas;
    const data = visualEffects.getContext('2d')!.getImageData(0, 0, width, height).data;
    const actualObj: Record<string, boolean> = {};
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const i = (y * width + x) * 4;
        actualObj[`${x},${y}`] = data[i + 3] > 0;
      }
    }
    const expectedObj = Object.fromEntries(expectedGrid.flatMap((row, y) =>
      row.map((highlighted, x) => [`${x},${y}`, highlighted])
    ));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({ name: 'assertHighlightedPixels', message: assertMessage ?? '' });
});

Cypress.Commands.add('assertTransparentCanvasPixels', (expectedPixels: CanvasPixels, assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const actualPixels = readTransparentCanvasPixels($canvas[0] as HTMLCanvasElement);
    const actualObj = Object.fromEntries(actualPixels.map(p => [p.pos.join(','), p.color]));
    const expectedObj = Object.fromEntries(expectedPixels.map(p => [p.pos.join(','), p.color]));
    expect(actualObj, assertMessage).to.deep.equal(expectedObj);
  });
  Cypress.log({
    name: 'assertTransparentCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

Cypress.Commands.add('addFrame', () => {
  cy.get('[title="add empty frame"]').click();
});

Cypress.Commands.add('addLayer', () => {
  cy.get('[title="add layer"]').click();
});

Cypress.Commands.add('stubPrompt', (returnValue: string | null) => {
  cy.window().then(win => {
    cy.stub(win, 'prompt').returns(returnValue);
  });
});

Cypress.Commands.add('stubConfirm', (returnValue: boolean) => {
  cy.window().then(win => {
    cy.stub(win, 'confirm').returns(returnValue);
  });
});

Cypress.on('window:before:load', function (window) {
  const original = window.EventTarget.prototype.addEventListener

  window.EventTarget.prototype.addEventListener = function () {
    if (arguments && arguments[0] === 'beforeunload') {
      return
    }
    return original.apply(this, arguments)
  }

  Object.defineProperty(window, 'onbeforeunload', {
    get: function () { },
    set: function () { }
  })
})
