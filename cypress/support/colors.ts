import { rgba } from './utils';

export const colors = {
  g: rgba(0, 128, 0),   // green
  b: rgba(0, 0, 0),     // black
  r: rgba(255, 0, 0),   // red
  y: rgba(0, 0, 255),   // blue (b is taken by black)
  t: rgba(0, 0, 0, 0),  // transparent
} as const;

export const { b, g, r, t, y } = colors;
