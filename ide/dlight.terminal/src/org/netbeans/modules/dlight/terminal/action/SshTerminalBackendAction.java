/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.dlight.terminal.action;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.netbeans.modules.dlight.terminal.backend.ssh.SshConnectionConfig;
import org.netbeans.modules.dlight.terminal.ui.TerminalContainerTopComponent;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.IOContainer;

/**
 * Opens an SSH terminal rendered by JediTerm over the SSH backend.
 */
@ActionID(id = "SshTerminalBackendAction", category = "Window")
@ActionRegistration(
        iconInMenu = true,
        displayName = "#SshTerminalBackendShortDescr",
        iconBase = "org/netbeans/modules/dlight/terminal/action/remote_term.svg"
)
public final class SshTerminalBackendAction extends TerminalAction {

    public SshTerminalBackendAction() {
        super(
                "SshTerminalBackendAction", // NOI18N
                NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalBackendShortDescr"), // NOI18N
                ImageUtilities.loadImageIcon("org/netbeans/modules/dlight/terminal/action/remote_term.svg", false) // NOI18N
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final TerminalContainerTopComponent instance = TerminalContainerTopComponent.findInstance();
        instance.open();
        instance.requestActive();
        final IOContainer ioContainer = instance.getIOContainer();
        SshConnectionConfig config = openDialog();
        if (config == null) {
            return;
        }
        JediTermSupport.openSshTerminal(ioContainer, config);
    }

    @Override
    protected ExecutionEnvironment getEnvironment() {
        return null;
    }

    private SshConnectionConfig openDialog() {
        SshConnectionPanel panel = new SshConnectionPanel();
        DialogDescriptor descriptor = new DialogDescriptor(
                panel,
                NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalDialogTitle"), // NOI18N
                true,
                DialogDescriptor.OK_CANCEL_OPTION,
                DialogDescriptor.OK_OPTION,
                null
        );
        Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        dialog.setVisible(true);
        dialog.dispose();
        if (descriptor.getValue() != DialogDescriptor.OK_OPTION) {
            return null;
        }

        String host = panel.hostField.getText().trim();
        String user = panel.userField.getText().trim();
        String portText = panel.portField.getText().trim();
        char[] password = panel.passwordField.getPassword();
        if (host.isEmpty() || user.isEmpty() || password.length == 0) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalConfigMissing"), // NOI18N
                    NotifyDescriptor.WARNING_MESSAGE
            ));
            return null;
        }
        final int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range"); // NOI18N
            }
        } catch (NumberFormatException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalConfigBadPort"), // NOI18N
                    NotifyDescriptor.WARNING_MESSAGE
            ));
            return null;
        }

        return SshConnectionConfig.builder(host, port, user, password)
                .acceptUnknownHostKeys(panel.acceptUnknownHostKeys.isSelected())
                .build();
    }

    private static final class SshConnectionPanel extends JPanel {

        private final JTextField hostField = new JTextField("localhost", 24); // NOI18N
        private final JTextField portField = new JTextField("22", 24); // NOI18N
        private final JTextField userField = new JTextField(System.getProperty("user.name"), 24); // NOI18N
        private final JPasswordField passwordField = new JPasswordField(24);
        private final JCheckBox acceptUnknownHostKeys = new JCheckBox(
                NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalAcceptUnknownHostKeys"), // NOI18N
                true
        );

        private SshConnectionPanel() {
            super(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0;
            add(new JLabel(NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalHost")), gbc); // NOI18N

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            add(hostField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.weightx = 0;
            add(new JLabel(NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalPort")), gbc); // NOI18N

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            add(portField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.weightx = 0;
            add(new JLabel(NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalUser")), gbc); // NOI18N

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            add(userField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.weightx = 0;
            add(new JLabel(NbBundle.getMessage(SshTerminalBackendAction.class, "SshTerminalPassword")), gbc); // NOI18N

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            add(passwordField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.gridwidth = 2;
            add(acceptUnknownHostKeys, gbc);
        }
    }
}
