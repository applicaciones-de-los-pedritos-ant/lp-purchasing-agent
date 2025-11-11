package org.rmj.purchasing.statusChange;

import java.sql.SQLException;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.json.simple.JSONObject;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.UserRight;

/**
 *
 * @author Maynard
 */
public class StatusChange {

    private double xOffset = 0.0d;
    private double yOffset = 0.0d;
    private GRider poGRider;
    private boolean pbWithUI;

    public boolean isPbWithUI() {
        return pbWithUI;
    }

    public void setPbWithUI(boolean pbWithUI) {
        this.pbWithUI = pbWithUI;
    }
    private JSONObject poJSON;
    private boolean pbWithParent = false;

    /**
     *
     * @param tableName
     * @param sourceNo
     * @param remarks
     * @param statusRequest
     * @param foGRider
     * @param wParent
     * @return
     * @throws SQLException
     * @throws CloneNotSupportedException
     */
    public JSONObject statusChange(String tableName,
            String sourceNo,
            String remarks,
            String statusRequest, GRider foGRider, boolean wParent) {

        this.poGRider = foGRider;
        pbWithParent = wParent;
        return statusChange(tableName, sourceNo, remarks, statusRequest);
    }

    protected JSONObject statusChange(String tableName,
            String sourceNo,
            String remarks,
            String statusRequest) {

        if (remarks.isEmpty()) {
            if (pbWithUI) {
                try {
                    remarks = getStatusChangeDialogue();

                    if (remarks.isEmpty()) {
                        poJSON = new JSONObject();
                        poJSON.put("result", "error");
                        poJSON.put("message", "status change is cancelled");
                        return poJSON;
                    }
                } catch (Exception e) {
                    poJSON = new JSONObject();
                    poJSON.put("result", "error");
                    poJSON.put("message", e.getMessage());
                    return poJSON;
                }

            }
        }

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        String lsApprovingOfficer = "";
        //system user level
        if (poGRider.getUserLevel() <= UserRight.ENCODER) {
            JSONObject loJSON = showFXDialog.getApproval(poGRider);

            if (loJSON == null) {
                this.poJSON = new JSONObject();
                this.poJSON.put("result", "error");
                this.poJSON.put("message", "Error updating the transaction status.");
                return poJSON;
            }

            if ((int) loJSON.get("nUserLevl") <= UserRight.ENCODER) {

                this.poJSON = new JSONObject();
                this.poJSON.put("result", "error");
                this.poJSON.put("message", "User account has no right to approve.");
                return poJSON;
            }

            lsApprovingOfficer = loJSON.get("sUserIDxx") != null ? (String) loJSON.get("sUserIDxx") : poGRider.getUserID();
        } else {
            lsApprovingOfficer = poGRider.getUserID();

        }

        String lsSQL = "";

//        lsSQL = "SELECT sTransNox,cTranstat FROM " + tableName + "WHERE sTransNox =" + SQLUtil.toSQL(sourceNo);
//
//        ResultSet loRS = poGRider.executeQuery(lsSQL);
//        int lnMax = (int) MiscUtil.RecordCount(loRS);
//        if (lnMax <= 0) {
//            this.poJSON = new JSONObject();
//            this.poJSON.put("result", "error");
//            this.poJSON.put("message", "Error updating the transaction status.");
//        }
//        while (loRS.next()) {
        lsSQL = "INSERT INTO Transaction_Status_History SET "
                + " sTransNox = " + SQLUtil.toSQL(MiscUtil.getNextCode("Transaction_Status_History", "sTransNox", true, poGRider.getConnection(), poGRider.getBranchCode()))
                + ", sTableNme = " + SQLUtil.toSQL(tableName)
                + ", sSourceNo = " + SQLUtil.toSQL(sourceNo)
                + ", sPayloadx = NULL"
                + ", sRemarksx = " + SQLUtil.toSQL(remarks)
                + ", sApproved = " + SQLUtil.toSQL(lsApprovingOfficer)
                + ", dApproved = " + SQLUtil.toSQL(poGRider.getServerDate())
                + ", cRefrStat = " + SQLUtil.toSQL(statusRequest)
                + ", cTranStat = " + SQLUtil.toSQL("1")
                + ", sModified = " + SQLUtil.toSQL(poGRider.getUserID())
                + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate());
        if (this.poGRider.executeQuery(lsSQL, tableName, poGRider.getBranchCode(), "") <= 0L) {

            if (!pbWithParent) {
                this.poGRider.rollbackTrans();
            }
            this.poJSON = new JSONObject();
            this.poJSON.put("result", "error");
            this.poJSON.put("message", "Error updating the transaction status.");
            return this.poJSON;
        }

        lsSQL = "UPDATE " + tableName
                + " SET   cTranStat = " + SQLUtil.toSQL(statusRequest)
                + ", sApproved = " + SQLUtil.toSQL(lsApprovingOfficer)
                + ", dApproved = " + SQLUtil.toSQL(poGRider.getServerDate())
                + " WHERE sTransNox = " + SQLUtil.toSQL((sourceNo));
        if (this.poGRider.executeQuery(lsSQL, tableName, poGRider.getBranchCode(), "") <= 0L) {

            if (!pbWithParent) {
                this.poGRider.rollbackTrans();
            }
            this.poJSON = new JSONObject();
            this.poJSON.put("result", "error");
            this.poJSON.put("message", "Error updating the transaction status.");
            return this.poJSON;
        }
//        }

        if (!pbWithParent) {
            poGRider.commitTrans();
        }

        poJSON = new JSONObject();
        poJSON.put("result", "success");
        return poJSON;
    }

    public String getStatusChangeDialogue() throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(getClass().getResource("/org/rmj/purchasing/statusChange/StatusChangeDialogue.fxml"));
        StatusChangeController loControl = new StatusChangeController();
        fxmlLoader.setController(loControl);
        Parent parent = fxmlLoader.<Parent>load();
        parent.setOnMousePressed(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent event) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });
        Stage stage = new Stage();
        parent.setOnMouseDragged(new EventHandler<MouseEvent>() {
            public void handle(MouseEvent event) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
        Scene scene = new Scene(parent);
        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Status Change");
        stage.showAndWait();
        if (!loControl.isCancelled()) {
            return loControl.getNote();
        }
        return "";
    }

}
