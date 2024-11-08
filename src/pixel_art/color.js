import tinycolor from "tinycolor2";

export const rgbaToInt = function colorToInt (r, g, b, a) {
  if (typeof color === 'number') {
    return color;
  }

  var a = Math.round(a * 255);
  var intValue = (a << 24 >>> 0) + (b << 16) + (g << 8) + r;
  if (a === 0) {
    // assign all 'transparent' colors to 0, theoretically mapped to rgba(0,0,0,0) only
    intValue = 0;
  }
  return intValue;
};

export const colorToInt = function colorToInt (color) {
    if (typeof color === 'number') {
      return color;
    }

    var tc = tinycolor(color);
    var rgb = tc.toRgb();
    var a = Math.round(rgb.a * 255);
    var intValue = (a << 24 >>> 0) + (rgb.b << 16) + (rgb.g << 8) + rgb.r;
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

    var r = intValue & 0xff;
    var g = intValue >> 8 & 0xff;
    var b = intValue >> 16 & 0xff;
    var a = (intValue >> 24 >>> 0 & 0xff) / 255;
    var color = 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';

    return color;
  };