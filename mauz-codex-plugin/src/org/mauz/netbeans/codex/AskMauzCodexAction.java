package org.mauz.netbeans.codex;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Registers the editor popup action and bridges the current selection to the
 * background Codex CLI invocation.
 */
@ActionID(
        category = "Edit",
        id = "org.mauz.netbeans.codex.AskMauzCodexAction"
)
@ActionRegistration(
        displayName = "#CTL_AskMauzCodexAction"
)
@ActionReference(
        path = "Editors/Popup",
        position = 2750
)
@Messages({
    "CTL_AskMauzCodexAction=Ask MAUZ Codex",
    "MSG_NoEditor=No active editor was found.",
    "MSG_NoSelection=Select some code in the editor before invoking Ask MAUZ Codex.",
    "MSG_Running=Running Codex patch against the selected code..."
})
public final class AskMauzCodexAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor(AskMauzCodexAction.class);

    @Override
    public void actionPerformed(ActionEvent event) {
        // EditorRegistry gives the currently focused editor component for popup actions.
        JTextComponent editor = EditorRegistry.focusedComponent();
        if (editor == null) {
            notifyUser(Bundle.MSG_NoEditor());
            return;
        }

        String selectedCode = editor.getSelectedText();
        if (selectedCode == null || selectedCode.isBlank()) {
            notifyUser(Bundle.MSG_NoSelection());
            return;
        }

        // The dedicated TopComponent acts as the plugin's output tool window.
        CodexPatchTopComponent outputWindow = CodexPatchTopComponent.findInstance();
        outputWindow.open();
        outputWindow.requestVisible();
        outputWindow.setOutput(Bundle.MSG_Running());
        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Running());

        RP.post(() -> {
            CodexCliService.CodexCliResult result = CodexCliService.runPatch(selectedCode);
            SwingUtilities.invokeLater(() -> {
                outputWindow.setOutput(result.formatForDisplay());
                outputWindow.requestActive();
            });
        });
    }

    private static void notifyUser(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message,
                NotifyDescriptor.INFORMATION_MESSAGE
        ));
    }
}
