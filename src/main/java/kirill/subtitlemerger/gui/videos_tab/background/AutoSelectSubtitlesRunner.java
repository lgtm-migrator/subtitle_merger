package kirill.subtitlemerger.gui.videos_tab.background;

import javafx.application.Platform;
import kirill.subtitlemerger.logic.settings.Settings;
import kirill.subtitlemerger.gui.videos_tab.table_with_files.TableFileInfo;
import kirill.subtitlemerger.gui.videos_tab.table_with_files.TableSubtitleOption;
import kirill.subtitlemerger.gui.videos_tab.table_with_files.TableWithFiles;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunner;
import kirill.subtitlemerger.gui.utils.background.BackgroundRunnerManager;
import kirill.subtitlemerger.gui.utils.entities.ActionResult;
import kirill.subtitlemerger.logic.core.SubRipParser;
import kirill.subtitlemerger.logic.core.entities.SubtitleFormatException;
import kirill.subtitlemerger.logic.ffmpeg.Ffmpeg;
import kirill.subtitlemerger.logic.ffmpeg.FfmpegException;
import kirill.subtitlemerger.logic.files.entities.FfmpegSubtitleStream;
import kirill.subtitlemerger.logic.files.entities.FileInfo;
import lombok.AllArgsConstructor;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@CommonsLog
@AllArgsConstructor
public class AutoSelectSubtitlesRunner implements BackgroundRunner<ActionResult> {
    private static final Comparator<FfmpegSubtitleStream> STREAM_COMPARATOR = Comparator.comparing(
            FfmpegSubtitleStream::getSize
    ).reversed();

    private List<TableFileInfo> displayedTableFilesInfo;

    private List<FileInfo> filesInfo;

    private TableWithFiles tableWithFiles;

    private Ffmpeg ffmpeg;

    private Settings settings;

    @Override
    public ActionResult run(BackgroundRunnerManager runnerManager) {
        VideoTabBackgroundUtils.clearActionResults(displayedTableFilesInfo, tableWithFiles, runnerManager);

        List<TableFileInfo> selectedTableFilesInfo = VideoTabBackgroundUtils.getSelectedFilesInfo(
                displayedTableFilesInfo,
                runnerManager
        );

        int allFileCount = selectedTableFilesInfo.size();
        int processedCount = 0;
        int finishedSuccessfullyCount = 0;
        int notPossibleCount = 0;
        int failedCount = 0;

        runnerManager.setIndeterminateProgress();

        runnerManager.setCancellationDescription("Please be patient, this may take a while depending on the size.");
        runnerManager.setCancellationPossible(true);

        for (TableFileInfo tableFileInfo : selectedTableFilesInfo) {
            runnerManager.updateMessage(
                    VideoTabBackgroundUtils.getProcessFileProgressMessage(processedCount, allFileCount, tableFileInfo)
            );

            FileInfo fileInfo = FileInfo.getById(tableFileInfo.getId(), filesInfo);
            if (CollectionUtils.isEmpty(fileInfo.getFfmpegSubtitleStreams())) {
                notPossibleCount++;
                processedCount++;
                continue;
            }

            List<FfmpegSubtitleStream> matchingUpperSubtitles = getMatchingUpperSubtitles(fileInfo, settings);
            List<FfmpegSubtitleStream> matchingLowerSubtitles = getMatchingLowerSubtitles(fileInfo, settings);
            if (CollectionUtils.isEmpty(matchingUpperSubtitles) || CollectionUtils.isEmpty(matchingLowerSubtitles)) {
                notPossibleCount++;
                processedCount++;
                continue;
            }

            try {
                boolean loadedSuccessfully = loadStreamsIfNecessary(
                        tableFileInfo,
                        fileInfo,
                        matchingUpperSubtitles,
                        matchingLowerSubtitles,
                        processedCount,
                        allFileCount,
                        runnerManager
                );
                if (!loadedSuccessfully) {
                    failedCount++;
                    processedCount++;
                    continue;
                }

                if (matchingUpperSubtitles.size() > 1) {
                    matchingUpperSubtitles.sort(STREAM_COMPARATOR);
                }
                if (matchingLowerSubtitles.size() > 1) {
                    matchingLowerSubtitles.sort(STREAM_COMPARATOR);
                }

                TableSubtitleOption upperOption = TableSubtitleOption.getById(
                        matchingUpperSubtitles.get(0).getId(),
                        tableFileInfo.getSubtitleOptions()
                );
                Platform.runLater(() -> tableWithFiles.setSelectedAsUpper(upperOption));

                TableSubtitleOption lowerOption = TableSubtitleOption.getById(
                        matchingLowerSubtitles.get(0).getId(),
                        tableFileInfo.getSubtitleOptions()
                );
                Platform.runLater(() -> tableWithFiles.setSelectedAsLower(lowerOption));

                finishedSuccessfullyCount++;
                processedCount++;
            } catch (InterruptedException e) {
                break;
            }
        }

        return generateActionResult(
                allFileCount, processedCount, finishedSuccessfullyCount, notPossibleCount, failedCount
        );
    }

