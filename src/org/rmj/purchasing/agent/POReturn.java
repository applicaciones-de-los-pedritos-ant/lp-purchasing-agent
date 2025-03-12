/**
 * Inventory Waste BASE
 *
 * @author Michael Torres Cuison
 * @since 2018.10.10
 */
package org.rmj.purchasing.agent;

import com.mysql.jdbc.Connection;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.view.JasperViewer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.GCrypt;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.RecordStatus;
import org.rmj.appdriver.constants.TransactionStatus;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.iface.GEntity;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.UserRight;
import org.rmj.appdriver.agentfx.callback.IMasterDetail;
import org.rmj.cas.client.base.XMClient;
import org.rmj.cas.inventory.base.InvExpiration;
import org.rmj.cas.inventory.base.InvMaster;
import org.rmj.cas.inventory.base.Inventory;
import org.rmj.cas.inventory.base.InventoryTrans;
import org.rmj.cas.purchasing.pojo.UnitPOReturnDetail;
import org.rmj.cas.purchasing.pojo.UnitPOReturnMaster;
import org.rmj.lp.parameter.agent.XMBranch;
import org.rmj.lp.parameter.agent.XMDepartment;
import org.rmj.lp.parameter.agent.XMInventoryType;
import org.rmj.lp.parameter.agent.XMSupplier;
import org.rmj.lp.parameter.agent.XMTerm;

public class POReturn {

    private final String MODULENAME = "POReturn";

    public POReturn(GRider foGRider, String fsBranchCD, boolean fbWithParent) {
        this.poGRider = foGRider;

        if (foGRider != null) {
            this.pbWithParent = fbWithParent;
            this.psBranchCd = fsBranchCD;

            this.psUserIDxx = foGRider.getUserID();
            pnEditMode = EditMode.UNKNOWN;
        }
    }

    public boolean BrowseRecord(String fsValue, boolean fbByCode) {
        String lsHeader = "Trans No.»Supplier»Date»Inv. Type»Date";
        String lsColName = "sTransNox»sClientNm»dTransact»sDescript»dTransact";
        String lsColCrit = "a.sTransNox»d.sClientNm»a.dTransact»c.sDescript»a.dTransact";
        String lsSQL = getSQ_POReturn();

        JSONObject loJSON = showFXDialog.jsonSearch(poGRider,
                lsSQL,
                fsValue,
                lsHeader,
                lsColName,
                lsColCrit,
                fbByCode ? 0 : 1);

        if (loJSON == null) {
            return false;
        } else {
            return openTransaction((String) loJSON.get("sTransNox"));
        }
    }

    public boolean addDetail() {
        if (paDetail.isEmpty()) {
            paDetail.add(new UnitPOReturnDetail());

        } else {
            if (!paDetail.get(ItemCount() - 1).getStockID().equals("")
                    && paDetail.get(ItemCount() - 1).getQuantity().doubleValue() != 0.00) {
                paDetail.add(new UnitPOReturnDetail());

            }
        }
        return true;
    }

    public boolean deleteDetail(int fnRow) {
        paDetail.remove(fnRow);

        if (paDetail.isEmpty()) {
            paDetail.add(new UnitPOReturnDetail());
        }

        poData.setTranTotal(computeTotal());
        poData.setTaxWHeld(computeTaxWHeld());
        return true;
    }

    public void setDetail(int fnRow, int fnCol, Object foData) {

        if (pnEditMode != EditMode.UNKNOWN) {
            // Don't allow specific fields to assign values
            if (!(fnCol == poDetail.getColumn("sTransNox")
                    || fnCol == poDetail.getColumn("nEntryNox")
                    || fnCol == poDetail.getColumn("dModified"))) {

//                setDetail(fnRow, fnCol, foData);
                if (fnCol == 5 || fnCol == 6 || fnCol == 7) {
                    if (foData instanceof Number) {
                        paDetail.get(fnRow).setValue(fnCol, foData);
                    } else {
                        paDetail.get(fnRow).setValue(fnCol, 0);
                    }
                } else {
                    paDetail.get(fnRow).setValue(fnCol, foData);
                }

                DetailRetreived(fnCol);
                if (fnCol == poDetail.getColumn("nQuantity")
                        || fnCol == poDetail.getColumn("nUnitPrce")
                        || fnCol == poDetail.getColumn("nFreightx")) {
                    poData.setTranTotal(computeTotal());
                    poData.setTaxWHeld(computeTaxWHeld());
                    MasterRetreived(6);
                    MasterRetreived(7);
                }
            }
        }
//        switch (fnCol){
//            case 6: //nUnitPrce
//            case 7: //nFreightx
//                if (foData instanceof Number){
//                    paDetail.get(fnRow).setValue(fnCol, foData);
//                }else paDetail.get(fnRow).setValue(fnCol, 0);
//                break;
//            case 5: //nQuantity
//                if (foData instanceof Number){
//                    paDetail.get(fnRow).setValue(fnCol, foData);
//                }else paDetail.get(fnRow).setValue(fnCol, 0);
//                break;
//            default:
//                paDetail.get(fnRow).setValue(fnCol, foData);
//        }
//        
//        DetailRetreived(fnCol);
    }

    public void setDetail(int fnRow, String fsCol, Object foData) {
        switch (fsCol) {
            case "nUnitPrce":
            case "nFreightx":
                if (foData instanceof Number) {
                    paDetail.get(fnRow).setValue(fsCol, foData);
                } else {
                    paDetail.get(fnRow).setValue(fsCol, 0);
                }

                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(6);
                MasterRetreived(7);
                break;
            case "nQuantity":
                if (foData instanceof Number) {
                    paDetail.get(fnRow).setValue(fsCol, foData);
                } else {
                    paDetail.get(fnRow).setValue(fsCol, 0);
                }

                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(6);
                MasterRetreived(7);
                break;
            default:
                paDetail.get(fnRow).setValue(fsCol, foData);
        }
        DetailRetreived(paDetail.get(fnRow).getColumn(fsCol));
    }

    public Object getDetail(int fnRow, int fnCol) {
        return paDetail.get(fnRow).getValue(fnCol);
    }

    public Object getDetail(int fnRow, String fsCol) {
        return paDetail.get(fnRow).getValue(fsCol);
    }

    private double computeTotal() {
        double lnTranTotal = 0;
        for (int lnCtr = 0; lnCtr <= ItemCount() - 1; lnCtr++) {
            lnTranTotal += (Double.valueOf(getDetail(lnCtr, "nQuantity").toString()) * Double.valueOf(getDetail(lnCtr, "nUnitPrce").toString()))
                    + Double.valueOf(getDetail(lnCtr, "nFreightx").toString());
        }

        //add the freight charge to total order
        lnTranTotal += Double.valueOf(poData.getFreightCharge().toString());
        //less the discounts
        lnTranTotal = lnTranTotal - (lnTranTotal * Double.valueOf(poData.getDiscountRate().toString())) - Double.valueOf(poData.getAdditionalDisc().toString());
        return lnTranTotal;
    }

