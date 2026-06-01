import { DBSeed } from "./data";
import { mergeFrameLayers, rgba } from "./utils";

export type CanvasPixels = Array<{ pos: [number, number], color: string }>;

declare global {
  namespace Cypress {
    interface Chainable {
      startApp(dbSeed: DBSeed): Chainable<void>;
      selectTool(toolName: string): Chainable<void>;
      mouseDownThenUpOnCanvas(coords: { x: number; y: number }, options?: { rightClick?: boolean }): Chainable<void>;
      mouseDownThenMoveThenUpOnCanvas(from: { x: number; y: number }, to: { x: number; y: number }, options?: { rightClick?: boolean }): Chainable<void>;
      mouseDownOnCanvas(coords: { x: number; y: number }, options?: { rightClick?: boolean }): Chainable<void>;
      mouseMoveOnCanvas(coords: { x: number; y: number }, options?: { rightClick?: boolean }): Chainable<void>;
      mouseMoveAndUpOnCanvas(coords: { x: number; y: number }, options?: { rightClick?: boolean }): Chainable<void>;
      assertVisibleCanvasPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      assertHighlightedPixels(grid: boolean[][], assertMessage?: string): Chainable<void>;
      stubPrompt(returnValue: string | null): Chainable<void>;
      stubConfirm(returnValue: boolean): Chainable<void>;
      assertCelPreview(frameIdx: number, layerIdx: number, expectedPixels: string[][], assertMessage?: string): Chainable<void>;
      assertResizePreviewPixels(frameIdx: number, pixels: string[][], assertMessage?: string): Chainable<void>;
      assertSpritePreviewPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      assertDownloadedPngPixels(filePath: string, pixels: string[][], assertMessage?: string): Chainable<void>;
      assertTimelineCelsAndVisiblePixels(cels: string[][][][], active: { activeFrameIdx: number; activeLayerIdx: number }, layerNames: string[]): Chainable<void>;
      assertTimelineLabels(frameCount: number, layerNames: string[]): Chainable<void>;
      assertOnionSkinPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
      undo(): Chainable<void>;
      redo(): Chainable<void>;
      drag(sourceSelector: string, targetSelector: string): Chainable<void>;
      assertCelGroupNumber(options: { frameIdx: number; layerIdx: number; groupNumber: number | null }): Chainable<void>;
      assertCanvasPixels(pixels: string[][], assertMessage?: string): Chainable<void>;
    }
  }
}

