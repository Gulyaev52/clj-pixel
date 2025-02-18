function getOrderedRectangleCoordinates (x0, y0, x1, y1) {
    return {
      x0 : Math.min(x0, x1),
      y0 : Math.min(y0, y1),
      x1 : Math.max(x0, x1),
      y1 : Math.max(y0, y1)
    };
}

export function getCirclePixels(x0, y0, x1, y1, penSize, fn) {
    var coords = getOrderedRectangleCoordinates(x0, y0, x1, y1);
    var pixels = [];
    var xC = Math.round((coords.x0 + coords.x1) / 2);
    var yC = Math.round((coords.y0 + coords.y1) / 2);
    var evenX = (coords.x0 + coords.x1) % 2;
    var evenY = (coords.y0 + coords.y1) % 2;
    var rX = coords.x1 - xC;
    var rY = coords.y1 - yC;

    var x;
    var y;
    var angle;
    var r;

    if (penSize == 1) {
      for (x = coords.x0 ; x <= xC ; x++) {
        angle = Math.acos((x - xC) / rX);
        y = Math.round(rY * Math.sin(angle) + yC);
        pixels.push(fn(x - evenX, y));
        pixels.push(fn(x - evenX, 2 * yC - y - evenY));
        pixels.push(fn(2 * xC - x, y));
        pixels.push(fn(2 * xC - x, 2 * yC - y - evenY));
      }
      for (y = coords.y0 ; y <= yC ; y++) {
        angle = Math.asin((y - yC) / rY);
        x = Math.round(rX * Math.cos(angle) + xC);
        pixels.push(fn(x, y - evenY));
        pixels.push(fn(2 * xC - x - evenX, y - evenY));
        pixels.push(fn(x, 2 * yC - y));
        pixels.push(fn(2 * xC - x - evenX, 2 * yC - y));
      }
      return pixels;
    }

    var iX = rX - penSize;
    var iY = rY - penSize;
    if (iX < 0) {
      iX = 0;
    }
    if (iY < 0) {
      iY = 0;
    }

    for (x = 0 ; x <= rX ; x++) {
      for (y = 0 ; y <= rY ; y++) {
        angle = Math.atan(y / x);
        r = Math.sqrt(x * x + y * y);
        if ((rX <= penSize || rY <= penSize ||
          r > iX * iY / Math.sqrt(iY * iY * Math.pow(Math.cos(angle), 2) + iX * iX * Math.pow(Math.sin(angle), 2)) +
          0.5) &&
          r < rX * rY / Math.sqrt(rY * rY * Math.pow(Math.cos(angle), 2) + rX * rX * Math.pow(Math.sin(angle), 2)) +
          0.5) {
          pixels.push(fn(xC + x, yC + y));
          pixels.push(fn(xC - x - evenX, yC + y));
          pixels.push(fn(xC + x, yC - y - evenY));
          pixels.push(fn(xC - x - evenX, yC - y - evenY));
        }
      }
    }

    return pixels;
  }

export function getScaledCoords(startCol, startRow, col, row) {
  var dX = startCol - col;
  var absX = Math.abs(dX);

  var dY = startRow - row;
  var absY = Math.abs(dY);

  var delta = Math.min(absX, absY);
  row = startRow - ((dY / absY) * delta);
  col = startCol - ((dX / absX) * delta);

  return {
    col : col,
    row : row
  };
}

/**
 * Bresenham line algorithm: Get an array of pixels from
 * start and end coordinates.
 *
 * http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
 * http://stackoverflow.com/questions/4672279/bresenham-algorithm-in-javascript
 */
export function getLinePixels(x0, x1, y0, y1) {
  var pixels = [];

  x1 = normalize(x1, 0);
  y1 = normalize(y1, 0);

  var dx = Math.abs(x1 - x0);
  var dy = Math.abs(y1 - y0);

  var sx = (x0 < x1) ? 1 : -1;
  var sy = (y0 < y1) ? 1 : -1;

  var err = dx - dy;
  while (true) {
    // Do what you need to for this
    pixels.push({'col': x0, 'row': y0});

    if ((x0 == x1) && (y0 == y1)) {
      break;
    }

    var e2 = 2 * err;
    if (e2 > -dy) {
      err -= dy;
      x0  += sx;
    }
    if (e2 < dx) {
      err += dx;
      y0  += sy;
    }
  }

  return pixels;
}

/**
 * Create a uniform line using the same number of pixel at each step, between the provided
 * origin and target coordinates.
 */
export function getUniformLinePixels(x0, x1, y0, y1) {
  var pixels = [];

  x1 = normalize(x1, 0);
  y1 = normalize(y1, 0);

  var dx = Math.abs(x1 - x0) + 1;
  var dy = Math.abs(y1 - y0) + 1;

  var sx = (x0 < x1) ? 1 : -1;
  var sy = (y0 < y1) ? 1 : -1;

  var ratio = Math.max(dx, dy) / Math.min(dx, dy);
  // in pixel art, lines should use uniform number of pixels for each step
  var pixelStep = Math.round(ratio) || 0;

  if (pixelStep > Math.min(dx, dy)) {
    pixelStep = Infinity;
  }

  var maxDistance = distance(x0, x1, y0, y1);

  var x = x0;
  var y = y0;
  var i = 0;
  while (true) {
    i++;

    pixels.push({'col': x, 'row': y});
    if (distance(x0, x, y0, y) >= maxDistance) {
      break;
    }

    var isAtStep = i % pixelStep === 0;
    if (dx >= dy || isAtStep) {
      x += sx;
    }
    if (dy >= dx || isAtStep) {
      y += sy;
    }
  }

  return pixels;
}

function normalize(value, def) {
  if (typeof value === 'undefined' || value === null) {
    return def;
  } else {
    return value;
  }
}

function distance(x0, x1, y0, y1) {
  var dx = Math.abs(x1 - x0);
  var dy = Math.abs(y1 - y0);
  return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
}
