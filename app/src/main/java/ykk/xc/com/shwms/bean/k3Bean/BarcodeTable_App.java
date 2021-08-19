package ykk.xc.com.shwms.bean.k3Bean;

/**
 * ����bean
 * @author Administrator
 *
 */
public class BarcodeTable_App {

	private int id;
	/**
	 * 1：仓库
	 * 2：库区
	 * 3：货架
	 * 4：库位
	 * 5：容器
	 * 6：供应商
	 * 7：客户
	 * 8：部门
	 *
	 * 21：物料
	 * 22：物料包装
	 * 23：盘点表
	 * 31：生产订单
	 * 41：采购订单
	 * 42：收料通知单
	 * 51：销售订单
	 * 52：发货通知单
	 * 61：委外订单
	 * 71：资产卡片
	 * 81：其他入库单
	 */
	private int caseId;						// 生码类型
	private String snCode;					// 序列号
	private String batchCode;				// 批次号
	private int relationBillId;				// 关联单据id
	private String relationBillNumber;		// 关联单据号
	private int relationBillEntryId;		// 关联单据分录id
	private int printNumber;				// 打印次数
	private int icItemId;					// 项目id
	private String icItemNumber;			// 项目代码
	private String icItemName;				// 项目名称
	private String barcode;					// 条码
	private String createDateTime;			// 创建时间
	private String createrName;				// 创建人名称
	private double barcodeQty;				// 条码数量
	private String productDate;				// 生产日期
	private int supplierId;					// 供应商ID 或者生产车间ID*/
	private String jdIcItemNumber;			// 套餐产品脚垫物料代码
	private String jdIcItemName;			// 套餐产品脚垫物料名称
	private String jdCarModel;				// 套餐产品脚垫车型名称
	private String jdColor;					// 套餐产品脚垫颜色
	private String hbxdIcItemNumber;		// 套餐产品后备箱垫物料代码
	private String hbxdIcItemName;			// 套餐产品后备箱垫物料名称
	private String hbxdCarModel;			// 套餐产品后备箱垫车型名称
	private String hbxdColor;				// 套餐产品后备箱垫颜色

	private ICItem_App icItem;
	private Department_App dept;

	// 临时字段，不存表
	private String relationObj; // 关联对象

	public BarcodeTable_App() {
		super();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getCaseId() {
		return caseId;
	}

	public void setCaseId(int caseId) {
		this.caseId = caseId;
	}

	public String getSnCode() {
		return snCode;
	}

	public void setSnCode(String snCode) {
		this.snCode = snCode;
	}

	public String getBatchCode() {
		return batchCode;
	}

	public void setBatchCode(String batchCode) {
		this.batchCode = batchCode;
	}

	public int getRelationBillId() {
		return relationBillId;
	}

	public void setRelationBillId(int relationBillId) {
		this.relationBillId = relationBillId;
	}

	public String getRelationBillNumber() {
		return relationBillNumber;
	}

	public void setRelationBillNumber(String relationBillNumber) {
		this.relationBillNumber = relationBillNumber;
	}

	public int getRelationBillEntryId() {
		return relationBillEntryId;
	}

	public void setRelationBillEntryId(int relationBillEntryId) {
		this.relationBillEntryId = relationBillEntryId;
	}

	public int getPrintNumber() {
		return printNumber;
	}

	public void setPrintNumber(int printNumber) {
		this.printNumber = printNumber;
	}

	public int getIcItemId() {
		return icItemId;
	}

	public void setIcItemId(int icItemId) {
		this.icItemId = icItemId;
	}

	public String getIcItemNumber() {
		return icItemNumber;
	}

	public void setIcItemNumber(String icItemNumber) {
		this.icItemNumber = icItemNumber;
	}

	public String getIcItemName() {
		return icItemName;
	}

	public void setIcItemName(String icItemName) {
		this.icItemName = icItemName;
	}

	public String getBarcode() {
		return barcode;
	}

	public void setBarcode(String barcode) {
		this.barcode = barcode;
	}

	public String getCreateDateTime() {
		return createDateTime;
	}

	public void setCreateDateTime(String createDateTime) {
		this.createDateTime = createDateTime;
	}

	public String getCreaterName() {
		return createrName;
	}

	public void setCreaterName(String createrName) {
		this.createrName = createrName;
	}

	public double getBarcodeQty() {
		return barcodeQty;
	}

	public void setBarcodeQty(double barcodeQty) {
		this.barcodeQty = barcodeQty;
	}

	public String getProductDate() {
		return productDate;
	}

	public void setProductDate(String productDate) {
		this.productDate = productDate;
	}

	public int getSupplierId() {
		return supplierId;
	}

	public void setSupplierId(int supplierId) {
		this.supplierId = supplierId;
	}

	public String getJdIcItemNumber() {
		return jdIcItemNumber;
	}

	public void setJdIcItemNumber(String jdIcItemNumber) {
		this.jdIcItemNumber = jdIcItemNumber;
	}

	public String getJdIcItemName() {
		return jdIcItemName;
	}

	public void setJdIcItemName(String jdIcItemName) {
		this.jdIcItemName = jdIcItemName;
	}

	public String getJdCarModel() {
		return jdCarModel;
	}

	public void setJdCarModel(String jdCarModel) {
		this.jdCarModel = jdCarModel;
	}

	public String getJdColor() {
		return jdColor;
	}

	public void setJdColor(String jdColor) {
		this.jdColor = jdColor;
	}

	public String getHbxdIcItemNumber() {
		return hbxdIcItemNumber;
	}

	public void setHbxdIcItemNumber(String hbxdIcItemNumber) {
		this.hbxdIcItemNumber = hbxdIcItemNumber;
	}

	public String getHbxdIcItemName() {
		return hbxdIcItemName;
	}

	public void setHbxdIcItemName(String hbxdIcItemName) {
		this.hbxdIcItemName = hbxdIcItemName;
	}

	public String getHbxdCarModel() {
		return hbxdCarModel;
	}

	public void setHbxdCarModel(String hbxdCarModel) {
		this.hbxdCarModel = hbxdCarModel;
	}

	public String getHbxdColor() {
		return hbxdColor;
	}

	public void setHbxdColor(String hbxdColor) {
		this.hbxdColor = hbxdColor;
	}

	public ICItem_App getIcItem() {
		return icItem;
	}

	public void setIcItem(ICItem_App icItem) {
		this.icItem = icItem;
	}

	public Department_App getDept() {
		return dept;
	}

	public void setDept(Department_App dept) {
		this.dept = dept;
	}

	public String getRelationObj() {
		return relationObj;
	}

	public void setRelationObj(String relationObj) {
		this.relationObj = relationObj;
	}

	
	
}
