package cz.hudecekpetr.snowride.undo;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An undo stack for one high element. Whenever Snowride's user makes a change to the high element, the change should be
 * placed on the stack. The change has code for "undo" and "redo". As you undo changes, the "undo" codes are executed and
 * you descend down the stack. As you redo changes, you ascend the stack back to the top, doing "redo" effects. If you
 * make a change while in the middle of the stack, the part of the stack that's above you is discarded.
 *
 * <p>
 *     Ideally, this should share code with {@link cz.hudecekpetr.snowride.ui.NavigationStack} because it's very similar
 *     but it doesn't.
 * </p>
 */
public class UndoStack {

    private List<UndoableOperation> theStack = new ArrayList<>();
    private int iAmBeforeOperation = 0;

    private String getTheStack() {
        return theStack.stream().map(UndoableOperation::toString).collect(Collectors.joining(" > "));
    }

    private void updatePossibilities() {
        canUndo.set(iAmBeforeOperation > 0);
        canRedo.set(iAmBeforeOperation < theStack.size());
        System.out.println(toString() + ": " + getTheStack());
    }

    private BooleanProperty canUndo = new SimpleBooleanProperty();
    private BooleanProperty canRedo = new SimpleBooleanProperty();

    public void clear() {
        theStack.clear();
        iAmBeforeOperation = 0;
        updatePossibilities();
    }

    /**
     * Call this just after you make a change to the high element to allow the user to undo that action.
     */
    public void iJustDid(UndoableOperation operation) {
        // Remove elements until you remove everything over our position
        while (iAmBeforeOperation < theStack.size()) {
            theStack.remove(theStack.size() - 1);
        }
        theStack.add(operation);
        iAmBeforeOperation++;
        updatePossibilities();
    }

    public void undoIfAble() {
        if (canUndo.getValue()) {
            iAmBeforeOperation--;
            updatePossibilities();
            UndoableOperation toUndo = theStack.get(iAmBeforeOperation);
            toUndo.undo();
        }
    }

    public void redoIfAble() {
        if (canRedo.getValue()) {
            UndoableOperation toRedo = theStack.get(iAmBeforeOperation);
            iAmBeforeOperation++;
            updatePossibilities();
            toRedo.redo();
        }

    }
}
