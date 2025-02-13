package cz.hudecekpetr.snowride.ui.settings;

import cz.hudecekpetr.snowride.fx.CenterToParentUtility;
import cz.hudecekpetr.snowride.settings.ReloadOnChangeStrategy;
import cz.hudecekpetr.snowride.settings.Settings;
import cz.hudecekpetr.snowride.ui.Images;
import cz.hudecekpetr.snowride.ui.MainForm;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

public class SettingsWindow extends Stage {

    private TitledTextArea additionalXmlFilesBox;
    private MainForm mainForm;
    private CheckBox cbAlsoImportTxtFiles;
    private CheckBox cbDeselectAll;
    private CheckBox cbReloadAll;
    private CheckBox cbDisableOutputParsing;
    private CheckBox cbDisableOutputParsingWarning;
    private CheckBox cbFirstCompletionOption;
    private CheckBox cbAutoExpandSelectedTests;
    private CheckBox cbUseStructureChanged;
    private TextField tbNumber;
    private CheckBox cbGarbageCollect;
    private CheckBox cbHighlightSameCells;
    private CheckBox cbUseSystemColorWindow;
    private CheckBox cbAutocompleteVariables;
    private TextField tbNumber2;
    private TextField tbProjectsHistorySizeField;
    private ComboBox<ReloadOnChangeStrategy> cbReloadStrategy;

    public SettingsWindow(MainForm mainForm) {
        this.mainForm = mainForm;
        TabPane tabs = createAllTabs();
        Button buttonSuperOK = new Button("Apply, close, and reload project", new ImageView(Images.refresh));
        buttonSuperOK.setOnAction(this::applyCloseAndRefresh);
        Button buttonOK = new Button("Apply and close");
        buttonOK.setOnAction(this::applyAndClose);
        Button buttonCancel = new Button("Cancel");
        buttonCancel.setOnAction(event -> SettingsWindow.this.close());
        HBox buttonRow = new HBox(5, buttonSuperOK, buttonOK, buttonCancel);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        VBox all = new VBox(5, tabs, buttonRow);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        all.setPadding(new Insets(8));
        this.setScene(new Scene(all, 700, 600));
        this.getIcons().add(Images.keywordIcon);
        this.setTitle("Settings");
        CenterToParentUtility.prepareToShowAtCenterOfMainForm(this);
    }

    private void applyCloseAndRefresh(ActionEvent actionEvent) {
        applyAndClose(actionEvent);
        mainForm.reloadAll();
    }

