/*
 * Script temprano del launcher (se sirve desde /oc-early.js y se inyecta
 * antes del bundle de la SPA). Hace tres cosas:
 *
 *  0. Completa las APIs modernas que la UI v2 usa y un WebView sin
 *     actualizar no tiene. Un Android 13 de fabrica trae Chromium 108 y la
 *     SPA moria al arrancar con "TypeError: URL.canParse is not a function"
 *     (pantalla "出了点问题"). Ver el bloque de compatibilidad.
 *
 *  1. Cachea y poda /provider. La UI v1 descarga 7,2 MB (232 proveedores,
 *     8.951 modelos) cada vez que cambia el directorio del proyecto; el
 *     selector de modelo tardaba ~2,5 s por eso. Solo los proveedores
 *     conectados necesitan sus modelos: el resto conserva sus metadatos
 *     (el dialogo de conexion usa id/nombre, no los modelos).
 *
 *  2. El dialogo "Add project" empieza en el home privado de la app, que no
 *     tiene carpetas de usuario. Con root, el APK monta /storage/emulated/0
 *     en HOME/sdcard (bind mount) y se inyectan sus carpetas como
 *     "sdcard/<nombre>/" para elegir una directamente. Sin root se usa el
 *     fallback con "..": el servidor lo resuelve, pero no sigue symlinks.
 *
 * Todo es defensivo: si algo falla, se deja pasar la peticion original.
 */
