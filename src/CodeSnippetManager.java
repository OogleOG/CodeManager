import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.tree.*;
import java.awt.datatransfer.*;

public class CodeSnippetManager extends JFrame {
    private JTree categoryTree;
    private DefaultTreeModel treeModel;
    private JTextPane codeDisplayPane;
    private JTextField searchField;
    private JList<Snippet> snippetList;
    private DefaultListModel<Snippet> listModel;
    private JComboBox<String> languageFilter;
    private JTextField tagsField;
    private JLabel statusLabel;

    private List<Snippet> allSnippets = new ArrayList<>();
    private Map<String, List<Snippet>> categorizedSnippets = new HashMap<>();
    private Map<String, Color> syntaxColors = new HashMap<>();

    private static final String DATA_FILE = "snippets.dat";
    private static final Color BACKGROUND_COLOR = new Color(45, 45, 48);
    private static final Color SIDEBAR_COLOR = new Color(37, 37, 38);
    private static final Color EDITOR_BACKGROUND = new Color(30, 30, 30);
    private static final Color SELECTION_COLOR = new Color(62, 62, 64);
    private static final Color ACCENT_COLOR = new Color(0, 122, 204);

    public CodeSnippetManager() {
        setTitle("Code Snippet Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initializeSyntaxColors();

        // Create menu bar
        setJMenuBar(createMenuBar());

        // Create main panels - this initializes all UI components including listModel
        add(createToolBar(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        loadSnippets();

        // Set frame properties
        setSize(1400, 800);
        setLocationRelativeTo(null);

//        ImageIcon icon = new ImageIcon(getClass().getResource("icon.png"));
//        setIconImage(icon.getImage());

        // Apply dark theme
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            List<Image> icons = new ArrayList<>();
            int[] sizes = {16,24,32,48,64,128};
            for (int s : sizes) {
                try {
                    icons.add(ImageIO.read(new File("resources/icon_" + s + "x" + s + ".png")));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            setIconImages(icons);
        } catch (Exception e) {
            e.printStackTrace();
        }

        setVisible(true);

        // Save on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveSnippets();
            }
        });
    }

    private void initializeSyntaxColors() {
        syntaxColors.put("keyword", new Color(86, 156, 214));
        syntaxColors.put("string", new Color(206, 145, 120));
        syntaxColors.put("comment", new Color(106, 153, 85));
        syntaxColors.put("number", new Color(181, 206, 168));
        syntaxColors.put("default", new Color(212, 212, 212));
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem newSnippet = new JMenuItem("New Snippet");
        newSnippet.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
        newSnippet.addActionListener(e -> showNewSnippetDialog());

        JMenuItem importItem = new JMenuItem("Import Snippets");
        importItem.addActionListener(e -> importSnippets());

        JMenuItem exportItem = new JMenuItem("Export Snippets");
        exportItem.addActionListener(e -> exportSnippets());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(newSnippet);
        fileMenu.addSeparator();
        fileMenu.add(importItem);
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        JMenuItem editSnippet = new JMenuItem("Edit Snippet");
        editSnippet.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK));
        editSnippet.addActionListener(e -> editSelectedSnippet());

        JMenuItem deleteSnippet = new JMenuItem("Delete Snippet");
        deleteSnippet.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        deleteSnippet.addActionListener(e -> deleteSelectedSnippet());

        editMenu.add(editSnippet);
        editMenu.add(deleteSnippet);

        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refreshItem.addActionListener(e -> refreshView());

        viewMenu.add(refreshItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);

        return menuBar;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(SIDEBAR_COLOR);
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // New snippet button
        JButton newButton = createToolButton("➕ New", "Add new snippet");
        newButton.addActionListener(e -> showNewSnippetDialog());
        toolBar.add(newButton);

        toolBar.addSeparator();

        // Search field
        JLabel searchLabel = new JLabel("Search: ");
        searchLabel.setForeground(Color.WHITE);
        toolBar.add(searchLabel);

