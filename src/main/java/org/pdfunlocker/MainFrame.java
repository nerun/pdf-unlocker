/*
 * PDF Unlocker — main window: file list, options and the unlock workflow (Swing UI).
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.pdfunlocker.PdfEntry.State;
import org.pdfunlocker.i18n.I18n;

public final class MainFrame extends JFrame {

    private static final String APP_NAME = "PDF Unlocker";
    private static final String VERSION = "1.0.0";
    private static final String PROJECT_URL = "https://github.com/nerun/pdf-unlocker";
    private static final String COPYRIGHT = "© 2026 Daniel Dias Rodrigues";

    private final PdfTableModel model = new PdfTableModel();
    private final JTable table = new HintTable(model);

    private final JPasswordField defaultPassword = new JPasswordField(16);
    private final JCheckBox showPassword = new JCheckBox(I18n.t("checkbox.showPassword"));
    private final JRadioButton sameFolder = new JRadioButton(I18n.t("radio.sameFolder"), true);
    private final JRadioButton otherFolder = new JRadioButton(I18n.t("radio.otherFolder"));
    private final JTextField outDirField = new JTextField(21);
    private final JButton browseOutDir = new JButton(I18n.t("button.browse"));
    private final JTextField suffixField = new JTextField(I18n.t("default.suffix"), 12);
    private final JCheckBox overwrite = new JCheckBox(I18n.t("checkbox.overwrite"));

    private final JButton addFilesButton = new JButton(I18n.t("button.addFiles"));
    private final JButton addFolderButton = new JButton(I18n.t("button.addFolder"));
    private final JButton removeButton = new JButton(I18n.t("button.removeSelected"));
    private final JButton clearButton = new JButton(I18n.t("button.clearList"));
    private final JButton aboutButton = new JButton(I18n.t("button.about"));

    private final JButton startButton = new JButton(I18n.t("button.start"));
    private final JButton cancelButton = new JButton(I18n.t("button.cancel"));
    private final JButton openFolderButton = new JButton(I18n.t("button.openOutputFolder"));
    private final JProgressBar progress = new JProgressBar();
    private final JLabel statusLabel = new JLabel(I18n.t("status.initial"));

    private UnlockWorker worker;
    private Path lastOutputDir;

    public MainFrame() {
        super(APP_NAME);

        ImageIcon icon = new ImageIcon(
                MainFrame.class.getResource("/icons/pdf-unlocker.png")
        );
        setIconImage(icon.getImage());

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        buildTable();

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(buildToolbar(), BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.add(buildOptions(), BorderLayout.NORTH);
        south.add(buildActionBar(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
        wireActions();
        updateOutputControls();

        setSize(860, 640);
        setMinimumSize(new java.awt.Dimension(720, 520));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- UI

    private void buildTable() {
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        table.setTransferHandler(new FileDropHandler());

        setWidth(PdfTableModel.COL_NAME, 380);
        setWidth(PdfTableModel.COL_SIZE, 80);
        setWidth(PdfTableModel.COL_PASSWORD, 130);
        setWidth(PdfTableModel.COL_STATUS, 150);

        TableColumn pwdCol = table.getColumnModel().getColumn(PdfTableModel.COL_PASSWORD);
        pwdCol.setCellEditor(new DefaultCellEditor(new JPasswordField()));
        pwdCol.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value == null || value.toString().isEmpty() ? "" : "••••••••");
            }
        });
        table.getColumnModel()
            .getColumn(PdfTableModel.COL_SIZE)
            .setCellRenderer(new DefaultTableCellRenderer() {
            {
                setHorizontalAlignment(RIGHT);
            }
        });
        table.getColumnModel()
            .getColumn(PdfTableModel.COL_STATUS)
            .setCellRenderer(new StatusRenderer());

        // Delete key removes the selected rows
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("DELETE"), "removeRows");
        table.getActionMap().put("removeRows", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeSelected();
            }
        });
    }

    private void setWidth(int col, int width) {
        table.getColumnModel().getColumn(col).setPreferredWidth(width);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.add(addFilesButton);
        bar.add(addFolderButton);
        bar.add(removeButton);
        bar.add(clearButton);
        bar.add(aboutButton);
        return bar;
    }
/*
    private JPanel buildOptions() {
        // GridLayout gives every row the full width of the panel.
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.t("panel.options.title")));
*/
    private JPanel buildOptions() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.t("panel.options.title")));

        JPanel pwdRow = row();
        pwdRow.add(new JLabel(I18n.t("label.openingPassword")));
        pwdRow.add(defaultPassword);
        pwdRow.add(showPassword);

        JPanel hintRow = row();
