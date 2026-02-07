package org.benf.cfr.test;

import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.benf.cfr.test.DecompilationTestImplementation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecompilationFullTest {
    private static final boolean CREATE_EXPECTED_DATA_IF_MISSING = false;
    private static final Path TEST_DATA_ROOT_DIR;
    private static final Path TEST_SPECS_DIR;
    private static final Path TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR;
    static {
        Path decompilationTestDir = Paths.get("decompilation-test");
        TEST_DATA_ROOT_DIR = decompilationTestDir.resolve("test-data");
        TEST_SPECS_DIR = decompilationTestDir.resolve("test-specs");
        TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR = decompilationTestDir.resolve("test-data-expected-output");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allClassFiles")
    void classFile(Path classFilePath, Map<String, String> cfrOptionsDict, Path output, String fileName) throws IOException {
        assertClassFile(classFilePath, cfrOptionsDict, output, fileName);
    }

    static Stream<Arguments> allClassFiles() throws IOException {
        String version = "java_16";
        Path baseDir = TEST_DATA_ROOT_DIR.resolve("precompiled_tests").resolve(version);

        return Files.walk(baseDir)
            .filter(p -> p.toString().endsWith(".class"))
            .map(classFile -> {
                Map<String, String> options = Map.of(); // 默认空 options
                Path output = TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.resolve(version);

                Path relativePath = baseDir.relativize(classFile);
                String outputFileName = relativePath
                    .toString()
                    .replace(File.separatorChar, '.')
                    .replaceAll("\\.class$", ".out");

                return Arguments.of(classFile, options, output, outputFileName);
            });
    }

    static void assertClassFile(Path classFilePath, Map<String, String> baseOptions, Path outputDir, String outputFileName) throws IOException {
        Map<String, String> options = createOptionsMap(baseOptions);
        DecompilationTestImplementation.DecompilationResult decompilationResult = decompile(classFilePath, options);

        boolean createdExpectedFile = false;
        boolean updatedExpectedFile = false;
        List<Path> notUpdatableDueToDecompilationNotes = new ArrayList<>();

        List<SinkReturns.DecompiledMultiVer> decompiledList = decompilationResult.decompiled;
        assertEquals(1, decompiledList.size());
        SinkReturns.DecompiledMultiVer decompiled = decompiledList.get(0);
        assertEquals(0, decompiled.getRuntimeFrom());
        String actualJavaCode = decompiled.getJava();

        Path expectedJavaPath = outputDir.resolve(outputFileName);
//        Path expectedSummaryPath = outputDir.resolve(filePrefix + EXPECTED_SUMMARY_FILE_EXTENSION);
//        Path expectedExceptionsPath = outputDir.resolve(filePrefix + EXPECTED_EXCEPTIONS_FILE_EXTENSION);

        if (!Files.exists(expectedJavaPath)) {
            if (CREATE_EXPECTED_DATA_IF_MISSING) {
                createdExpectedFile = true;
                writeString(expectedJavaPath, actualJavaCode);
            } else {
                throwTestSetupError("Missing file: " + expectedJavaPath + " (Create with -Dcfr.decompilation-test.create-expected)");
            }
        } else {
            DecompilationTestImplementation.DiffCodeResult diffCodeResult = diffCodeAndWriteOnMismatch(expectedJavaPath, actualJavaCode);
            AssertionError assertionError = diffCodeResult.assertionError;
            if (assertionError != null) {
                throw assertionError;
            }

            updatedExpectedFile |= diffCodeResult.updatedExpectedData;
            if (diffCodeResult.decompilationNotesPreventedUpdate) {
                notUpdatableDueToDecompilationNotes.add(expectedJavaPath);
            }
        }

        String actualSummary = decompilationResult.summary;
//        if (!actualSummary.isEmpty() || Files.exists(expectedSummaryPath)) {
//            if (!Files.exists(expectedSummaryPath)) {
//                if (CREATE_EXPECTED_DATA_IF_MISSING) {
//                    createdExpectedFile = true;
//                    writeString(expectedSummaryPath, actualSummary);
//                } else {
//                    throwTestSetupError("Missing file: " + expectedSummaryPath);
//                }
//            } else {
//                if (!assertFileEquals(expectedSummaryPath, actualSummary)) {
//                    updatedExpectedFile = true;
//                }
//            }
//        }

        String actualExceptions = decompilationResult.exceptions;
//        if (!actualExceptions.isEmpty() || Files.exists(expectedExceptionsPath)) {
//            if (!Files.exists(expectedExceptionsPath)) {
//                if (CREATE_EXPECTED_DATA_IF_MISSING) {
//                    createdExpectedFile = true;
//                    writeString(expectedExceptionsPath, actualExceptions);
//                } else {
//                    throwTestSetupError("Missing file: " + expectedExceptionsPath);
//                }
//            } else {
//                if (!assertFileEquals(expectedExceptionsPath, actualExceptions)) {
//                    updatedExpectedFile = true;
//                }
//            }
//        }

        if (createdExpectedFile) {
            failCreatedMissingExpectedData();
        }
        if (updatedExpectedFile) {
            failUpdatedExpectedData(notUpdatableDueToDecompilationNotes);
        }
        if (!notUpdatableDueToDecompilationNotes.isEmpty()) {
            failNotUpdatableDueToDecompilationNotes(notUpdatableDueToDecompilationNotes);
        }
    }

    private static Map<String, String> createOptionsMap(Map<String, String> baseOptions) {
        Map<String, String> options = new HashMap<>();

        // Do not include CFR version, would otherwise cause source changes when switching CFR version
        options.put(OptionsImpl.SHOW_CFR_VERSION.getName(), "false");
        // Don't dump exception stack traces because they might differ depending on how these tests are started (different IDEs, Maven, ...)
        options.put(OptionsImpl.DUMP_EXCEPTION_STACK_TRACE.getName(), "false");

        for (Map.Entry<String, String> kvp : baseOptions.entrySet()) {
            options.put(kvp.getKey(), kvp.getValue());
        }
        return options;
    }

}
