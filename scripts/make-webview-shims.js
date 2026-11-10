#!/usr/bin/env node
/*
 * Genera app/src/main/assets/oc-corejs.js: un bundle de core-js (MIT) con
 * solo los modulos que necesita la UI v2 de opencode y que un WebView sin
 * actualizar no trae. Se sirve como /oc-corejs.js e se inyecta antes del
 * bundle de la SPA (ver oc-early.js y MainActivity.injectEarlyScript).
 *
 *   node scripts/make-webview-shims.js
 *
 * Descarga core-js-builder y esbuild en un directorio temporal la primera vez.
 * El resultado se versiona para que el APK se pueda compilar sin red.
 */
const { execSync } = require("child_process")
const fs = require("fs")
const os = require("os")
const path = require("path")

const CORE_JS = "3.51.0"
const ESBUILD = "0.25.0"
const ROOT = path.dirname(__dirname)
const DEST = path.join(ROOT, "app/src/main/assets/oc-corejs.js")
const WORK = path.join(os.tmpdir(), "adia-corejs-build")

// Lo que usa la app (opencode 2.2.6) y no existe en Chromium 108. Los
// AbortSignal.* son DOM y core-js no los cubre: siguen en oc-early.js.
const MODULES = [
  "web.url.can-parse",
  "web.url.parse",
  "es.promise.try",
  "es.promise.with-resolvers",
  "es.object.group-by",
  "es.map.group-by",
  "es.object.has-own",
  "es.array.at",
  "es.array.find-last",
  "es.array.find-last-index",
  "es.array.to-sorted",
  "es.array.to-reversed",
  "es.array.to-spliced",
  "es.array.with",
  "es.string.at-alternative",
  "es.string.is-well-formed",
  "es.string.to-well-formed",
  "es.set.union.v2",
  "es.set.intersection.v2",
  "es.set.difference.v2",
  "es.set.symmetric-difference.v2",
  "es.set.is-subset-of.v2",
  "es.set.is-superset-of.v2",
  "es.set.is-disjoint-from.v2",
]

const HEADER = `/*
 * core-js ${CORE_JS} (MIT) - https://github.com/zloirock/core-js
 * GENERADO por scripts/make-webview-shims.js. No editar a mano.
 *
 * Solo los modulos que la UI v2 de opencode necesita y Chromium 108 no
 * tiene. Se inyecta antes del bundle de la SPA para que arranque en
 * WebViews sin actualizar. AbortSignal.timeout/any van aparte, en
 * oc-early.js, porque core-js no cubre APIs del DOM.
 *
 * Modulos: ${MODULES.join(", ")}
 */
`

function ensureDependencies() {
  const marker = path.join(WORK, "node_modules/core-js-builder/package.json")
  if (fs.existsSync(marker)) return
  fs.mkdirSync(WORK, { recursive: true })
  fs.writeFileSync(
    path.join(WORK, "package.json"),
    JSON.stringify({ name: "adia-corejs-build", private: true }, null, 2),
  )
  console.log("instalando core-js-builder y esbuild en", WORK)
  // Una sola cadena: en Windows npm es un .cmd y Node 20+ exige shell.
  execSync(
    `npm install --no-audit --no-fund --loglevel=error ` +
      `core-js-builder@${CORE_JS} esbuild@${ESBUILD}`,
    { cwd: WORK, stdio: "inherit" },
  )
}

async function main() {
  ensureDependencies()
  const builder = require(path.join(WORK, "node_modules/core-js-builder"))
  const esbuild = require(path.join(WORK, "node_modules/esbuild"))

  const raw = await builder({ modules: MODULES, format: "bundle" })
  // es2015 para que tambien pueda parsearlo un WebView mas viejo que 108.
  const minified = await esbuild.transform(raw, { minify: true, target: "es2015" })

  fs.writeFileSync(DEST, HEADER + minified.code)
  const size = fs.statSync(DEST).size
  console.log(`escrito ${path.relative(ROOT, DEST)} (${(size / 1024).toFixed(1)} KB)`)
  console.log(`${MODULES.length} modulos de core-js ${CORE_JS}`)

  for (const needle of ["canParse", "withResolvers", "groupBy", "toSorted"]) {
    if (!minified.code.includes(needle)) {
      throw new Error(`el bundle no contiene ${needle}; revisa MODULES`)
    }
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
