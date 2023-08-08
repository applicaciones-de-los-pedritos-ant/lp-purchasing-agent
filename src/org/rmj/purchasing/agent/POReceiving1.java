/**
 * Inventory Waste BASE
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
import org.rmj.cas.inventory.base.Inventory;
import org.rmj.cas.inventory.base.InventoryTrans;
import org.rmj.cas.purchasing.pojo.UnitPOReceivingDetail;
import org.rmj.cas.purchasing.pojo.UnitPOReceivingMaster;
import org.rmj.cas.purchasing.pojo.UnitPOReceivingDetailOthers;
import org.rmj.lp.parameter.agent.XMBranch;
import org.rmj.lp.parameter.agent.XMDepartment;
import org.rmj.lp.parameter.agent.XMInventoryType;
import org.rmj.lp.parameter.agent.XMSupplier;
import org.rmj.lp.parameter.agent.XMTerm;

public class POReceiving1{
    private final String MODULENAME = "PurchaseOrder";
    
    public POReceiving1(GRider foGRider, String fsBranchCD, boolean fbWithParent){
        this.poGRider = foGRider;
        
        if (foGRider != null){
            this.pbWithParent = fbWithParent;
            this.psBranchCd = fsBranchCD;
            
            this.psUserIDxx = foGRider.getUserID();
            pnEditMode = EditMode.UNKNOWN;
        }
    }
    
    public boolean BrowseRecord(String fsValue, boolean fbByCode){
        String lsHeader = "Refer Date»Supplier»Refer No»Inv. Type»Date»Trans No";
        String lsColName = "dRefernce»sClientNm»sReferNox»sDescript»dTransact»sTransNox";
        String lsColCrit = "a.dRefernce»d.sClientNm»a.sReferNox»c.sDescript»a.dTransact»a.sTransNox";
        String lsSQL = getSQ_POReceiving();
        JSONObject loJSON;
        
       System.out.println(lsSQL);
        loJSON = showFXDialog.jsonSearch(poGRider, 
                                                    lsSQL, 
                                                    fsValue, 
                                                    lsHeader, 
                                                    lsColName, 
                                                    lsColCrit, 
                                                    fbByCode ? 2 : 1);
        
        if(loJSON == null)
            return false;
        else
            return openTransaction((String) loJSON.get("sTransNox"));
    }
    
    public boolean addDetail() {
        if (paDetail.isEmpty()){
            paDetail.add(new UnitPOReceivingDetail());
            
            paDetailOthers.add(new UnitPOReceivingDetailOthers());
        } else{
            if (!paDetail.get(ItemCount()-1).getStockID().equals("") &&
                    paDetail.get(ItemCount() -1).getQuantity().doubleValue() != 0.00){
                paDetail.add(new UnitPOReceivingDetail());
                
                paDetailOthers.add(new UnitPOReceivingDetailOthers());
            }
        }
        return true;
    }
    public boolean deleteDetail(int fnRow) {
        paDetail.remove(fnRow);
        paDetailOthers.remove(fnRow);
        
        if (paDetail.isEmpty()){
            paDetail.add(new UnitPOReceivingDetail());
            paDetailOthers.add(new UnitPOReceivingDetailOthers());
        }

        poData.setTranTotal(computeTotal());
        poData.setTaxWHeld(computeTaxWHeld());
        return true;
    }
//    
    public void setDetail(int fnRow, int fnCol, Object foData){
        switch (fnCol){
            case 8: //nUnitPrce
            case 9: //nFreightx
                if (foData instanceof Number){
                    paDetail.get(fnRow).setValue(fnCol, foData);
                }else paDetail.get(fnRow).setValue(fnCol, 0);
                
                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(11);
                MasterRetreived(13);
                break;
            case 7: //nQuantity
                if (foData instanceof Number){
                    paDetail.get(fnRow).setValue(fnCol, foData);
                }else paDetail.get(fnRow).setValue(fnCol, 0);
                
                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(11);
                MasterRetreived(13);
                break;
            case 10: //dExpiryDt
                if (foData instanceof Date){
                    paDetail.get(fnRow).setValue(fnCol, foData);
                }else paDetail.get(fnRow).setValue(fnCol, null);
                break;
            case 100: //xBarCodex
            case 101: //xDescript
                if (System.getProperty("store.inventory.strict.type").equals("0")){
                    paDetailOthers.get(fnRow).setValue(fnCol, foData);
                }
                break;
             case 102: //sMeasurNm
                paDetailOthers.get(fnRow).setValue("sMeasurNm", foData);
                break;
            default:
                paDetail.get(fnRow).setValue(fnCol, foData);
        }
       
        DetailRetreived(fnCol);
    }
    public void setDetail(int fnRow, String fsCol, Object foData){
        switch(fsCol){
            case "nUnitPrce":
            case "nFreightx":
                if (foData instanceof Number){
                    paDetail.get(fnRow).setValue(fsCol, foData);
                }else paDetail.get(fnRow).setValue(fsCol, 0);
                
                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(11);
                MasterRetreived(13);
                break;
            case "nQuantity":
                if (foData instanceof Number){
                    paDetail.get(fnRow).setValue(fsCol, foData);
                }else paDetail.get(fnRow).setValue(fsCol, 0);
                poData.setTranTotal(computeTotal());
                poData.setTaxWHeld(computeTaxWHeld());
                MasterRetreived(11);
                MasterRetreived(13);
                break;
            case "dExpiryDt":
                if (foData instanceof Date){
                    paDetail.get(fnRow).setValue(fsCol, foData);
                }else paDetail.get(fnRow).setValue(fsCol, null);
                break;
            case "xBarCodex":
                setDetail(fnRow, 100, foData);
                break;
            case "xDescript":
                setDetail(fnRow, 101, foData);
                break;
            case "sMeasurNm":
                setDetail(fnRow, 102, foData);
                break;
            default:
                paDetail.get(fnRow).setValue(fsCol, foData);
        }
        
            
        
    }
    
    public Object getDetail(int fnRow, int fnCol){
        switch(fnCol){
            case 100:
                return paDetailOthers.get(fnRow).getValue("xBarCodex");
            case 101:
            case 102:
                return paDetailOthers.get(fnRow).getValue("sMeasurNm");
            default:
                return paDetail.get(fnRow).getValue(fnCol);
                
        }
    }
    
    public Object getDetail(int fnRow, String fsCol){
        switch(fsCol){
            case "xBarCodex":
                return getDetail(fnRow, 100);
            case "xDescript":
                return getDetail(fnRow, 101);
            case "sMeasurNm":
                return getDetail(fnRow, 102);
            default:
                return paDetail.get(fnRow).getValue(fsCol);
        }       
    }
    
    public Object getDetailOthers(int fnRow, String fsCol){
        switch(fsCol){
            case "sStockIDx":
            case "xBarCodex":
            case "xDescript":
            case "xQtyOnHnd":
            case "sMeasurNm":
                return paDetailOthers.get(fnRow).getValue(fsCol);
            default:
                return null;
        }
    }
    private UnitPOReceivingDetail loadInvDetail(String fsTransNox) throws SQLException{
         UnitPOReceivingDetail loObj = null;
         System.out.println(MiscUtil.addCondition(getSQ_Stock(), 
                                                    "a.sStockIDx = " + SQLUtil.toSQL(fsTransNox)));
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Stock(), 
                                                    "a.sStockIDx = " + SQLUtil.toSQL(fsTransNox)));
        
        if (!loRS.next()){
            setMessage("No Record Found");
        }else{
            //load each column to the entity
            loObj = new UnitPOReceivingDetail();
            for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                if(lnCol<=9){
                    if(lnCol == 2){
                        loObj.setValue(lnCol, 1);
                    }else loObj.setValue(lnCol, loRS.getObject(lnCol));
                    
                }else if(lnCol >= 10 && lnCol <=11){
                        loObj.setValue(lnCol, poGRider.getServerDate());
                }else if(lnCol == 19){
                        loObj.setValue(12, loRS.getObject(lnCol));
                }
            }
        }      
        return loObj;
    }
                         
    private double computeUnitPrice(UnitPOReceivingDetail loDetail) {
        try{
            UnitPOReceivingDetail loOldDet = loadInvDetail(loDetail.getStockID());
            double lnQty = Double.parseDouble(loDetail.getQuantity().toString());
            double lnQty1 = Double.parseDouble(loOldDet.getQuantity().toString());
            double lnValue = Double.parseDouble(loDetail.getUnitPrice().toString());
            double lnValue1 =  Double.parseDouble(loOldDet.getUnitPrice().toString());
            System.out.println("new qty = " + lnQty);
            System.out.println("old qty= " + lnQty1);
            System.out.println("new price = " + lnValue);
            System.out.println("old price= " + lnValue1);
            System.out.println("new = " + (lnValue * lnQty));
            System.out.println("old = " + (lnValue1 * lnQty1));
            if(Double.parseDouble(loOldDet.getUnitPrice().toString())> 0){
                if(!loDetail.getUnitPrice().equals(loOldDet.getUnitPrice())){
                    double avgCost = ((lnValue * lnQty) + (lnValue1 * lnQty1)) / (lnQty + lnQty1);
//                    poDetail.get(lnRow).setUnitPrice(avgCost);
                    
                    return avgCost;
                }
            }
        }catch(SQLException e){
            
        }
        
        return 0.00;
    }
    private double computeTotal(){
        double lnTranTotal = 0;
        for (int lnCtr = 0; lnCtr <= ItemCount()-1; lnCtr ++){
            lnTranTotal += (Double.valueOf(getDetail(lnCtr, "nQuantity").toString()) * Double.valueOf(getDetail(lnCtr, "nUnitPrce").toString())) 
                                + Double.valueOf(getDetail(lnCtr, "nFreightx").toString());
        }
        
        //add the freight charge to total order
        lnTranTotal += Double.valueOf(poData.getFreightCharge().toString());
        //less the  
        lnTranTotal = lnTranTotal - (lnTranTotal * Double.valueOf(poData.getDiscountRate().toString())) - Double.valueOf(poData.getAdditionalDisc().toString());
        return lnTranTotal;
    }
    
    private double computeTaxWHeld(){
        DecimalFormat df2 = new DecimalFormat(".##");
        String lsTaxWHeld = df2.format((Double.valueOf(poData.getTranTotal().toString()) / pxeTaxExcludRte) * pxeTaxWHeldRate);
        return Double.parseDouble(lsTaxWHeld);        
    }
    
    public boolean newTransaction() {
        Connection loConn = null;
        loConn = setConnection();
        
        poData = new UnitPOReceivingMaster();
        poData.setTransNox(MiscUtil.getNextCode(poData.getTable(), "sTransNox", true, loConn, psBranchCd));
        poData.setDateTransact(poGRider.getServerDate());
        
        //init detail
        paDetail = new ArrayList<>();      
        paDetailOthers = new ArrayList<>(); //detail other info storage
        
        pnEditMode = EditMode.ADDNEW;
        
        addDetail();
        return true;
    }
    
    public boolean openTransaction(String fsTransNox){
        poData = loadTransaction(fsTransNox);
        
        if (poData != null) 
            paDetail = loadTransactionDetail(fsTransNox);
        else{
            setMessage("Unable to load transaction.");
            return false;
        } 
            
        pnEditMode = EditMode.READY;
        return true;
    }

    public UnitPOReceivingMaster loadTransaction(String fsTransNox) {
        UnitPOReceivingMaster loObject = new UnitPOReceivingMaster();
        
        Connection loConn = null;
        loConn = setConnection();   
        
        String lsSQL = MiscUtil.addCondition(getSQ_ReceivingMaster(), "sTransNox = " + SQLUtil.toSQL(fsTransNox));
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        
        try {
            if (!loRS.next()){
                setMessage("No Record Found");
            }else{
                //load each column to the entity
                for(int lnCol=1; lnCol<=loRS.getMetaData().getColumnCount(); lnCol++){
                    loObject.setValue(lnCol, loRS.getObject(lnCol));
                }
            }              
        } catch (SQLException ex) {
            setErrMsg(ex.getMessage());
        } finally{
            MiscUtil.close(loRS);
            if (!pbWithParent) MiscUtil.close(loConn);
        }
        
        return loObject;
    }
    
    public ResultSet getExpiration(String fsStockIDx){
        String lsSQL = "SELECT * FROM Inv_Master_Expiration" +
                        " WHERE sStockIDx = " + SQLUtil.toSQL(fsStockIDx) +
                            " AND sBranchCd = " + SQLUtil.toSQL(psBranchCd) +
                            " AND nQtyOnHnd > 0" +
                        " ORDER BY dExpiryDt";     
        
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        
        return loRS;
    }
    
    private ArrayList<UnitPOReceivingDetail> loadTransactionDetail(String fsTransNox){
        UnitPOReceivingDetail loOcc = null;
        UnitPOReceivingDetailOthers loOth = null;
        Connection loConn = null;
        loConn = setConnection();
        
        ArrayList<UnitPOReceivingDetail> loDetail = new ArrayList<>();
        paDetailOthers = new ArrayList<>(); //reset detail others
        
        ResultSet loRS = poGRider.executeQuery(
                            MiscUtil.addCondition(getSQ_Detail(), 
                                                    "sTransNox = " + SQLUtil.toSQL(fsTransNox)));
        try {
            for (int lnCtr = 1; lnCtr <= MiscUtil.RecordCount(loRS); lnCtr ++){
                loRS.absolute(lnCtr);

                loOcc = new UnitPOReceivingDetail();
                loOcc.setValue("sTransNox", loRS.getObject("sTransNox"));        
                loOcc.setValue("nEntryNox", loRS.getObject("nEntryNox"));
                loOcc.setValue("sOrderNox", loRS.getObject("sOrderNox"));
                loOcc.setValue("sStockIDx", loRS.getObject("sStockIDx"));
                loOcc.setValue("sReplacID", loRS.getObject("sReplacID"));
                loOcc.setValue("cUnitType", loRS.getObject("cUnitType"));
                loOcc.setValue("nQuantity", loRS.getObject("nQuantity"));
                loOcc.setValue("nUnitPrce", loRS.getObject("nUnitPrce"));
                loOcc.setValue("nFreightx", loRS.getObject("nFreightx"));
                loOcc.setValue("dExpiryDt", loRS.getObject("dExpiryDt"));
                loOcc.setValue("dModified", loRS.getObject("dModified"));
                loOcc.setValue("sBrandNme", loRS.getObject("sBrandNme"));
            
                loDetail.add(loOcc);
                
                loOth = new UnitPOReceivingDetailOthers();
                loOth.setValue("sStockIDx", loRS.getObject("sStockIDx"));
                loOth.setValue("nQtyOnHnd", loRS.getObject("nQtyOnHnd"));
                loOth.setValue("xQtyOnHnd", loRS.getObject("xQtyOnHnd"));
                loOth.setValue("nResvOrdr", loRS.getObject("nResvOrdr"));
                loOth.setValue("nBackOrdr", loRS.getObject("nBackOrdr"));
                loOth.setValue("nReorderx", 0);
                loOth.setValue("nLedgerNo", loRS.getInt("nLedgerNo"));
                if (loRS.getString("sMeasurNm")!=null){
                   loOth.setValue("sMeasurNm", loRS.getString("sMeasurNm"));
                }else{
                   loOth.setValue("sMeasurNm", "");
                }
                paDetailOthers.add(loOth);
            }
        } catch (SQLException e) {
            //log error message
            return null;
        }        
        
        return loDetail;
    }
    
    public boolean updateRecord() {
        if(pnEditMode != EditMode.READY) {
         return false;
      }
      else{
         pnEditMode = EditMode.UPDATE;
         return true;
      }
    }

    public boolean saveTransaction() {
        String lsSQL = "";
        boolean lbUpdate = false;
        
        UnitPOReceivingMaster loOldEnt = null;
        UnitPOReceivingMaster loNewEnt = null;
        UnitPOReceivingMaster loResult = null;
        
        // Check for the value of foEntity
        if (!(poData instanceof UnitPOReceivingMaster)) {
            setErrMsg("Invalid Entity Passed as Parameter");
            return false;
        }
        
        // Typecast the Entity to this object
        loNewEnt = (UnitPOReceivingMaster) poData;        
               
        if (!pbWithParent) poGRider.beginTrans();
        
        //delete empty detail
        if (paDetail.get(ItemCount()-1).getStockID().equals("")) deleteDetail(ItemCount()-1);
        
        // Generate the SQL Statement
        if (pnEditMode == EditMode.ADDNEW){
            Connection loConn = null;
            loConn = setConnection();

            String lsTransNox = MiscUtil.getNextCode(loNewEnt.getTable(), "sTransNox", true, loConn, psBranchCd);

            loNewEnt.setTransNox(lsTransNox);
            loNewEnt.setEntryNox(ItemCount());
            loNewEnt.setModifiedBy(poGRider.getUserID());
            loNewEnt.setDateModified(poGRider.getServerDate());

            if (!pbWithParent) MiscUtil.close(loConn);

            lbUpdate = saveDetail(loNewEnt.getTransNox());
            if (!lbUpdate) lsSQL = "";
            else lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt);
        }else{
            //Load previous transaction
            loOldEnt = loadTransaction(poData.getTransNox());

            loNewEnt.setEntryNox(ItemCount());
            loNewEnt.setDateModified(poGRider.getServerDate());

            lbUpdate = saveDetail(loNewEnt.getTransNox());
            if (!lbUpdate) lsSQL = "";            
            else lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, (GEntity) loOldEnt, "sTransNox = " + SQLUtil.toSQL(loNewEnt.getValue(1)));
        }
                
        if (!lsSQL.equals("") && getErrMsg().isEmpty()){
            if(poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0){
                if(!poGRider.getErrMsg().isEmpty())
                    setErrMsg(poGRider.getErrMsg());
                else 
                    setMessage("No record updated");
            } 
            lbUpdate = true;
        }
        
        if (!pbWithParent) {
            if (!getErrMsg().isEmpty()){
                poGRider.rollbackTrans();
            } else poGRider.commitTrans();
        }        
        
        return lbUpdate;
    }
    
    private boolean saveDetail(String fsTransNox){
        setMessage("");
        if (paDetail.isEmpty()){
            setMessage("Unable to save empty detail transaction.");
            return false;
        } 
        else if (paDetail.get(0).getStockID().equals("") ||
                paDetail.get(0).getQuantity().doubleValue() == 0.00){
            setMessage("Detail might not have item or zero quantity.");
            return false;
        }
        
        int lnCtr;
        String lsSQL;
        UnitPOReceivingDetail loNewEnt = null;
        
        if (pnEditMode == EditMode.ADDNEW){
            Connection loConn = null;
            loConn = setConnection();  
            
            for (lnCtr = 0; lnCtr <= paDetail.size() -1; lnCtr++){
                loNewEnt = paDetail.get(lnCtr);
                
                if (!loNewEnt.getStockID().equals("")){
                    loNewEnt.setTransNox(fsTransNox);
                    loNewEnt.setEntryNox(lnCtr + 1);
                    loNewEnt.setDateModified(poGRider.getServerDate());

                    lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, "sBrandNme");

                    if (!lsSQL.equals("")){
                        if(poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0){
                            if(!poGRider.getErrMsg().isEmpty()){
                                setErrMsg(poGRider.getErrMsg());
                                return false;
                            }
                        } 
                    }
                }
            }
        } else{
            ArrayList<UnitPOReceivingDetail> laSubUnit = loadTransactionDetail(poData.getTransNox());
            
            for (lnCtr = 0; lnCtr <= paDetail.size()-1; lnCtr++){
                loNewEnt = paDetail.get(lnCtr);
                
                if (!loNewEnt.getStockID().equals("")){
                    if (lnCtr <= laSubUnit.size()-1){
                        if (loNewEnt.getEntryNox() != lnCtr+1) loNewEnt.setEntryNox(lnCtr+1);
                        
                        lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt, 
                                                (GEntity) laSubUnit.get(lnCtr), 
                                                " nEntryNox = " + SQLUtil.toSQL(loNewEnt.getValue(2)) + 
                                                " AND sTransNox = " + SQLUtil.toSQL(loNewEnt.getValue(1)),
                                                "sBrandNme");

                    } else{
                        loNewEnt.setStockID(fsTransNox);
                        loNewEnt.setEntryNox(lnCtr + 1);
                        loNewEnt.setDateModified(poGRider.getServerDate());
                        lsSQL = MiscUtil.makeSQL((GEntity) loNewEnt,"sBrandNme");
                    }
                    
                    if (!lsSQL.equals("")){
                        if(poGRider.executeQuery(lsSQL, loNewEnt.getTable(), "", "") == 0){
                            if(!poGRider.getErrMsg().isEmpty()){
                                setErrMsg(poGRider.getErrMsg());
                                return false;
                            }
                        } 
                    }
                } else{
                    for(int lnCtr2 = lnCtr; lnCtr2 <= laSubUnit.size()-1; lnCtr2++){
                        lsSQL = "DELETE FROM " + poDetail.getTable()+
                                " WHERE sStockIDx = " + SQLUtil.toSQL(laSubUnit.get(lnCtr2).getStockID()) +
                                    " AND nEntryNox = " + SQLUtil.toSQL(laSubUnit.get(lnCtr2).getEntryNox());

                        if (!lsSQL.equals("")){
                            if(poGRider.executeQuery(lsSQL, poDetail.getTable(), "", "") == 0){
                                if(!poGRider.getErrMsg().isEmpty()){
                                    setErrMsg(poGRider.getErrMsg());
                                    return false;
                                }
                            } 
                        }
                    }
                    break;
                }
            }
        }

        return true;
    }

    public boolean deleteTransaction(String string) {
        UnitPOReceivingMaster loObject = loadTransaction(string);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        String lsSQL = "DELETE FROM " + loObject.getTable() + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(string);
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        //delete detail rows
        lsSQL = "DELETE FROM " + poDetail.getTable() +
                " WHERE sTransNox = " + SQLUtil.toSQL(string);
        
        if (poGRider.executeQuery(lsSQL, poDetail.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        
        return lbResult;
    }
    
    public boolean closeTransaction(String fsTransNox, String fsApprovalCode) {
        UnitPOReceivingMaster loObject = loadTransaction(fsTransNox);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (fsApprovalCode == null || fsApprovalCode.isEmpty()){
            setMessage("Invalid/No approval code detected.");
            return lbResult;
        }
        
        if (poGRider.getUserLevel() < UserRight.SUPERVISOR){
            setMessage("User is not allowed confirming transaction.");
            return lbResult;
        }
        
        
        if (!loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_OPEN)){
            setMessage("Unable to close closed/cancelled/posted/voided transaction.");
            return lbResult;
        }
         if(pnEditMode != EditMode.READY){
            return false;
        } 
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CLOSED) + 
                            ", sApproved = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dApproved = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                            ", sAprvCode = " + SQLUtil.toSQL(fsApprovalCode) +
                            ", sModified = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = saveInvTrans(loObject.getTransNox(), loObject.getSupplier(),loObject.getDateTransact());
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }


    public boolean postTransaction(String string) {
        if (poGRider.getUserLevel() <= UserRight.ENCODER){
            JSONObject loJSON = showFXDialog.getApproval(poGRider);
            
            if (loJSON == null){
                ShowMessageFX.Warning("Approval failed.", pxeModuleName, "Unable to post transaction");
            }
            
            if ((int) loJSON.get("nUserLevl") <= UserRight.ENCODER){
                ShowMessageFX.Warning("User account has no right to approve.", pxeModuleName, "Unable to post transaction");
                return false;
            }
        }
        
        UnitPOReceivingMaster loObject = loadTransaction(string);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to close proccesed transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_POSTED) + 
                            ", sModified = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }

    public boolean voidTransaction(String string) {
        UnitPOReceivingMaster loObject = loadTransaction(string);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to close processed transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_VOID) + 
                            ", sModified = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }

    public boolean cancelTransaction(String string) {
        UnitPOReceivingMaster loObject = loadTransaction(string);
        boolean lbResult = false;
        
        if (loObject == null){
            setMessage("No record found...");
            return lbResult;
        }
        
        if (loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_POSTED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_CANCELLED) ||
                loObject.getTranStat().equalsIgnoreCase(TransactionStatus.STATE_VOID)){
            setMessage("Unable to close processed transaction.");
            return lbResult;
        }
        
        String lsSQL = "UPDATE " + loObject.getTable() + 
                        " SET  cTranStat = " + SQLUtil.toSQL(TransactionStatus.STATE_CANCELLED) + 
                            ", sModified = " + SQLUtil.toSQL(psUserIDxx) +
                            ", dModified = " + SQLUtil.toSQL(poGRider.getServerDate()) + 
                        " WHERE sTransNox = " + SQLUtil.toSQL(loObject.getTransNox());
        
        if (!pbWithParent) poGRider.beginTrans();
        
        if (poGRider.executeQuery(lsSQL, loObject.getTable(), "", "") == 0){
            if (!poGRider.getErrMsg().isEmpty()){
                setErrMsg(poGRider.getErrMsg());
            } else setErrMsg("No record deleted.");  
        } else lbResult = true;
        
        if (!pbWithParent){
            if (getErrMsg().isEmpty()){
                poGRider.commitTrans();
            } else poGRider.rollbackTrans();
        }
        return lbResult;
    }
    
    
    //Added methods
    private boolean saveInvAvgCost(){
        String lsStockID = "";
        String lsSQL = "";
        
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();
        
        
        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr ++){
            if (paDetail.get(lnCtr).getStockID().equals("")) break;
            
            lsStockID = paDetail.get(lnCtr).getStockID();
            if (!lsStockID.equals("")) {
                lsSQL = "UPDATE Inv_Master SET" +
                            "  nAvgCostx = + " + computeUnitPrice(paDetail.get(lnCtr)) +
                        " WHERE sStockIDx = " + SQLUtil.toSQL(lsStockID)+
                            " AND sBranchCd = " + SQLUtil.toSQL(psBranchCd) ;
                
                if (poGRider.executeQuery(lsSQL, "Inv_Master", psBranchCd, "") <= 0){
                    setMessage("Unable to update inventory average cost.");
                    return false;
                }
            }  
        }
        

        return true;
    }
    
    private boolean saveInvTrans(String fsTransNox, String fsSupplier, Date fdTransact){
        String lsOrderNo = "";
        String lsSQL = "";
        
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();
        
        
        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr ++){
            if (paDetail.get(lnCtr).getStockID().equals("")) break;
            loInvTrans.setDetail(lnCtr, "sStockIDx", paDetail.get(lnCtr).getStockID());
            loInvTrans.setDetail(lnCtr, "sReplacID", paDetail.get(lnCtr).getReplaceID());
            loInvTrans.setDetail(lnCtr, "nQuantity", paDetail.get(lnCtr).getQuantity());
            loInvTrans.setDetail(lnCtr, "nPurchase", paDetail.get(lnCtr).getUnitPrice());
            loInvTrans.setDetail(lnCtr, "nQtyOnHnd", paDetailOthers.get(lnCtr).getValue("nQtyOnHnd"));
            loInvTrans.setDetail(lnCtr, "nResvOrdr", paDetailOthers.get(lnCtr).getValue("nResvOrdr"));
            loInvTrans.setDetail(lnCtr, "nBackOrdr", paDetailOthers.get(lnCtr).getValue("nBackOrdr"));
            loInvTrans.setDetail(lnCtr, "nLedgerNo", paDetailOthers.get(lnCtr).getValue("nLedgerNo"));
            loInvTrans.setDetail(lnCtr, "dExpiryDt", paDetail.get(lnCtr).getDateExpiry());
            
            lsOrderNo = paDetail.get(lnCtr).getOrderNox();
            
            if (!lsOrderNo.equals("")) {
                lsSQL = "UPDATE PO_Detail SET" +
                            "  nReceived = nReceived + " + paDetail.get(lnCtr).getQuantity() +
                        " WHERE sTransNox = " + SQLUtil.toSQL(lsOrderNo) +
                            " AND nEntryNox = " + paDetail.get(lnCtr).getEntryNox();
                
                if (poGRider.executeQuery(lsSQL, "PO_Detail", psBranchCd, "") <= 0){
                    setMessage("Unable to update order reference.");
                    return false;
                }
            }  
        }
        
        if (!loInvTrans.POReceiving(fsTransNox, fdTransact, fsSupplier, EditMode.ADDNEW)){
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }

        return saveInvExpiration(fdTransact);
    }
    
    private boolean unsaveInvTrans(String fsTransNox, String fsSupplier){
        String lsOrderNo = "";
        String lsSQL = "";
        
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();
        
        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr ++){
            loInvTrans.setDetail(lnCtr, "sStockIDx", paDetail.get(lnCtr).getStockID());
            loInvTrans.setDetail(lnCtr, "nQtyOnHnd", paDetailOthers.get(lnCtr).getValue("nQtyOnHnd"));
            loInvTrans.setDetail(lnCtr, "nResvOrdr", paDetailOthers.get(lnCtr).getValue("nResvOrdr"));
            loInvTrans.setDetail(lnCtr, "nBackOrdr", paDetailOthers.get(lnCtr).getValue("nBackOrdr"));
            loInvTrans.setDetail(lnCtr, "nLedgerNo", paDetailOthers.get(lnCtr).getValue("nLedgerNo"));
        
            lsOrderNo = paDetail.get(lnCtr).getOrderNox();
            
            if (!lsOrderNo.equals("")) {
                lsSQL = "UPDATE PO_Detail SET" +
                            "  nReceived = nReceived - " + paDetail.get(lnCtr).getQuantity() +
                        " WHERE sTransNox = " + SQLUtil.toSQL(lsOrderNo) +
                            " AND nEntryNox = " + paDetail.get(lnCtr).getEntryNox();
                
                if (poGRider.executeQuery(lsSQL, "PO_Detail", psBranchCd, "") <= 0){
                    setMessage("Unable to update order reference.");
                    return false;
                }
            }  
        }
        
        if (!loInvTrans.POReceiving(fsTransNox, poGRider.getServerDate(), fsSupplier, EditMode.DELETE)){
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }

        return true;
    }
    
    private boolean saveInvExpiration(Date fdTransact){
        InvExpiration loInvTrans = new InvExpiration(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();
        
        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr ++){
            if (paDetail.get(lnCtr).getStockID().equals("")) break;
            loInvTrans.setDetail(lnCtr, "sStockIDx", paDetail.get(lnCtr).getStockID());
            loInvTrans.setDetail(lnCtr, "dExpiryDt", paDetail.get(lnCtr).getDateExpiry());
            loInvTrans.setDetail(lnCtr, "nQtyInxxx", paDetail.get(lnCtr).getQuantity());
        }
        
        if (!loInvTrans.POReceiving(fdTransact, EditMode.ADDNEW)){
            setMessage(loInvTrans.getMessage());
            setErrMsg(loInvTrans.getErrMsg());
            return false;
        }

        return saveInvUnitPrice();
    }
    
    //Added methods
    private boolean saveInvUnitPrice(){
        String lsBarCode = "";
        String lsStockID = "";
        String lsSQL = "";
        Inventory loInventory;
        InventoryTrans loInvTrans = new InventoryTrans(poGRider, poGRider.getBranchCode());
        loInvTrans.InitTransaction();
        
        
        for (int lnCtr = 0; lnCtr <= paDetail.size() - 1; lnCtr ++){
            if (paDetail.get(lnCtr).getStockID().equals("")) break;
            
            
            lsStockID = paDetail.get(lnCtr).getStockID();
//            lsBarCode = paDetailOthers.get(lnCtr).getValue("xBarCodex").toString();
//            lsBarCode = getDetail(lnCtr, 100).toString();
            
            if (!lsStockID.equals("")) {
                loInventory =  GetInventory(lsStockID, true, false);
                lsBarCode = loInventory.getMaster("sBarCodex").toString();
                lsSQL = "UPDATE Inventory SET" +
                            "  nUnitPrce = " + paDetail.get(lnCtr).getUnitPrice()+
                        " WHERE sStockIDx = " + SQLUtil.toSQL(lsStockID) +
                            " AND sBarCodex = " + SQLUtil.toSQL(lsBarCode) ;
                
                if (poGRider.executeQuery(lsSQL, "Inventory", psBranchCd, "") <= 0){
                    setMessage("Unable to update inventory unit price.");
                    return false;
                }
            }  
        }
        

        return saveInvAvgCost();
    }
    
    public boolean SearchDetail(int fnRow, int fnCol, String fsValue, boolean fbSearch, boolean fbByCode){
        String lsHeader = "";
        String lsColName = "";
        String lsColCrit = "";
        String lsSQL = "";
        JSONObject loJSON;
        ResultSet loRS;
        
        setErrMsg("");
        setMessage("");
        
        switch(fnCol){
             case 3:
                lsHeader = "Order No»Supplier»Refer No»Date";
                lsColName = "sTransNox»sClientNm»sReferNox»dTransact";
                lsColCrit = "a.sTransNox»d.sClientNm»a.sReferNox»a.dTransact";
                lsSQL = getSQ_Purchases();
                
                loJSON = showFXDialog.jsonSearch(poGRider, 
                                                    lsSQL, 
                                                    fsValue, 
                                                    lsHeader, 
                                                    lsColName, 
                                                    lsColCrit, 
                                                    fbByCode ? 0 : 2);
                
                if (loJSON != null){
                    setDetail(fnRow, fnCol, (String) loJSON.get("sTransNox"));                    
                    return true;
                } else{
                    setDetail(fnRow, fnCol, "");
                    return false;
                }
            case 4:
            case 5:
                lsHeader = "Brand»Description»Unit»Model»Inv. Type»Barcode»Stock ID";
                lsColName = "xBrandNme»sDescript»sMeasurNm»xModelNme»xInvTypNm»sBarCodex»sStockIDx";
                lsColCrit = "b.sDescript»a.sDescript»f.sMeasurNm»c.sDescript»d.sDescript»a.sBarCodex»a.sStockIDx";
                
                if (getDetail(fnRow, "sOrderNox").equals(""))
                    lsSQL = MiscUtil.addCondition(getSQ_Inventory(), "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE));
                else 
                    lsSQL = MiscUtil.addCondition(getSQ_Stocks((String) getDetail(fnRow, "sOrderNox")), "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE));
                
                if (fbByCode){
                    lsSQL = MiscUtil.addCondition(lsSQL, "a.sStockIDx = " + SQLUtil.toSQL(fsValue));
                    
                    loRS = poGRider.executeQuery(lsSQL);
                    
                    loJSON = showFXDialog.jsonBrowse(poGRider, loRS, lsHeader, lsColName);
                }else {
                    loJSON = showFXDialog.jsonSearch(poGRider, 
                                                        lsSQL, 
                                                        fsValue, 
                                                        lsHeader, 
                                                        lsColName, 
                                                        lsColCrit, 
                                                        fbSearch ? 1 : 5);
                }
                                
                if (loJSON != null){
                    setDetail(fnRow, fnCol, (String) loJSON.get("sStockIDx"));
                    //delete the barcode and descript on temp table
                    setDetail(fnRow, 100, (String) loJSON.get("sBarCodex"));
                    setDetail(fnRow, 101, "");
                    setDetail(fnRow, 102, "");
                    
                    setDetail(fnRow, "sBrandNme", (String) loJSON.get("xBrandNme"));
                    setDetail(fnRow, "sMeasurNm",loJSON.get("sMeasurNm"));
                    if (fnCol == 4) setDetail(fnRow, "nUnitPrce", Double.valueOf((String)loJSON.get("nUnitPrce")));
                    if (loJSON.get("nQuantity") != null) setDetail(fnRow, 7, Double.valueOf((String)loJSON.get("nQuantity")));
                    return true;
                } else{
                    setDetail(fnRow, fnCol, "");
                    //delete the barcode and descript on temp table
                    setDetail(fnRow, 100, "");
                    setDetail(fnRow, 101, "");
                    setDetail(fnRow, 102, "");
                    setDetail(fnRow, "sBrandNme", "");
                    
                    if (fnCol == 4)
                        setDetail(fnRow, "nUnitPrce", 0.00);
                    return false;
                }
            default:
                return false;
        }
    }
    
    public boolean SearchDetail(int fnRow, String fsCol, String fsValue, boolean fbSearch, boolean fbByCode){
        return SearchDetail(fnRow, poDetail.getColumn(fsCol), fsValue, fbSearch, fbByCode);
    }
    public void setMaster(int fnCol, Object foData) {
        if (pnEditMode != EditMode.UNKNOWN){
            // Don't allow specific fields to assign values
            if(!(fnCol == poData.getColumn("sTransNox") ||
                fnCol == poData.getColumn("cTranStat") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){
                
                poData.setValue(fnCol, foData);
                MasterRetreived(fnCol);
                
                
                if (fnCol == poData.getColumn("nDiscount") ||
                    fnCol == poData.getColumn("nAddDiscx") ||
                    fnCol == poData.getColumn("nFreightx")){
                    poData.setTranTotal(computeTotal());
                    poData.setTaxWHeld(computeTaxWHeld());
                    
                    MasterRetreived(11);
                    MasterRetreived(13);
                }
            }
        }
    }

    public void setMaster(String fsCol, Object foData) {
        setMaster(poData.getColumn(fsCol), foData);
    }

    public Object getMaster(int fnCol) {
        if(pnEditMode == EditMode.UNKNOWN)
            return null;
        else{
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
        return MiscUtil.makeSelect(new UnitPOReceivingMaster());
    }
    private String getSQ_Inventory(){
        String lsSQL = "SELECT " +
                    "  a.sStockIDx" +
                    ", a.sBarCodex" + 
                    ", a.sDescript" + 
                    ", a.sBriefDsc" + 
                    ", a.sAltBarCd" + 
                    ", a.sCategCd1" + 
                    ", a.sCategCd2" + 
                    ", a.sCategCd3" + 
                    ", a.sCategCd4" + 
                    ", a.sBrandCde" + 
                    ", a.sModelCde" + 
                    ", a.sColorCde" + 
                    ", a.sInvTypCd" + 
                    ", a.nUnitPrce" + 
                    ", a.nSelPrice" + 
                    ", a.nDiscLev1" + 
                    ", a.nDiscLev2" + 
                    ", a.nDiscLev3" + 
                    ", a.nDealrDsc" + 
                    ", a.cComboInv" + 
                    ", a.cWthPromo" + 
                    ", a.cSerialze" + 
                    ", a.cUnitType" + 
                    ", a.cInvStatx" + 
                    ", a.sSupersed" + 
                    ", a.cRecdStat" + 
                    ", b.sDescript xBrandNme" + 
                    ", c.sDescript xModelNme" + 
                    ", d.sDescript xInvTypNm" +
                    ", f.sMeasurNm" +
                " FROM Inventory a" + 
                    " LEFT JOIN Brand b" + 
                        " ON a.sBrandCde = b.sBrandCde" + 
                    " LEFT JOIN Model c" + 
                        " ON a.sModelCde = c.sModelCde" + 
                    " LEFT JOIN Inv_Type d" + 
                        " ON a.sInvTypCd = d.sInvTypCd" +
                    " LEFT JOIN Measure f" +
                        " ON a.sMeasurID = f.sMeasurID";
        
        if (!System.getProperty("store.inventory.type").isEmpty())
            lsSQL = MiscUtil.addCondition(lsSQL, " a.sInvTypCd IN " + CommonUtils.getParameter(System.getProperty("store.inventory.type")));
        
        return lsSQL;
    }
    private String getSQ_Stocks(String fsOrderNox){
        return "SELECT " +
                    "  a.sStockIDx" +
                    ", a.sBarCodex" + 
                    ", a.sDescript" + 
                    ", a.sBriefDsc" + 
                    ", a.sAltBarCd" + 
                    ", a.sCategCd1" + 
                    ", a.sCategCd2" + 
                    ", a.sCategCd3" + 
                    ", a.sCategCd4" + 
                    ", a.sBrandCde" + 
                    ", a.sModelCde" + 
                    ", a.sColorCde" + 
                    ", a.sInvTypCd" + 
                    ", a.nUnitPrce" + 
                    ", a.nSelPrice" + 
                    ", a.nDiscLev1" + 
                    ", a.nDiscLev2" + 
                    ", a.nDiscLev3" + 
                    ", a.nDealrDsc" + 
                    ", a.cComboInv" + 
                    ", a.cWthPromo" + 
                    ", a.cSerialze" + 
                    ", a.cUnitType" + 
                    ", a.cInvStatx" + 
                    ", a.sSupersed" + 
                    ", a.cRecdStat" + 
                    ", b.sDescript xBrandNme" + 
                    ", c.sDescript xModelNme" + 
                    ", d.sDescript xInvTypNm" + 
                    ", f.sMeasurNm" +
                    ", h.nQuantity" +
                " FROM Inventory a" + 
                        " LEFT JOIN Brand b" + 
                            " ON a.sBrandCde = b.sBrandCde" + 
                        " LEFT JOIN Model c" + 
                            " ON a.sModelCde = c.sModelCde" + 
                        " LEFT JOIN Inv_Type d" + 
                            " ON a.sInvTypCd = d.sInvTypCd" + 
                        " LEFT JOIN Measure f" +
                            " ON a.sMeasurID = f.sMeasurID" +
                    ", Inv_Master e" + 
                    ", PO_Master g" +
                    ", PO_Detail h" +
                " WHERE a.sStockIDx = e.sStockIDx" + 
                    " AND g.sTransNox = h.sTransNox" +
                    " AND h.sStockIDx = e.sStockIDx" +
                    " AND g.sTransNox = " + SQLUtil.toSQL(fsOrderNox) +
                    " AND e.sBranchCd = " + SQLUtil.toSQL(psBranchCd);
    }
    
    private String getSQ_Purchases(){
        return "SELECT " +
                    "  a.sTransNox" +
                    ", a.sReferNox" +
                    ", a.sBranchCd" + 
                    ", a.dTransact" +
                    ", a.sInvTypCd" +
                    ", a.nTranTotl" + 
                    ", b.sBranchNm" + 
                    ", c.sDescript xDescript" + 
                    ", d.sClientNm" + 
                    ", CASE " +
                        " WHEN a.cTranStat = '0' THEN 'OPEN'" +
                        " WHEN a.cTranStat = '1' THEN 'CLOSED'" +
                        " WHEN a.cTranStat = '2' THEN 'POSTED'" +
                        " WHEN a.cTranStat = '3' THEN 'CANCELLED'" +
                        " WHEN a.cTranStat = '4' THEN 'VOID'" +
                        " END AS xTranStat" +
                    ", a.sSupplier" + 
                    ", a.sReferNox" + 
                    ", a.sTermCode" +
                " FROM PO_Master a" + 
                            " LEFT JOIN Branch b" + 
                                " ON a.sBranchCd = b.sBranchCd" + 
                            " LEFT JOIN Inv_Type c" + 
                                " ON a.sInvTypCd = c.sInvTypCd" + 
                        ", Client_Master d" + 
                " WHERE a.sSupplier = d.sClientID" + 
                    " AND LEFT(a.sTransNox, 4) = " + SQLUtil.toSQL(poGRider.getBranchCode()) +
                    " AND a.cTranStat = '1'";
    }
    
   private String getSQ_Detail(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.nEntryNox" +
                    ", a.sOrderNox" +
                    ", a.sStockIDx" +
                    ", a.sReplacID" +
                    ", a.cUnitType" +
                    ", a.nQuantity" +
                    ", a.nUnitPrce" +
                    ", a.nFreightx" +
                    ", a.dExpiryDt" +
                    ", a.dModified" +
                    ", IFNULL(b.nQtyOnHnd, 0) nQtyOnHnd" + 
                    ", IFNULL(b.nQtyOnHnd, 0) + a.nQuantity xQtyOnHnd" + 
                    ", IFNULL(b.nResvOrdr, 0) nResvOrdr" +
                    ", IFNULL(b.nBackOrdr, 0) nBackOrdr" +
                    ", IFNULL(b.nFloatQty, 0) nFloatQty" +
                    ", IFNULL(b.nLedgerNo, 0) nLedgerNo" +
                    ", IFNULL(e.sMeasurNm, '') sMeasurNm" +
                    ", IFNULL(f.sDescript, '') sBrandNme" +
                " FROM PO_Receiving_Detail a" +
                    " LEFT JOIN Inventory d" + 
                        " ON a.sReplacID = d.sStockIDx" + 
                    " LEFT JOIN Inv_Master b" +
                        " ON a.sStockIDx = b.sStockIDx" + 
                            " AND b.sBranchCD = " + SQLUtil.toSQL(psBranchCd) +
                    " LEFT JOIN Inventory c" + 
                        " ON b.sStockIDx = c.sStockIDx" +  
                    " LEFT JOIN Brand f" + 
                        " ON c.sBrandCde = f.sBrandCde" +  
                    " LEFT JOIN Measure e" +
                        " ON c.sMeasurID = e.sMeasurID" +
                " ORDER BY a.nEntryNox";
    }
    
    public int ItemCount(){
        return paDetail.size();
    }
    
    private Connection setConnection(){
        Connection foConn;
        
        if (pbWithParent){
            foConn = (Connection) poGRider.getConnection();
            if (foConn == null) foConn = (Connection) poGRider.doConnect();
        }else foConn = (Connection) poGRider.doConnect();
        
        return foConn;
    }
    
    public int getEditMode(){return pnEditMode;}
    private String getSQ_Stock(){
        return "SELECT" +
                    "  '' sTransNox" +
                    ", 0 nEntryNox" +
                    ", '' sOrderNox" +
                    ", a.sStockIDx" +
                    ", '' sReplacID" +
                    ", '' cUnitType" +
                    ", IFNULL(b.nQtyOnHnd, 0)  nQuantity" +
                    ", a.nUnitPrce" +
                    ", 0 nFreightx" +
                    ", '' dExpiryDt" +
                    ", '' dModified" +
                    ", IFNULL(b.nQtyOnHnd, 0) nQtyOnHnd" + 
                    ", IFNULL(b.nQtyOnHnd, 0) xQtyOnHnd" + 
                    ", IFNULL(b.nResvOrdr, 0) nResvOrdr" +
                    ", IFNULL(b.nBackOrdr, 0) nBackOrdr" +
                    ", IFNULL(b.nFloatQty, 0) nFloatQty" +
                    ", IFNULL(b.nLedgerNo, 0) nLedgerNo" +
                    ", IFNULL(d.sMeasurNm, '') sMeasurNm" +
                    ", IFNULL(c.sDescript, '') sBrandNme" +
                " FROM  Inventory a" + 
                    " LEFT JOIN Inv_Master b" +
                        " ON a.sStockIDx = b.sStockIDx" + 
                            " AND b.sBranchCD = " + SQLUtil.toSQL(psBranchCd) +
                    " LEFT JOIN Brand c" + 
                        " ON a.sBrandCde = c.sBrandCde" +  
                    " LEFT JOIN Measure d" +
                        " ON a.sMeasurID = d.sMeasurID" +
                " ORDER BY a.sStockIDx";
    }
    
    private String getSQ_POReceiving(){
        String lsTranStat = String.valueOf(pnTranStat);
        String lsCondition = "";
        if (lsTranStat.length() == 1) {
            lsCondition = "a.cTranStat = " + SQLUtil.toSQL(lsTranStat);
        } else {
            for (int lnCtr = 0; lnCtr <= lsTranStat.length() -1; lnCtr++){
                lsCondition = lsCondition + SQLUtil.toSQL(String.valueOf(lsTranStat.charAt(lnCtr))) + ",";
            }
            lsCondition = "(" + lsCondition.substring(0, lsCondition.length()-1) + ")";
            lsCondition = "a.cTranStat IN " + lsCondition;
        }
        
        return MiscUtil.addCondition("SELECT " +
                                        "  a.sTransNox" +
                                        ", a.sBranchCd" + 
                                        ", a.dTransact" +
                                        ", a.sInvTypCd" +
                                        ", a.nTranTotl" + 
                                        ", b.sBranchNm" + 
                                        ", c.sDescript" + 
                                        ", d.sClientNm" + 
                                        ", a.cTranStat" + 
                                        ", a.dRefernce" +
                                        ", a.sReferNox" + 
                                        ", CASE " +
                                            " WHEN a.cTranStat = '0' THEN 'OPEN'" +
                                            " WHEN a.cTranStat = '1' THEN 'CLOSED'" +
                                            " WHEN a.cTranStat = '2' THEN 'POSTED'" +
                                            " WHEN a.cTranStat = '3' THEN 'CANCELLED'" +
                                            " WHEN a.cTranStat = '4' THEN 'VOID'" +
                                            " END AS xTranStat" +
                                    " FROM PO_Receiving_Master a" + 
                                                " LEFT JOIN Branch b" + 
                                                    " ON a.sBranchCd = b.sBranchCd" + 
                                                " LEFT JOIN Inv_Type c" + 
                                                    " ON a.sInvTypCd = c.sInvTypCd" + 
                                            ", Client_Master d" + 
                                    " WHERE a.sSupplier = d.sClientID" +
                                        " AND LEFT(a.sTransNox, 4) = " + SQLUtil.toSQL(poGRider.getBranchCode()), lsCondition);
    }
    public String getSQ_ReceivingMaster() {
        return "SELECT" +
                    "  sTransNox" +
                    ", sBranchCd" +
                    ", dTransact" +
                    ", sCompnyID" +
                    ", sSupplier" +
                    ", sReferNox" +
                    ", dRefernce" +
                    ", sTermCode" +
                    ", nTranTotl" +
                    ", nVATRatex" +
                    ", nTWithHld" +
                    ", nDiscount" +
                    ", nAddDiscx" +
                    ", nAmtPaidx" +
                    ", nFreightx" +
                    ", sRemarksx" +
                    ", sSourceNo" +
                    ", sSourceCd" +
                    ", nEntryNox" +
                    ", sInvTypCd" +
                    ", cTranStat" +
                    ", sPrepared" +
                    ", dPrepared" +
                    ", sApproved" +
                    ", dApproved" +
                    ", sAprvCode" +
                    ", sPostedxx" +
                    ", dPostedxx" +
                    ", sDeptIDxx" +
                    ", cDivision" +
                    ", sModified" +
                    ", dModified" +
                " FROM PO_Receiving_Master";
    }
   
//    private String getSQ_POMaster(){
//        String lsTranStat = String.valueOf(pnTranStat);
//        String lsCondition = "";
//        String lsSQL =  "SELECT" +
//                    "  sTransNox" +
//                    ", sBranchCd" +
//                    ", dTransact" +
//                    ", sCompnyID" +
//                    ", sDestinat" +
//                    ", sSupplier" +
//                    ", sReferNox" +
//                    ", sTermCode" +
//                    ", nTranTotl" +
//                    ", sRemarksx" +
//                    ", sSourceNo" +
//                    ", sSourceCd" +
//                    ", cEmailSnt" +
//                    ", nEmailSnt" +
//                    ", nEntryNox" +
//                    ", sInvTypCd" +
//                    ", cTranStat" +
//                    ", sPrepared" +
//                    ", dPrepared" +
//                    ", sApproved" +
//                    ", dApproved" +
//                    ", sAprvCode" +
//                    ", sPostedxx" +
//                    ", dPostedxx" +
//                    ", sModified" +
//                    ", dModified" +
//                        " FROM PO_Master a" + 
//                        " WHERE sTransNox LIKE " + SQLUtil.toSQL(psBranchCd + "%");
//        
//        if (lsTranStat.length() == 1) {
//            lsCondition = "cTranStat = " + SQLUtil.toSQL(lsTranStat);
//        } else {
//            for (int lnCtr = 0; lnCtr <= lsTranStat.length() -1; lnCtr++){
//                lsCondition = lsCondition + SQLUtil.toSQL(String.valueOf(lsTranStat.charAt(lnCtr))) + ",";
//            }
//            lsCondition = "(" + lsCondition.substring(0, lsCondition.length()-1) + ")";
//            lsCondition = "cTranStat IN " + lsCondition;
//        }
//        
//        lsSQL = MiscUtil.addCondition(lsSQL, lsCondition);
//        return lsSQL;
//    }
    
    private String getSQ_Stocks(){
       String lsSQL =  "SELECT " +
                    "  a.sStockIDx" +
                    ", a.sBarCodex" + 
                    ", a.sDescript" + 
                    ", a.sBriefDsc" + 
                    ", a.sAltBarCd" + 
                    ", a.sCategCd1" + 
                    ", a.sCategCd2" + 
                    ", a.sCategCd3" + 
                    ", a.sCategCd4" + 
                    ", a.sBrandCde" + 
                    ", a.sModelCde" + 
                    ", a.sColorCde" + 
                    ", a.sInvTypCd" + 
                    ", a.nUnitPrce" + 
                    ", a.nSelPrice" + 
                    ", a.nDiscLev1" + 
                    ", a.nDiscLev2" + 
                    ", a.nDiscLev3" + 
                    ", a.nDealrDsc" + 
                    ", a.cComboInv" + 
                    ", a.cWthPromo" + 
                    ", a.cSerialze" + 
                    ", a.cUnitType" + 
                    ", a.cInvStatx" + 
                    ", a.sSupersed" + 
                    ", a.cRecdStat" + 
                    ", b.sDescript xBrandNme" + 
                    ", c.sDescript xModelNme" + 
                    ", d.sDescript xInvTypNm" + 
                    ", f.sMeasurNm" +
                    ", IFNULL(e.nQtyOnHnd,0) nQtyOnHnd" +
                " FROM Inventory a" + 
                        " LEFT JOIN Brand b" + 
                            " ON a.sBrandCde = b.sBrandCde" + 
                        " LEFT JOIN Model c" + 
                            " ON a.sModelCde = c.sModelCde" + 
                        " LEFT JOIN Inv_Type d" + 
                            " ON a.sInvTypCd = d.sInvTypCd" + 
                        " LEFT JOIN Measure f" +
                            " ON a.sMeasurID = f.sMeasurID" +
                    ", Inv_Master e" + 
                " WHERE a.sStockIDx = e.sStockIDx" + 
                    " AND e.sBranchCd = " + SQLUtil.toSQL(psBranchCd);
        if (!System.getProperty("store.inventory.type").isEmpty())
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sInvTypCd IN " + CommonUtils.getParameter(System.getProperty("store.inventory.type")));
        
        return lsSQL;
    }
    private String getPODetail(String fsOrderNox){
        return "SELECT " +
                    "  a.sTransNox" +
                    ", a.nEntryNox" + 
                    ", a.sStockIDx" + 
                    ", (a.nQuantity - a.nReceived - a.nCancelld) nQuantity" + 
                    ", a.nUnitPrce" + 
                    ", a.nReceived" + 
                    ", a.nCancelld" + 
                    ", IFNULL(c.sDescript,'') sBrandNme" + 
                " FROM PO_Detail a" + 
                " LEFT JOIN Inventory b" + 
                "   ON a.sStockIDx = b.sStockIDx" + 
                " LEFT JOIN Brand c" + 
                "   ON b.sBrandCde = c.sBrandCde" + 
                " WHERE a.sTransNox = " + SQLUtil.toSQL(fsOrderNox) +
                    " ORDER BY a.nEntryNox";
    }
    public void printColumnsMaster(){poData.list();}
    public void printColumnsDetail(){poDetail.list();}
    public void setTranStat(int fnValue){this.pnTranStat = fnValue;}
    
    //callback methods
    public void setCallBack(IMasterDetail foCallBack){
        poCallBack = foCallBack;
    }
    
    private void MasterRetreived(int fnRow){
        if (poCallBack == null) return;
        
        poCallBack.MasterRetreive(fnRow);
    }
    
    private void DetailRetreived(int fnRow){
        if (poCallBack == null) return;
        
        poCallBack.DetailRetreive(fnRow);
    }
    
    public void setClientNm(String fsClientNm){
        this.psClientNm = fsClientNm;
    }
    
    
    public Inventory GetInventory(String fsValue, boolean fbByCode, boolean fbSearch){        
        Inventory instance = new Inventory(poGRider, psBranchCd, fbSearch);
        instance.BrowseRecord(fsValue, fbByCode, false);
        return instance;
    }
    
    public XMTerm GetTerm(String fsValue, boolean fbByCode){
        if (fbByCode && fsValue.equals("")) return null;
        
        XMTerm instance  = new XMTerm(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode))
            return instance;
        else
            return null;
    }
    
    public XMBranch GetBranch(String fsValue, boolean fbByCode){
        if (fbByCode && fsValue.equals("")) return null;
        
        XMBranch instance  = new XMBranch(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode))
            return instance;
        else
            return null;
    }
    
    public JSONObject GetSupplier(String fsValue, boolean fbByCode){
        if (fbByCode && fsValue.equals("")) return null;
        
        XMClient instance  = new XMClient(poGRider, psBranchCd, true);
        return instance.SearchClient(fsValue, fbByCode);
    }
    
    public XMInventoryType GetInventoryType(String fsValue, boolean fbByCode){
        if (fbByCode && fsValue.equals("")) return null;
        
        XMInventoryType instance  = new XMInventoryType(poGRider, psBranchCd, true);
        if (instance.browseRecord(fsValue, fbByCode))
            return instance;
        else
            return null;
    }
    
    
    public boolean SearchMaster(int fnCol, String fsValue, boolean fbByCode){
        switch(fnCol){
            case 2: //sBranchCd
                XMBranch loBranch = new XMBranch(poGRider, psBranchCd, true);
                if (loBranch.browseRecord(fsValue, fbByCode)){
                    setMaster(fnCol, (String) loBranch.getMaster("sBranchCd"));
                    setMaster(4, (String) loBranch.getMaster("sCompnyID"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 5: //sSupplier
                XMSupplier loSupplier = new XMSupplier(poGRider, psBranchCd, true);
                 
                if (loSupplier.browseRecord(fsValue, psBranchCd, fbByCode)){
                    setMaster(fnCol, loSupplier.getMaster("sClientID"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 8: //sTermCode
                XMTerm loTerm = new XMTerm(poGRider, psBranchCd, true);
                if (loTerm.browseRecord(fsValue, fbByCode)){
                    setMaster(fnCol, loTerm.getMaster("sTermCode"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 20: //sInvTypCd
                XMInventoryType loInv = new XMInventoryType(poGRider, psBranchCd, true);
                if (loInv.browseRecord(fsValue, fbByCode)){
                setMaster(fnCol, loInv.getMaster("sInvTypCd"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 29:
                XMDepartment loDept = new XMDepartment(poGRider, psBranchCd, true);
                if (loDept.browseRecord(fsValue, fbByCode)){
                    setMaster(fnCol, loDept.getMaster("sDeptIDxx"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 17:
                String lsHeader = "Order No»Supplier»Refer No»Date";
                String lsColName = "sTransNox»sClientNm»sReferNox»dTransact";
                String lsColCrit = "a.sTransNox»d.sClientNm»a.sReferNox»a.dTransact";
                String lsSQL = getSQ_Purchases();
                
                JSONObject loJSON = showFXDialog.jsonSearch(poGRider, 
                                                            lsSQL, 
                                                            fsValue, 
                                                            lsHeader, 
                                                            lsColName, 
                                                            lsColCrit, 
                                                            fbByCode ? 0 : 2);
                
                
                if (loJSON != null){
                    setMaster(5, (String) loJSON.get("sSupplier"));
                    MasterRetreived(5);
                    
                    setMaster(8, (String) loJSON.get("sTermCode"));
                    MasterRetreived(8);                    
                    
                    setMaster(17, (String) loJSON.get("sTransNox"));
                    setMaster(18,  "PO");
                    MasterRetreived(17);
                    
                    loadOrder((String) loJSON.get("sTransNox"));
                    return true;
                } else{
                    setMaster(17, (String) loJSON.get("sTransNox"));
                    setMaster(18,  "PO");
                    MasterRetreived(17);
                    
                    return false;
                }
                
        }
        
        return false;
    }
    
    
    public boolean SearchMaster(String fsCol, String fsValue, boolean fbByCode){
        return SearchMaster(poData.getColumn(fsCol), fsValue, fbByCode);
    }
    private void loadOrder(String fsOrderNox){
        java.sql.Connection loCon = poGRider.getConnection();

        Statement loStmt = null;
        ResultSet loRS = null;
        
        try {
            
            loStmt = loCon.createStatement();
            loRS = loStmt.executeQuery(getPODetail(fsOrderNox));
            while (loRS.next()) {
                setDetail(ItemCount() -1,"sOrderNox", loRS.getString("sTransNox"));
                setDetail(ItemCount() -1,"sStockIDx", loRS.getString("sStockIDx"));
                setDetail(ItemCount() -1,"nUnitPrce", loRS.getDouble("nUnitPrce"));
                setDetail(ItemCount() -1,"nQuantity", loRS.getDouble("nQuantity"));  
                setDetail(ItemCount() -1,"sBrandNme", loRS.getString("sBrandNme"));                
                addDetail();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        finally{
            MiscUtil.close(loRS);
            MiscUtil.close(loStmt);
        }
    }
    
    public boolean printRecord(){
        if (poData == null){
            ShowMessageFX.Warning("Unable to print transaction.", "Warning", "No record loaded.");
            return false;
        }
        
        //Create the parameter
        Map<String, Object> params = new HashMap<>();
        params.put("sCompnyNm", "Guanzon Group");
        params.put("sBranchNm", poGRider.getBranchName());
        params.put("sAddressx", poGRider.getAddress() + ", " + poGRider.getTownName() + " " +poGRider.getProvince());
        JSONObject loSupplier = GetSupplier(poData.getSupplier(), true);
        if (loSupplier != null) {
            params.put("sSupplier", loSupplier.get("sClientNm"));
        }else{
            params.put("sSupplier", "NONE");
        }
        params.put("sTransNox", poData.getTransNox());
        params.put("sReferNox", poData.getReferNo());
        params.put("dTransact", SQLUtil.dateFormat(poData.getDateTransact(), SQLUtil.FORMAT_LONG_DATE));
        params.put("dReferDte", SQLUtil.dateFormat(poData.getReferDate(), SQLUtil.FORMAT_LONG_DATE));
        params.put("sPrintdBy", psClientNm);
        
        JSONObject loJSON;
        JSONArray loArray = new JSONArray();
        
        String lsBarCodex;
        String lsDescript;
        String lsMeasurex;
        Inventory loInventory = new Inventory(poGRider, psBranchCd, true);
        
        for (int lnCtr = 0; lnCtr <= ItemCount() -1; lnCtr ++){
            loInventory.BrowseRecord((String) getDetail(lnCtr, "sStockIDx"), true, false);
            lsBarCodex = (String) loInventory.getMaster("sBarCodex");
            lsDescript = (String) loInventory.getMaster("sDescript");
            lsMeasurex = (String) loInventory.getMeasureMent(loInventory.getMaster("sMeasurID").toString());
            
            loJSON = new JSONObject();
            loJSON.put("sField01", lsBarCodex);
            loJSON.put("sField02", lsDescript);
            loJSON.put("sField05", lsMeasurex);
            loJSON.put("sField03", getDetail(lnCtr, "sBrandNme"));
            loJSON.put("nField01", getDetail(lnCtr, "nQuantity"));
            loJSON.put("lField01", getDetail(lnCtr, "nUnitPrce"));
            loArray.add(loJSON);
        }
                 
        try {
            InputStream stream = new ByteArrayInputStream(loArray.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson;
            
            jrjson = new JsonDataSource(stream);
            JasperPrint jrprint = JasperFillManager.fillReport(poGRider.getReportPath() + 
                                                                "PurchaseReceiving.jasper", params, jrjson);
        
            JasperViewer jv = new JasperViewer(jrprint, false);     
            jv.setVisible(true);
            jv.setAlwaysOnTop(true);
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
    
    private UnitPOReceivingMaster poData = new UnitPOReceivingMaster();
    private UnitPOReceivingDetail poDetail = new UnitPOReceivingDetail();
    private ArrayList<UnitPOReceivingDetail> paDetail;
    private ArrayList<UnitPOReceivingDetailOthers> paDetailOthers;
    
    private final Double pxeTaxWHeldRate = 0.00; //0.01
    private final Double pxeTaxRate = 0.12;
    private final Double pxeTaxExcludRte = 1.12;
    
    private final String pxeModuleName = POReceiving1.class.getSimpleName();
}
