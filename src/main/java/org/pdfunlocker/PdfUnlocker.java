/*
 * PDF Unlocker — core logic: removes PDF passwords and restrictions using Apache PDFBox.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.pdfunlocker.i18n.I18n;

/**
 * Core logic: opens a PDF with a known password (or an empty one) and saves a
 * copy without any encryption or usage restrictions.
 *
 * <p>This does NOT guess or crack unknown passwords. A file that needs an
 * opening password can only be unlocked if the caller supplies that password.
 * Files that open without a password but restrict printing, copying or editing
 * (owner-password only) are unlocked with no input at all.</p>
 */
public final class PdfUnlocker {

    public enum Outcome { UNLOCKED, NOT_PROTECTED, PASSWORD_REQUIRED, ERROR }

    public record Result(Outcome outcome, String message) { }

    private PdfUnlocker() { }

    /**
     * @param input    source PDF
     * @param output   destination PDF (may equal {@code input} to overwrite)
     * @param password opening password, or null/empty if none is known
     */
    public static Result unlock(Path input, Path output, String password) {
        String pwd = password == null ? "" : password;
        Path temp = null;
        try {
            Path outDir = output.toAbsolutePath().getParent();
            Files.createDirectories(outDir);
            // Write to a temp file first so a failure never leaves a broken output
            // (and so overwriting the original is safe).
            temp = Files.createTempFile(outDir, ".pdfunlocker-", ".tmp");

            boolean usedPassword;
            try (PDDocument doc = Loader.loadPDF(input.toFile(), pwd)) {
                if (!doc.isEncrypted()) {
                    return new Result(Outcome.NOT_PROTECTED, I18n.t("result.notProtected"));
                }
                usedPassword = !pwd.isEmpty();
                doc.setAllSecurityToBeRemoved(true);
                doc.save(temp.toFile());
            }
            // The source is closed here, so replacing it in place is safe on every OS.
            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);

            return new Result(Outcome.UNLOCKED, usedPassword
                    ? I18n.t("result.unlockedWithPassword")
                    : I18n.t("result.unlockedNoPassword"));
        } catch (InvalidPasswordException e) {
            return new Result(Outcome.PASSWORD_REQUIRED, pwd.isEmpty()
                    ? I18n.t("result.passwordRequired")
                    : I18n.t("result.wrongPassword"));
        } catch (Exception e) {
            String msg = e.getMessage();
            String detail = msg == null ? e.getClass().getSimpleName() : msg;
            return new Result(Outcome.ERROR, I18n.t("result.error", detail));
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }
}
