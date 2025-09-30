package manager;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.io.OutputStream;
import java.util.Map;

public class CodeSnippetManagerFX extends Application {
    private final ObservableList<Snippet> snippets = FXCollections.observableArrayList();
    private final ListView<Snippet> listView = new ListView<>();
    private final TextField searchField = new TextField();
    private final CodeArea previewArea = new CodeArea();

    private Button addBtn;

    private static final String CONFIG_FILE = "config.properties";
    private String githubToken = null;

    public CodeSnippetManagerFX() {
        TokenStorage.loadToken();
    }

    private final File storageDir = new File("snippets");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Load snippets
        if (!storageDir.exists()) storageDir.mkdirs();
        loadSnippets();

        // Toolbar
        ToolBar toolBar = createToolBar(primaryStage);

        // Left pane (list + search)
        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(12));
        Label mySnips = new Label("Snippets");
        mySnips.setFont(Font.font(16));
        searchField.setPromptText("Search title, language or tags...");
        searchField.textProperty().addListener((obs, old, nw) -> applyFilter(nw));

        listView.setItems(snippets);
        listView.setCellFactory(lv -> new ListCell<Snippet>() {
            @Override
            protected void updateItem(Snippet item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    VBox meta = new VBox(2);
                    Label title = new Label(item.title);
                    title.setStyle("-fx-font-weight: bold;");
                    Label metaLine = new Label(item.language + " · " + String.join(", ", item.tags));
                    metaLine.setStyle("-fx-text-fill: #606e7b; -fx-font-size: 11px;");
                    meta.getChildren().addAll(title, metaLine);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label date = new Label(formatDate(item.lastModified));
                    date.setStyle("-fx-text-fill: #9aa6b2; -fx-font-size: 11px;");

                    row.getChildren().addAll(meta, spacer, date);
                    setGraphic(row);
                }
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showPreview(n));
        listView.setPlaceholder(new Label("No snippets. Click + to add one."));

        HBox searchRow = new HBox(8, searchField);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        leftPane.getChildren().addAll(mySnips, searchRow, listView);
        leftPane.setPrefWidth(320);

        // Right pane (editor/preview)
        VBox rightPane = new VBox(8);
        rightPane.setPadding(new Insets(12));
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label previewTitle = new Label("Preview");
        previewTitle.setFont(Font.font(16));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editBtn = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        editBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        editBtn.setOnAction(e -> {
            Snippet selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showSnippetDialog(selected, primaryStage);
            }
        });
        deleteBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        header.getChildren().addAll(previewTitle, spacer, editBtn, deleteBtn);

        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setParagraphGraphicFactory(LineNumberFactory.get(previewArea));

        // Enhanced syntax highlighting
        previewArea.getStylesheets().add("data:text/css," + encodeCss(
                ".keyword { -fx-fill: #0000ff; -fx-font-weight: bold; }" +
                        ".comment { -fx-fill: #008000; font-style: italic; }" +
                        ".string { -fx-fill: #a31515; }" +
                        ".number { -fx-fill: #098658; }" +
                        ".annotation { -fx-fill: #646695; }" +
                        ".operator { -fx-fill: #aa22ff; }"
        ));

        HBox metaStrip = new HBox(12);
        metaStrip.setPadding(new Insets(6, 0, 6, 0));
        Label emptyMeta = new Label("");
        metaStrip.getChildren().add(emptyMeta);

        rightPane.getChildren().addAll(header, metaStrip, previewArea);

        // Layout
        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.32);
        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setCenter(split);

        Scene scene = new Scene(root, 1000, 640);
        scene.getStylesheets().add("data:text/css," + encodeCss(getCss()));

        addBtn.setOnAction(e -> showSnippetDialog(null, primaryStage));
        deleteBtn.setOnAction(e -> deleteSelected());

        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                () -> showSnippetDialog(null, primaryStage));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                searchField::requestFocus);

        primaryStage.setTitle("Code Manager / Snippet Manager");

        // Load taskbar icons correctly from classpath
        int[] sizes = {16, 32, 48, 64, 128};
        for (int size : sizes) {
            try {
                InputStream is = getClass().getResourceAsStream("/icons/icon_" + size + "x" + size + ".png");
                if (is != null) {
                    javafx.scene.image.Image fxIcon = new javafx.scene.image.Image(is);
                    primaryStage.getIcons().add(fxIcon);
                } else {
                    System.out.println("Icon not found: " + size + "x" + size);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }


    private ToolBar createToolBar(Stage owner) {
        addBtn = new Button("+"); // assign to the field!
        addBtn.setId("btnAdd");
        addBtn.getStyleClass().add("circle-button");

        Button scanFolderBtn = new Button("Scan Folder");
        scanFolderBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Project Folder");
            File folder = chooser.showDialog(owner);
            if (folder != null && folder.isDirectory()) {
                List<ProjectFunction> functions = indexProjectFolder(folder);
                showFunctionSelectionDialog(functions, owner);
            }
        });

        Button imp = new Button("Import");
        Button exp = new Button("Export");
        Button prefs = new Button("Preferences");

        ToolBar tb = new ToolBar(addBtn, scanFolderBtn, new Separator(), imp, exp, new Region(), prefs);
        HBox.setHgrow(tb.getItems().get(tb.getItems().size() - 1), Priority.ALWAYS);
        tb.setPadding(new Insets(6));

        // actions
        imp.setOnAction(e -> importSnippets(owner));
        exp.setOnAction(e -> exportSnippets(owner));
        prefs.setOnAction(e -> showPrefs(owner));

        return tb;
    }

    private void showFunctionSelectionDialog(List<ProjectFunction> functions, Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Select Function");

        ListView<ProjectFunction> lv = new ListView<>();
        lv.setItems(FXCollections.observableArrayList(functions));
        lv.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectFunction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.projectName + "/" + item.fileName + " :: " + item.functionName);
                }
            }
        });

        Button gistBtn = new Button("Create Gist");
        gistBtn.setDisable(true);
        lv.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> gistBtn.setDisable(newV == null));

        gistBtn.setOnAction(e -> {
            ProjectFunction selected = lv.getSelectionModel().getSelectedItem();
            if (selected != null) {

                // Skip empty functions
                if (selected.code == null || selected.code.isBlank()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Empty Function");
                    alert.setHeaderText(null);
                    alert.setContentText("Cannot create gist: the selected function is empty.");
                    alert.showAndWait();
                    return;
                }

                Map<String, String> files = new HashMap<>();
                files.put(selected.functionName + ".java", selected.code);

                String description = selected.projectName + "/" + selected.fileName + " :: " + selected.functionName;

                try {
                    String githubToken = TokenStorage.loadToken(); // load the stored token
                    if (githubToken == null) {
                        githubToken = showTokenPrompt(owner); // your existing method to input token
                    }
                    createGist(description, true, files, githubToken);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Gist Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Failed to create gist: " + ex.getMessage());
                    alert.showAndWait();
                }

                dialog.close();
            }
        });


        VBox layout = new VBox(8, lv, gistBtn);
        layout.setPadding(new Insets(12));
        Scene scene = new Scene(layout, 600, 400);
        dialog.setScene(scene);
        dialog.show();
    }



    // Prompt user for GitHub token
    private String showTokenPrompt(Stage owner) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle("GitHub Token Required");
        dialog.setHeaderText("Enter your GitHub Personal Access Token");
        dialog.setContentText("Token:");
        return dialog.showAndWait().orElse(null);
    }

    // Simple alert
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }



    private void loadToken() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                githubToken = props.getProperty("github.token").trim();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Save token to file
    private void saveToken() {
        Properties props = new Properties();
        props.setProperty("github.token", githubToken != null ? githubToken : "");
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "CodeSnippetManager Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<ProjectFunction> indexProjectFolder(File folder) {
        List<ProjectFunction> functions = new ArrayList<>();
        if (folder == null || !folder.isDirectory()) {
            System.out.println("Invalid folder: " + folder);
            return functions;
        }

        try {
            Files.walk(folder.toPath())
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String lang = getLanguage(file.getFileName().toString());
                        if (lang == null) {
                            System.out.println("Skipping file (unknown language): " + file);
                            return;
                        }

                        System.out.println("Scanning file: " + file + " (language: " + lang + ")");
                        try {
                            String content = Files.readString(file);
                            List<ProjectFunction> fileFunctions = parseFunctions(lang, content, file);
                            System.out.println("Found " + fileFunctions.size() + " functions in " + file);
                            functions.addAll(fileFunctions);
                        } catch (IOException ex) {
                            System.out.println("Error reading file: " + file);
                            ex.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Total functions indexed: " + functions.size());
        return functions;
    }

    private List<ProjectFunction> parseFunctions(String language, String content, Path file) {
        List<ProjectFunction> result = new ArrayList<>();
        Pattern pattern;

        switch (language.toLowerCase()) {
            case "java", "c++", "c#" -> pattern = Pattern.compile(
                    "(?:public|protected|private|static|\\s)+[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{"
            );
            case "python" -> pattern = Pattern.compile(
                    "^\\s*def\\s+(\\w+)\\s*\\(([^)]*)\\):", Pattern.MULTILINE
            );
            case "javascript" -> pattern = Pattern.compile(
                    "function\\s+(\\w+)\\s*\\(([^)]*)\\)|([\\w]+)\\s*=\\s*\\(([^)]*)\\)\\s*=>"
            );
            default -> {
                System.out.println("No regex for language: " + language);
                return result;
            }
        }

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String fnName = matcher.group(1) != null ? matcher.group(1) :
                    (matcher.group(3) != null ? matcher.group(3) : "unknown");
            int start = matcher.start();
            int end = findFunctionEnd(content, start, language);
            String codeSnippet = content.substring(start, end);

            String projectName = file.getParent() != null ? file.getParent().getFileName().toString() : "UnknownProject";

            result.add(new ProjectFunction(projectName, file.getFileName().toString(), language, fnName, codeSnippet, file.toFile().toPath()));
            System.out.println("Parsed function: " + fnName + " in " + file);
        }

        return result;
    }


    private int findFunctionEnd(String content, int start, String language) {
        // naive: find next closing brace or newline for Python
        if (language.equalsIgnoreCase("python")) {
            int nextFn = content.indexOf("\ndef ", start + 1);
            return nextFn > 0 ? nextFn : content.length();
        } else {
            int braceCount = 0;
            for (int i = start; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                if (braceCount == 0) return i + 1;
            }
            return content.length();
        }
    }


    private String getLanguage(String fileName) {
        if (fileName.endsWith(".java")) return "java";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".js")) return "javascript";
        return null;
    }


    private void showPreview(Snippet s) {
        previewArea.clear();
        if (s == null) return;

        String code = s.code;
        previewArea.replaceText(code);

        String langKey = s.language.toLowerCase().trim();
        LanguageSyntax syntax = LANGUAGE_SYNTAX.getOrDefault(langKey, LANGUAGE_SYNTAX.get("java")); // default to Java

        Matcher matcher = syntax.pattern.matcher(code);
        while (matcher.find()) {
            for (Map.Entry<String, String> entry : syntax.styleMap.entrySet()) {
                if (matcher.group(entry.getKey()) != null) {
                    previewArea.setStyleClass(matcher.start(), matcher.end(), entry.getValue());
                    break;
                }
            }
        }
    }

    private void applyFilter(String q) {
        String ql = q == null ? "" : q.trim().toLowerCase();
        if (ql.isEmpty()) {
            listView.setItems(snippets);
            return;
        }
        ObservableList<Snippet> filtered = FXCollections.observableArrayList(
                snippets.stream()
                        .filter(s -> s.title.toLowerCase().contains(ql) || s.language.toLowerCase().contains(ql) || s.description.toLowerCase().contains(ql) || s.tags.stream().anyMatch(t -> t.toLowerCase().contains(ql)))
                        .collect(Collectors.toList())
        );
        listView.setItems(filtered);
    }

    private void showSnippetDialog(Snippet base, Stage owner) {
        Stage d = new Stage();
        d.initModality(Modality.APPLICATION_MODAL);
        d.initOwner(owner);

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        TextField title = new TextField(base != null ? base.title : "");
        title.setPromptText("Title");
        TextField language = new TextField(base != null ? base.language : "");
        language.setPromptText("Language (e.g. Java, Python)");
        TextField tags = new TextField(base != null ? String.join(", ", base.tags) : "");
        tags.setPromptText("tags, comma separated");
        TextField desc = new TextField(base != null ? base.description : "");
        desc.setPromptText("Short description");
        TextArea code = new TextArea(base != null ? base.code : "");
        code.setFont(Font.font("Monospaced", 12));
        code.setPrefHeight(320);

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button save = new Button("Save");
        Button cancel = new Button("Cancel");
        buttons.getChildren().addAll(cancel, save);

        root.getChildren().addAll(new Label("Title"), title, new Label("Language"), language, new Label("Tags"), tags, new Label("Description"), desc, new Label("Code"), code, buttons);

        Scene scene = new Scene(root, 720, 600);
        scene.getStylesheets().add("data:text/css," + encodeCss(getCss()));
        d.setScene(scene);

        cancel.setOnAction(e -> d.close());
        save.setOnAction(e -> {
            String t = title.getText().trim();
            if (t.isEmpty()) {
                alert("Validation", "Title is required");
                return;
            }
            Snippet s;
            if (base == null)
                s = new Snippet(t, code.getText(), language.getText().trim(), tags.getText().trim(), desc.getText().trim());
            else {
                s = new Snippet(t, code.getText(), language.getText().trim(), tags.getText().trim(), desc.getText().trim());
                s.dateCreated = base.dateCreated;
            }
            s.lastModified = new Date();
            // save file
            saveSnippetToFile(s);
            // refresh list
            int idx = snippets.indexOf(base);
            if (base == null) snippets.add(0, s);
            else {
                if (idx >= 0) snippets.set(idx, s);
            }
            listView.setItems(snippets);
            listView.getSelectionModel().select(s);
            d.close();
        });

        d.showAndWait();
    }

    private void deleteSelected() {
        Snippet s = listView.getSelectionModel().getSelectedItem();
        if (s == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + s.title + "'?", ButtonType.YES, ButtonType.NO);
        a.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                // delete file
                if (s.sourceFile != null && s.sourceFile.exists()) s.sourceFile.delete();
                snippets.remove(s);
                listView.getSelectionModel().clearSelection();
            }
        });
    }

    private void importSnippets(Stage owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import snippets (properties)");
        File f = fc.showOpenDialog(owner);
        if (f == null) return;
        try (InputStream is = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            Snippet s = Snippet.fromProperties(p);
            saveSnippetToFile(s);
            snippets.add(s);
        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Import error", ex.getMessage());
        }
    }

    private void exportSnippets(Stage owner) {
        // Export all to a zip (simple implementation omitted) - for demo export first selected
        Snippet s = listView.getSelectionModel().getSelectedItem();
        if (s == null) {
            alert("Export", "Select a snippet to export.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export snippet");
        fc.setInitialFileName(s.slug() + ".properties");
        File f = fc.showSaveDialog(owner);
        if (f == null) return;
        try (OutputStream os = new FileOutputStream(f)) {
            Properties p = s.toProperties();
            p.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), "CodeSnippet");
        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Export error", ex.getMessage());
        }
    }

    private void showPrefs(Stage owner) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Preferences are not implemented in this demo.", ButtonType.OK);
        a.initOwner(owner);
        a.showAndWait();
    }

    private void saveSnippetToFile(Snippet s) {
        try {
            if (!storageDir.exists()) storageDir.mkdirs();
            String name = s.slug() + ".properties";
            File f = new File(storageDir, name);
            // if file already exists for new snippet, add timestamp to avoid overwrite
            if (f.exists() && (s.sourceFile == null || !f.equals(s.sourceFile))) {
                name = s.slug() + "-" + System.currentTimeMillis() + ".properties";
                f = new File(storageDir, name);
            }
            try (OutputStream os = new FileOutputStream(f)) {
                Properties p = s.toProperties();
                p.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), "CodeSnippet");
            }
            s.sourceFile = f;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadSnippets() {
        snippets.clear();
        File[] files = storageDir.listFiles((d, name) -> name.endsWith(".properties"));
        if (files == null) return;
        for (File f : files) {
            try (InputStream is = new FileInputStream(f)) {
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                Snippet s = Snippet.fromProperties(p);
                s.sourceFile = f;
                snippets.add(s);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        // sort by lastModified desc
        snippets.sort(Comparator.comparing((Snippet a) -> a.lastModified).reversed());
    }

    private static void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(title);
        a.showAndWait();
    }

    private static String formatDate(Date d) {
        if (d == null) return "-";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(d);
    }

    // Simple CSS - embedded for convenience
    private String getCss() {
        return ""
                + "root { -fx-font-family: 'Segoe UI', 'Helvetica Neue', Arial; -fx-background-color: linear-gradient(to bottom right, #f7fbff, #eaf3ff); }"
                + " .tool-bar { -fx-background-color: transparent; -fx-padding: 8; }"
                + " .circle-button { -fx-background-radius: 999px; -fx-min-width: 36px; -fx-min-height: 36px; -fx-max-width: 36px; -fx-max-height: 36px; -fx-font-weight: bold; }"
                + " .list-cell:filled:selected { -fx-background-color: #dbeeff; }"
                + " .text-area { -fx-control-inner-background: white; -fx-background-color: white; -fx-border-color: #e1e8ee; -fx-border-radius: 6px; -fx-background-radius: 6px; }"
                ;
    }

    // encode CSS so it can be used as a data: URL
    private String encodeCss(String css) {
        return css.replace("#", "%23").replace("\n", "");
    }

    public void createGist(String description, boolean isPublic, Map<String, String> files, String githubToken) throws Exception {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Gist must contain at least one file.");
        }

        JSONObject json = new JSONObject();
        json.put("description", description);
        json.put("public", isPublic);

        JSONObject filesJson = new JSONObject();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();

            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("File content cannot be empty: " + filename);
            }

            // Sanitize filename (no slashes, colons, or other invalid chars)
            filename = filename.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");

            JSONObject fileContent = new JSONObject();
            fileContent.put("content", content);
            filesJson.put(filename, fileContent);
        }
        json.put("files", filesJson);

        System.out.println("Gist JSON: " + json.toString(2)); // debug

        URL url = new URL("https://api.github.com/gists");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "token " + githubToken);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            try (InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    String response = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                    System.err.println("GitHub response: " + response);
                }
            }
            throw new RuntimeException("Failed to create gist: " + responseCode);
        }

        System.out.println("Gist created successfully!");
    }



    // Escape quotes and newlines in code for JSON
    private String escapeForJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getGithubToken(Stage owner) {
        // Try to load token from storage
        String token = TokenStorage.loadToken();
        if (token != null && !token.isEmpty()) {
            System.out.println("Using stored token.");
            return token;
        }

        // Token not found, prompt user
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(owner);
        dialog.setTitle("GitHub Token Required");
        dialog.setHeaderText("Enter your GitHub Personal Access Token (with Gist permissions):");
        dialog.setContentText("Token:");

        dialog.showAndWait().ifPresent(input -> {
            TokenStorage.saveToken(input);
            System.out.println("Token saved.");
        });

        // Reload token after saving
        return TokenStorage.loadToken();
    }



    // Model
    public static class Snippet {
        public String title;
        public String code;
        public String language;
        public List<String> tags;
        public String description;
        public Date dateCreated;
        public Date lastModified;
        public File sourceFile;

        public Snippet(String title, String code, String language, String tagsCsv, String description) {
            this.title = title;
            this.code = code == null ? "" : code;
            this.language = language == null ? "" : language;
            this.tags = new ArrayList<>();
            if (tagsCsv != null && !tagsCsv.trim().isEmpty()) {
                for (String t : tagsCsv.split(",\\s*")) if (!t.isBlank()) this.tags.add(t.trim());
            }
            this.description = description == null ? "" : description;
            this.dateCreated = new Date();
            this.lastModified = new Date();
        }

        public Properties toProperties() {
            Properties p = new Properties();
            p.setProperty("title", title == null ? "" : title);
            p.setProperty("code", code == null ? "" : Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8)));
            p.setProperty("language", language == null ? "" : language);
            p.setProperty("tags", String.join(",", tags));
            p.setProperty("description", description == null ? "" : description);
            p.setProperty("dateCreated", Long.toString(dateCreated.getTime()));
            p.setProperty("lastModified", Long.toString(lastModified.getTime()));
            return p;
        }

        public static Snippet fromProperties(Properties p) {
            String title = p.getProperty("title", "(untitled)");
            String codeB64 = p.getProperty("code", "");
            String code = "";
            if (!codeB64.isEmpty()) {
                try {
                    code = new String(Base64.getDecoder().decode(codeB64), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }
            String language = p.getProperty("language", "");
            String tags = p.getProperty("tags", "");
            String desc = p.getProperty("description", "");
            Snippet s = new Snippet(title, code, language, tags, desc);
            try {
                s.dateCreated = new Date(Long.parseLong(p.getProperty("dateCreated", Long.toString(new Date().getTime()))));
            } catch (Exception ignored) {
            }
            try {
                s.lastModified = new Date(Long.parseLong(p.getProperty("lastModified", Long.toString(new Date().getTime()))));
            } catch (Exception ignored) {
            }
            return s;
        }

        public String slug() {
            String s = title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            if (s.length() > 40) s = s.substring(0, 40);
            s = s.replaceAll("-+", "-");
            if (s.startsWith("-")) s = s.substring(1);
            if (s.isEmpty()) s = "snippet";
            return s;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final Map<String, LanguageSyntax> LANGUAGE_SYNTAX = new HashMap<>();

    static {
        // Java syntax
        String javaKeywords = "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|" +
                "default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|" +
                "instanceof|int|interface|long|native|new|package|private|protected|public|return|short|" +
                "static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b";
        String javaComments = "//[^\n]*|/\\*(.|\\R)*?\\*/";
        String javaStrings = "\"([^\"\\\\]|\\\\.)*\"";
        String javaNumbers = "\\b\\d+(\\.\\d+)?\\b";
        String javaAnnotations = "@\\w+";
        String javaOperators = "[+\\-*/%=!<>&|^~?:]+";
        Pattern javaPattern = Pattern.compile(
                "(?<KEYWORD>" + javaKeywords + ")"
                        + "|(?<COMMENT>" + javaComments + ")"
                        + "|(?<STRING>" + javaStrings + ")"
                        + "|(?<NUMBER>" + javaNumbers + ")"
                        + "|(?<ANNOTATION>" + javaAnnotations + ")"
                        + "|(?<OPERATOR>" + javaOperators + ")"
        );
        Map<String, String> javaStyles = Map.of(
                "KEYWORD", "keyword",
                "COMMENT", "comment",
                "STRING", "string",
                "NUMBER", "number",
                "ANNOTATION", "annotation",
                "OPERATOR", "operator"
        );
        LANGUAGE_SYNTAX.put("java", new LanguageSyntax(javaPattern, javaStyles));

        // Python syntax
        String pyKeywords = "\\b(False|None|True|and|as|assert|async|await|break|class|continue|def|del|elif|else|except|" +
                "finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b";
        String pyComments = "#[^\\n]*";
        String pyStrings = "\"\"\"(.|\\R)*?\"\"\"|'''(.|\\R)*?'''|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
        String pyNumbers = "\\b\\d+(\\.\\d+)?\\b";
        String pyOperators = "[+\\-*/%=!<>&|^~?:]+";
        Pattern pyPattern = Pattern.compile(
                "(?<KEYWORD>" + pyKeywords + ")"
                        + "|(?<COMMENT>" + pyComments + ")"
                        + "|(?<STRING>" + pyStrings + ")"
                        + "|(?<NUMBER>" + pyNumbers + ")"
                        + "|(?<OPERATOR>" + pyOperators + ")"
        );
        Map<String, String> pyStyles = Map.of(
                "KEYWORD", "keyword",
                "COMMENT", "comment",
                "STRING", "string",
                "NUMBER", "number",
                "OPERATOR", "operator"
        );
        LANGUAGE_SYNTAX.put("python", new LanguageSyntax(pyPattern, pyStyles));

        // JavaScript syntax
        String jsKeywords = "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|" +
                "finally|for|function|if|import|in|instanceof|let|new|return|super|switch|this|throw|try|typeof|var|void|while|with|yield)\\b";
        String jsComments = "//[^\\n]*|/\\*(.|\\R)*?\\*/";
        String jsStrings = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`";
        String jsNumbers = "\\b\\d+(\\.\\d+)?\\b";
        String jsOperators = "[+\\-*/%=!<>&|^~?:]+";
        Pattern jsPattern = Pattern.compile(
                "(?<KEYWORD>" + jsKeywords + ")"
                        + "|(?<COMMENT>" + jsComments + ")"
                        + "|(?<STRING>" + jsStrings + ")"
                        + "|(?<NUMBER>" + jsNumbers + ")"
                        + "|(?<OPERATOR>" + jsOperators + ")"
        );
        Map<String, String> jsStyles = Map.of(
                "KEYWORD", "keyword",
                "COMMENT", "comment",
                "STRING", "string",
                "NUMBER", "number",
                "OPERATOR", "operator"
        );
        LANGUAGE_SYNTAX.put("javascript", new LanguageSyntax(jsPattern, jsStyles));

        // C++ syntax
        String cppKeywords = "\\b(alignas|alignof|and|and_eq|asm|atomic_cancel|atomic_commit|atomic_noexcept|" +
                "auto|bitand|bitor|bool|break|case|catch|char|char16_t|char32_t|class|compl|const|constexpr|const_cast|continue|" +
                "decltype|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|" +
                "inline|int|long|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|" +
                "register|reinterpret_cast|return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|template|" +
                "this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while|xor|xor_eq)\\b";
        String cppComments = "//[^\\n]*|/\\*(.|\\R)*?\\*/";
        String cppStrings = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
        String cppNumbers = "\\b\\d+(\\.\\d+)?\\b";
        String cppOperators = "[+\\-*/%=!<>&|^~?:]+";
        Pattern cppPattern = Pattern.compile(
                "(?<KEYWORD>" + cppKeywords + ")"
                        + "|(?<COMMENT>" + cppComments + ")"
                        + "|(?<STRING>" + cppStrings + ")"
                        + "|(?<NUMBER>" + cppNumbers + ")"
                        + "|(?<OPERATOR>" + cppOperators + ")"
        );
        Map<String, String> cppStyles = Map.of(
                "KEYWORD", "keyword",
                "COMMENT", "comment",
                "STRING", "string",
                "NUMBER", "number",
                "OPERATOR", "operator"
        );
        LANGUAGE_SYNTAX.put("cpp", new LanguageSyntax(cppPattern, cppStyles));

        // SQL syntax
        String sqlKeywords = "\\b(SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|ALTER|DROP|JOIN|" +
                "INNER|LEFT|RIGHT|FULL|ON|AS|AND|OR|NOT|NULL|DISTINCT|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET)\\b";
        String sqlComments = "--[^\\n]*|/\\*(.|\\R)*?\\*/";
        String sqlStrings = "'([^'\\\\]|\\\\.)*'";
        String sqlNumbers = "\\b\\d+(\\.\\d+)?\\b";
        Pattern sqlPattern = Pattern.compile(
                "(?<KEYWORD>" + sqlKeywords + ")"
                        + "|(?<COMMENT>" + sqlComments + ")"
                        + "|(?<STRING>" + sqlStrings + ")"
                        + "|(?<NUMBER>" + sqlNumbers + ")"
        );
        Map<String, String> sqlStyles = Map.of(
                "KEYWORD", "keyword",
                "COMMENT", "comment",
                "STRING", "string",
                "NUMBER", "number"
        );
        LANGUAGE_SYNTAX.put("sql", new LanguageSyntax(sqlPattern, sqlStyles));

        // HTML syntax
        String htmlTags = "</?[a-zA-Z][^>]*>";
        String htmlComments = "<!--(.|\\R)*?-->";
        String htmlStrings = "\"([^\"]*)\"|'([^']*)'";
        Pattern htmlPattern = Pattern.compile(
                "(?<TAG>" + htmlTags + ")"
                        + "|(?<COMMENT>" + htmlComments + ")"
                        + "|(?<STRING>" + htmlStrings + ")"
        );
        Map<String, String> htmlStyles = Map.of(
                "TAG", "keyword",
                "COMMENT", "comment",
                "STRING", "string"
        );
        LANGUAGE_SYNTAX.put("html", new LanguageSyntax(htmlPattern, htmlStyles));

        // CSS syntax
        String cssSelectors = "[.#]?[a-zA-Z0-9_-]+";
        String cssProperties = "\\b([a-z-]+)\\b(?=\\s*:)";
        String cssValues = "[^;{}]+";
        String cssComments = "/\\*(.|\\R)*?\\*/";
        Pattern cssPattern = Pattern.compile(
                "(?<PROPERTY>" + cssProperties + ")"
                        + "|(?<VALUE>" + cssValues + ")"
                        + "|(?<COMMENT>" + cssComments + ")"
                        + "|(?<SELECTOR>" + cssSelectors + ")"
        );
        Map<String, String> cssStyles = Map.of(
                "PROPERTY", "keyword",
                "VALUE", "string",
                "COMMENT", "comment",
                "SELECTOR", "annotation"
        );
        LANGUAGE_SYNTAX.put("css", new LanguageSyntax(cssPattern, cssStyles));
    }
}

