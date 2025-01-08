package cz.hudecekpetr.snowride.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.MultiChangeBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxmisc.undo.UndoManagerFactory;
import org.fxmisc.undo.impl.UndoManagerImpl;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;

import cz.hudecekpetr.snowride.tree.highelements.HighElement;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class SnowCodeArea extends CodeArea {
    private HighElement highElement;
    private static final String SEPARATOR = "\n";
    private boolean reloading;
    
    public SnowCodeArea(HighElement highElement) {
        this.highElement = highElement;
        reload();
        getUndoManager().forgetHistory();

        Nodes.addInputMap(
                this,
                InputMap.consume(
                        EventPattern.anyOf(
                                EventPattern.keyPressed(KeyCode.TAB, KeyCombination.SHORTCUT_ANY, KeyCombination.SHIFT_ANY),
                                EventPattern.keyPressed(KeyCode.Y, KeyCombination.SHORTCUT_ANY)
                        )
                )
        );

        keyBindings();
        setStyle("-fx-font-family: \"JetBrains Mono\"");
    }
    
    public void setHighElement(HighElement highElement) {
		this.highElement = highElement;
	}
    
    public HighElement getHighElement() {
		return highElement;
	}

    public boolean isReloading() {
		return reloading;
	}
    
    public void reload() {
        String contents = highElement != null ? highElement.contents.replace("\r\n", "\n") : null;
        if (contents != null && !getText().equals(contents)) {
            reloading = true;
            int indexOfDifference = StringUtils.indexOfDifference(getText(), contents);
            if (indexOfDifference > 0) {
                replaceText(indexOfDifference, getText().length(), contents.substring(indexOfDifference));
            } else {
                replaceText(contents);
                displaceCaret(0);
            }
            reloading = false;
        }
    }

    public void moveCaretToCurrentlyEditedScenario(String scenarioName) {
        int index = (scenarioName != null) ? getText().indexOf("\n" + scenarioName) + 1 : 0;

        Platform.runLater(() -> {
            requestFocus();
            displaceCaret(index);
            requestFollowCaret();
        });
    }

    public HighElement getCurrentlyEditedScenario() {
        int total = 0;
        for (String line : getText().split(SEPARATOR)) {
            int start = total;
            total += line.length() + 1;
            if (start <= getCaretPosition()) {
                if (line.matches("^\\w.*")) {
                    for (HighElement child : highElement.children) {
                        if (child.getShortName().equals(line)) {
                            return child;
                        }
                    }
                }
            }
        }
        return highElement;
    }

    private void keyBindings() {
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F3 && event.isShiftDown()) {
            	SnowCodeAreaSearchBox.INSTANCE.searchNext(true);
                event.consume();
            } else if (event.getCode() == KeyCode.F3) {
                SnowCodeAreaSearchBox.INSTANCE.searchNext(false);
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                String text = getText();
                if (text != null) {
                    clearStyle(0, text.length());
                }
                event.consume();
            } else if (event.getCode() == KeyCode.Y && event.isControlDown()) {
                // Delete current line or selected lines
                if (getSelectedText().isBlank()) {
                    Line line = line(getCaretPosition());
                    line.delete();
                } else {
                    for (Line line : selectedLines()) {
                        line.delete();
                    }
                }
                event.consume();
            } else if (event.getCode() == KeyCode.D && event.isControlDown()) {
                // Duplicate current line or selected text
                if (getSelectedText().isBlank()) {
                    Line line = line(getCaretPosition());
                    replaceText(line.getTo(), line.getTo(), SEPARATOR + line.getText());
                } else {
                    replaceText(getCaretPosition(), getCaretPosition(), getSelectedText());
                }
                event.consume();
            } else if (event.getCode() == KeyCode.SLASH && event.isControlDown()) {
                // Comment/Uncomment current line or selected lines
                if (getSelectedText().isBlank()) {
                    Line line = line(getCaretPosition());
                    if (line.isCommented()) {
                        line.unComment();
                    } else {
                        line.comment();
                    }
                } else {
                    int startIndex = getSelection().getStart();
                    int endIndex = getSelection().getEnd();

                    for (Line line : selectedLines()) {
                        if (!line.isCommented()) {
                            line.comment();
                            startIndex += 1;
                            endIndex += 1;
                        } else {
                            line.unComment();
                            startIndex -= 1;
                            endIndex -= 1;
                        }
                    }
                    selectRange(startIndex, endIndex);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.TAB) {
                handleTab(event);
            }
        });
    }

    private void handleTab(KeyEvent event) {
        MultiChangeBuilder<Collection  <String>, String, Collection<String>> multiChange = createMultiChange();
        String selectedText = getSelectedText();
        if (event.isShiftDown()) {
            if (selectedText.isBlank()) {
                int removedChars = tryToRemoveSpacesFromBeginningOfLine(multiChange, getCaretPosition());
                if (removedChars > 0) {
                    multiChange.commit();
                    displaceCaret(getCaretPosition() - removedChars);
                }
            } else {
                boolean doCommit = false;
                int selectionStart = getSelection().getStart();
                int selectionEnd = getSelection().getEnd();
                int firstLineRemovedChars = tryToRemoveSpacesFromBeginningOfLine(multiChange, selectionStart);
                selectionEnd -= firstLineRemovedChars;
                doCommit = firstLineRemovedChars != 0;
                int index = selectedText.indexOf(SEPARATOR);
                while (index >= 0) {
                    int removedChars = tryToRemoveSpacesFromBeginningOfLine(multiChange, selectionStart + index + SEPARATOR.length());
                    doCommit |= removedChars != 0;
                    index = selectedText.indexOf(SEPARATOR, index + SEPARATOR.length());
                    selectionEnd -= removedChars;
                }
                if (doCommit) {
                    multiChange.commit();
                }
                selectRange(selectionStart - firstLineRemovedChars, selectionEnd);
            }
        } else {
            if (selectedText.isBlank()) {
                insertText(getCaretPosition(), "    ");
            } else {
                int selectionStart = getSelection().getStart();
                int selectionEnd = getSelection().getEnd();
                 Position pos = offsetToPosition(selectionStart, TwoDimensional.Bias.Backward);
                multiChange.insertText(selectionStart - pos.getMinor(), "    ");
                selectionEnd += 4;
                int index = selectedText.indexOf(SEPARATOR);
                while (index >= 0) {
                    multiChange.insertText(selectionStart + index + SEPARATOR.length(), "    ");
                    index = selectedText.indexOf(SEPARATOR, index + SEPARATOR.length());
                    selectionEnd += 4;
                }
                multiChange.commit();
                selectRange(selectionStart + 4, selectionEnd);
            }
        }
    }

    private int tryToRemoveSpacesFromBeginningOfLine(MultiChangeBuilder< Collection<String>, String, Collection<String>> multiChange, int index) {
    	Position pos = offsetToPosition(index, TwoDimensional.Bias.Backward);
        int lineStartIndex = index - pos.getMinor();
        if (getText(lineStartIndex, lineStartIndex + 4).equals("    ")) {
            multiChange.replaceText(lineStartIndex, lineStartIndex + 4, "");
            return 4;
        }
        return 0;
    }

    private static class Line {
        private final String text;
        private final int from;
        private final int to;
		private SnowCodeArea area;

        public Line( SnowCodeArea area, String text, int from, int to) {
        	this.area = area;
            this.text = text;
            this.from = from;
            this.to = to;
        }

        public boolean isCommented() {
            return text.trim().startsWith("#");
        }

        public void unComment() {
            area.replaceText(from, to + 1, text.replaceFirst("#", "") + SEPARATOR);
        }

        public void comment() {
        	int commentIndex = text.length() - text.trim().length();
            area.replaceText(from, to + 1, text.substring(0, commentIndex) + "#" + text.substring(commentIndex) + SEPARATOR);
        }

        public void delete() {
        	area.replaceText(from, to + SEPARATOR.length(), "");
        }

        public String getText() {
            return text;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }
    }

    private List<Line> selectedLines() {
        int startIndex = getSelection().getStart();
        Line firstLine = line(startIndex);
        AtomicInteger currentIndex = new AtomicInteger(startIndex);
        List<Line> otherLines = Arrays.stream(getSelectedText().split(SEPARATOR))
                .map(it -> {
                    int currentIndeX2 = currentIndex.addAndGet( it.length() + SEPARATOR.length());
                    return line(currentIndeX2);
                })
                .collect(Collectors.toList());
        otherLines = otherLines.subList(0, otherLines.size() - 1);
        List<Line> lines = new ArrayList<>();
        lines.add(firstLine);
        lines.addAll(otherLines);
        return lines;
    }

    private Line line(int index) {
    	// Getting the substring before the separator
    	String textBefore = getText().substring(0, index);
    	int lastSeparatorIndex = textBefore.lastIndexOf(SEPARATOR);
    	if (lastSeparatorIndex != -1) {
    	    textBefore = textBefore.substring(lastSeparatorIndex + 1);
    	} else {
    	    // No separator found, keep the entire substring
    	}

    	// Getting the substring after the separator
    	String textAfter = getText().substring(index);
    	int firstSeparatorIndex = textAfter.indexOf(SEPARATOR);
    	if (firstSeparatorIndex != -1) {
    	    textAfter = textAfter.substring(0, firstSeparatorIndex);
    	} else {
    	    // No separator found, keep the entire substring
    	}
        int from = index - textBefore.length();
        int to = index + textAfter.length();
        return new Line(this,textBefore + textAfter, from, to);
    }

	public void apply(Consumer< SnowCodeArea> aplicator) {
		aplicator.accept(this);
	}
}