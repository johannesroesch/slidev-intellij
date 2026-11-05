(function () {
  'use strict';
  if (window.__slidevIdePresentation) return;
  const report = function (message) { /*REPORT*/ };
  const fullscreenChanged = function () {
    report(document.fullscreenElement ? 'fullscreen:1' : 'fullscreen:0');
  };
  const keydown = function (event) {
    if (event.key === 'Escape') report('escape');
  };
  document.addEventListener('fullscreenchange', fullscreenChanged);
  document.addEventListener('keydown', keydown, true);
  window.__slidevIdePresentation = {
    dispose() {
      document.removeEventListener('fullscreenchange', fullscreenChanged);
      document.removeEventListener('keydown', keydown, true);
      delete window.__slidevIdePresentation;
    }
  };
})();
