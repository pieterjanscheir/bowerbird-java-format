# Bowerbird

_A Java formatter that arranges your code the way a bowerbird arranges its nest: carefully, and always the same way._

Bowerbird is a modified version of [palantir-java-format](https://github.com/palantir/palantir-java-format)
(itself based on [google-java-format](https://github.com/google/google-java-format)) with custom formatting rules.
It is available under the same [Apache 2.0 License](./LICENSE). See [NOTICE](./NOTICE) for attribution.

The upstream documentation is kept in [docs/upstream-README.md](./docs/upstream-README.md).

## Coordinates

| Artifact | Coordinates |
|---|---|
| Formatter | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format` |
| SPI | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format-spi` |
| JDK bootstrap | `io.github.pieterjanscheir.bowerbird:bowerbird-java-format-jdk-bootstrap` |

Module folders and Java packages (`com.palantir.javaformat`) are deliberately unchanged, so that
upstream fixes can be merged in and the upstream Gradle and IntelliJ plugins can load Bowerbird.

## Using it in a Gradle project

Apply the upstream Gradle plugin and point it at Bowerbird instead of the default formatter:

```groovy
plugins {
    id 'com.palantir.java-format' version '<upstream version Bowerbird is based on>'
}

dependencies {
    palantirJavaFormat 'io.github.pieterjanscheir.bowerbird:bowerbird-java-format:<bowerbird version>'
}
```

Do not enable the native formatter (`palantir.native.formatter`): it downloads Palantir's precompiled
binary, which does not contain Bowerbird's rules.

## Building

```bash
./gradlew :palantir-java-format:test
./gradlew :palantir-java-format-spi:publishToMavenLocal :palantir-java-format-jdk-bootstrap:publishToMavenLocal :palantir-java-format:publishToMavenLocal
```

## Adding a formatting rule

1. Add a `YourRule.input` and `YourRule.output` pair to
   `palantir-java-format/src/test/resources/com/palantir/javaformat/java/testdata/`.
2. Change the formatter, usually in `JavaInputAstVisitor.java`.
3. Run the tests. `./gradlew test -Drecreate=true` regenerates expected outputs, so review that diff carefully.

Keep one rule per commit, so upstream merge conflicts are easy to trace.

## Staying up to date with upstream

```bash
git fetch upstream
git merge upstream/develop
```

`README.md` will conflict on every upstream change to it: keep this version and apply their changes to
`docs/upstream-README.md` if you care about them.
