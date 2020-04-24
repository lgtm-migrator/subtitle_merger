package kirill.subtitlemerger.gui.common_controls;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import kirill.subtitlemerger.gui.utils.GuiUtils;
import kirill.subtitlemerger.gui.utils.entities.ActionResult;
import org.apache.commons.lang3.StringUtils;

/**
 * A control to display data from the ActionResult class in three different colors - the error part in red, the part
 * with warnings in orange and the part with success in green.
 * @see ActionResult
 */
public class ActionResultPane extends HBox {
    @FXML
    private Label successLabel;

    @FXML
    private Label warnLabel;

    @FXML
    private Label errorLabel;

    private BooleanProperty wrapText;

    private BooleanProperty alwaysManaged;

    private BooleanProperty empty;

    public ActionResultPane() {
        GuiUtils.initializeControl(this, "/gui/javafx/common_controls/action_result_pane.fxml");

        wrapText = new SimpleBooleanProperty(false);
        alwaysManaged = new SimpleBooleanProperty(false);
        empty = new SimpleBooleanProperty(true);

        setVisible(false);
        managedProperty().bind(alwaysManaged.or(Bindings.not(empty)));

        successLabel.wrapTextProperty().bind(wrapText);
        warnLabel.wrapTextProperty().bind(wrapText);
        errorLabel.wrapTextProperty().bind(wrapText);
    }

    public boolean isWrapText() {
        return wrapText.get();
    }

    public BooleanProperty wrapTextProperty() {
        return wrapText;
    }

    public void setWrapText(boolean wrapText) {
        this.wrapText.set(wrapText);
    }

    public boolean isAlwaysManaged() {
        return alwaysManaged.get();
    }

    public BooleanProperty alwaysManagedProperty() {
        return alwaysManaged;
    }

    public void setAlwaysManaged(boolean alwaysManaged) {
        this.alwaysManaged.set(alwaysManaged);
    }

    public boolean isEmpty() {
        return empty.get();
    }

    public BooleanProperty emptyProperty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty.set(empty);
    }

    public void set(ActionResult actionResult) {
        String success = null;
        String warn = null;
        String error = null;

        boolean empty = true;

        if (!StringUtils.isBlank(actionResult.getSuccess())) {
            empty = false;

            success = actionResult.getSuccess();
            if (!StringUtils.isBlank(actionResult.getWarn()) || !StringUtils.isBlank(actionResult.getError())) {
                success += ", ";
            }
        }

        if (!StringUtils.isBlank(actionResult.getWarn())) {
            empty = false;

            warn = actionResult.getWarn();
            if (!StringUtils.isBlank(actionResult.getError())) {
                warn += ", ";
            }
        }

        if (!StringUtils.isBlank(actionResult.getError())) {
            empty = false;

            error = actionResult.getError();
        }

        successLabel.setText(success);
        warnLabel.setText(warn);
        errorLabel.setText(error);

        setEmpty(empty);
        setVisible(!empty);
    }

    public void clear() {
        set(ActionResult.NO_RESULT);
    }

    public void setOnlySuccess(String text) {
        set(ActionResult.onlySuccess(text));
    }

    public void setOnlyWarn(String text) {
        set(ActionResult.onlyWarn(text));
    }

    public void setOnlyError(String text) {
        set(ActionResult.onlyError(text));
    }
}