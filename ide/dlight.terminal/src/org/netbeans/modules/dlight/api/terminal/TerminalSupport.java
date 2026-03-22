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
package org.netbeans.modules.dlight.api.terminal;

import java.awt.Component;
import javax.swing.Action;
import org.netbeans.modules.dlight.terminal.action.JediTermSupport;
import org.netbeans.modules.dlight.terminal.action.TerminalUiSupport;
import org.netbeans.modules.dlight.terminal.ui.TerminalContainerTopComponent;
import org.netbeans.modules.nativeexecution.api.ExecutionEnvironment;
import org.openide.windows.IOContainer;

/**
 *
 * @author Vladimir Voskresensky
 */
public final class TerminalSupport {

    private TerminalSupport() {
    }

    /**
     * opens terminal tab in tab container for specified host in default
     * location
     *
     * @param ioContainer
     * @param env
     */
    public static void openTerminal(IOContainer ioContainer, String termTitle, ExecutionEnvironment env) {
        JediTermSupport.openTerminal(ioContainer, termTitle, env, null, false);
    }

    /**
     * opens terminal tab in tab container and change dir into specified
     * directory
     *
     * @param ioContainer
     * @param env
     */
    public static void openTerminal(IOContainer ioContainer, String termTitle, ExecutionEnvironment env, String dir) {
        JediTermSupport.openTerminal(ioContainer, termTitle, env, dir, false);
    }

    /**
     * opens terminal tab in default terminals container and change dir into
     * specified directory
     *
     * @param env
     */
    public static void openTerminal(String termTitle, ExecutionEnvironment env, String dir) {
        openTerminal(termTitle, env, dir, true);
    }

    /**
     * opens terminal tab in default terminals container sets and change dir
     * into specified directory. If pwdFlag terminal tries to set title to
     * user@host - `pwd`
     */
    public static void openTerminal(String termTitle, ExecutionEnvironment env, String dir, boolean pwdFlag) {
        final TerminalContainerTopComponent instance = TerminalContainerTopComponent.findInstance();
        Object prev = instance.getClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY);
        instance.putClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY, Boolean.FALSE);
        try {
            instance.open();
            instance.requestActive();
            IOContainer ioContainer = instance.getIOContainer();
            JediTermSupport.openTerminal(ioContainer, termTitle, env, dir, false);
        } finally {
            instance.putClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY, prev);
        }
    }

    /**
     * Open terminal tab with specified id (try to restore pinned tab state from
     * Preferences)
     *
     * @param id ID of a stored tab, from which we try to restore a state.
     * Default value = 0 which means don't restore the state
     */
    public static void restoreTerminal(String termTitle, ExecutionEnvironment env, String dir, boolean pwdFlag, long id) {
        final TerminalContainerTopComponent instance = TerminalContainerTopComponent.findInstance();
        Object prev = instance.getClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY);
        instance.putClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY, Boolean.FALSE);
        try {
            instance.open();
            instance.requestActive();
            IOContainer ioContainer = instance.getIOContainer();
            JediTermSupport.openTerminal(ioContainer, termTitle, env, dir, false);
        } finally {
            instance.putClientProperty(TerminalContainerTopComponent.AUTO_OPEN_LOCAL_PROPERTY, prev);
        }
    }

    public static Component getToolbarPresenter(Action action) {
        return TerminalUiSupport.getToolbarPresenter(action);
    }
}
