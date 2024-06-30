<div align="center">
    <a href="https://clojure.org/" target="_blank" rel="noopener noreferrer"><img src="https://img.shields.io/badge/clojure-v1.11.1-blue.svg" alt="Clojure Version"></a>
    <a href="https://redplanetlabs.com/" target="_blank" rel="noopener noreferrer"><img src="https://img.shields.io/badge/rama-v0.16.1-red.svg" alt="Rama Public Version"></a>
    <a href="https://github.com/skydread1/rama-space/actions/workflows/test.yml"><img src="https://github.com/skydread1/rama-space/actions/workflows/test.yml/badge.svg" alt="CI"></a>
    <a href="https://codecov.io/gh/skydread1/rama-space" ><img src="https://codecov.io/gh/skydread1/rama-space/branch/master/graph/badge.svg"/></a>
    <a href="https://github.com/skydread1/rama-space/issues" target="_blank" rel="noopener noreferrer"><img src="https://img.shields.io/badge/contributions-welcome-blue.svg" alt="Contributions welcome"></a>
</div>

<h1 align="center">rama-space - Using Clojure API</h1>

In the Red Planet Labs Documentation, there is a [tutorial](https://redplanetlabs.com/docs/~/tutorial6.html#gsc.tab=0) to build a simple social network called `rama-space` with RAMA using the Java API.

This repo aims at providing an implementation of `rama-space` using the Clojure API instead.

It provides more tests than the original examples.

## Get clj-kondo congig

```sh
clj-kondo --lint "$(clojure -A:provided -Spath)" --copy-configs --skip-lint
```

## Formatting

Check formatting errors:
```clojure
clj -T:cljfmt check
```

Fix formatting errors
```clojure
clj -T:cljfmt fix
```

CHeck outdated deps:
```clojure
clj -M:outdated
```

## Running tests

Run all unit tests (including rich-comment-tests)

```sh
clj -M:dev:test
```

Run rich-comment-tests (rct) in the REPL:
```clj
(com.mjdowney.rich-comment-tests/run-ns-tests! *ns*)
```

## Contributing

Feel free to open new issues if you discover bugs or wish to add new features.
