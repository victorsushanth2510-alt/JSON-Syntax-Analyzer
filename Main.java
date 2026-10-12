import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JFrame {

    private static final Color BACKGROUND = new Color(245, 247, 251);
    private static final Color SURFACE = Color.WHITE;
    private static final Color NAVY = new Color(20, 39, 62);
    private static final Color BLUE = new Color(45, 105, 210);
    private static final Color SUCCESS = new Color(23, 124, 81);
    private static final Color ERROR = new Color(195, 54, 54);
    private static final Color WARNING = new Color(190, 125, 24);
    private static final Color TEXT = new Color(42, 52, 69);
    private static final Color MUTED = new Color(103, 115, 131);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private JTextPane inputArea;
    private JLabel statusLabel;
    private JLabel fileLabel;
    private JLabel documentInfoLabel;
    private JTextArea resultArea;
    private JTree treeView;
    private JTable tokenTable;
    private DefaultTableModel tokenModel;
    private JTextArea statsArea;
    private JTextArea historyArea;
    private JTextField pathField;
    private final List<String> history = new ArrayList<>();
    private List<Lexer.Token> lastTokens = new ArrayList<>();
    private Parser lastParser;
    private JsonNode lastRoot;
    private File currentFile;
    private Timer highlightTimer;
    private boolean applyingSyntaxHighlighting;

    Main() {
        setTitle("JSON Syntax & Structure Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));
        setContentPane(root);

        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createCenterPanel(), BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);

        installKeyboardShortcuts();
        setSampleJSON();
        analyzeJSON(false);
    }

    private void setSampleJSON() {
        inputArea.setText("{\n" +
                "  \"student\": {\n" +
                "    \"name\": \"Alice\",\n" +
                "    \"age\": 20,\n" +
                "    \"status\": \"active\"\n" +
                "  },\n" +
                "  \"courses\": [\"Java\", \"Algorithms\"],\n" +
                "  \"passed\": true,\n" +
                "  \"meta\": null\n" +
                "}");
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(NAVY);
        header.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel title = new JLabel("JSON Syntax & Structure Analyzer");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 25));

        JLabel subtitle = new JLabel("Validate • Format • Minify • Explore structure • Detect issues");
        subtitle.setForeground(new Color(218, 226, 236));
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(subtitle);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        rightPanel.setOpaque(false);
        statusLabel = new JLabel("NOT ANALYZED");
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(138, 150, 161));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        statusLabel.setBorder(new EmptyBorder(8, 12, 8, 12));
        rightPanel.add(statusLabel);

        header.add(textPanel, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JComponent createCenterPanel() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createEditorPanel(), createResultsPanel());
        split.setResizeWeight(0.52);
        split.setDividerSize(10);
        split.setBorder(null);
        return split;
    }

    private JPanel createEditorPanel() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 12));

        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        JLabel title = sectionLabel("JSON INPUT");
        fileLabel = new JLabel("No file selected");
        fileLabel.setForeground(MUTED);
        fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.add(title, BorderLayout.NORTH);
        heading.add(fileLabel, BorderLayout.SOUTH);

        inputArea = new JTextPane();
        inputArea.setEditable(true);
        inputArea.setFont(MONO);
        inputArea.setForeground(TEXT);
        inputArea.setBackground(new Color(250, 252, 255));
        inputArea.setCaretColor(BLUE);
        inputArea.setMargin(new Insets(12, 12, 12, 12));
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 226, 236)),
                new EmptyBorder(0, 0, 0, 0)));
        inputArea.setEditorKit(new javax.swing.text.StyledEditorKit());
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(222, 228, 238)));

        documentInfoLabel = new JLabel("Characters: 0   Lines: 1");
        documentInfoLabel.setForeground(MUTED);
        documentInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel controls = new JPanel(new GridLayout(2, 4, 8, 8));
        controls.setOpaque(false);

        JButton chooseFile = createButton("Choose JSON File", true);
        JButton saveJSON = createButton("Save JSON", false);
        JButton formatBtn = createButton("Format", false);
        JButton minifyBtn = createButton("Minify", false);
        JButton analyzeBtn = createButton("Analyze JSON", true);
        JButton clearBtn = createButton("Clear", false);
        JButton copyBtn = createButton("Copy Results", false);
        JButton sampleBtn = createButton("Load Sample", false);

        chooseFile.addActionListener(e -> chooseJsonFile());
        saveJSON.addActionListener(e -> saveJsonFile());
        formatBtn.addActionListener(e -> formatJSON());
        minifyBtn.addActionListener(e -> minifyJSON());
        analyzeBtn.addActionListener(e -> analyzeJSON(false));
        clearBtn.addActionListener(e -> clearEditor());
        copyBtn.addActionListener(e -> copyResults());
        sampleBtn.addActionListener(e -> loadSampleJSON());

        controls.add(chooseFile);
        controls.add(saveJSON);
        controls.add(formatBtn);
        controls.add(minifyBtn);
        controls.add(analyzeBtn);
        controls.add(clearBtn);
        controls.add(copyBtn);
        controls.add(sampleBtn);

        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));
        pathPanel.setOpaque(false);
        JLabel pathLabel = new JLabel("JSON Path");
        pathLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        pathLabel.setForeground(NAVY);
        pathField = new JTextField("$");
        pathField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JButton pathFind = createButton("Find Value", false);
        pathFind.addActionListener(e -> findValueByPath());
        pathPanel.add(pathLabel, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(pathFind, BorderLayout.EAST);

        card.add(heading, BorderLayout.NORTH);
        card.add(inputScroll, BorderLayout.CENTER);
        card.add(documentInfoLabel, BorderLayout.SOUTH);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(pathPanel);
        bottom.add(Box.createVerticalStrut(10));
        bottom.add(controls);

        card.add(bottom, BorderLayout.SOUTH);

        inputArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { scheduleHighlight(); updateDocumentInfo(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { scheduleHighlight(); updateDocumentInfo(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                if (!applyingSyntaxHighlighting) {
                    scheduleHighlight();
                }
            }
        });

        return card;
    }

    private JPanel createResultsPanel() {
        JPanel card = createCardPanel();
        card.setLayout(new BorderLayout(0, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(MONO);
        resultArea.setForeground(TEXT);
        resultArea.setBackground(new Color(250, 252, 255));
        resultArea.setLineWrap(false);
        resultArea.setWrapStyleWord(false);
        resultArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane resultsScroll = new JScrollPane(resultArea);
        tabs.addTab("Results", resultsScroll);

        tokenModel = new DefaultTableModel(new Object[] {"#", "Type", "Value", "Line", "Column"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tokenTable = new JTable(tokenModel);
        tokenTable.setRowHeight(26);
        tokenTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tokenTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        tokenTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        tokenTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        tokenTable.getColumnModel().getColumn(2).setPreferredWidth(230);
        tokenTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        tokenTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        JScrollPane tokensScroll = new JScrollPane(tokenTable);
        tabs.addTab("Tokens", tokensScroll);

        DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("No valid analysis available");
        treeView = new JTree(emptyRoot);
        treeView.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JScrollPane treeScroll = new JScrollPane(treeView);
        tabs.addTab("Structure Tree", treeScroll);

        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statsArea.setBackground(new Color(250, 252, 255));
        statsArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane statsScroll = new JScrollPane(statsArea);
        tabs.addTab("Statistics", statsScroll);

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        historyArea.setBackground(new Color(250, 252, 255));
        historyArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane historyScroll = new JScrollPane(historyArea);
        tabs.addTab("History", historyScroll);

        card.add(tabs, BorderLayout.CENTER);
        return card;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(BACKGROUND);
        footer.setBorder(new EmptyBorder(0, 8, 0, 8));

        JLabel left = new JLabel("Java Swing • Custom Lexer/Parser • JSON analysis tool");
        left.setForeground(MUTED);
        left.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JLabel right = new JLabel("JSON Analyzer");
        right.setForeground(MUTED);
        right.setFont(new Font("SansSerif", Font.BOLD, 11));

        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 233, 241)),
                new EmptyBorder(14, 14, 14, 14)));
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(NAVY);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setBorder(new EmptyBorder(9, 12, 9, 12));
        if (primary) {
            button.setBackground(BLUE);
            button.setForeground(Color.WHITE);
            button.setOpaque(true);
            button.setBorderPainted(false);
        } else {
            button.setBackground(new Color(237, 242, 250));
            button.setForeground(NAVY);
            button.setOpaque(true);
            button.setBorderPainted(false);
        }
        return button;
    }

    private void installKeyboardShortcuts() {
        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "analyze");
        inputArea.getActionMap().put("analyze", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                analyzeJSON(false);
            }
        });

        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "open");
        inputArea.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                chooseJsonFile();
            }
        });

        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        inputArea.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveJsonFile();
            }
        });

        inputArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "clear");
        inputArea.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearEditor();
            }
        });
    }

    private void updateDocumentInfo() {
        String text = inputArea.getText();
        int chars = text.length();
        int lines = Math.max(1, text.split("\\R", -1).length);
        documentInfoLabel.setText("Characters: " + chars + "   Lines: " + lines);
    }

    private void scheduleHighlight() {
        if (highlightTimer != null) {
            highlightTimer.stop();
        }
        highlightTimer = new Timer(250, e -> applySyntaxHighlighting());
        highlightTimer.setRepeats(false);
        highlightTimer.start();
    }

    private void applySyntaxHighlighting() {
        StyledDocument doc = inputArea.getStyledDocument();
        String text = inputArea.getText();
        if (text.isEmpty()) {
            return;
        }

        int caretDot = inputArea.getCaret().getDot();
        int caretMark = inputArea.getCaret().getMark();
        applyingSyntaxHighlighting = true;
        try {
            SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(defaultStyle, "Monospaced");
            StyleConstants.setFontSize(defaultStyle, 13);
            StyleConstants.setForeground(defaultStyle, TEXT);
            doc.setCharacterAttributes(0, text.length(), defaultStyle, true);

            SimpleAttributeSet punctuation = new SimpleAttributeSet(defaultStyle);
            StyleConstants.setForeground(punctuation, new Color(140, 145, 160));

            SimpleAttributeSet keyStyle = new SimpleAttributeSet(defaultStyle);
            StyleConstants.setForeground(keyStyle, new Color(39, 117, 182));
            StyleConstants.setBold(keyStyle, true);

            SimpleAttributeSet stringStyle = new SimpleAttributeSet(defaultStyle);
            StyleConstants.setForeground(stringStyle, new Color(31, 120, 71));

            SimpleAttributeSet numberStyle = new SimpleAttributeSet(defaultStyle);
            StyleConstants.setForeground(numberStyle, new Color(174, 90, 17));

            SimpleAttributeSet literalStyle = new SimpleAttributeSet(defaultStyle);
            StyleConstants.setForeground(literalStyle, new Color(122, 73, 167));

            Pattern stringPattern = Pattern.compile("\"(?:\\\\.|[^\\\"\\\\])*\"");
            Matcher stringMatcher = stringPattern.matcher(text);
            while (stringMatcher.find()) {
                doc.setCharacterAttributes(stringMatcher.start(), stringMatcher.group().length(), stringStyle, false);
            }

            Pattern keyPattern = Pattern.compile("\"(?:\\\\.|[^\\\"\\\\])*\"\\s*:");
            Matcher keyMatcher = keyPattern.matcher(text);
            while (keyMatcher.find()) {
                String keyMatch = keyMatcher.group();
                int keyStart = keyMatcher.start();
                int colonIndex = keyMatch.lastIndexOf(':');
                int keyLength = colonIndex > 0 ? colonIndex : keyMatch.length();
                doc.setCharacterAttributes(keyStart, keyLength, keyStyle, false);
            }

            Pattern numberPattern = Pattern.compile("(?<![A-Za-z0-9_])[-+]?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][-+]?\\d+)?");
            Matcher numberMatcher = numberPattern.matcher(text);
            while (numberMatcher.find()) {
                doc.setCharacterAttributes(numberMatcher.start(), numberMatcher.group().length(), numberStyle, false);
            }

            Pattern literalPattern = Pattern.compile("\\b(?:true|false|null)\\b");
            Matcher literalMatcher = literalPattern.matcher(text);
            while (literalMatcher.find()) {
                doc.setCharacterAttributes(literalMatcher.start(), literalMatcher.group().length(), literalStyle, false);
            }

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if ("{}[],:".indexOf(c) >= 0) {
                    doc.setCharacterAttributes(i, 1, punctuation, false);
                }
            }
        } finally {
            inputArea.getCaret().setDot(caretMark);
            inputArea.getCaret().moveDot(caretDot);
            applyingSyntaxHighlighting = false;
        }
    }

    private void chooseJsonFile() {
        if (!isCurrentEditorClean()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Discard the current JSON input before opening another file?",
                    "Open JSON file",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        try {
            String content = Files.readString(selected.toPath(), StandardCharsets.UTF_8);
            inputArea.setText(content);
            currentFile = selected;
            fileLabel.setText("Selected: " + selected.getName());
            statusLabel.setText("LOADED");
            setStatusColor(MUTED);
            resultArea.setText("JSON file loaded from " + selected.getAbsolutePath() + "\n\nReady to analyze.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Unable to read the selected file:\n" + ex.getMessage(), "File read error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveJsonFile() {
        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is no JSON content to save.", "Save JSON", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.setSelectedFile(currentFile != null ? currentFile : new File("document.json"));
        int option = chooser.showSaveDialog(this);
        if (option != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".json")) {
            target = new File(target.getAbsolutePath() + ".json");
        }

        try {
            Files.writeString(target.toPath(), inputArea.getText(), StandardCharsets.UTF_8);
            currentFile = target;
            fileLabel.setText("Selected: " + target.getName());
            resultArea.setText("JSON saved successfully to:\n" + target.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Unable to save JSON:\n" + ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearEditor() {
        inputArea.setText("");
        resultArea.setText("Editor cleared.\n\nNo analysis available.");
        tokenModel.setRowCount(0);
        treeView.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No valid analysis available")));
        statsArea.setText("No statistics available.");
        pathField.setText("$");
        lastParser = null;
        lastRoot = null;
        lastTokens.clear();
        statusLabel.setText("NOT ANALYZED");
        setStatusColor(MUTED);
        currentFile = null;
        fileLabel.setText("No file selected");
        documentInfoLabel.setText("Characters: 0   Lines: 1");
    }

    private boolean isCurrentEditorClean() {
        String currentText = inputArea.getText();
        return currentText == null || currentText.trim().isEmpty();
    }

    private void analyzeJSON(boolean silent) {
        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) {
            resultArea.setText("Please enter JSON to analyze.");
            statusLabel.setText("NO INPUT");
            setStatusColor(WARNING);
            return;
        }

        new SwingWorker<AnalysisResult, Void>() {
            @Override
            protected AnalysisResult doInBackground() {
                try {
                    Lexer lexer = new Lexer(text);
                    List<Lexer.Token> tokens = lexer.tokenize();
                    Parser parser = new Parser(tokens);
                    JsonNode root = parser.parse();
                    return new AnalysisResult(tokens, parser, root, true, null, null);
                } catch (Exception ex) {
                    return new AnalysisResult(new ArrayList<>(), null, null, false, ex.getMessage(), classifyError(ex.getMessage()));
                }
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    if (result.valid) {
                        lastTokens = result.tokens;
                        lastParser = result.parser;
                        lastRoot = result.root;
                        renderValidAnalysis(result);
                    } else {
                        lastParser = null;
                        lastRoot = null;
                        lastTokens = new ArrayList<>();
                        renderInvalidAnalysis(result);
                    }
                    recordHistory(result);
                } catch (Exception ex) {
                    resultArea.setText("An unexpected error occurred while analyzing the JSON.\n\n" + ex.getMessage());
                    statusLabel.setText("ERROR");
                    setStatusColor(ERROR);
                }
            }
        }.execute();
    }

    private void renderValidAnalysis(AnalysisResult result) {
        Parser.Statistics stats = result.parser.getStatistics();
        StringBuilder summary = new StringBuilder();
        summary.append("VALID JSON\n");
        summary.append("================================\n\n");
        summary.append("Lexical analysis: PASS\n");
        summary.append("Syntax analysis: PASS\n");
        summary.append("Duplicate keys: ").append(result.parser.getDuplicateKeyWarnings().isEmpty() ? "none" : result.parser.getDuplicateKeyWarnings().size() + " warning(s)").append("\n\n");
        summary.append("Structure statistics\n");
        summary.append("--------------------------------\n");
        summary.append(String.format("%-24s %d%n", "Objects:", stats.totalObjects));
        summary.append(String.format("%-24s %d%n", "Arrays:", stats.totalArrays));
        summary.append(String.format("%-24s %d%n", "Object members:", stats.totalObjectMembers));
        summary.append(String.format("%-24s %d%n", "Object keys:", stats.totalObjectKeys));
        summary.append(String.format("%-24s %d%n", "Array elements:", stats.totalArrayElements));
        summary.append(String.format("%-24s %d%n", "String values:", stats.totalStringValues));
        summary.append(String.format("%-24s %d%n", "Number values:", stats.totalNumericValues));
        summary.append(String.format("%-24s %d%n", "Boolean values:", stats.totalBooleanValues));
        summary.append(String.format("%-24s %d%n", "Null values:", stats.totalNullValues));
        summary.append(String.format("%-24s %d%n", "Primitive values:", stats.totalPrimitiveValues));
        summary.append(String.format("%-24s %d%n", "Empty objects:", stats.emptyObjects));
        summary.append(String.format("%-24s %d%n", "Empty arrays:", stats.emptyArrays));
        summary.append(String.format("%-24s %d%n", "Maximum depth:", stats.maxNestingDepth));
        summary.append(String.format("%-24s %d%n", "Token count:", stats.totalTokens));
        summary.append(String.format("%-24s %d%n", "Input characters:", stats.totalInputCharacters));
        summary.append(String.format("%-24s %d%n", "Non-whitespace chars:", stats.totalNonWhitespaceCharacters));
        summary.append(String.format("%-24s %d%n", "Max array length:", stats.maxArrayLength));
        summary.append(String.format("%-24s %d%n", "Max object members:", stats.maxObjectMembers));
        summary.append("\n");
        if (!result.parser.getDuplicateKeyWarnings().isEmpty()) {
            summary.append("Duplicate key warnings:\n");
            for (String warning : result.parser.getDuplicateKeyWarnings()) {
                summary.append("- ").append(warning).append("\n");
            }
        }

        resultArea.setText(summary.toString());
        statusLabel.setText("VALID JSON");
        setStatusColor(SUCCESS);

        updateTokenTable(result.tokens);
        renderTree(lastRoot);
        renderStatistics(stats);
    }

    private void renderInvalidAnalysis(AnalysisResult result) {
        StringBuilder errorOutput = new StringBuilder();
        errorOutput.append("INVALID JSON\n");
        errorOutput.append("================================\n\n");
        errorOutput.append("Error category: ").append(result.errorCategory == null ? "unknown" : result.errorCategory).append("\n");
        errorOutput.append("Message: ").append(result.message).append("\n\n");
        errorOutput.append("Please correct the malformed JSON and run analysis again.\n");
        resultArea.setText(errorOutput.toString());
        statusLabel.setText("INVALID JSON");
        setStatusColor(ERROR);
        tokenModel.setRowCount(0);
        treeView.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No valid analysis available")));
        statsArea.setText("No statistics available because the document is invalid.");
    }

    private void updateTokenTable(List<Lexer.Token> tokens) {
        tokenModel.setRowCount(0);
        if (tokens == null || tokens.isEmpty()) {
            tokenModel.addRow(new Object[]{"-", "Info", "No tokens available", "-", "-"});
            return;
        }
        int counter = 1;
        for (Lexer.Token token : tokens) {
            if (token == null || "EOF".equals(token.getType())) {
                continue;
            }
            tokenModel.addRow(new Object[]{counter++, token.getType(), token.getValue(), token.getLine(), token.getColumn()});
        }
        if (tokenModel.getRowCount() == 0) {
            tokenModel.addRow(new Object[]{"-", "Info", "No lexical tokens produced", "-", "-"});
        }
    }

    private void renderTree(JsonNode root) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("root");
        if (root == null) {
            rootNode = new DefaultMutableTreeNode("No valid analysis available");
        } else {
            rootNode.add(buildTreeNode(root));
        }
        treeView.setModel(new DefaultTreeModel(rootNode));
        treeView.expandRow(0);
    }

    private DefaultMutableTreeNode buildTreeNode(JsonNode node) {
        if (node == null) {
            return new DefaultMutableTreeNode("null");
        }

        String label;
        if (node.isObject()) {
            label = "Object" + " [members=" + node.getChildren().size() + "]";
        } else if (node.isArray()) {
            label = "Array" + " [items=" + node.getChildren().size() + "]";
        } else if (node.getType() == JsonNode.NodeType.STRING) {
            label = "String: \"" + node.getValue() + "\"";
        } else if (node.getType() == JsonNode.NodeType.NUMBER) {
            label = "Number: " + node.getValue();
        } else if (node.getType() == JsonNode.NodeType.BOOLEAN) {
            label = "Boolean: " + node.getValue();
        } else if (node.getType() == JsonNode.NodeType.NULL) {
            label = "Null";
        } else {
            label = node.getTypeLabel();
        }

        DefaultMutableTreeNode current = new DefaultMutableTreeNode(label);
        if (node.isObject()) {
            for (JsonNode child : node.getChildren()) {
                String key = child.getObjectKey() == null ? "member" : child.getObjectKey();
                DefaultMutableTreeNode keyNode = new DefaultMutableTreeNode("Key: " + key);
                keyNode.add(buildTreeNode(child));
                current.add(keyNode);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.getChildren().size(); i++) {
                JsonNode child = node.getChildren().get(i);
                DefaultMutableTreeNode indexNode = new DefaultMutableTreeNode("Index " + i);
                indexNode.add(buildTreeNode(child));
                current.add(indexNode);
            }
        }
        return current;
    }

    private void renderStatistics(Parser.Statistics stats) {
        StringBuilder statsText = new StringBuilder();
        statsText.append("JSON STRUCTURE STATISTICS\n");
        statsText.append("================================\n\n");
        statsText.append(String.format("%-28s %d%n", "Total objects:", stats.totalObjects));
        statsText.append(String.format("%-28s %d%n", "Total arrays:", stats.totalArrays));
        statsText.append(String.format("%-28s %d%n", "Object members:", stats.totalObjectMembers));
        statsText.append(String.format("%-28s %d%n", "Object keys:", stats.totalObjectKeys));
        statsText.append(String.format("%-28s %d%n", "Array elements:", stats.totalArrayElements));
        statsText.append(String.format("%-28s %d%n", "String values:", stats.totalStringValues));
        statsText.append(String.format("%-28s %d%n", "Numeric values:", stats.totalNumericValues));
        statsText.append(String.format("%-28s %d%n", "Boolean values:", stats.totalBooleanValues));
        statsText.append(String.format("%-28s %d%n", "Null values:", stats.totalNullValues));
        statsText.append(String.format("%-28s %d%n", "Primitive values:", stats.totalPrimitiveValues));
        statsText.append(String.format("%-28s %d%n", "Empty objects:", stats.emptyObjects));
        statsText.append(String.format("%-28s %d%n", "Empty arrays:", stats.emptyArrays));
        statsText.append(String.format("%-28s %d%n", "Maximum nesting depth:", stats.maxNestingDepth));
        statsText.append(String.format("%-28s %d%n", "Total tokens:", stats.totalTokens));
        statsText.append(String.format("%-28s %d%n", "Input characters:", stats.totalInputCharacters));
        statsText.append(String.format("%-28s %d%n", "Non-whitespace chars:", stats.totalNonWhitespaceCharacters));
        statsText.append(String.format("%-28s %d%n", "Max array length:", stats.maxArrayLength));
        statsText.append(String.format("%-28s %d%n", "Max object members:", stats.maxObjectMembers));
        statsArea.setText(statsText.toString());
    }

    private void recordHistory(AnalysisResult result) {
        String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        String entry = stamp + " — " + (result.valid ? "VALID" : "INVALID");
        history.add(entry);
        if (history.size() > 12) {
            history.remove(0);
        }
        StringBuilder builder = new StringBuilder();
        for (String item : history) {
            builder.append(item).append("\n");
        }
        historyArea.setText(builder.toString());
    }

    private void formatJSON() {
        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter JSON before formatting.", "Format JSON", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Lexer lexer = new Lexer(text);
            List<Lexer.Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens);
            parser.parse();
            String formatted = parser.prettyPrint();
            inputArea.setText(formatted);
            analyzeJSON(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Formatting failed:\n" + ex.getMessage(), "Format error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void minifyJSON() {
        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter JSON before minifying.", "Minify JSON", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Lexer lexer = new Lexer(text);
            List<Lexer.Token> tokens = lexer.tokenize();
            Parser parser = new Parser(tokens);
            parser.parse();
            String minified = parser.minify();
            inputArea.setText(minified);
            analyzeJSON(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Minification failed:\n" + ex.getMessage(), "Minify error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void findValueByPath() {
        if (lastRoot == null || lastParser == null) {
            JOptionPane.showMessageDialog(this, "The current JSON must be valid before searching by path.", "Path explorer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = pathField.getText();
        if (path == null || path.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a JSON path such as $.student.name or $.courses[0].", "Path explorer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Object value = lastParser.findValueByPath(path);
            String type = value instanceof JsonNode ? ((JsonNode) value).getTypeLabel() : value.getClass().getSimpleName();
            JOptionPane.showMessageDialog(this,
                    "Path: " + path + "\nType: " + type + "\nValue: " + String.valueOf(value),
                    "JSON path result",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Path error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyResults() {
        String content = resultArea.getText();
        if (content == null || content.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is no analysis result to copy.", "Copy results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
        JOptionPane.showMessageDialog(this, "Analysis results were copied to the clipboard.", "Copy results", JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadSampleJSON() {
        inputArea.setText("{\n  \"name\": \"Alice\",\n  \"age\": 20,\n  \"status\": \"active\",\n  \"scores\": [90, 86, 94],\n  \"active\": true,\n  \"meta\": null\n}");
        analyzeJSON(false);
    }

    private void setStatusColor(Color color) {
        statusLabel.setBackground(color);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setOpaque(true);
    }

    private String classifyError(String message) {
        if (message == null) {
            return "Syntax";
        }
        String lower = message.toLowerCase();
        if (lower.contains("unterminated") || lower.contains("invalid escape") || lower.contains("invalid character") || lower.contains("control character") || lower.contains("expected a digit") || lower.contains("leading zeros") || lower.contains("number format") || lower.contains("incomplete")) {
            return "Lexical";
        }
        return "Syntax";
    }

    private static class AnalysisResult {
        private final List<Lexer.Token> tokens;
        private final Parser parser;
        private final JsonNode root;
        private final boolean valid;
        private final String message;
        private final String errorCategory;

        private AnalysisResult(List<Lexer.Token> tokens, Parser parser, JsonNode root, boolean valid, String message, String errorCategory) {
            this.tokens = tokens == null ? new ArrayList<>() : tokens;
            this.parser = parser;
            this.root = root;
            this.valid = valid;
            this.message = message;
            this.errorCategory = errorCategory;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            Main app = new Main();
            app.setVisible(true);
        });
    }
}
