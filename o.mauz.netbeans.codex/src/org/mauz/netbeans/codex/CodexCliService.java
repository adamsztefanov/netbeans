package org.mauz.netbeans.codex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Encapsulates the external Codex CLI invocation so the action stays focused on
 * IDE integration.
 */
final class CodexCliService {

    private static final String DEFAULT_INSTRUCTION =
            "Edit this file and return only the complete updated file contents. "
            + "Do not return a diff. "
            + "Do not wrap the answer in markdown fences.";
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    private static final String CLI_COMMAND_PROPERTY = "mauz.codex.command";
    private static final String CLI_COMMAND_ENV = "MAUZ_CODEX_COMMAND";

    private CodexCliService() {
    }

    static CodexCliResult runEdit(AskMauzCodexAction.EditorInvocation invocation) {
        Path promptFile = null;
        Path responseFile = null;
        List<String> command = new ArrayList<>(List.of("codex", "exec"));
        try {
            promptFile = Files.createTempFile(
                    "mauz-codex-file-",
                    extensionOf(invocation.originalFile().getFileName().toString())
            );
            responseFile = Files.createTempFile(".mauz-codex-response-", extensionOf(invocation.originalFile().getFileName().toString()));

            String prompt = buildPrompt(invocation);
            Files.writeString(promptFile, invocation.documentText(), StandardCharsets.UTF_8);

            command = new ArrayList<>(resolveCodexCommand().command());
            command.add("exec");
            command.add("--skip-git-repo-check");
            command.add("--color");
            command.add("never");
            command.add("--output-last-message");
            command.add(responseFile.toAbsolutePath().toString());
            command.add("-");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Path workingDirectory = invocation.originalFile().getParent();
            if (workingDirectory != null && Files.isDirectory(workingDirectory)) {
                processBuilder.directory(workingDirectory.toFile());
            }
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
                    command,
                    promptFile,
                    -1,
                    "",
                    "",
                    "Failed to start the Codex CLI.\n\n"
                    + "Set `" + CLI_COMMAND_PROPERTY + "` or `" + CLI_COMMAND_ENV
                    + "` to the full CLI path if discovery does not match your setup.\n\n"
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CodexCliResult(
                    command,
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

    private static ResolvedCommand resolveCodexCommand() throws IOException {
        String configuredCommand = firstNonBlank(
                System.getProperty(CLI_COMMAND_PROPERTY),
                System.getenv(CLI_COMMAND_ENV)
        );
        List<Path> searchedPaths = new ArrayList<>();

        if (configuredCommand != null) {
            Path configuredPath = Path.of(configuredCommand);
            if (Files.isRegularFile(configuredPath)) {
                return launcherFor(configuredPath);
            }

            ResolvedCommand configuredByName = findCommand(configuredCommand, searchedPaths);
            if (configuredByName != null) {
                return configuredByName;
            }

            throw new IOException("Configured Codex CLI was not found: " + configuredCommand);
        }

        ResolvedCommand resolved = findCommand("codex", searchedPaths);
        if (resolved != null) {
            return resolved;
        }

        throw new IOException(
                "Could not locate the Codex CLI. Searched these locations:\n"
                + formatSearchedPaths(searchedPaths)
        );
    }

    private static ResolvedCommand findCommand(String baseName, List<Path> searchedPaths) {
        for (String candidate : executableCandidates(baseName)) {
            Path directCandidate = Path.of(candidate);
            if (directCandidate.isAbsolute()) {
                searchedPaths.add(directCandidate);
                if (Files.isRegularFile(directCandidate)) {
                    return launcherFor(directCandidate);
                }
            }
        }

        Set<Path> searchDirectories = new LinkedHashSet<>();
        addPathEntries(searchDirectories, System.getenv("PATH"));

        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            searchDirectories.add(Path.of(appData, "npm"));
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            if (WINDOWS) {
                searchDirectories.add(Path.of(userHome, "AppData", "Roaming", "npm"));
                searchDirectories.add(Path.of(userHome, "AppData", "Local", "OpenAI", "Codex", "bin"));
            } else {
                searchDirectories.add(Path.of(userHome, ".local", "bin"));
            }
        }

        for (Path directory : searchDirectories) {
            for (String candidate : executableCandidates(baseName)) {
                Path candidatePath = directory.resolve(candidate);
                searchedPaths.add(candidatePath);
                if (Files.isRegularFile(candidatePath)) {
                    return launcherFor(candidatePath);
                }
            }
        }

        return null;
    }

    private static void addPathEntries(Set<Path> searchDirectories, String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }

        String[] entries = pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator));
        for (String entry : entries) {
            String trimmed = stripMatchingQuotes(entry.trim());
            if (!trimmed.isEmpty()) {
                searchDirectories.add(Path.of(trimmed));
            }
        }
    }

    private static String stripMatchingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static List<String> executableCandidates(String baseName) {
        String lowerName = baseName.toLowerCase(Locale.ROOT);
        boolean hasExtension = lowerName.endsWith(".exe")
                || lowerName.endsWith(".cmd")
                || lowerName.endsWith(".bat")
                || lowerName.endsWith(".com")
                || lowerName.endsWith(".ps1");

        if (!WINDOWS || hasExtension) {
            return List.of(baseName);
        }

        return List.of(
                baseName + ".cmd",
                baseName + ".exe",
                baseName + ".bat",
                baseName + ".com",
                baseName + ".ps1",
                baseName
        );
    }

    private static ResolvedCommand launcherFor(Path executable) {
        String lowerName = executable.getFileName().toString().toLowerCase(Locale.ROOT);

        if (WINDOWS && (lowerName.endsWith(".cmd") || lowerName.endsWith(".bat"))) {
            return new ResolvedCommand(List.of("cmd.exe", "/c", executable.toString()));
        }

        if (WINDOWS && lowerName.endsWith(".ps1")) {
            return new ResolvedCommand(List.of("powershell.exe", "-NoProfile", "-File", executable.toString()));
        }

        return new ResolvedCommand(List.of(executable.toString()));
    }

    private static String formatSearchedPaths(List<Path> searchedPaths) {
        if (searchedPaths.isEmpty()) {
            return "<no candidate paths>";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(searchedPaths.size(), 12);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(" - ").append(searchedPaths.get(i));
        }
        if (searchedPaths.size() > limit) {
            sb.append('\n').append(" - ...");
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ResolvedCommand(List<String> command) {
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
