package cz.hudecekpetr.snowride.ui;

import cz.hudecekpetr.snowride.tree.LogicalLine;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class SnowTableArrowSelectionHelper {

    private TablePosition<Object, Object> selectionAnchor;
    private KeyCode horizontalDirection = null;
    private KeyCode verticalDirection = null;
    private boolean manualSelectionInProgress = false;

    public int minColumn = 0;
    public int maxColumn = 0;
    public int minRow = 0;
    public int maxRow = 0;

    private enum SelectionAnchorDirection {
        HORIZONTAL, VERTICAL
    }

    public void selectionModelChange(TableView.TableViewSelectionModel<LogicalLine> selectionModel) {
        if (manualSelectionInProgress) {
            return;
        }
        // reset selection anchor
        if (selectionModel.getSelectedCells().size() == 1) {
            selectionAnchor = selectionModel.getSelectedCells().get(0);
            horizontalDirection = null;
            verticalDirection = null;
        }
    }

    public void onKeyPressed(KeyEvent keyEvent, TableView.TableViewSelectionModel<LogicalLine> selectionModel) {
        if (keyEvent.isShiftDown() && (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.RIGHT || keyEvent.getCode() == KeyCode.UP || keyEvent.getCode() == KeyCode.DOWN)) {

            SelectionAnchorDirection direction = (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.RIGHT) ? SelectionAnchorDirection.HORIZONTAL : SelectionAnchorDirection.VERTICAL;

            // reset selection anchor
            if (selectionModel.getSelectedCells().size() == 1) {
                selectionAnchor = selectionModel.getSelectedCells().get(0);
                horizontalDirection = (direction == SelectionAnchorDirection.HORIZONTAL) ? keyEvent.getCode() : null;
                verticalDirection = (direction == SelectionAnchorDirection.VERTICAL) ? keyEvent.getCode() : null;
            } else {
                if (horizontalDirection == null || verticalDirection == null) {
                    recalculateSelectionIndexes(selectionModel);
                    if (verticalDirection == null && minRow != maxRow) {
                        verticalDirection = (selectionAnchor.getRow() == minRow) ? KeyCode.DOWN : KeyCode.UP;
                    }
                    if (horizontalDirection == null && minColumn != maxColumn) {
                        horizontalDirection = (selectionAnchor.getColumn() == minColumn) ? KeyCode.RIGHT : KeyCode.LEFT;
                    }
                }
            }
            // decide on the direction in which the selection is growing
            horizontalDirection = (horizontalDirection == null && direction == SelectionAnchorDirection.HORIZONTAL) ? keyEvent.getCode() : horizontalDirection;
            verticalDirection = (verticalDirection == null && direction == SelectionAnchorDirection.VERTICAL) ? keyEvent.getCode() : verticalDirection;

            recalculateSelectionIndexes(selectionModel);

            // decide what to do now
            if (direction == SelectionAnchorDirection.HORIZONTAL) {
                if (horizontalDirection == keyEvent.getCode()) {
                    if (keyEvent.getCode() == KeyCode.RIGHT) maxColumn++;
                    else minColumn--;
                } else {
                    if (keyEvent.getCode() == KeyCode.RIGHT) minColumn++;
                    else maxColumn--;
                }
            } else {
                if (verticalDirection == keyEvent.getCode()) {
                    if (keyEvent.getCode() == KeyCode.UP) minRow--;
                    else maxRow++;
                } else {
                    if (keyEvent.getCode() == KeyCode.UP) maxRow--;
                    else minRow++;
                }
            }

            // change horizontal/vertical direction when necessary
            if (minColumn > maxColumn) {
                int temp = minColumn;
                minColumn = maxColumn;
                maxColumn = temp;
                horizontalDirection = keyEvent.getCode();
            }
            if (minRow > maxRow) {
                int temp = minRow;
                minRow = maxRow;
                maxRow = temp;
                verticalDirection = keyEvent.getCode();
            }

            // clear selection and select necessary cells
            manualSelectionInProgress = true;
            selectionModel.clearSelection();
            for (int col = minColumn; col <= maxColumn; col++) {
                for (int row = minRow; row <= maxRow; row++ ) {
                    if (col < selectionModel.getTableView().getColumns().size()) {
                        selectionModel.select(row, selectionModel.getTableView().getColumns().get(col));
                    }
                }
            }
            manualSelectionInProgress = false;

            keyEvent.consume();
        }
    }

    private void recalculateSelectionIndexes(TableView.TableViewSelectionModel<LogicalLine> selectionModel) {
        // get min/max row/column indexes of currently selected cells
        minColumn = selectionAnchor.getColumn();
        maxColumn = selectionAnchor.getColumn();
        minRow = selectionAnchor.getRow();
        maxRow = selectionAnchor.getRow();
        selectionModel.getSelectedCells().forEach(cell -> {
            minColumn = Math.min(cell.getColumn(), minColumn);
            maxColumn = Math.max(cell.getColumn(), maxColumn);
            minRow = Math.min(cell.getRow(), minRow);
            maxRow = Math.max(cell.getRow(), maxRow);
        });
    }
}
