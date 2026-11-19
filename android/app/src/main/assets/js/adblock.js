/*
 * Lumora ad-block script — injected at document start (and re-applied on
 * page finish) into YouTube / YouTube Music.
 *
 *  1. Prunes ad payloads (adPlacements, playerAds, adSlots...) from the
 *     player response before YouTube's player ever sees them.
 *  2. Injects cosmetic CSS that hides promoted cards, masthead ads and
 *     Premium upsell dialogs.
 *  3. A MutationObserver that fast-forwards / skips any ad that slips
 *     through and dismisses anti-adblock enforcement popups.
 *
 * __LUMORA_CSS__ is replaced by the native side with AdBlockManager.cosmeticCss.
 */
(function () {
  'use strict';
  if (window.__lumoraAdBlock) return;
  window.__lumoraAdBlock = true;

  /* ---------------- 1. JSON pruning ---------------- */
  var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams', 'adBreakParams'];

  function prune(obj, depth) {
    if (!obj || typeof obj !== 'object' || (depth || 0) > 3) return obj;
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (Object.prototype.hasOwnProperty.call(obj, AD_KEYS[i])) {
        try { delete obj[AD_KEYS[i]]; } catch (e) {}
      }
    }
    if (obj.playerResponse) prune(obj.playerResponse, (depth || 0) + 1);
    if (Array.isArray(obj)) {
      for (var j = 0; j < obj.length && j < 8; j++) prune(obj[j], (depth || 0) + 1);
    }
    return obj;
  }

  try {
    var origParse = JSON.parse;
    JSON.parse = function () {
      return prune(origParse.apply(this, arguments));
    };
  } catch (e) {}

  try {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.apply(this, arguments).then(function (d) { return prune(d); });
    };
  } catch (e) {}

  try {
    var initial;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return initial; },
      set: function (v) { initial = prune(v); }
    });
  } catch (e) {}

  /* ---------------- 2. Cosmetic CSS ---------------- */
  var CSS = '__LUMORA_CSS__';
  function injectCss() {
    if (document.getElementById('lumora-adblock-css')) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var style = document.createElement('style');
    style.id = 'lumora-adblock-css';
    style.textContent = CSS;
    root.appendChild(style);
  }
  injectCss();

  /* ---------------- 3. Ad skipper + popup killer ---------------- */
  var SKIP_SELECTORS = [
    '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button',
    '.ytp-ad-skip-button-container button', '.ytm-skip-ad-button', 'button.ytp-ad-skip-button-modern',
    '[id^="skip-button"] button', '.videoAdUiSkipButton', 'ytm-skip-ad-button button', '.skip-ad-button'
  ];
  var ENFORCEMENT = 'ytd-enforcement-message-view-model, ytm-enforcement-message-view-model, ' +
    'ytmusic-enforcement-message-view-model, #enforcement-message';

  var restore = null;

  function handleAds() {
    var player = document.querySelector('.html5-video-player');
    var video = document.querySelector('.html5-video-player video') || document.querySelector('video');
    var adShowing = !!(player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));

    for (var i = 0; i < SKIP_SELECTORS.length; i++) {
      var btn = document.querySelector(SKIP_SELECTORS[i]);
      if (btn) { try { btn.click(); } catch (e) {} }
    }

    if (adShowing && video) {
      if (!restore) restore = { muted: video.muted, rate: video.playbackRate };
      try {
        video.muted = true;
        video.playbackRate = 16;
        if (isFinite(video.duration) && video.duration > 0.5) video.currentTime = video.duration - 0.1;
        if (video.paused) { var p = video.play(); if (p && p.catch) p.catch(function () {}); }
      } catch (e) {}
    } else if (!adShowing && restore && video) {
      try {
        video.muted = restore.muted;
        video.playbackRate = restore.rate || 1;
      } catch (e) {}
      restore = null;
    }

    // Overlay / banner ads inside the player.
    var overlays = document.querySelectorAll('.ytp-ad-overlay-close-button, .ytp-ad-overlay-close-container');
    for (var k = 0; k < overlays.length; k++) { try { overlays[k].click(); } catch (e) {} }

    // Anti-adblock enforcement dialog.
    var enforcement = document.querySelector(ENFORCEMENT);
    if (enforcement) {
      var dialog = enforcement.closest('tp-yt-paper-dialog, ytm-popup-container, ytd-popup-container') || enforcement;
      dialog.remove();
      var backdrops = document.querySelectorAll('tp-yt-iron-overlay-backdrop');
      for (var b = 0; b < backdrops.length; b++) backdrops[b].remove();
      document.body && (document.body.style.overflow = '');
      if (video && video.paused) { var pp = video.play(); if (pp && pp.catch) pp.catch(function () {}); }
    }

    // Premium upsell dialogs (YouTube Music & YouTube).
    var upsells = document.querySelectorAll('ytmusic-mealbar-promo-renderer, ytd-mealbar-promo-renderer, ' +
      'ytm-mealbar-promo-renderer, ytmusic-upsell-dialog-renderer');
    for (var u = 0; u < upsells.length; u++) {
      var host = upsells[u].closest('tp-yt-paper-dialog') || upsells[u];
      var dismiss = host.querySelector('#dismiss-button button, .dismiss-button, button[aria-label*="No thanks" i]');
      if (dismiss) { try { dismiss.click(); } catch (e) {} } else { host.remove(); }
    }
  }

  var scheduled = false;
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    (window.requestAnimationFrame || setTimeout)(function () {
      scheduled = false;
      injectCss();
      handleAds();
    });
  }

  function start() {
    injectCss();
    handleAds();
    var target = document.documentElement || document.body;
    if (!target) return;
    new MutationObserver(schedule).observe(target, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['class']
    });
    // Safety net: some ad transitions happen without DOM mutations.
    setInterval(handleAds, 750);
  }

  if (document.documentElement) start();
  else document.addEventListener('DOMContentLoaded', start);
})();
