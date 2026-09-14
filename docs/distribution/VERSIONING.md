[English](VERSIONING.md) | [Português](VERSIONING.pt_BR.md)

# Kof Versioning

## Format

```text
MAJOR.MINOR.PATCH
```

The conceptual hierarchy:

```text
Major releases
    >
Major fixes
    >
Bugfixes
```

| Component | Meaning |
|-----------|-------------|
| `X` (MAJOR) | Major release |
| `Y` (MINOR) | Major fix / significant evolution |
| `Z` (PATCH) | Bugfix — the *little dot of shame* |

`PATCH` is affectionately called the **little dot of shame** because it
mainly represents:

- bugfix;
- fix;
- regression;
- small adjustments;
- small improvements without relevant architectural change.

## Current stage

Kof is at the initial stage:

```text
0.0.x
```

Therefore:

```text
0.0.4
0.0.5
0.0.6
...
```

## Alpha Convention

While Kof is in Alpha, the release explicitly carries this information:

```text
0.0.5-alpha
```

Rules:

- `0.0.5-alpha` identifies the artifact, the GitHub Release and the tag
  (`kof-0.0.5-alpha`);
- the component version (compiler/runtime/stdlib) is `0.0.4` — the
  `-alpha` suffix belongs to the release;
- nothing is called stable;
- the intended evolution is Alpha → Beta → Release Candidate → Stable
  (without a complex state machine at this moment).

## Single source of truth

The version lives in **a single file**: `VERSION` at the repository root.

```text
VERSION ──► scripts/bump-version.sh ──► pom.xml (<revision>)
                                     ──► kof-compiler/src/main/resources/dev/kof/version.properties
```

The pipeline automatically updates:

- compiler version;
- CLI version;
- runtime metadata;
- artifacts (jars);
- distribution package;
- GitHub Release;
- changelog.

**No version hardcoded in dozens of files** — that is a recipe for
inconsistency. If the version needs to change, change `VERSION` (or the
pipeline does it) and the rest follows.

## When the version changes

While in Alpha, **every commit on `main` generates the next Alpha version**
(PATCH increment):

```text
0.0.5-alpha → 0.0.5-alpha → 0.0.6-alpha → ...
```

Common-sense rules for the future:

- PATCH: bugfix, fix, regression;
- MINOR: significant capability evolution;
- MAJOR: architectural change / compatibility break.

## Verification

`kof version` and `kof info` report the packaged version. CI verifies that
`VERSION`, `pom.xml` and the version resource agree before any build.
