package org.benf.cfr.test;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.benf.cfr.test.DecompilationTestImplementation.ClassFileTestDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;

import static org.benf.cfr.test.DecompilationFullTest.TEST_OUTPUT_DIR;

/**
 * CFR decompilation test. For increased readability this class only contains the JUnit test methods;
 * the actual implementation is in {@link DecompilationTestImplementation}.
 */
class DecompilationTest {
    @ParameterizedTest(name = "[{index}] {0}")
    @ClassFileTestDataSource("classes.xml")
    void classFile(Path classFilePath, Map<String, String> cfrOptionsDict, Path output, String outputPrefix) throws IOException {
        DecompilationTestImplementation.assertClassFile(classFilePath, cfrOptionsDict, output, outputPrefix);
    }

    @AfterAll
    static void generateSummaryPatch() throws IOException {
        DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String fileName = "DecompilationTest-" + simpleDateFormat.format(new Date()) + ".patch";
        Path summaryPatch = TEST_OUTPUT_DIR.resolve("test-diff").resolve(fileName);
        DiffCollector.writeSummaryPatch(summaryPatch);

        if (DiffCollector.hasDiff()) {
            System.err.println("❌ Test diffs written to " + summaryPatch);
        }
    }
}
