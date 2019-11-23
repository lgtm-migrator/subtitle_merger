package kirill.subtitlesmerger.gui;

import javafx.event.ActionEvent;
import kirill.subtitlesmerger.logic.data.Config;
import kirill.subtitlesmerger.logic.ffmpeg.Ffprobe;

import java.io.File;

public class SettingsTabController {
    private SettingsTab tab;

    private Config config;

    SettingsTabController(SettingsTab tab, Config config) {
        this.tab = tab;
        this.config = config;
    }

    void initialize() {
        updateFileChoosersAndFields();
        tab.getFfprobeSetButton().setOnAction(this::ffprobeFileButtonClicked);
        tab.getFfmpegSetButton().setOnAction(this::ffmpegFileButtonClicked);
    }

    private void updateFileChoosersAndFields() {
        File ffprobeFile = config.getFfprobeFile();
        if (ffprobeFile != null) {
            tab.getFfprobeField().setText(ffprobeFile.getAbsolutePath());

            tab.getFfprobeSetButton().setText("update path to ffprobe");

            tab.getFfprobeFileChooser().setInitialDirectory(ffprobeFile.getParentFile());
            tab.getFfprobeFileChooser().setTitle("update path to ffprobe");
        } else {
            tab.getFfprobeSetButton().setText("choose path to ffprobe");

            tab.getFfprobeFileChooser().setTitle("choose path to ffprobe");
        }

        File ffmpegFile = config.getFfmpegFile();
        if (ffmpegFile != null) {
            tab.getFfmpegField().setText(ffmpegFile.getAbsolutePath());

            tab.getFfmpegSetButton().setText("update path to ffmpeg");

            tab.getFfmpegFileChooser().setInitialDirectory(ffmpegFile.getParentFile());
            tab.getFfmpegFileChooser().setTitle("update path to ffmpeg");
        } else {
            tab.getFfmpegSetButton().setText("update path to ffmpeg");

            tab.getFfmpegFileChooser().setTitle("choose path to ffmpeg");
        }
    }

    private void ffprobeFileButtonClicked(ActionEvent event) {
        File ffprobeFile = tab.getFfprobeFileChooser().showOpenDialog(tab.getStage());
        if (ffprobeFile == null) {
            return;
        }

        try {
            config.saveFfprobeFile(ffprobeFile.getAbsolutePath());
            updateFileChoosersAndFields();

        } catch (Config.ConfigException e) {
            e.printStackTrace();
        }
    }
}