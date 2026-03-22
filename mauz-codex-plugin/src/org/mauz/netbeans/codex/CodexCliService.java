package org.mauz.netbeans.codex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the external Codex CLI invocation so the action stays focused on
 * IDE integration.
 */
final class CodexCliService {

    private static final String DEFAULT_INSTRUCTION =
            "Generate a unified diff patch for the provided file contents. "
            + "Return only the patch text.";

    private CodexCliService() {
    }

    static CodexCliResult runPatch(AskMauzCodexAction.EditorInvocation invocation) {
        Path tempFile = null;
        try {
            // Codex is invoked on a temporary file so the CLI can operate on a real path.
            tempFile = Files.createTempFile(
                    "mauz-codex-selection-",
                    extensionOf(invocation.originalFile().getFileName().toString())
            );
            Files.writeString(tempFile, invocation.selectedCode(), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add("codex");
            command.add("patch");
            command.add(tempFile.toAbsolutePath().toString());
            command.add("--instruction");
            command.add(buildInstruction(invocation));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            return new CodexCliResult(command, tempFile, exitCode, output, null);
        } catch (IOException ex) {
            return new CodexCliResult(
                    List.of("codex", "patch"),
                    tempFile,
                    -1,
                    "",
                    "Failed to start the Codex CLI. Ensure `codex` is on PATH.\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CodexCliResult(
                    List.of("codex", "patch"),
                    tempFile,
                    -1,
                    "",
                    "The Codex CLI invocation was interrupted."
            );
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    // Temporary file cleanup failure is non-fatal for the IDE flow.
                }
            }
        }
    }

    private static String buildInstruction(AskMauzCodexAction.EditorInvocation invocation) {
        return DEFAULT_INSTRUCTION
                + " The selected code came from " + invocation.originalFile().getFileName() + "."
                + " Keep the patch scoped to the selected region.";
    }

    private static String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index) : ".txt";
    }

    static final class CodexCliResult {

        private final List<String> command;
        private final Path tempFile;
        private final int exitCode;
        private final String output;
        private final String error;

        CodexCliResult(List<String> command, Path tempFile, int exitCode, String output, String error) {
            this.command = List.copyOf(command);
            this.tempFile = tempFile;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error;
        }

        String formatForDisplay() {
            StringBuilder sb = new StringBuilder(512);
            sb.append("Command:\n");
            sb.append(String.join(" ", command)).append('\n').append('\n');
            if (tempFile != null) {
                sb.append("Temporary file:\n");
                sb.append(tempFile.toAbsolutePath()).append('\n').append('\n');
            }
            sb.append("Exit code: ").append(exitCode).append('\n').append('\n');

            if (error != null && !error.isBlank()) {
                sb.append("Error:\n");
                sb.append(error).append('\n').append('\n');
            }

            sb.append("Codex output:\n");
            if (output.isBlank()) {
                sb.append("<no output>");
            } else {
                sb.append(output.strip());
            }
            sb.append('\n');
            return sb.toString();
        }

        boolean canApplyPatch() {
            return error == null && exitCode == 0 && !output.isBlank();
        }

        String patchText() {
            return output;
        }
    }
}
