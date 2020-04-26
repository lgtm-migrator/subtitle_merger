package kirill.subtitlemerger.gui.forms.common.subtitle_preview;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import kirill.subtitlemerger.gui.common_controls.ActionResultPane;
import kirill.subtitlemerger.gui.forms.common.BackgroundTaskFormController;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.background.BackgroundCallback;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.logic.LogicConstants;
import kirill.subtitlemerger.logic.core.SubRipParser;
import kirill.subtitlemerger.logic.core.SubRipWriter;
import kirill.subtitlemerger.logic.core.entities.SubtitleFormatException;
import kirill.subtitlemerger.logic.core.entities.Subtitles;
import kirill.subtitlemerger.logic.utils.Utils;
import kirill.subtitlemerger.logic.utils.entities.ActionResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SubtitlePreviewFormController extends BackgroundTaskFormController {
    private static final CharsetStringConverter CHARSET_STRING_CONVERTER = new CharsetStringConverter();

    private BooleanProperty linesTruncated;

    @FXML
    private Label title;

    @FXML
    private Pane mergedUpperPane;

    @FXML
    private Label mergedUpperTitle;

    @FXML
    private Pane mergedLowerPane;

    @FXML
    private Label mergedLowerTitle;

    @FXML
    private Pane encodingPane;

    @FXML
    private ComboBox<Charset> encodingComboBox;

    @FXML
    private ActionResultPane actionResultPane;

    @FXML
    private ListView<String> listView;

    @FXML
    private Pane okPane;

    @FXML
    private Pane cancelSavePane;

    @FXML
    private Button saveButton;

    private Mode mode;

    private byte[] data;

    private Charset originalEncoding;

    private Charset currentEncoding;

    private Subtitles originalSubtitles;

    private Subtitles currentSubtitles;

    private boolean plainText;

    private Stage dialogStage;

    private SubtitlePreviewResult result;

    public SubtitlePreviewFormController() {
        linesTruncated = new SimpleBooleanProperty(false);
    }

    public boolean isLinesTruncated() {
        return linesTruncated.get();
    }

    public BooleanProperty linesTruncatedProperty() {
        return linesTruncated;
    }

    public void setLinesTruncated(boolean linesTruncated) {
        this.linesTruncated.set(linesTruncated);
    }

    public void initializeSimple(Subtitles subtitles, String title, Stage dialogStage) {
        mode = Mode.SIMPLE;
        originalSubtitles = subtitles;
        currentSubtitles = subtitles;
        this.dialogStage = dialogStage;

        this.title.setText(getShortenedTitle(title));
        GuiUtils.setVisibleAndManaged(mergedUpperPane, false);
        GuiUtils.setVisibleAndManaged(mergedLowerPane, false);
        GuiUtils.setVisibleAndManaged(encodingPane, false);
        listView.setSelectionModel(new NoSelectionModel<>());
        GuiUtils.setVisibleAndManaged(cancelSavePane, false);

        getPreviewInfoAndUpdateScene(true);
    }

    private static String getShortenedTitle(String title) {
        return Utils.getShortenedString(title, 0, 128);
    }

    private void getPreviewInfoAndUpdateScene(boolean initialRun) {
        listView.getItems().clear();

        BackgroundRunner<PreviewInfo> backgroundRunner = backgroundManager -> {
            if (mode == Mode.SIMPLE) {
                return getPreviewInfo(originalSubtitles, null, false);
            } else if (mode == Mode.WITH_ENCODING){
                return getPreviewInfo(data, currentEncoding, false);
            } else if (mode == Mode.MERGED) {
                return getPreviewInfo(originalSubtitles, null, plainText);
            } else {
                throw new IllegalStateException();
            }
        };

        BackgroundCallback<PreviewInfo> callback = previewInfo -> {
            if (mode == Mode.WITH_ENCODING) {
                if (initialRun) {
                    originalSubtitles = previewInfo.getSubtitles();
                    result = new SubtitlePreviewResult(originalSubtitles, originalEncoding);
                }
                currentSubtitles = previewInfo.getSubtitles();
            }

            updateScene(previewInfo, initialRun);
        };

        runInBackground(backgroundRunner, callback);
    }

    private static PreviewInfo getPreviewInfo(byte[] data, Charset encoding, boolean plainTextSubtitles) {
        String text = new String(data, encoding);
        Subtitles subtitles;
        try {
            subtitles = SubRipParser.from(text);
        } catch (SubtitleFormatException e) {
            subtitles = null;
        }

        return getPreviewInfo(subtitles, text, plainTextSubtitles);
    }

    private static PreviewInfo getPreviewInfo(Subtitles subtitles, String subtitleText, boolean plainTextSubtitles) {
        if (subtitleText == null) {
            subtitleText = SubRipWriter.toText(subtitles, plainTextSubtitles);
        }

        List<String> lines = new ArrayList<>();
        boolean linesTruncated = false;
        for (String line : LogicConstants.LINE_SEPARATOR_PATTERN.split(subtitleText)) {
            if (line.length() > 1000) {
                lines.add(line.substring(0, 1000));
                linesTruncated = true;
            } else {
                lines.add(line);
            }
        }

        return new PreviewInfo(subtitles, FXCollections.observableArrayList(lines), linesTruncated);
    }

    private void updateScene(PreviewInfo previewInfo, boolean initialRun) {
        setLinesTruncated(previewInfo.isLinesTruncated());

        listView.setDisable(previewInfo.getSubtitles() == null);
        listView.setItems(previewInfo.getLinesToDisplay());

        if (mode == Mode.WITH_ENCODING) {
            ActionResult actionResult = ActionResult.NO_RESULT;
            if (previewInfo.getSubtitles() == null) {
                actionResult = ActionResult.onlyError(
                        String.format(
                                "This encoding (%s) doesn't fit or the file has an incorrect format",
                                currentEncoding.name()
                        )
                );
            } else {
                if (!initialRun) {
                    if (Objects.equals(currentEncoding, originalEncoding)) {
                        actionResult = ActionResult.onlySuccess(
                                "Encoding has been restored to the original value successfully"
                        );
                    } else {
                        actionResult = ActionResult.onlySuccess("Encoding has been changed successfully");
                    }
                }
            }
            actionResultPane.set(actionResult);

            saveButton.setDisable(Objects.equals(currentEncoding, originalEncoding) || currentSubtitles == null);
        }
    }

    public void initializeMerged(
            Subtitles subtitles,
            String upperTitle,
            String lowerTitle,
            boolean plainText,
            Stage dialogStage
    ) {
        mode = Mode.MERGED;
        originalSubtitles = subtitles;
        currentSubtitles = subtitles;
        this.plainText = plainText;
        this.dialogStage = dialogStage;

        title.setText("This is the result of merging");
        mergedUpperTitle.setText(upperTitle);
        mergedLowerTitle.setText(lowerTitle);
        GuiUtils.setVisibleAndManaged(encodingPane, false);
        listView.setSelectionModel(new NoSelectionModel<>());
        GuiUtils.setVisibleAndManaged(cancelSavePane, false);

        getPreviewInfoAndUpdateScene(true);
    }

    public void initializeWithEncoding(byte[] data, Charset originalEncoding, String fileFullPath, Stage dialogStage) {
        mode = Mode.WITH_ENCODING;
        this.data = data;
        this.originalEncoding = originalEncoding;
        currentEncoding = originalEncoding;
        this.dialogStage = dialogStage;

        title.setText(getShortenedTitle(fileFullPath));
        GuiUtils.setVisibleAndManaged(mergedUpperPane, false);
        GuiUtils.setVisibleAndManaged(mergedLowerPane, false);
        encodingComboBox.setConverter(CHARSET_STRING_CONVERTER);
        encodingComboBox.getItems().setAll(LogicConstants.SUPPORTED_ENCODINGS);
        encodingComboBox.getSelectionModel().select(originalEncoding);
        listView.setSelectionModel(new NoSelectionModel<>());
        GuiUtils.setVisibleAndManaged(okPane, false);

        getPreviewInfoAndUpdateScene(true);
    }

    public SubtitlePreviewResult getResult() {
        if (mode != Mode.WITH_ENCODING) {
            throw new IllegalStateException();
        }

        return result;
    }

    @FXML
    private void encodingChanged() {
        Charset encoding = encodingComboBox.getSelectionModel().getSelectedItem();

        if (Objects.equals(encoding, currentEncoding)) {
            return;
        }

        currentEncoding = encoding;
        getPreviewInfoAndUpdateScene(false);
    }

    @FXML
    private void okButtonClicked() {
        dialogStage.close();
    }

    @FXML
    private void cancelButtonClicked() {
        result = new SubtitlePreviewResult(originalSubtitles, originalEncoding);
        dialogStage.close();
    }

    @FXML
    private void saveButtonClicked() {
        result = new SubtitlePreviewResult(currentSubtitles, currentEncoding);
        dialogStage.close();
    }

    private static class CharsetStringConverter extends StringConverter<Charset> {
        @Override
        public String toString(Charset charset) {
            return charset.name();
        }

        @Override
        public Charset fromString(String name) {
            return Charset.forName(name);
        }
    }

    private enum Mode {
        SIMPLE,
        WITH_ENCODING,
        MERGED
    }

    @AllArgsConstructor
    @Getter
    private static class PreviewInfo {
        private Subtitles subtitles;

        private ObservableList<String> linesToDisplay;

        private boolean linesTruncated;
    }

    /*
     * A special class created so that when the user clicks on the list view there are no errors. It does nothing and
     * that's the whole point.
     */
    private static class NoSelectionModel<T> extends MultipleSelectionModel<T> {
        @Override
        public ObservableList<Integer> getSelectedIndices() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public ObservableList<T> getSelectedItems() {
            return FXCollections.emptyObservableList();
        }

        @Override
        public void selectIndices(int index, int... indices) {}

        @Override
        public void selectAll() {}

        @Override
        public void selectFirst() {}

        @Override
        public void selectLast() {}

        @Override
        public void clearAndSelect(int index) {}

        @Override
        public void select(int index) {}

        @Override
        public void select(T obj) {}

        @Override
        public void clearSelection(int index) {}

        @Override
        public void clearSelection() {}

        @Override
        public boolean isSelected(int index) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void selectPrevious() {}

        @Override
        public void selectNext() {}
    }
}