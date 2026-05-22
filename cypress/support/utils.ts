export const rgbaToInt = function colorToInt (r: number, g: number, b: number, a: number = 1) {
  a = Math.round(a * 255);
  let intValue = (a << 24 >>> 0) + (b << 16) + (g << 8) + r;
  if (a === 0) {
    // assign all 'transparent' colors to 0, theoretically mapped to rgba(0,0,0,0) only
    intValue = 0;
  }
  return intValue;
};

export const intToColor = function(intValue) {
    if (typeof intValue === 'string') {
      return intValue;
    }

    let r = intValue & 0xff;
    let g = intValue >> 8 & 0xff;
    let b = intValue >> 16 & 0xff;
    let a = (intValue >> 24 >>> 0 & 0xff) / 255;
    let color = 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';

    return color;
};

export const rgba = (r: number, g: number, b: number, a: number = 255) => `rgba(${r},${g},${b},${a})`;
