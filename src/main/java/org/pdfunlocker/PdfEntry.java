/*
 * PDF Unlocker — one file row in the GUI's file list and its processing state.
 * Copyright 2026 Daniel Dias Rodrigues <danieldiasr@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pdfunlocker;

import java.nio.file.Path;

import org.pdfunlocker.i18n.I18n;

/** One row of the file list. Mutated only on the Swing event thread. */
final class PdfEntry {

    enum State {
        PENDING("state.pending"),
        WORKING("state.working"),
        UNLOCKED("state.unlocked"),
        NOT_PROTECTED("state.notProtected"),
        NEEDS_PASSWORD("state.needsPassword"),
        ERROR("state.error");

        private final String key;

        State(String key) {
            this.key = key;
        }

        /** Resolved in the UI language at call time. */
        String label() {
            return I18n.t(key);
        }
    }

    final Path path;
    final long size;
    String password = "";
    State state = State.PENDING;
    String message = "";

    PdfEntry(Path path, long size) {
        this.path = path;
        this.size = size;
    }

    String fileName() {
        return path.getFileName().toString();
    }
}
