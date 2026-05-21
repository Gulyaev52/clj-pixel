// pixels[x][y] = [r, g, b, a]
type Rgba = [number, number, number, number];
type CanvasPixels = Rgba[][];

function readCanvasPixels(canvas: HTMLCanvasElement): CanvasPixels {
  const { width, height } = canvas;
  const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
  return Array.from({ length: width }, (_, x) =>
    Array.from({ length: height }, (_, y) => {
      const i = (y * width + x) * 4;
      return [data[i], data[i + 1], data[i + 2], data[i + 3]] as Rgba;
    })
  );
}

const DB_SEED = {
  id: 'backup',
  backup: {
    'primary-color': 4278190080,
    'secondary-color': 4278190335,
    palettes: [{ name: 'default', current: true, colors: [4278190080, 4278190335, 4278222848, 4294901760, 4278255615, 4286611584, 4286578816] }],
    sprite: {
      size: { width: 5, height: 5 },
      frames: [{ duration: 100 }],
      cels: [[{ size: { width: 5, height: 5 }, 'data-url': 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAAKElEQVQ4T2NkoDJgpLJ5DKMGUh6io2E4GoZkhMBosiEj0NC0jMAwBABIxgAVO+SUsAAAAABJRU5ErkJggg==' }]],
      layers: [{ 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' }]
    }
  }
};

declare global {
  namespace Cypress {
    interface Chainable {
      seedDatabase(): Chainable<void>;
      selectTool(toolName: string): Chainable<void>;
      drawOnCanvas(x: number, y: number): Chainable<void>;
      drawAtCanvasPixel(px: number, py: number, options?: { rightClick?: boolean; toX?: number; toY?: number }): Chainable<void>;
      // Reads all canvas pixels into pixels[x][y]=[r,g,b,a] and runs check inside
      // should() so Cypress retries until all assertions inside pass.
      getCanvasPixels(check: (pixels: CanvasPixels) => void): Chainable<void>;
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
Cypress.Commands.add('seedDatabase', () => {
  cy.visit('/index.html');
  cy.window().then((win) => {
    return new Promise<void>((resolve, reject) => {
      const openReq = (win as any).indexedDB.open('pixel-database', 1);
      openReq.onupgradeneeded = (e: any) => {
        (e.target.result as IDBDatabase).createObjectStore('pixel', { keyPath: 'id' });
      };
      openReq.onsuccess = (e: any) => {
        const db = e.target.result as IDBDatabase;
        const tx = db.transaction('pixel', 'readwrite');
        tx.objectStore('pixel').put(DB_SEED);
        tx.oncomplete = () => { db.close(); resolve(); };
        tx.onerror = () => reject((tx as any).error);
      };
      openReq.onerror = () => reject((openReq as any).error);
    });
  });
  cy.reload();
});

Cypress.Commands.add('waitForAppReady', () => {
  cy.get('[data-testid="canvas-viewport"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-testid="canvas-current-layer"]').should('be.visible');
});

Cypress.Commands.add('selectTool', (toolName: string) => {
  cy.get(`[data-testid="tool-${toolName}"]`).click();
});

Cypress.Commands.add('drawOnCanvas', (x: number, y: number) => {
  cy.get('[data-testid="canvas-viewport"]')
    .trigger('mousedown', x, y, { button: 0, force: true })
    .trigger('mousemove', x + 5, y + 5, { button: 0, force: true })
    .trigger('mouseup', x + 5, y + 5, { button: 0, force: true });
});

// Triggers mouse events at a specific canvas buffer pixel (px, py).
// Coordinates are computed dynamically from the canvas element's bounding rect.
Cypress.Commands.add('drawAtCanvasPixel', (px: number, py: number, options: { rightClick?: boolean; toX?: number; toY?: number } = {}) => {
  cy.get('[data-testid="canvas-viewport"]').then(($vp) => {
    cy.get('[data-testid="canvas-current-layer"]').then(($canvas) => {
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
      cy.get('[data-testid="canvas-viewport"]')
        .trigger('mousedown', x, y, { button: btn, force: true })
        .trigger('mousemove', toX, toY, { button: btn, force: true })
        .trigger('mouseup', toX, toY, { button: btn, force: true });
    });
  });
});

// Reads all canvas pixels inside a should() callback so Cypress retries until
// all assertions inside check() pass. pixels[x][y] = [r, g, b, a].
Cypress.Commands.add('getCanvasPixels', (check: (pixels: CanvasPixels) => void) => {
  cy.get('[data-testid="canvas-current-layer"]').should(($canvas) => {
    check(readCanvasPixels($canvas[0] as HTMLCanvasElement));
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
