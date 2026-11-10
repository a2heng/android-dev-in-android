#!/usr/bin/env node
/*
 * Prueba los scripts previos a la SPA (app/src/main/assets/oc-corejs.js y
 * oc-early.js) sin WebView: borra del motor de Node las APIs modernas
 * (URL.canParse, Promise.try, toSorted...) y comprueba que se reponen.
 *
 *   node scripts/test-webview-shims.js
 *
 * Motivo: la UI v2 de opencode necesita APIs de Chromium 108-128. Un WebView
 * sin actualizar rompia el arranque con "TypeError: URL.canParse is not a
 * function"; si se cambia un modulo de core-js o un shim, esto avisa antes de
 * compilar.
 */
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.dirname(__dirname)
const early = path.join(root, "app/src/main/assets/oc-early.js")
const corejs = path.join(root, "app/src/main/assets/oc-corejs.js")
const src = fs.readFileSync(early, "utf8")
const polyfills = fs.readFileSync(corejs, "utf8")

const logs = []
globalThis.window = globalThis
globalThis.location = { href: "http://127.0.0.1:4098/", origin: "http://127.0.0.1:4098" }
globalThis.localStorage = {
  length: 0,
  key: () => null,
  getItem: () => null,
  setItem: () => {},
}
globalThis.ocConfig = {
  debug: (m) => logs.push(m),
  homeDir: () => "/data/data/com.a2heng.adia/files/home",
}
globalThis.fetch = async () =>
  new Response("[]", { headers: { "content-type": "application/json" } })

function drop(owner, names) {
  for (const name of names) {
    try {
      delete owner[name]
    } catch {}
    assert.strictEqual(typeof owner[name], "undefined", `${name} no se pudo borrar`)
  }
}

// Lo que falta en un WebView Chromium 108 (y en 120-127 para Promise.try).
drop(URL, ["canParse", "parse"])
drop(Promise, ["withResolvers", "try"])
drop(Map, ["groupBy"])
drop(Object, ["groupBy", "hasOwn"])
drop(Set.prototype, [
  "union",
  "intersection",
  "difference",
  "symmetricDifference",
  "isSubsetOf",
  "isSupersetOf",
  "isDisjointFrom",
])
for (const proto of [Array.prototype, String.prototype]) {
  drop(proto, [
    "at",
    "findLast",
    "findLastIndex",
    "toSorted",
    "toReversed",
    "toSpliced",
    "with",
    "isWellFormed",
    "toWellFormed",
  ])
}
drop(AbortSignal, ["timeout", "any"])

// El script se ejecuta como lo haria el WebView: sin modulos, en global.
// Primero core-js (/oc-corejs.js) y luego el launcher (/oc-early.js).
new Function(polyfills)()
new Function(src)()
assert.strictEqual(window.__ocEarlyV1, true, "oc-early.js se ejecuto entero")

// URL: era el fallo del arranque de la SPA.
assert.strictEqual(URL.canParse("https://example.com"), true)
assert.strictEqual(URL.canParse("nope"), false)
assert.strictEqual(URL.canParse("/x", "https://example.com/a/b"), true)
assert.strictEqual(URL.canParse("http://"), false)
assert.strictEqual(URL.parse("https://example.com/a?b=1").searchParams.get("b"), "1")
assert.strictEqual(URL.parse("nope"), null)
assert.strictEqual(URL.parse("/y", "https://example.com").href, "https://example.com/y")

// Promesas.
const deferred = Promise.withResolvers()
deferred.resolve(7)
deferred.promise.then((value) => assert.strictEqual(value, 7))
assert.ok(Promise.try(() => 1) instanceof Promise)
Promise.try((a, b) => a + b, 2, 3).then((value) => assert.strictEqual(value, 5))
Promise.try(() => {
  throw new Error("boom")
}).catch((e) => assert.strictEqual(e.message, "boom"))

