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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.pdfunlocker.i18n.I18n;

/**
 * Core logic: opens a PDF with a known password (or an empty one) and saves a
 * copy without any encryption or usage restrictions.
 *
 * <p>This does NOT guess or crack unknown passwords. A file that needs an
 * opening password can only be unlocked if the caller supplies that password.
 * Files that open without a password but restrict printing, copying or editing
 * (owner-password only) are unlocked with no input at all, even when the
 * caller typed a password that does not belong to them.</p>
 *
 * <p>AES-256 files (revision 5/6) whose {@code /Perms} entry is missing or has
 * the wrong size are also accepted: {@code /Perms} only double-checks the
 * permission flags and plays no part in decrypting the content.</p>
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

            // Try the empty password first: a restriction-only file opens with it,
            // whatever the caller typed. Then the given password, if any.
            List<String> candidates = pwd.isEmpty() ? List.of("") : List.of("", pwd);
            PDDocument opened = null;
            String openedWith = null;
            InvalidPasswordException lastRefusal = null;
            for (String candidate : candidates) {
                try {
                    opened = load(input.toFile(), candidate);
                    openedWith = candidate;
                    break;
                } catch (InvalidPasswordException e) {
                    lastRefusal = e;
                }
            }
            if (opened == null) {
                throw lastRefusal;
            }

            boolean usedPassword;
            try (PDDocument doc = opened) {
                if (!doc.isEncrypted()) {
                    return new Result(Outcome.NOT_PROTECTED, I18n.t("result.notProtected"));
                }
                usedPassword = !openedWith.isEmpty();
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

    /** Loads a PDF like {@code Loader.loadPDF(file, password)}, tolerating a missing {@code /Perms}. */
    private static PDDocument load(File file, String password) throws IOException {
        RandomAccessReadBufferedFile source = new RandomAccessReadBufferedFile(file);
        try {
            return new TolerantPermsParser(source, password).parse();
        } catch (IOException | RuntimeException e) {
            IOUtils.closeQuietly(source);
            throw e;
        }
    }

    /**
     * A parser that gives AES-256 files without a usable {@code /Perms} entry a
     * placeholder one, in memory only, before decryption starts. PDFBox only logs
     * a warning when {@code /Perms} does not verify, but fails outright when it is
     * absent; the placeholder turns the second case into the first.
     */
    private static final class TolerantPermsParser extends PDFParser {

        TolerantPermsParser(RandomAccessReadBufferedFile source, String password) throws IOException {
            super(source, password);
        }

        @Override
        protected void prepareDecryption() throws IOException {
            try {
                COSDictionary dictionary = document == null ? null : document.getEncryptionDictionary();
                PDEncryption encryption = dictionary == null ? null : new PDEncryption(dictionary);
                if (encryption != null && encryption.getRevision() >= 5) {
                    byte[] perms = encryption.getPerms();
                    if (perms == null || perms.length != 16) {
                        encryption.setPerms(new byte[16]);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Not encrypted or unreadable here: let the normal path report it.
            }
            super.prepareDecryption();
        }
    }
}
