const createGif = (canvases, onFinish) => {
  const gif = new window.GIF({ workers: 2, quality: 1, transparent: 'rgba(0,0,0,0)', background: "#000" // todo: pass as arg
 });
  canvases.forEach((c) => gif.addFrame(c));
  gif.on('finished', function(blob) {
    onFinish(blob);
  });
  gif.render();
};

export default createGif;
