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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import org.netbeans.modules.dlight.terminal.ui.TerminalContainerTopComponent;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.openide.util.actions.Presenter;
import org.openide.windows.IOContainer;

/**
 *
 * @author Vladimir Voskresensky
 */
public abstract class TerminalAction extends AbstractAction implements Presenter.Toolbar {
    
    public static final String TERMINAL_ACTIONS_PATH = "Terminal/Actions"; // NOI18N
    private static final Logger LOG = Logger.getLogger(TerminalAction.class.getName());
    private static final boolean DEBUG_LOCAL_PTY =
            Boolean.getBoolean("org.netbeans.modules.dlight.terminal.local.debug"); // NOI18N

    public TerminalAction(String name, String descr, ImageIcon icon) {
        putValue(Action.NAME, name);
        putValue(Action.SHORT_DESCRIPTION, descr);
        putValue(Action.SMALL_ICON, icon);
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        if (DEBUG_LOCAL_PTY) {
            LOG.log(Level.INFO, "Terminal action invoked: command={0} class={1}",
                    new Object[]{e == null ? null : e.getActionCommand(), getClass().getName()}); // NOI18N
        }
        final TerminalContainerTopComponent instance = TerminalContainerTopComponent.findInstance();
        instance.open();
        instance.requestActive();
        final IOContainer ioContainer = instance.getIOContainer();
        final ExecutionEnvironment env = getEnvironment();
        if (env != null) {
            JediTermSupport.openTerminal(
                    ioContainer,
                    env.getDisplayName(),
                    env,
                    null,
                    TerminalContainerTopComponent.SILENT_MODE_COMMAND.equals(e.getActionCommand()));
        }
    }

    @Override
    public Component getToolbarPresenter() {
        return TerminalUiSupport.getToolbarPresenter(this);
    }

    protected abstract ExecutionEnvironment getEnvironment();
}
