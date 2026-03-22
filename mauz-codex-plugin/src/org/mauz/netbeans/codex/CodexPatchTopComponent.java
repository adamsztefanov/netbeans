package org.mauz.netbeans.codex;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Simple output window for displaying the raw patch or CLI diagnostics returned
 * by the external Codex process.
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
    "HINT_CodexPatchTopComponent=Displays the output returned by the Codex CLI"
})
public final class CodexPatchTopComponent extends TopComponent {

    static final String PREFERRED_ID = "CodexPatchTopComponent";

    private final JTextArea outputArea = new JTextArea();

    public CodexPatchTopComponent() {
        setName(Bundle.CTL_CodexPatchTopComponent());
        setToolTipText(Bundle.HINT_CodexPatchTopComponent());
        setLayout(new BorderLayout());

        outputArea.setEditable(false);
        outputArea.setLineWrap(false);
        outputArea.setWrapStyleWord(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    static CodexPatchTopComponent findInstance() {
        TopComponent topComponent = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (topComponent instanceof CodexPatchTopComponent codexOutput) {
            return codexOutput;
        }
        return new CodexPatchTopComponent();
    }

    void setOutput(String text) {
        outputArea.setText(text == null ? "" : text);
        outputArea.setCaretPosition(0);
    }
}
