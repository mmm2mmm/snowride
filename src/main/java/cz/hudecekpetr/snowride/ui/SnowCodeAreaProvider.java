package cz.hudecekpetr.snowride.ui;

import cz.hudecekpetr.snowride.filesystem.LastChangeKind;
import cz.hudecekpetr.snowride.tree.highelements.HighElement;
import cz.hudecekpetr.snowride.tree.highelements.Suite;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SnowCodeAreaProvider {
	public static final SnowCodeAreaProvider INSTANCE = new SnowCodeAreaProvider();
    private static final SnowCodeArea nonEditableCodeArea = new SnowCodeArea(null);
    public static final VirtualizedScrollPane<SnowCodeArea> nonEditableCodeAreaPane = new VirtualizedScrollPane<>(nonEditableCodeArea);
    public static SnowCodeArea codeArea = nonEditableCodeArea;

    private static final Map<HighElement, VirtualizedScrollPane<SnowCodeArea>> textEditCodeAreaMap = new ConcurrentHashMap<>();
    private static final Map<HighElement, VirtualizedScrollPane<SnowCodeArea>> previewCodeAreaMap = new ConcurrentHashMap<>();

    static {
        nonEditableCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(nonEditableCodeArea));
        VBox.setVgrow(nonEditableCodeAreaPane, Priority.ALWAYS);
        nonEditableCodeArea.setEditable(false);
    }

    public static VirtualizedScrollPane<SnowCodeArea> getTextEditCodeArea(Suite suite) {
        VirtualizedScrollPane<SnowCodeArea> textEditCodeAreaPane = textEditCodeAreaMap.computeIfAbsent(suite, element->  SnowCodeAreaProvider.createCodeArea(suite));
        textEditCodeAreaPane.getContent().reload();
        codeArea = textEditCodeAreaPane.getContent();
        return textEditCodeAreaPane;
    }

    public static VirtualizedScrollPane<SnowCodeArea> getPreviewCodeArea(Suite suite) {
        VirtualizedScrollPane<SnowCodeArea> previewCodeAreaPane = previewCodeAreaMap.computeIfAbsent(suite, element->  SnowCodeAreaProvider.createCodeArea(suite));
        previewCodeAreaPane.getContent().reload();
        return previewCodeAreaPane;
    }

    private static VirtualizedScrollPane<SnowCodeArea> createCodeArea(Suite suite) {
        SnowCodeArea codeArea = new SnowCodeArea(suite);
        codeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!MainForm.INSTANCE.switchingTextEditContents && !codeArea.isReloading()) {
                HighElement element = codeArea.getHighElement();
                if (element instanceof Suite) {
                    Suite suiteElement = (Suite) element;
                    suiteElement.areTextChangesUnapplied =true;
                    suiteElement.contents = suiteElement.newlineStyle.convertToStyle(newValue);
                    MainForm.INSTANCE.changeOccurredTo(suiteElement, LastChangeKind.TEXT_CHANGED);
                }
            }
        });
        VirtualizedScrollPane<SnowCodeArea> pane = new VirtualizedScrollPane<>(codeArea);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    public static void clear() {
        textEditCodeAreaMap.clear();
        previewCodeAreaMap.clear();
    }

    public static void replaceHighElement(Suite newElement, HighElement oldElement) {
        replaceHighElementInMap(textEditCodeAreaMap, oldElement, newElement);
        replaceHighElementInMap(previewCodeAreaMap, oldElement, newElement);
    }

    private static void replaceHighElementInMap(Map<HighElement, VirtualizedScrollPane<SnowCodeArea>> map, HighElement oldElement, Suite newElement) {
        VirtualizedScrollPane<SnowCodeArea> scrollPane = map.get(oldElement);
        if (scrollPane != null) {
            scrollPane.getContent().setHighElement(newElement);
            map.remove(oldElement);
            map.put(newElement, scrollPane);
        }
    }
}