Cypress.Commands.add('startApp', (dbSeed: DBSeed) => {
  cy.visit('/index.html', {
    onBeforeLoad(window) {
      return new Promise<void>((resolve, reject) => {
        const openReq = window.indexedDB.open('pixel-database', 1);
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
    }
  });
  cy.get('[data-testid="canvas-viewport"]', { timeout: 10000, log: false }).should('be.visible');
  cy.get('[data-testid="current-layer"]', { log: false }).should('be.visible');
  Cypress.log({ name: 'startApp', message: 'App started with seeded database' });
});

Cypress.Commands.add('selectTool', (toolName: string) => {
  cy.get(`[data-testid="tool-${toolName}"]`, { log: false }).click();
  Cypress.log({ name: 'selectTool', message: `Selected tool: ${toolName}` });
});

Cypress.Commands.add('mouseDownThenMoveThenUpOnCanvas', (start, to, options) => {
  triggerMouseEventsOnCanvas([{ type: "mousedown", coords: start }, { type: "mousemove", coords: to }, { type: "mouseup", coords: to }], options ?? {});
  Cypress.log({ name: 'mouseDownThenMoveThenUpOnCanvas', message: `(${start.x}, ${start.y}) → (${to.x}, ${to.y})` });
});

Cypress.Commands.add('mouseDownThenUpOnCanvas', (coords: { x: number; y: number }, options) => {
  triggerMouseEventsOnCanvas([{ type: "mousedown", coords }, { type: "mouseup", coords }], options ?? {});
  Cypress.log({ name: 'mouseDownThenUpOnCanvas', message: `(${coords.x}, ${coords.y})` });
});

Cypress.Commands.add('mouseDownOnCanvas', (coords: { x: number; y: number }, options) => {
  triggerMouseEventsOnCanvas([{type:"mousedown", coords}], options ?? {});
  Cypress.log({ name: 'mouseDownOnCanvas', message: `mousedown at (${coords.x}, ${coords.y})` });
});

Cypress.Commands.add('mouseMoveAndUpOnCanvas', (coords, options) => {
  triggerMouseEventsOnCanvas([{type:"mousemove", coords}, {type:"mouseup", coords}], options ?? {});
  Cypress.log({ name: 'mouseMoveAndUpOnCanvas', message: `mousemove + mouseup at (${coords.x}, ${coords.y})` });
});

Cypress.Commands.add('mouseMoveOnCanvas', (coords, options) => {
  triggerMouseEventsOnCanvas([{type:"mousemove", coords}], options ?? {});
  Cypress.log({ name: 'mouseMoveOnCanvas', message: `mousemove at (${coords.x}, ${coords.y})` });
});

const triggerMouseEventsOnCanvas = (events: Array<{ type: "mousedown" | "mousemove" | "mouseup"; coords: { x: number; y: number; }}>, options: { rightClick?: boolean; } = {}) => {
  cy.get('[data-testid="canvas-viewport"]', { log: false }).then(($vp) => {
    cy.get('[data-testid="current-layer"]', { log: false }).then(($canvas) => {
      const vpRect = $vp[0].getBoundingClientRect();
      const cvRect = ($canvas[0] as HTMLCanvasElement).getBoundingClientRect();
      const scaleX = cvRect.width / ($canvas[0] as HTMLCanvasElement).width;
      const scaleY = cvRect.height / ($canvas[0] as HTMLCanvasElement).height;
      const offsetX = cvRect.left - vpRect.left;
      const offsetY = cvRect.top - vpRect.top;
      const btn = options.rightClick ? 2 : 0;

      events.forEach((event, i) => {
        if (events[i - 1]?.type === "mousemove" && event.type === "mouseup") {
          cy.wait(1); // почему то без ожидания mouseup срабатывает раньше чем mousemove
        }
        const x = offsetX + (event.coords.x + 0.5) * scaleX;
        const y = offsetY + (event.coords.y + 0.5) * scaleY;
        cy.get('[data-testid="canvas-viewport"]', { log: false })
          .trigger(event.type, x, y, { button: btn, force: true });
      });
    });
  });
};

Cypress.Commands.add('assertVisibleCanvasPixels', (expectedPixels: string[][], assertMessage?: string) => {
  cy.get('[data-testid="current-layer"]', { log: false }).should(($canvas) => {
    const canvas = $canvas[0] as HTMLCanvasElement;
    const actualPixels = readCompositePixels(canvas.ownerDocument, canvas.width, canvas.height);
    expect(pixelsToMap(actualPixels), assertMessage).to.deep.equal(pixelsToMap(expectedPixels));
  });
  Cypress.log({
    name: 'assertVisibleCanvasPixels',
    message: assertMessage || '',
    consoleProps: () => ({ expectedPixels })
  });
});

Cypress.Commands.add('assertOnionSkinPixels', (expectedPixels: string[][], assertMessage?: string) => {
  cy.get('#onion-skin', { log: false }).should(($canvas) => {
    const canvas = $canvas[0] as HTMLCanvasElement;
    const height = expectedPixels.length;
    const width = expectedPixels[0].length;
    const data = canvas.getContext('2d')!.getImageData(0, 0, width, height).data;
    const actualPixels = Array.from({ length: height }, (_, y) =>
      Array.from({ length: width }, (_, x) => {
        const i = (y * width + x) * 4;
        return data[i + 3] > 0
          ? rgba(data[i], data[i + 1], data[i + 2], data[i + 3])
          : rgba(0, 0, 0, 0);
      })
    );
    expect(pixelsToMap(actualPixels), assertMessage).to.deep.equal(pixelsToMap(expectedPixels));
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

Cypress.Commands.add('assertCelPreview', (frameIdx: number, layerIdx: number, expectedPixels: string[][], assertMessage?: string) => {
  assertImgPixels(
    cy.get(`[data-testid="cel-${frameIdx}-${layerIdx}"] img`, { log: false }),
    expectedPixels,
    assertMessage
  );
  Cypress.log({ name: 'assertCelPreview', message: assertMessage || `cel-${frameIdx}-${layerIdx}` });
});

Cypress.Commands.add('assertResizePreviewPixels', (frameIdx: number, expectedPixels: string[][], assertMessage?: string) => {
  assertImgPixels(
    cy.get(`[data-testid="preview-frame-${frameIdx}"]`, { log: false }).find('img', { log: false }),
    expectedPixels,
    assertMessage
  );
  Cypress.log({ name: 'assertResizePreviewPixels', message: assertMessage || `frame-${frameIdx}` });
});

Cypress.Commands.add('assertSpritePreviewPixels', (expectedPixels: string[][], assertMessage?: string) => {
  assertImgPixels(
    cy.get('[data-testid="sprite-preview"]', { log: false }).find('img', { log: false }),
    expectedPixels,
    assertMessage
  );
  Cypress.log({ name: 'assertSpritePreviewPixels', message: assertMessage || 'sprite-preview' });
});

Cypress.Commands.add('assertDownloadedPngPixels', (filePath: string, expectedPixels: string[][], assertMessage?: string) => {
  cy.readFile(filePath, 'base64', { timeout: 10000 }).then((base64: string) => {
    cy.window().then((win) => {
      return new Promise<string[][]>((resolve) => {
        const img = new win.Image();
        img.onload = () => {
          const { naturalWidth: width, naturalHeight: height } = img;
          const canvas = win.document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          canvas.getContext('2d')!.drawImage(img, 0, 0);
          const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
          const pixels = Array.from({ length: height }, (_, y) =>
            Array.from({ length: width }, (_, x) => {
              const i = (y * width + x) * 4;
              return rgba(data[i], data[i + 1], data[i + 2], data[i + 3]);
            })
          );
          resolve(pixels);
        };
        img.src = `data:image/png;base64,${base64}`;
      });
    }).then((actualPixels: string[][]) => {
      expect(pixelsToMap(actualPixels), assertMessage).to.deep.equal(pixelsToMap(expectedPixels));
    });
  });
  Cypress.log({ name: 'assertDownloadedPngPixels', message: assertMessage || filePath });
});

Cypress.Commands.add('assertTimelineCelsAndVisiblePixels', (
  cels: string[][][][],
  active: { activeFrameIdx: number; activeLayerIdx: number },
  layerNames: string[]
) => {
  const frameCount = cels.length;
  cy.assertTimelineLabels(frameCount, layerNames);

  cels.forEach((frameCels, frameIdx) => {
    const isActiveFrame = frameIdx === active.activeFrameIdx;
    cy.get(`[data-testid="frame-${frameIdx}"][data-current="${isActiveFrame}"]`)
      .should('exist', `frame-${frameIdx} data-current=${isActiveFrame}`);

    frameCels.forEach((pixels, layerIdx) => {
      const isActiveCel = isActiveFrame && layerIdx === active.activeLayerIdx;
      cy.get(`[data-testid="cel-${frameIdx}-${layerIdx}"]`).should('be.visible');
      cy.assertCelPreview(frameIdx, layerIdx, pixels, `cel-${frameIdx}-${layerIdx} pixels`);
      cy.get(`[data-testid="cel-${frameIdx}-${layerIdx}"][data-selected="${isActiveCel}"]`)
        .should('exist', `cel-${frameIdx}-${layerIdx} data-selected=${isActiveCel}`);
    });
  });

  cels[0].forEach((_, layerIdx) => {
    const isActiveLayer = layerIdx === active.activeLayerIdx;
    cy.get(`[data-testid="layer-${layerIdx}"][data-current="${isActiveLayer}"]`)
      .should('exist', `layer-${layerIdx} data-current=${isActiveLayer}`);
  });

  cy.assertVisibleCanvasPixels(mergeFrameLayers(cels[active.activeFrameIdx]), 'canvas shows current cel');
});

Cypress.Commands.add('assertTimelineLabels', (frameCount: number, layerNames: string[]) => {
  cy.get('[data-testid^="frame-"]').should('have.length', frameCount);
  for (let i = 0; i < frameCount; i++) {
    cy.get('[data-testid^="frame-"]').eq(i)
      .should('contain.text', String(i + 1), `frame at position ${i} shows number ${i + 1}`);
  }
  cy.get('[data-testid^="layer-"]').should('have.length', layerNames.length);
  layerNames.forEach((name, i) => {
    cy.get('[data-testid^="layer-"]').eq(i)
      .should('contain.text', name, `layer at position ${i} shows "${name}"`);
  });
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

Cypress.Commands.add('undo', () => { cy.realPress(['Control', 'z']); });
Cypress.Commands.add('redo', () => { cy.realPress(['Control', 'y']); });

Cypress.Commands.add('assertCanvasPixels', (pixels: string[][], assertMessage?: string) => {
  cy.assertVisibleCanvasPixels(pixels, assertMessage);
});

Cypress.Commands.add('assertCelGroupNumber', ({ frameIdx, layerIdx, groupNumber }: { frameIdx: number; layerIdx: number; groupNumber: number | null }) => {
  const sel = `[data-testid="cel-group-${frameIdx}-${layerIdx}"]`;
  if (groupNumber === null) {
    cy.get(sel).should('be.empty');
  } else {
    cy.get(sel).should('contain.text', String(groupNumber));
  }
});

Cypress.Commands.add('drag', (sourceSelector: string, targetSelector: string) => {
  cy.window().then(win => {
    const dataTransfer = new win.DataTransfer();
    cy.get(sourceSelector).trigger('dragstart', { dataTransfer });
    cy.get(targetSelector).trigger('dragenter', { dataTransfer });
    cy.get(targetSelector).trigger('dragover', { dataTransfer });
    cy.get(targetSelector).trigger('drop', { dataTransfer });
    cy.get(sourceSelector).trigger('dragend', { dataTransfer });
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

// helpers

function pixelsToMap(pixels: string[][]): Record<string, string> {
  return Object.fromEntries(pixels.flatMap((row, y) => row.map((color, x) => [`${x},${y}`, color])));
}

function assertImgPixels(imgChain: Cypress.Chainable<JQuery>, expectedPixels: string[][], assertMessage?: string) {
  imgChain
    .should(($img) => {
      expect(($img[0] as HTMLImageElement).complete).to.be.true;
      expect(($img[0] as HTMLImageElement).naturalWidth).to.be.greaterThan(0);
    })
    .should(($img) => {
      expect(pixelsToMap(readImgPixels($img[0] as HTMLImageElement)), assertMessage)
        .to.deep.equal(pixelsToMap(expectedPixels));
    });
}

function readCompositePixels(doc: Document, width: number, height: number): string[][] {
  const below   = doc.getElementById('layers-below')  as HTMLCanvasElement;
  const current = doc.getElementById('current-layer') as HTMLCanvasElement;
  const above   = doc.getElementById('layers-above')  as HTMLCanvasElement;

  const belowData   = below.getContext('2d')!.getImageData(0, 0, width, height).data;
  const currentData = current.getContext('2d')!.getImageData(0, 0, width, height).data;
  const aboveData   = above.getContext('2d')!.getImageData(0, 0, width, height).data;

  return Array.from({ length: height }, (_, y) =>
    Array.from({ length: width }, (_, x) => {
      const i = (y * width + x) * 4;
      for (const data of [aboveData, currentData, belowData]) {
        if (data[i + 3] > 0) {
          return rgba(data[i], data[i + 1], data[i + 2], data[i + 3]);
        }
      }
      return rgba(0, 0, 0, 0);
    })
  );
}

function readImgPixels(img: HTMLImageElement): string[][] {
  const { naturalWidth: width, naturalHeight: height } = img;
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  canvas.getContext('2d')!.drawImage(img, 0, 0);
  const { data } = canvas.getContext('2d')!.getImageData(0, 0, width, height);
  return Array.from({ length: height }, (_, y) =>
    Array.from({ length: width }, (_, x) => {
      const i = (y * width + x) * 4;
      return rgba(data[i], data[i + 1], data[i + 2], data[i + 3]);
    })
  );
}
