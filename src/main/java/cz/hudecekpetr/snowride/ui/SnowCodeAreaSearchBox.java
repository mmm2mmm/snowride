package cz.hudecekpetr.snowride.ui;

import java.util.Collections;

import cz.hudecekpetr.snowride.ui.SnowCodeAreaProvider;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Paint;

public class SnowCodeAreaSearchBox extends TextField {

    public static final SnowCodeAreaSearchBox INSTANCE = new SnowCodeAreaSearchBox();

	public SnowCodeAreaSearchBox() {
        setPromptText("Ctrl+F to search...");
        textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                searchBoxChanged(newValue);
            }
        });
        focusedProperty().addListener((observable, oldValue, isFocused) -> {
            if (!oldValue && isFocused && !SnowCodeAreaProvider.codeArea.getSelectedText().isEmpty()) {
                setText(SnowCodeAreaProvider.codeArea.getSelectedText());
            }
            if (isFocused) {
                if (!getText().isEmpty() && SnowCodeAreaProvider.codeArea.getText() != null) {
                    highlightAllOccurrences(getText());
                }
            }
        });
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F3 && event.isShiftDown()) {
                searchNext(true);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.F3) {
                searchNext(false);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                if (SnowCodeAreaProvider.codeArea.getText() != null) {
                    SnowCodeAreaProvider.codeArea.clearStyle(0, SnowCodeAreaProvider.codeArea.getText().length());
                }
                SnowCodeAreaProvider.codeArea.requestFocus();
            }
        });
    }

    /**
     * Selects the next instance of the searched text in the main editor.
     */
    public void searchNext(boolean reversed) {
        String searchPhrase = getText();
        SnowCodeAreaProvider.codeArea.apply(codeArea -> {
        	
            if (searchPhrase == null) {
                return;
            }
            int nextIndex = reversed
                    ? codeArea.getText().substring(0, codeArea.getAnchor() - 1).lastIndexOf(searchPhrase, 0)
                    : codeArea.getText().indexOf(searchPhrase, codeArea.getAnchor() + 1);

            if (nextIndex == -1) {
                int fromStartIndex = reversed
                        ? codeArea.getText().lastIndexOf(searchPhrase, 0)
                        : codeArea.getText().indexOf(searchPhrase, 0);
                if (fromStartIndex != -1) {
                    codeArea.selectRange(fromStartIndex, fromStartIndex + searchPhrase.length());
                    codeArea.requestFollowCaret();
                }
            } else {
                codeArea.selectRange(nextIndex, nextIndex + searchPhrase.length());
                codeArea.requestFollowCaret();
            }
            
        });
    }

    private void highlightAllOccurrences(String searchPhrase) {
        int index = 0;
        while (true) {
            index = SnowCodeAreaProvider.codeArea.getText().indexOf(searchPhrase, index);
            if (index < 0) {
                break;
            }
            SnowCodeAreaProvider.codeArea.setStyle(index, index + searchPhrase.length(), Collections.singleton(  "search-highlight"));
            index += searchPhrase.length();
        }
    }

    private void searchBoxChanged(String newValue) {
        String text = SnowCodeAreaProvider.codeArea.getText();
        if (text != null) {
            SnowCodeAreaProvider.codeArea.clearStyle(0, text.length());
        } else {
            return;
        }

        if (newValue.isEmpty()) {
            setStyle(null);
            return;
        }

        if (text.contains(newValue)) {
            highlightAllOccurrences(newValue);
            int firstIndex = text.indexOf(newValue, SnowCodeAreaProvider.codeArea.getCaretPosition());
            if (firstIndex == -1) {
                firstIndex = text.indexOf(newValue);
            }
            Paint paint = Paint.valueOf("#bff2ff");
            setStyle("-fx-control-inner-background: #" + paint.toString().substring(2));
            SnowCodeAreaProvider.codeArea.selectRange(firstIndex, firstIndex + newValue.length());
        } else {
            Paint paint = Paint.valueOf("#ffa0b9");
            setStyle("-fx-control-inner-background: #" + paint.toString().substring(2));
        }
    }
}