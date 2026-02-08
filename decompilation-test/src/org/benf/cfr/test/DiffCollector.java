package org.benf.cfr.test;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DiffCollector {
    private static final List<String> ALL_DIFFS = new ArrayList<>();

    public static synchronized void addDiff(Path sourceFile, String fileName, List<String> expectedLines, Patch<String> diff) {
        ALL_DIFFS.add("### " + sourceFile);
        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(fileName, fileName, expectedLines, diff, 5);
        ALL_DIFFS.addAll(unifiedDiff);
        ALL_DIFFS.add(""); // 空行分隔
    }

    public static void writeSummaryPatch(Path output) throws IOException {
        if (ALL_DIFFS.isEmpty()) return;

        Files.createDirectories(output.getParent());
        Files.write(output, ALL_DIFFS);
    }

    public static boolean hasDiff() {
        return !ALL_DIFFS.isEmpty();
    }
}
