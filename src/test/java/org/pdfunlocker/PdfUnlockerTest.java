/*
 * PDF Unlocker — automated tests for the password/restriction removal logic (PdfUnlocker).
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfUnlockerTest {

    @TempDir
    Path dir;

    private Path makePdf(String name, String owner, String user, int keyLength) throws IOException {
        Path file = dir.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            if (owner != null) {
                AccessPermission ap = new AccessPermission();
                ap.setCanPrint(false);
                ap.setCanModify(false);
                ap.setCanExtractContent(false);
                StandardProtectionPolicy policy = new StandardProtectionPolicy(owner, user, ap);
                policy.setEncryptionKeyLength(keyLength);
                doc.protect(policy);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private void assertOpenWithoutPassword(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            assertFalse(doc.isEncrypted());
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    void removesRestrictionsWhenThereIsNoOpeningPassword() throws IOException {
        for (int bits : new int[] {128, 256}) {
            Path in = makePdf("restricted" + bits + ".pdf", "owner-secret", "", bits);
            Path out = dir.resolve("restricted" + bits + "_out.pdf");
            PdfUnlocker.Result r = PdfUnlocker.unlock(in, out, "");
            assertEquals(PdfUnlocker.Outcome.UNLOCKED, r.outcome(), r.message());
            assertOpenWithoutPassword(out);
        }
    }

    @Test
    void needsCorrectOpeningPassword() throws IOException {
        Path in = makePdf("locked.pdf", "owner-secret", "open-me", 256);
        Path out = dir.resolve("locked_out.pdf");

        assertEquals(PdfUnlocker.Outcome.PASSWORD_REQUIRED,
            PdfUnlocker.unlock(in, out, null).outcome());
        assertEquals(PdfUnlocker.Outcome.PASSWORD_REQUIRED,
            PdfUnlocker.unlock(in, out, "wrong").outcome());
        assertFalse(Files.exists(out), "no output may be left behind after a failure");

        PdfUnlocker.Result ok = PdfUnlocker.unlock(in, out, "open-me");
        assertEquals(PdfUnlocker.Outcome.UNLOCKED, ok.outcome(), ok.message());
        assertOpenWithoutPassword(out);

        // the original must stay protected
        assertThrows(InvalidPasswordException.class, () -> Loader.loadPDF(in.toFile()).close());
    }

    @Test
    void ownerPasswordAlsoWorksAsOpeningPassword() throws IOException {
        Path in = makePdf("both.pdf", "owner-secret", "open-me", 128);
        Path out = dir.resolve("both_out.pdf");
        assertEquals(PdfUnlocker.Outcome.UNLOCKED,
            PdfUnlocker.unlock(in, out, "owner-secret").outcome());
        assertOpenWithoutPassword(out);
    }

    @Test
    void unprotectedFileIsReportedAndNotWritten() throws IOException {
        Path in = makePdf("plain.pdf", null, null, 0);
        Path out = dir.resolve("plain_out.pdf");
        assertEquals(PdfUnlocker.Outcome.NOT_PROTECTED, PdfUnlocker.unlock(in, out, "").outcome());
        assertFalse(Files.exists(out));
    }

    @Test
    void canOverwriteTheOriginalInPlace() throws IOException {
        Path in = makePdf("inplace.pdf", "owner-secret", "open-me", 256);
        assertEquals(PdfUnlocker.Outcome.UNLOCKED,
            PdfUnlocker.unlock(in, in, "open-me").outcome());
        assertOpenWithoutPassword(in);
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "temp files must be cleaned up");
        }
    }

    @Test
    void garbageInputIsReportedAsError() throws IOException {
        Path in = dir.resolve("garbage.pdf");
        Files.writeString(in, "this is not a pdf");
        assertEquals(PdfUnlocker.Outcome.ERROR,
            PdfUnlocker.unlock(in, dir.resolve("g_out.pdf"), "").outcome());
    }

    @Test
    void restrictionOnlyFileUnlocksEvenWithAWrongTypedPassword() throws IOException {
        for (int bits : new int[] {40, 128, 256}) {
            Path in = makePdf("typed" + bits + ".pdf", "owner-secret", "", bits);
            Path out = dir.resolve("typed" + bits + "_out.pdf");
            PdfUnlocker.Result r = PdfUnlocker.unlock(in, out, "not-the-password");
            assertEquals(PdfUnlocker.Outcome.UNLOCKED, r.outcome(), r.message());
            assertOpenWithoutPassword(out);
        }
    }

    /** Hides the {@code /Perms} key (same length, so offsets stay valid). */
    private static void removePerms(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        byte[] key = "/Perms".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int found = 0;
        for (int i = 0; i + key.length <= bytes.length; i++) {
            if (java.util.Arrays.equals(bytes, i, i + key.length, key, 0, key.length)) {
                bytes[i + 1] = 'X';
                found++;
            }
        }
        assertTrue(found > 0, "the test file should carry a /Perms entry");
        Files.write(file, bytes);
    }

    @Test
    void aes256FileWithoutPermsEntryStillUnlocks() throws IOException {
        Path restricted = makePdf("noperms_owner.pdf", "owner-secret", "", 256);
        removePerms(restricted);
        Path out1 = dir.resolve("noperms_owner_out.pdf");
        PdfUnlocker.Result r1 = PdfUnlocker.unlock(restricted, out1, "");
        assertEquals(PdfUnlocker.Outcome.UNLOCKED, r1.outcome(), r1.message());
        assertOpenWithoutPassword(out1);

        Path locked = makePdf("noperms_user.pdf", "owner-secret", "open-me", 256);
        removePerms(locked);
        Path out2 = dir.resolve("noperms_user_out.pdf");
        assertEquals(PdfUnlocker.Outcome.PASSWORD_REQUIRED, PdfUnlocker.unlock(locked, out2, "wrong").outcome());
        PdfUnlocker.Result r2 = PdfUnlocker.unlock(locked, out2, "open-me");
        assertEquals(PdfUnlocker.Outcome.UNLOCKED, r2.outcome(), r2.message());
        assertOpenWithoutPassword(out2);
    }
}
