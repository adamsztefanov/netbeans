package org.mauz.netbeans.codex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            "Edit this file and return only the complete updated file contents. "
            + "Do not return a diff. "
            + "Do not wrap the answer in markdown fences.";

    private CodexCliService() {
    }

    static CodexCliResult runEdit(AskMauzCodexAction.EditorInvocation invocation) {
        Path promptFile = null;
        Path responseFile = null;
        try {
            promptFile = Files.createTempFile(
                    "mauz-codex-file-",
                    extensionOf(invocation.originalFile().getFileName().toString())
            );
            responseFile = Files.createTempFile(".mauz-codex-response-", extensionOf(invocation.originalFile().getFileName().toString()));

            String prompt = buildPrompt(invocation);
            Files.writeString(promptFile, invocation.documentText(), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add("C:\\Users\\mauz\\AppData\\Roaming\\npm\\codex.cmd");
            command.add("exec");
            command.add("--skip-git-repo-check");
            command.add("--color");
            command.add("never");
            command.add("--output-last-message");
            command.add(responseFile.toAbsolutePath().toString());
            command.add("-");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (OutputStream output = process.getOutputStream()) {
                output.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            String replacementText = "";
            if (responseFile != null && Files.exists(responseFile)) {
                replacementText = Files.readString(responseFile, StandardCharsets.UTF_8);
            }
            return new CodexCliResult(command, promptFile, exitCode, output, replacementText, null);
        } catch (IOException ex) {
            return new CodexCliResult(
                    List.of("codex", "exec"),
                    promptFile,
                    -1,
                    "",
                    "",
                    "Failed to start the Codex CLI. Ensure `codex` is on PATH.\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CodexCliResult(
                    List.of("codex", "exec"),
                    promptFile,
                    -1,
                    "",
                    "",
                    "The Codex CLI invocation was interrupted."
            );
        } finally {
            if (promptFile != null) {
                try {
                    Files.deleteIfExists(promptFile);
                } catch (IOException ex) {
                    // Temporary file cleanup failure is non-fatal for the IDE flow.
                }
            }
            if (responseFile != null) {
                try {
                    Files.deleteIfExists(responseFile);
                } catch (IOException ex) {
                    // Temporary file cleanup failure is non-fatal for the IDE flow.
                }
            }
        }
    }

    private static String buildPrompt(AskMauzCodexAction.EditorInvocation invocation) {
        return DEFAULT_INSTRUCTION + "\n"
                + "The selected code marks the main region of interest in "
                + invocation.originalFile().getFileName() + ".\n"
                + "Keep unrelated parts of the file unchanged.\n"
                + "Return only the complete updated file contents.\n\n"
                + "Selected code:\n"
                + "```" + extensionLanguageHint(invocation.originalFile().getFileName().toString()) + "\n"
                + invocation.selectedCode() + "\n"
                + "```\n\n"
                + "Current file:\n"
                + "```" + extensionLanguageHint(invocation.originalFile().getFileName().toString()) + "\n"
                + invocation.documentText() + "\n"
                + "```";
    }

    private static String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index) : ".txt";
    }

    private static String extensionLanguageHint(String fileName) {
        String extension = extensionOf(fileName);
        return extension.startsWith(".") ? extension.substring(1) : extension;
    }

    static final class CodexCliResult {

        private final List<String> command;
        private final Path tempFile;
        private final int exitCode;
        private final String output;
        private final String replacementText;
        private final String error;

        CodexCliResult(List<String> command, Path tempFile, int exitCode, String output, String replacementText, String error) {
            this.command = List.copyOf(command);
            this.tempFile = tempFile;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.replacementText = replacementText == null ? "" : replacementText;
            this.error = error;
        }

        String formatForDisplay() {
            StringBuilder sb = new StringBuilder(1024);
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

            sb.append("Replacement output:\n");
            if (replacementText.isBlank()) {
                sb.append("<no replacement>");
            } else {
                sb.append(replacementText.strip());
            }
            sb.append('\n').append('\n');

            sb.append("CLI output:\n");
            if (output.isBlank()) {
                sb.append("<no output>");
            } else {
                sb.append(output.strip());
            }
            sb.append('\n');
            return sb.toString();
        }

        boolean canReplaceBuffer() {
            return error == null && exitCode == 0 && !replacementText.isBlank();
        }

        String replacementText() {
            return replacementText;
        }
    }
}
