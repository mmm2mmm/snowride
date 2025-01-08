package cz.hudecekpetr.snowride.search;

import cz.hudecekpetr.snowride.tree.highelements.FileSuite;
import cz.hudecekpetr.snowride.tree.highelements.FolderSuite;
import cz.hudecekpetr.snowride.tree.highelements.Suite;
import cz.hudecekpetr.snowride.ui.Images;
import cz.hudecekpetr.snowride.ui.MainForm;
import cz.hudecekpetr.snowride.ui.SnowCodeArea;
import cz.hudecekpetr.snowride.ui.SnowCodeAreaProvider;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.fxmisc.flowless.VirtualizedScrollPane;

public class FullTextSearchScene {

	public static final FullTextSearchScene INSTANCE =  new FullTextSearchScene();
    private static final Stage stage = new Stage();
    private static final ListView<SearchResult> searchResultsView = new ListView<>();
    private static final TextField searchTextField = new TextField();

    public static class SearchResult {
        private final Suite suite;
        private final String line;
        private final int lineNumber;
        private final int lineAnchor;
        private final int anchor;
        private boolean isNameUnique = true;
        private final File file;

        public SearchResult(Suite suite, String line, int lineNumber, int lineAnchor, int anchor) {
            this.suite = suite;
            this.line = line;
            this.lineNumber = lineNumber;
            this.lineAnchor = lineAnchor;
            this.anchor = anchor;
            if (suite instanceof FileSuite) {
                this.file = ((FileSuite) suite).file;
            } else if (suite instanceof FolderSuite) {
                this.file = ((FolderSuite) suite).initFile;
            } else {
                throw new RuntimeException("Unexpected suite '" + suite.getClass() + "'.");
            }
        }

        public String getName() {
            return (isNameUnique ? "" : file.getParentFile().getName() + File.separator) + file.getName();
        }

        public File getFile() {
            return file;
        }

        public Suite getSuite() {
            return suite;
        }

        public String getLine() {
            return line;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public int getLineAnchor() {
            return lineAnchor;
        }

        public int getAnchor() {
            return anchor;
        }

        public boolean isNameUnique() {
            return isNameUnique;
        }

        public void setNameUnique(boolean nameUnique) {
            isNameUnique = nameUnique;
        }
    }