    private double computeTaxWHeld() {
        DecimalFormat df2 = new DecimalFormat(".##");
        String lsTaxWHeld = df2.format(((Double) poData.getTranTotal() / pxeTaxExcludRte) * pxeTaxWHeldRate);
        return Double.parseDouble(lsTaxWHeld);
    }

    public boolean newTransaction() {
        Connection loConn = null;
        loConn = setConnection();

        poData = new UnitPOReturnMaster();
        poData.setTransNox(MiscUtil.getNextCode(poData.getTable(), "sTransNox", true, loConn, psBranchCd));
        poData.setDateTransact(poGRider.getServerDate());

        //init detail
        paDetail = new ArrayList<>();

        pnEditMode = EditMode.ADDNEW;

        addDetail();
        return true;
    }

    public boolean openTransaction(String fsTransNox) {
        poData = loadTransaction(fsTransNox);

        if (poData != null) {
            paDetail = loadTransactionDetail(fsTransNox);
        } else {
            setMessage("Unable to load transaction.");
            return false;
        }

        pnEditMode = EditMode.READY;
        return true;
    }

    public UnitPOReturnMaster loadTransaction(String fsTransNox) {
        UnitPOReturnMaster loObject = new UnitPOReturnMaster();

        Connection loConn = null;
        loConn = setConnection();

        String lsSQL = MiscUtil.addCondition(getSQ_ReturnMaster(), "sTransNox = " + SQLUtil.toSQL(fsTransNox));
        ResultSet loRS = poGRider.executeQuery(lsSQL);

        try {
            if (!loRS.next()) {
                setMessage("No Record Found");
            } else {
                //load each column to the entity
                for (int lnCol = 1; lnCol <= loRS.getMetaData().getColumnCount(); lnCol++) {
                    loObject.setValue(lnCol, loRS.getObject(lnCol));
                }
            }
        } catch (SQLException ex) {
            setErrMsg(ex.getMessage());
        } finally {
            MiscUtil.close(loRS);
            if (!pbWithParent) {
                MiscUtil.close(loConn);
            }
        }

        return loObject;
    }

    private ArrayList<UnitPOReturnDetail> loadTransactionDetail(String fsTransNox) {
        UnitPOReturnDetail loOcc = null;
        Connection loConn = null;
        loConn = setConnection();

        ArrayList<UnitPOReturnDetail> loDetail = new ArrayList<>();

        ResultSet loRS = poGRider.executeQuery(
                MiscUtil.addCondition(getSQ_Detail(),
                        "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        try {
            for (int lnCtr = 1; lnCtr <= MiscUtil.RecordCount(loRS); lnCtr++) {
                loRS.absolute(lnCtr);

                loOcc = new UnitPOReturnDetail();
                loOcc.setValue("sTransNox", loRS.getString("sTransNox"));
                loOcc.setValue("nEntryNox", loRS.getInt("nEntryNox"));
                loOcc.setValue("sStockIDx", loRS.getString("sStockIDx"));
                loOcc.setValue("cUnitType", loRS.getString("cUnitType"));
                loOcc.setValue("nQuantity", loRS.getDouble("nQuantity"));
                loOcc.setValue("nUnitPrce", loRS.getDouble("nUnitPrce"));
                loOcc.setValue("nFreightx", loRS.getDouble("nFreightx"));
                loOcc.setValue("dExpiryDt", loRS.getDate("dExpiryDt"));
                loOcc.setValue("dModified", loRS.getDate("dModified"));
                loOcc.setValue("sBrandNme", loRS.getString("sBrandNme"));

                loDetail.add(loOcc);
            }
        } catch (SQLException e) {
            //log error message
            return null;
        }

        return loDetail;
    }

    public boolean updateRecord() {
        if (pnEditMode != EditMode.READY) {
            return false;
        } else {
            pnEditMode = EditMode.UPDATE;
            return true;
        }
    }

    public boolean saveTransaction() {
        String lsSQL = "";
        boolean lbUpdate = false;

        UnitPOReturnMaster loOldEnt = null;
        UnitPOReturnMaster loNewEnt = null;
        UnitPOReturnMaster loResult = null;

        // Check for the value of foEntity
        if (!(poData instanceof UnitPOReturnMaster)) {
            setErrMsg("Invalid Entity Passed as Parameter");
            return false;
        }

        // Typecast the Entity to this object
        loNewEnt = (UnitPOReturnMaster) poData;

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        //delete empty detail
        if (paDetail.get(ItemCount() - 1).getStockID().equals("")) {
            deleteDetail(ItemCount() - 1);
        }
        if (loNewEnt.getSupplier() == null || loNewEnt.getSupplier().isEmpty()) {
            setMessage("No supplier detected.");
            return false;
        }
        // Generate the SQL Statement
        if (pnEditMode == EditMode.ADDNEW) {
            Connection loConn = null;
            loConn = setConnection();

            String lsTransNox = MiscUtil.getNextCode(loNewEnt.getTable(), "sTransNox", true, loConn, psBranchCd);

            loNewEnt.setTransNox(lsTransNox);
            loNewEnt.setEntryNox(ItemCount());
            loNewEnt.setModifiedBy(poCrypt.encrypt(poGRider.getUserID()));
            loNewEnt.setDateModified(poGRider.getServerDate());

            loNewEnt.setPreparedBy(poCrypt.encrypt(poGRider.getUserID()));
            loNewEnt.setDatePrepared(poGRider.getServerDate());

            if (!pbWithParent) {
                MiscUtil.close(loConn);
            }

            lbUpdate = saveDetail(loNewEnt.getTransNox());
            if (!lbUpdate) {
                lsSQL = "";
            } else {
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt);
            }
        } else {
            //Load previous transaction
            loOldEnt = loadTransaction(poData.getTransNox());

            loNewEnt.setEntryNox(ItemCount());
            loNewEnt.setDateModified(poGRider.getServerDate());

            lbUpdate = saveDetail(loNewEnt.getTransNox());
            if (!lbUpdate) {
                lsSQL = "";
            } else {
                lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, (GEntity) loOldEnt, "sTransNox = " + SQLUtil.toSQL(loNewEnt.getValue(1)));
            }
        }

        if (!lsSQL.equals("") && getErrMsg().isEmpty()) {
            if (poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0) {
                if (!poGRider.getErrMsg().isEmpty()) {
                    setErrMsg(poGRider.getErrMsg());
                } else {
                    setMessage("No record updated");
                }
            }
            lbUpdate = true;
        }

        if (!pbWithParent) {
            if (!getErrMsg().isEmpty()) {
                poGRider.rollbackTrans();
            } else {
                poGRider.commitTrans();
            }
        }

