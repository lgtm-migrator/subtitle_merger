package kirill.subtitlemerger.gui.application_specific.videos_tab.loaders_and_handlers;

import javafx.application.Platform;
import javafx.scene.control.ProgressBar;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.GuiFfmpegSubtitleStream;
import kirill.subtitlemerger.gui.application_specific.videos_tab.table_with_files.GuiFileInfo;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunnerManager;
import kirill.subtitlemerger.gui.utils.entities.ActionResult;
import kirill.subtitlemerger.logic.core.SubtitleParser;
import kirill.subtitlemerger.logic.work_with_files.entities.FfmpegSubtitleStream;
import kirill.subtitlemerger.logic.work_with_files.entities.FileInfo;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.Ffmpeg;
import kirill.subtitlemerger.logic.work_with_files.ffmpeg.FfmpegException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class LoadSingleSubtitleTask implements BackgroundRunner<LoadSingleSubtitleTask.Result> {
    private String streamId;

    private FileInfo fileInfo;

    private GuiFileInfo guiFileInfo;

    private Ffmpeg ffmpeg;

    @Override
    public Result run(BackgroundRunnerManager runnerManager) {
        runnerManager.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, ProgressBar.INDETERMINATE_PROGRESS);

        FfmpegSubtitleStream stream = FfmpegSubtitleStream.getById(streamId, fileInfo.getFfmpegSubtitleStreams());
        GuiFfmpegSubtitleStream guiStream = GuiSubtitleStream.getById(streamId, guiFileInfo.getFfmpegSubtitleStreams());
        runnerManager.updateMessage(
                LoadFilesAllSubtitlesTask.getUpdateMessage(
                        1,
                        0,
                        stream,
                        fileInfo.getFile()
                )
        );

        runnerManager.setCancellationPossible(true);
        try {
            String subtitleText = ffmpeg.getSubtitlesText(stream.getFfmpegIndex(), fileInfo.getFile());
            stream.setSubtitles(SubtitleParser.fromSubRipText(subtitleText, stream.getLanguage()));
            boolean haveSubtitlesToLoad = fileInfo.haveSubtitlesToLoad();

            Platform.runLater(() -> {
                guiStream.setSize(stream.getSubtitles().getSize());
                guiStream.setFailedToLoadReason(null);
                guiFileInfo.setHaveSubtitleSizesToLoad(haveSubtitlesToLoad);
            });

            return new Result(Status.SUCCESS);
        } catch (FfmpegException e) {
            if (e.getCode() == FfmpegException.Code.INTERRUPTED) {
                return new Result(Status.CANCELLED);
            } else {
                Platform.runLater(() -> guiStream.setFailedToLoadReason(BackgroundTaskUtils.guiTextFrom(e)));
                return new Result(Status.ERROR);
            }
        } catch (SubtitleParser.IncorrectFormatException e) {
            Platform.runLater(() -> guiStream.setFailedToLoadReason("subtitles seem to have an incorrect format"));
            return new Result(Status.ERROR);
        }
    }

    public static ActionResult generateMultiPartResult(Result taskResult) {
        String success = null;
        String warn = null;
        String error = null;

        switch (taskResult.getStatus()) {
            case SUCCESS:
                success = "Subtitles have been loaded successfully";
                break;
            case CANCELLED:
                warn = "Task has been cancelled";
                break;
            case ERROR:
                error = "Failed to load subtitles";
                break;
            default:
                throw new IllegalStateException();
        }

        return new ActionResult(success, warn, error);
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        private Status status;
    }

    public enum Status {
        SUCCESS,
        ERROR,
        CANCELLED
    }
}