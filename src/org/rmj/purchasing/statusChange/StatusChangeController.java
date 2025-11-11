/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.rmj.purchasing.statusChange;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.rmj.appdriver.agentfx.ShowMessageFX;

/**
 *
 * @author Maynard
 */
public class StatusChangeController {

    @FXML
    private Button btnOkay;

    @FXML
    private Button btnCancel;
//
    @FXML
    private TextArea tfComment;

    public void initialize(URL url, ResourceBundle rb) {
        this.tfComment.requestFocus();
    }

    @FXML
    void cmdCancel_Click(ActionEvent event) {
        this.pbCancelled = true;
        unloadScene(event);

    }

    @FXML
    void cmdOkay_Click(ActionEvent event) {
        if (tfComment.getText().isEmpty()) {
            ShowMessageFX.Information("Please ensure note is not empty", "Status Change", "");
            return;

        }
        this.psNote = this.tfComment.getText();
        this.pbCancelled = false;
        unloadScene(event);
    }

    private void unloadScene(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

    public String getNote() {
        return this.psNote;
    }

    public boolean isCancelled() {
        return this.pbCancelled;
    }

    private String psNote = "";

    private boolean pbCancelled = true;
}
