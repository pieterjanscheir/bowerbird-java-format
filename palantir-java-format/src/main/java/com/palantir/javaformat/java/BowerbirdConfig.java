/*
 * Copyright 2026 Pieter-Jan Scheir
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.palantir.javaformat.java;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * Bowerbird: reads {@link BowerbirdRules} from a {@code bowerbird.yml} file. Every setting is optional; a missing
 * setting keeps Bowerbird's default. Unknown settings and wrong types are errors, so typos don't go unnoticed.
 *
 * <pre>{@code
 * preset: bowerbird        # or palantir, google: the starting point for every other setting
 * lineLength: 120
 * indent: 4
 * continuationIndent: 4
 * spaceAfterControlKeyword: false
 * arrayInitializerBraceSpaces: true
 * closingParenOnOwnLine: true
 * tryResourcesOnOwnLines: true
 * bodyWithoutBracesOnOwnLine: true
 * blankLines:
 *   keepTyped: false
 *   aroundClassBody: true
 *   betweenFields: false
 *   aroundFieldsWithJavadoc: false
 *   aroundInterfaceMethods: false
 * imports:
 *   groups: [static, java, javax, org, com, "*"]
 *   blankLineBetweenGroups: true
 * javadoc:
 *   enabled: true
 *   removeBlankLinesInDescription: true
 *   blankLineAfterDescription: true
 *   blankLineAfterParams: true
 *   blankLineAfterReturn: true
 *   singleSpaceAfterTagName: true
 * }</pre>
 *
 * <p>The formatter finds the file on its own classpath, so a project adds the folder that contains it to the
 * {@code palantirJavaFormat} dependencies. That classpath is the only thing the upstream Gradle, Spotless and IntelliJ
 * plugins share with the formatter.
 */
public final class BowerbirdConfig {

    /** Name of the settings file, looked up at the root of the formatter's classpath. */
    public static final String FILE_NAME = "bowerbird.yml";

    /**
     * Created on first use, so formatting without a settings file never loads the YAML parser (which keeps the
     * native-image formatter free of it).
     */
    private static final class Yaml {
        static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    }

    private static final ImmutableMap<String, BiConsumer<BowerbirdRules.Builder, Value>> SETTINGS =
            ImmutableMap.<String, BiConsumer<BowerbirdRules.Builder, Value>>builder()
                    .put("lineLength", (b, v) -> b.lineLength(v.integer()))
                    .put("indent", (b, v) -> b.indent(v.integer()))
                    .put("continuationIndent", (b, v) -> b.continuationIndent(v.integer()))
                    .put("spaceAfterControlKeyword", (b, v) -> b.spaceAfterControlKeyword(v.bool()))
                    .put("arrayInitializerBraceSpaces", (b, v) -> b.arrayInitializerBraceSpaces(v.bool()))
                    .put("closingParenOnOwnLine", (b, v) -> b.closingParenOnOwnLine(v.bool()))
                    .put("tryResourcesOnOwnLines", (b, v) -> b.tryResourcesOnOwnLines(v.bool()))
                    .put("bodyWithoutBracesOnOwnLine", (b, v) -> b.bodyWithoutBracesOnOwnLine(v.bool()))
                    .put("blankLines.keepTyped", (b, v) -> b.keepTypedBlankLines(v.bool()))
                    .put("blankLines.aroundClassBody", (b, v) -> b.blankLinesAroundClassBody(v.bool()))
                    .put("blankLines.betweenFields", (b, v) -> b.blankLinesBetweenFields(v.bool()))
                    .put("blankLines.aroundFieldsWithJavadoc", (b, v) -> b.blankLinesAroundFieldsWithJavadoc(v.bool()))
                    .put("blankLines.aroundInterfaceMethods", (b, v) -> b.blankLinesAroundInterfaceMethods(v.bool()))
                    .put("imports.groups", (b, v) -> b.importGroups(v.strings()))
                    .put("imports.blankLineBetweenGroups", (b, v) -> b.blankLineBetweenImportGroups(v.bool()))
                    .put("javadoc.enabled", (b, v) -> b.javadocLayout(v.bool()))
                    .put(
                            "javadoc.removeBlankLinesInDescription",
                            (b, v) -> b.javadocRemoveBlankLinesInDescription(v.bool()))
                    .put("javadoc.blankLineAfterDescription", (b, v) -> b.javadocBlankLineAfterDescription(v.bool()))
                    .put("javadoc.blankLineAfterParams", (b, v) -> b.javadocBlankLineAfterParams(v.bool()))
                    .put("javadoc.blankLineAfterReturn", (b, v) -> b.javadocBlankLineAfterReturn(v.bool()))
                    .put("javadoc.singleSpaceAfterTagName", (b, v) -> b.javadocSingleSpaceAfterTagName(v.bool()))
                    .buildOrThrow();

    private BowerbirdConfig() {}

