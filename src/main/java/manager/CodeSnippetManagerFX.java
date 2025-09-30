package manager;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CodeSnippetManagerFX extends Application {
    private final ObservableList<Snippet> snippets = FXCollections.observableArrayList();
    private final ListView<Snippet> listView = new ListView<>();
    private final TextField searchField = new TextField();
    private final CodeArea previewArea = new CodeArea();

    private Button addBtn;

    private final File storageDir = new File("snippets");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Load data
        if (!storageDir.exists()) storageDir.mkdirs();
        loadSnippets();

        // Top toolbar
        ToolBar toolBar = createToolBar(primaryStage);

        // Left: list + search
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

        // Right: preview / editor
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
        deleteBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        header.getChildren().addAll(previewTitle, spacer, editBtn, deleteBtn);

        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setParagraphGraphicFactory(LineNumberFactory.get(previewArea));
        previewArea.getStylesheets().add("data:text/css," + encodeCss(
                ".keyword { -fx-fill: #0000ff; -fx-font-weight: bold; }" +
                        ".comment { -fx-fill: #008000; }" +
                        ".string { -fx-fill: #a31515; }"
        ));


        // Metadata strip
        HBox metaStrip = new HBox(12);
        metaStrip.setPadding(new Insets(6, 0, 6, 0));
        Label emptyMeta = new Label("");
        metaStrip.getChildren().add(emptyMeta);

        rightPane.getChildren().addAll(header, metaStrip, previewArea);

        // Layout split pane
        SplitPane split = new SplitPane(leftPane, rightPane);
        split.setDividerPositions(0.32);

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setCenter(split);

        // Styling
        Scene scene = new Scene(root, 1000, 640);
        scene.getStylesheets().add("data:text/css," + encodeCss(getCss()));

        addBtn.setOnAction(e -> showSnippetDialog(null, primaryStage));
        deleteBtn.setOnAction(e -> deleteSelected());

        // Keyboard shortcuts
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), () -> showSnippetDialog(null, primaryStage));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> searchField.requestFocus());

        primaryStage.setTitle("Code manager.Snippet Manager");
        // set window icon if available - place codemanager.png next to jar
        try {
            InputStream is = new FileInputStream("icons/codemanager.png");
            Image ico = new Image(is);
            primaryStage.getIcons().add(ico);
        } catch (Exception ignored) {}

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private ToolBar createToolBar(Stage owner) {
        addBtn = new Button("+"); // assign to the field!
        addBtn.setId("btnAdd");
        addBtn.getStyleClass().add("circle-button");

        Button imp = new Button("Import");
        Button exp = new Button("Export");
        Button prefs = new Button("Preferences");

        ToolBar tb = new ToolBar(addBtn, new Separator(), imp, exp, new Region(), prefs);
        HBox.setHgrow(tb.getItems().get(tb.getItems().size()-1), Priority.ALWAYS);
        tb.setPadding(new Insets(6));

        // actions
        imp.setOnAction(e -> importSnippets(owner));
        exp.setOnAction(e -> exportSnippets(owner));
        prefs.setOnAction(e -> showPrefs(owner));

        return tb;
    }


    private void showPreview(Snippet s) {
        previewArea.clear();
        if (s == null) return;

        String code = s.code;
        previewArea.replaceText(code);

        // Define patterns
        String KEYWORDS = "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b";
        String COMMENTS = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
        String STRINGS = "\"([^\"\\\\]|\\\\.)*\"";

        Pattern pattern = Pattern.compile(
                "(?<KEYWORD>" + KEYWORDS + ")"
                        + "|(?<COMMENT>" + COMMENTS + ")"
                        + "|(?<STRING>" + STRINGS + ")"
        );

        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            if (matcher.group("KEYWORD") != null) {
                previewArea.setStyleClass(matcher.start(), matcher.end(), "keyword");
            } else if (matcher.group("COMMENT") != null) {
                previewArea.setStyleClass(matcher.start(), matcher.end(), "comment");
            } else if (matcher.group("STRING") != null) {
                previewArea.setStyleClass(matcher.start(), matcher.end(), "string");
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
            if (t.isEmpty()) { alert("Validation", "Title is required"); return; }
            Snippet s;
            if (base == null) s = new Snippet(t, code.getText(), language.getText().trim(), tags.getText().trim(), desc.getText().trim());
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
        } catch (Exception ex) { ex.printStackTrace(); alert("Import error", ex.getMessage()); }
    }

    private void exportSnippets(Stage owner) {
        // Export all to a zip (simple implementation omitted) - for demo export first selected
        Snippet s = listView.getSelectionModel().getSelectedItem();
        if (s == null) { alert("Export", "Select a snippet to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export snippet");
        fc.setInitialFileName(s.slug() + ".properties");
        File f = fc.showSaveDialog(owner);
        if (f == null) return;
        try (OutputStream os = new FileOutputStream(f)) {
            Properties p = s.toProperties();
            p.store(new OutputStreamWriter(os, StandardCharsets.UTF_8), "CodeSnippet");
        } catch (Exception ex) { ex.printStackTrace(); alert("Export error", ex.getMessage()); }
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
        } catch (Exception ex) { ex.printStackTrace(); }
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
            } catch (Exception ex) { ex.printStackTrace(); }
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
                try { code = new String(Base64.getDecoder().decode(codeB64), StandardCharsets.UTF_8); } catch (Exception ignored) {}
            }
            String language = p.getProperty("language", "");
            String tags = p.getProperty("tags", "");
            String desc = p.getProperty("description", "");
            Snippet s = new Snippet(title, code, language, tags, desc);
            try { s.dateCreated = new Date(Long.parseLong(p.getProperty("dateCreated", Long.toString(new Date().getTime())))); } catch (Exception ignored) {}
            try { s.lastModified = new Date(Long.parseLong(p.getProperty("lastModified", Long.toString(new Date().getTime())))); } catch (Exception ignored) {}
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
        public String toString() { return title; }
    }
}