// Agrupacion.
const list = [1, 2, 3, 4, 5]
const grouped = Map.groupBy(list, (n) => (n % 2 ? "odd" : "even"))
assert.deepStrictEqual(grouped.get("odd"), [1, 3, 5])
assert.deepStrictEqual(grouped.get("even"), [2, 4])
const objectGroups = Object.groupBy(list, (n) => (n % 2 ? 1 : "1"))
assert.deepStrictEqual(Array.from(objectGroups["1"]), [1, 2, 3, 4, 5])
assert.strictEqual(Object.getPrototypeOf(objectGroups), null)

// Objetos y arrays.
assert.strictEqual(Object.hasOwn({ a: 1 }, "a"), true)
assert.strictEqual([1, 2, 3].at(-1), 3)
assert.strictEqual([1, 2, 3].at(9), undefined)
assert.strictEqual([1, 2, 3].findLast((n) => n < 3), 2)
assert.strictEqual([1, 2, 3].findLastIndex((n) => n < 3), 1)
assert.deepStrictEqual([3, 1, 2].toSorted(), [1, 2, 3])
assert.deepStrictEqual([1, 2, 3].toSorted((a, b) => b - a), [3, 2, 1])
assert.deepStrictEqual([1, 2, 3].toReversed(), [3, 2, 1])
assert.deepStrictEqual([1, 2, 3].toSpliced(1, 1, "x"), [1, "x", 3])
assert.deepStrictEqual([1, 2, 3].toSpliced(1), [1])
assert.deepStrictEqual([1, 2, 3].toSpliced(-1, 1), [1, 2])
assert.deepStrictEqual([1, 2, 3].with(1, 9), [1, 9, 3])
assert.deepStrictEqual([1, 2, 3].with(-1, 9), [1, 2, 9])
assert.throws(() => [1, 2, 3].with(5, 0), RangeError)
const original = [1, 2, 3]
original.toSorted((a, b) => b - a)
assert.deepStrictEqual(original, [1, 2, 3], "toSorted no muta el original")

// Strings.
assert.strictEqual("abc".at(-1), "c")
assert.strictEqual("ok".isWellFormed(), true)
assert.strictEqual("a\uD800b".isWellFormed(), false)
assert.strictEqual("a\uD800b".toWellFormed(), "a\uFFFDb")
assert.strictEqual("a\uDC00b".toWellFormed(), "a\uFFFDb")
assert.strictEqual("a\uD83D\uDE00b".toWellFormed(), "a\uD83D\uDE00b")

// Set.
assert.deepStrictEqual(Array.from(new Set([1, 2]).union(new Set([2, 3]))), [1, 2, 3])
assert.deepStrictEqual(Array.from(new Set([1, 2]).intersection(new Set([2, 3]))), [2])
assert.deepStrictEqual(Array.from(new Set([1, 2]).difference(new Set([2, 3]))), [1])
assert.deepStrictEqual(Array.from(new Set([1, 2]).symmetricDifference(new Set([2, 3]))), [1, 3])
assert.strictEqual(new Set([1]).isSubsetOf(new Set([1, 2])), true)
assert.strictEqual(new Set([1, 2]).isSupersetOf(new Set([1])), true)
assert.strictEqual(new Set([1]).isDisjointFrom(new Set([2])), true)

// AbortSignal.
const timed = AbortSignal.timeout(5)
const first = new AbortController()
const second = new AbortController()
const combined = AbortSignal.any([first.signal, second.signal])
second.abort("why")
assert.strictEqual(combined.aborted, true)
assert.strictEqual(combined.reason, "why")

setTimeout(() => {
  assert.strictEqual(timed.aborted, true)
  assert.strictEqual(timed.reason.name, "TimeoutError")
  const shimmed = logs.find((line) => line.includes("shims")) || ""
  console.log(shimmed)
  console.log("OK: shims de oc-early.js correctos")
}, 30)
