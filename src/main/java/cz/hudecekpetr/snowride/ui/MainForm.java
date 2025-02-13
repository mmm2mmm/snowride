package cz.hudecekpetr.snowride.ui;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import cz.hudecekpetr.snowride.SnowConstants;
import cz.hudecekpetr.snowride.filesystem.ReloadChangesWindow;
import cz.hudecekpetr.snowride.output.OutputParser;
import cz.hudecekpetr.snowride.search.FullTextSearchScene;
import cz.hudecekpetr.snowride.ui.popup.DocumentationPopup;
import cz.hudecekpetr.snowride.ui.popup.MessagesPopup;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.controlsfx.control.NotificationPane;

import cz.hudecekpetr.snowride.errors.ErrorsTab;
import cz.hudecekpetr.snowride.filesystem.Filesystem;
import cz.hudecekpetr.snowride.filesystem.FilesystemWatcher;
import cz.hudecekpetr.snowride.filesystem.LastChangeKind;
import cz.hudecekpetr.snowride.fx.SnowAlert;
import cz.hudecekpetr.snowride.fx.Underlining;
import cz.hudecekpetr.snowride.fx.systemcolor.StringURLStreamHandlerFactory;
import cz.hudecekpetr.snowride.fx.systemcolor.SystemColorService;
import cz.hudecekpetr.snowride.parser.GateParser;
import cz.hudecekpetr.snowride.runner.RunTab;
import cz.hudecekpetr.snowride.runner.TestResult;
import cz.hudecekpetr.snowride.semantics.externallibraries.ReloadExternalLibraries;
import cz.hudecekpetr.snowride.semantics.findusages.FindUsages;
import cz.hudecekpetr.snowride.settings.Settings;
import cz.hudecekpetr.snowride.settings.SnowFile;
import cz.hudecekpetr.snowride.tree.DeepCopy;
import cz.hudecekpetr.snowride.tree.highelements.ExternalResourcesElement;
import cz.hudecekpetr.snowride.tree.highelements.FileSuite;
import cz.hudecekpetr.snowride.tree.highelements.FolderSuite;
import cz.hudecekpetr.snowride.tree.highelements.HighElement;
import cz.hudecekpetr.snowride.tree.highelements.ISuite;
import cz.hudecekpetr.snowride.tree.highelements.Scenario;
import cz.hudecekpetr.snowride.tree.highelements.Suite;
import cz.hudecekpetr.snowride.tree.highelements.UltimateRoot;
import cz.hudecekpetr.snowride.ui.about.AboutChangelog;
import cz.hudecekpetr.snowride.ui.about.AboutKeyboardShortcuts;
import cz.hudecekpetr.snowride.ui.about.AboutSnowride;
import cz.hudecekpetr.snowride.ui.settings.SettingsWindow;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import static cz.hudecekpetr.snowride.filesystem.LastChangeKind.*;

public class MainForm {
    public static final Font BIGGER_FONT = new Font("System Regular", 14);
    public static final Font TEXT_EDIT_FONT = new Font("Courier New", 12);
    public static MainForm INSTANCE;
    public static DocumentationPopup documentationPopup = new DocumentationPopup();
    public static MessagesPopup messagesPopup = new MessagesPopup();
    private final SerializingTab serializingTab;
    private final TabPane tabs;
    public final Tab tabTextEdit;
    private final TextEditTab textEditTab;
    private final NotificationPane notificationPane;
    private final ToolBar toolBar;
    public BooleanProperty canSave = new SimpleBooleanProperty(false);
    public RunTab runTab;
    public GateParser gateParser = new GateParser();
    public NavigationStack navigationStack = new NavigationStack();
    public LongRunningOperation projectLoad = new LongRunningOperation();
    public GridTab gridTab;
    public boolean keepTabSelection;
    boolean switchingTextEditContents = false;
    private boolean humanInControl = true;
    private BooleanProperty canNavigateBack = new SimpleBooleanProperty(false);
    private BooleanProperty canNavigateForwards = new SimpleBooleanProperty(false);
    private Stage stage;
    private TreeView<HighElement> projectTree;
    private SearchSuites searchSuitesAutoCompletion;
    private Filesystem filesystem;
    private SeparatorMenuItem separatorBeforeRecentProjects;
    private SeparatorMenuItem separatorAfterRecentProjects;
    private DirectoryChooser openFolderDialog;
    private FileChooser openSnowFileDialog;
    private FileChooser openOutputFileDialog;
    private FileChooser saveSnowFileDialog;
    private Menu projectMenu;
    private TextField tbSearchTests;
    private ContextMenu treeContextMenu;
    private ExecutorService projectLoader = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService endTheToastExecutor = Executors.newSingleThreadScheduledExecutor();
    private String notificationShowingWhat = null;
    private Button bStop;
    private Scene scene;

