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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bowerbird: lays out JavaDoc blocks the way IntelliJ does with the Bowerbird code style. Text is never wrapped or
 * joined; only blank lines and the spacing after tag names change:
 *
 * <ul>
 *   <li>blank lines inside the description are removed
 *   <li>one blank line between the description and the first tag
 *   <li>one blank line after the {@code @param} block and after {@code @return}, when more tags follow
 *   <li>no other blank lines between tags
 *   <li>one space between {@code @param} or {@code @throws} names and their description
 * </ul>
 *
 * <p>One-line comments and comments containing {@code <pre>} are left unchanged.
 */
final class BowerbirdJavadoc {

    private static final Pattern LINE_PREFIX = Pattern.compile("^\\s*\\*(?!/) ?");
    private static final Pattern TAG = Pattern.compile("^@(\\w+)(.*)$");
    private static final Pattern NAMED_TAG = Pattern.compile("^@(param|throws|exception)\\s+(\\S+)\\s+(.*)$");

    private BowerbirdJavadoc() {}

    /** Returns the JavaDoc comment {@code text} (from {@code /**} to {@code *}{@code /}) in Bowerbird layout. */
    static String format(String text, String lineSeparator) {
        if (!text.startsWith("/**") || !text.endsWith("*/") || text.contains("<pre")) {
            return text;
        }
        String[] rawLines = text.substring(3, text.length() - 2).split("\\R", -1);
        if (rawLines.length < 2) {
            return text; // one-line comment
        }

        List<String> description = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        for (String raw : rawLines) {
            String line = stripPrefix(raw);
            if (line.isEmpty()) {
                continue; // blank lines are dropped, the right ones are added back below
            }
            Matcher tag = TAG.matcher(line);
            if (tag.matches()) {
                tags.add(new Tag(tag.group(1), normalizeTagLine(line)));
            } else if (tags.isEmpty()) {
                description.add(line);
            } else {
                tags.get(tags.size() - 1).lines.add(line);
            }
        }
        if (description.isEmpty() && tags.isEmpty()) {
            return text;
        }

        List<String> out = new ArrayList<>(description);
        if (!description.isEmpty() && !tags.isEmpty()) {
            out.add("");
        }
        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            out.addAll(tag.lines);
            boolean hasNext = i + 1 < tags.size();
            if (hasNext) {
                Tag next = tags.get(i + 1);
                boolean endOfParams = tag.name.equals("param") && !next.name.equals("param");
                boolean afterReturn = tag.name.equals("return");
                if (endOfParams || afterReturn) {
                    out.add("");
                }
            }
        }

        StringBuilder sb = new StringBuilder("/**");
        for (String line : out) {
            sb.append(lineSeparator).append(line.isEmpty() ? " *" : " * " + line);
        }
        sb.append(lineSeparator).append(" */");
        return sb.toString();
    }

    private static String stripPrefix(String raw) {
        Matcher m = LINE_PREFIX.matcher(raw);
        String line = m.find() ? raw.substring(m.end()) : raw.trim();
        return line.stripTrailing();
    }

    private static String normalizeTagLine(String line) {
        Matcher named = NAMED_TAG.matcher(line);
        if (named.matches()) {
            return "@" + named.group(1) + " " + named.group(2) + " " + named.group(3);
        }
        Matcher tag = TAG.matcher(line);
        if (tag.matches() && !tag.group(2).isBlank()) {
            return "@" + tag.group(1) + " " + tag.group(2).strip();
        }
        return line;
    }

    private static final class Tag {
        final String name;
        final List<String> lines = new ArrayList<>();

        Tag(String name, String firstLine) {
            this.name = name;
            lines.add(firstLine);
        }
    }
}
