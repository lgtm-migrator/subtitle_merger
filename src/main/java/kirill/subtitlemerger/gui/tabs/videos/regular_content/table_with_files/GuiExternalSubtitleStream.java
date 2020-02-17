package kirill.subtitlemerger.gui.tabs.videos.regular_content.table_with_files;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;

public class GuiExternalSubtitleStream extends GuiSubtitleStream {
    @Getter
    private int index;

    private StringProperty fileName;

    public GuiExternalSubtitleStream(int index) {
        super(GuiSubtitleStream.UNKNOWN_SIZE, false, false);

        this.index = index;
        this.fileName = new SimpleStringProperty(null);
    }

    public String getFileName() {
        return fileName.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName.set(fileName);
    }

    @Override
    public String getUniqueId() {
        return getFileName();
    }
}