    static {
        VBox previewVBox = new VBox(5.0);
        previewVBox.setPadding(new Insets(2.0));

        searchTextField.setPromptText("Search text..");
        PauseTransition pause = new PauseTransition(Duration.millis(250.0));
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                searchResultsView.getItems().clear();
                if (newValue.isEmpty()) {
                    previewVBox.getChildren().clear();
                } else {
                    fullTextSearch(newValue);
                }
            });
            pause.playFromStart();
        });
        searchTextField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
                searchResultsView.fireEvent(event);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                navigateToSelectedSuite();
            }
        });

        VBox searchAdResultVBox = new VBox(5.0, searchTextField, searchResultsView);
        searchAdResultVBox.setPadding(new Insets(2.0));

        searchResultsView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                setGraphic(null);
                if (result != null) {
                    HBox root = new HBox(10.0);
                    int searchPhraseLength = searchTextField.getText().length();
                    TextFlow lineFlow = new TextFlow();
                    lineFlow.getChildren().add(new Text(result.getLine().substring(0, result.getLineAnchor())));
                    TextFlow highlight = new TextFlow(new Text(result.getLine().substring(result.getLineAnchor(), result.getLineAnchor() + searchPhraseLength)));
                    highlight.setStyle("-fx-background-color: yellow");
                    lineFlow.getChildren().add(highlight);
                    lineFlow.getChildren().add(new Text(result.getLine().substring(result.getLineAnchor() + searchPhraseLength)));
                    root.getChildren().add(lineFlow);

                    Region region = new Region();
                    HBox.setHgrow(region, Priority.ALWAYS);
                    root.getChildren().add(region);

                    Text fileInfo = new Text(result.getName() + " " + result.getLineNumber());
                    fileInfo.setFill(Color.GRAY);
                    root.getChildren().add(fileInfo);

                    setGraphic(root);
                }
            }
        });

        searchResultsView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, searchResult) -> {
            if (searchResult != null) {
            	VirtualizedScrollPane<SnowCodeArea> codeAreaPane = SnowCodeAreaProvider.getPreviewCodeArea(searchResult.getSuite());
                if (previewVBox.getChildren().isEmpty() || previewVBox.getChildren().get(1) != codeAreaPane) {
                    previewVBox.getChildren().clear();
                    previewVBox.getChildren().add(new Text(searchResult.getFile().getAbsolutePath()));
                    previewVBox.getChildren().add(codeAreaPane);
                    
                    codeAreaPane.getContent().setEditable(false); 
                    
                }
                codeAreaPane.getContent().selectRange(searchResult.getAnchor(), searchResult.getAnchor() + searchTextField.getText().length());
                codeAreaPane.getContent().requestFollowCaret();
            }
        });

        searchResultsView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                navigateToSelectedSuite();
            }
            searchTextField.requestFocus();
        });

        SplitPane fulltextSearchPane = new SplitPane(searchAdResultVBox, previewVBox);
        fulltextSearchPane.setOrientation(Orientation.VERTICAL);
        fulltextSearchPane.setDividerPosition(0, 0.3);

        stage.setScene(new Scene(fulltextSearchPane, 820.0, 1000.0));
        stage.setTitle("Snowride - Find in Files");
        stage.getIcons().add(Images.internet);
        stage.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
    }

    private static void navigateToSelectedSuite() {
        SearchResult searchResult = searchResultsView.getSelectionModel().getSelectedItem();
        MainForm.INSTANCE.keepTabSelection =  true;
        MainForm.INSTANCE.selectProgrammatically(searchResult.getSuite());
        MainForm.INSTANCE.getTabs().getSelectionModel().select(MainForm.INSTANCE.tabTextEdit);
        VirtualizedScrollPane <SnowCodeArea>  codeArea = SnowCodeAreaProvider.getTextEditCodeArea(searchResult.getSuite());
        codeArea.getContent().clearStyle(0, codeArea.getContent().getText().length());
        codeArea.getContent().selectRange(searchResult.getAnchor(), searchResult.getAnchor() + searchTextField.getText().length());
        codeArea.getContent().requestFollowCaret();
        stage.close();
    }

    private static void fullTextSearch(String searchPhrase) {
        List<SearchResult> results = new LinkedList<>();
        MainForm.INSTANCE.getRootElement().childrenRecursively.forEach(highElement -> {
            if (highElement instanceof Suite) {
                String contents = ((Suite) highElement).contents;
                if (contents != null && contents.toLowerCase().contains(searchPhrase.toLowerCase())) {
                    int globalIndex = 0;
                    String[] lines = contents.split("\n");
                    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                        String line = lines[lineIndex];
                        int index = 0;
                        while (true) {
                            int anchor = line.toLowerCase().indexOf(searchPhrase.toLowerCase(), index);
                            if (anchor < 0) break;
                            results.add(new SearchResult((Suite) highElement, line, lineIndex + 1, anchor, globalIndex + anchor));
                            index += anchor + searchPhrase.length();
                        }
                        globalIndex += line.length() + 1;
                    }
                }
            }
        });

        results.stream()
                .collect(Collectors.groupingBy(result -> result.getFile().getName()))
                .forEach((name, group) -> {
                    if (group.size() > 1 && group.stream().collect(Collectors.groupingBy(SearchResult::getFile)).size() > 1) {
                        group.forEach(result -> result.setNameUnique(false));
                    }
                });

        results.forEach(result -> {
            searchResultsView.getItems().add(result);
            searchResultsView.getSelectionModel().select(0);
        });
    }

    public static void setSearchPhrase(String searchPhrase) {
        searchTextField.setText(searchPhrase);
    }

    public static void show() {
        stage.show();
        searchTextField.requestFocus();
    }
}