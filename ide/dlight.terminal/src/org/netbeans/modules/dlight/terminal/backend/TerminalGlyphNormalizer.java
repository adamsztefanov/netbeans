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
package org.netbeans.modules.dlight.terminal.backend;

/**
 * Normalizes a small set of Unicode glyphs that are rendered inconsistently by
 * some prompt/tooling combinations in the embedded terminal.
 */
public final class TerminalGlyphNormalizer {

    private TerminalGlyphNormalizer() {
    }

    public static void normalize(char[] buffer, int offset, int length) {
        if (buffer == null || length <= 0) {
            return;
        }
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            buffer[i] = normalize(buffer[i]);
        }
    }

    static char normalize(char ch) {
        switch (ch) {
            case '\u256D':
                return '\u250C';
            case '\u256E':
                return '\u2510';
            case '\u256F':
                return '\u2518';
            case '\u2570':
                return '\u2514';
            default:
                return ch;
        }
    }
}
