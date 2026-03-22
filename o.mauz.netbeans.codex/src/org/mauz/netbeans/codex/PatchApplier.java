package org.mauz.netbeans.codex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies unified diffs to the selected text and reinserts the result into the
 * original file content.
 */
final class PatchApplier {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private PatchApplier() {
    }

    static String applyToSelection(String originalSelection, String patchText) throws IOException {
        String normalizedOriginal = normalizeNewlines(originalSelection);
        List<String> originalLines = splitLines(normalizedOriginal);
        boolean trailingNewline = normalizedOriginal.endsWith("\n");

        List<String> patchedLines = new ArrayList<>();
        int cursor = 0;
        boolean sawHunk = false;

        String[] diffLines = normalizeNewlines(patchText).split("\n", -1);
        int index = 0;
        while (index < diffLines.length) {
            String line = diffLines[index];
            if (!line.startsWith("@@")) {
                index++;
                continue;
            }

            Matcher matcher = HUNK_HEADER.matcher(line);
            if (!matcher.matches()) {
                throw new IOException("Unsupported hunk header: " + line);
            }
            sawHunk = true;

            int oldStart = Integer.parseInt(matcher.group(1));
            int targetIndex = Math.max(0, oldStart - 1);
            while (cursor < targetIndex && cursor < originalLines.size()) {
                patchedLines.add(originalLines.get(cursor));
                cursor++;
            }

            index++;
            while (index < diffLines.length) {
                String hunkLine = diffLines[index];
                if (hunkLine.startsWith("@@")) {
                    break;
                }
                if (hunkLine.startsWith("\\ No newline at end of file")) {
                    trailingNewline = false;
                    index++;
                    continue;
                }
                if (hunkLine.isEmpty()) {
                    throw new IOException("Malformed diff hunk.");
                }

                char prefix = hunkLine.charAt(0);
                String content = hunkLine.substring(1);
                switch (prefix) {
                    case ' ' -> {
                        requireMatch(originalLines, cursor, content, "context");
                        patchedLines.add(content);
                        cursor++;
                    }
                    case '-' -> {
                        requireMatch(originalLines, cursor, content, "removal");
                        cursor++;
                    }
                    case '+' -> patchedLines.add(content);
                    default -> throw new IOException("Unsupported diff line: " + hunkLine);
                }
                index++;
            }
        }

        if (!sawHunk) {
            throw new IOException("No unified diff hunks were found in the Codex output.");
        }

        while (cursor < originalLines.size()) {
            patchedLines.add(originalLines.get(cursor));
            cursor++;
        }

        return joinLines(patchedLines, trailingNewline);
    }

    static String replaceSelectionInFile(
            String fileText,
            String originalSelection,
            String replacement,
            int selectionStart,
            int selectionEnd
    ) throws IOException {
        if (selectionStart >= 0
                && selectionEnd >= selectionStart
                && selectionEnd <= fileText.length()
                && fileText.substring(selectionStart, selectionEnd).equals(originalSelection)) {
            return fileText.substring(0, selectionStart) + replacement + fileText.substring(selectionEnd);
        }

        int fallbackOffset = fileText.indexOf(originalSelection);
        if (fallbackOffset >= 0) {
            return fileText.substring(0, fallbackOffset)
                    + replacement
                    + fileText.substring(fallbackOffset + originalSelection.length());
        }

        throw new IOException("The original selected text no longer matches the current file contents.");
    }

    private static void requireMatch(List<String> originalLines, int cursor, String expected, String kind)
            throws IOException {
        if (cursor >= originalLines.size()) {
            throw new IOException("Patch " + kind + " exceeds the original selection.");
        }
        if (!originalLines.get(cursor).equals(expected)) {
            throw new IOException("Patch " + kind + " does not match the original selection.");
        }
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }

        String[] parts = text.split("\n", -1);
        int length = parts.length;
        if (text.endsWith("\n")) {
            length--;
        }
        for (int i = 0; i < length; i++) {
            lines.add(parts[i]);
        }
        return lines;
    }

    private static String joinLines(List<String> lines, boolean trailingNewline) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        if (trailingNewline) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