//        JLabel hint = new JLabel(I18n.t("label.passwordHint"));
        JTextArea hint = new JTextArea(I18n.t("label.passwordHint"));
        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setFocusable(false);
        hint.setLineWrap(false);
        hint.setWrapStyleWord(false);
        hint.setBorder(null);
        hint.setFont(UIManager.getFont("Label.font"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hintRow.add(hint);

        ButtonGroup group = new ButtonGroup();
        group.add(sameFolder);
        group.add(otherFolder);
        JPanel folderRow = row();
        folderRow.add(new JLabel(I18n.t("label.saveTo")));
        folderRow.add(sameFolder);
        folderRow.add(otherFolder);
        folderRow.add(outDirField);
        folderRow.add(browseOutDir);

        JPanel nameRow = row();
        nameRow.add(new JLabel(I18n.t("label.suffix")));
        nameRow.add(suffixField);
        nameRow.add(overwrite);

        panel.add(pwdRow);
        panel.add(hintRow);
        panel.add(folderRow);
        panel.add(nameRow);
        return panel;
    }

    private static JPanel row() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 6));

        // Status text on its own full-width line so long summaries are never cut off.
        bar.add(statusLabel, BorderLayout.NORTH);

        progress.setStringPainted(false);
        JPanel progressWrap = new JPanel(new BorderLayout());
        progressWrap.add(progress, BorderLayout.CENTER);
        bar.add(progressWrap, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        cancelButton.setEnabled(false);
        buttons.add(openFolderButton);
        buttons.add(cancelButton);
        buttons.add(startButton);
        bar.add(buttons, BorderLayout.EAST);

        getRootPane().setDefaultButton(startButton);
        return bar;
    }

    private void wireActions() {
        addFilesButton.addActionListener(e -> chooseFiles());
        addFolderButton.addActionListener(e -> chooseFolder());
        removeButton.addActionListener(e -> removeSelected());
        clearButton.addActionListener(e -> {
            model.clear();
            progress.setValue(0);
            statusLabel.setText(I18n.t("status.listCleared"));
        });
        aboutButton.addActionListener(e -> showAboutDialog());

        showPassword.addActionListener(e ->
                defaultPassword.setEchoChar(showPassword.isSelected() ? (char) 0 : '•'));
        sameFolder.addActionListener(e -> updateOutputControls());
        otherFolder.addActionListener(e -> updateOutputControls());
        overwrite.addActionListener(e -> updateOutputControls());
        browseOutDir.addActionListener(e -> chooseOutputDir());

        startButton.addActionListener(e -> startUnlock());
        cancelButton.addActionListener(e -> {
            if (worker != null) {
                worker.cancel(false);
                cancelButton.setEnabled(false);
                statusLabel.setText(I18n.t("status.cancelling"));
            }
        });
        openFolderButton.addActionListener(e -> openOutputFolder());
    }

    private void showAboutDialog() {
        String text = APP_NAME + " " + VERSION
                + "\n\n"
                + I18n.t("about.body")
                + "\n\n"
                + COPYRIGHT
                + "\n"
                + PROJECT_URL;

        text = text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br>");

        text = text.replace(
                PROJECT_URL,
                "<a href=\"" + PROJECT_URL + "\">" + PROJECT_URL + "</a>"
        );

        JEditorPane pane = new JEditorPane(
                "text/html",
                "<html><body>" + text + "</body></html>"
        );
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(UIManager.getFont("Label.font"));

        pane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(event.getURL().toURI());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            this,
                            event.getURL().toString(),
                            APP_NAME,
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        ImageIcon icon = new ImageIcon(
                MainFrame.class.getResource("/icons/pdf-unlocker_64px.png")
        );

        JOptionPane.showMessageDialog(
                this,
                pane,
                I18n.t("about.title"),
                JOptionPane.PLAIN_MESSAGE,
                icon
        );
    }

    private void updateOutputControls() {
        boolean over = overwrite.isSelected();
        sameFolder.setEnabled(!over);
        otherFolder.setEnabled(!over);
        suffixField.setEnabled(!over);
        boolean custom = !over && otherFolder.isSelected();
        outDirField.setEnabled(custom);
        browseOutDir.setEnabled(custom);
    }

    private void setRunning(boolean running) {
        for (JComponent c : new JComponent[] {addFilesButton, addFolderButton, removeButton,
                clearButton, startButton, defaultPassword, showPassword, overwrite, table}) {
            c.setEnabled(!running);
        }
        cancelButton.setEnabled(running);
        if (running) {
            sameFolder.setEnabled(false);
            otherFolder.setEnabled(false);
            suffixField.setEnabled(false);
            outDirField.setEnabled(false);
            browseOutDir.setEnabled(false);
        } else {
            sameFolder.setEnabled(true);
            otherFolder.setEnabled(true);
            suffixField.setEnabled(true);
            updateOutputControls();
        }
    }

    // ------------------------------------------------------- adding files

    private void chooseFiles() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter(I18n.t("filechooser.pdfFilter"), "pdf"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<Path> paths = new ArrayList<>();
            for (File f : fc.getSelectedFiles()) {
                paths.add(f.toPath());
            }
            addPaths(paths);
        }
    }

    private void chooseFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(I18n.t("filechooser.chooseFolderTitle"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addPaths(List.of(fc.getSelectedFile().toPath()));
        }
    }

    private void chooseOutputDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outDirField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    /** Adds PDFs; directories are scanned recursively. */
    void addPaths(List<Path> paths) {
        int added = 0;
        for (Path raw : paths) {
            Path p = raw.toAbsolutePath().normalize();
            List<Path> found = new ArrayList<>();
            if (Files.isDirectory(p)) {
                try (Stream<Path> walk = Files.walk(p)) {
                    walk.filter(Files::isRegularFile)
                        .filter(MainFrame::isPdf)
                        .sorted()
                        .forEach(found::add);
                } catch (IOException | UncheckedIOException ex) {
                    statusLabel.setText(I18n.t("status.folderUnreadable", p));
                }
            } else if (Files.isRegularFile(p) && isPdf(p)) {
                found.add(p);
            }
            for (Path f : found) {
                try {
                    if (model.add(f, Files.size(f))) {
                        added++;
                    }
                } catch (IOException ex) {
                    // unreadable file: skip it
                }
            }
        }
        statusLabel.setText(added == 0
                ? I18n.t("status.noNewPdf")
                : I18n.t("status.filesAdded", added, model.getRowCount()));
    }

    private static boolean isPdf(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private void removeSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length > 0 && table.isEnabled()) {
            if (table.isEditing()) {
                table.getCellEditor().cancelCellEditing();
            }
            model.remove(rows);
        }
    }

    // ------------------------------------------------------------ unlocking

    private record Job(PdfEntry entry, Path output, String password) { }

    private record Update(
        PdfEntry entry, State state, String message, int completed, Path output) { }

    private void startUnlock() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        if (model.getRowCount() == 0) {
            statusLabel.setText(I18n.t("status.needAtLeastOneFile"));
            return;
        }
        boolean over = overwrite.isSelected();
        Path customDir = null;
        if (!over && otherFolder.isSelected()) {
            if (outDirField.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, I18n.t("dialog.chooseOutputFolder"), APP_NAME,
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                customDir = Path.of(outDirField.getText().trim()).toAbsolutePath();
            } catch (InvalidPathException ex) {
                JOptionPane.showMessageDialog(this, I18n.t("dialog.invalidOutputFolder"), APP_NAME,
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (over && JOptionPane.showConfirmDialog(this,
                I18n.t("dialog.overwriteConfirm"),
                APP_NAME, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        String globalPassword = new String(defaultPassword.getPassword());
        String suffix = suffixField.getText();
        Set<Path> reserved = new HashSet<>();
        List<Job> jobs = new ArrayList<>();

        for (PdfEntry e : model.entries()) {
            if (e.state == State.UNLOCKED || e.state == State.NOT_PROTECTED) {
                continue;
            }
            Path out;
            if (over) {
                out = e.path;
            } else {
                Path dir = customDir != null ? customDir : e.path.getParent();
                String name = e.fileName();
                String base = name.substring(0, name.length() - 4);
                out = uniquePath(dir.resolve(base + suffix + ".pdf"), reserved);
            }
            reserved.add(out);
            jobs.add(new Job(e, out, e.password.isEmpty() ? globalPassword : e.password));
        }

        if (jobs.isEmpty()) {
            statusLabel.setText(I18n.t("status.allProcessed"));
            return;
        }

        progress.setMinimum(0);
        progress.setMaximum(jobs.size());
        progress.setValue(0);
        setRunning(true);
        worker = new UnlockWorker(jobs);
        worker.execute();
    }

    private static Path uniquePath(Path wanted, Set<Path> reserved) {
        Path candidate = wanted;
        String name = wanted.getFileName().toString();
        String base = name.substring(0, name.length() - 4);
        int n = 2;
        while (Files.exists(candidate) || reserved.contains(candidate)) {
            candidate = wanted.resolveSibling(base + " (" + n++ + ").pdf");
        }
        return candidate;
    }

    private final class UnlockWorker extends SwingWorker<Void, Update> {
        private final List<Job> jobs;
        private int unlocked;
        private int skipped;
        private int needPassword;
        private int errors;

        UnlockWorker(List<Job> jobs) {
            this.jobs = jobs;
        }

        @Override
        protected Void doInBackground() {
            for (int i = 0; i < jobs.size() && !isCancelled(); i++) {
                Job job = jobs.get(i);
                // Qualified on purpose: inside a SwingWorker, plain "State" is
                // Future.State (Java 19+).
                publish(new Update(job.entry(), PdfEntry.State.WORKING, "", i, null));
                PdfUnlocker.Result r = PdfUnlocker.unlock(
                    job.entry().path, job.output(), job.password()
                );
                PdfEntry.State state = switch (r.outcome()) {
                    case UNLOCKED -> PdfEntry.State.UNLOCKED;
                    case NOT_PROTECTED -> PdfEntry.State.NOT_PROTECTED;
                    case PASSWORD_REQUIRED -> PdfEntry.State.NEEDS_PASSWORD;
                    case ERROR -> PdfEntry.State.ERROR;
                };
                publish(new Update(job.entry(), state, r.message(), i + 1,
                        r.outcome() == PdfUnlocker.Outcome.UNLOCKED ? job.output() : null));
            }
            return null;
        }

        @Override
        protected void process(List<Update> chunks) {
            for (Update u : chunks) {
                u.entry().state = u.state();
                u.entry().message = u.message();
                model.entryChanged(u.entry());
                progress.setValue(u.completed());
                switch (u.state()) {
                    case UNLOCKED -> unlocked++;
                    case NOT_PROTECTED -> skipped++;
                    case NEEDS_PASSWORD -> needPassword++;
                    case ERROR -> errors++;
                    default -> { }
                }
                if (u.output() != null) {
                    lastOutputDir = u.output().getParent();
                }
            }
        }

        @Override
        protected void done() {
            setRunning(false);
            String prefix = isCancelled()
                ? I18n.t("status.prefixCancelled")
                : I18n.t("status.prefixDone");
            String summary = I18n.t("status.summary", unlocked, skipped, needPassword, errors);
            statusLabel.setText(prefix + " " + summary
                    + (needPassword > 0 ? I18n.t("status.retryPasswordHint") : ""));
            worker = null;
        }
    }

    private void openOutputFolder() {
        Path dir = lastOutputDir;
        if (dir == null && !overwrite.isSelected() && otherFolder.isSelected()
                && !outDirField.getText().isBlank()) {
            dir = Path.of(outDirField.getText().trim());
        }
        if (dir == null && model.getRowCount() > 0) {
            dir = model.get(0).path.getParent();
        }
        if (dir == null || !Files.isDirectory(dir)) {
            statusLabel.setText(I18n.t("status.noOutputFolderYet"));
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                statusLabel.setText(I18n.t("status.outputFolder", dir));
            }
        } catch (IOException ex) {
            statusLabel.setText(I18n.t("status.outputFolder", dir));
        }
    }

    // --------------------------------------------------------- helper types

    /** JTable that paints a hint when it is empty. */
    private static final class HintTable extends JTable {
        HintTable(PdfTableModel model) {
            super(model);
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int col = columnAtPoint(event.getPoint());
            if (row < 0 || col < 0) {
                return null;
            }
            PdfEntry e = ((PdfTableModel) getModel()).get(convertRowIndexToModel(row));
            return switch (convertColumnIndexToModel(col)) {
                case PdfTableModel.COL_NAME -> e.path.toString();
                case PdfTableModel.COL_STATUS -> e.message.isEmpty() ? null : e.message;
                default -> null;
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() == 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color c = UIManager.getColor("Label.disabledForeground");
                g2.setColor(c != null ? c : Color.GRAY);
                g2.setFont(getFont().deriveFont(Font.PLAIN, getFont().getSize() + 3f));
                String text = I18n.t("table.dropHint");
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = getParent() != null ? getParent().getHeight() / 2 : getHeight() / 2;
                g2.drawString(text, Math.max(x, 8), y);
                g2.dispose();
            }
        }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (!selected) {
                PdfEntry e = model.get(t.convertRowIndexToModel(row));
                setForeground(switch (e.state) {
                    case UNLOCKED -> new Color(0x1E8E3E);
                    case NEEDS_PASSWORD -> new Color(0xB06000);
                    case ERROR -> new Color(0xD93025);
                    default -> t.getForeground();
                });
            }
            return this;
        }
    }

    /** Accepts files and folders dropped from the OS file manager. */
    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return table.isEnabled()
                && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Object data = support.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
                List<Path> paths = new ArrayList<>();
                if (data instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof File f) {
                            paths.add(f.toPath());
                        }
                    }
                }
                addPaths(paths);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
