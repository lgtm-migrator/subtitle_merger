package kirill.subtitlemerger.gui.application_specific.previews;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import kirill.subtitlemerger.gui.utils.entities.NoSelectionModel;
import kirill.subtitlemerger.gui.utils.background_tasks.BackgroundTask;
import kirill.subtitlemerger.gui.utils.custom_controls.MultiColorLabels;
import kirill.subtitlemerger.logic.LogicConstants;
import kirill.subtitlemerger.logic.core.SubtitleWriter;
import kirill.subtitlemerger.logic.core.entities.Subtitles;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class SimpleSubtitlePreviewController {
    @FXML
    private Pane mainPane;

    @FXML
    private Pane singleSubtitlesPane;

    @FXML
    private Label singleSubtitlesTitle;

    @FXML
    private Pane mergedSubtitlesPane;

    @FXML
    private Label mergedUpperTitle;

    @FXML
    private Label mergedLowerTitle;

    @FXML
    private MultiColorLabels resultLabels;

    @FXML
    private ListView<String> listView;

    @FXML
    private Pane progressPane;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private Label progressLabel;

    private Subtitles subtitles;

    private Stage dialogStage;

    public void initializeSingleSubtitles(
            Subtitles subtitles,
            String title,
            Stage dialogStage
    ) {
        this.subtitles = subtitles;
        this.dialogStage = dialogStage;

        singleSubtitlesPane.setVisible(true);
        singleSubtitlesPane.setManaged(true);
        singleSubtitlesTitle.setText(title);
        listView.setSelectionModel(new NoSelectionModel<>());

        processDataAndUpdateScene();
    }

    public void initializeMergedSubtitles(
            Subtitles subtitles,
            String upperTitle,
            String lowerTitle,
            Stage dialogStage
    ) {
        this.subtitles = subtitles;
        this.dialogStage = dialogStage;

        mergedSubtitlesPane.setVisible(true);
        mergedSubtitlesPane.setManaged(true);
        mergedUpperTitle.setText(upperTitle);
        mergedLowerTitle.setText(lowerTitle);
        listView.setSelectionModel(new NoSelectionModel<>());

        processDataAndUpdateScene();
    }

    private void processDataAndUpdateScene() {
        listView.getItems().clear();

        BackgroundTask<ProcessedData> task = new BackgroundTask<>() {
            @Override
            protected ProcessedData run() {
                return getProcessedData(subtitles);
            }

            @Override
            protected void onFinish(ProcessedData result) {
                progressPane.setVisible(false);
                mainPane.setDisable(false);

                updateScene(result);
            }
        };

        progressPane.setVisible(true);
        mainPane.setDisable(true);
        progressIndicator.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        task.start();
    }

    private static ProcessedData getProcessedData(Subtitles subtitles) {
        String subtitleText = SubtitleWriter.toSubRipText(subtitles);

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

        return new ProcessedData(subtitles, FXCollections.observableArrayList(lines), linesTruncated);
    }

    private void updateScene(ProcessedData processedData) {
        if (processedData.isLinesTruncated()) {
            resultLabels.setOnlyWarn("lines that are longer than 1000 symbols were truncated");
        } else {
            resultLabels.clear();
        }

        listView.setItems(processedData.getLinesToDisplay());
    }

    @FXML
    private void okButtonClicked() {
        dialogStage.close();
    }

    @AllArgsConstructor
    @Getter
    private static class ProcessedData {
        private Subtitles subtitles;

        private ObservableList<String> linesToDisplay;

        private boolean linesTruncated;
    }
}