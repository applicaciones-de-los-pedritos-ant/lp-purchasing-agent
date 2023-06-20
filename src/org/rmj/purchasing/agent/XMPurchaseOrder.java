package org.rmj.purchasing.agent;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.iface.XMRecord;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.RecordStatus;
import org.rmj.cas.client.base.XMClient;
import org.rmj.cas.inventory.base.Inventory;
import org.rmj.lp.parameter.agent.XMBranch;
import org.rmj.lp.parameter.agent.XMInventoryType;
import org.rmj.lp.parameter.agent.XMSupplier;
import org.rmj.lp.parameter.agent.XMTerm;
import org.rmj.cas.purchasing.base.PurchaseOrder;
import org.rmj.cas.purchasing.pojo.UnitPODetail;
import org.rmj.cas.purchasing.pojo.UnitPOMaster;
import org.rmj.appdriver.agentfx.callback.IMasterDetail;

public class XMPurchaseOrder implements XMRecord{
    public XMPurchaseOrder(GRider foGRider, String fsBranchCD, boolean fbWithParent){
        this.poGRider = foGRider;
        if (foGRider != null){
            this.pbWithParent = fbWithParent;
            this.psBranchCd = fsBranchCD;
            
            poControl = new PurchaseOrder();
            poControl.setGRider(foGRider);
            poControl.setBranch(fsBranchCD);
            poControl.setWithParent(fbWithParent);
            
            pnEditMode = EditMode.UNKNOWN;
        }
    }
    
    @Override
    public void setMaster(int fnCol, Object foData) {
        if (pnEditMode != EditMode.UNKNOWN){
            // Don't allow specific fields to assign values
            if(!(fnCol == poData.getColumn("sTransNox") ||
                fnCol == poData.getColumn("cTranStat") ||
                fnCol == poData.getColumn("sModified") ||
                fnCol == poData.getColumn("dModified"))){
                
                poData.setValue(fnCol, foData);
                MasterRetreived(fnCol);
            }
        }
    }

    @Override
    public void setMaster(String fsCol, Object foData) {
        setMaster(poData.getColumn(fsCol), foData);
    }

    @Override
    public Object getMaster(int fnCol) {
        if(pnEditMode == EditMode.UNKNOWN || poControl == null)
         return null;
      else{
         return poData.getValue(fnCol);
      }
    }

    @Override
    public Object getMaster(String fsCol) {
        return getMaster(poData.getColumn(fsCol));
    }

    @Override
    public boolean newRecord() {
        poData = poControl.newTransaction();              
        
        if (poData == null){
            ShowMessageFX();
            return false;
        }else{
            poData.setDateTransact(poGRider.getServerDate());
            
            addDetail();
            pnEditMode = EditMode.ADDNEW;
            return true;
        }
    }

    @Override
    public boolean openRecord(String fstransNox) {
        poData = poControl.loadTransaction(fstransNox);
        
        if (poData.getTransNox()== null){
            ShowMessageFX();
            return false;
        } else{
            pnEditMode = EditMode.READY;
            return true;
        }
    }

    @Override
    public boolean updateRecord() {
        if(pnEditMode != EditMode.READY) {
            return false;
        } else {
            pnEditMode = EditMode.UPDATE;
            return true;
        }
    }

    @Override
    public boolean saveRecord() {
        if(pnEditMode == EditMode.UNKNOWN){
            return false;
        }else{
            poData.setTranTotal(computeTotal());
            // Perform testing on values that needs approval here...
            UnitPOMaster loResult;
            if(pnEditMode == EditMode.ADDNEW)
                loResult = poControl.saveUpdate(poData, "");
            else 
                loResult = poControl.saveUpdate(poData, (String) poData.getValue(1));

            if(loResult == null){
                ShowMessageFX();
                return false;
            }else{
                pnEditMode = EditMode.READY;
                poData = loResult;
                return true;
            }
      }
    }

    @Override
    public boolean deleteRecord(String fsTransNox) {
        if(pnEditMode != EditMode.READY){
            return false;
        } else{
            boolean lbResult = poControl.deleteTransaction(fsTransNox);
            if (lbResult)
                pnEditMode = EditMode.UNKNOWN;
            else ShowMessageFX();

            return lbResult;
        }
    }

    @Override
    public boolean deactivateRecord(String fsTransNox) {
        return false;
    }

    @Override
    public boolean activateRecord(String fsTransNox) {
        return false;
    }

    @Override
    public void setBranch(String foBranchCD) {
        psBranchCd = foBranchCD;
    }

