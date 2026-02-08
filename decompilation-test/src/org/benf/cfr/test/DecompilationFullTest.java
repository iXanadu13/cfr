package org.benf.cfr.test;

import com.github.ixanadu13.Driver;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.util.CfrVersionInfo;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.benf.cfr.test.DecompilationTestImplementation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecompilationFullTest {
    private static final boolean CREATE_EXPECTED_DATA_IF_MISSING = false;
    private static final Path TEST_DATA_ROOT_DIR;
    private static final Path TEST_SPECS_DIR;
    static final Path TEST_OUTPUT_DIR;
    private static final Path TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR;
    static {
        Path decompilationTestDir = Paths.get("decompilation-test");
        TEST_DATA_ROOT_DIR = decompilationTestDir.resolve("test-data");
        TEST_SPECS_DIR = decompilationTestDir.resolve("test-specs");
        TEST_OUTPUT_DIR = decompilationTestDir.resolve("output");
        TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR = decompilationTestDir.resolve("test-data-expected-output");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("allClassFiles")
    void classFile(Path classFilePath, Map<String, String> cfrOptionsDict, Path output, String fileName) throws IOException {
        assertClassFile(classFilePath, cfrOptionsDict, output, fileName);
    }

    @TestFactory
    Stream<DynamicNode> jarFile() throws IOException {
        AtomicInteger jarIndex = new AtomicInteger(1);

        return allJarFiles().map(args -> {
            Path jarFile = (Path) args.get()[0];
            Map<String, String> options = (Map<String, String>) args.get()[1];
            Path output = (Path) args.get()[2];
            String outputDirName = (String) args.get()[3];

            return DynamicContainer.dynamicContainer(
                "[" + jarIndex.getAndIncrement() + "] " + jarFile.getFileName().toString(),
                classLevelTests(jarFile, options, output, outputDirName)
            );
        });
    }

    @AfterAll
    static void generateSummaryPatch() throws IOException {
        DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String fileName = simpleDateFormat.format(new Date()) + ".patch";
        Path summaryPatch = TEST_OUTPUT_DIR.resolve("test-diff").resolve(fileName);
        DiffCollector.writeSummaryPatch(summaryPatch);

        if (DiffCollector.hasDiff()) {
            System.err.println("❌ Test diffs written to " + summaryPatch);
        }
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

    static Stream<Arguments> allJarFiles() throws IOException {
        Path baseDir = TEST_DATA_ROOT_DIR.resolve("jars");

        return Files.walk(baseDir)
            .filter(p -> p.toString().endsWith(".jar"))
            .map(jarFile -> {
                Map<String, String> options = Map.of(); // 默认空 options
                Path output = TEST_DATA_EXPECTED_OUTPUT_ROOT_DIR.resolve("jars");

                Path relativePath = baseDir.relativize(jarFile);
                String outputDirName = relativePath
                    .toString()
                    .replace(File.separatorChar, '.')
                    .replaceAll("\\.jar$", "");

                return Arguments.of(jarFile, options, output, outputDirName);
            });
    }

    static DecompilationResult decompileJar(Path path, Map<String, String> options) {
        StringWriter summaryOutput = new StringWriter();
        OutputSinkFactory.Sink<String> summarySink = summaryOutput::append; // Messages include line terminator, therefore only print

        StringWriter exceptionsOutput = new StringWriter();
        OutputSinkFactory.Sink<SinkReturns.ExceptionMessage> exceptionSink = exceptionMessage -> {
            exceptionsOutput.append(exceptionMessage.getPath()).append('\n');
            exceptionsOutput.append(exceptionMessage.getMessage()).append('\n');

            Exception exception = exceptionMessage.getThrownException();
            exceptionsOutput
                .append(exception.getClass().getName())
                .append(": ")
                .append(exception.getMessage())
                .append("\n\n");
        };

        List<SinkReturns.DecompiledMultiVer> decompiledList = new ArrayList<>();
        OutputSinkFactory.Sink<SinkReturns.DecompiledMultiVer> decompiledSourceSink = decompiledList::add;

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                switch (sinkType) {
                    case JAVA:
                        return Collections.singletonList(SinkClass.DECOMPILED_MULTIVER);
                    case EXCEPTION:
                        return Collections.singletonList(SinkClass.EXCEPTION_MESSAGE);
                    case SUMMARY:
                        return Collections.singletonList(SinkClass.STRING);
                    default:
                        // Required to always support STRING
                        return Collections.singletonList(SinkClass.STRING);
                }
            }

            @SuppressWarnings("unchecked")
            private <T> Sink<T> castSink(Sink<?> sink) {
                return (Sink<T>) sink;
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                switch (sinkType) {
                    case JAVA:
                        if (sinkClass != SinkClass.DECOMPILED_MULTIVER) {
                            throw new IllegalArgumentException("Sink class " + sinkClass + " is not supported for decompiled output");
                        }
                        return castSink(decompiledSourceSink);
                    case EXCEPTION:
                        switch (sinkClass) {
                            case EXCEPTION_MESSAGE:
                                return castSink(exceptionSink);
                            // Always have to support STRING
                            case STRING:
                                return castSink(summarySink);
                            default:
                                throw new IllegalArgumentException("Sink factory does not support " + sinkClass);
                        }
                    case SUMMARY:
                        return castSink(summarySink);
                    default:
                        return ignored -> { };
                }
            }
        };

        Driver.JarSource cfs = Driver.fromPath(path);
        CfrDriver driver = new CfrDriver.Builder()
            .withClassFileSource(cfs)
            .withOptions(options)
            .withOutputSink(sinkFactory)
            .build();

        for (String className : cfs.getClasses().keySet()) {
            driver.analyse(List.of(className));
        }

        // Replace version information and file path to prevent changes in the output
        String summary = summaryOutput.toString().replace(CfrVersionInfo.VERSION_INFO, "<version>");
//            .replace(pathString, "<path>/" + path.getFileName().toString());
        return new DecompilationResult(summary, exceptionsOutput.toString(), decompiledList);
    }

    static Stream<DynamicTest> classLevelTests(
        Path jarFilePath,
        Map<String, String> baseOptions,
        Path outputDir,
        String outputDirName
    ) {
        Map<String, String> options = createOptionsMap(baseOptions);
        DecompilationTestImplementation.DecompilationResult decompilationResult = decompileJar(jarFilePath, options);
        Path expectedDirPath = outputDir.resolve(outputDirName);

        AtomicInteger clsIndex = new AtomicInteger(1);

        return decompilationResult.decompiled.stream().map(decompiled -> {
            String outputName =
                decompiled.getPackageName() + "." + decompiled.getClassName();

            return DynamicTest.dynamicTest(
                "[" + clsIndex.getAndIncrement() + "] " + outputName,
                () -> assertSingleClass(expectedDirPath, outputName, decompiled)
            );
        });
    }

    static void assertSingleClass(
        Path expectedDirPath,
        String outputName,
        SinkReturns.DecompiledMultiVer decompiled
    ) throws IOException {

        boolean createdExpectedFile = false;
        boolean updatedExpectedFile = false;
        List<Path> notUpdatableDueToDecompilationNotes = new ArrayList<>();

        String fileName = outputName + ".out";
        Path expectedJavaPath = expectedDirPath.resolve(fileName);
        String actualJavaCode = decompiled.getJava();
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

            updatedExpectedFile = diffCodeResult.updatedExpectedData;
            if (diffCodeResult.decompilationNotesPreventedUpdate) {
                notUpdatableDueToDecompilationNotes.add(expectedJavaPath);
            }
        }
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
