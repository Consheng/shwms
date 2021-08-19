package ykk.xc.com.shwms.bean;

import java.io.Serializable;

import ykk.xc.com.shwms.bean.k3Bean.ICItem_App;

/**
 * 报工记录
 */
public class WorkRecord implements Serializable,Cloneable {
	private static final long serialVersionUID = 1L;

	private int id;
	private int deptId; 					// 班组id
	private int processFlowId;				// 工艺路线id
	private int processFlowEntryId;			// 工艺路线子表
	private int processId;					// 工序id
	private double fqty;					// 报工数量（最终）
	private double fqty2;					// 报工数（第一次）
	private double price;					// 单价
	private int seatNum;					// 座位数
	private int workUserId; 				// 报工人（Staff）
	private String workDate; 				// 报工日期（最终）
	private String workDate2; 				// 报工日期（第一次）
	private int mtlId;						// 物料id
	private String barcode;					// 条码
	private int prodId;						// 生产订单内码id
	private int prodEntryId;				// 生产订单分录id
	private String prodNo;					// 生产订单号
	private double prodQty;					// 生产订单数
	private int salOrderId;					// 销售订单内码id
	private int salOrderEntryId;			// 销售订单分录id
	private String salOrderNo;				// 销售订单号
	private int createUserId; 				// 创建人
	private String createDate; 				// 创建日期
	private int passUserId;					//审核人id
	private String passDate;				//审核时间
	private int passStatus;					//审核状态,0:未审核，1:已审核

	private ICItem_App icItem;

	// 临时字段，不存表
	private boolean checkRow; // 是否选中行
	private String deptName;			//班组名称
	private String workUserName;		//报工人名称
	private String createUserName;		//创建人
	private String passUserName;		//审核人
	private String processName;			// 工序名称
	
	public WorkRecord() {
		super();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getDeptId() {
		return deptId;
	}

	public void setDeptId(int deptId) {
		this.deptId = deptId;
	}

	public int getProcessFlowId() {
		return processFlowId;
	}

	public void setProcessFlowId(int processFlowId) {
		this.processFlowId = processFlowId;
	}

	public int getProcessFlowEntryId() {
		return processFlowEntryId;
	}

	public void setProcessFlowEntryId(int processFlowEntryId) {
		this.processFlowEntryId = processFlowEntryId;
	}

	public int getProcessId() {
		return processId;
	}

	public void setProcessId(int processId) {
		this.processId = processId;
	}

	public double getFqty() {
		return fqty;
	}

	public void setFqty(double fqty) {
		this.fqty = fqty;
	}

	public double getFqty2() {
		return fqty2;
	}

	public void setFqty2(double fqty2) {
		this.fqty2 = fqty2;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getSeatNum() {
		return seatNum;
	}

	public void setSeatNum(int seatNum) {
		this.seatNum = seatNum;
	}

	public int getWorkUserId() {
		return workUserId;
	}

	public void setWorkUserId(int workUserId) {
		this.workUserId = workUserId;
	}

	public String getWorkDate() {
		return workDate;
	}

	public void setWorkDate(String workDate) {
		this.workDate = workDate;
	}

	public String getWorkDate2() {
		return workDate2;
	}

	public void setWorkDate2(String workDate2) {
		this.workDate2 = workDate2;
	}

	public int getMtlId() {
		return mtlId;
	}

	public void setMtlId(int mtlId) {
		this.mtlId = mtlId;
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}

	public int getProdId() {
		return prodId;
	}

	public void setProdId(int prodId) {
		this.prodId = prodId;
	}

	public int getProdEntryId() {
		return prodEntryId;
	}

	public void setProdEntryId(int prodEntryId) {
		this.prodEntryId = prodEntryId;
	}

	public String getProdNo() {
		return prodNo;
	}

	public void setProdNo(String prodNo) {
		this.prodNo = prodNo;
	}

	public double getProdQty() {
		return prodQty;
	}

	public void setProdQty(double prodQty) {
		this.prodQty = prodQty;
	}

	public int getSalOrderId() {
		return salOrderId;
	}

	public void setSalOrderId(int salOrderId) {
		this.salOrderId = salOrderId;
	}

	public int getSalOrderEntryId() {
		return salOrderEntryId;
	}

	public void setSalOrderEntryId(int salOrderEntryId) {
		this.salOrderEntryId = salOrderEntryId;
	}

	public String getSalOrderNo() {
		return salOrderNo;
	}

	public void setSalOrderNo(String salOrderNo) {
		this.salOrderNo = salOrderNo;
	}

	public int getCreateUserId() {
		return createUserId;
	}

	public void setCreateUserId(int createUserId) {
		this.createUserId = createUserId;
	}

	public String getCreateDate() {
		return createDate;
	}

	public void setCreateDate(String createDate) {
		this.createDate = createDate;
	}

	public int getPassUserId() {
		return passUserId;
	}

	public void setPassUserId(int passUserId) {
		this.passUserId = passUserId;
	}

	public String getPassDate() {
		return passDate;
	}

	public void setPassDate(String passDate) {
		this.passDate = passDate;
	}

	public int getPassStatus() {
		return passStatus;
	}

	public void setPassStatus(int passStatus) {
		this.passStatus = passStatus;
	}

	public ICItem_App getIcItem() {
		return icItem;
	}

	public void setIcItem(ICItem_App icItem) {
		this.icItem = icItem;
	}

	public boolean isCheckRow() {
		return checkRow;
	}

	public void setCheckRow(boolean checkRow) {
		this.checkRow = checkRow;
	}

	public String getDeptName() {
		return deptName;
	}

	public void setDeptName(String deptName) {
		this.deptName = deptName;
	}

	public String getWorkUserName() {
		return workUserName;
	}

	public void setWorkUserName(String workUserName) {
		this.workUserName = workUserName;
	}

	public String getCreateUserName() {
		return createUserName;
	}

	public void setCreateUserName(String createUserName) {
		this.createUserName = createUserName;
	}

	public String getPassUserName() {
		return passUserName;
	}

	public void setPassUserName(String passUserName) {
		this.passUserName = passUserName;
	}

	public String getProcessName() {
		return processName;
	}

	public void setProcessName(String processName) {
		this.processName = processName;
	}
	
	
}