    @Override
    public int getEditMode() {
        return pnEditMode;
    }
    
    //Added methods
    public void setGRider(GRider foGrider){
        this.poGRider = foGrider;
        this.psUserIDxx = foGrider.getUserID();
        
        if (psBranchCd.isEmpty()) psBranchCd = poGRider.getBranchCode();
    }
    
    public boolean deleteDetail(int fnRow){
        boolean lbDel = poControl.deleteDetail(fnRow);
        
        if (lbDel){
            poData.setTranTotal(computeTotal());
            return true;
        } else return false;
    }
    
    public boolean cancelRecord(String fsTransNox){
        if(pnEditMode != EditMode.READY){
            return false;
        } else{
            boolean lbResult = poControl.cancelTransaction(fsTransNox);
            if (lbResult)
                pnEditMode = EditMode.UNKNOWN;
            else ShowMessageFX();

            return lbResult;
        }
    }
    
    public boolean closeRecord(String fsTransNox, String fsUserIDxx, String fsAprvCode){
        if(pnEditMode != EditMode.READY){
            return false;
        } else{
            boolean lbResult = poControl.closeTransaction(fsTransNox, fsUserIDxx, fsAprvCode);
            if (lbResult)
                pnEditMode = EditMode.UNKNOWN;
            else ShowMessageFX();

            return lbResult;
        }
    }
    
    public boolean postRecord(String fsTransNox){
        if(pnEditMode != EditMode.READY){
            return false;
        } else{
            boolean lbResult = poControl.postTransaction(fsTransNox);
            if (lbResult)
                pnEditMode = EditMode.UNKNOWN;
            else ShowMessageFX();

            return lbResult;
        }
    }
    
    public boolean voidRecord(String fsTransNox){
        if(pnEditMode != EditMode.READY){
            return false;
        } else{
            boolean lbResult = poControl.voidTransaction(fsTransNox);
            if (lbResult)
                pnEditMode = EditMode.UNKNOWN;
            else ShowMessageFX();

            return lbResult;
        }
    }
    