    private static List<FfmpegSubtitleStream> getMatchingUpperSubtitles(FileInfo fileInfo, Settings settings) {
        return fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() == settings.getUpperLanguage())
                .collect(Collectors.toList());
    }

    private static List<FfmpegSubtitleStream> getMatchingLowerSubtitles(FileInfo fileInfo, Settings settings) {
        return fileInfo.getFfmpegSubtitleStreams().stream()
                .filter(stream -> stream.getUnavailabilityReason() == null)
                .filter(stream -> stream.getLanguage() == settings.getLowerLanguage())
                .collect(Collectors.toList());
    }

    private boolean loadStreamsIfNecessary(
            TableFileInfo tableFileInfo,
            FileInfo fileInfo,
            List<FfmpegSubtitleStream> matchingUpperSubtitles,
            List<FfmpegSubtitleStream> matchingLowerSubtitles,
            int processedCount,
            int allFileCount,
            BackgroundRunnerManager runnerManager
    ) throws InterruptedException {
        boolean result = true;

        List<FfmpegSubtitleStream> ffmpegStreams = new ArrayList<>();
        if (matchingUpperSubtitles.size() > 1) {
            ffmpegStreams.addAll(matchingUpperSubtitles);
        }
        if (matchingLowerSubtitles.size() > 1) {
            ffmpegStreams.addAll(matchingLowerSubtitles);
        }

        int failedToLoadForFile = 0;

        for (FfmpegSubtitleStream ffmpegStream : ffmpegStreams) {
            runnerManager.updateMessage(
                    getUpdateMessage(processedCount, allFileCount, ffmpegStream, fileInfo.getFile())
            );

            if (ffmpegStream.getSubtitles() != null) {
                continue;
            }

            TableSubtitleOption tableSubtitleOption = TableSubtitleOption.getById(
                    ffmpegStream.getId(),
                    tableFileInfo.getSubtitleOptions()
            );

            try {
                String subtitleText = ffmpeg.getSubtitleText(ffmpegStream.getFfmpegIndex(), fileInfo.getFile());
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
                result = false;
                Platform.runLater(
                        () -> tableWithFiles.failedToLoadSubtitles(
                                VideoTabBackgroundUtils.failedToLoadReasonFrom(e.getCode()),
                                tableSubtitleOption
                        )
                );
                failedToLoadForFile++;
            } catch (SubtitleFormatException e) {
                result = false;
                Platform.runLater(
                        () -> tableWithFiles.failedToLoadSubtitles(
                                VideoTabBackgroundUtils.FAILED_TO_LOAD_STREAM_INCORRECT_FORMAT,
                                tableSubtitleOption
                        )
                );
                failedToLoadForFile++;
            } catch (InterruptedException e) {
                setFileInfoErrorIfNecessary(failedToLoadForFile, tableFileInfo, tableWithFiles);
                throw e;
            }
        }

        setFileInfoErrorIfNecessary(failedToLoadForFile, tableFileInfo, tableWithFiles);

        return result;
    }

    private static String getUpdateMessage(
            int processedCount,
            int allFileCount,
            FfmpegSubtitleStream subtitleStream,
            File file
    ) {
        String progressPrefix = allFileCount > 1
                ? (processedCount + 1) + "/" + allFileCount + " getting subtitles "
                : "Getting subtitles ";

        return progressPrefix
                + GuiUtils.languageToString(subtitleStream.getLanguage()).toUpperCase()
                + (StringUtils.isBlank(subtitleStream.getTitle()) ? "" : " " + subtitleStream.getTitle())
                + " in " + file.getName();
    }

    private static void setFileInfoErrorIfNecessary(
            int failedToLoadForFile,
            TableFileInfo fileInfo,
            TableWithFiles tableWithFiles
    ) {
        if (failedToLoadForFile == 0) {
            return;
        }

        String message = GuiUtils.getTextDependingOnTheCount(
                failedToLoadForFile,
                "Auto-select has failed because failed to load subtitles",
                "Auto-select has failed because failed to load %d subtitles"
        );

        Platform.runLater(() -> tableWithFiles.setActionResult(ActionResult.onlyError(message), fileInfo));
    }

    private static ActionResult generateActionResult(
            int allFileCount,
            int processedCount,
            int finishedSuccessfullyCount,
            int notPossibleCount,
            int failedCount
    ) {
        String success = null;
        String warn = null;
        String error = null;

        if (processedCount == 0) {
            warn = "Task has been cancelled, nothing was done";
        } else if (finishedSuccessfullyCount == allFileCount) {
            success = GuiUtils.getTextDependingOnTheCount(
                    finishedSuccessfullyCount,
                    "Auto-selection has finished successfully for the file",
                    "Auto-selection has finished successfully for all %d files"
            );
        } else if (notPossibleCount == allFileCount) {
            warn = GuiUtils.getTextDependingOnTheCount(
                    notPossibleCount,
                    "Auto-selection is not possible for this file",
                    "Auto-selection is not possible for all %d files"
            );
        } else if (failedCount == allFileCount) {
            error = GuiUtils.getTextDependingOnTheCount(
                    failedCount,
                    "Failed to perform auto-selection for the file",
                    "Failed to perform auto-selection for all %d files"
            );
        } else {
            if (finishedSuccessfullyCount != 0) {
                success = String.format(
                        "Auto-selection has finished for %d/%d files successfully",
                        finishedSuccessfullyCount,
                        allFileCount
                );
            }

            if (processedCount != allFileCount) {
                if (finishedSuccessfullyCount == 0) {
                    warn = String.format(
                            "Auto-selection has been cancelled for %d/%d files",
                            allFileCount - processedCount,
                            allFileCount
                    );
                } else {
                    warn = String.format("cancelled for %d/%d", allFileCount - processedCount, allFileCount);
                }
            }

            if (notPossibleCount != 0) {
                if (processedCount != allFileCount) {
                    warn += String.format(", not possible for %d/%d", notPossibleCount, allFileCount);
                } else if (finishedSuccessfullyCount != 0) {
                    warn = String.format("not possible for %d/%d", notPossibleCount, allFileCount);
                } else {
                    warn = String.format(
                            "Auto-selection is not possible for %d/%d files",
                            notPossibleCount,
                            allFileCount
                    );
                }
            }

            if (failedCount != 0) {
                error = String.format("failed for %d/%d", failedCount, allFileCount);
            }
        }

        return new ActionResult(success, warn, error);
    }
}