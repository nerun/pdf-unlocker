/*
 * PDF Unlocker — loads UI text in Portuguese (pt_BR) or English based on the system locale.
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
package org.pdfunlocker.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Picks the UI language from the JVM's default locale: any Portuguese variant
 * (pt, pt_BR, pt_PT, ...) selects the Portuguese (pt_BR) bundle; every other
 * locale falls back to English. The messages live in
 * {@code src/main/resources/i18n/messages.properties} (English, the default)
 * and {@code messages_pt_BR.properties} (Portuguese).
 */
public final class I18n {

    private static final ResourceBundle BUNDLE =
        ResourceBundle.getBundle("i18n.messages", targetLocale());

    private I18n() {
    }

    private static Locale targetLocale() {
        Locale system = Locale.getDefault();
        return "pt".equalsIgnoreCase(system.getLanguage())
            ? Locale.of("pt", "BR")
            : Locale.ENGLISH;
    }

    /**
     * Returns the message for {@code key}, formatted with {@code args} via
     * {@link MessageFormat} when any are given.
     */
    public static String t(String key, Object... args) {
        String pattern = BUNDLE.getString(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