    public boolean SearchMaster(int fnCol, String fsValue, boolean fbByCode){
        switch(fnCol){
            case 2: //sBranchCd
                XMBranch loBranch = new XMBranch(poGRider, psBranchCd, true);
                if (loBranch.browseRecord(fsValue, fbByCode)){
                    setMaster(fnCol, loBranch.getMaster("sBranchCd"));
                    setMaster("sCompnyID", loBranch.getMaster("sCompnyID"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 5: //sDestinat
                XMBranch loDest = new XMBranch(poGRider, psBranchCd, true);
                if (loDest.browseRecord(fsValue, fbByCode)){
                    setMaster(fnCol, loDest.getMaster("sBranchCd"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
            case 6: //sSupplier
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
            case 16: //sInvTypCd
                XMInventoryType loInv = new XMInventoryType(poGRider, psBranchCd, true);
                if (loInv.browseRecord(fsValue, fbByCode)){
                setMaster(fnCol, loInv.getMaster("sInvTypCd"));
                    MasterRetreived(fnCol);
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    public boolean SearchMaster(String fsCol, String fsValue, boolean fbByCode){
        return SearchMaster(poData.getColumn(fsCol), fsValue, fbByCode);
    }
    
    public JSONObject SearchDetail(int fnRow, int fnCol, String fsValue, boolean fbSearch, boolean fbByCode){
        String lsHeader = "";
        String lsColName = "";
        String lsColCrit = "";
        String lsSQL = "";
        JSONObject loJSON;
        ResultSet loRS;
        
        switch(fnCol){
            case 3:
                lsHeader = "Barcode»Description»Brand»Unit»Qty. on hand»Inv. Type";
                lsColName = "sBarCodex»sDescript»xBrandNme»sMeasurNm»nQtyOnHnd»xInvTypNm";
                lsColCrit = "a.sBarCodex»a.sDescript»b.sDescript»f.sMeasurNm»e.nQtyOnHnd»d.sDescript";
                
                lsSQL = MiscUtil.addCondition(getSQ_Stocks(), "a.cRecdStat = " + SQLUtil.toSQL(RecordStatus.ACTIVE));
                
                loJSON = showFXDialog.jsonSearch(poGRider, 
                                                        lsSQL, 
                                                        fsValue, 
                                                        lsHeader, 
                                                        lsColName, 
                                                        lsColCrit, 
                                                        fbByCode ? 0 : 1);
                                
                if (loJSON != null){
                    setDetail(fnRow, fnCol, (String) loJSON.get("sStockIDx"));
                    setDetail(fnRow, "nUnitPrce", Double.valueOf((String) loJSON.get("nUnitPrce")));
                    setDetail(fnRow, "nQtyOnHnd", Double.valueOf((String) loJSON.get("nQtyOnHnd")));
                    
                    return loJSON;
                } else{
                    setDetail(fnRow, fnCol, "");
                    setDetail(fnRow, "nUnitPrce", 0.00);
                    setDetail(fnRow, "nQtyOnHnd", 0.00);
                    return null;
                }
            default:
                return null;
        }
    }
    
    public JSONObject SearchDetail(int fnRow, String fsCol, String fsValue, boolean fbSearch, boolean fbByCode){
        return SearchDetail(fnRow, poDetail.getColumn(fsCol), fsValue, fbSearch, fbByCode);
    }
    
    public boolean BrowseRecord(String fsValue, boolean fbByCode){
        JSONObject loJSON;
        String lsHeader = "Trans No.»Supplier»Refer No»Date";
        String lsColName = "sTransNox»sClientNm»sReferNox»dTransact";
        String lsColCrit = "a.sTransNox»d.sClientNm»a.sReferNox»a.dTransact";
        String lsSQL = getSQ_Purchase_Order();
        
        loJSON = showFXDialog.jsonSearch(poGRider, 
                                            lsSQL, 
                                            fsValue, 
                                            lsHeader, 
                                            lsColName, 
                                            lsColCrit, 
                                            fbByCode ? 2 : 1);
        
        if(loJSON == null)
            return false;
        else{
            return openRecord((String) loJSON.get("sTransNox"));
        }
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
    
    public void setClientNm(String fsClientNm){
        this.psClientNm = fsClientNm;
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
        params.put("sDestinat", poData.getDestinat());
        params.put("sTransNox", poData.getTransNox());
        params.put("sReferNox", poData.getReferNo());
        params.put("dTransact", SQLUtil.dateFormat(poData.getDateTransact(), SQLUtil.FORMAT_LONG_DATE));
        params.put("sPrintdBy", psClientNm);
        
        JSONObject loJSON;

        try {
            String lsSQL = "SELECT sClientNm FROM Client_Master WHERE sClientID = " + SQLUtil.toSQL(poData.getSupplier());
            ResultSet loRS = poGRider.executeQuery(lsSQL);

            if (loRS.next()){
                params.put("xSupplier", loRS.getString("sClientNm"));
            } else {
                params.put("xSupplier", "NOT SPECIFIED");
            }
            
            lsSQL = "SELECT sClientNm FROM Client_Master WHERE sClientID IN (" +
                        "SELECT sEmployNo FROM xxxSysUser WHERE sUserIDxx = " + SQLUtil.toSQL(poData.getApprovedBy().isEmpty() ? poData.getPreparedBy() : poData.getApprovedBy()) + ")";
            loRS = poGRider.executeQuery(lsSQL);
            
            if (loRS.next()){
                params.put("sApprval1", loRS.getString("sClientNm"));
            } else {
                params.put("sApprval1", "");
            }
            
            params.put("sApprval2", "");
            
//            lsSQL = "SELECT sClientNm FROM Client_Master WHERE sClientID IN (" +
//                        "SELECT sEmployNo FROM xxxSysUser WHERE sUserIDxx = " + SQLUtil.toSQL(poData.getApprovedBy()) + ")";
//            loRS = poGRider.executeQuery(lsSQL);
//            
//            if (loRS.next()){
//                params.put("sApprval2", loRS.getString("sClientNm"));
//            } else {
//                params.put("sApprval2", "NOT SPECIFIED");
//            }
            params.put("xRemarksx", poData.getRemarks());

            JSONArray loArray = new JSONArray();

            String lsBarCodex;
            String lsDescript;
            String lsMeasurex;
            Inventory loInventory = new Inventory(poGRider, psBranchCd, true);

            for (int lnCtr = 0; lnCtr <= poControl.ItemCount() -1; lnCtr ++){
                loInventory.BrowseRecord((String) poControl.getDetail(lnCtr, "sStockIDx"), true, false);
                lsBarCodex = (String) loInventory.getMaster("sBarCodex");
                lsDescript = (String) loInventory.getMaster("sDescript");
                lsMeasurex = (String) loInventory.getMeasureMent(loInventory.getMaster("sMeasurID").toString());

                loJSON = new JSONObject();
                loJSON.put("sField01", lsBarCodex);
                loJSON.put("sField02", lsDescript);
                loJSON.put("sField03", lsMeasurex);
                loJSON.put("nField01", poControl.getDetail(lnCtr, "nQuantity"));
                loArray.add(loJSON);
            }
       
            InputStream stream = new ByteArrayInputStream(loArray.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson;
            
            jrjson = new JsonDataSource(stream);
            
            JasperPrint jrprint = JasperFillManager.fillReport(poGRider.getReportPath() + 
                                                                "PurchaseOrderLP.jasper", params, jrjson);
        
            JasperViewer jv = new JasperViewer(jrprint, false);     
            jv.setVisible(true);
            jv.setAlwaysOnTop(true);
        } catch (JRException | UnsupportedEncodingException | SQLException ex) {
            Logger.getLogger(XMPOReceiving.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }
    
    private String getSQ_Purchase_Order(){
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
                    ", a.sReferNox" + 
                    ", CASE " +
                        " WHEN a.cTranStat = '0' THEN 'OPEN'" +
                        " WHEN a.cTranStat = '1' THEN 'CLOSED'" +
                        " WHEN a.cTranStat = '2' THEN 'POSTED'" +
                        " WHEN a.cTranStat = '3' THEN 'CANCELLED'" +
                        " WHEN a.cTranStat = '4' THEN 'VOID'" +
                        " END AS xTranStat" +
                " FROM PO_Master a" + 
                            " LEFT JOIN Branch b" + 
                                " ON a.sBranchCd = b.sBranchCd" + 
                            " LEFT JOIN Inv_Type c" + 
                                " ON a.sInvTypCd = c.sInvTypCd" + 
                        ", Client_Master d" + 
                " WHERE a.sSupplier ="
                + " d.sClientID", lsCondition);
    }
    
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
    
    public boolean addDetail(){return poControl.addDetail();}
    public int getDetailCount(){return poControl.ItemCount();}
    
    public void setDetail(int fnRow, int fnCol, Object foData){
        if (pnEditMode != EditMode.UNKNOWN){
            // Don't allow specific fields to assign values
            if(!(fnCol == poDetail.getColumn("sTransNox") ||
                fnCol == poDetail.getColumn("nEntryNox") ||
                fnCol == poDetail.getColumn("dModified"))){
                
                poControl.setDetail(fnRow, fnCol, foData);
                DetailRetreived(fnCol);
                
                if (fnCol == Integer.parseInt(String.valueOf(poDetail.getColumn("nQuantity"))) ||
                     fnCol == Integer.parseInt(String.valueOf(poDetail.getColumn("nUnitPrce")))){
                    poData.setTranTotal(computeTotal());
                    MasterRetreived(9);
                }                
            }
        }
    }

    public void setDetail(int fnRow, String fsCol, Object foData){        
        setDetail(fnRow, poDetail.getColumn(fsCol), foData);
    }
    
    public Object getDetail(int fnRow, String fsCol){
        if (fnRow < 0) return null;
        
        return poControl.getDetail(fnRow, fsCol);
    }
    public Object getDetail(int fnRow, int fnCol){
        if (fnRow < 0) return null;
        
        return poControl.getDetail(fnRow, fnCol);
    }

    private double computeTotal(){
        double lnTranTotal = 0;
        for (int lnCtr = 0; lnCtr <= poControl.ItemCount()-1; lnCtr ++){
            lnTranTotal += Double.valueOf(String.valueOf(poControl.getDetail(lnCtr, "nQuantity"))) * Double.valueOf(String.valueOf(poControl.getDetail(lnCtr, "nUnitPrce")));
        }
        
        return lnTranTotal;
    }
    
    private void ShowMessageFX(){
        if (!poControl.getErrMsg().isEmpty()){
            if (!poControl.getMessage().isEmpty())
                ShowMessageFX.Error(poControl.getErrMsg(), pxeModuleName, poControl.getMessage());
            else ShowMessageFX.Error(poControl.getErrMsg(), pxeModuleName, null);
        }else ShowMessageFX.Information(null, pxeModuleName, poControl.getMessage());
    }
    
    
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
    
    public void setTranStat(int fnValue){this.pnTranStat = fnValue;}
    
    //Member Variables
    private GRider poGRider;
    private PurchaseOrder poControl;
    private UnitPOMaster poData;
    private final UnitPODetail poDetail = new UnitPODetail();
    
    private String psBranchCd;
    private int pnEditMode;
    private String psUserIDxx;
    private boolean pbWithParent;
    private int pnTranStat = 0;
    private IMasterDetail poCallBack;
    private String psClientNm = "";
    
    private final String pxeModuleName = "XMPurchaseOrder";
}
