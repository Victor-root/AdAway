/* ============================================================
   AdAway Community - site behaviour
   Theme (system by default), language (browser by default),
   hero feed, nav.
   ============================================================ */
(function () {
  'use strict';

  /* ---------------------------------------------------------
     1. Translations
     --------------------------------------------------------- */
  var I18N = {
    en: {
      'meta.desc': 'An unofficial community fork of AdAway: a system-wide, open source ad blocker for Android, with VPN-mode stability fixes and Android TV support.',
      'a11y.skip': 'Skip to content',

      'nav.why': 'Why',
      'nav.how': 'How it works',
      'nav.features': 'Features',
      'nav.shots': 'Screenshots',
      'nav.get': 'Download',

      'hero.tag': 'Unofficial community fork',
      'hero.h1a': 'Ads stop',
      'hero.h1b': 'before they load.',
      'hero.sub': 'AdAway blocks ads and trackers across every app on your Android device, at the DNS level. No root required, no account, no traffic sent anywhere. This community fork keeps it stable on recent Android versions and adds Android TV support.',
      'hero.cta1': 'Download the APK',
      'hero.cta2': 'View source',
      'hero.req': 'Android 8 Oreo or later · GPLv3 · No trackers, no ads, no telemetry by default',
      'hero.feed': 'DNS requests',
      'hero.feedBlocked': 'blocked',

      'why.kicker': 'The fork',
      'why.h2': 'Why this exists',
      'why.lead': 'AdAway is a great project with a long history. Upstream release and review activity has been limited for a while, and some fixes affecting daily use have been sitting unused. This fork keeps them in a build you can actually install.',
      'why.1t': 'Fixes that were already written',
      'why.1b': 'Bug reports and pull requests, including VPN-mode stability work, had been open upstream for a long time. Rather than let tested improvements go to waste, they ship here.',
      'why.2t': 'Modern Android and OEM ROMs',
      'why.2b': 'Recent Android versions and aggressive manufacturer battery managers break VPN-based blocking in subtle ways. Most of the work here is about surviving that: no restart loops, no silent stops, no lying status.',
      'why.3t': 'Android TV in the same app',
      'why.3b': 'A remote-friendly TV interface lives in the same codebase as the phone app, so one build covers your phone, your tablet and your TV box.',
      'why.note': 'This is not a hostile fork and not a claim of ownership. It is not affiliated with, endorsed by, or signed by the official AdAway maintainers. If the official project becomes actively maintained again and equivalent fixes land there, this fork may be archived or re-aligned with upstream.',

      'how.kicker': 'How it works',
      'how.h2': 'Blocking at the source',
      'how.lead': 'Ads and trackers have to resolve a domain name before they can load anything. AdAway keeps a list of those domains and answers them with nothing, so the request never leaves your device. It works in every app, not just your browser.',
      'how.vpnTag': 'No root needed',
      'how.vpnT': 'VPN mode',
      'how.vpnB': 'AdAway registers a local VPN on the device purely to inspect DNS requests. Nothing is sent to a remote server, there is no account and no tunnel to anyone else. Your traffic never leaves your phone through AdAway.',
      'how.rootTag': 'Root required',
      'how.rootT': 'Root mode',
      'how.rootB': 'On a rooted device, AdAway writes the blocklist straight into the system hosts file. Nothing runs in the background at all, and the VPN slot stays free for an actual VPN.',
      'how.s1t': 'Pick a mode',
      'how.s1b': "The setup wizard walks you through VPN or root on first launch, and helps you keep the app alive against your phone's battery manager.",
      'how.s2t': 'Choose your sources',
      'how.s2b': 'Blocklists are plain hosts files maintained by the community. Ship with sensible defaults, add your own, and they refresh automatically.',
      'how.s3t': 'Turn it on',
      'how.s3b': 'That is it. Allow anything that breaks, redirect what you want, and watch live what is being blocked in the DNS log.',

      'feat.kicker': 'Features',
      'feat.h2': 'What you get',
      'feat.1t': 'Stable VPN mode',
      'feat.1b': "No restarting itself after you pause it, no reconnect loops, no tunnel rebuilt every time a network flickers. The app's status, the notification and the Quick Settings tile finally agree with each other, and the VPN comes back on its own if your phone kills it.",
      'feat.2t': 'Android TV',
      'feat.2b': 'A real remote-friendly interface: D-pad navigation, a leanback launcher entry, and a TV-adapted DNS monitor.',
      'feat.3t': 'Live DNS log',
      'feat.3b': 'See exactly what each app resolves, then block, allow or redirect a domain straight from the list.',
      'feat.4t': 'Your own lists',
      'feat.4b': 'Blocked, allowed and redirected hosts, each searchable, plus per-app exclusions from the VPN.',
      'feat.5t': 'Built-in updater',
      'feat.5b': 'The app checks its own GitHub releases and installs the new version for you. No store account involved.',
      'feat.6t': 'Diagnosable when it goes wrong',
      'feat.6b': 'An opt-in diagnostic log records VPN lifecycle and network events, never browsing history, so a problem that happens once a week can still be reported with something useful attached. Copy or share it in one tap.',

      'shots.kicker': 'Screenshots',
      'shots.h2': 'See it running',
      'shots.lead': 'These follow the theme of this page. Switch it in the top bar and they switch with it.',
      'shots.1': 'Home screen: block on or off in one tap, with live counters.',
      'shots.2': 'Your lists: blocked, allowed and redirected hosts.',
      'shots.3': 'DNS log: what your apps actually resolve, in real time.',
      'shots.4': 'The Android TV home screen, built for a remote.',

      'get.kicker': 'Install',
      'get.h2': 'Three ways to get it',
      'get.1t': 'Download the APK',
      'get.1b': 'Grab the latest release and install it. Android will ask you once to allow installs from your browser or file manager.',
      'get.1c': 'Download',
      'get.2t': 'Track it with Omnify',
      'get.2b': 'Add this repository to Omnify and it will follow new releases and offer updates alongside the rest of your apps.',
      'get.2c': 'Get Omnify',
      'get.3t': 'Let the app update itself',
      'get.3b': 'Once installed, AdAway Community checks for new versions on its own and walks you through the update, no browser needed.',
      'get.3c': 'Built in',
      'get.note': 'Every Community release is signed with the same key, so updates keep your existing settings and data. That key differs from the official AdAway one, so if you are coming from the official app you have to uninstall it first.',

      'foot.tagline': 'Unofficial community fork of AdAway',
      'foot.project': 'Project',
      'foot.releases': 'Releases',
      'foot.issues': 'Report an issue',
      'foot.contribute': 'Contribute',
      'foot.upstream': 'Upstream',
      'foot.official': 'Official AdAway',
      'foot.tvwork': 'Android TV work',
      'foot.license': 'Licensed under the GPLv3+, like the original AdAway. Created and maintained by the AdAway project contributors; this fork is not affiliated with or endorsed by them.'
    },

    fr: {
      'meta.desc': "Un fork communautaire non officiel d'AdAway : un bloqueur de publicités open source pour Android, qui agit sur tout le système, avec des correctifs de stabilité du mode VPN et la prise en charge d'Android TV.",
      'a11y.skip': 'Aller au contenu',

      'nav.why': 'Pourquoi',
      'nav.how': 'Fonctionnement',
      'nav.features': 'Fonctionnalités',
      'nav.shots': "Captures d'écran",
      'nav.get': 'Télécharger',

      'hero.tag': 'Fork communautaire non officiel',
      'hero.h1a': 'Les pubs bloquées',
      'hero.h1b': "avant même de s'afficher.",
      'hero.sub': "AdAway bloque les publicités et les traqueurs dans toutes les applications de votre appareil Android, au niveau DNS. Sans root, sans compte, sans envoyer votre trafic où que ce soit. Ce fork communautaire le garde stable sur les versions récentes d'Android et ajoute la prise en charge d'Android TV.",
      'hero.cta1': "Télécharger l'APK",
      'hero.cta2': 'Voir le code source',
      'hero.req': 'Android 8 Oreo ou supérieur · GPLv3 · Aucun traqueur, aucune publicité, aucune télémétrie par défaut',
      'hero.feed': 'Requêtes DNS',
      'hero.feedBlocked': 'bloquées',

      'why.kicker': 'Le fork',
      'why.h2': 'Pourquoi ce fork existe',
      'why.lead': "AdAway est un excellent projet avec une longue histoire. L'activité de publication et de revue en amont est limitée depuis un moment, et certains correctifs qui touchent l'usage quotidien dorment sans être publiés. Ce fork les regroupe dans une version que vous pouvez réellement installer.",
      'why.1t': 'Des correctifs déjà écrits',
      'why.1b': "Des rapports de bugs et des pull requests, dont un travail sur la stabilité du mode VPN, étaient ouverts en amont depuis longtemps. Plutôt que de laisser ces améliorations testées inutilisées, elles sont publiées ici.",
      'why.2t': 'Android récent et ROMs constructeur',
      'why.2b': "Les versions récentes d'Android et les gestionnaires de batterie agressifs des constructeurs cassent le blocage par VPN de manière subtile. L'essentiel du travail ici consiste à y survivre : plus de boucles de redémarrage, plus d'arrêts silencieux, plus d'état affiché qui ment.",
      'why.3t': 'Android TV dans la même app',
      'why.3b': "Une interface TV pensée pour la télécommande vit dans le même code que l'application mobile : une seule version couvre votre téléphone, votre tablette et votre box TV.",
      'why.note': "Ce n'est pas un fork hostile, ni une revendication de propriété. Il n'est ni affilié, ni approuvé, ni signé par les mainteneurs officiels d'AdAway. Si le projet officiel redevient activement maintenu et que des correctifs équivalents y sont intégrés, ce fork pourra être archivé ou réaligné sur l'amont.",

      'how.kicker': 'Fonctionnement',
      'how.h2': 'Un blocage à la source',
      'how.lead': "Les publicités et les traqueurs doivent résoudre un nom de domaine avant de pouvoir charger quoi que ce soit. AdAway garde une liste de ces domaines et n'y répond rien : la requête ne quitte jamais votre appareil. Cela fonctionne dans toutes les applications, pas seulement dans le navigateur.",
      'how.vpnTag': 'Sans root',
      'how.vpnT': 'Mode VPN',
      'how.vpnB': "AdAway déclare un VPN local sur l'appareil uniquement pour inspecter les requêtes DNS. Rien n'est envoyé à un serveur distant, il n'y a ni compte ni tunnel vers qui que ce soit. Votre trafic ne quitte jamais votre téléphone via AdAway.",
      'how.rootTag': 'Root requis',
      'how.rootT': 'Mode root',
      'how.rootB': "Sur un appareil rooté, AdAway écrit la liste de blocage directement dans le fichier hosts du système. Rien ne tourne en arrière-plan, et l'emplacement VPN reste libre pour un vrai VPN.",
      'how.s1t': 'Choisissez un mode',
      'how.s1b': "L'assistant de configuration vous guide entre VPN et root au premier lancement, et vous aide à empêcher le gestionnaire de batterie de votre téléphone de tuer l'application.",
      'how.s2t': 'Choisissez vos sources',
      'how.s2b': "Les listes de blocage sont de simples fichiers hosts maintenus par la communauté. Des sources par défaut sont fournies, vous pouvez ajouter les vôtres, et elles se mettent à jour automatiquement.",
      'how.s3t': 'Activez le blocage',
      'how.s3b': "C'est tout. Autorisez ce qui casse, redirigez ce que vous voulez, et observez en direct ce qui est bloqué dans le journal DNS.",

      'feat.kicker': 'Fonctionnalités',
      'feat.h2': 'Ce que vous obtenez',
      'feat.1t': 'Un mode VPN stable',
      'feat.1b': "Plus de redémarrage tout seul après une mise en pause, plus de boucles de reconnexion, plus de tunnel reconstruit à chaque scintillement du réseau. L'état affiché dans l'app, la notification et la tuile des réglages rapides sont enfin cohérents, et le VPN revient de lui-même si votre téléphone le tue.",
      'feat.2t': 'Android TV',
      'feat.2b': "Une vraie interface pour télécommande : navigation au D-pad, entrée dans le lanceur leanback et moniteur DNS adapté à la TV.",
      'feat.3t': 'Journal DNS en direct',
      'feat.3b': "Voyez exactement ce que chaque application résout, puis bloquez, autorisez ou redirigez un domaine directement depuis la liste.",
      'feat.4t': 'Vos propres listes',
      'feat.4b': "Hôtes bloqués, autorisés et redirigés, chacun avec une recherche, plus l'exclusion du VPN application par application.",
      'feat.5t': 'Mise à jour intégrée',
      'feat.5b': "L'application vérifie ses propres versions sur GitHub et installe la nouvelle pour vous. Aucun compte de store nécessaire.",
      'feat.6t': 'Diagnosticable quand ça coince',
      'feat.6b': "Un journal de diagnostic optionnel enregistre le cycle de vie du VPN et les événements réseau, jamais l'historique de navigation, pour qu'un problème qui survient une fois par semaine puisse quand même être signalé avec quelque chose d'utile. Copiez-le ou partagez-le en un geste.",

      'shots.kicker': "Captures d'écran",
      'shots.h2': "L'application en action",
      'shots.lead': 'Elles suivent le thème de cette page. Changez-le dans la barre du haut et elles changent avec.',
      'shots.1': "Écran d'accueil : blocage activé ou non en un geste, avec les compteurs en direct.",
      'shots.2': 'Vos listes : hôtes bloqués, autorisés et redirigés.',
      'shots.3': 'Journal DNS : ce que vos applications résolvent réellement, en temps réel.',
      'shots.4': "L'écran d'accueil Android TV, conçu pour la télécommande.",

      'get.kicker': 'Installation',
      'get.h2': 'Trois façons de l’obtenir',
      'get.1t': "Télécharger l'APK",
      'get.1b': "Récupérez la dernière version et installez-la. Android vous demandera une fois d'autoriser les installations depuis votre navigateur ou votre gestionnaire de fichiers.",
      'get.1c': 'Télécharger',
      'get.2t': 'Suivre les mises à jour avec Omnify',
      'get.2b': "Ajoutez ce dépôt à Omnify : il suivra les nouvelles versions et vous proposera les mises à jour en même temps que vos autres applications.",
      'get.2c': 'Obtenir Omnify',
      'get.3t': "Laisser l'app se mettre à jour",
      'get.3b': "Une fois installée, AdAway Community vérifie elle-même les nouvelles versions et vous guide pour la mise à jour, sans passer par le navigateur.",
      'get.3c': 'Intégré',
      'get.note': "Chaque version Community est signée avec la même clé, donc les mises à jour conservent vos réglages et vos données. Cette clé diffère de celle d'AdAway officiel : si vous venez de l'application officielle, vous devez d'abord la désinstaller.",

      'foot.tagline': "Fork communautaire non officiel d'AdAway",
      'foot.project': 'Projet',
      'foot.releases': 'Versions',
      'foot.issues': 'Signaler un problème',
      'foot.contribute': 'Contribuer',
      'foot.upstream': 'Projet amont',
      'foot.official': 'AdAway officiel',
      'foot.tvwork': 'Travail Android TV',
      'foot.license': "Distribué sous licence GPLv3+, comme AdAway d'origine. Créé et maintenu par les contributeurs du projet AdAway ; ce fork n'est ni affilié ni approuvé par eux."
    }
  };

  var LS_LANG = 'aac.lang';
  var LS_THEME = 'aac.theme';

  function store(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* private mode */ }
  }
  function read(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  /* ---------------------------------------------------------
     2. Theme: follows the system unless the visitor chose one
     --------------------------------------------------------- */
  var mq = window.matchMedia('(prefers-color-scheme: dark)');
  var themeBtn = document.getElementById('themeBtn');

  function systemTheme() {
    return mq.matches ? 'dark' : 'light';
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    if (themeBtn) {
      themeBtn.setAttribute('aria-label', theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme');
    }
  }

  applyTheme(read(LS_THEME) || systemTheme());

  // Keep following the system while no explicit choice has been made.
  var onSystemChange = function () {
    if (!read(LS_THEME)) applyTheme(systemTheme());
  };
  if (mq.addEventListener) mq.addEventListener('change', onSystemChange);
  else if (mq.addListener) mq.addListener(onSystemChange);

  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      store(LS_THEME, next);
      applyTheme(next);
    });
  }

  /* ---------------------------------------------------------
     3. Language: browser locale by default
     --------------------------------------------------------- */
  function detectLang() {
    var saved = read(LS_LANG);
    if (saved && I18N[saved]) return saved;
    var list = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en'];
    for (var i = 0; i < list.length; i++) {
      var code = String(list[i]).toLowerCase().split('-')[0];
      if (I18N[code]) return code;
    }
    return 'en';
  }

  var descTag = document.querySelector('meta[name="description"]');

  function applyLang(lang) {
    var dict = I18N[lang] || I18N.en;
    document.documentElement.setAttribute('lang', lang);

    var nodes = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < nodes.length; i++) {
      var key = nodes[i].getAttribute('data-i18n');
      if (dict[key]) nodes[i].textContent = dict[key];
    }
    if (descTag && dict['meta.desc']) descTag.setAttribute('content', dict['meta.desc']);

    var buttons = document.querySelectorAll('[data-lang]');
    for (var j = 0; j < buttons.length; j++) {
      buttons[j].setAttribute('aria-pressed', buttons[j].getAttribute('data-lang') === lang ? 'true' : 'false');
    }
  }

  applyLang(detectLang());

  var langButtons = document.querySelectorAll('[data-lang]');
  for (var k = 0; k < langButtons.length; k++) {
    langButtons[k].addEventListener('click', function () {
      var lang = this.getAttribute('data-lang');
      store(LS_LANG, lang);
      applyLang(lang);
    });
  }

  /* ---------------------------------------------------------
     4. Navigation
     --------------------------------------------------------- */
  var nav = document.getElementById('nav');
  var menuBtn = document.getElementById('menuBtn');
  var navLinks = document.querySelector('.nav-links');

  if (nav) {
    var onScroll = function () {
      nav.classList.toggle('stuck', window.scrollY > 8);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
  }

  if (menuBtn && navLinks) {
    menuBtn.addEventListener('click', function () {
      var open = navLinks.classList.toggle('open');
      menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    navLinks.addEventListener('click', function (event) {
      if (event.target.tagName === 'A') {
        navLinks.classList.remove('open');
        menuBtn.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* ---------------------------------------------------------
     5. Screenshot placeholders
        Keeps the layout honest before the real captures land.
     --------------------------------------------------------- */
  function markMissing(img) {
    var name = (img.getAttribute('src') || '').split('/').pop();
    var box = document.createElement('div');
    box.className = 'shot-missing ' + (img.className || '');
    box.textContent = name;
    if (img.parentNode) img.parentNode.replaceChild(box, img);
  }

  var shots = document.querySelectorAll('.phone img, .tvframe img');
  for (var s = 0; s < shots.length; s++) {
    (function (img) {
      img.addEventListener('error', function () { markMissing(img); });
      // The image may already have failed before this script ran, in which
      // case the error event is long gone; catch that state directly.
      if (img.complete && img.naturalWidth === 0) markMissing(img);
    })(shots[s]);
  }

  /* ---------------------------------------------------------
     6. Direct APK link
        The release asset is named after its version
        (AdAway.Community.v6.6.0-c.apk), so no fixed URL can point at
        it. The buttons therefore ship pointing at the latest release
        page, which always works and needs no script, and are upgraded
        here to the actual .apk once the API answers. Any failure
        (offline, rate limit, blocked request) simply leaves the
        working fallback in place.
        The answer is cached so a visitor costs one request per day.
     --------------------------------------------------------- */
  var apkLinks = document.querySelectorAll('[data-apk]');

  if (apkLinks.length && window.fetch) {
    var API = 'https://api.github.com/repos/Victor-root/AdAway-Community/releases/latest';
    var CACHE_KEY = 'aac.apk';
    var CACHE_MS = 24 * 60 * 60 * 1000;

    var setApkHref = function (url) {
      for (var i = 0; i < apkLinks.length; i++) apkLinks[i].setAttribute('href', url);
    };

    var cached = null;
    try { cached = JSON.parse(read(CACHE_KEY) || 'null'); } catch (e) { cached = null; }

    if (cached && cached.url && (Date.now() - cached.at) < CACHE_MS) {
      setApkHref(cached.url);
    } else {
      fetch(API, { headers: { Accept: 'application/vnd.github+json' } })
        .then(function (response) {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          return response.json();
        })
        .then(function (release) {
          var assets = release.assets || [];
          for (var i = 0; i < assets.length; i++) {
            var name = (assets[i].name || '').toLowerCase();
            if (name.slice(-4) === '.apk' && assets[i].browser_download_url) {
              setApkHref(assets[i].browser_download_url);
              store(CACHE_KEY, JSON.stringify({ url: assets[i].browser_download_url, at: Date.now() }));
              return;
            }
          }
        })
        .catch(function () { /* keep the releases-page fallback */ });
    }
  }

  /* ---------------------------------------------------------
     7. Hero feed
     --------------------------------------------------------- */
  var feedList = document.getElementById('feedList');
  var feedCount = document.getElementById('feedCount');

  if (feedList) {
    var ENTRIES = [
      ['ads.doubleclick.net', true],
      ['graph.facebook.com', true],
      ['api.github.com', false],
      ['googleads.g.doubleclick.net', true],
      ['analytics.tiktok.com', true],
      ['cdn.jsdelivr.net', false],
      ['app-measurement.com', true],
      ['sdk.iad-01.braze.com', true],
      ['upload.wikimedia.org', false],
      ['track.adform.net', true],
      ['pagead2.googlesyndication.com', true],
      ['api.spotify.com', false],
      ['telemetry.mozilla.org', true],
      ['events.redditmedia.com', true],
      ['static.cloudflareinsights.com', true]
    ];

    var MAX_ROWS = 6;
    var index = 0;
    var blocked = 0;
    var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function label(isBlocked) {
      var lang = document.documentElement.getAttribute('lang') || 'en';
      if (isBlocked) return lang === 'fr' ? 'BLOQUÉ' : 'BLOCKED';
      return lang === 'fr' ? 'OK' : 'OK';
    }

    function push() {
      var entry = ENTRIES[index % ENTRIES.length];
      index++;

      var li = document.createElement('li');
      if (entry[1]) {
        li.className = 'b';
        blocked++;
        if (feedCount) feedCount.textContent = blocked;
      }

      var domain = document.createElement('span');
      domain.className = 'd';
      domain.textContent = entry[0];

      var tag = document.createElement('span');
      tag.className = 'f-tag ' + (entry[1] ? 'f-b' : 'f-o');
      tag.textContent = label(entry[1]);

      li.appendChild(domain);
      li.appendChild(tag);
      feedList.insertBefore(li, feedList.firstChild);

      while (feedList.children.length > MAX_ROWS) {
        feedList.removeChild(feedList.lastChild);
      }
    }

    for (var p = 0; p < MAX_ROWS; p++) push();

    if (!reduced) {
      var timer = null;
      var start = function () { if (!timer) timer = setInterval(push, 1900); };
      var stop = function () { clearInterval(timer); timer = null; };

      document.addEventListener('visibilitychange', function () {
        if (document.hidden) stop(); else start();
      });

      if ('IntersectionObserver' in window) {
        new IntersectionObserver(function (entries) {
          entries[0].isIntersecting ? start() : stop();
        }, { threshold: 0.1 }).observe(feedList);
      } else {
        start();
      }
    }
  }
})();
