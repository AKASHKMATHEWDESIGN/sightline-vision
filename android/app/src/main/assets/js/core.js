/*
 * Lumora core script — injected at document start into YouTube / YouTube Music.
 *
 *  - Background playback: spoofs the Page Visibility API so the site never
 *    learns it has been backgrounded (screen off / app minimised).
 *  - Media bridge: reports playback state + metadata to the native
 *    MediaSession and executes transport commands sent from Android.
 *  - Web Share polyfill, pull-to-refresh scroll hints, SponsorBlock skipping.
 *
 * window.__lumoraConfig is prepended by the native side.
 */
(function () {
  'use strict';
  if (window.__lumora) return;

  var cfg = window.__lumoraConfig || { bgPlay: true, adBlock: true, sponsorBlock: true };

  function post(type, payload) {
    try {
      var msg = payload || {};
      msg.type = type;
      if (window.LumoraBridge && window.LumoraBridge.postMessage) {
        window.LumoraBridge.postMessage(JSON.stringify(msg));
      }
    } catch (e) { /* bridge unavailable */ }
  }

  /* ------------------------------------------------------------------ */
  /* 1. Background playback: Page Visibility spoofing                    */
  /* ------------------------------------------------------------------ */
  if (cfg.bgPlay) {
    var define = function (obj, prop, value) {
      try {
        Object.defineProperty(obj, prop, { configurable: true, get: function () { return value; } });
      } catch (e) {}
    };
    define(Document.prototype, 'hidden', false);
    define(Document.prototype, 'webkitHidden', false);
    define(Document.prototype, 'visibilityState', 'visible');
    define(Document.prototype, 'webkitVisibilityState', 'visible');
    try { Document.prototype.hasFocus = function () { return true; }; } catch (e) {}

    var swallow = function (e) {
      // Only swallow page-level events; element blur/focus must keep working.
      if (e.target === window || e.target === document) {
        e.stopImmediatePropagation();
      }
    };
    ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide', 'freeze'].forEach(function (t) {
      window.addEventListener(t, swallow, true);
      document.addEventListener(t, swallow, true);
    });

    // YouTube's "Video paused. Continue watching?" idle prompt.
    setInterval(function () {
      try {
        var w = window.yt && window.yt.util && window.yt.util.activity;
        if (w && w.setTimestamp) w.setTimestamp();
        window._lact = Date.now();
      } catch (e) {}
    }, 60000);
  }

  /* ------------------------------------------------------------------ */
  /* 2. MediaSession capture: reuse the site's own next/prev handlers    */
  /* ------------------------------------------------------------------ */
  var actionHandlers = {};
  try {
    if (navigator.mediaSession && navigator.mediaSession.setActionHandler) {
      var origSet = navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
      navigator.mediaSession.setActionHandler = function (action, handler) {
        actionHandlers[action] = handler;
        try { return origSet(action, handler); } catch (e) {}
      };
    }
  } catch (e) {}

  /* ------------------------------------------------------------------ */
  /* 3. Media state reporting                                            */
  /* ------------------------------------------------------------------ */
  function mainVideo() {
    var vids = document.querySelectorAll('video');
    var best = null;
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (!v.src && !v.currentSrc) continue;
      if (!best || (!v.paused && best.paused) || v.duration > (best.duration || 0)) best = v;
    }
    return best;
  }

  function currentVideoId() {
    try {
      var u = new URL(location.href);
      var id = u.searchParams.get('v');
      if (id) return id;
      var m = u.pathname.match(/\/(?:shorts|live|embed)\/([A-Za-z0-9_-]{11})/);
      if (m) return m[1];
    } catch (e) {}
    return null;
  }

  function isAdShowing() {
    var p = document.querySelector('.html5-video-player');
    return !!(p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')));
  }

  function metadata() {
    var md = (navigator.mediaSession && navigator.mediaSession.metadata) || null;
    var title = md && md.title ? md.title : document.title.replace(/ - YouTube( Music)?$/, '');
    var artist = md && md.artist ? md.artist : '';
    var album = md && md.album ? md.album : '';
    var art = '';
    if (md && md.artwork && md.artwork.length) {
      var biggest = md.artwork[md.artwork.length - 1];
      art = biggest.src || '';
    }
    var id = currentVideoId();
    if (!art && id) art = 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
    return { title: title, artist: artist, album: album, artwork: art, videoId: id };
  }

  var lastSent = '';
  function report(force) {
    var v = mainVideo();
    var md = metadata();
    var state = {
      hasMedia: !!v,
      playing: !!(v && !v.paused && !v.ended && v.readyState > 2),
      buffering: !!(v && !v.paused && v.readyState <= 2),
      ended: !!(v && v.ended),
      position: v ? Math.floor((v.currentTime || 0) * 1000) : 0,
      duration: v && isFinite(v.duration) ? Math.floor(v.duration * 1000) : 0,
      width: v ? v.videoWidth : 0,
      height: v ? v.videoHeight : 0,
      ad: isAdShowing(),
      title: md.title, artist: md.artist, album: md.album, artwork: md.artwork, videoId: md.videoId
    };
    var key = JSON.stringify([state.hasMedia, state.playing, state.buffering, state.ended, state.title,
      state.artist, state.artwork, state.duration, state.ad, Math.floor(state.position / 5000)]);
    if (!force && key === lastSent) return;
    lastSent = key;
    post('media', state);
  }

  var hooked = new WeakSet();
  function hookVideos() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (hooked.has(v)) continue;
      hooked.add(v);
      ['play', 'playing', 'pause', 'ended', 'loadedmetadata', 'seeked', 'waiting', 'emptied'].forEach(function (t) {
        v.addEventListener(t, function () { report(true); });
      });
      v.addEventListener('timeupdate', onTimeUpdate);
    }
  }
  setInterval(function () { hookVideos(); report(false); }, 1000);

  /* ------------------------------------------------------------------ */
  /* 4. Commands from Android (notification / lock screen / PiP)         */
  /* ------------------------------------------------------------------ */
  function clickFirst(selectors) {
    for (var i = 0; i < selectors.length; i++) {
      var el = document.querySelector(selectors[i]);
      if (el) { el.click(); return true; }
    }
    return false;
  }

  function command(name, arg) {
    var v = mainVideo();
    try {
      switch (name) {
        case 'play':
          if (v) { var p = v.play(); if (p && p.catch) p.catch(function () {}); }
          break;
        case 'pause':
          if (v) v.pause();
          break;
        case 'seek':
          if (v) v.currentTime = Math.max(0, arg / 1000);
          break;
        case 'next':
          if (actionHandlers.nexttrack) { actionHandlers.nexttrack({ action: 'nexttrack' }); break; }
          clickFirst(['.ytp-next-button', 'ytmusic-player-bar .next-button', '.player-controls-middle-core-buttons button:last-child',
            'button[aria-label*="Next" i]']);
          break;
        case 'previous':
          if (v && v.currentTime > 3) { v.currentTime = 0; break; }
          if (actionHandlers.previoustrack) { actionHandlers.previoustrack({ action: 'previoustrack' }); break; }
          if (!clickFirst(['.ytp-prev-button', 'ytmusic-player-bar .previous-button', 'button[aria-label*="Previous" i]'])) {
            history.back();
          }
          break;
        case 'stop':
          if (v) v.pause();
          break;
      }
    } catch (e) {}
    setTimeout(function () { report(true); }, 250);
  }

  /* ------------------------------------------------------------------ */
  /* 5. Web Share API polyfill (YouTube's share sheet)                   */
  /* ------------------------------------------------------------------ */
  try {
    navigator.share = function (data) {
      data = data || {};
      post('share', { title: data.title || document.title, text: data.text || '', url: data.url || location.href });
      return Promise.resolve();
    };
    navigator.canShare = function () { return true; };
  } catch (e) {}

  /* ------------------------------------------------------------------ */
  /* 6. Pull-to-refresh hints: is the touched content scrolled?          */
  /* ------------------------------------------------------------------ */
  function blocksRefresh(target) {
    if ((window.scrollY || document.documentElement.scrollTop || 0) > 0) return true;
    var el = target && target.nodeType === 1 ? target : null;
    if (el && el.closest && el.closest('ytmusic-player-page, ytmusic-player, #player, .player-container, ' +
      'ytm-engagement-panel, ytd-engagement-panel-section-list-renderer, tp-yt-paper-dialog, ytm-bottom-sheet-renderer, ' +
      'bottom-sheet-container, ytm-shorts-player, ytd-shorts, .reel-player-overlay-renderer')) return true;
    while (el && el !== document.body && el !== document.documentElement) {
      if (el.scrollTop > 0) {
        var oy = getComputedStyle(el).overflowY;
        if (oy === 'auto' || oy === 'scroll' || oy === 'overlay') return true;
      }
      el = el.parentElement;
    }
    return false;
  }
  document.addEventListener('touchstart', function (e) {
    post('scroll', { canScrollUp: blocksRefresh(e.target) });
  }, { capture: true, passive: true });

  /* ------------------------------------------------------------------ */
  /* 7. SponsorBlock-style auto skip                                     */
  /* ------------------------------------------------------------------ */
  var segments = { id: null, list: [] };
  var lastVideoId = null;
  var lastSkipAt = 0;

  function toast(text) {
    var t = document.createElement('div');
    t.textContent = text;
    t.style.cssText = 'position:fixed;left:50%;bottom:88px;transform:translateX(-50%);z-index:2147483647;' +
      'background:rgba(22,22,26,.92);color:#fff;padding:10px 18px;border-radius:999px;font:500 13px/1.2 Roboto,sans-serif;' +
      'box-shadow:0 6px 24px rgba(0,0,0,.45);border:1px solid rgba(255,255,255,.08);pointer-events:none;transition:opacity .4s';
    (document.body || document.documentElement).appendChild(t);
    setTimeout(function () { t.style.opacity = '0'; }, 1800);
    setTimeout(function () { t.remove(); }, 2300);
  }

  function onTimeUpdate(e) {
    var v = e.target;
    var id = currentVideoId();
    if (id !== lastVideoId) {
      lastVideoId = id;
      if (id) post('video', { videoId: id });
    }
    if (!cfg.sponsorBlock || !id || segments.id !== id || isAdShowing()) return;
    var t = v.currentTime;
    for (var i = 0; i < segments.list.length; i++) {
      var s = segments.list[i];
      if (t >= s.start && t < s.end - 0.3 && Date.now() - lastSkipAt > 1000) {
        lastSkipAt = Date.now();
        v.currentTime = s.end;
        toast('Skipped ' + s.category.replace(/_/g, ' '));
        post('skipped', { category: s.category, seconds: s.end - t });
        break;
      }
    }
  }

  window.__lumora = {
    cmd: command,
    report: function () { report(true); },
    setSegments: function (videoId, list) { segments = { id: videoId, list: list || [] }; },
    setConfig: function (c) { for (var k in c) cfg[k] = c[k]; }
  };

  document.addEventListener('DOMContentLoaded', function () { hookVideos(); report(true); });
})();