        return lbUpdate;
    }

    private boolean saveDetail(String fsTransNox) {
        setMessage("");
        int lnCtr;
        String lsSQL;
        UnitPOReturnDetail loNewEnt = null;

        for (lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
            if (paDetail.isEmpty()) {
                setMessage("Unable to save empty detail transaction.");
                return false;
            } else if (paDetail.get(0).getStockID().equals("")
                    || paDetail.get(0).getQuantity().doubleValue() == 0.00) {
                setMessage("Detail might not have item or zero quantity.");
                return false;
            }
        }

        if (pnEditMode == EditMode.ADDNEW) {
            Connection loConn = null;
            loConn = setConnection();

            for (lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
                loNewEnt = paDetail.get(lnCtr);

                if (!loNewEnt.getStockID().equals("")) {
                    loNewEnt.setTransNox(fsTransNox);
                    loNewEnt.setEntryNox(lnCtr + 1);
                    loNewEnt.setDateModified(poGRider.getServerDate());

                    lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, "sBrandNme");

                    if (!lsSQL.equals("")) {
                        if (poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0) {
                            if (!poGRider.getErrMsg().isEmpty()) {
                                setErrMsg(poGRider.getErrMsg());
                                return false;
                            }
                        }
                    }
                }
            }
        } else {
            ArrayList<UnitPOReturnDetail> laSubUnit = loadTransactionDetail(poData.getTransNox());

            for (lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
                loNewEnt = paDetail.get(lnCtr);
                if (Double.parseDouble(loNewEnt.getQuantity().toString()) <= 0.00) {
                    setMessage("Unable to save zero quantity detail.");
                    return false;
                }
                if (!loNewEnt.getStockID().equals("")) {
                    if (lnCtr <= laSubUnit.size() - 1) {
                        if (loNewEnt.getEntryNox() != lnCtr + 1) {
                            loNewEnt.setEntryNox(lnCtr + 1);
                        }
                        if (loNewEnt.getTransNox().isEmpty()) {
                            loNewEnt.setTransNox(fsTransNox);
                        }

                        lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt,
                                (GEntity) laSubUnit.get(lnCtr),
                                " nEntryNox = " + SQLUtil.toSQL(loNewEnt.getValue(2))
                                + " AND sTransNox = " + SQLUtil.toSQL(loNewEnt.getValue(1)),
                                "sBrandNme");

                    } else {
                        loNewEnt.setTransNox(fsTransNox);
                        loNewEnt.setEntryNox(lnCtr + 1);
                        loNewEnt.setDateModified(poGRider.getServerDate());
                        lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, "sBrandNme");
                    }

                    if (!lsSQL.equals("")) {
                        if (poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0) {
                            if (!poGRider.getErrMsg().isEmpty()) {
                                setErrMsg(poGRider.getErrMsg());
                                return false;
                            }
                        }
                    }
                } else {
                    for (int lnCtr2 = lnCtr; lnCtr2 <= laSubUnit.size() - 1; lnCtr2++) {
                        lsSQL = "DELETE FROM " + poDetail.getTable()
                                + " WHERE sStockIDx = " + SQLUtil.toSQL(laSubUnit.get(lnCtr2).getStockID())
                                + " AND nEntryNox = " + SQLUtil.toSQL(laSubUnit.get(lnCtr2).getEntryNox())
                                + " AND sTransNox = " + SQLUtil.toSQL(laSubUnit.get(lnCtr2).getTransNox());

                        if (!lsSQL.equals("")) {
                            if (poGRider.executeQuery(lsSQL, poDetail.getTable(), "", "") == 0) {
                                if (!poGRider.getErrMsg().isEmpty()) {
                                    setErrMsg(poGRider.getErrMsg());
                                    return false;
                                }
                            }
                        }
                    }
                    break;
                }
            }
            if (lnCtr == laSubUnit.size() - 1) {
                lsSQL = "DELETE FROM " + poDetail.getTable()
                        + " WHERE sStockIDx = " + SQLUtil.toSQL(laSubUnit.get(lnCtr).getStockID())
                        + " AND nEntryNox = " + SQLUtil.toSQL(laSubUnit.get(lnCtr).getEntryNox())
                        + " AND sTransNox = " + SQLUtil.toSQL(laSubUnit.get(lnCtr).getTransNox());

                if (!lsSQL.equals("")) {
                    if (poGRider.executeQuery(lsSQL, poDetail.getTable(), "", "") == 0) {
                        if (!poGRider.getErrMsg().isEmpty()) {
                            setErrMsg(poGRider.getErrMsg());
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    //Added detail methods
    //Added methods
    private boolean saveInvTrans(String fsTransNox, String fsSupplier, Date fdTransact) {
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();

        InvMaster loInv = new InvMaster(poGRider, psBranchCd, true);

        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
            if (paDetail.get(lnCtr).getStockID().equals("")) {
                break;
            }

            if (loInv.SearchInventory(paDetail.get(lnCtr).getStockID(), false, true)) {
                loInvTrans.setDetail(lnCtr, "sStockIDx", loInv.getMaster("sStockIDx"));
                loInvTrans.setDetail(lnCtr, "sReplacID", "");
                loInvTrans.setDetail(lnCtr, "nQuantity", paDetail.get(lnCtr).getQuantity());
                loInvTrans.setDetail(lnCtr, "nQtyOnHnd", loInv.getMaster("nQtyOnHnd"));
                loInvTrans.setDetail(lnCtr, "nResvOrdr", loInv.getMaster("nResvOrdr"));
                loInvTrans.setDetail(lnCtr, "nBackOrdr", loInv.getMaster("nBackOrdr"));
                loInvTrans.setDetail(lnCtr, "nLedgerNo", loInv.getMaster("nLedgerNo"));
            } else {
                setErrMsg("No Inventory Found.");
                setMessage("Unable to search item on inventory.");
                return false;
            }
        }

        if (!loInvTrans.POReturn(fsTransNox, poGRider.getServerDate(), fsSupplier, EditMode.ADDNEW)) {
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }
        return saveInvExpiration(fdTransact);
    }

    private boolean unsaveInvTrans(String fsTransNox, String fsSupplier) {
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();

        InvMaster loInv = new InvMaster(poGRider, psBranchCd, true);

        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
            if (paDetail.get(lnCtr).getStockID().equals("")) {
                break;
            }

            if (loInv.SearchInventory(paDetail.get(lnCtr).getStockID(), false, true)) {
                loInvTrans.setDetail(lnCtr, "sStockIDx", loInv.getMaster("sStockIDx"));
                loInvTrans.setDetail(lnCtr, "nQtyOnHnd", loInv.getMaster("nQtyOnHnd"));
                loInvTrans.setDetail(lnCtr, "nResvOrdr", loInv.getMaster("nResvOrdr"));
                loInvTrans.setDetail(lnCtr, "nBackOrdr", loInv.getMaster("nBackOrdr"));
                loInvTrans.setDetail(lnCtr, "nLedgerNo", loInv.getMaster("nLedgerNo"));
            } else {
                setErrMsg("No Inventory Found.");
                setMessage("Unable to search item on inventory.");
                return false;
            }

        }

        if (!loInvTrans.POReturn(fsTransNox, poGRider.getServerDate(), fsSupplier, EditMode.DELETE)) {
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }

        //TODO
        //update branch order info
        return true;
    }

    private boolean saveInvExpiration(Date fdTransact) {
        InvExpiration loInvTrans = new InvExpiration(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();

        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr++) {
            if (paDetail.get(lnCtr).getStockID().equals("")) {
                break;
            }
            loInvTrans.setDetail(lnCtr, "sStockIDx", paDetail.get(lnCtr).getStockID());
            loInvTrans.setDetail(lnCtr, "dExpiryDt", paDetail.get(lnCtr).getDateExpiry());
            loInvTrans.setDetail(lnCtr, "nQtyOutxx", paDetail.get(lnCtr).getQuantity());
        }

        if (!loInvTrans.POReturn(fdTransact, EditMode.ADDNEW)) {
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }

        //TODO
        //update branch order info
        return true;
    }

    public boolean deleteTransaction(String string) {
        UnitPOReturnMaster loObject = loadTransaction(string);
        boolean lbResult = false;

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        String lsSQL = "DELETE FROM " + loObject.getTable()
                + " WHERE sTransNox = " + SQLUtil.toSQL(string);

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("No record deleted.");
            }
        } else {
            lbResult = true;
        }

        //delete detail rows
        lsSQL = "DELETE FROM " + poDetail.getTable()
                + " WHERE sTransNox = " + SQLUtil.toSQL(string);

        if (poGRider.executeQuery(lsSQL, poDetail.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("No record deleted.");
            }
        } else {
            lbResult = true;
        }

        if (!pbWithParent) {
            if (getErrMsg().isEmpty()) {
                poGRider.commitTrans();
            } else {
                poGRider.rollbackTrans();
            }
        }

        return lbResult;
    }

    public boolean closeTransaction(String fsTransNox) {
        UnitPOReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        if (!loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_OPEN)) {
            setMessage("Unable to close closed/cancelled/posted/voided transaction.");
            return lbResult;
        }

        if (pnEditMode != EditMode.READY) {
            setMessage("Invalid edit mode detected.");
            return false;
        }

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        //if it is already closed, just return true
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CLOSED)) {
            return true;
        }

        if (!loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_OPEN)) {
            setMessage("Unable to close closed/cancelled/posted/voided transaction.");
            return lbResult;
        }

        String lsSQL = "UPDATE " + loObject.getTable()
                + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CLOSED)
                + ", sModified = " + SQLUtil.toSQL(psUserIDxx)
                + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                + " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("Unable to close transaction.");
            }
        } else {
            lbResult = saveInvTrans(loObject.getTransNox(), loObject.getSupplier(), loObject.getDateTransact());
        }

        if (!pbWithParent) {
            if (getErrMsg().isEmpty()) {
                poGRider.commitTrans();
            } else {
                poGRider.rollbackTrans();
            }
        }
        return lbResult;
    }

    public boolean postTransaction(String fsTransNox) {
        UnitPOReturnMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)) {
            setMessage("Unable to post cancelled/posted/voided transaction.");
            return lbResult;
        }

        String lsSQL = "UPDATE " + loObject.getTable()
                + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_POSTED)
                + ", sPostedxx = " + SQLUtil.toSQL(poCrypt.encrypt(psUserIDxx))
                + ", dPostedxx = " + SQLUtil.toSQL(poGRider.getServerDate())
                + ", sModified = " + SQLUtil.toSQL(poCrypt.encrypt(psUserIDxx))
                + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                + " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("No record deleted.");
            }
        } else {
            lbResult = saveInvTrans(loObject.getTransNox(), loObject.getSupplier(), loObject.getDateTransact());
        }

        if (!pbWithParent) {
            if (getErrMsg().isEmpty()) {
                poGRider.commitTrans();
            } else {
                poGRider.rollbackTrans();
            }
        }
        return lbResult;
    }

    public boolean voidTransaction(String string) {
        UnitPOReturnMaster loObject = loadTransaction(string);
        boolean lbResult = false;

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)) {
            setMessage("Unable to close processed transaction.");
            return lbResult;
        }

        String lsSQL = "UPDATE " + loObject.getTable()
                + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID)
                + ", sModified = " + SQLUtil.toSQL(psUserIDxx)
                + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                + " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("No record deleted.");
            }
        } else {
            lbResult = true;
        }

        if (!pbWithParent) {
            if (getErrMsg().isEmpty()) {
                poGRider.commitTrans();
            } else {
                poGRider.rollbackTrans();
            }
        }
        return lbResult;
    }

    public boolean cancelTransaction(String string) {
        UnitPOReturnMaster loObject = loadTransaction(string);
        boolean lbResult = false;

        if (loObject == null) {
            setMessage("No record found...");
            return lbResult;
        }

        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED)
                || loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)) {
            setMessage("Unable to close processed transaction.");
            return lbResult;
        }

        String lsSQL = "UPDATE " + loObject.getTable()
                + " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CANCELLED)
                + ", sModified = " + SQLUtil.toSQL(psUserIDxx)
                + ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate())
                + " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());

        if (!pbWithParent) {
            poGRider.beginTrans();
        }

        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0) {
            if (!poGRider.getErrMsg().isEmpty()) {
                setErrMsg(poGRider.getErrMsg());
            } else {
                setErrMsg("No record deleted.");
            }
        } else {
            lbResult = true;
        }

        if (!pbWithParent) {
            if (getErrMsg().isEmpty()) {
                poGRider.commitTrans();
            } else {
                poGRider.rollbackTrans();
            }
        }
        return lbResult;
    }

    public boolean SearchDetail(int fnRow, int fnCol, String fsValue, boolean fbSearch, boolean fbByCode) {
        String lsHeader = "";
        String lsColName = "";
        String lsColCrit = "";
        String lsSQL = "";
        JSONObject loJSON;
        ResultSet loRS;
        switch (fnCol) {
            case 3:
                lsHeader = "Barcode»Description»Brand»Unit»Qty. On-Hand»Model»Inv. Type»Stock ID";
                lsColName = "sBarCodex»sDescript»xBrandNme»sMeasurNm»nQtyOnHnd»xModelNme»xInvTypNm»sStockIDx";
                lsColCrit = "a.sBarCodex»a.sDescript»b.sDescript»f.sMeasurNm»e.nQtyOnHnd»c.sDescript»d.sDescript»a.sStockIDx";
                System.out.println("sPOTransx = " + getMaster("sPOTransx"));

                String lsCondition = "";
                if (ItemCount() > 0) {
                    for (int lnCtr = 0; lnCtr < ItemCount(); lnCtr++) {
                        lsCondition += ", " + SQLUtil.toSQL(getDetail(lnCtr, "sStockIDx"));
                    }
                    lsCondition = " AND a.sStockIDx NOT IN (" + lsCondition.substring(2) + ") GROUP BY a.sStockIDx";
                }

                if (getMaster("sPOTransx").equals("")) {
                    lsSQL = MiscUtil.addCondition(getSQ_Stocks(), "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE)) + lsCondition;
                } else {
                    lsSQL = MiscUtil.addCondition(getSQ_Stocks((String) getMaster("sPOTransx")),
                            "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE)) + lsCondition;
                }

//                if (fbByCode){
//                    lsSQL = MiscUtil.addCondition(lsSQL, "a.sBarCodex LIKE " + SQLUtil.toSQL(fsValue));
//                    
//                    loRS = poGRider.executeQuery(lsSQL);
//                    
//                    loJSON = showFXDialog.jsonBrowse(poGRider, loRS, lsHeader, lsColName);
//                }else {
//                    loJSON = showFXDialog.jsonSearch(poGRider, 
//                                                        lsSQL, 
//                                                        fsValue, 
//                                                        lsHeader, 
//                                                        lsColName, 
//                                                        lsColCrit, 
//                                                        fbSearch ? 1 : 5);
//                }
                System.out.println("lsSQL = " + lsSQL);
                loJSON = showFXDialog.jsonSearch(poGRider,
                        lsSQL,
                        fsValue,
                        lsHeader,
                        lsColName,
                        lsColCrit,
                        fbSearch ? 0 : 1);

                if (loJSON != null) {
                    setDetail(fnRow, fnCol, (String) loJSON.get("sStockIDx"));

                    if (loJSON.get("nQuantity") != null) {
                        setDetail(fnRow, "nQuantity", Double.valueOf((String) loJSON.get("nQuantity")));
                        setDetail(fnRow, "dExpiryDt", CommonUtils.toDate((String) loJSON.get("dExpiryDt")));
                        setDetail(fnRow, "nFreightx", Double.valueOf((String) loJSON.get("nFreightx")));
                        setDetail(fnRow, "nUnitPrce", Double.valueOf((String) loJSON.get("xUnitPrce")));
                        setDetail(fnRow, "sBrandNme", (String) loJSON.get("xBrandNme"));
                    }
                    return true;
                } else {
//                    setDetail(fnRow, fnCol, "");
//                    setDetail(fnRow, "sBrandNme", "");
                    return false;
                }
            default:
                return false;
        }
    }
//    
//    public boolean SearchDetail(int fnRow, int fnCol, String fsValue, boolean fbSearch, boolean fbByCode){
//        String lsHeader = "";
//        String lsColName = "";
//        String lsColCrit = "";
//        String lsSQL = "";
//        JSONObject loJSON;
//        ResultSet loRS;
//        switch(fnCol){
//            case 3:
//                lsHeader = "Brand»Description»Unit»Model»Inv. Type»Barcode»Stock ID";
//                lsColName = "xBrandNme»sDescript»sMeasurNm»xModelNme»xInvTypNm»sBarCodex»sStockIDx";
//                lsColCrit = "b.sDescript»a.sDescript»f.sMeasurNm»c.sDescript»d.sDescript»a.sBarCodex»a.sStockIDx";
//                
//                if (getMaster("sPOTransx").equals(""))
//                    lsSQL = MiscUtil.addCondition(getSQ_Stocks(""), "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE));
//                else 
//                    lsSQL = MiscUtil.addCondition(getSQ_Stocks((String) getMaster("sPOTransx")), 
//                                                    "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE));
//                
//                System.out.println(lsSQL);
//                loJSON = showFXDialog.jsonSearch(poGRider, 
//                                                        lsSQL, 
//                                                        fsValue, 
//                                                        lsHeader, 
//                                                        lsColName, 
//                                                        lsColCrit, 
//                                                        fbByCode ? 5 : 1);
//                                
//                if (loJSON != null){
//                    setDetail(fnRow, fnCol, (String) loJSON.get("sStockIDx"));
//                        System.out.println("sBrandNme = " + loJSON.get("xBrandNme"));
//                    
//                        setDetail(fnRow, "sBrandNme", (String) loJSON.get("xBrandNme"));
//                    if (loJSON.get("nQuantity")!=null){
//                        setDetail(fnRow, "nQuantity", Double.valueOf((String)loJSON.get("nQuantity")));
//                        setDetail(fnRow, "dExpiryDt", CommonUtils.toDate((String) loJSON.get("dExpiryDt")));
//                        setDetail(fnRow, "nFreightx", Double.valueOf((String)loJSON.get("nFreightx")));
//                        setDetail(fnRow, "nUnitPrce", Double.valueOf((String)loJSON.get("xUnitPrce")));
//                    }
//                    return true;
//                } else{
//                    setDetail(fnRow, fnCol, "");
//                    setDetail(fnRow, "sBrandNme", "");
//                    return false;
//                }
//            default:
//                return false;
//        }
//    }

    public boolean SearchDetail(int fnRow, String fsCol, String fsValue, boolean fbSearch, boolean fbByCode) {
        return SearchDetail(fnRow, poDetail.getColumn(fsCol), fsValue, fbSearch, fbByCode);
    }

    public void setMaster(int fnCol, Object foData) {
        if (pnEditMode != EditMode.UNKNOWN) {
            // Don't allow specific fields to assign values
            if (!(fnCol == poData.getColumn("sTransNox")
                    || fnCol == poData.getColumn("cTranStat")
                    || fnCol == poData.getColumn("sModified")
                    || fnCol == poData.getColumn("dModified"))) {

                poData.setValue(fnCol, foData);
                MasterRetreived(fnCol);

                if (fnCol == poData.getColumn("nDiscount")
                        || fnCol == poData.getColumn("nAddDiscx")
                        || fnCol == poData.getColumn("nFreightx")) {
                    poData.setTranTotal(computeTotal());
                    poData.setTaxWHeld(computeTaxWHeld());
                    MasterRetreived(6);
                    MasterRetreived(7);
                }
            }
        }
    }

    public void setMaster(String fsCol, Object foData) {
        setMaster(poData.getColumn(fsCol), foData);
    }

    public Object getMaster(int fnCol) {
        if (pnEditMode == EditMode.UNKNOWN) {
            return null;
        } else {
            return poData.getValue(fnCol);
        }
    }

    public Object getMaster(String fsCol) {
        return getMaster(poData.getColumn(fsCol));
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

    public String getSQ_Master() {
        return MiscUtil.makeSelect(new UnitPOReturnMaster());
    }

    private String getSQ_Stocks() {
        String lsSQL = "SELECT "
                + "  a.sStockIDx"
                + ", a.sBarCodex"
                + ", a.sDescript"
                + ", a.sBriefDsc"
                + ", a.sAltBarCd"
                + ", a.sCategCd1"
                + ", a.sCategCd2"
                + ", a.sCategCd3"
                + ", a.sCategCd4"
                + ", a.sBrandCde"
                + ", a.sModelCde"
                + ", a.sColorCde"
                + ", a.sInvTypCd"
                + ", a.nUnitPrce"
                + ", a.nSelPrice"
                + ", a.nDiscLev1"
                + ", a.nDiscLev2"
                + ", a.nDiscLev3"
                + ", a.nDealrDsc"
                + ", a.cComboInv"
                + ", a.cWthPromo"
                + ", a.cSerialze"
                + ", a.cUnitType"
                + ", a.cInvStatx"
                + ", a.sSupersed"
                + ", a.cRecdStat"
                + ", b.sDescript xBrandNme"
                + ", c.sDescript xModelNme"
                + ", d.sDescript xInvTypNm"
                + ", f.sMeasurNm"
                + ", e.nQtyOnHnd"
                + " FROM Inventory a"
                + " LEFT JOIN Brand b"
                + " ON a.sBrandCde = b.sBrandCde"
                + " LEFT JOIN Model c"
                + " ON a.sModelCde = c.sModelCde"
                + " LEFT JOIN Inv_Type d"
                + " ON a.sInvTypCd = d.sInvTypCd"
                + " LEFT JOIN Measure f"
                + " ON a.sMeasurID = f.sMeasurID"
                + ", Inv_Master e"
                + " WHERE a.sStockIDx = e.sStockIDx"
                + " AND e.sBranchCd = " + SQLUtil.toSQL(psBranchCd);
        if (!System.getProperty("store.inventory.type").isEmpty()) {
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sInvTypCd IN " + CommonUtils.getParameter(System.getProperty("store.inventory.type")));
        }

        return lsSQL;
    }

    private String getSQ_Stocks(String fsPOTransx) {

        return "SELECT "
                + "  a.sStockIDx"
                + ", a.sBarCodex"
                + ", a.sDescript"
                + ", a.sBriefDsc"
                + ", a.sAltBarCd"
                + ", a.sCategCd1"
                + ", a.sCategCd2"
                + ", a.sCategCd3"
                + ", a.sCategCd4"
                + ", a.sBrandCde"
                + ", a.sModelCde"
                + ", a.sColorCde"
                + ", a.sInvTypCd"
                + ", a.nUnitPrce"
                + ", a.nSelPrice"
                + ", a.nDiscLev1"
                + ", a.nDiscLev2"
                + ", a.nDiscLev3"
                + ", a.nDealrDsc"
                + ", a.cComboInv"
                + ", a.cWthPromo"
                + ", a.cSerialze"
                + ", a.cUnitType"
                + ", a.cInvStatx"
                + ", a.sSupersed"
                + ", a.cRecdStat"
                + ", b.sDescript xBrandNme"
                + ", c.sDescript xModelNme"
                + ", d.sDescript xInvTypNm"
                + ", f.sMeasurNm"
                + ", g.nQuantity"
                + ", g.dExpiryDt"
                + ", g.nFreightx"
                + ", g.nUnitPrce xUnitPrce"
                + ", e.nQtyOnHnd"
                + " FROM Inventory a"
                + " LEFT JOIN Brand b"
                + " ON a.sBrandCde = b.sBrandCde"
                + " LEFT JOIN Model c"
                + " ON a.sModelCde = c.sModelCde"
                + " LEFT JOIN Inv_Type d"
                + " ON a.sInvTypCd = d.sInvTypCd"
                + " LEFT JOIN Measure f"
                + " ON a.sMeasurID = f.sMeasurID"
                + ", Inv_Master e"
                + ", PO_Receiving_Detail g"
                + ", PO_Receiving_Master h"
                + " WHERE a.sStockIDx = e.sStockIDx"
                + " AND g.sTransNox = h.sTransNox"
                + " AND e.sStockIDx = g.sStockIDx"
                + " AND e.nQtyOnHnd > 0"
                + " AND e.sBranchCd = " + SQLUtil.toSQL(psBranchCd)
                + " AND g.sTransNox = " + SQLUtil.toSQL(fsPOTransx);
    }

    private String getSQ_Detail() {
        return "SELECT"
                + "  a.sTransNox"
                + ", a.nEntryNox"
                + ", a.sStockIDx"
                + ", a.cUnitType"
                + ", a.nQuantity"
                + ", a.nUnitPrce"
                + ", a.nFreightx"
                + ", a.dExpiryDt"
                + ", a.dModified"
                + ", IFNULL(c.sDescript,'') sBrandNme  "
                + " FROM PO_Return_Detail a "
                + "   LEFT JOIN Inventory b  "
                + "       ON a.sStockIDx = b.sStockIDx  "
                + "   LEFT JOIN Brand c  "
                + "      ON b.sBrandCde = c.sBrandCde  "
                + " ORDER BY nEntryNox";
    }

    public int ItemCount() {
        return paDetail.size();
    }

    private Connection setConnection() {
        Connection foConn;

        if (pbWithParent) {
            foConn = (Connection) poGRider.getConnection();
            if (foConn == null) {
                foConn = (Connection) poGRider.doConnect();
            }
        } else {
            foConn = (Connection) poGRider.doConnect();
        }

        return foConn;
    }

    public int getEditMode() {
        return pnEditMode;
    }

    private String getSQ_POReturn() {
        String lsTranStat = String.valueOf(pnTranStat);
        String lsCondition = "";
        if (lsTranStat.length() == 1) {
            lsCondition = "a.cTranStat = " + SQLUtil.toSQL(lsTranStat);
        } else {
            for (int lnCtr = 0; lnCtr <= lsTranStat.length() - 1; lnCtr++) {
                lsCondition = lsCondition + SQLUtil.toSQL(String.valueOf(lsTranStat.charAt(lnCtr))) + ",";
            }
            lsCondition = "(" + lsCondition.substring(0, lsCondition.length() - 1) + ")";
            lsCondition = "a.cTranStat IN " + lsCondition;
        }

        return MiscUtil.addCondition("SELECT "
                + "  a.sTransNox"
                + ", a.sBranchCd"
                + ", DATE_FORMAT(a.dTransact, '%m/%d/%Y') AS dTransact"
                + ", a.sInvTypCd"
                + ", a.nTranTotl"
                + ", b.sBranchNm"
                + ", c.sDescript"
                + ", d.sClientNm"
                + ", a.cTranStat"
                + ", IFNULL(e.sReferNox, '') xReferNox"
                + ", CASE "
                + " WHEN a.cTranStat = '0' THEN 'OPEN'"
                + " WHEN a.cTranStat = '1' THEN 'CLOSED'"
                + " WHEN a.cTranStat = '2' THEN 'POSTED'"
                + " WHEN a.cTranStat = '3' THEN 'CANCELLED'"
                + " WHEN a.cTranStat = '4' THEN 'VOID'"
                + " END AS xTranStat"
                + " FROM PO_Return_Master a"
                + " LEFT JOIN Branch b"
                + " ON a.sBranchCd = b.sBranchCd"
                + " LEFT JOIN Inv_Type c"
                + " ON a.sInvTypCd = c.sInvTypCd"
                + " LEFT JOIN PO_Receiving_Master e"
                + " ON a.sPOTransx = e.sTransNox"
                + ", Client_Master d"
                + " WHERE a.sSupplier = d.sClientID"
                + " AND LEFT(a.sTransNox, 4) = " + SQLUtil.toSQL(poGRider.getBranchCode()), lsCondition);
    }

    public String getSQ_ReturnMaster() {
        return "SELECT"
                + "  sTransNox"
                + ", sBranchCd"
                + ", dTransact"
                + ", sCompnyID"
                + ", sSupplier"
                + ", nTranTotl"
                + ", nVATRatex"
                + ", nTWithHld"
                + ", nDiscount"
                + ", nAddDiscx"
                + ", nFreightx"
                + ", sRemarksx"
                + ", nAmtPaidx"
                + ", sSourceNo"
                + ", sSourceCd"
                + ", sPOTransx"
                + ", nEntryNox"
                + ", sInvTypCd"
                + ", cTranStat"
                + ", sPrepared"
                + ", dPrepared"
                + ", sApproved"
                + ", dApproved"
                + ", sAprvCode"
                + ", sPostedxx"
                + ", dPostedxx"
                + ", sDeptIDxx"
                + ", cDivision"
                + ", sModified"
                + ", dModified"
                + " FROM PO_Return_Master";
    }

    private String getPODetail(String fsOrderNox) {
        return "SELECT "
                + "  a.sTransNox"
                + ", a.nEntryNox"
                + ", a.sStockIDx"
                + ", (a.nQuantity - a.nReceived - a.nCancelld) nQuantity"
                + ", a.nUnitPrce"
                + ", a.nReceived"
                + ", a.nCancelld"
                + ", IFNULL(c.sDescript,'') sBrandNme"
                + " FROM PO_Detail a"
                + " LEFT JOIN Inventory b"
                + "   ON a.sStockIDx = b.sStockIDx"
                + " LEFT JOIN Brand c"
                + "   ON b.sBrandCde = c.sBrandCde"
                + " WHERE a.sTransNox = " + SQLUtil.toSQL(fsOrderNox)
                + " ORDER BY a.nEntryNox";
    }

    public void printColumnsMaster() {
        poData.list();
    }

    public void printColumnsDetail() {
        poDetail.list();
    }

    public void setTranStat(int fnValue) {
        this.pnTranStat = fnValue;
    }

    //callback methods
    public void setCallBack(IMasterDetail foCallBack) {
        poCallBack = foCallBack;
    }

    private void MasterRetreived(int fnRow) {
        if (poCallBack == null) {
            return;
        }

        poCallBack.MasterRetreive(fnRow);
    }

    private void DetailRetreived(int fnRow) {
        if (poCallBack == null) {
            return;
        }

        poCallBack.DetailRetreive(fnRow);
    }

    public void setClientNm(String fsClientNm) {
        this.psClientNm = fsClientNm;
    }

    public Inventory GetInventory(String fsValue, boolean fbByCode, boolean fbSearch) {
        Inventory instance = new Inventory(poGRider, psBranchCd, fbSearch);
        instance.BrowseRecord(fsValue, fbByCode, false);
        return instance;
    }

    public XMPOReceiving GetPOReceving(String fsValue, boolean fbByCode) {
        if (fbByCode && fsValue.equals("")) {
            return null;
        }

        XMPOReceiving instance = new XMPOReceiving(poGRider, psBranchCd, true);
        instance.setTranStat(12);
        if (instance.BrowseRecord(fsValue, fbByCode)) {
            return instance;
        } else {
            return null;
        }
    }

    public XMBranch GetBranch(String fsValue, boolean fbByCode) {
        if (fbByCode && fsValue.equals("")) {
            return null;
        }

        XMBranch instance = new XMBranch(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode)) {
            return instance;
        } else {
            return null;
        }
    }

    public JSONObject GetSupplier(String fsValue, boolean fbByCode) {
        if (fbByCode && fsValue.equals("")) {
            return null;
        }

        XMClient instance = new XMClient(poGRider, psBranchCd, true);
        return instance.SearchClient(fsValue, fbByCode);
    }

    public XMInventoryType GetInventoryType(String fsValue, boolean fbByCode) {
        if (fbByCode && fsValue.equals("")) {
            return null;
        }

        XMInventoryType instance = new XMInventoryType(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode)) {
            return instance;
        } else {
            return null;
        }
    }

    public XMDepartment GetDepartment(String fsValue, boolean fbByCode) {
        if (fbByCode && fsValue.equals("")) {
            return null;
        }

        XMDepartment instance = new XMDepartment(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode)) {
            return instance;
        } else {
            return null;
        }
    }

    public boolean SearchMaster(int fnCol, String fsValue, boolean fbByCode) {
        boolean lbReturn = false;

        switch (fnCol) {
            case 2: //sBranchCd
                XMBranch loBranch = new XMBranch(poGRider, psBranchCd, true);
                if (loBranch.browseRecord(fsValue, fbByCode)) {
                    setMaster(fnCol, (String) loBranch.getMaster("sBranchCd"));
                    setMaster(4, (String) loBranch.getMaster("sCompnyID"));
                    lbReturn = true;
                } else {
                    setMaster(fnCol, "");
                    setMaster(4, "");
                }

                MasterRetreived(fnCol);
                return lbReturn;
            case 5: //sSupplier
                XMSupplier loSupplier = new XMSupplier(poGRider, psBranchCd, true);
                if (loSupplier.browseRecord(fsValue, psBranchCd, fbByCode)) {
                    setMaster(fnCol, loSupplier.getMaster("sClientID"));
                    lbReturn = true;
                } else {
                    setMaster(fnCol, "");
                }

                MasterRetreived(fnCol);
                return lbReturn;
            case 16: //PO Receiving Trans
                POReceiving loPORec = new POReceiving(poGRider, psBranchCd, true);
                loPORec.setTranStat(1);

                if (loPORec.BrowseRecord(fsValue, false)) {
                    setMaster(fnCol, loPORec.getMaster("sTransNox"));
                    setMaster("sBranchCd", loPORec.getMaster("sBranchCd"));
                    setMaster("sDeptIDxx", loPORec.getMaster("sDeptIDxx"));
                    setMaster("cDivision", loPORec.getMaster("cDivision"));
                    setMaster("sCompnyID", loPORec.getMaster("sCompnyID"));
                    setMaster("sInvTypCd", loPORec.getMaster("sInvTypCd"));
                    setMaster("sSupplier", loPORec.getMaster("sSupplier"));
                    lbReturn = true;
                } else {
                    setMaster(fnCol, "");
                    setMaster("sBranchCd", "");
                    setMaster("sDeptIDxx", "");
                    setMaster("cDivision", "3");
                    setMaster("sCompnyID", "");
                    setMaster("sInvTypCd", "");
                    setMaster("sSupplier", "");
                }

                MasterRetreived(fnCol);
                MasterRetreived(2);
                MasterRetreived(5);
                MasterRetreived(18);
                MasterRetreived(27);
                MasterRetreived(28);
                return lbReturn;
            case 18: //sInvTypCd
                XMInventoryType loInv = new XMInventoryType(poGRider, psBranchCd, true);
                if (loInv.browseRecord(fsValue, fbByCode)) {
                    setMaster(fnCol, loInv.getMaster("sInvTypCd"));
                    lbReturn = true;
                } else {
                    setMaster(fnCol, "");
                }

                MasterRetreived(fnCol);
                return lbReturn;
            case 27:
                XMDepartment loDept = new XMDepartment(poGRider, psBranchCd, true);
                if (loDept.browseRecord(fsValue, fbByCode)) {
                    setMaster(fnCol, loDept.getMaster("sDeptIDxx"));
                    lbReturn = true;
                } else {
                    setMaster(fnCol, "");
                }

                MasterRetreived(fnCol);
                return lbReturn;
        }

        return false;
    }

    public boolean SearchMaster(String fsCol, String fsValue, boolean fbByCode) {
        return SearchMaster(poData.getColumn(fsCol), fsValue, fbByCode);
    }

    private void loadOrder(String fsOrderNox) {
        java.sql.Connection loCon = poGRider.getConnection();

        Statement loStmt = null;
        ResultSet loRS = null;

        try {

            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(getPODetail(fsOrderNox));
            while (loRS.next()) {
                setDetail(ItemCount() - 1, "sOrderNox", loRS.getString("sTransNox"));
                setDetail(ItemCount() - 1, "sStockIDx", loRS.getString("sStockIDx"));
                setDetail(ItemCount() - 1, "nUnitPrce", loRS.getDouble("nUnitPrce"));
                setDetail(ItemCount() - 1, "nQuantity", loRS.getDouble("nQuantity"));
                setDetail(ItemCount() - 1, "sBrandNme", loRS.getString("sBrandNme"));
                addDetail();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        } finally {
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
    }

    public void ShowMessageFX() {
        if (!getErrMsg().isEmpty()) {
            if (!getMessage().isEmpty()) {
                ShowMessageFX.Error(getErrMsg(), pxeModuleName, getMessage());
            } else {
                ShowMessageFX.Error(getErrMsg(), pxeModuleName, null);
            }
        } else {
            ShowMessageFX.Information(null, pxeModuleName, getMessage());
        }
    }

    public boolean printRecord() {
        if (pnEditMode != EditMode.READY || poData == null) {
            ShowMessageFX.Warning("Unable to print transaction.", "Warning", "No record loaded.");
            return false;
        }

        String lsSupplier = "";

        ResultSet loRS = poGRider.executeQuery("SELECT sClientNm FROM Client_Master WHERE sClientID = " + SQLUtil.toSQL(poData.getSupplier()));

        try {
            if (loRS.next()) {
                lsSupplier = loRS.getString("sClientNm");
            }

        } catch (SQLException ex) {
            Logger.getLogger(XMPOReturn.class.getName()).log(Level.SEVERE, null, ex);
        }

        //Create the parameter
        Map<String, Object> params = new HashMap<>();
        params.put("sCompnyNm", "Guanzon Group");
        params.put("sBranchNm", poGRider.getBranchName());
        params.put("sAddressx", poGRider.getAddress() + ", " + poGRider.getTownName() + " " + poGRider.getProvince());
        params.put("sTransNox", poData.getTransNox());
        params.put("sReferNox", poData.getPOTrans());
        params.put("xSupplier", lsSupplier);
        params.put("dTransact", SQLUtil.dateFormat(poData.getDateTransact(), SQLUtil.FORMAT_LONG_DATE));
        params.put("sPrintdBy", psClientNm);
        params.put("xRemarksx", poData.getRemarks());

        String lsSQL = "SELECT sClientNm FROM Client_Master WHERE sClientID IN ("
                + "SELECT sEmployNo FROM xxxSysUser WHERE sUserIDxx = " + SQLUtil.toSQL(poData.getApprovedBy().isEmpty() ? poData.getPreparedBy() : poData.getApprovedBy()) + ")";
        loRS = poGRider.executeQuery(lsSQL);

        try {
            if (loRS.next()) {
                params.put("sApprval1", loRS.getString("sClientNm"));
            } else {
                params.put("sApprval1", "");
            }
        } catch (SQLException ex) {
            Logger.getLogger(POReturn.class.getName()).log(Level.SEVERE, null, ex);
        }

        params.put("sApprval2", "");

        JSONObject loJSON;
        JSONArray loArray = new JSONArray();

        String lsBarCodex;
        String lsDescript;
        String lsMeasurex;
        Inventory loInventory = new Inventory(poGRider, psBranchCd, true);

        for (int lnCtr = 0; lnCtr <= ItemCount() - 1; lnCtr++) {
            loInventory.BrowseRecord((String) getDetail(lnCtr, "sStockIDx"), true, false);
            lsBarCodex = (String) loInventory.getMaster("sBarCodex");
            lsDescript = (String) loInventory.getMaster("sDescript");
            lsMeasurex = (String) loInventory.getMeasureMent(loInventory.getMaster("sMeasurID").toString());

            loJSON = new JSONObject();
            loJSON.put("sField01", lsBarCodex);
            loJSON.put("sField02", lsDescript);
            loJSON.put("sField03", lsMeasurex);
            loJSON.put("sField04", getDetail(lnCtr, "sBrandNme"));
            loJSON.put("nField01", getDetail(lnCtr, "nQuantity"));
            loJSON.put("nField02", getDetail(lnCtr, "nUnitPrce"));
            loJSON.put("nField03", (Double.parseDouble(getDetail(lnCtr, "nUnitPrce").toString()) * Double.parseDouble(getDetail(lnCtr, "nQuantity").toString())));
            loArray.add(loJSON);
        }

        try {
            InputStream stream = new ByteArrayInputStream(loArray.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson;

            jrjson = new JsonDataSource(stream);

            JasperPrint jrprint = JasperFillManager.fillReport(poGRider.getReportPath()
                    + "PurchaseReturn.jasper", params, jrjson);

            JasperViewer jv = new JasperViewer(jrprint, false);
            jv.setVisible(true);
        } catch (JRException | UnsupportedEncodingException ex) {
            Logger.getLogger(XMPOReceiving.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
    //Member Variables
    private GRider poGRider = null;
    private String psUserIDxx = "";
    private String psBranchCd = "";
    private String psWarnMsg = "";
    private String psErrMsgx = "";
    private String psClientNm = "";
    private boolean pbWithParent = false;
    private int pnEditMode;
    private int pnTranStat = 0;
    private IMasterDetail poCallBack;

    private final GCrypt poCrypt = new GCrypt();
    private UnitPOReturnMaster poData = new UnitPOReturnMaster();
    private UnitPOReturnDetail poDetail = new UnitPOReturnDetail();
    private ArrayList<UnitPOReturnDetail> paDetail;

    private final Double pxeTaxWHeldRate = 0.00; //0.01
    private final Double pxeTaxRate = 0.12;
    private final Double pxeTaxExcludRte = 1.12;

    private final String pxeModuleName = POReturn.class.getSimpleName();
}
