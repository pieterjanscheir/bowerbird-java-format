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

package io.github.pieterjanscheir.bowerbird.java;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.palantir.javaformat.java.FormatterException;
import io.github.pieterjanscheir.bowerbird.Input;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Bowerbird: finds the regions the formatter leaves as written, between a comment containing
 * {@link BowerbirdRules#formatterOffTag()} and one containing {@link BowerbirdRules#formatterOnTag()} (issue #22). A
 * region runs from the start of the off comment to the end of the on comment; an off comment without an on comment
 * runs to the end of the file. An off tag inside a region is ignored, as in IntelliJ. Only comments count, so the tag
 * inside a string literal does nothing.
 */
final class FormatterTags {
    private FormatterTags() {}

    /** The regions in {@code input}; empty when {@code rules} is null or doesn't enable formatter tags. */
    static RangeSet<Integer> offRegions(JavaInput input, @Nullable BowerbirdRules rules) {
        RangeSet<Integer> regions = TreeRangeSet.create();
        if (rules == null || !rules.formatterTags()) {
            return regions;
        }
        // A comment can be reachable from two tokens, so collect them by position first.
        Map<Integer, Input.Tok> comments = new TreeMap<>();
        for (Input.Token token : input.getTokens()) {
            for (Input.Tok tok : token.getToksBefore()) {
                if (tok.isComment()) {
                    comments.put(tok.getPosition(), tok);
                }
            }
            for (Input.Tok tok : token.getToksAfter()) {
                if (tok.isComment()) {
                    comments.put(tok.getPosition(), tok);
                }
            }
        }
        Integer start = null;
        for (Input.Tok comment : comments.values()) {
            String text = comment.getOriginalText();
            if (start == null && text.contains(rules.formatterOffTag())) {
                start = comment.getPosition();
            } else if (start != null && text.contains(rules.formatterOnTag())) {
                regions.add(Range.closedOpen(start, comment.getPosition() + comment.length()));
                start = null;
            }
        }
        if (start != null) {
            regions.add(Range.closedOpen(start, input.getText().length()));
        }
        return regions;
    }

    /** The regions in {@code text}, or none when it can't be read as Java (formatting reports that error itself). */
    static RangeSet<Integer> offRegions(String text, @Nullable BowerbirdRules rules) {
        if (rules == null || !rules.formatterTags()) {
            return TreeRangeSet.create();
        }
        try {
            return offRegions(new JavaInput(text), rules);
        } catch (FormatterException e) {
            return TreeRangeSet.create();
        }
    }

    /**
     * {@code output} (the formatted {@code input}) with each region replaced by the region's original text from
     * {@code input}, or {@code null} when {@code output} doesn't have the same number of regions.
     */
    @Nullable
    static String restore(String input, RangeSet<Integer> inputRegions, String output, BowerbirdRules rules) {
        List<Range<Integer>> before = new ArrayList<>(inputRegions.asRanges());
        List<Range<Integer>> after = new ArrayList<>(offRegions(output, rules).asRanges());
        if (before.size() != after.size()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int last = 0;
        for (int i = 0; i < before.size(); i++) {
            result.append(output, last, after.get(i).lowerEndpoint());
            result.append(input, before.get(i).lowerEndpoint(), before.get(i).upperEndpoint());
            last = after.get(i).upperEndpoint();
        }
        return result.append(output.substring(last)).toString();
    }

    /**
     * Whether replacing {@code range} would change text inside one of {@code regions}. An empty range is an insertion,
     * which only changes a region when it falls strictly inside it.
     */
    static boolean touches(RangeSet<Integer> regions, Range<Integer> range) {
        if (!range.isEmpty()) {
            return !regions.subRangeSet(range).isEmpty();
        }
        int position = range.lowerEndpoint();
        Range<Integer> region = regions.rangeContaining(position);
        return region != null && position > region.lowerEndpoint();
    }

    /** Whether an {@code import} declaration starts inside one of the regions, so imports must be left alone. */
    static boolean coversImports(String text, @Nullable BowerbirdRules rules) {
        if (rules == null || !rules.formatterTags()) {
            return false;
        }
        try {
            JavaInput input = new JavaInput(text);
            RangeSet<Integer> regions = offRegions(input, rules);
            if (regions.isEmpty()) {
                return false;
            }
            for (Input.Token token : input.getTokens()) {
                if (token.getTok().getText().equals("import")
                        && regions.contains(token.getTok().getPosition())) {
                    return true;
                }
            }
            return false;
        } catch (FormatterException e) {
            return false;
        }
    }
}
