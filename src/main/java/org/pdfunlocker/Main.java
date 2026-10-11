/*
 * PDF Unlocker — application entry point: configures the UI and launches the main window.
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
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);

            // Files passed on the command line are added to the list.
            List<Path> initial = new ArrayList<>();
            for (String arg : args) {
                initial.add(Path.of(arg));
            }
            if (!initial.isEmpty()) {
                frame.addPaths(initial);
            }
        });
    }

    private static void installLookAndFeel() {
        try {
            if (com.formdev.flatlaf.FlatLightLaf.setup()) {
                return;
            }
        } catch (Throwable ignored) {
            // fall through to the platform look and feel
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the default look and feel
        }
    }
}
