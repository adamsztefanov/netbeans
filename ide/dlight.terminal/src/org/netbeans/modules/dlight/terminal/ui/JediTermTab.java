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
package org.netbeans.modules.dlight.terminal.ui;

import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.TerminalWidgetListener;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.netbeans.lib.terminalemulator.support.TermOptions;
import org.openide.util.NbPreferences;
import org.openide.windows.IOContainer;

public final class JediTermTab extends JPanel implements IOContainer.CallBacks {

    private final IOContainer ioContainer;
    private final String baseTitle;
    private final JediTermWidget widget;
    private final TtyConnector connector;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public JediTermTab(IOContainer ioContainer, String title, TtyConnector connector) {
        super(new BorderLayout());
        this.ioContainer = ioContainer;
        this.baseTitle = title;
        this.connector = connector;
        TermOptions termOptions = TermOptions.getDefault(NbPreferences.forModule(TermOptions.class));
        this.widget = new JediTermWidget(new NbTermSettingsProvider(termOptions));
        setName(title);
        widget.setTtyConnector(connector);
        widget.getTerminal().addApplicationTitleListener(this::updateApplicationTitle);
        widget.addListener((TerminalWidget terminalWidget) -> requestContainerClose());
        add(widget.getComponent(), BorderLayout.CENTER);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        startWhenShowing();
    }

    private void startWhenShowing() {
        if (isShowing()) {
            startWidgetSession();
            return;
        }
        HierarchyListener[] listenerRef = new HierarchyListener[1];
        listenerRef[0] = (HierarchyEvent e) -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0 || !isShowing()) {
                return;
            }
            removeHierarchyListener(listenerRef[0]);
            startWidgetSession();
        };
        addHierarchyListener(listenerRef[0]);
    }

    private void startWidgetSession() {
        widget.start();
        Thread waiter = new Thread(() -> {
            try {
                connector.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            requestContainerClose();
        }, "JediTermTab-" + Integer.toHexString(System.identityHashCode(this))); // NOI18N
        waiter.setDaemon(true);
        waiter.start();
    }

    @Override
    public void closed() {
        closeRequested.set(true);
        disposeTab();
    }

    @Override
    public void selected() {
        focusTerminal();
    }

    @Override
    public void activated() {
        focusTerminal();
    }

    @Override
    public void deactivated() {
    }

    @Override
    public void requestFocus() {
        widget.requestFocus();
    }

    @Override
    public boolean requestFocusInWindow() {
        return widget.requestFocusInWindow();
    }

    private void updateApplicationTitle(String applicationTitle) {
        if (applicationTitle == null || applicationTitle.isBlank()) {
            return;
        }
        SwingUtilities.invokeLater(() -> ioContainer.setTitle(this, baseTitle + " - " + applicationTitle)); // NOI18N
    }

    private void requestContainerClose() {
        if (!closeRequested.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> ioContainer.remove(this));
    }

    private void focusTerminal() {
        SwingUtilities.invokeLater(widget::requestFocusInWindow);
    }

    private void disposeTab() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        try {
            connector.close();
        } finally {
            widget.close();
        }
    }
}