(function () {
  'use strict';

  /*
   * --- Compatibilidad con WebView antiguos -----------------------------
   *
   * La UI v2 usa APIs recientes: URL.canParse (Chromium 120), Promise.try
   * (128), Promise.withResolvers (119), Map.groupBy (117),
   * AbortSignal.any (116), Array.prototype.toSorted/with (110)... Un
   * WebView que no se actualiza (Android 13 de fabrica trae Chromium 108)
   * lanza TypeError en la primera llamada y la SPA se queda en el error
   * de arranque. Se completan aqui, que es lo unico que corre antes del
   * bundle. Todo va con guardas: lo que ya existe no se toca.
   */
  var SHIMMED = (function compat() {
    var done = [];

    function toInteger(value) {
      var n = Number(value);
      if (n !== n) return 0;
      if (n === Infinity || n === -Infinity) return n;
      return n < 0 ? Math.ceil(n) : Math.floor(n);
    }

    function define(owner, name, value) {
      if (!owner || typeof owner[name] === 'function') return;
      try {
        // No enumerable: los polyfills no deben salir en for...in.
        Object.defineProperty(owner, name, {
          value: value,
          writable: true,
          configurable: true,
          enumerable: false
        });
        done.push(name);
      } catch (e) {}
    }

    function copyOf(o, len) {
      var out = new Array(len);
      for (var i = 0; i < len; i++) out[i] = o[i];
      return out;
    }

    function at(index) {
      var o = Object(this);
      var len = o.length >>> 0;
      var k = toInteger(index);
      if (k < 0) k += len;
      if (k < 0 || k >= len) return undefined;
      return o[k];
    }

    function findLastIndex(predicate, thisArg) {
      var o = Object(this);
      var len = o.length >>> 0;
      for (var i = len - 1; i >= 0; i--) {
        if (predicate.call(thisArg, o[i], i, o)) return i;
      }
      return -1;
    }

    function toSorted(compare) {
      var o = Object(this);
      return copyOf(o, o.length >>> 0).sort(compare);
    }

    function toReversed() {
      var o = Object(this);
      return copyOf(o, o.length >>> 0).reverse();
    }

    function toSpliced(start, skipCount) {
      var o = Object(this);
      var len = o.length >>> 0;
      var out = copyOf(o, len);
      var s = toInteger(start);
      s = s < 0 ? Math.max(len + s, 0) : Math.min(s, len);
      var items = Array.prototype.slice.call(arguments, 2);
      if (arguments.length < 2) {
        out.splice.apply(out, [s, len - s].concat(items));
      } else {
        var dc = toInteger(skipCount);
        dc = dc < 0 ? 0 : Math.min(dc, len - s);
        out.splice.apply(out, [s, dc].concat(items));
      }
      return out;
    }

    function withIndex(index, value) {
      var o = Object(this);
      var len = o.length >>> 0;
      var k = toInteger(index);
      if (k < 0) k += len;
      if (k < 0 || k >= len) throw new RangeError('Invalid index: ' + index);
      var out = copyOf(o, len);
      out[k] = value;
      return out;
    }

    function emptySet() {
      return new Set();
    }

    function unarySet(fn) {
      return function (other) {
        var list = other instanceof Set ? other : new Set(other);
        return fn(this, list);
      };
    }

    function isWellFormed() {
      var s = String(this);
      for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        if (c >= 0xd800 && c <= 0xdbff) {
          var next = s.charCodeAt(i + 1);
          if (!(next >= 0xdc00 && next <= 0xdfff)) return false;
          i++;
        } else if (c >= 0xdc00 && c <= 0xdfff) {
          // Suplente bajo sin pareja: la cadena no esta bien formada.
          return false;
        }
      }
      return true;
    }

    function toWellFormed() {
      var s = String(this);
      var out = '';
      for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        if (c >= 0xd800 && c <= 0xdbff) {
          var next = s.charCodeAt(i + 1);
          if (next >= 0xdc00 && next <= 0xdfff) {
            out += s.charAt(i) + s.charAt(i + 1);
            i++;
          } else {
            out += '\uFFFD';
          }
        } else if (c >= 0xdc00 && c <= 0xdfff) {
          out += '\uFFFD';
        } else {
          out += s.charAt(i);
        }
      }
      return out;
    }

    // URL.canParse (120) y URL.parse (126): el fallo que rompia el arranque.
    define(URL, 'canParse', function canParse(input, base) {
      try {
        if (arguments.length < 2) new URL(input);
        else new URL(input, base);
        return true;
      } catch (e) {
        return false;
      }
    });
    define(URL, 'parse', function parse(input, base) {
      try {
        return arguments.length < 2 ? new URL(input) : new URL(input, base);
      } catch (e) {
        return null;
      }
    });

    // Promesas: withResolvers (119) y try (128).
    define(Promise, 'withResolvers', function withResolvers() {
      var resolve;
      var reject;
      var promise = new Promise(function (res, rej) {
        resolve = res;
        reject = rej;
      });
      return { promise: promise, resolve: resolve, reject: reject };
    });
    define(Promise, 'try', function tryCall(fn) {
      var args = Array.prototype.slice.call(arguments, 1);
      return new Promise(function (resolve) {
        // Si fn lanza, el ejecutor rechaza la promesa.
        resolve(fn.apply(undefined, args));
      });
    });

    // Agrupacion: Object.groupBy / Map.groupBy (117).
    var groupInto = function (callback) {
      var groups = new Map();
      var self = this;
      var index = 0;
      var step;
      var iter = self[Symbol.iterator]();
      while (!(step = iter.next()).done) {
        var item = step.value;
        var key = callback(item, index++);
        var bucket = groups.get(key);
        if (bucket) bucket.push(item);
        else groups.set(key, [item]);
      }
      return groups;
    };
    define(Map, 'groupBy', function groupBy(items, callback) {
      return groupInto.call(items, callback);
    });
    define(Object, 'groupBy', function groupBy(items, callback) {
      var groups = groupInto.call(items, callback);
      var out = Object.create(null);
      groups.forEach(function (bucket, key) {
        // La clave de un objeto se coacciona a propiedad (1 y "1" se juntan).
        var prop = typeof key === 'symbol' ? key : String(key);
        if (out[prop]) out[prop] = out[prop].concat(bucket);
        else out[prop] = bucket;
      });
      return out;
    });

    define(Object, 'hasOwn', function hasOwn(object, key) {
      return Object.prototype.hasOwnProperty.call(object, key);
    });

    // Array: at (92), findLast/findLastIndex (97) y los "copying" de ES2023.
    define(Array.prototype, 'at', at);
    define(Array.prototype, 'findLast', function findLast(predicate, thisArg) {
      var index = findLastIndex.call(this, predicate, thisArg);
      return index < 0 ? undefined : this[index];
    });
    define(Array.prototype, 'findLastIndex', findLastIndex);
    define(Array.prototype, 'toSorted', toSorted);
    define(Array.prototype, 'toReversed', toReversed);
    define(Array.prototype, 'toSpliced', toSpliced);
    define(Array.prototype, 'with', withIndex);

    // String: at (92), isWellFormed/toWellFormed (111).
    define(String.prototype, 'at', at);
    define(String.prototype, 'isWellFormed', isWellFormed);
    define(String.prototype, 'toWellFormed', toWellFormed);

    // Set: metodos de conjuntos (122).
    define(Set.prototype, 'union', unarySet(function (self, other) {
      var out = new Set(self);
      other.forEach(function (value) {
        out.add(value);
      });
      return out;
    }));
    define(Set.prototype, 'intersection', unarySet(function (self, other) {
      var out = emptySet();
      self.forEach(function (value) {
        if (other.has(value)) out.add(value);
      });
      return out;
    }));
    define(Set.prototype, 'difference', unarySet(function (self, other) {
      var out = new Set(self);
      other.forEach(function (value) {
        out.delete(value);
      });
      return out;
    }));
    define(Set.prototype, 'symmetricDifference', unarySet(function (self, other) {
      var out = new Set(self);
      other.forEach(function (value) {
        if (out.has(value)) out.delete(value);
        else out.add(value);
      });
      return out;
    }));
    define(Set.prototype, 'isSubsetOf', unarySet(function (self, other) {
      var ok = true;
      self.forEach(function (value) {
        if (!other.has(value)) ok = false;
      });
      return ok;
    }));
    define(Set.prototype, 'isSupersetOf', unarySet(function (self, other) {
      var ok = true;
      other.forEach(function (value) {
        if (!self.has(value)) ok = false;
      });
      return ok;
    }));
    define(Set.prototype, 'isDisjointFrom', unarySet(function (self, other) {
      var ok = true;
      self.forEach(function (value) {
        if (other.has(value)) ok = false;
      });
      return ok;
    }));

    // AbortSignal.timeout (103) y AbortSignal.any (116).
    if (typeof AbortSignal === 'function') {
      define(AbortSignal, 'timeout', function timeout(ms) {
        var controller = new AbortController();
        setTimeout(function () {
          try {
            controller.abort(new DOMException('signal timed out', 'TimeoutError'));
          } catch (e) {
            controller.abort();
          }
        }, ms);
        return controller.signal;
      });
      define(AbortSignal, 'any', function any(signals) {
        var controller = new AbortController();
        var list = Array.prototype.slice.call(signals);
        for (var i = 0; i < list.length; i++) {
          var signal = list[i];
          if (!signal) continue;
          if (signal.aborted) {
            controller.abort(signal.reason);
            break;
          }
          signal.addEventListener('abort', function () {
            controller.abort(this.reason);
          }, { once: true });
        }
        return controller.signal;
      });
    }

    return done;
  })();

  if (window.__ocEarlyV1) {
    if (SHIMMED.length) dbg('shims ' + SHIMMED.join(','));
    return;
  }
  window.__ocEarlyV1 = true;

  var nativeFetch = window.fetch ? window.fetch.bind(window) : null;
  if (!nativeFetch || typeof Response === 'undefined') return;

  var PROVIDER_TTL = 10 * 60 * 1000;
  var cache = Object.create(null);
  var inflight = Object.create(null);
  var generation = 0;

  function dbg(msg) {
    try {
      if (window.ocConfig && ocConfig.debug) ocConfig.debug('early: ' + msg);
    } catch (e) {}
  }

  if (SHIMMED.length) dbg('shims ' + SHIMMED.join(','));

  /*
   * Instalaciones anteriores guardaban el proyecto como /data/user/0/...,
   * mientras el servidor devuelve las sesiones como /data/data/.... Son la
   * misma ruta, pero la UI compara cadenas y las sesiones desaparecian de la
   * barra lateral. Se migran los valores de localStorage antes del bundle.
   */
  function migrateLegacyPaths() {
    try {
      if (!window.ocConfig || !ocConfig.homeDir) return;
      var canonical = ocConfig.homeDir();
      if (!canonical) return;
      var legacy = canonical.replace('/data/data/', '/data/user/0/');
      if (legacy === canonical) return;
      var migrated = 0;
      for (var i = 0; i < localStorage.length; i++) {
        var key = localStorage.key(i);
        var value = localStorage.getItem(key);
        if (!value || value.indexOf(legacy) < 0) continue;
        localStorage.setItem(key, value.split(legacy).join(canonical));
        migrated++;
      }
      if (migrated > 0) dbg('paths migrated ' + migrated);
    } catch (e) {
      dbg('path migration error ' + e);
    }
  }

  migrateLegacyPaths();

  function parseUrl(input) {
    try {
      if (typeof input === 'string') return new URL(input, location.href);
      if (input && typeof input.href === 'string') {
        return new URL(input.href, location.href);
      }
      if (input && input.url) return new URL(input.url, location.href);
    } catch (e) {}
    return null;
  }

  function dirFromUrl(u) {
    if (!u) return '';
    // URLSearchParams ya devuelve el valor decodificado.
    var d = u.searchParams.get('directory');
    if (!d) d = u.searchParams.get('location[directory]');
    if (!d) d = u.searchParams.get('location%5Bdirectory%5D');
    return d || '';
  }

  function clearCache() {
    generation++;
    cache = Object.create(null);
    inflight = Object.create(null);
  }

  function prune(obj) {
    if (!obj || !Array.isArray(obj.all) || !Array.isArray(obj.connected)) return obj;
    var connected = Object.create(null);
    for (var i = 0; i < obj.connected.length; i++) {
      connected[obj.connected[i]] = true;
    }
    var all = new Array(obj.all.length);
    for (var j = 0; j < obj.all.length; j++) {
      var p = obj.all[j];
      if (!p || connected[p.id]) {
        all[j] = p;
        continue;
      }
      var copy = {};
      for (var k in p) {
        if (Object.prototype.hasOwnProperty.call(p, k)) copy[k] = p[k];
      }
      copy.models = {};
      all[j] = copy;
    }
    obj.all = all;
    return obj;
  }

  function pruneText(text) {
    try { return JSON.stringify(prune(JSON.parse(text))); } catch (e) { return text; }
  }

  function jsonResponse(body) {
    return new Response(body, {
      status: 200,
      statusText: 'OK',
      headers: { 'Content-Type': 'application/json' }
    });
  }

  function isProvider(u, method) {
    return u && u.origin === location.origin && u.pathname === '/provider' &&
      (!method || method === 'GET' || method === 'HEAD');
  }

  /*
   * El selector de proyectos empieza a buscar en "/" y va bajando. Android
   * no deja al servidor listar "/", "/storage" ni "/storage/emulated", asi
   * que el buscador nunca llegaba a /storage/emulated/0 (almacenamiento
   * compartido) y decia "No folders found". Se completan esos tres niveles;
   * a partir de /storage/emulated/0 lista el servidor de verdad.
   */
  var SYNTHETIC_DIRS = {
    '/': [{ name: 'storage', path: 'storage/', absolute: '/storage',
      type: 'directory', ignored: false }],
    '/storage': [{ name: 'emulated', path: 'emulated/', absolute: '/storage/emulated',
      type: 'directory', ignored: false }],
    '/storage/emulated': [{ name: '0', path: '0/', absolute: '/storage/emulated/0',
      type: 'directory', ignored: false }]
  };

  function syntheticFileList(u, method) {
    if (!u || u.origin !== location.origin || u.pathname !== '/file') return null;
    if (method && method !== 'GET' && method !== 'HEAD') return null;
    var dir = u.searchParams.get('directory') || '';
    dir = dir.replace(/\/+$/, '') || '/';
    var list = SYNTHETIC_DIRS[dir];
    if (!list) return null;
    dbg('file synthetic ' + dir);
    return jsonResponse(JSON.stringify(list));
  }

  /*
   * El selector muestra cualquier carpeta, incluida .git, y quien no conoce
   * git acaba abriendola como proyecto. Las carpetas ocultas no son
   * proyectos: se quitan de los listados del selector. Los ficheros ocultos
   * (.env, .gitignore...) se conservan.
   */
  function baseName(path) {
    var name = String(path || '').replace(/\/+$/, '');
    return name.substring(name.lastIndexOf('/') + 1);
  }

  function hiddenName(path) {
    return baseName(path).charAt(0) === '.';
  }

  function appHome() {
    try {
      if (window.ocConfig && ocConfig.homeDir) {
        return String(ocConfig.homeDir() || '').replace(/\/+$/, '');
      }
    } catch (e) {}
    return '';
  }

  function hiddenDir(entry) {
    // /find/file?dirs=true devuelve cadenas; /file devuelve objetos.
    if (typeof entry === 'string') return hiddenName(entry);
    // /find?type=directory no siempre incluye "type"; /file si lo incluye.
    if (!entry || (entry.type && entry.type !== 'directory')) return false;
    return hiddenName(entry.name || entry.path || '');
  }

  /*
   * El home privado (/data/data/<pkg>/files/home) esta cinco niveles por
   * debajo de "/". Se usa una ruta relativa con ".." porque el servidor la
   * resuelve pero no sigue symlinks. Asi el selector puede entrar en
   * /storage/emulated/0 sin tocar el home ni crear enlaces.
   */
  var SDCARD_ROOT = '/storage/emulated/0';

  function homeToSdcard() {
    var home = appHome();
    if (!home) return '';
    var parts = home.split('/').filter(function (p) { return p; });
    if (parts.length < 2) return '';
    var rel = '';
    for (var i = 0; i < parts.length; i++) rel += '../';
    return rel + 'storage/emulated/0';
  }

  function isHomeDir(dir) {
    var home = appHome();
    if (!home || !dir) return false;
    return String(dir).replace(/\/+$/, '') === home;
  }

  function filterEntries(list, dir) {
    var inHome = isHomeDir(dir);
    return list.filter(function (e) {
      if (typeof e === 'string') {
        // El selector solo quiere carpetas. En v1 /find/file?dirs=true
        // mezcla ficheros ("nota.txt") con carpetas ("docs/").
        if (e !== '/' && e.charAt(e.length - 1) !== '/') return false;
      } else if (e && e.type && e.type !== 'directory') {
        return true;
      }
      // "lib" es la carpeta nativa del runtime dentro de HOME; no es un
      // proyecto del usuario. Solo se oculta en la raiz de HOME.
      if (inHome && baseName(typeof e === 'string' ? e : (e.name || e.path)) === 'lib') {
        return false;
      }
      return !hiddenDir(e);
    });
  }

  function stripHiddenDirs(text, dir) {
    try {
      var list = JSON.parse(text);
      if (!Array.isArray(list)) return text;
      var out = filterEntries(list, dir);
      return out.length === list.length ? text : JSON.stringify(out);
    } catch (e) {
      return text;
    }
  }

  /*
   * Lista las carpetas reales de /storage/emulated/0 y las devuelve como
   * entradas dentro del home. Con root el origen es el bind mount
   * (HOME/sdcard); sin root es la ruta relativa con "..". El dialogo une
   * entry.path con la carpeta actual e ignora entry.absolute.
   */
  function sdcardDirs(list, mounted) {
    var home = appHome();
    var rel = mounted ? 'sdcard' : homeToSdcard();
    if (!home || !rel) return Promise.resolve(list);
    var url = '/file?path=&directory=' +
      encodeURIComponent(home + '/' + rel);
    return nativeFetch(url).then(function (r) {
      if (!r || !r.ok) return list;
      return r.json().then(function (items) {
        if (!Array.isArray(items)) return list;
        var seen = Object.create(null);
        for (var i = 0; i < list.length; i++) {
          var e = list[i];
          if (e && e.name) seen[e.name] = true;
        }
        var out = list.slice();
        var absoluteRoot = mounted ? home + '/sdcard' : SDCARD_ROOT;
        for (var j = 0; j < items.length; j++) {
          var it = items[j];
          if (!it || it.type !== 'directory') continue;
          var name = it.name || baseName(it.path);
          if (!name || name.charAt(0) === '.' || seen[name]) continue;
          seen[name] = true;
          out.push({
            name: name,
            path: rel + '/' + name + '/',
            absolute: absoluteRoot + '/' + name,
            type: 'directory',
            ignored: false
          });
        }
        return out;
      });
    }).catch(function () {
      return list;
    });
  }

  function hasSdcard(list) {
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      if (e && (e.name === 'sdcard' || baseName(e.path) === 'sdcard')) return true;
    }
    return false;
  }

  function pickerList(text, dir) {
    try {
      var list = JSON.parse(text);
      if (!Array.isArray(list)) return text;
      var out = filterEntries(list, dir);
      // En moviles con root, el APK monta /storage/emulated/0 en HOME/sdcard.
      // El servidor lo lista como una carpeta normal y las rutas quedan
      // limpias (~/sdcard/Download). El fallback con ".." solo se usa si el
      // montaje no existe (sin root o si el montaje fallo).
      if (!isHomeDir(dir)) return JSON.stringify(out);
      // Si el servidor ya lista "sdcard" (bind mount), se inyectan sus
      // carpetas como "sdcard/<nombre>/" para poder elegir una directamente.
      return sdcardDirs(out, hasSdcard(out)).then(function (merged) {
        return JSON.stringify(merged);
      });
    } catch (e) {
      return text;
    }
  }

  function filterJson(r, fn) {
    if (!r || !r.ok) return r;
    var ct = r.headers.get('content-type') || '';
    if (ct.indexOf('application/json') < 0) return r;
    return r.text().then(function (text) {
      return Promise.resolve(fn(text)).then(function (body) {
        return new Response(body, {
          status: r.status,
          statusText: r.statusText,
          headers: r.headers
        });
      });
    });
  }

  function isPickerList(u, method) {
    // En v1 el selector pide /file?path=&directory=<carpeta>. El arbol de
    // ficheros usa path="." o una ruta real, asi que solo se filtra el
    // listado del selector (path vacio y directory presente).
    return u && u.origin === location.origin && u.pathname === '/file' &&
      (!method || method === 'GET' || method === 'HEAD') &&
      !(u.searchParams.get('path') || '') &&
      u.searchParams.has('directory');
  }

  function isPickerFind(u, method) {
    if (!u || u.origin !== location.origin) return false;
    if (method && method !== 'GET' && method !== 'HEAD') return false;
    // v1: /find/file?dirs=true. v2: /find?type=directory.
    if (u.pathname === '/find/file') return u.searchParams.get('dirs') === 'true';
    return u.pathname === '/find' && u.searchParams.get('type') === 'directory';
  }

  function isMutation(u, method) {
    if (!u || u.origin !== location.origin) return false;
    if (!method || method === 'GET' || method === 'HEAD') return false;
    var p = u.pathname;
    return p === '/config' || p === '/global/config' ||
      p === '/provider' || p === '/global/dispose' ||
      p.indexOf('/auth') === 0 || p.indexOf('/provider/') === 0;
  }

  function fetchProvider(u, input, init, key) {
    var hit = cache[key];
    var gen = generation;
    if (hit) {
      var age = Date.now() - hit.at;
      if (age < PROVIDER_TTL) {
        dbg('provider hit ' + key + ' ' + hit.body.length);
        return Promise.resolve(jsonResponse(hit.body));
      }
      // Caducado: se sirve al instante y se refresca en segundo plano.
      dbg('provider stale ' + key);
      nativeFetch(input, init).then(function (r) {
        if (!r || !r.ok) return;
        var ct = r.headers.get('content-type') || '';
        if (ct.indexOf('application/json') < 0) return;
        return r.text().then(function (text) {
          if (gen !== generation) return;
          var body = pruneText(text);
          cache[key] = { at: Date.now(), body: body };
          dbg('provider refresh ' + text.length + ' -> ' + body.length);
        });
      }).catch(function () {});
      return Promise.resolve(jsonResponse(hit.body));
    }

    if (inflight[key]) return inflight[key];
    dbg('provider fetch ' + key);
    inflight[key] = nativeFetch(input, init).then(function (r) {
      if (!r || !r.ok) return r;
      var ct = r.headers.get('content-type') || '';
      if (ct.indexOf('application/json') < 0) return r;
      return r.text().then(function (text) {
        var body = pruneText(text);
        if (gen === generation) cache[key] = { at: Date.now(), body: body };
        dbg('provider pruned ' + text.length + ' -> ' + body.length);
        return jsonResponse(body);
      });
    }).then(function (r) {
      delete inflight[key];
      return r;
    }, function (e) {
      delete inflight[key];
      throw e;
    });
    return inflight[key];
  }

  window.fetch = function (input, init) {
    var u = parseUrl(input);
    if (!u || u.origin !== location.origin) return nativeFetch(input, init);

    var method = (init && init.method) || (input && input.method) || 'GET';

    if (isMutation(u, method)) {
      clearCache();
      dbg('cache clear ' + method + ' ' + u.pathname);
      return nativeFetch(input, init);
    }

    var dir = dirFromUrl(u);

    var synth = syntheticFileList(u, method);
    if (synth) return synth;

    if (isPickerList(u, method) || isPickerFind(u, method)) {
      return nativeFetch(input, init).then(function (r) {
        return filterJson(r, function (text) {
          if (isPickerList(u, method)) return pickerList(text, dir);
          return stripHiddenDirs(text, dir);
        });
      });
    }

    if (isProvider(u, method)) {
      // El catalogo v1 es global: el servidor devuelve el mismo JSON para
      // cualquier directorio. Una sola clave evita el segundo fetch de 7 MB.
      return fetchProvider(u, input, init, 'global');
    }
    return nativeFetch(input, init);
  };

  dbg('loaded');
})();
