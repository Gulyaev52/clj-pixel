export const test = function (x0, y0, x1, y1) {
    var rectangle = getOrderedRectangleCoordinates(x0, y0, x1, y1);
    var pixels = [];

    for (var x = rectangle.x0 ; x <= rectangle.x1 ; x++) {
      for (var y = rectangle.y0 ; y <= rectangle.y1 ; y++) {
        pixels.push([x, y]);
      }
    }

    return pixels;
  };

const getOrderedRectangleCoordinates = function (x0, y0, x1, y1) {
    return {
      x0 : Math.min(x0, x1),
      y0 : Math.min(y0, y1),
      x1 : Math.max(x0, x1),
      y1 : Math.max(y0, y1)
    };
  }