    private TabPane createAllTabs() {
        additionalXmlFilesBox = new TitledTextArea("Folders for XML, Python and Java files", Settings.getInstance().additionalFolders);
        Label folderDescription = new Label("Each line is an absolute path to a folder. Snowride will add these folders to the runner script's pythonpath, and it will browse these folders for XML files, Python files, and Robot Framework files in order to get documentation.");
        folderDescription.setWrapText(true);
        cbAlsoImportTxtFiles = new CheckBox("Parse .txt files in addition to .robot files.");
        cbAlsoImportTxtFiles.setSelected(Settings.getInstance().cbAlsoImportTxtFiles);
        cbReloadAll = new CheckBox("Show 'Reload all' button in the toolbar.");
        cbReloadAll.setSelected(Settings.getInstance().toolbarReloadAll);
        cbDeselectAll = new CheckBox("Show 'Deselect all' button in the toolbar.");
        cbDeselectAll.setSelected(Settings.getInstance().toolbarDeselectEverything);
        cbDisableOutputParsing = new CheckBox("Disable output.xml parsing.");
        cbDisableOutputParsing.setWrapText(true);
        cbDisableOutputParsing.setSelected(Settings.getInstance().disableOutputParsing);
        cbDisableOutputParsingWarning = new CheckBox("Disable Warning popup (old Robot version) during output.xml parsing.");
        cbDisableOutputParsingWarning.setWrapText(true);
        cbDisableOutputParsingWarning.setSelected(Settings.getInstance().disableOutputParsingWarning);
        cbFirstCompletionOption = new CheckBox("If you type a nonexistent keyword, confirm it with Enter instead of choosing the first completion option.");
        cbFirstCompletionOption.setWrapText(true);
        cbFirstCompletionOption.setSelected(Settings.getInstance().cbShowNonexistentOptionFirst);
        cbAutoExpandSelectedTests = new CheckBox("When you 'select all tests' or 'select failed tests', also expand them all.");
        cbAutoExpandSelectedTests.setWrapText(true);
        cbAutoExpandSelectedTests.setSelected(Settings.getInstance().cbAutoExpandSelectedTests);
        cbUseStructureChanged = new CheckBox("Show [structure changed] or [text changed] instead of an asterisk (*) for changed files.");
        cbUseStructureChanged.setWrapText(true);
        cbUseStructureChanged.setSelected(Settings.getInstance().cbUseStructureChanged);
        Label lblNumber = new Label("In 'until failure' mode, how many successes should end testing even if there's no failure: ");
        tbNumber = new TextField(Integer.toString(Settings.getInstance().numberOfSuccessesBeforeEnd));
        //https://stackoverflow.com/a/36436243/1580088
        tbNumber.setTextFormatter(new TextFormatter<String>(change -> {
            String text = change.getText();
            if (text.matches("[0-9]*")) {
                return change;
            }
            return null;
        }));
        HBox num = new HBox(5, lblNumber, tbNumber);
        num.setAlignment(Pos.CENTER_LEFT);
        cbGarbageCollect = new CheckBox("Automatically garbage collect every 5 minutes. Changes to this will take effect when you next start Snowride. Additional action from your side required!: Add the following VM options to your launcher to force JVM to return freed memory back to the operating system.");
        cbGarbageCollect.setSelected(Settings.getInstance().cbRunGarbageCollection);
        cbGarbageCollect.setWrapText(true);
        TextField tbXXargs = new TextField("-XX:+UseG1GC -XX:MaxHeapFreeRatio=30 -XX:MinHeapFreeRatio=10");
        tbXXargs.setEditable(false);
        VBox borderBox = new VBox(5, cbGarbageCollect, tbXXargs);
        borderBox.setStyle("-fx-border-color: black; -fx-border-width: 1px; -fx-padding: 3px; ");

        cbHighlightSameCells = new CheckBox("Highlight cells with the same content as the selected cell in yellow.");
        cbHighlightSameCells.setWrapText(true);
        cbHighlightSameCells.setSelected(Settings.getInstance().cbHighlightSameCells);

        cbUseSystemColorWindow = new CheckBox("Use system color 'window' for background of text boxes instead of the default off-white color (requires a Snowride restart).");
        cbUseSystemColorWindow.setWrapText(true);
        cbUseSystemColorWindow.setSelected(Settings.getInstance().cbUseSystemColorWindow);

        // tree size
        Label lblNumber2 = new Label("Size of tree view font (in points): ");
        tbNumber2 = new TextField(Integer.toString(Settings.getInstance().treeSizeItemHeight));
        tbNumber2.setTextFormatter(new TextFormatter<String>(change -> {
            String text = change.getText();
            if (text.matches("[0-9]*")) {
                return change;
            }
            return null;
        }));
        tbNumber2.textProperty().addListener((ChangeListener<String>)this::treeSizeChanged);
        HBox tbTreeSizeItemHeightFieldBox = new HBox(5, lblNumber2, tbNumber2);
        tbTreeSizeItemHeightFieldBox.setAlignment(Pos.CENTER_LEFT);

        // projects history
        Label lblProjectsHistory = new Label("Size of recet projets section in main menu: ");
        tbProjectsHistorySizeField = new TextField(Integer.toString(Settings.getInstance().lastOpeneProjectsMaxSize));
        tbProjectsHistorySizeField.setTextFormatter(new TextFormatter<String>(change -> {
            String text = change.getText();
            if (text.matches("[0-9]*")) {
                return change;
            }
            return null;
        }));
        tbProjectsHistorySizeField.textProperty()
                .addListener((ChangeListener<String>) this::projectsHistorySizeChanged);
        HBox projectsHistoryFieldBox = new HBox(5, lblProjectsHistory, tbProjectsHistorySizeField);
        projectsHistoryFieldBox.setAlignment(Pos.CENTER_LEFT);

        // autocomplete
        cbAutocompleteVariables = new CheckBox("Offer autocompletion for variables");
        cbAutocompleteVariables.setWrapText(true);
        cbAutocompleteVariables.setSelected(Settings.getInstance().cbAutocompleteVariables);

        Label lblReloadStrategy = new Label("When another application updates a file:");
        ObservableList<ReloadOnChangeStrategy> options = FXCollections.observableArrayList(ReloadOnChangeStrategy.POPUP_DIALOG, ReloadOnChangeStrategy.DO_NOTHING, ReloadOnChangeStrategy.RELOAD_AUTOMATICALLY);
        cbReloadStrategy = new ComboBox<>(options);
        cbReloadStrategy.getSelectionModel().select(Settings.getInstance().reloadOnChangeStrategy);
        HBox hboxReloadStrategy = new HBox(5, lblReloadStrategy, cbReloadStrategy);
        hboxReloadStrategy.setAlignment(Pos.CENTER_LEFT);

        VBox vboxEditor = new VBox(5, additionalXmlFilesBox, folderDescription, cbAlsoImportTxtFiles,
              cbAutocompleteVariables, hboxReloadStrategy);
        vboxEditor.setPadding(new Insets(5, 0, 0, 0));
        VBox vboxAppearance = new VBox(5,
                cbReloadAll, cbDeselectAll, cbHighlightSameCells, cbUseSystemColorWindow, tbTreeSizeItemHeightFieldBox,
                projectsHistoryFieldBox);
        vboxAppearance.setPadding(new Insets(5, 0, 0, 0));
        VBox vboxBehavior = new VBox(5, cbDisableOutputParsing, cbDisableOutputParsingWarning, cbFirstCompletionOption, cbAutoExpandSelectedTests, cbUseStructureChanged);
        vboxBehavior.setPadding(new Insets(5, 0, 0, 0));
        VBox vboxAdvanced = new VBox(5, borderBox);
        vboxAdvanced.setPadding(new Insets(5, 0, 0, 0));


        Tab tabEditor = new Tab("Editor", vboxEditor);
        tabEditor.setClosable(false);
        Tab tabAppearance = new Tab("Appearance", vboxAppearance);
        tabAppearance.setClosable(false);
        Tab tabBehaviour = new Tab("Behaviour", vboxBehavior);
        tabBehaviour.setClosable(false);
        Tab tabAdvanced = new Tab("Advanced", vboxAdvanced);
        tabAdvanced.setClosable(false);

        TabPane tabPane = new TabPane(tabEditor, tabAppearance, tabBehaviour, tabAdvanced);
        return tabPane;
    }

