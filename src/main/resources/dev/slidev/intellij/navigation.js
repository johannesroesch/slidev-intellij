(function () {
  'use strict';
  if (window.__slidevIdeNavigation) return;
  const report = function (message) { /*REPORT*/ };
  let lastPage = 0;
  let ready = false;
  let warned = false;
  const deadline = Date.now() + 10000;
  const bridge = window.__slidevIdeNavigation = {
    navigate(no) {
      if (!Number.isSafeInteger(no) || no < 1) return;
      const nav = window.__slidev__ && window.__slidev__.nav;
      if (!nav || typeof nav.go !== 'function') {
        report('error:Slidev navigation API unavailable');
        return;
      }
      // Do not reset click steps when the requested slide is already visible.
      if (Number(nav.currentSlideNo ?? nav.currentPage) === no) {
        report('page:' + no);
        return;
      }
      // Slidev's development-mode embedded control API calls nav.go().
      window.postMessage({ target: 'slidev', type: 'navigate', no, clicks: 0 }, window.location.origin);
    },
    dispose() {
      clearInterval(bridge.timer);
      delete window.__slidevIdeNavigation;
    }
  };
  bridge.timer = setInterval(function () {
    const nav = window.__slidev__ && window.__slidev__.nav;
    if (!nav || typeof nav.go !== 'function') {
      if (!warned && Date.now() > deadline) {
        warned = true;
        report('error:Slidev navigation API unavailable. This adapter requires Slidev development mode.');
      }
      return;
    }
    if (!ready) {
      ready = true;
      report('ready');
    }
    const no = Number(nav.currentSlideNo ?? nav.currentPage);
    if (Number.isSafeInteger(no) && no > 0 && no !== lastPage) {
      lastPage = no;
      report('page:' + no);
    }
  }, 100);
})();
