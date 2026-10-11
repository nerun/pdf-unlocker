# PDF Unlocker

Copyright 2026 Daniel Dias Rodrigues \<danieldiasr@gmail.com\>. Licensed under the
[Apache License, Version 2.0](LICENSE).

A free and open-source alternative to tools like "PDF Password Remover": a desktop
GUI (Java Swing) to remove passwords and restrictions from PDF files in batch.
The interface is bilingual (Brazilian Portuguese / English), chosen automatically
based on the operating system's language.

## What it does

- **Removes restrictions** (printing, text copying, editing, annotations) from PDFs that open
  without a password.
- **Removes the opening password** when you provide it (a single password for all files, or one per
  file).
- Processes several files at once: add files, whole folders (including subfolders), or drag and
  drop.
- Saves a copy (`name_unlocked.pdf`) in the same folder or another one; optionally overwrites the
  original.
- Never alters the original unless you check "Overwrite". Writing goes through a temporary file
  first, so a failure never leaves a corrupted PDF.
- Supports RC4 40/128-bit and AES 128/256-bit encryption.
- **Bilingual interface**: if the system language is Portuguese (`pt`, `pt_BR`, `pt_PT`, etc.), the
  interface appears in Brazilian Portuguese; otherwise, in English.

## What it does NOT do

It does not guess or crack unknown passwords. If the PDF requires an opening password, you must
provide it. Only use it on files you own or are authorized to unlock.

Note: removing the encryption invalidates the document's digital signatures.

## Requirements

- JDK 25 and Maven 3.9+ to build; JRE 25 to run.

## Build and run

```bash
mvn clean package
java -jar target/pdf-unlocker.jar
# optional: open already with files in the list
java -jar target/pdf-unlocker.jar file1.pdf folder/

# force a specific language, regardless of the system language
java -Duser.language=en -jar target/pdf-unlocker.jar
java -Duser.language=pt -Duser.country=BR -jar target/pdf-unlocker.jar
```

The tests (`mvn test`) create protected PDFs with PDFBox and verify that unlocking works.
To build on an older JDK, use `mvn clean package -Djava.release=21`.

## Code structure

- `PdfUnlocker` — password/restriction removal logic (PDFBox).
- `MainFrame`, `PdfEntry`, `PdfTableModel` — graphical interface (Swing).
- `i18n/I18n` — loads the interface text in Portuguese or English based on the
  system language (`src/main/resources/i18n/messages*.properties`).

Every `.java` file carries, in its header, a minimal description of what it does, the
copyright notice, and the license (Apache-2.0).

## Dependencies

- [Apache PDFBox](https://pdfbox.apache.org/) 3.x (Apache-2.0): PDF reading/writing and encryption.
- [FlatLaf](https://www.formdev.com/flatlaf/) (Apache-2.0): modern look and feel (falls back to the
  system look if missing).

See the [NOTICE](NOTICE) file for these libraries' attribution notices.

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE). Same license as both of the
project's dependencies, which avoids any licensing conflict in the final jar.
