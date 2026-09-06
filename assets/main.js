/**
 * Anubhav v1.0.0 Landing Page Scripts
 * Minimal, lightweight, zero dependencies
 */

(function () {
  'use strict';

  // --- Theme Management ---
  const THEME_KEY = 'anubhav-theme';
  const root = document.documentElement;
  const themeToggle = document.getElementById('theme-toggle');
  const themeMeta = document.querySelector('meta[name="theme-color"]');

  const THEME_COLORS = {
    light: '#FAF9F6',
    dark: '#121316'
  };

  function getPreferredTheme() {
    const saved = localStorage.getItem(THEME_KEY);
    if (saved === 'light' || saved === 'dark') {
      return saved;
    }
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  function applyTheme(theme) {
    if (theme === 'dark') {
      root.setAttribute('data-theme', 'dark');
    } else {
      root.setAttribute('data-theme', 'light');
    }

    if (themeMeta) {
      themeMeta.setAttribute('content', THEME_COLORS[theme] || THEME_COLORS.light);
    }

    if (themeToggle) {
      const isDark = theme === 'dark';
      themeToggle.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
      themeToggle.innerHTML = isDark
        ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>'
        : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';
    }
  }

  const currentTheme = getPreferredTheme();
  applyTheme(currentTheme);

  if (themeToggle) {
    themeToggle.addEventListener('click', function () {
      const active = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      localStorage.setItem(THEME_KEY, active);
      applyTheme(active);
    });
  }

  // Listen for system theme changes if user hasn't explicitly set preference
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
      if (!localStorage.getItem(THEME_KEY)) {
        applyTheme(e.matches ? 'dark' : 'light');
      }
    });
  }

  // --- Sticky Mobile CTA Visibility via IntersectionObserver ---
  const heroCta = document.getElementById('hero-download-cta');
  const finalCta = document.getElementById('final-cta-section');
  const stickyBar = document.getElementById('mobile-sticky-bar');

  if (stickyBar && heroCta && 'IntersectionObserver' in window) {
    let heroVisible = true;
    let finalVisible = false;

    function updateStickyState() {
      if (!heroVisible && !finalVisible) {
        stickyBar.classList.add('is-visible');
      } else {
        stickyBar.classList.remove('is-visible');
      }
    }

    const heroObserver = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          heroVisible = entry.isIntersecting;
          updateStickyState();
        });
      },
      { threshold: 0.1 }
    );

    heroObserver.observe(heroCta);

    if (finalCta) {
      const finalObserver = new IntersectionObserver(
        function (entries) {
          entries.forEach(function (entry) {
            finalVisible = entry.isIntersecting;
            updateStickyState();
          });
        },
        { threshold: 0.15 }
      );
      finalObserver.observe(finalCta);
    }
  }
})();