    /**
     * Reads {@value #FILE_NAME} from the root of {@code classLoader}'s classpath, or returns
     * {@link BowerbirdRules#defaults()} when there is none.
     *
     * @throws BowerbirdConfigException when the file can't be read, is invalid, or is on the classpath twice
     */
    public static BowerbirdRules load(ClassLoader classLoader) {
        List<URL> files;
        try {
            files = Collections.list(classLoader.getResources(FILE_NAME));
        } catch (IOException e) {
            throw new BowerbirdConfigException("Could not look up " + FILE_NAME + " on the formatter classpath", e);
        }
        if (files.isEmpty()) {
            return BowerbirdRules.defaults();
        }
        if (files.size() > 1) {
            throw new BowerbirdConfigException(
                    "Found " + FILE_NAME + " more than once on the formatter classpath, keep only one: " + files);
        }
        URL file = files.get(0);
        try (InputStream in = file.openStream()) {
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            throw new BowerbirdConfigException("Could not read " + file, e);
        }
    }

    /** Reads the settings file at {@code file}, for the command line's {@code --config}. */
    public static BowerbirdRules load(Path file) {
        String text;
        try {
            text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BowerbirdConfigException("Could not read " + file, e);
        }
        return parse(text, file.toString());
    }

    /** Parses the YAML {@code text} of a settings file; {@code source} names the file in error messages. */
    public static BowerbirdRules parse(String text, String source) {
        JsonNode root;
        try {
            root = Yaml.MAPPER.readTree(text);
        } catch (JacksonException e) {
            throw new BowerbirdConfigException(source + " is not valid YAML: " + e.getOriginalMessage(), e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return BowerbirdRules.defaults(); // empty file
        }
        if (!root.isObject()) {
            throw new BowerbirdConfigException(source + " must contain settings (key: value), not a single value");
        }
        // The preset is the starting point, so it applies first, wherever it is in the file.
        BowerbirdRules.Builder builder = BowerbirdRules.builder(preset(root.get(PRESET), source));
        apply(builder, root, "", source);
        return builder.build();
    }

    /** The names of all settings, with sections joined by dots, for example {@code blankLines.keepTyped}. */
    public static List<String> settingNames() {
        return SETTINGS.keySet().asList();
    }

    /** The setting that picks the starting point for all other settings. */
    private static final String PRESET = "preset";

    private static BowerbirdRules.Preset preset(@Nullable JsonNode node, String source) {
        if (node == null) {
            return BowerbirdRules.Preset.BOWERBIRD;
        }
        for (BowerbirdRules.Preset preset : BowerbirdRules.Preset.values()) {
            if (node.isTextual() && node.textValue().equals(preset.name().toLowerCase(Locale.ROOT))) {
                return preset;
            }
        }
        throw new BowerbirdConfigException(
                source + ": preset must be one of bowerbird, palantir or google, got " + node);
    }

    private static void apply(BowerbirdRules.Builder builder, JsonNode node, String prefix, String source) {
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            String name = prefix + field.getKey();
            JsonNode value = field.getValue();
            if (name.equals(PRESET)) {
                continue; // already applied
            }
            BiConsumer<BowerbirdRules.Builder, Value> setting = SETTINGS.get(name);
            if (setting != null) {
                try {
                    setting.accept(builder, new Value(name, value));
                } catch (IllegalArgumentException e) {
                    throw new BowerbirdConfigException(source + ": " + e.getMessage(), e);
                }
            } else if (value.isObject() && isSection(name)) {
                apply(builder, value, name + ".", source);
            } else {
                throw new BowerbirdConfigException(source + ": unknown setting '" + name + "'. Known settings: "
                        + PRESET + ", " + String.join(", ", SETTINGS.keySet()));
            }
        }
    }

    private static boolean isSection(String name) {
        String prefix = name + ".";
        return SETTINGS.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    /** A setting's YAML value, converted with a clear error when it has the wrong type. */
    private static final class Value {
        private final String name;
        private final JsonNode node;

        Value(String name, JsonNode node) {
            this.name = name;
            this.node = node;
        }

        int integer() {
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new IllegalArgumentException(name + " must be a whole number, got " + node);
            }
            return node.intValue();
        }

        boolean bool() {
            if (!node.isBoolean()) {
                throw new IllegalArgumentException(name + " must be true or false, got " + node);
            }
            return node.booleanValue();
        }

        List<String> strings() {
            if (!node.isArray()) {
                throw new IllegalArgumentException(name + " must be a list, for example [static, java, \"*\"]");
            }
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                if (!element.isTextual()) {
                    throw new IllegalArgumentException(name + " must only contain text, got " + element);
                }
                result.add(element.textValue());
            }
            return result;
        }
    }

    /** Thrown when {@value #FILE_NAME} can't be read or contains an invalid setting. */
    public static final class BowerbirdConfigException extends RuntimeException {
        BowerbirdConfigException(String message) {
            super(message);
        }

        BowerbirdConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
