# Bowerbird

[![CI](https://github.com/pieterjanscheir/bowerbird-java-format/actions/workflows/ci.yml/badge.svg)](https://github.com/pieterjanscheir/bowerbird-java-format/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pieterjanscheir/bowerbird-java-format)](https://github.com/pieterjanscheir/bowerbird-java-format/releases)
[![Docs](https://img.shields.io/badge/docs-GitHub%20Pages-blue)](https://pieterjanscheir.github.io/bowerbird-java-format/)

_A Java formatter that arranges your code the way a bowerbird arranges its nest: carefully, and always the same way._

Bowerbird is a modified version of [palantir-java-format](https://github.com/palantir/palantir-java-format)
(itself based on [google-java-format](https://github.com/google/google-java-format)). It adds a `BOWERBIRD`
style whose output matches IntelliJ IDEA's formatter for the target code style.
It is available under the same [Apache 2.0 License](./LICENSE). See [NOTICE](./NOTICE) for attribution.

**Documentation: <https://pieterjanscheir.github.io/bowerbird-java-format/>**. It covers setup, every formatting
rule with before/after examples, and the contributor workflow. The page is built from [docs/index.html](./docs/index.html).
The upstream documentation is kept in [docs/upstream-README.md](./docs/upstream-README.md).

## What Bowerbird changes

`BOWERBIRD` is the default style, so the Gradle and IntelliJ plugins use it without extra settings. Upstream's
`PALANTIR`, `GOOGLE` and `AOSP` styles are unchanged. In short:

- No space between a control keyword and `(`: `if(`, `for(`, `while(`, `switch(`, `try(`, `catch(`, `synchronized(`.
- Import groups: static, `java`, `javax`, `org`, `com`, everything else, with a blank line between groups.
- A continuation indent of 4 spaces instead of 8, and `)` on its own line when arguments wrap one per line.
- Array initializers with spaces inside the braces: `{ 1, 2, 3 }`.
- Blank lines in code removed; fixed blank-line rules for class bodies, fields and methods.
- JavaDoc tag blocks separated by blank lines, and JavaDoc never re-wrapped.

See [Formatting rules](https://pieterjanscheir.github.io/bowerbird-java-format/#rules) for the full list and examples.

## Installing

| Artifact | Coordinates |
|---|---|
| Formatter | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format` |
| SPI | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format-spi` |
| JDK bootstrap | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format-jdk-bootstrap` |

Releases are published to GitHub Packages, which needs a personal access token with the `read:packages`
scope, even for public packages. The jars are also attached to each [GitHub Release](https://github.com/pieterjanscheir/bowerbird-java-format/releases).

Apply the upstream Gradle plugin and point it at Bowerbird instead of the default formatter:

```groovy
plugins {
    id 'com.palantir.java-format' version '<upstream version Bowerbird is based on>'
}

repositories {
    mavenCentral()
    maven {
        url = 'https://maven.pkg.github.com/pieterjanscheir/bowerbird-java-format'
        credentials {
            username = providers.gradleProperty('gpr.user').orElse(providers.environmentVariable('GITHUB_ACTOR')).get()
            password = providers.gradleProperty('gpr.key').orElse(providers.environmentVariable('GITHUB_TOKEN')).get()
        }
    }
}

dependencies {
    palantirJavaFormat 'io.github.pieterjanscheir.bowerbird:bowerbird-java-format:<bowerbird version>'
}
```

Do not enable the native formatter (`palantir.native.formatter`): it downloads Palantir's precompiled
binary, which does not contain Bowerbird's rules. For IntelliJ, install the upstream
[palantir-java-format plugin](https://plugins.jetbrains.com/plugin/13180-palantir-java-format); it picks up the
formatter version the Gradle plugin resolves. See [IntelliJ](https://pieterjanscheir.github.io/bowerbird-java-format/#intellij).

Module folders and Java packages (`com.palantir.javaformat`) are deliberately unchanged, so that
upstream fixes can be merged in and the upstream Gradle and IntelliJ plugins can load Bowerbird.

## Building

Requires JDK 21.

```bash
./gradlew :palantir-java-format:test
./gradlew :palantir-java-format-spi:publishToMavenLocal :palantir-java-format-jdk-bootstrap:publishToMavenLocal :palantir-java-format:publishToMavenLocal
```

CI ([ci.yml](./.github/workflows/ci.yml)) runs `./gradlew build` on every pull request and push to `main`.
It fails when the build changes tracked files.

## Adding a formatting rule

1. Add a `YourRule.input` and `YourRule.output` pair to
   `palantir-java-format/src/test/resources/com/palantir/javaformat/java/bowerbird-testdata/`.
   `BowerbirdIntegrationTest` picks them up.
2. Change the formatter, usually in `JavaInputAstVisitor.java`, and only for `Style.BOWERBIRD`,
   so upstream styles and their tests are unaffected.
3. Run the tests. `./gradlew test -Drecreate=true` regenerates expected outputs, so review that diff carefully.
4. Check the output against IntelliJ's formatter with the target code style: IntelliJ should leave it unchanged.

Keep one rule per commit, so upstream merge conflicts are easy to trace. When a rule changes, update
[docs/index.html](./docs/index.html) too.

## Staying up to date with upstream

Every Monday, [upstream-sync.yml](./.github/workflows/upstream-sync.yml) merges the latest palantir-java-format
release into a branch and opens a pull request, or opens an issue when the merge conflicts. Run it from the
Actions tab to merge a specific upstream tag or branch. To merge by hand:

```bash
git fetch upstream
git merge upstream/develop
```

`README.md` will conflict on every upstream change to it: keep this version and apply their changes to
`docs/upstream-README.md` if you care about them.

## Releasing

Push a version tag. [release.yml](./.github/workflows/release.yml) runs the tests, publishes the three
artifacts to GitHub Packages and creates a GitHub Release with the jars and generated release notes.

```bash
git tag 1.0.0
git push origin 1.0.0
```