        searchField = new JTextField(20);
        searchField.setMaximumSize(new Dimension(200, 30));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                performSearch();
            }
        });
        toolBar.add(searchField);

        toolBar.addSeparator();

        // Language filter
        JLabel langLabel = new JLabel("Language: ");
        langLabel.setForeground(Color.WHITE);
        toolBar.add(langLabel);

        languageFilter = new JComboBox<>(new String[]{
                "All", "Java", "Python", "JavaScript", "SQL", "HTML/CSS", "C++", "C#", "PHP", "Ruby", "Go", "Other"
        });
        languageFilter.setMaximumSize(new Dimension(120, 30));
        languageFilter.addActionListener(e -> filterByLanguage());
        toolBar.add(languageFilter);

        toolBar.addSeparator();

        // Copy button
        JButton copyButton = createToolButton("📋 Copy", "Copy snippet to clipboard");
        copyButton.addActionListener(e -> copySelectedSnippet());
        toolBar.add(copyButton);

        // Edit button
        JButton editButton = createToolButton("✏️ Edit", "Edit selected snippet");
        editButton.addActionListener(e -> editSelectedSnippet());
        toolBar.add(editButton);

        // Delete button
        JButton deleteButton = createToolButton("🗑️ Delete", "Delete selected snippet");
        deleteButton.addActionListener(e -> deleteSelectedSnippet());
        toolBar.add(deleteButton);

        return toolBar;
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBackground(ACCENT_COLOR);
        button.setForeground(Color.WHITE);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JSplitPane createMainContent() {
        // Left panel - Categories tree
        JPanel leftPanel = createCategoryPanel();

        // Middle panel - Snippets list
        JPanel middlePanel = createSnippetListPanel();

        // Right panel - Code display
        JPanel rightPanel = createCodeDisplayPanel();

        // Create split panes
        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, middlePanel);
        leftSplit.setDividerLocation(200);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, rightPanel);
        mainSplit.setDividerLocation(550);

        return mainSplit;
    }

    private JPanel createCategoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SIDEBAR_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));

        JLabel label = new JLabel("Categories");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(Color.WHITE);
        panel.add(label, BorderLayout.NORTH);

        // Create tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("All Snippets");

        // Add language nodes
        String[] languages = {"Java", "Python", "JavaScript", "SQL", "HTML/CSS", "C++", "C#", "PHP", "Ruby", "Go", "Other"};
        for (String lang : languages) {
            root.add(new DefaultMutableTreeNode(lang));
        }

        treeModel = new DefaultTreeModel(root);
        categoryTree = new JTree(treeModel);
        categoryTree.setBackground(SIDEBAR_COLOR);
        categoryTree.setForeground(Color.WHITE);
        categoryTree.setRowHeight(25);

        // Custom tree cell renderer for dark theme
        categoryTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setBackground(sel ? SELECTION_COLOR : SIDEBAR_COLOR);
                setForeground(Color.WHITE);
                setOpaque(true);
                return this;
            }
        });

        categoryTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) categoryTree.getLastSelectedPathComponent();
            if (node != null) {
                filterByCategory(node.getUserObject().toString());
            }
        });

        JScrollPane scrollPane = new JScrollPane(categoryTree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(SIDEBAR_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSnippetListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        JLabel label = new JLabel("Snippets");
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setForeground(Color.WHITE);
        panel.add(label, BorderLayout.NORTH);

        // Create list
        listModel = new DefaultListModel<>();
        snippetList = new JList<>(listModel);
        snippetList.setBackground(BACKGROUND_COLOR);
        snippetList.setForeground(Color.WHITE);
        snippetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snippetList.setSelectionBackground(SELECTION_COLOR);

        // Custom list cell renderer
        snippetList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Snippet) {
                    Snippet snippet = (Snippet) value;
                    setText(snippet.title);
                    setToolTipText(snippet.description);
                }
                setBackground(isSelected ? SELECTION_COLOR : BACKGROUND_COLOR);
                setForeground(Color.WHITE);
                setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                return this;
            }
        });

        snippetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedSnippet();
            }
        });

        JScrollPane scrollPane = new JScrollPane(snippetList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createCodeDisplayPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(EDITOR_BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));

        // Header panel with snippet info
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(EDITOR_BACKGROUND);

        JLabel codeLabel = new JLabel("Code");
        codeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        codeLabel.setForeground(Color.WHITE);
        headerPanel.add(codeLabel, BorderLayout.WEST);

        tagsField = new JTextField();
        tagsField.setEditable(false);
        tagsField.setBackground(EDITOR_BACKGROUND);
        tagsField.setForeground(new Color(150, 150, 150));
        tagsField.setBorder(BorderFactory.createEmptyBorder());
        headerPanel.add(tagsField, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);

        // Code display area
        codeDisplayPane = new JTextPane();
        codeDisplayPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codeDisplayPane.setBackground(EDITOR_BACKGROUND);
        codeDisplayPane.setForeground(syntaxColors.get("default"));
        codeDisplayPane.setCaretColor(Color.WHITE);
        codeDisplayPane.setEditable(false);

        // Line numbers
        JTextArea lineNumbers = new JTextArea("1");
        lineNumbers.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        lineNumbers.setBackground(new Color(45, 45, 45));
        lineNumbers.setForeground(new Color(100, 100, 100));
        lineNumbers.setEditable(false);
        lineNumbers.setFocusable(false);

        // Sync line numbers with code
        codeDisplayPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void removeUpdate(DocumentEvent e) { updateLineNumbers(); }
            public void changedUpdate(DocumentEvent e) { updateLineNumbers(); }

            private void updateLineNumbers() {
                String text = codeDisplayPane.getText();
                int lines = text.split("\n").length;
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lines; i++) {
                    sb.append(i).append("\n");
                }
                lineNumbers.setText(sb.toString());
            }
        });

        JScrollPane scrollPane = new JScrollPane(codeDisplayPane);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        scrollPane.getViewport().setBackground(EDITOR_BACKGROUND);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(SIDEBAR_COLOR);
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(Color.WHITE);
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel countLabel = new JLabel("Total snippets: " + allSnippets.size());
        countLabel.setForeground(Color.WHITE);
        statusBar.add(countLabel, BorderLayout.EAST);

        return statusBar;
    }

    private void showNewSnippetDialog() {
        JDialog dialog = new JDialog(this, "New Snippet", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Title
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1;
        JTextField titleField = new JTextField(30);
        formPanel.add(titleField, gbc);

        // Language
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> langCombo = new JComboBox<>(new String[]{
                "Java", "Python", "JavaScript", "SQL", "HTML/CSS", "C++", "C#", "PHP", "Ruby", "Go", "Other"
        });
        formPanel.add(langCombo, gbc);

        // Tags
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Tags:"), gbc);
        gbc.gridx = 1;
        JTextField tagsField = new JTextField(30);
        tagsField.setToolTipText("Comma-separated tags");
        formPanel.add(tagsField, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        JTextField descField = new JTextField(30);
        formPanel.add(descField, gbc);

        // Code
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        formPanel.add(new JLabel("Code:"), gbc);

        JTextArea codeArea = new JTextArea(15, 50);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane codeScroll = new JScrollPane(codeArea);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            String title = titleField.getText().trim();
            String code = codeArea.getText().trim();

            if (title.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Title and code are required!");
                return;
            }

            Snippet snippet = new Snippet(
                    title,
                    code,
                    (String) langCombo.getSelectedItem(),
                    tagsField.getText(),
                    descField.getText()
            );

            allSnippets.add(snippet);
            refreshView();
            saveSnippets();
            dialog.dispose();
            statusLabel.setText("Snippet added: " + title);
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(formPanel, BorderLayout.NORTH);
        dialog.add(codeScroll, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void editSelectedSnippet() {
        Snippet selected = snippetList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a snippet to edit");
            return;
        }

        JDialog dialog = new JDialog(this, "Edit Snippet", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Title
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1;
        JTextField titleField = new JTextField(selected.title, 30);
        formPanel.add(titleField, gbc);

        // Language
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        JComboBox<String> langCombo = new JComboBox<>(new String[]{
                "Java", "Python", "JavaScript", "SQL", "HTML/CSS", "C++", "C#", "PHP", "Ruby", "Go", "Other"
        });
        langCombo.setSelectedItem(selected.language);
        formPanel.add(langCombo, gbc);

        // Tags
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Tags:"), gbc);
        gbc.gridx = 1;
        JTextField tagsField = new JTextField(String.join(", ", selected.tags), 30);
        formPanel.add(tagsField, gbc);

        // Description
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        JTextField descField = new JTextField(selected.description, 30);
        formPanel.add(descField, gbc);

        // Code
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        formPanel.add(new JLabel("Code:"), gbc);

        JTextArea codeArea = new JTextArea(selected.code, 15, 50);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane codeScroll = new JScrollPane(codeArea);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            selected.title = titleField.getText();
            selected.code = codeArea.getText();
            selected.language = (String) langCombo.getSelectedItem();
            selected.tags = Arrays.asList(tagsField.getText().split(",\\s*"));
            selected.description = descField.getText();
            selected.lastModified = new Date();

            refreshView();
            saveSnippets();
            dialog.dispose();
            statusLabel.setText("Snippet updated: " + selected.title);
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(formPanel, BorderLayout.NORTH);
        dialog.add(codeScroll, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void deleteSelectedSnippet() {
        Snippet selected = snippetList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a snippet to delete");
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + selected.title + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            allSnippets.remove(selected);
            refreshView();
            saveSnippets();
            statusLabel.setText("Snippet deleted: " + selected.title);
        }
    }

    private void copySelectedSnippet() {
        Snippet selected = snippetList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a snippet to copy");
            return;
        }

        StringSelection stringSelection = new StringSelection(selected.code);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);

        statusLabel.setText("Copied to clipboard: " + selected.title);
    }

    private void displaySelectedSnippet() {
        Snippet selected = snippetList.getSelectedValue();
        if (selected != null) {
            codeDisplayPane.setText(selected.code);
            applySyntaxHighlighting(selected.language);
            tagsField.setText("Tags: " + String.join(", ", selected.tags));
        }
    }

    private void applySyntaxHighlighting(String language) {
        // Basic syntax highlighting (simplified version)
        StyledDocument doc = codeDisplayPane.getStyledDocument();
        String text = codeDisplayPane.getText();

        // Reset all text to default color
        SimpleAttributeSet defaultAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultAttr, syntaxColors.get("default"));
        doc.setCharacterAttributes(0, text.length(), defaultAttr, true);

        // Apply language-specific highlighting
        if ("Java".equals(language)) {
            highlightJavaSyntax(doc, text);
        } else if ("Python".equals(language)) {
            highlightPythonSyntax(doc, text);
        }
        // Add more languages as needed
    }

    private void highlightJavaSyntax(StyledDocument doc, String text) {
        // Keywords
        String[] keywords = {"public", "private", "protected", "class", "interface", "extends",
                "implements", "static", "final", "void", "int", "double", "boolean", "String",
                "if", "else", "for", "while", "return", "new", "this", "super", "try", "catch"};

        SimpleAttributeSet keywordAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(keywordAttr, syntaxColors.get("keyword"));

        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                doc.setCharacterAttributes(matcher.start(), keyword.length(), keywordAttr, false);
            }
        }

        // Strings
        SimpleAttributeSet stringAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(stringAttr, syntaxColors.get("string"));
        Pattern stringPattern = Pattern.compile("\".*?\"");
        Matcher stringMatcher = stringPattern.matcher(text);
        while (stringMatcher.find()) {
            doc.setCharacterAttributes(stringMatcher.start(),
                    stringMatcher.end() - stringMatcher.start(), stringAttr, false);
        }

        // Comments
        SimpleAttributeSet commentAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(commentAttr, syntaxColors.get("comment"));
        Pattern commentPattern = Pattern.compile("//.*$|/\\*.*?\\*/", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher commentMatcher = commentPattern.matcher(text);
        while (commentMatcher.find()) {
            doc.setCharacterAttributes(commentMatcher.start(),
                    commentMatcher.end() - commentMatcher.start(), commentAttr, false);
        }
    }

    private void highlightPythonSyntax(StyledDocument doc, String text) {
        // Python keywords
        String[] keywords = {"def", "class", "if", "elif", "else", "for", "while", "return",
                "import", "from", "as", "try", "except", "finally", "with", "lambda", "in",
                "not", "and", "or", "True", "False", "None", "self"};

        SimpleAttributeSet keywordAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(keywordAttr, syntaxColors.get("keyword"));

        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                doc.setCharacterAttributes(matcher.start(), keyword.length(), keywordAttr, false);
            }
        }

        // Strings (both single and double quotes)
        SimpleAttributeSet stringAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(stringAttr, syntaxColors.get("string"));
        Pattern stringPattern = Pattern.compile("\"\"\".*?\"\"\"|'''.*?'''|\".*?\"|'.*?'", Pattern.DOTALL);
        Matcher stringMatcher = stringPattern.matcher(text);
        while (stringMatcher.find()) {
            doc.setCharacterAttributes(stringMatcher.start(),
                    stringMatcher.end() - stringMatcher.start(), stringAttr, false);
        }

        // Comments
        SimpleAttributeSet commentAttr = new SimpleAttributeSet();
        StyleConstants.setForeground(commentAttr, syntaxColors.get("comment"));
        Pattern commentPattern = Pattern.compile("#.*$", Pattern.MULTILINE);
        Matcher commentMatcher = commentPattern.matcher(text);
        while (commentMatcher.find()) {
            doc.setCharacterAttributes(commentMatcher.start(),
                    commentMatcher.end() - commentMatcher.start(), commentAttr, false);
        }
    }

    private void performSearch() {
        String searchText = searchField.getText().toLowerCase();
        listModel.clear();

        if (searchText.isEmpty()) {
            for (Snippet snippet : allSnippets) {
                listModel.addElement(snippet);
            }
        } else {
            for (Snippet snippet : allSnippets) {
                if (snippet.title.toLowerCase().contains(searchText) ||
                        snippet.code.toLowerCase().contains(searchText) ||
                        snippet.description.toLowerCase().contains(searchText) ||
                        snippet.tags.stream().anyMatch(tag -> tag.toLowerCase().contains(searchText))) {
                    listModel.addElement(snippet);
                }
            }
        }

        statusLabel.setText("Found " + listModel.size() + " snippets");
    }

    private void filterByLanguage() {
        String selected = (String) languageFilter.getSelectedItem();
        listModel.clear();

        if ("All".equals(selected)) {
            for (Snippet snippet : allSnippets) {
                listModel.addElement(snippet);
            }
        } else {
            for (Snippet snippet : allSnippets) {
                if (snippet.language.equals(selected)) {
                    listModel.addElement(snippet);
                }
            }
        }

        statusLabel.setText("Showing " + listModel.size() + " " + selected + " snippets");
    }

    private void filterByCategory(String category) {
        listModel.clear();

        if ("All Snippets".equals(category)) {
            for (Snippet snippet : allSnippets) {
                listModel.addElement(snippet);
            }
        } else {
            for (Snippet snippet : allSnippets) {
                if (snippet.language.equals(category)) {
                    listModel.addElement(snippet);
                }
            }
        }
    }

    private void refreshView() {
        listModel.clear();
        for (Snippet snippet : allSnippets) {
            listModel.addElement(snippet);
        }

        // Update status bar count
        Component statusBar = getContentPane().getComponent(2);
        if (statusBar instanceof JPanel) {
            JPanel panel = (JPanel) statusBar;
            Component[] components = panel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JLabel && ((JLabel) comp).getText().startsWith("Total")) {
                    ((JLabel) comp).setText("Total snippets: " + allSnippets.size());
                }
            }
        }
    }

    private void importSnippets() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                // Simple import - in real app, you'd parse JSON properly
                JOptionPane.showMessageDialog(this, "Import feature would import from: " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error importing snippets: " + e.getMessage());
            }
        }
    }

    private void exportSnippets() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                if (!file.getName().endsWith(".json")) {
                    file = new File(file.getAbsolutePath() + ".json");
                }

                // Simple export - in real app, you'd create proper JSON
                try (PrintWriter writer = new PrintWriter(file)) {
                    writer.println("[");
                    for (int i = 0; i < allSnippets.size(); i++) {
                        Snippet s = allSnippets.get(i);
                        writer.println("  {");
                        writer.println("    \"title\": \"" + s.title + "\",");
                        writer.println("    \"language\": \"" + s.language + "\",");
                        writer.println("    \"code\": \"" + s.code.replace("\"", "\\\"").replace("\n", "\\n") + "\",");
                        writer.println("    \"tags\": \"" + String.join(",", s.tags) + "\",");
                        writer.println("    \"description\": \"" + s.description + "\"");
                        writer.println("  }" + (i < allSnippets.size() - 1 ? "," : ""));
                    }
                    writer.println("]");
                }

                JOptionPane.showMessageDialog(this, "Snippets exported successfully!");
                statusLabel.setText("Exported " + allSnippets.size() + " snippets");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error exporting snippets: " + e.getMessage());
            }
        }
    }

    private void saveSnippets() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(allSnippets);
        } catch (IOException e) {
            System.err.println("Error saving snippets: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSnippets() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                allSnippets = (List<Snippet>) ois.readObject();
                refreshView();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error loading snippets: " + e.getMessage());
                loadSampleSnippets();
            }
        } else {
            loadSampleSnippets();
        }
    }

    private void loadSampleSnippets() {
        allSnippets.add(new Snippet(
                "Read File",
                "List<String> lines = Files.readAllLines(Paths.get(\"file.txt\"));\nfor (String line : lines) {\n    System.out.println(line);\n}",
                "Java",
                "file,io,read",
                "Read all lines from a text file"
        ));

        allSnippets.add(new Snippet(
                "JDBC Connection",
                "String url = \"jdbc:mysql://localhost:3306/database\";\nString user = \"username\";\nString password = \"password\";\n\ntry (Connection conn = DriverManager.getConnection(url, user, password)) {\n    System.out.println(\"Connected to database\");\n} catch (SQLException e) {\n    e.printStackTrace();\n}",
                "Java",
                "database,jdbc,mysql,connection",
                "Connect to MySQL database using JDBC"
        ));

        allSnippets.add(new Snippet(
                "Binary Search",
                "public int binarySearch(int[] arr, int target) {\n    int left = 0;\n    int right = arr.length - 1;\n    \n    while (left <= right) {\n        int mid = left + (right - left) / 2;\n        \n        if (arr[mid] == target) {\n            return mid;\n        }\n        \n        if (arr[mid] < target) {\n            left = mid + 1;\n        } else {\n            right = mid - 1;\n        }\n    }\n    \n    return -1;\n}",
                "Java",
                "algorithm,search,binary",
                "Binary search implementation"
        ));

        allSnippets.add(new Snippet(
                "Quick Sort",
                "def quicksort(arr):\n    if len(arr) <= 1:\n        return arr\n    \n    pivot = arr[len(arr) // 2]\n    left = [x for x in arr if x < pivot]\n    middle = [x for x in arr if x == pivot]\n    right = [x for x in arr if x > pivot]\n    \n    return quicksort(left) + middle + quicksort(right)",
                "Python",
                "algorithm,sort,quick",
                "Quick sort implementation in Python"
        ));

        allSnippets.add(new Snippet(
                "Fetch API",
                "fetch('https://api.example.com/data')\n    .then(response => response.json())\n    .then(data => console.log(data))\n    .catch(error => console.error('Error:', error));",
                "JavaScript",
                "api,fetch,ajax,web",
                "Basic fetch API call"
        ));

        allSnippets.add(new Snippet(
                "SQL Join",
                "SELECT customers.name, orders.order_date, orders.total\nFROM customers\nINNER JOIN orders ON customers.id = orders.customer_id\nWHERE orders.total > 100\nORDER BY orders.order_date DESC;",
                "SQL",
                "database,join,query,select",
                "SQL inner join example"
        ));

        refreshView();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new CodeSnippetManager();
        });
    }
}

class Snippet implements Serializable {
    private static final long serialVersionUID = 1L;

    String title;
    String code;
    String language;
    List<String> tags;
    String description;
    Date dateCreated;
    Date lastModified;

    public Snippet(String title, String code, String language, String tags, String description) {
        this.title = title;
        this.code = code;
        this.language = language;
        this.tags = Arrays.asList(tags.split(",\\s*"));
        this.description = description;
        this.dateCreated = new Date();
        this.lastModified = new Date();
    }

    @Override
    public String toString() {
        return title;
    }
}