    public MainForm(Stage stage) {
        SystemColorService.initialize();
        URL.setURLStreamHandlerFactory(new StringURLStreamHandlerFactory());
        INSTANCE = this;
        this.stage = stage;
        filesystem = new Filesystem(this);
        canNavigateBack.bindBidirectional(navigationStack.canNavigateBack);
        canNavigateForwards.bindBidirectional(navigationStack.canNavigateForwards);
        stage.setTitle("Snowride");
        stage.setOnCloseRequest(event -> {
            boolean abort = maybeOfferSaveDiscardOrCancel();
            if (abort) {
                event.consume();
                return;
            }
            System.exit(0);
        });
        runTab = new RunTab(this);
        Tab tabRun = runTab.createTab();
        MenuBar mainMenu = buildMainMenu();
        toolBar = buildToolBar();
        updateAdditionalToolbarButtonsVisibility();
        textEditTab = new TextEditTab(this);
        tabTextEdit = textEditTab.createTab();
        gridTab = new GridTab(this);
        Tab tabGrid = gridTab.createTab();
        serializingTab = new SerializingTab(this);
        serializingTab.createTab();
        VBox searchableTree = createLeftPane();
        ErrorsTab errorsTab = new ErrorsTab(this);
        Tab tabErrors = errorsTab.tab;
        tabs = new TabPane(tabTextEdit, tabGrid, tabRun,
                // Serializing is now mostly stable, so we don't need the debugging tab:
                //  tabSerializing,
                tabErrors);
        tabs.getSelectionModel().select(tabGrid);
        tabs.getSelectionModel().selectedItemProperty().addListener(serializingTab::selTabChanged);
        tabs.getSelectionModel().selectedItemProperty().addListener(textEditTab::selTabChanged);
        SplitPane treeAndGrid = new SplitPane(searchableTree, tabs);
        treeAndGrid.setOrientation(Orientation.HORIZONTAL);
        SplitPane.setResizableWithParent(searchableTree, false);
        treeAndGrid.setDividerPosition(0, 0.3);
        VBox vBox = new VBox(mainMenu, toolBar, treeAndGrid);
        notificationPane = new NotificationPane(vBox);
        VBox.setVgrow(treeAndGrid, Priority.ALWAYS);

        this.scene = new Scene(notificationPane, Settings.getInstance().width, Settings.getInstance().height);
        scene.getStylesheets().add(getClass().getResource("/snow.css").toExternalForm());
        scene.getStylesheets().add("snow://autogen.css");
        addGlobalShortcuts(scene);
        stage.setScene(scene);
        if (Settings.getInstance().x != -1) {
            stage.setX(Settings.getInstance().x);
        }
        if (Settings.getInstance().y != -1) {
            stage.setY(Settings.getInstance().y);
        }
        stage.setMaximized(Settings.getInstance().maximized);
        stage.xProperty().addListener((observable, oldValue, newValue) -> mainWindowCoordinatesChanged());
        stage.yProperty().addListener((observable, oldValue, newValue) -> mainWindowCoordinatesChanged());
        stage.widthProperty().addListener((observable, oldValue, newValue) -> mainWindowCoordinatesChanged());
        stage.heightProperty().addListener((observable, oldValue, newValue) -> mainWindowCoordinatesChanged());
        stage.maximizedProperty().addListener((observable, oldValue, newValue) -> mainWindowCoordinatesChanged());
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/Snowflake3.png")));
        stage.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                ReloadChangesWindow.considerActivating();
            }
        });
    }

    public HighElement getFocusedElement() {
        TreeItem<HighElement> focusedItem = getProjectTree().getFocusModel().getFocusedItem();
        if (focusedItem != null) {
            return focusedItem.getValue();
        } else {
            return null;
        }
    }

    /**
     * Gets the folder suite that's the root of the project.
     */
    public UltimateRoot getRootElement() {
        return (UltimateRoot) getProjectTree().getRoot().getValue();
    }

    public TreeView<HighElement> getProjectTree() {
        return projectTree;
    }

    private void addGlobalShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SHORTCUT) {
                Underlining.ctrlDown = true;
                Underlining.update();
            }
            if (event.getCode() == KeyCode.F && event.isShortcutDown() && event.isShiftDown()) {
                if (getTabs().getSelectionModel().getSelectedItem() == tabTextEdit) {
                    String selectedText = SnowCodeAreaProvider.INSTANCE.getCodeArea().getSelectedText();
                    if (StringUtils.isNotBlank(selectedText)) {
                        FullTextSearchScene.INSTANCE.setSearchPhrase(selectedText);
                    }
                }
                FullTextSearchScene.INSTANCE.show();
                event.consume();
            } else if (event.getCode() == KeyCode.F && event.isShortcutDown() && getTabs().getSelectionModel().getSelectedItem() == tabTextEdit) {
                SnowCodeAreaSearchBox.INSTANCE.requestFocus();
                event.consume();
            } else if (event.getCode() == KeyCode.F5 || event.getCode() == KeyCode.F8) {
                runTab.clickRun(null);
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT && event.isAltDown()) {
                goBack();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT && event.isAltDown()) {
                goForwards();
                event.consume();
            } else if (event.getCode() == KeyCode.N && event.isShortcutDown() || event.getCode() == KeyCode.F && event.isShortcutDown()) {
                tbSearchTests.requestFocus();
                searchSuitesAutoCompletion.trigger();
                event.consume();
            } else {
                if (event.isAltDown()) {
                    // "Alt" should not activate the main menu bar. Normal applications don't do that.
                    event.consume();
                }
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.SHORTCUT) {
                Underlining.ctrlDown = false;
                Underlining.update();
            }
        });
    }

    public void changeOccurredTo(HighElement whatChanged, LastChangeKind how) {
        if (how == TEXT_CHANGED && whatChanged.contents.equals(whatChanged.pristineContents)) {
            whatChanged.unsavedChanges = PRISTINE;
            whatChanged.children.forEach(highElement -> highElement.unsavedChanges = PRISTINE);
        } else if (how == STRUCTURE_CHANGED && whatChanged instanceof Scenario && whatChanged.contents.equals(whatChanged.pristineContents)) {
            whatChanged.parent.updateContents();
            whatChanged.unsavedChanges = PRISTINE;
        } else if (how == STRUCTURE_CHANGED && whatChanged instanceof Suite && whatChanged.children.stream().allMatch(highElement -> highElement.unsavedChanges == PRISTINE)) {
            // Ensure changes done for Suite in Grid Edit are "applied" first
            ((Suite) whatChanged).updateContents();
            if (whatChanged.contents.equals(whatChanged.pristineContents)) {
                whatChanged.unsavedChanges = PRISTINE;
            } else {
                whatChanged.unsavedChanges = how;
            }
        } else {
            whatChanged.unsavedChanges = how;
        }
        whatChanged.treeNode.setValue(null);
        whatChanged.treeNode.setValue(whatChanged);
        // TODO It's possible the user change the tag which should cause the number of tests to update
        // but for performance reasons we choose not to update the number here.
        boolean canActuallySave = getRootElement().childrenRecursively.stream().anyMatch(e -> e.unsavedChanges != PRISTINE);
        canSave.set(canActuallySave);
    }

    private void mainWindowCoordinatesChanged() {
        Settings.getInstance().x = stage.getX();
        Settings.getInstance().y = stage.getY();
        Settings.getInstance().width = stage.getWidth();
        Settings.getInstance().height = stage.getHeight();
        Settings.getInstance().maximized = stage.isMaximized();
        Settings.getInstance().saveAllSettings();
    }

    private VBox createLeftPane() {
        tbSearchTests = new TextField();
        tbSearchTests.setPromptText("Search for tests, keywords or suites (Ctrl+N)...");
        searchSuitesAutoCompletion = new SearchSuites(this);
        searchSuitesAutoCompletion.bind(tbSearchTests);
        projectTree = new TreeView<>();
        projectTree.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        projectTree.setStyle("-fx-font-size: " + Settings.getInstance().treeSizeItemHeight + "pt;");
        projectTree.setShowRoot(false);
        projectTree.setSkin(new ProjectTreeViewSkin(projectTree));
        projectTree.setOnKeyPressed(event -> {
            // Do not permit moving with CTRL+UP and CTRL+DOWN, because we're using these shortcuts to hard-move children.
            if (event.getCode() == KeyCode.UP && event.isShortcutDown()) {
                event.consume();
            }
            if (event.getCode() == KeyCode.DOWN && event.isShortcutDown()) {
                event.consume();
            }

            // for more convenient navigation in projectTree (using only right arrow key)
            if (event.getCode() == KeyCode.RIGHT && !event.isShortcutDown() && !event.isAltDown() && !event.isShortcutDown()) {
                TreeItem<HighElement> selectedItem = projectTree.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.getValue() instanceof Scenario) {
                    projectTree.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
                    event.consume();
                }
            }
        });

        projectTree.setOnKeyReleased(event -> {
            TreeItem<HighElement> focusedItem = projectTree.getFocusModel().getFocusedItem();
            if (focusedItem != null) {
                if (event.getCode() == KeyCode.SPACE) {
                    HighElement element = focusedItem.getValue();
                    withUpdateSuppression(() -> invertCheckboxes(element));
                }
                if (event.getCode() == KeyCode.F2) {
                    renameIfAble(focusedItem.getValue());
                    event.consume();
                }

            }

            TreeItem<HighElement> selectedItem = projectTree.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                if (selectedItem.getValue() instanceof Scenario) {
                    if (event.getCode() == KeyCode.UP && event.isShortcutDown() && !event.isAltDown()) {
                        moveScenario((Scenario) selectedItem.getValue(), -1);
                        event.consume();
                    }
                    if (event.getCode() == KeyCode.DOWN && event.isShortcutDown() && !event.isAltDown()) {
                        moveScenario((Scenario) selectedItem.getValue(), 1);
                        event.consume();
                    }
                }
                if (selectedItem.getValue() != null) {
                    if (event.getCode() == KeyCode.UP && event.isShortcutDown() && event.isAltDown() && event.isShortcutDown()) {
                        navigateToSelectedScenario(selectedItem.getValue(), KeyCode.UP);
                        event.consume();
                    }
                    if (event.getCode() == KeyCode.DOWN && event.isShortcutDown() && event.isAltDown() && event.isShortcutDown()) {
                        navigateToSelectedScenario(selectedItem.getValue(), KeyCode.DOWN);
                        event.consume();
                    }
                }
            }
        });
        projectTree.getFocusModel().focusedItemProperty().addListener((observable, oldValue, newValue) -> {
            // FIXME: workaround to ensure 'cancelEdit' is called on Cell in Grid editor when focus is lost
            boolean humanInControl = this.humanInControl;
            Platform.runLater(() -> {
                if (oldValue != null) {
                    oldValue.getValue().applyText();
                }
                if (newValue != null) {
                    if (humanInControl) {
                        navigationStack.standardEnter(newValue.getValue());
                        textEditTab.manuallySelected = true;
                    }
                    reloadElementIntoTabs(newValue.getValue());
                }
            });
        });
        treeContextMenu = new ContextMenu();
        treeContextMenu.getItems().add(new MenuItem("A"));


        projectTree.setOnContextMenuRequested(event -> {

            treeContextMenu.getItems().clear();
            TreeItem<HighElement> focused = projectTree.getFocusModel().getFocusedItem();
            if (focused != null) {
                treeContextMenu.getItems().addAll(buildContextMenuFor(focused));
            }
        });

        projectTree.setContextMenu(treeContextMenu);
        ProgressBar progressBar = new ProgressBar();
        progressBar.visibleProperty().bind(projectLoad.progress.lessThan(1));
        progressBar.progressProperty().bind(projectLoad.progress);
        StackPane stackPane = new StackPane(projectTree, progressBar);
        StackPane.setAlignment(progressBar, Pos.CENTER);
        VBox vBox = new VBox(tbSearchTests, stackPane);
        VBox.setVgrow(stackPane, Priority.ALWAYS);
        return vBox;
    }

    private void navigateToSelectedScenario(HighElement currentScenario, KeyCode direction) {
        AtomicReference<Scenario> previousSelectedScenario = new AtomicReference<>();
        AtomicReference<Boolean> navigateToNextSelectedScenario = new AtomicReference<>(false);

        getRootElement().selfAndDescendantHighElements().anyMatch(element -> {
            if (element == currentScenario) {
                if (direction == KeyCode.DOWN) {
                    navigateToNextSelectedScenario.set(true);
                    return false;
                } else {
                    Scenario scenario = previousSelectedScenario.get();
                    if (scenario != null) {
                        navigationStack.standardEnter(scenario);
                        selectProgrammatically(scenario);
                        return true;
                    }
                }
            }
            if (element instanceof Scenario) {
                Scenario scenario = (Scenario) element;
                if (scenario.isTestCase() && scenario.checkbox.isSelected()) {
                    if (navigateToNextSelectedScenario.get()) {
                        navigationStack.standardEnter(scenario);
                        selectProgrammatically(scenario);
                        return true;
                    }
                    previousSelectedScenario.set(scenario);
                }
            }
            return false;
        });
    }

    private List<MenuItem> buildContextMenuFor(TreeItem<HighElement> forWhat) {
        List<MenuItem> menu = new ArrayList<>();
        HighElement element = forWhat.getValue();
        if (element instanceof FolderSuite) {
            MenuItem new_folder_suite = new MenuItem("New folder");
            new_folder_suite.setOnAction(event -> {
                String name = TextFieldForm.askForText("Create new folder suite", "Enter folder name:", "Create new folder", "");
                if (name != null) {
                    filesystem.createNewFolderInTree((FolderSuite) element, name);
                }
            });
            menu.add(new_folder_suite);
            MenuItem new_file_suite = new MenuItem("New file");
            new_file_suite.setOnAction(event -> {
                String name = TextFieldForm.askForText("Create new file", "Enter file name (without .robot extension):", "Create new file", "");
                if (name != null) {
                    try {
                        filesystem.createNewRobotFile((FolderSuite) element, name);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            menu.add(new_file_suite);
        }
        maybeAddSeparator(menu);
        if (element instanceof FileSuite) {
            MenuItem new_test_case = new MenuItem("New test case");
            new_test_case.setOnAction(event -> {
                String name = TextFieldForm.askForText("New test case", "Test case name:", "Create new test case", "");
                if (name != null) {
                    ((FileSuite) element).createNewChild(name, true, MainForm.this);
                    changeOccurredTo(element, STRUCTURE_CHANGED);
                }
            });
            menu.add(new_test_case);
            MenuItem open_in_external_editor = new MenuItem("Open in external editor");
            open_in_external_editor.setOnAction(event -> {
                try {
                    Desktop.getDesktop().edit(((FileSuite) element).file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            menu.add(open_in_external_editor);
        }
        if (element instanceof ISuite && !(element instanceof ExternalResourcesElement)) {
            MenuItem new_user_keyword = new MenuItem("New user keyword");
            new_user_keyword.setOnAction(event -> {
                String name = TextFieldForm.askForText("New user keyword", "Keyword name:", "Create new user keyword", "");
                if (name != null) {
                    ((ISuite) element).createNewChild(name, false, MainForm.this);
                    changeOccurredTo(element, STRUCTURE_CHANGED);
                }
            });
            menu.add(new_user_keyword);
        }
        maybeAddSeparator(menu);
        if (element instanceof FolderSuite || element instanceof FileSuite) {
            MenuItem select_all_tests = new MenuItem("Select all tests");
            select_all_tests.setOnAction(event -> setCheckboxes(element, true));
            menu.add(select_all_tests);
            if (element.selfAndDescendantHighElements().anyMatch(he -> he instanceof Scenario && ((Scenario) he).lastTestResult == TestResult.FAILED)) {
                MenuItem select_failed_tests = new MenuItem("Select failed tests only");
                select_failed_tests.setOnAction(event -> withUpdateSuppression(() -> selectFailedTests(element)));
                menu.add(select_failed_tests);
            }
            MenuItem deselect_all_tests = new MenuItem("Deselect all tests");
            deselect_all_tests.setOnAction(event -> setCheckboxes(element, false));
            menu.add(deselect_all_tests);
            MenuItem expandAll = new MenuItem("Expand all");
            expandAll.setOnAction(event -> setExpandedFlagRecursively(element.treeNode, true));
            menu.add(expandAll);
            MenuItem collapseAll = new MenuItem("Collapse all");
            collapseAll.setOnAction(event -> setExpandedFlagRecursively(element.treeNode, false));
            menu.add(collapseAll);
        }
        if (element != getRootElement() && element.parent != getRootElement()
                && element.parent != null && !(element.parent instanceof ExternalResourcesElement)) {
            maybeAddSeparator(menu);
            // Everything except for root directories and the two special elements can be deleted or renamed
            MenuItem rename = new MenuItem("Rename");
            rename.setAccelerator(new KeyCodeCombination(KeyCode.F2));
            rename.setOnAction(event -> renameIfAble(element));
            menu.add(rename);
            MenuItem delete = new MenuItem("Delete");
            delete.setOnAction(event -> {
                ButtonType deleteAnswer = new ButtonType("Delete");
                Alert alert = new SnowAlert(Alert.AlertType.CONFIRMATION, "Delete " + element + "?",
                        deleteAnswer,
                        ButtonType.NO);
                if (alert.showAndWait().orElse(ButtonType.NO) == deleteAnswer) {
                    delete(element);
                }
            });
            menu.add(delete);
            if (element instanceof Scenario) {
                Scenario asScenario = (Scenario) element;
                MenuItem copy = new MenuItem("Copy");
                String isWhat = asScenario.isTestCase() ? "test case" : "user keyword";
                copy.setOnAction(event -> {
                    String name = TextFieldForm.askForText("Copy a " + isWhat, "Name of the copy:", "Create new " + isWhat + " as a copy", element.getShortName() + " - copy");
                    if (name != null) {
                        Scenario newCopy = element.parent.createNewChild(name, asScenario.isTestCase(), MainForm.this, element);
                        DeepCopy.copyOldIntoNew(asScenario, newCopy);
                        changeOccurredTo(element.parent, STRUCTURE_CHANGED);
                    }
                });
                menu.add(copy);

                MenuItem moveUp = new MenuItem("Move up");
                moveUp.setAccelerator(new KeyCodeCombination(KeyCode.UP, KeyCombination.SHORTCUT_DOWN));
                moveUp.setOnAction(event -> moveScenario(asScenario, -1));
                menu.add(moveUp);

                MenuItem moveDown = new MenuItem("Move down");
                moveDown.setAccelerator(new KeyCodeCombination(KeyCode.DOWN, KeyCombination.SHORTCUT_DOWN));
                moveDown.setOnAction(event -> moveScenario(asScenario, 1));
                menu.add(moveDown);

            }
        }
        if (element instanceof Scenario) {
            Scenario asScenario = (Scenario) element;
            maybeAddSeparator(menu);
            if (asScenario.isTestCase()) {
                MenuItem runThis = new MenuItem("Run just this test", loadIcon(Images.play));
                runThis.disableProperty().bind(runTab.canRun.not());
                runThis.setOnAction(event -> {
                    deselectAll();
                    asScenario.checkbox.setSelected(true);
                    runTab.clickRun(event);
                });
                menu.add(runThis);
            } else {
                MenuItem runThis = new MenuItem("Run all tests that use this keyword", loadIcon(Images.doublePlay));
                runThis.disableProperty().bind(runTab.canRun.not());
                runThis.setOnAction(event -> {
                    deselectAll();
                    withUpdateSuppression(() -> {
                        FindUsages.findUsagesAsTestCases(null, asScenario, getRootElement()).forEach(sc -> sc.checkbox.setSelected(true));
                        runTab.clickRun(event);
                    });
                });
                menu.add(runThis);
            }
        }
        if (menu.size() == 0) {
            MenuItem menuItemNothing = new MenuItem("(you can't do anything with this tree node)");
            menuItemNothing.setDisable(true);
            menu.add(menuItemNothing);
        }
        return menu;
    }

    private void moveScenario(Scenario asScenario, int indexDifference) {
        System.out.println("Before: " + projectTree.getRow(asScenario.treeNode));
        asScenario.parent.displaceChildScenario(asScenario, indexDifference);
        // We can reselect the target immediately because we have a guarantee that it's expanded:
        int index = projectTree.getRow(asScenario.treeNode);
        System.out.println("After: " + projectTree.getRow(asScenario.treeNode));
        projectTree.getFocusModel().focus(index);
        projectTree.getSelectionModel().select(index);
    }

    private void renameIfAble(HighElement element) {
        if (element != getRootElement() && element.parent != getRootElement()
                && element.parent != null && !(element.parent instanceof ExternalResourcesElement)) {
            // Not using getShortName() because we want to preserve underscores and, more importantly, execution order prefix.
            String newName = TextFieldForm.askForText("Rename " + element, "New name:", "Rename", element.getShortNameAsOnDisk());
            if (newName != null) {
                element.renameSelfTo(newName, MainForm.this);
            }
        }
    }

    private void setExpandedFlagRecursively(TreeItem<HighElement> treeNode, boolean shouldBeExpanded) {
        treeNode.setExpanded(shouldBeExpanded);
        for (TreeItem<HighElement> child : treeNode.getChildren()) {
            setExpandedFlagRecursively(child, shouldBeExpanded);
        }
    }

    public void selectFailedTests(HighElement element) {
        boolean isToBeChecked = element instanceof Scenario && ((Scenario) element).lastTestResult == TestResult.FAILED;
        element.checkbox.setSelected(isToBeChecked);
        if (isToBeChecked) {
            maybeExpandUpTo(element);
        }
        for (HighElement child : element.children) {
            selectFailedTests(child);
        }
    }

    private void maybeExpandUpTo(HighElement element) {
        if (Settings.getInstance().cbAutoExpandSelectedTests) {
            expandUpTo(element.treeNode);
        }
    }

    /**
     * Runs a block of code while suppressing some changes to UI elements during that time, then updates the UI afterwards.
     * This improves performance.
     */
    private void withUpdateSuppression(Runnable block) {
        runTab.suppressRunNumberChangeNotifications = true;
        block.run();
        runTab.suppressRunNumberChangeNotifications = false;
        runTab.maybeRunNumberChanged();
    }

    private void invertCheckboxes(HighElement element) {
        element.checkbox.setSelected(!element.checkbox.isSelected());
        for (HighElement child : element.children) {
            invertCheckboxes(child);
        }
    }

    private void setCheckboxes(HighElement element, boolean shouldBeChecked) {
        withUpdateSuppression(() -> setCheckboxesRecursive(element, shouldBeChecked));
    }

    private void setCheckboxesRecursive(HighElement element, boolean shouldBeChecked) {
        element.checkbox.setSelected(shouldBeChecked);
        if (shouldBeChecked && element instanceof Scenario && ((Scenario) element).isTestCase()) {
            maybeExpandUpTo(element);
        }
        for (HighElement child : element.children) {
            setCheckboxesRecursive(child, shouldBeChecked);
        }
    }

    private void delete(HighElement element) {
        element.deleteSelf(this);
    }

    private void maybeAddSeparator(List<MenuItem> menu) {
        if (menu.size() > 0 && !(menu.get(menu.size() - 1) instanceof SeparatorMenuItem)) {
            menu.add(new SeparatorMenuItem());
        }
    }

    private ToolBar buildToolBar() {
        Button bNavigateBack = new Button(null, loadIcon(Images.goLeft));
        bNavigateBack.setTooltip(new Tooltip("Navigate back"));
        Button bNavigateForwards = new Button(null, loadIcon(Images.goRight));
        bNavigateForwards.setTooltip(new Tooltip("Navigate forwards"));
        Button bSaveAll = new Button("Save all", loadIcon(Images.save));
        Button bRun = new Button("Run", loadIcon(Images.play));
        bRun.textProperty().bind(runTab.runCaption);
        bStop = new Button("Stop", loadIcon(Images.stop));
        bNavigateBack.disableProperty().bind(canNavigateBack.not());
        bNavigateBack.setOnAction(event1 -> goBack());
        bNavigateForwards.disableProperty().bind(canNavigateForwards.not());
        bNavigateForwards.setOnAction(event -> goForwards());
        bSaveAll.disableProperty().bind(canSave.not());
        bSaveAll.setOnAction(event -> saveAll());
        bRun.disableProperty().bind(runTab.canRun.not());
        bRun.setOnAction(runTab::clickRun);
        bStop.disableProperty().bind(runTab.canStop.not());
        bStop.setOnAction(runTab::clickStop);
        return new ToolBar(bNavigateBack, bNavigateForwards, bSaveAll, bRun, bStop);
    }

    public void updateAdditionalToolbarButtonsVisibility() {
        int removeAt = toolBar.getItems().indexOf(bStop) + 1;
        while (toolBar.getItems().size() > removeAt) {
            toolBar.getItems().remove(removeAt);
        }
        if (Settings.getInstance().toolbarReloadAll) {
            Button bReloadAll = new Button("Reload all", loadIcon(Images.refresh));
            bReloadAll.setTooltip(new Tooltip("Reloads the current Robot project as though you restarted Snowride."));
            bReloadAll.setOnAction(actionEvent -> reloadAll());
            toolBar.getItems().add(bReloadAll);
        }
        if (Settings.getInstance().toolbarDeselectEverything) {
            Button bDeselectAll = new Button("Deselect all");
            bDeselectAll.setTooltip(new Tooltip("Deselects all tests so that everything would be run the next time you run tests."));
            bDeselectAll.setOnAction(actionEvent -> deselectAll());
            toolBar.getItems().add(bDeselectAll);
        }
    }

    private void deselectAll() {
        setCheckboxes(getRootElement(), false);
    }

    public void reloadAll() {
        loadProjectFromFolderOrSnowFile(new File(Settings.getInstance().lastOpenedProject));
    }

    // Hello
    private ImageView loadIcon(Image image) {
        return new ImageView(image);
    }

    private void reloadElementIntoTabs(HighElement element) {
        reloadElementIntoTabs(element, true);
    }

    private void reloadElementIntoTabs(HighElement element, boolean andSwitchToGrid) {
        switchingTextEditContents = true;
        textEditTab.loadElement(element);
        gridTab.loadElement(element);
        serializingTab.loadElement(element);
        switchingTextEditContents = false;
        if (element != null && andSwitchToGrid) {
            if (keepTabSelection) {
                keepTabSelection = false;
            } else {
                tabs.getSelectionModel().select(gridTab.getTabGrid());
            }
        }
    }

    public void saveAll() {
        try {
            projectTree.getRoot().getValue().saveAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        canSave.setValue(false);
    }

    private MenuBar buildMainMenu() {
        MenuBar mainMenu = new MenuBar();
        // --------- Project
        projectMenu = new Menu("Project");

        MenuItem bLoadArbitrary = new MenuItem("Load directory...", loadIcon(Images.open));
        bLoadArbitrary.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        bLoadArbitrary.setOnAction(actionEvent1 -> openDirectory());
        openFolderDialog = new DirectoryChooser();

        MenuItem bLoadSnow = new MenuItem("Load snow project file...", loadIcon(Images.open));
        bLoadSnow.setOnAction(actionEvent1 -> loadSnow());
        openSnowFileDialog = new FileChooser();
        openSnowFileDialog.getExtensionFilters().add(new FileChooser.ExtensionFilter("Snow project files", "*.snow"));
        saveSnowFileDialog = new FileChooser();
        saveSnowFileDialog.getExtensionFilters().add(new FileChooser.ExtensionFilter("Snow project files", "*.snow"));

        MenuItem bLoadOutputFile = new MenuItem("Load output.xml file...", loadIcon(Images.xml));
        bLoadOutputFile.setOnAction(actionEvent1 -> loadOutput());
        openOutputFileDialog = new FileChooser();
        openOutputFileDialog.getExtensionFilters().add(new FileChooser.ExtensionFilter("output.xml files", "*.xml"));

        MenuItem bSaveAll = new MenuItem("Save all", loadIcon(Images.save));
        bSaveAll.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        bSaveAll.setOnAction(event3 -> saveAll());
        bSaveAll.disableProperty().bind(canSave.not());

        MenuItem bSaveAsSnow = new MenuItem("Export project configuration as a snow file...", loadIcon(Images.save));
        bSaveAsSnow.setOnAction(event3 -> saveAsSnow());

        MenuItem bSettings = new MenuItem("Settings", loadIcon(Images.keywordIcon));
        bSettings.setOnAction(event -> {
            SettingsWindow settingsWindow = new SettingsWindow(MainForm.this);
            settingsWindow.show();
        });
        MenuItem bReloadAll = new MenuItem("Reload everything", loadIcon(Images.refresh));
        bReloadAll.setOnAction(actionEvent -> reloadAll());
        MenuItem bReload = new MenuItem("Reload external libraries");
        bReload.setOnAction(event -> reloadExternalLibraries());

        MenuItem bExit = new MenuItem("Exit Snowride", loadIcon(Images.exit));
        bExit.setOnAction(event -> System.exit(0));
        separatorBeforeRecentProjects = new SeparatorMenuItem();
        separatorAfterRecentProjects = new SeparatorMenuItem();
        projectMenu.getItems().addAll(bLoadArbitrary, bLoadSnow, bLoadOutputFile, bSaveAll, bSaveAsSnow, separatorBeforeRecentProjects, separatorAfterRecentProjects, bSettings, bReloadAll, bReload, bExit);
        refreshRecentlyOpenMenu();

        MenuItem back = new MenuItem("Navigate back", loadIcon(Images.goLeft));
        back.disableProperty().bind(canNavigateBack.not());
        back.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN));
        back.setOnAction(event2 -> goBack());

        MenuItem forwards = new MenuItem("Navigate forwards", loadIcon(Images.goRight));
        forwards.setOnAction(event1 -> goForwards());
        forwards.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN));
        forwards.disableProperty().bind(canNavigateForwards.not());

        Menu navigateMenu = new Menu("Navigate", null, back, forwards);

        MenuItem run = new MenuItem("Run", loadIcon(Images.play));
        run.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        run.disableProperty().bind(runTab.canRun.not());
        run.textProperty().bind(runTab.runCaption);
        run.setOnAction(runTab::clickRun);
        MenuItem stop = new MenuItem("Stop", loadIcon(Images.stop));
        stop.disableProperty().bind(runTab.canStop.not());
        stop.setOnAction(runTab::clickStop);
        Menu runMenu = new Menu("Run", null, run, stop);

        MenuItem about = new MenuItem("About");
        about.setOnAction(event -> {
            AboutSnowride aboutSnowride = new AboutSnowride();
            aboutSnowride.show();
        });
        MenuItem shortcuts = new MenuItem("Keyboard shortcuts");
        shortcuts.setOnAction(event -> {
            AboutKeyboardShortcuts aboutSnowride = new AboutKeyboardShortcuts();
            aboutSnowride.show();
        });
        MenuItem robotFrameworkUserGuide = new MenuItem("Navigate to Robot Framework User Guide", loadIcon(Images.internet));
        robotFrameworkUserGuide.setOnAction(event -> navigateToWebsite("http://robotframework.org/robotframework/latest/RobotFrameworkUserGuide.html"));
        MenuItem robotFrameworkLibrariesDocumentation = new MenuItem("Navigate to Robot Framework libraries documentation", loadIcon(Images.internet));
        robotFrameworkLibrariesDocumentation.setOnAction(event -> navigateToWebsite("http://robotframework.org/robotframework/#user-guide"));
        MenuItem releaseNotes = new MenuItem("View Snowride changelog/release notes");
        releaseNotes.setOnAction(event -> {
            AboutChangelog aboutChangelog = new AboutChangelog();
            aboutChangelog.show();
        });
        MenuItem bSettings2 = new MenuItem("Settings", loadIcon(Images.keywordIcon)); // we have to create another menu item, because a menu item can have only one parent
        // And we need this in Tools, because that's what people are used to.
        bSettings2.setOnAction(event -> {
            SettingsWindow settingsWindow = new SettingsWindow(MainForm.this);
            settingsWindow.show();
        });
        CheckMenuItem darkThemeSwitch = new CheckMenuItem("Dark/Light switch");
        darkThemeSwitch.selectedProperty().addListener( (obs,wasSelected,isSelected)->{
            if(isSelected) {
                scene.getStylesheets().add("snow-dark.css");
            } else {
                scene.getStylesheets().remove("snow-dark.css");
            }
        });
        
        Menu toolsMenu = new Menu("Tools", null, darkThemeSwitch,bSettings2);
        Menu helpMenu = new Menu("Help", null, about, shortcuts, robotFrameworkUserGuide, robotFrameworkLibrariesDocumentation, releaseNotes);
        mainMenu.getMenus().addAll(projectMenu, navigateMenu, runMenu, toolsMenu, helpMenu);
        return mainMenu;
    }

    private void loadSnow() {
        if (getProjectTree().getRoot() != null && getRootDirectoryElement() != null) {
            openSnowFileDialog.setInitialDirectory(this.getRootDirectoryElement().directoryPath);
        }
        File loadFromWhere = openSnowFileDialog.showOpenDialog(this.stage);
        if (loadFromWhere != null) {
            loadProjectFromFolderOrSnowFile(loadFromWhere.getAbsoluteFile());
        }
    }

    private void loadOutput() {
        if (getProjectTree().getRoot() != null && getRootDirectoryElement() != null) {
            openOutputFileDialog.setInitialDirectory(this.getRootDirectoryElement().directoryPath);
        }
        File loadFromWhere = openOutputFileDialog.showOpenDialog(this.stage);
        if (loadFromWhere != null) {
            OutputParser.parseOutput(loadFromWhere.getAbsoluteFile());
        }
    }

    private void saveAsSnow() {
        File saveWhere = saveSnowFileDialog.showSaveDialog(this.stage);
        if (saveWhere != null) {
            Settings.getInstance().saveIntoSnow(saveWhere);
        }
    }

    private void navigateToWebsite(String uri) {
        try {
            Desktop.getDesktop().browse(URI.create(uri));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void openDirectory() {
        if (getProjectTree().getRoot() != null && getRootDirectoryElement() != null) {
            openFolderDialog.setInitialDirectory(this.getRootDirectoryElement().directoryPath);
        }
        File openWhat = openFolderDialog.showDialog(this.stage);
        if (openWhat != null) {
            loadProjectFromFolderOrSnowFile(openWhat);
        }
    }

    private void goBack() {
        if (navigationStack.canNavigateBack.getValue()) {
            selectProgrammatically(navigationStack.navigateBackwards());
        }
    }

    private void goForwards() {
        if (navigationStack.canNavigateForwards.getValue()) {
            selectProgrammatically(navigationStack.navigateForwards());
        }
    }

    public void selectProgrammatically(HighElement navigateTo) {
        if (navigateTo == null) {
            return;
        }
        TreeItem<HighElement> selectWhat = navigateTo.treeNode;
        humanInControl = false;
        expandUpTo(selectWhat);
        Platform.runLater(() -> {
            int index = projectTree.getRow(selectWhat);
            projectTree.getFocusModel().focus(index);
            projectTree.getSelectionModel().select(index);
            ProjectTreeViewSkin treeViewSkin = (ProjectTreeViewSkin) projectTree.getSkin();
            if (!treeViewSkin.isIndexVisible(index)) {
                projectTree.scrollTo(index);
            }
            humanInControl = true;
        });
    }

    private void expandUpTo(TreeItem<HighElement> expandUpTo) {
        if (expandUpTo.getParent() != null) {
            expandUpTo(expandUpTo.getParent());
        }
        expandUpTo.setExpanded(true);
    }

    public void loadProjectFromFolderOrSnowFile(File path) {
        SnowConstants.INSTANCE.setStageTitle("- " + path.getName());

        boolean abort = maybeOfferSaveDiscardOrCancel();
        if (abort) {
            return;
        }
        canSave.setValue(false);
        File robotDirectory = path;
        if (path.isFile() && path.getName().toLowerCase().endsWith(".snow")) {
            robotDirectory = loadSnowFileAndReturnRobotsDirectory(path);
        }
        File loadRobotsFrom = robotDirectory;
        projectLoad.progress.set(0);
        navigationStack.clear();
        SnowCodeAreaProvider.INSTANCE.clear();
        reloadElementIntoTabs(null);
        projectLoader.submit(() -> {
            try {
                FilesystemWatcher.getInstance().forgetEverything();
                File canonicalPath = loadRobotsFrom.getAbsoluteFile().getCanonicalFile();
                if (!canonicalPath.exists()) {
                    projectLoad.progress.set(1);
                    Platform.runLater(() -> new SnowAlert(Alert.AlertType.WARNING, "File or folder '" + canonicalPath + "' does not exist.", new ButtonType("Close")).showAndWait());
                    return;
                }
                FolderSuite folderSuite = gateParser.loadDirectory(canonicalPath, projectLoad, 0.8);

                List<File> missingAdditionalFiles = Settings.getInstance().getMissingAdditionalFoldersAsFiles();
                if (!missingAdditionalFiles.isEmpty()) {
                    Platform.runLater(() -> new SnowAlert(Alert.AlertType.WARNING, "Additional files '" + missingAdditionalFiles + "' were not found. Please remove them from settings.").show());
                }

                List<File> additionalFiles = Settings.getInstance().getAdditionalFoldersAsFiles();
                ExternalResourcesElement externalResources = gateParser.createExternalResourcesElement(additionalFiles, projectLoad, 0.2);
                UltimateRoot ultimateRoot = new UltimateRoot(folderSuite, externalResources);
                ultimateRoot.selfAndDescendantHighElements().forEachOrdered(he -> {
                    if (he instanceof Suite) {
                        ((Suite) he).analyzeSemantics();
                    }
                });

                ultimateRoot.sortTree();

                // Ensure immediate children of "UltimateRoot" are expanded after loading
                ultimateRoot.treeNode.getChildren().stream()
                        .filter(e -> !e.getValue().getShortName().equals("External resources"))
                        .forEach(e -> e.setExpanded(true));

                Platform.runLater(() -> {
                    projectLoad.progress.set(1);
                    navigationStack.clear();
                    SnowCodeAreaProvider.INSTANCE.clear();
                    reloadElementIntoTabs(null);
                    humanInControl = false;
                    projectTree.setRoot(ultimateRoot.treeNode);
                    tbSearchTests.requestFocus();
                    projectTree.getSelectionModel().select(0);
                    projectTree.getFocusModel().focus(0);
                    runTab.maybeRunNumberChanged();
                    humanInControl = true;

                    Settings.getInstance().lastOpenedProject = path.toString();
                    Settings.getInstance().addToRecentlyOpen(path.toString());
                    refreshRecentlyOpenMenu();
                    Settings.getInstance().saveAllSettings();
                    reloadExternalLibraries();
                });
            } catch (IOException e) {
                projectLoad.progress.set(1);
                throw new RuntimeException(e);
            } catch (Throwable t) {
                projectLoad.progress.set(1);
                t.printStackTrace();
            }
        });

    }

    private File loadSnowFileAndReturnRobotsDirectory(File snowFile) {
        SnowFile.loadSnowFile(snowFile);
        return new File(Settings.getInstance().lastOpenedProject);
    }

    /**
     * Opens a Save/Don't save/Cancel dialog.
     *
     * @return Returns true if the action that prompted this dialog should be aborted.
     */
    private boolean maybeOfferSaveDiscardOrCancel() {
        if (canSave.getValue()) {
            // dialog
            ButtonType save = new ButtonType("Save changes");
            ButtonType dontSave = new ButtonType("Don't save");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new SnowAlert(Alert.AlertType.CONFIRMATION, "You have unsaved changes.",
                    save,
                    dontSave,
                    cancel);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(Images.snowflake);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                ButtonType pressedButton = result.get();
                if (pressedButton == save) {
                    saveAll();
                    return false;
                } else if (pressedButton == dontSave) {
                    // proceed
                    return false;
                } else {
                    // abort
                    return true;
                }
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void refreshRecentlyOpenMenu() {
        int startAt = projectMenu.getItems().indexOf(separatorBeforeRecentProjects) + 1;
        int endAt = projectMenu.getItems().indexOf(separatorAfterRecentProjects);
        projectMenu.getItems().remove(startAt, endAt);
        List<MenuItem> newItems = new ArrayList<>();
        for (String whatShouldBeThere : Settings.getInstance().lastOpenedProjects) {
            MenuItem newItem = new MenuItem(whatShouldBeThere);
            newItem.setOnAction(event -> loadProjectFromFolderOrSnowFile(new File(whatShouldBeThere)));
            newItems.add(newItem);
        }
        projectMenu.getItems().addAll(startAt, newItems);
    }

    public void show() {
        stage.show();
    }

    public void selectProgrammaticallyAndRememberInHistory(HighElement enterWhere) {
        navigationStack.standardEnter(enterWhere);
        selectProgrammatically(enterWhere);
    }

    public Stage getStage() {
        return this.stage;
    }

    public TabPane getTabs() {
        return this.tabs;
    }

    public Optional<HighElement> findTestByFullyQualifiedName(String longname) {
        return getRootElement().selfAndDescendantHighElements().filter(he -> he.getQualifiedNameNormalized().equalsIgnoreCase(longname.replace('_', ' '))).findFirst();
    }

    public void toast(String toastMessage) {
        notificationShowingWhat = toastMessage;
        notificationPane.show(toastMessage);
        endTheToastExecutor.schedule(() -> endTheToast(toastMessage), 5, TimeUnit.SECONDS);
    }

    private void endTheToast(String toastMessage) {
        Platform.runLater(() -> {
            //noinspection StringEquality -- reference comparison on purpose
            if (this.notificationShowingWhat == toastMessage) {
                notificationPane.hide();
            }
        });
    }


    public void reloadExternalLibraries() {
        ReloadExternalLibraries.reload(this::reloadCurrentThing);
    }

    public void reloadCurrentThing() {
        humanInControl = false;
        // Reload current thing
        reloadElementIntoTabs(getFocusedElement(), false);
        humanInControl = true;
    }

    public FolderSuite getRootDirectoryElement() {
        return getRootElement().getRootDirectory();
    }

    public ExternalResourcesElement getExternalResourcesElement() {
        return getRootElement().getExternalResourcesElement();
    }
}
