package ykk.xc.com.shwms.bean;

import java.io.Serializable;

/**
 * @Description:工艺体
 *
 * @author qxp 2018年11月10日 上午11:14:24
 */
public class ProcessflowEntry implements Serializable {
	private static final long serialVersionUID = 1L;

	private int id;
	/* 工序id */
	private int procedureId;
	/* 工艺路线id */
	private int processflowId;
	/* 工艺体编号 */
	private String processflowElementNumber;
	/* 图片地址 */
	private String imgUrl;
	/* 工资类型id */
	private int wageTypeId;
	/* 流转类型id */
	private int flowTypeId;
	/* 工序汇报方式  A:自动汇报  B:手工汇报 */
	private String reportWay;
	/*工序汇报类型 A：按位置汇报 B：按套汇报*/
	private String reportType;
	/*是否需要拆分细胞小组  A:不拆分细胞小组，B:拆分细胞小组 */
	private String reportQtyControlWay;
	/*单价*/
	private double price;
	//五人座单价
	private double fivesSeatPrice;
	//七人座单价
	private double sevenSeatPrice;
	/*预警时间*/
	private int amaranTime;

	/* 工序类 */
	private Process procedure;

	// 临时字段，不存表
	private String procedureNumber; //工序编号
	private String procedureName; //工序名称
	private int deptId;				// 部门id
	private int prodId;				// 生产订单id
	private int prodEntryId;		// 生产订单分录id
	private String prodNo;			// 生产订单号
	private double prodQty;			// 生产订单数
	private int salOrderId;			// 销售订单内码id
	private int salOrderEntryId;	// 销售订单分录id
	private String salOrderNo;		// 销售订单号
	private int mtlId;				// 物料id
	private boolean defaultProcess;	// 是否默认工序
	private int seatNum;			// 座位数id
	private String carModelName;	// 车型名称
	

	public ProcessflowEntry() {
		super();
	}

	public double getFivesSeatPrice() {
		return fivesSeatPrice;
	}

	public void setFivesSeatPrice(double fivesSeatPrice) {
		this.fivesSeatPrice = fivesSeatPrice;
	}

	public double getSevenSeatPrice() {
		return sevenSeatPrice;
	}

	public void setSevenSeatPrice(double sevenSeatPrice) {
		this.sevenSeatPrice = sevenSeatPrice;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getProcedureId() {
		return procedureId;
	}

	public void setProcedureId(int procedureId) {
		this.procedureId = procedureId;
	}

	public int getProcessflowId() {
		return processflowId;
	}

	public void setProcessflowId(int processflowId) {
		this.processflowId = processflowId;
	}

	public String getProcessflowElementNumber() {
		return processflowElementNumber;
	}

	public void setProcessflowElementNumber(String processflowElementNumber) {
		this.processflowElementNumber = processflowElementNumber;
	}

	public String getImgUrl() {
		return imgUrl;
	}

	public void setImgUrl(String imgUrl) {
		this.imgUrl = imgUrl;
	}

	public int getWageTypeId() {
		return wageTypeId;
	}

	public void setWageTypeId(int wageTypeId) {
		this.wageTypeId = wageTypeId;
	}

	public int getFlowTypeId() {
		return flowTypeId;
	}

	public void setFlowTypeId(int flowTypeId) {
		this.flowTypeId = flowTypeId;
	}

	public String getReportWay() {
		return reportWay;
	}

	public void setReportWay(String reportWay) {
		this.reportWay = reportWay;
	}

	public String getReportType() {
		return reportType;
	}

	public void setReportType(String reportType) {
		this.reportType = reportType;
	}

	public String getReportQtyControlWay() {
		return reportQtyControlWay;
	}

	public void setReportQtyControlWay(String reportQtyControlWay) {
		this.reportQtyControlWay = reportQtyControlWay;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getAmaranTime() {
		return amaranTime;
	}

	public void setAmaranTime(int amaranTime) {
		this.amaranTime = amaranTime;
	}

	public Process getProcedure() {
		return procedure;
	}

	public void setProcedure(Process procedure) {
		this.procedure = procedure;
	}

	public String getProcedureNumber() {
		return procedureNumber;
	}

	public void setProcedureNumber(String procedureNumber) {
		this.procedureNumber = procedureNumber;
	}

	public String getProcedureName() {
		return procedureName;
	}

	public void setProcedureName(String procedureName) {
		this.procedureName = procedureName;
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

	public int getMtlId() {
		return mtlId;
	}

	public void setMtlId(int mtlId) {
		this.mtlId = mtlId;
	}

	public boolean isDefaultProcess() {
		return defaultProcess;
	}

	public void setDefaultProcess(boolean defaultProcess) {
		this.defaultProcess = defaultProcess;
	}

	public int getSeatNum() {
		return seatNum;
	}

	public void setSeatNum(int seatNum) {
		this.seatNum = seatNum;
	}

	public int getDeptId() {
		return deptId;
	}

	public void setDeptId(int deptId) {
		this.deptId = deptId;
	}

	public String getCarModelName() {
		return carModelName;
	}

	public void setCarModelName(String carModelName) {
		this.carModelName = carModelName;
	}

}
