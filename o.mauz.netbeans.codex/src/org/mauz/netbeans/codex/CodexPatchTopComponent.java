package org.mauz.netbeans.codex;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Displays Codex output and can apply the generated file replacement back to
 * the original file that the editor selection came from.
 */
@TopComponent.Description(
        preferredID = CodexPatchTopComponent.PREFERRED_ID,
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(
        mode = "output",
        openAtStartup = false
)
@ActionID(
        category = "Window",
        id = "org.mauz.netbeans.codex.CodexPatchTopComponent"
)
@ActionReference(
        path = "Menu/Window",
        position = 1600
)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_CodexPatchTopComponentAction",
        preferredID = CodexPatchTopComponent.PREFERRED_ID
)
@Messages({
    "CTL_CodexPatchTopComponent=MAUZ Codex Output",
    "CTL_CodexPatchTopComponentAction=MAUZ Codex Output",
    "HINT_CodexPatchTopComponent=Displays the output returned by the Codex CLI",
    "MSG_NoPatch=There are no successful Codex changes to apply.",
    "MSG_PatchApplied=Changes applied to the original file.",
    "# {0} - patch application failure details",
    "MSG_PatchFailed=Failed to apply the changes:\n\n{0}"
})
public final class CodexPatchTopComponent extends TopComponent {

    static final String PREFERRED_ID = "CodexPatchTopComponent";

    private static final RequestProcessor RP = new RequestProcessor(CodexPatchTopComponent.class);

    private final JTextArea outputArea = new JTextArea();
    private final JButton applyPatchButton = new JButton("Apply Changes");
    private final JLabel targetFileLabel = new JLabel("No file selected");

    private PatchSession currentSession;

    public CodexPatchTopComponent() {
        setName(Bundle.CTL_CodexPatchTopComponent());
        setToolTipText(Bundle.HINT_CodexPatchTopComponent());
        setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        headerPanel.add(applyPatchButton);
        headerPanel.add(targetFileLabel);
        add(headerPanel, BorderLayout.NORTH);

        outputArea.setEditable(false);
        outputArea.setLineWrap(false);
        outputArea.setWrapStyleWord(false);
        Font outputFont = UIManager.getFont("controlFont");
        if (outputFont != null) {
            outputArea.setFont(outputFont);
        }
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        applyPatchButton.setEnabled(false);
        applyPatchButton.addActionListener(event -> applyPatch());
    }

    static CodexPatchTopComponent findInstance() {
        TopComponent topComponent = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (topComponent instanceof CodexPatchTopComponent codexOutput) {
            return codexOutput;
        }
        return new CodexPatchTopComponent();
    }

    void showRunning(AskMauzCodexAction.EditorInvocation invocation, String message) {
        currentSession = null;
        applyPatchButton.setEnabled(false);
        targetFileLabel.setText(invocation.originalFile().toString());
        setOutput(message);
    }

    void showResult(AskMauzCodexAction.EditorInvocation invocation, CodexCliService.CodexCliResult result) {
        currentSession = new PatchSession(invocation, result);
        applyPatchButton.setEnabled(result.canReplaceBuffer());
        targetFileLabel.setText(invocation.originalFile().toString());
        setOutput(result.formatForDisplay());
    }

    private void applyPatch() {
        PatchSession session = currentSession;
        if (session == null || !session.result().canReplaceBuffer()) {
            notifyUser(Bundle.MSG_NoPatch(), NotifyDescriptor.WARNING_MESSAGE);
            return;
        }

        applyPatchButton.setEnabled(false);
        RP.post(() -> {
            try {
                writeBackToOriginalFile(session);
                SwingUtilities.invokeLater(() -> {
                    setOutput(session.result().formatForDisplay()
                            + "\nApplied changes to: " + session.invocation().originalFile() + '\n');
                    applyPatchButton.setEnabled(true);
                    notifyUser(Bundle.MSG_PatchApplied(), NotifyDescriptor.INFORMATION_MESSAGE);
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    applyPatchButton.setEnabled(true);
                    notifyUser(Bundle.MSG_PatchFailed(ex.getMessage()), NotifyDescriptor.ERROR_MESSAGE);
                });
            }
        });
    }

    private void writeBackToOriginalFile(PatchSession session) throws IOException {
        Files.writeString(
                session.invocation().originalFile(),
                session.result().replacementText(),
                StandardCharsets.UTF_8
        );
    }

    private void notifyUser(String message, int messageType) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(message, messageType));
    }

    private void setOutput(String text) {
        outputArea.setText(text == null ? "" : text);
        outputArea.setCaretPosition(0);
    }

    private record PatchSession(
            AskMauzCodexAction.EditorInvocation invocation,
            CodexCliService.CodexCliResult result
    ) {
    }
}
