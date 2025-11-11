/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.rmj.purchasing.agent;

import com.mysql.jdbc.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.TransactionStatus;

/**
 *
 * @author User
 */
public class AutoVoidTrans {
    
    private final String pxeModuleName = AutoVoidTrans.class.getSimpleName();
    private GRider poGRider = null;
    private String psUserIDxx = "";
    private String psBranchCd = "";
    private String psWarnMsg = "";
    private String psErrMsgx = "";

    private boolean pbWithParent = false;
    private int pnEditMode;
    
    public void setUserID(String fsUserID){
        this.psUserIDxx  = fsUserID;
    }
    public int getEditMode(){
        return pnEditMode;
    }
    

    public String getMessage() {
        return psWarnMsg;
    }
    public void setMessage(String string) {
        psWarnMsg = string;
    }

    public String getErrMsg() {
        return psErrMsgx;
    }

    public void setErrMsg(String string) {
        psErrMsgx = string;
    }

    public void setBranch(String string) {
        psBranchCd = string;
    }

    public void setWithParent(boolean bln) {
        pbWithParent = bln;
    }
    private Connection setConnection(){
        Connection foConn;
        
        if (pbWithParent){
            foConn = (Connection) poGRider.getConnection();
            if (foConn == null) foConn = (Connection) poGRider.doConnect();
        }else foConn = (Connection) poGRider.doConnect();
        
        return foConn;
    }
    

    public AutoVoidTrans(GRider foGRider, String fsBranchCD, boolean fbWithParent) {
        this.poGRider = foGRider;

        if (foGRider != null) {
            this.pbWithParent = fbWithParent;
            this.psBranchCd = fsBranchCD;

            this.psUserIDxx = foGRider.getUserID();
            pnEditMode = EditMode.READY;

        }
    }
    public boolean voidTransaction() {
       voidPurchase();
       voidPOReceiving();
       voidInvTransfer();
       if(voidPurchase() || voidPOReceiving() || voidInvTransfer()){
            return true;
        }else{
            return false;
       }
    }
    
    private boolean voidPurchase(){
        try {
            String lsSQL = "SELECT sTransNox"
                + " FROM PO_Master "
                + " WHERE sBranchCd = " + SQLUtil.toSQL(psBranchCd)
                + " AND cTranStat = '0'"
                + " AND dTransact < " + SQLUtil.toSQL(poGRider.getServerDate());

            ResultSet loRS = poGRider.executeQuery(lsSQL);
            int lnMax = (int) MiscUtil.RecordCount(loRS);
            if (lnMax <= 0) {
                setMessage("No transaction for void.");
                return false;
            }

            while (loRS.next()) {
                lsSQL = "UPDATE PO_Master"
                        + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID)
                        + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                        + " WHERE sBranchCD = " + SQLUtil.toSQL(poGRider.getBranchCode())
                        + " AND sTransNox = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                System.out.println(lsSQL);
                poGRider.executeQuery(lsSQL, "PO_Master", psBranchCd, "");
            }
        } catch (SQLException ex) {
            Logger.getLogger(AutoVoidTrans.class.getName()).log(Level.SEVERE, null, ex);
            setMessage(ex.getMessage());
            return false;
        }
        return true;
    }
    
    private boolean voidPOReceiving(){
        try {
            String lsSQL = "SELECT sTransNox"
                + "IFNULL(sReferNox, '') sReferNox"
                + " FROM PO_Receiving_Master "
                + " WHERE sBranchCd = " + SQLUtil.toSQL(psBranchCd)
                + " AND cTranStat = '0'"
                + " AND dTransact < " + SQLUtil.toSQL(poGRider.getServerDate());

            ResultSet loRS = poGRider.executeQuery(lsSQL);
            
            int lnMax = (int) MiscUtil.RecordCount(loRS);
            if (lnMax <= 0) {
                setMessage("No transaction for void.");
                return false;
            }

            while (loRS.next()) {
                lsSQL = "UPDATE PO_Receiving_Master"
                        + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID)
                        + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                        + "     WHERE sBranchCD = " + SQLUtil.toSQL(poGRider.getBranchCode())
                        + " AND sTransNox = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                System.out.println(lsSQL);
                poGRider.executeQuery(lsSQL, "PO_Receiving_Master", psBranchCd, "");
            }
        } catch (SQLException ex) {
            Logger.getLogger(AutoVoidTrans.class.getName()).log(Level.SEVERE, null, ex);
            setMessage(ex.getMessage());
            return false;
        }
        return true;
    }
    
    private boolean voidInvTransfer(){
        try {
            String lsSQL = "SELECT sTransNox"
                + " FROM Inv_Transfer_Master "
                + " WHERE sBranchCd = " + SQLUtil.toSQL(psBranchCd)
                + " AND cTranStat = '0'"
                + " AND dTransact < " + SQLUtil.toSQL(poGRider.getServerDate());

            ResultSet loRS = poGRider.executeQuery(lsSQL);
            int lnMax = (int) MiscUtil.RecordCount(loRS);
            if (lnMax <= 0) {
                setMessage("No transaction for void.");
                return false;
            }

            while (loRS.next()) {
                lsSQL = "UPDATE Inv_Transfer_Master"
                        + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID)
                        + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                        + " WHERE sBranchCD = " + SQLUtil.toSQL(poGRider.getBranchCode())
                        + " AND sTransNox = " + SQLUtil.toSQL(loRS.getString("sTransNox"));
                System.out.println(lsSQL);
                poGRider.executeQuery(lsSQL, "Inv_Transfer_Master", psBranchCd, "");
            }
        } catch (SQLException ex) {
            Logger.getLogger(AutoVoidTrans.class.getName()).log(Level.SEVERE, null, ex);
            setMessage(ex.getMessage());
            return false;
        }
        return true;
    }

    
}
