package org.mauz.netbeans.codex;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.DynamicMenuContent;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Editor popup action that resolves the editor from popup context or the last
 * focused editor, then forwards the request to Codex.
 */
@ActionID(
        category = "Edit",
        id = "org.mauz.netbeans.codex.AskMauzCodexAction"
)
@ActionRegistration(
        displayName = "#CTL_AskMauzCodexAction",
        lazy = false
)
@ActionReference(
        path = "Editors/Popup",
        position = 2750
)
@Messages({
    "CTL_AskMauzCodexAction=Ask MAUZ Codex",
    "MSG_NoEditor=No active editor was found.",
    "MSG_NoFile=The selected editor content is not backed by a regular file.",
    "MSG_NoSelection=Select some code in the editor before invoking Ask MAUZ Codex.",
    "MSG_Running=Running Codex patch against the selected code..."
})
public final class AskMauzCodexAction extends AbstractAction implements ContextAwareAction {

    private static final RequestProcessor RP = new RequestProcessor(AskMauzCodexAction.class);

    private final Lookup context;

    public AskMauzCodexAction() {
        this(Lookup.EMPTY);
    }

    private AskMauzCodexAction(Lookup context) {
        super(Bundle.CTL_AskMauzCodexAction());
        putValue(DynamicMenuContent.HIDE_WHEN_DISABLED, false);
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        EditorInvocation invocation = EditorInvocation.resolve(context);
        if (invocation.editor() == null) {
            notifyUser(Bundle.MSG_NoEditor());
            return;
        }
        if (invocation.selectedCode() == null || invocation.selectedCode().isBlank()) {
            notifyUser(Bundle.MSG_NoSelection());
            return;
        }
        if (invocation.originalFile() == null) {
            notifyUser(Bundle.MSG_NoFile());
            return;
        }

        CodexPatchTopComponent outputWindow = CodexPatchTopComponent.findInstance();
        outputWindow.open();
        outputWindow.requestVisible();
        outputWindow.showRunning(invocation, Bundle.MSG_Running());
        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_Running());

        RP.post(() -> {
            CodexCliService.CodexCliResult result = CodexCliService.runPatch(invocation);
            javax.swing.SwingUtilities.invokeLater(() -> {
                outputWindow.showResult(invocation, result);
                outputWindow.requestActive();
            });
        });
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new AskMauzCodexAction(actionContext);
    }

    private static void notifyUser(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message,
                NotifyDescriptor.INFORMATION_MESSAGE
        ));
    }

    static record EditorInvocation(
            JTextComponent editor,
            DataObject dataObject,
            java.nio.file.Path originalFile,
            String selectedCode,
            int selectionStart,
            int selectionEnd
    ) {

        static EditorInvocation resolve(Lookup context) {
            JTextComponent editor = context.lookup(JTextComponent.class);
            if (editor == null) {
                editor = EditorRegistry.focusedComponent();
            }
            if (editor == null) {
                editor = EditorRegistry.lastFocusedComponent();
            }
            if (editor == null) {
                return new EditorInvocation(null, null, null, null, -1, -1);
            }

            DataObject dataObject = context.lookup(DataObject.class);
            if (dataObject == null) {
                Document document = editor.getDocument();
                Object description = document.getProperty(Document.StreamDescriptionProperty);
                if (description instanceof DataObject documentDataObject) {
                    dataObject = documentDataObject;
                }
            }

            java.nio.file.Path originalFile = null;
            if (dataObject != null) {
                FileObject fileObject = dataObject.getPrimaryFile();
                if (fileObject != null) {
                    java.io.File file = FileUtil.toFile(fileObject);
                    if (file != null) {
                        originalFile = file.toPath();
                    }
                }
            }

            return new EditorInvocation(
                    editor,
                    dataObject,
                    originalFile,
                    editor.getSelectedText(),
                    editor.getSelectionStart(),
                    editor.getSelectionEnd()
            );
        }
    }
}
