export const getPoints = (p1, p2, createPoint) => {
    const res = [];
    for (let x = p1.x; x < p2.x + 1; x++) {
        for (let y = p1.y; y < p2.y + 1; y++) {
            if (!((x > p1.x && x < p2.x) && (y > p1.y && y < p2.y))) {
                res.push(createPoint(x, y));
            }
        }
    }
    return res;
};

export const getPoints2 = (p1, p2, createPoint) => {
    const res = [];
    for (let x = p1.x; x < p2.x + 1; x++) {
        for (let y = p1.y; y < p2.y + 1; y++) {
            res.push(createPoint(x, y));
        }
    }
    return res;
};

export const fors = ({ i1, i1Stop, i2, i2Stop, when }, createV) => {
    const res = [];
    for (let x = i1; x < i1Stop; x++) {
        for (let y = i2; y < i2Stop; y++) {
            if (when ? when(x, y) : true) {
                res.push(createV(x, y));
            }
        }
    }
    return res;
};
