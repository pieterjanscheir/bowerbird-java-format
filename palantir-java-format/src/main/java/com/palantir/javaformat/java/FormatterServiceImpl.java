/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.javaformat.java;

import com.google.auto.service.AutoService;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import java.util.Collection;
import java.util.function.Supplier;

@AutoService(FormatterService.class)
public final class FormatterServiceImpl implements FormatterService {

    private final Supplier<Formatter> formatter;

    /**
     * Bowerbird: formats with the rules in {@code bowerbird.yml} on the formatter's classpath, or Bowerbird's defaults
     * without one. The Gradle, Spotless and IntelliJ plugins all load this class from the {@code palantirJavaFormat}
     * classpath.
     *
     * <p>The file is read on first use, not here: the plugins create this class through {@link java.util.ServiceLoader},
     * which hides an exception from the constructor behind "could not be instantiated". Thrown while formatting, the
     * message about the invalid setting reaches the user.
     */
    public FormatterServiceImpl() {
        this.formatter = Suppliers.memoize(
                () -> createFormatter(BowerbirdConfig.load(FormatterServiceImpl.class.getClassLoader())));
    }

    /** Bowerbird: formats with {@code rules}. */
    public FormatterServiceImpl(BowerbirdRules rules) {
        Formatter created = createFormatter(rules);
        this.formatter = () -> created;
    }

    private static Formatter createFormatter(BowerbirdRules rules) {
        // Style.PALANTIR only sets what the rules don't cover; it exists in every SPI version, unlike a Bowerbird
        // style, which matters because the plugins load the SPI classes from their own jars.
        JavaFormatterOptions options =
                JavaFormatterOptions.builder().style(Style.PALANTIR).build();
        return Formatter.createFormatter(options, rules);
    }

    @Override
    public ImmutableList<Replacement> getFormatReplacements(String text, Collection<Range<Integer>> toRanges)
            throws FormatterException {
        return formatter.get().getFormatReplacements(text, toRanges);
    }

    @Override
    public String formatSourceReflowStringsAndFixImports(String input) throws FormatterException {
        return formatter.get().formatSourceAndFixImports(input);
    }

    @Override
    public String fixImports(String input) throws FormatterException {
        return formatter.get().fixImports(input);
    }
}
