package com.github.ixanadu13;

import lombok.Getter;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class Driver {
    private static Map<String, String> options = new HashMap<>();
    private final ClassFileSource cfs;

    public Driver(ClassFileSource classFileSource) {
        this.cfs = classFileSource;
    }

    public String decompile(String className) {
        HashMap<String, String> ops = new HashMap<>(options);

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
        final String[] result = new String[1];
        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<OutputSinkFactory.SinkClass> getSupportedSinks(OutputSinkFactory.SinkType
            sinkType, Collection< OutputSinkFactory.SinkClass > collection) {
                return Arrays.asList(OutputSinkFactory.SinkClass.values());
            }

            @SuppressWarnings("unchecked")
            private <T> Sink<T> castSink(Sink<?> sink) {
                return (OutputSinkFactory.Sink<T>) sink;
            }
            @Override
            public <T> Sink<T> getSink(OutputSinkFactory.SinkType sinkType, OutputSinkFactory.SinkClass sinkClass) {
                switch (sinkType) {
                    case JAVA:
                        return this::setDecompile;
                    case EXCEPTION:
                        switch (sinkClass) {
                            case EXCEPTION_MESSAGE:
                                return castSink(exceptionSink);
                            // Always have to support STRING
                            case STRING:
                                return castSink(summarySink);
//                                return t -> {};
                            default:
                                throw new IllegalArgumentException("Sink factory does not support " + sinkClass);
                        }
                    case LINENUMBER:
                    case SUMMARY:
                    case PROGRESS:
                    default:
                        return t -> {};
                }
            }
            private <T> void setDecompile(T decompile){
                result[0] = decompile.toString();
            }

        };
        CfrDriver cfrDriver = new CfrDriver.Builder()
            .withClassFileSource(cfs)
            .withOptions(ops)
            .withOutputSink(sinkFactory)
            .build();

        cfrDriver.analyse(List.of(className));
        Log.error(exceptionsOutput.toString());
        Log.info(summaryOutput.toString());
        return result[0];
    }

    public static JarSource fromPath(Path jarPath) {
        return fromPaths(List.of(jarPath));
    }
    public static JarSource fromPaths(Path... jarPath) {
        return fromPaths(List.of(jarPath));
    }
    public static JarSource fromPaths(String... patterns) {
        return fromPaths(resolvePaths(patterns));
    }

    public static JarSource fromPaths(List<Path> pathList) {
        Function<byte[], String> getInnerName = (bytes) -> {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            return cn.name;
        };

        Map<String, byte[]> classes = new HashMap<>();
        for (Path path : pathList) {
            File file = path.toFile();
            if (!file.exists()) {
                Log.error("Path not found: " + path);
                continue;
            }

            if (file.isFile() && file.getName().endsWith(".class")) {
                // 处理单个 .class 文件
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String className = getInnerName.apply(bytes);
                    if (className == null) {
                        Log.error("Missing class name with `%s`, skipping it...", path);
                    } else {
                        classes.put(className, bytes);
                    }
                } catch (IOException e) {
                    Log.error("Failed to read .class file: " + file.getAbsolutePath(), e);
                }
            } else if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"))) {
                // 处理 .jar/.zip 文件
                try (JarFile jarFile = new JarFile(file)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            String entryName = entry.getName();
                            try (InputStream is = jarFile.getInputStream(entry)) {
                                byte[] bytes = is.readAllBytes();
                                String className = getInnerName.apply(bytes);
                                if (className == null) {
                                    Log.error("Missing class name with `%s`, skipping it...", path);
                                } else {
                                    classes.put(className, bytes);
                                }
                            } catch (IOException e) {
                                Log.error("Failed to read .class entry: " + entryName, e);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.error("Failed to read JAR file: " + file.getAbsolutePath(), e);
                }
            } else {
                Log.error("Unsupported file type: " + file.getAbsolutePath());
            }
        }

        return new JarSource(classes);
    }

    private static List<Path> resolvePaths(String... patterns) {
        List<Path> result = new ArrayList<>();

        for (String pattern : patterns) {
            // 没有通配符，直接当普通路径
            if (!hasWildcard(pattern)) {
                result.add(Paths.get(pattern));
                continue;
            }

            Path baseDir = Paths.get(".").toAbsolutePath().normalize();
            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern);

            try (Stream<Path> stream = Files.walk(baseDir)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(baseDir.relativize(p)))
                    .forEach(result::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return result;
    }

    private static boolean hasWildcard(String path) {
        return path.contains("*") || path.contains("?");
    }

    public static class JarSource implements ClassFileSource {
        /**
         * p/k/g/A -> byte[]
         */
        @Getter
        private final Map<String, byte[]> classes;

        public JarSource(Map<String, byte[]> classes) {
            this.classes = classes;
        }
        @Override
        public void informAnalysisRelativePathDetail(String s, String s1) {

        }

        @Override
        public Collection<String> addJar(String s) {
            return Collections.emptySet();
        }

        @Override
        public String getPossiblyRenamedPath(String s) {
            return s;
        }

        @Override
        public Pair<byte[], String> getClassFileContent(String path) throws IOException {
            String name = path.substring(0, path.length() - 6); // ".class".length == 6
            byte[] code = classes.get(name);
            if (code == null) {
//                    Log.debug("CFR is loading `%s` from the env.", path);
                ClassReader r = ClassUtil.fromRuntime(name);
                if (r != null) {
                    code = r.b;
                }
            }
            if (code == null) {
                Log.debug("CFR tried to load `%s`, but not found.", path);
                return null;
            }
            return Pair.make(code, path);
        }
    }

}
