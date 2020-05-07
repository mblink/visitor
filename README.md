# Scalafix symbol literals

This repository contains a scalafix rule to convert symbol literals, e.g. `'foo` into calls to `Symbol.apply`, e.g. `Symbol("foo")`.
