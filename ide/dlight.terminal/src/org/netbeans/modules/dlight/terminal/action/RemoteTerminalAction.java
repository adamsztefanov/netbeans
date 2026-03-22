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

import java.awt.event.ActionEvent;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;

/**
 *
 * @author Vladimir Voskresensky
 */
@ActionID(id = "RemoteTerminalAction", category = "Window")
@ActionRegistration(iconInMenu = true, displayName = "#RemoteTerminalShortDescr", iconBase = "org/netbeans/modules/dlight/terminal/action/remote_term.svg")
@ActionReference(path = TerminalAction.TERMINAL_ACTIONS_PATH, name = "org-netbeans-modules-dlight-terminal-action-RemoteTerminalAction", position = 200)
public final class RemoteTerminalAction extends TerminalAction {

    public RemoteTerminalAction() {
        super("RemoteTerminalAction", NbBundle.getMessage(RemoteTerminalAction.class, "RemoteTerminalShortDescr"), // NOI18N
                ImageUtilities.loadImageIcon("org/netbeans/modules/dlight/terminal/action/remote_term.svg", false)); // NOI18N
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Keep the default remote-terminal button, but render the session with JediTerm.
        new SshTerminalBackendAction().actionPerformed(e);
    }

    @Override
    protected ExecutionEnvironment getEnvironment() {
        return null;
    }
}
