package kirill.subtitlemerger.gui.tabs.videos.background;

import com.neovisionaries.i18n.LanguageAlpha3Code;
import javafx.application.Platform;
import kirill.subtitlemerger.gui.GuiContext;
import kirill.subtitlemerger.logic.settings.MergeMode;
import kirill.subtitlemerger.logic.settings.Settings;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableFileInfo;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableSubtitleOption;
import kirill.subtitlemerger.gui.tabs.videos.table_with_files.TableWithFiles;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.background.BackgroundResult;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunnerManager;
import kirill.subtitlemerger.logic.core.SubRipParser;
import kirill.subtitlemerger.logic.core.SubtitleMerger;
import kirill.subtitlemerger.logic.core.entities.SubtitleFormatException;
import kirill.subtitlemerger.logic.core.entities.Subtitles;
import kirill.subtitlemerger.logic.ffmpeg.FfmpegException;
import kirill.subtitlemerger.logic.files.entities.FfmpegSubtitleStream;
import kirill.subtitlemerger.logic.files.entities.FileInfo;
import kirill.subtitlemerger.logic.files.entities.FileWithSubtitles;
import kirill.subtitlemerger.logic.files.entities.SubtitleOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@CommonsLog
@AllArgsConstructor
public class MergePreparationRunner implements BackgroundRunner<MergePreparationRunner.Result> {
    private List<TableFileInfo> displayedTableFilesInfo;

    private List<FileInfo> filesInfo;

    private TableWithFiles tableWithFiles;

    private GuiContext context;