    private void treeSizeChanged(ObservableValue<? extends String> observableValue, String old, String newValue) {
        if (StringUtils.isBlank(newValue)) {
            return;
        }
        try {
            int asInt = Integer.parseInt(newValue);
            mainForm.getProjectTree().setStyle("-fx-font-size: " + asInt + "pt;");
            Settings.getInstance().treeSizeItemHeight = asInt;
        } catch (Exception e) {
            throw new RuntimeException(newValue + " is not a number.");
        }
    }

    private void projectsHistorySizeChanged(ObservableValue<? extends String> observableValue, String old,
            String newValue) {
        if (StringUtils.isBlank(newValue)) {
            return;
        }
        try {
            int asInt = Integer.parseInt(newValue);
            Settings.getInstance().lastOpeneProjectsMaxSize = asInt;
        } catch (Exception e) {
            throw new RuntimeException(newValue + " is not a number.");
        }
    }

    private void applyAndClose(ActionEvent actionEvent) {
        Settings.getInstance().additionalFolders = additionalXmlFilesBox.getText();
        Settings.getInstance().cbAlsoImportTxtFiles = cbAlsoImportTxtFiles.isSelected();
        Settings.getInstance().toolbarDeselectEverything = cbDeselectAll.isSelected();
        Settings.getInstance().disableOutputParsing = cbDisableOutputParsing.isSelected();
        Settings.getInstance().disableOutputParsingWarning = cbDisableOutputParsingWarning.isSelected();
        Settings.getInstance().cbShowNonexistentOptionFirst = cbFirstCompletionOption.isSelected();
        Settings.getInstance().toolbarReloadAll = cbReloadAll.isSelected();
        Settings.getInstance().cbAutoExpandSelectedTests = cbAutoExpandSelectedTests.isSelected();
        Settings.getInstance().cbRunGarbageCollection = cbGarbageCollect.isSelected();
        Settings.getInstance().cbHighlightSameCells = cbHighlightSameCells.isSelected();
        Settings.getInstance().cbUseStructureChanged = cbUseStructureChanged.isSelected();
        Settings.getInstance().cbUseSystemColorWindow = cbUseSystemColorWindow.isSelected();
        Settings.getInstance().cbAutocompleteVariables = cbAutocompleteVariables.isSelected();
        Settings.getInstance().reloadOnChangeStrategy = cbReloadStrategy.getValue();
        try {
            Settings.getInstance().numberOfSuccessesBeforeEnd = Integer.parseInt(tbNumber.getText());
            mainForm.runTab.numberOfSuccessesToStop.setValue(Settings.getInstance().numberOfSuccessesBeforeEnd);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        Settings.getInstance().saveAllSettings();
        mainForm.updateAdditionalToolbarButtonsVisibility();
        mainForm.reloadExternalLibraries();
        this.close();
    }
}
