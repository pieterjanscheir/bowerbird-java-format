/*
 * Copyright 2016 Google Inc.
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

import static io.github.pieterjanscheir.bowerbird.java.FileBasedTests.assumeJavaVersionForTest;
import static io.github.pieterjanscheir.bowerbird.java.FileBasedTests.isRecreate;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.palantir.javaformat.java.JavaFormatterOptions.Style;
import io.github.pieterjanscheir.bowerbird.Newlines;
import io.github.pieterjanscheir.bowerbird.jupiter.ParameterizedClass;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Bowerbird: runs the Bowerbird style against {@code bowerbird-testdata}, the same way {@link FormatterIntegrationTest}
 * runs the Palantir style against {@code testdata}. Run with {@code -Drecreate=true} to regenerate the outputs.
 */
@ExtendWith(ParameterizedClass.class)
@Execution(ExecutionMode.CONCURRENT)
public class BowerbirdIntegrationTest {

    private static FileBasedTests tests = new FileBasedTests(BowerbirdIntegrationTest.class, "bowerbird-testdata");

    @ParameterizedClass.Parameters(name = "{0}")
    public static List<Object[]> data() throws IOException {
        return tests.paramsAsNameInputOutput();
    }

    private final String name;
    private final String input;
    private final String expected;
    private final String separator;

    public BowerbirdIntegrationTest(String name, String input, String expected) {
        this.name = name;
        this.input = input;
        this.expected = expected;
        this.separator = isRecreate() ? null : Newlines.getLineEnding(expected);
    }

    /**
     * If enabled, then {@link DebugRenderer} will produce an output at {@link DebugRenderer#getOutputFile()}. This can
     * then be viewed in a browser by running the following once (after which it will auto-reload whenever the debug
     * output file changes):
     *
     * <pre>
     * cd debugger
     * yarn start
     * </pre>
     *
     * <p>Warning: don't turn this on for all tests. The debugger will always write to the same file.
     */
    private static boolean isDebugMode() {
        return Boolean.getBoolean("debugOutput");
    }

    @TestTemplate
    public void format() {
        assumeJavaVersionForTest(name);
        try {
            Formatter formatter = createFormatter();
            String output = formatter.formatSourceAndFixImports(input);
            output = StringWrapper.wrap(rules().lineLength(), output, formatter);
            if (isRecreate()) {
                tests.writeFormatterOutput(name, output);
                return;
            }
            assertThat(output).describedAs("bad output for " + name).isEqualTo(expected);
        } catch (FormatterException e) {
            throw new RuntimeException(String.format("Formatter crashed on %s", name), e);
        }
    }

    private Formatter createFormatter() {
        return new Formatter(
                JavaFormatterOptions.builder().style(Style.PALANTIR).build(), isDebugMode(), rules());
    }

    /**
     * The rules for this test case: {@code bowerbird-testdata/<name>.yml} when it exists, so a test case can switch
     * rules on or off, and Bowerbird's defaults otherwise.
     */
    private BowerbirdRules rules() {
        String resource = "bowerbird-testdata/" + name + ".yml";
        try (InputStream in = BowerbirdIntegrationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return BowerbirdRules.defaults();
            }
            return BowerbirdConfig.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestTemplate
    public void idempotentLF() {
        assumeJavaVersionForTest(name);
        Assumptions.assumeFalse(isRecreate(), "Not running when recreating test outputs");
        try {
            String mangled = expected.replace(separator, "\n");
            String output = createFormatter().formatSourceAndFixImports(mangled);
            assertThat(output).describedAs("bad output for " + name).isEqualTo(mangled);
        } catch (FormatterException e) {
            throw new RuntimeException(String.format("Formatter crashed on %s", name), e);
        }
    }

    @TestTemplate
    public void idempotentCR() {
        assumeJavaVersionForTest(name);
        Assumptions.assumeFalse(isRecreate(), "Not running when recreating test outputs");
        try {
            String mangled = expected.replace(separator, "\r");
            String output = createFormatter().formatSourceAndFixImports(mangled);
            assertThat(output).describedAs("bad output for " + name).isEqualTo(mangled);
        } catch (FormatterException e) {
            throw new RuntimeException(String.format("Formatter crashed on %s", name), e);
        }
    }

    @TestTemplate
    public void idempotentCRLF() {
        assumeJavaVersionForTest(name);
        Assumptions.assumeFalse(isRecreate(), "Not running when recreating test outputs");
        try {
            String mangled = expected.replace(separator, "\r\n");
            String output = createFormatter().formatSourceAndFixImports(mangled);
            assertThat(output).describedAs("bad output for " + name).isEqualTo(mangled);
        } catch (FormatterException e) {
            throw new RuntimeException(String.format("Formatter crashed on %s", name), e);
        }
    }
}