    @Override
    public MergePreparationRunner.Result run(BackgroundRunnerManager runnerManager) {
        VideoTabBackgroundUtils.clearActionResults(displayedTableFilesInfo, tableWithFiles, runnerManager);

        List<TableFileInfo> selectedTableFilesInfo = VideoTabBackgroundUtils.getSelectedFilesInfo(
                displayedTableFilesInfo,
                runnerManager
        );

        int filesWithoutSelectionCount = getFilesWithoutSelectionCount(selectedTableFilesInfo, runnerManager);
        if (filesWithoutSelectionCount != 0) {
            return new Result(
                    false,
                    filesWithoutSelectionCount,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        runnerManager.setIndeterminateProgress();
        runnerManager.setCancellationPossible(true);

        List<FileMergeInfo> filesMergeInfo = new ArrayList<>();

        int processedCount = 0;
        for (TableFileInfo tableFileInfo : selectedTableFilesInfo) {
            try {
                filesMergeInfo.add(
                        getFileMergeInfo(tableFileInfo, processedCount, selectedTableFilesInfo.size(), runnerManager)
                );
                processedCount++;
            } catch (InterruptedException e) {
                return new Result(
                        true,
                        filesWithoutSelectionCount,
                        null,
                        null,
                        null,
                        null,
                        filesMergeInfo
                );
            }
        }

        RequiredAndAvailableSpace requiredAndAvailableSpace = getRequiredAndAvailableTempSpace(
                filesMergeInfo,
                filesInfo,
                context.getSettings(),
                runnerManager
        ).orElse(null);

        return new Result(
                false,
                filesWithoutSelectionCount,
                requiredAndAvailableSpace != null ? requiredAndAvailableSpace.getRequiredSpace() : null,
                requiredAndAvailableSpace != null ? requiredAndAvailableSpace.getAvailableSpace() : null,
                requiredAndAvailableSpace != null ? requiredAndAvailableSpace.getDirectoryForTempFile() : null,
                getFilesToOverwrite(filesMergeInfo, context.getSettings(), runnerManager),
                filesMergeInfo
        );
    }

    private static int getFilesWithoutSelectionCount(
            List<TableFileInfo> filesInfo,
            BackgroundRunnerManager runnerManager
    ) {
        runnerManager.setIndeterminateProgress();
        runnerManager.updateMessage("Getting file availability info...");

        return (int) filesInfo.stream()
                .filter(fileInfo -> fileInfo.getUpperOption() == null || fileInfo.getLowerOption() == null)
                .count();
    }

    private FileMergeInfo getFileMergeInfo(
            TableFileInfo tableFileInfo,
            int processedCount,
            int allCount,
            BackgroundRunnerManager runnerManager
    ) throws InterruptedException {
        String progressMessagePrefix = getProgressMessagePrefix(processedCount, allCount, tableFileInfo);
        runnerManager.updateMessage(progressMessagePrefix + "...");

        FileInfo fileInfo = FileInfo.getById(tableFileInfo.getId(), filesInfo);

        TableSubtitleOption tableUpperOption = tableFileInfo.getUpperOption();
        TableSubtitleOption tableLowerOption = tableFileInfo.getLowerOption();
        SubtitleOption upperSubtitles = SubtitleOption.getById(tableUpperOption.getId(), fileInfo.getSubtitleOptions());
        SubtitleOption lowerSubtitles = SubtitleOption.getById(tableLowerOption.getId(), fileInfo.getSubtitleOptions());

        Set<LanguageAlpha3Code> usedLanguages = getUsedLanguages(upperSubtitles, lowerSubtitles);

        int failedToLoadCount = loadStreamsIfNecessary(
                tableFileInfo,
                fileInfo,
                usedLanguages,
                progressMessagePrefix,
                runnerManager
        );
        if (failedToLoadCount != 0) {
            return new FileMergeInfo(
                    fileInfo.getId(),
                    FileMergeStatus.FAILED_TO_LOAD_SUBTITLES,
                    failedToLoadCount,
                    upperSubtitles,
                    lowerSubtitles,
                    null,
                    getFileWithResult(fileInfo, upperSubtitles, lowerSubtitles, context.getSettings())
            );
        }

        runnerManager.updateMessage(progressMessagePrefix + ": merging subtitles...");
        Subtitles mergedSubtitles = SubtitleMerger.mergeSubtitles(
                upperSubtitles.getSubtitles(),
                lowerSubtitles.getSubtitles()
        );

        if (isDuplicate(mergedSubtitles, usedLanguages, fileInfo)) {
            return new FileMergeInfo(
                    fileInfo.getId(),
                    FileMergeStatus.DUPLICATE,
                    0,
                    upperSubtitles,
                    lowerSubtitles,
                    mergedSubtitles,
                    getFileWithResult(fileInfo, upperSubtitles, lowerSubtitles, context.getSettings())
            );
        } else {
            return new FileMergeInfo(
                    fileInfo.getId(),
                    FileMergeStatus.OK,
                    0,
                    upperSubtitles,
                    lowerSubtitles,
                    mergedSubtitles,
                    getFileWithResult(fileInfo, upperSubtitles, lowerSubtitles, context.getSettings())
            );
        }
    }

    private static String getProgressMessagePrefix(int processedCount, int allFileCount, TableFileInfo fileInfo) {
        String progressPrefix = allFileCount > 1
                ? String.format("%d/%d ", processedCount + 1, allFileCount) + "stage 1 of 2: processing file "
                : "Stage 1 of 2: processing file ";

        return progressPrefix + fileInfo.getFilePath();
    }

    private static Set<LanguageAlpha3Code> getUsedLanguages(SubtitleOption upperOption, SubtitleOption lowerOption) {
        Set<LanguageAlpha3Code> result = new HashSet<>();

        if (upperOption instanceof FfmpegSubtitleStream) {
            result.add(((FfmpegSubtitleStream) upperOption).getLanguage());
        }

        if (lowerOption instanceof FfmpegSubtitleStream) {
            result.add(((FfmpegSubtitleStream) lowerOption).getLanguage());
        }

        return result;
    }

    private int loadStreamsIfNecessary(
            TableFileInfo tableFileInfo,
            FileInfo fileInfo,
            Set<LanguageAlpha3Code> usedLanguages,
            String progressMessagePrefix,
            BackgroundRunnerManager runnerManager
    ) throws InterruptedException {
        int failedToLoad = 0;

        List<FfmpegSubtitleStream> streamsToLoad = fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> usedLanguages.contains(stream.getLanguage()))
                .filter(stream -> stream.getSubtitles() == null)
                .collect(Collectors.toList());

        for (FfmpegSubtitleStream ffmpegStream : streamsToLoad) {
            runnerManager.updateMessage(
                    progressMessagePrefix + ": loading subtitles "
                            + GuiUtils.languageToString(ffmpegStream.getLanguage()).toUpperCase()
                            + (StringUtils.isBlank(ffmpegStream.getTitle()) ? "" : " " + ffmpegStream.getTitle())
                            + "..."
            );

            TableSubtitleOption tableSubtitleOption = TableSubtitleOption.getById(
                    ffmpegStream.getId(),
                    tableFileInfo.getSubtitleOptions()
            );

            try {
                String subtitleText = context.getFfmpeg().getSubtitleText(
                        ffmpegStream.getFfmpegIndex(),
                        ffmpegStream.getFormat(),
                        fileInfo.getFile()
                );
                ffmpegStream.setSubtitlesAndSize(SubRipParser.from(subtitleText), subtitleText.getBytes().length);

                Platform.runLater(
                        () -> tableWithFiles.subtitlesLoadedSuccessfully(
                                ffmpegStream.getSize(),
                                tableSubtitleOption,
                                tableFileInfo
                        )
                );
            } catch (FfmpegException e) {
                log.warn("failed to get subtitle text: " + e.getCode() + ", console output " + e.getConsoleOutput());
                Platform.runLater(
                        () -> tableWithFiles.failedToLoadSubtitles(
                                VideoTabBackgroundUtils.failedToLoadReasonFrom(e.getCode()),
                                tableSubtitleOption
                        )
                );
                failedToLoad++;
            } catch (SubtitleFormatException e) {
                Platform.runLater(
                        () -> tableWithFiles.failedToLoadSubtitles(
                                VideoTabBackgroundUtils.FAILED_TO_LOAD_STREAM_INCORRECT_FORMAT,
                                tableSubtitleOption
                        )
                );
                failedToLoad++;
            }
        }

        return failedToLoad;
    }

    private static boolean isDuplicate(Subtitles merged, Set<LanguageAlpha3Code> usedLanguages, FileInfo fileInfo) {
        for (FfmpegSubtitleStream subtitleStream : fileInfo.getFfmpegSubtitleStreams()) {
            if (usedLanguages.contains(subtitleStream.getLanguage())) {
                //todo !!!
                if (Objects.equals(merged, subtitleStream.getSubtitles())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static File getFileWithResult(
            FileInfo fileInfo,
            SubtitleOption upperOption,
            SubtitleOption lowerOption,
            Settings settings
    ) {
        if (settings.getMergeMode() == MergeMode.ORIGINAL_VIDEOS) {
            return fileInfo.getFile();
        } else if (settings.getMergeMode() == MergeMode.SEPARATE_SUBTITLE_FILES) {
            return new File(
                    FilenameUtils.removeExtension(fileInfo.getFile().getAbsolutePath())
                            + "_" + getOptionTitleForFile(upperOption) + "-" + getOptionTitleForFile(lowerOption)
                            + ".srt"
            );
        } else {
            throw new IllegalStateException();
        }
    }

    private static String getOptionTitleForFile(SubtitleOption subtitleOption) {
        if (subtitleOption instanceof FileWithSubtitles) {
            return "external";
        } else if (subtitleOption instanceof FfmpegSubtitleStream) {
            LanguageAlpha3Code language = ((FfmpegSubtitleStream) subtitleOption).getLanguage();
            return language != null ? language.toString() : "unknown";
        } else {
            throw new IllegalStateException();
        }
    }

    private static Optional<RequiredAndAvailableSpace> getRequiredAndAvailableTempSpace(
            List<FileMergeInfo> filesMergeInfo,
            List<FileInfo> filesInfo,
            Settings settings,
            BackgroundRunnerManager runnerManager
    ) {
        if (settings.getMergeMode() != MergeMode.ORIGINAL_VIDEOS) {
            return Optional.empty();
        }

        runnerManager.setIndeterminateProgress();
        runnerManager.setCancellationPossible(false);
        runnerManager.updateMessage("Calculating required temporary space...");

        Long requiredSpace = null;
        Long availableSpace = null;
        File directoryForTempFile = null;

        for (FileMergeInfo fileMergeInfo : filesMergeInfo) {
            if (fileMergeInfo.getStatus() != FileMergeStatus.OK) {
                continue;
            }

            FileInfo fileInfo = FileInfo.getById(fileMergeInfo.getId(), filesInfo);

            long currentRequiredSpace = fileInfo.getFile().length();
            long currentAvailableSpace = fileInfo.getFile().getFreeSpace();

            if (requiredSpace == null || requiredSpace < currentAvailableSpace) {
                requiredSpace = currentRequiredSpace;
            }

            if (availableSpace == null || availableSpace < currentAvailableSpace) {
                availableSpace = currentAvailableSpace;
                directoryForTempFile = fileInfo.getFile().getParentFile();
            }
        }

        if (requiredSpace == null) {
            return Optional.empty();
        }

        return Optional.of(new RequiredAndAvailableSpace(requiredSpace, availableSpace, directoryForTempFile));
    }

    private static List<File> getFilesToOverwrite(
            List<FileMergeInfo> filesMergeInfo,
            Settings settings,
            BackgroundRunnerManager runnerManager
    ) {
        if (settings.getMergeMode() == MergeMode.ORIGINAL_VIDEOS) {
            return new ArrayList<>();
        }

        runnerManager.setIndeterminateProgress();
        runnerManager.setCancellationPossible(false);
        runnerManager.updateMessage("Getting files to overwrite...");

        List<File> result = new ArrayList<>();
        for (FileMergeInfo fileMergeInfo : filesMergeInfo) {
            if (fileMergeInfo.getFileWithResult().exists()) {
                result.add(fileMergeInfo.getFileWithResult());
            }
        }

        return result;
    }

    @Getter
    public static class Result extends BackgroundResult {
        private int filesWithoutSelectionCount;

        private Long requiredTempSpace;

        private Long availableTempSpace;

        private File directoryForTempFile;

        private List<File> filesToOverwrite;

        private List<FileMergeInfo> filesMergeInfo;

        public Result(
                boolean cancelled,
                int filesWithoutSelectionCount,
                Long requiredTempSpace,
                Long availableTempSpace,
                File directoryForTempFile,
                List<File> filesToOverwrite,
                List<FileMergeInfo> filesMergeInfo
        ) {
            super(cancelled);
            this.filesWithoutSelectionCount = filesWithoutSelectionCount;
            this.requiredTempSpace = requiredTempSpace;
            this.availableTempSpace = availableTempSpace;
            this.directoryForTempFile = directoryForTempFile;
            this.filesToOverwrite = filesToOverwrite;
            this.filesMergeInfo = filesMergeInfo;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class FileMergeInfo {
        private String id;

        private FileMergeStatus status;

        private int failedToLoadSubtitlesCount;

        private SubtitleOption upperSubtitles;

        private SubtitleOption lowerSubtitles;

        private Subtitles mergedSubtitles;

        private File fileWithResult;
    }

    public enum FileMergeStatus {
        FAILED_TO_LOAD_SUBTITLES,
        DUPLICATE,
        OK
    }

    @AllArgsConstructor
    @Getter
    private static class RequiredAndAvailableSpace {
        private long requiredSpace;

        private long availableSpace;

        private File directoryForTempFile;
    }
}