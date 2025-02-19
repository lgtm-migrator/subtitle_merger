package kirill.subtitlemerger.gui.forms.videos.background;

import kirill.subtitlemerger.gui.forms.videos.table.TableSubtitleOption;
import kirill.subtitlemerger.gui.forms.videos.table.TableVideo;
import kirill.subtitlemerger.gui.utils.background.BackgroundManager;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.logic.ffmpeg.Ffmpeg;
import kirill.subtitlemerger.logic.settings.Settings;
import kirill.subtitlemerger.logic.subtitles.SubRipWriter;
import kirill.subtitlemerger.logic.subtitles.SubtitleMerger;
import kirill.subtitlemerger.logic.subtitles.entities.Subtitles;
import kirill.subtitlemerger.logic.videos.entities.BuiltInSubtitleOption;
import kirill.subtitlemerger.logic.videos.entities.SubtitleOption;
import kirill.subtitlemerger.logic.videos.entities.Video;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static kirill.subtitlemerger.gui.forms.videos.background.VideosBackgroundUtils.getLoadSubtitlesError;
import static kirill.subtitlemerger.gui.forms.videos.background.VideosBackgroundUtils.getLoadingCancelDescription;

@AllArgsConstructor
public class MergedPreviewRunner implements BackgroundRunner<MergedPreviewRunner.Result> {
    private Video video;

    private TableVideo tableVideo;

    private Ffmpeg ffmpeg;

    private Settings settings;

    public Result run(BackgroundManager backgroundManager) {
        backgroundManager.setCancelPossible(true);
        backgroundManager.setIndeterminateProgress();

        SubtitleOption upperOption = video.getOption(tableVideo.getUpperOption().getId());
        SubtitleOption lowerOption = video.getOption(tableVideo.getLowerOption().getId());

        try {
            String loadError = loadSubtitles(video, tableVideo, ffmpeg, backgroundManager);
            if (!StringUtils.isBlank(loadError)) {
                return new Result(loadError, null);
            }

            backgroundManager.updateMessage("Preview: merging the subtitles...");
            Subtitles merged = SubtitleMerger.mergeSubtitles(upperOption.getSubtitles(), lowerOption.getSubtitles());

            return new Result(null, SubRipWriter.toText(merged, settings.isPlainTextSubtitles()));
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Nullable
    private static String loadSubtitles(
            Video video,
            TableVideo tableVideo,
            Ffmpeg ffmpeg,
            BackgroundManager backgroundManager
    ) throws InterruptedException {
        List<BuiltInSubtitleOption> optionsToLoad = getOptionsToLoad(video, tableVideo);
        if (CollectionUtils.isEmpty(optionsToLoad)) {
            return null;
        }

        backgroundManager.setCancelDescription(getLoadingCancelDescription(video));

        int toLoadCount = optionsToLoad.size();
        int incorrectCount = 0;
        int failedCount = 0;
        for (BuiltInSubtitleOption option : optionsToLoad) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            TableSubtitleOption tableOption = tableVideo.getOption(option.getId());

            String action = "Preview: loading " + tableOption.getTitle() + " in " + video.getFile().getName() + "...";
            backgroundManager.updateMessage(action);

            LoadSubtitlesResult loadResult = VideosBackgroundUtils.loadSubtitles(option, video, tableOption, ffmpeg);
            if (loadResult == LoadSubtitlesResult.INCORRECT_FORMAT) {
                incorrectCount++;
            } else if (loadResult == LoadSubtitlesResult.FAILED) {
                failedCount++;
            }
        }

        backgroundManager.setCancelDescription(null);

        if (failedCount != 0 || incorrectCount != 0) {
            String error = getLoadSubtitlesError(toLoadCount, failedCount, incorrectCount);
            return "Previewing is not possible: " + StringUtils.uncapitalize(error);
        } else {
            return null;
        }
    }

    private static List<BuiltInSubtitleOption> getOptionsToLoad(Video video, TableVideo tableVideo) {
        List<BuiltInSubtitleOption> result = new ArrayList<>();

        SubtitleOption upperOption = video.getOption(tableVideo.getUpperOption().getId());
        if (upperOption instanceof BuiltInSubtitleOption && upperOption.getSubtitlesAndInput() == null) {
            result.add((BuiltInSubtitleOption) upperOption);
        }

        SubtitleOption lowerOption = video.getOption(tableVideo.getLowerOption().getId());
        if (lowerOption instanceof BuiltInSubtitleOption && lowerOption.getSubtitlesAndInput() == null) {
            result.add((BuiltInSubtitleOption) lowerOption);
        }

        return result;
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        @Nullable
        private String error;

        @Nullable
        private String subtitleText;
    }
 }
