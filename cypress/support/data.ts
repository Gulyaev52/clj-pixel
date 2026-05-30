import { g, r, t } from "./colors";
import { rgbaToInt } from "./utils";

export interface DBSeed {
  "primary-color": number;
  "secondary-color": number;
  palettes: Array<{ name: string; current: boolean; colors: number[] }>;
  sprite: {
    title: string;
    size: { width: number; height: number };
    frames: Array<{ duration: number }>;
    cels: Array<Array<{ size: { width: number; height: number }; 'data-url': string }>>;
    layers: Array<{ 'visible?': boolean; 'automatic-linking?': boolean; name: string }>;
  };
}

export const getSpriteFromPixels = (pixels: string[][]): DBSeed['sprite'] => {
  return {
    title: "ProjectTitle",
    size: { width: pixels[0].length, height: pixels.length },
    frames: [{ duration: 100 }],
    cels: [[{ size: { width: pixels[0].length, height: pixels.length }, 'data-url': pixelsToDataURL(pixels) }]],
    layers: [{ 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' }]
  };
}

// cels[frame_idx][layer_idx] = 2D pixel grid (pixels[y][x] = rgba string)
export const getSpriteFromCels = (
  cels: string[][][][],
  frames: Array<{ duration: number }>,
  layers: Array<{ 'visible?': boolean; 'automatic-linking?': boolean; name: string }>
): DBSeed['sprite'] => {
  const height = cels[0][0].length;
  const width = cels[0][0][0].length;
  return {
    title: "ProjectTitle",
    size: { width, height },
    frames,
    layers,
    cels: cels.map(frameCels =>
      frameCels.map(pixels => ({
        size: { width, height },
        'data-url': pixelsToDataURL(pixels)
      }))
    )
  };
};

export const getPixels = (width: number, height: number, color?: string) => {
  const pixels = Array(height).fill(null).map(() => Array(width).fill([0, 0, 0, 0]));
  if (color) {
    pixels.forEach(row => row.fill(color));
  }
  return pixels;
};

function pixelsToDataURL(pixels: string[][]): string {
  const height = pixels.length;
  const width = pixels[0].length;

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;

  const ctx = canvas.getContext('2d');
  const imageData = ctx.createImageData(width, height);

  let offset = 0;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const rgba = pixels[y][x];

      const match = rgba.match(
        /rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)[,\s]+(\d+)\s*\)/
      );

      if (!match) {
        throw new Error(`Invalid rgba color: ${rgba}`);
      }

      const [, r, g, b, a] = match;

      imageData.data[offset++] = Number(r);
      imageData.data[offset++] = Number(g);
      imageData.data[offset++] = Number(b);
      imageData.data[offset++] = Number(a);
    }
  }

  ctx.putImageData(imageData, 0, 0);

  return canvas.toDataURL();
}

export const _4x4EmptySeed: DBSeed = {
  'primary-color': rgbaToInt(0, 0, 0),
  'secondary-color': rgbaToInt(255, 0, 0),
  palettes: [{ name: 'default', current: true, colors: [rgbaToInt(0, 0, 0), rgbaToInt(255, 0, 0), rgbaToInt(0, 0, 255), rgbaToInt(0, 128, 0)] }],
  sprite: getSpriteFromPixels(getPixels(4, 4))
};

export const _2x2Seed: DBSeed = {
  ..._4x4EmptySeed,
  sprite: getSpriteFromPixels([[r, t], [t, t]])
};

export const twoFramesTwoLayersSeed: DBSeed = {
  ..._4x4EmptySeed,
  sprite: getSpriteFromCels(
    [
      [[[r, t], [t, t]], [[t, t], [t, r]]],   // frame-0: layer-0 red(0,0) | layer-1 red(1,1)
      [[[t, t], [t, g]], [[g, t], [t, t]]], // frame-1: layer-0 green(1,1) | layer-1 empty
    ],
    [{ duration: 150 }, { duration: 100 }],
    [
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 1' },
      { 'visible?': true, 'automatic-linking?': false, name: 'Layer 2' }
    ]
  )
};
