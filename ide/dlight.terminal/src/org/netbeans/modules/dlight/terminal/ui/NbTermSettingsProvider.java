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

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.AwtTransformers;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import java.awt.Color;
import java.awt.Font;
import org.netbeans.lib.terminalemulator.support.TermOptions;

final class NbTermSettingsProvider extends DefaultSettingsProvider {

    private static final int RED_LUMA_WEIGHT = 299;
    private static final int GREEN_LUMA_WEIGHT = 587;
    private static final int BLUE_LUMA_WEIGHT = 114;
    private static final int LUMA_SCALE = 1000;
    private static final int DARK_BACKGROUND_THRESHOLD = 128;

    private final TermOptions termOptions;

    NbTermSettingsProvider(TermOptions termOptions) {
        this.termOptions = termOptions;
    }

    @Override
    public Font getTerminalFont() {
        return termOptions.getFont();
    }

    @Override
    public float getTerminalFontSize() {
        return termOptions.getFont().getSize2D();
    }

    @Override
    @SuppressWarnings("deprecation")
    public TextStyle getDefaultStyle() {
        return new TextStyle(
                toTerminalColor(termOptions.getForeground()),
                toTerminalColor(termOptions.getBackground()));
    }

    @Override
    public TextStyle getSelectionColor() {
        Color selectionBackground = termOptions.getSelectionBackground();
        return new TextStyle(
                toTerminalColor(getContrastingColor(selectionBackground)),
                toTerminalColor(selectionBackground));
    }

    @Override
    public boolean useInverseSelectionColor() {
        return false;
    }

    @Override
    public int getBufferMaxLinesCount() {
        return termOptions.getHistorySize();
    }

    @Override
    public boolean altSendsEscape() {
        return termOptions.getAltSendsEscape();
    }

    @Override
    public boolean scrollToBottomOnTyping() {
        return termOptions.getScrollOnInput();
    }

    private static TerminalColor toTerminalColor(Color color) {
        return AwtTransformers.fromAwtToTerminalColor(color);
    }

    private static Color getContrastingColor(Color background) {
        int luma = (RED_LUMA_WEIGHT * background.getRed()
                + GREEN_LUMA_WEIGHT * background.getGreen()
                + BLUE_LUMA_WEIGHT * background.getBlue()) / LUMA_SCALE;
        return luma >= DARK_BACKGROUND_THRESHOLD ? Color.BLACK : Color.WHITE;
    }
}
