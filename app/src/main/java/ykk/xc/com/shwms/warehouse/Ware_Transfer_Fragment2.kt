package ykk.xc.com.shwms.warehouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.OnClick
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import kotlinx.android.synthetic.main.ware_transfer_fragment2.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.basics.MoreBatchInputDialog
import ykk.xc.com.shwms.basics.Mtl_DialogActivity
import ykk.xc.com.shwms.basics.Stock_GroupDialogActivity
import ykk.xc.com.shwms.bean.*
import ykk.xc.com.shwms.bean.k3Bean.*
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import java.io.IOException
import java.io.Serializable
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/**
 * 日期：2020-07-22 09:50
 * 描述：自由调拨---添加明细
 * 作者：ykk
 */
class Ware_Transfer_Fragment2 : BaseFragment() {

    companion object {
        private val SEL_POSITION = 61
        private val SEL_POSITION2 = 62
        private val SEL_MTL = 63
        private val SUCC1 = 200
        private val UNSUCC1 = 500
        private val SUCC2 = 201
        private val UNSUCC2 = 501
        private val SAVE = 202
        private val UNSAVE = 502

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val RESULT_PRICE = 3
        private val RESULT_NUM = 4
        private val RESULT_BATCH = 6
        private val RESULT_REMAREK = 7
        private val WRITE_CODE = 8
        private val SM_RESULT_NUM = 10
    }
    private val context = this
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private var stock :Stock_App? = null
    private var stockPlace :StockPlace_App? = null
    private var stock2 :Stock_App? = null
    private var stockPlace2 :StockPlace_App? = null
    private var mContext: Activity? = null
    private val df = DecimalFormat("#.######")
    private var parent: Ware_Transfer_MainActivity? = null
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var timesTamp:String? = null // 时间戳
    var icstockBillEntry = ICStockBillEntry_App()
    private var smICStockBillEntry: ICStockBillEntry_App? = null // 扫码返回的对象
    private var autoICStockBillEntry: ICStockBillEntry_App? = null // 用于自动保存记录的对象
    private var smICStockBillEntry_Barcodes = ArrayList<ICStockBillEntryBarcode_App>() // 扫码返回的对象
    private var smqFlag = '1' // 扫描类型1：调出位置扫描，2：调入位置扫描，3：物料扫描

    // 消息处理
    private val mHandler = MyHandler(this)
    private class MyHandler(activity: Ware_Transfer_Fragment2) : Handler() {
        private val mActivity: WeakReference<Ware_Transfer_Fragment2>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            val m = mActivity.get()
            if (m != null) {
                m.hideLoadDialog()

                var errMsg: String? = null
                var msgObj: String? = null
                if (msg.obj is String) {
                    msgObj = msg.obj as String
                }
                when (msg.what) {
                    SUCC1 -> { // 扫码成功 进入
                        when(m.smqFlag) {
                            '1'-> { // 调出仓库位置
                                m.resetStockGroup2()
                                m.getStockGroup2(msgObj)
                            }
                            '2'-> { // 调入位置
                                m.resetStockGroup()
                                m.getStockGroup(msgObj)
                            }
                            '3'-> { // 物料
                                val icEntry = JsonUtil.strToObject(msgObj, ICStockBillEntry_App::class.java)
                                if(m.getValues(m.tv_mtlName).length > 0 && m.smICStockBillEntry != null && m.smICStockBillEntry!!.fitemId != icEntry.fitemId) {
                                    m.autoSave(icEntry) // 如果扫描第二次和第一次id一样，且分录id大于0，就自动保存

                                } else {
                                    m.getMaterial(icEntry)
                                }
                            }
                        }
                        m.isTextChange = false
                    }
                    UNSUCC1 -> { // 扫码失败
                        m.isTextChange = false
                        when(m.smqFlag) {
                            '1' -> m.tv_outPositionName.text = ""
                            '2' -> m.tv_inPositionName.text = ""
                            '3' -> m.tv_icItemName.text = ""
                        }
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.mContext, errMsg)
                    }
                    SUCC2 -> { // 查询库存 进入
                        val fqty = JsonUtil.strToString(msgObj)
                        m.tv_stockQty.text = Html.fromHtml("即时库存：<font color='#6a5acd'>"+m.df.format(m.parseDouble(fqty))+"</font>")
                    }
                    UNSUCC2 -> { // 查询库存  失败
                        m.tv_stockQty.text = "即时库存：0"
                    }
                    SAVE -> { // 保存成功 进入
                        // 保存了分录，供应商就不能修改
//                        m.setEnables(m.parent!!.fragment1.tv_suppSel, R.drawable.back_style_gray2a,false)
                        EventBus.getDefault().post(EventBusEntity(21)) // 发送指令到fragment3，告其刷新
                        m.reset()
//                        m.toasts("保存成功✔")
                        // 如果有自动保存的对象，保存后就显示下一个
                        if(m.autoICStockBillEntry != null) {
                            m.toasts("自动保存成功✔")
                            m.getMaterial(m.autoICStockBillEntry!!)
                            m.autoICStockBillEntry = null

                        } else {
                            m.toasts("保存成功✔")
                        }
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
                        Comm.showWarnDialog(m.mContext, errMsg)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        when(m.smqFlag) {
                            '1'-> m.setFocusable(m.et_outPositionCode)
                            '2'-> m.setFocusable(m.et_inPositionCode)
                            '3'-> m.setFocusable(m.et_code)
                        }
                    }
                    SAOMA -> { // 扫码之后
                        // 执行查询方法
                        m.run_smDatas(0)
                    }
                }
            }
        }
    }

    @Subscribe
    fun onEventBus(entity: EventBusEntity) {
        when (entity.caseId) {
            11 -> { // 接收第一个页面发来的指令
                reset()
            }
            31 -> { // 接收第三个页面发来的指令
                var icEntry = entity.obj as ICStockBillEntry_App
                btn_save.text = "保存"
                smICStockBillEntry_Barcodes.clear()
                smICStockBillEntry_Barcodes.addAll(icEntry.icstockBillEntryBarcodes)
                getICStockBillEntry(icEntry)
            }
        }
    }

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.ware_transfer_fragment2, container, false)
    }

    override fun initView() {
        mContext = getActivity()
        parent = mContext as Ware_Transfer_MainActivity
        EventBus.getDefault().register(this) // 注册EventBus
    }

    override fun initData() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(120, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(120, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }

        getUserInfo()
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        hideSoftInputMode(mContext, et_outPositionCode)
        hideSoftInputMode(mContext, et_inPositionCode)
        hideSoftInputMode(mContext, et_code)

        parent!!.fragment1.icstockBill.fselTranType = 0
        icstockBillEntry.fsourceTranType = 0

        // 显示记录的本地仓库
        showLocalStockGroup()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
        }
    }

    @OnClick(R.id.btn_scan, R.id.btn_mtlSel, R.id.btn_outPositionScan, R.id.btn_outPositionSel, R.id.btn_inPositionScan, R.id.btn_inPositionSel, R.id.tv_num, R.id.tv_batchNo,
             R.id.tv_remark, R.id.btn_save, R.id.btn_clone, R.id.tv_outPositionName, R.id.tv_icItemName)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_outPositionSel -> { // 选择调出仓库
                smqFlag = '1'
                val bundle = Bundle()
                bundle.putSerializable("stock", stock2)
                bundle.putSerializable("stockPlace", stockPlace2)
                showForResult(context, Stock_GroupDialogActivity::class.java, SEL_POSITION2, bundle)
            }
            R.id.btn_inPositionSel -> { // 选择调入仓库
                smqFlag = '2'
                val bundle = Bundle()
                bundle.putSerializable("stock", stock)
                bundle.putSerializable("stockPlace", stockPlace)
                showForResult(context, Stock_GroupDialogActivity::class.java, SEL_POSITION, bundle)
            }
            R.id.btn_mtlSel -> { // 选择物料
                smqFlag = '3'
                val bundle = Bundle()
                showForResult(Mtl_DialogActivity::class.java, SEL_MTL, bundle)
            }
            R.id.btn_outPositionScan -> { // 调用摄像头扫描（调出位置）
                smqFlag = '1'
                ScanUtil.startScan(mContext, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create());
            }
            R.id.btn_inPositionScan -> { // 调用摄像头扫描（调入位置）
                smqFlag = '2'
                ScanUtil.startScan(mContext, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create());
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                smqFlag = '3'
                ScanUtil.startScan(mContext, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create());
            }
            R.id.tv_outPositionName -> { // 调出位置点击
                smqFlag = '1'
                mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
            }
            R.id.tv_inPositionName -> { // 调入位置点击
                smqFlag = '2'
                mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
            }
            R.id.tv_icItemName -> { // 物料点击
                smqFlag = '3'
                mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
            }
            R.id.tv_price -> { // 单价
//                showInputDialog("单价", icstockBillEntry.fprice.toString(), "0.0", RESULT_PRICE)
            }
            R.id.tv_num -> { // 数量
                showInputDialog("单位数", icstockBillEntry.fqty.toString(), "0.0", RESULT_NUM)
            }
            R.id.tv_batchNo -> { // 批次号
                val bundle = Bundle()
                bundle.putInt("icstockBillEntryId", icstockBillEntry.id)
                bundle.putSerializable("icstockBillEntryBarcodes", icstockBillEntry.icstockBillEntryBarcodes as Serializable)
                bundle.putString("userName", user!!.username)
                bundle.putString("barcode", getValues(et_code))
                bundle.putInt("againUse", 1)
                showForResult(MoreBatchInputDialog::class.java, RESULT_BATCH, bundle)
            }
            R.id.tv_remark -> { // 备注
                showInputDialog("备注", icstockBillEntry.remark, "none", RESULT_REMAREK)
            }
            R.id.btn_save -> { // 保存
                if(!checkSave()) return
                icstockBillEntry.icstockBillId = parent!!.fragment1.icstockBill.id
                run_save(null);
            }
            R.id.btn_clone -> { // 重置
                if (checkSaveHint()) {
                    val build = AlertDialog.Builder(mContext)
                    build.setIcon(R.drawable.caution)
                    build.setTitle("系统提示")
                    build.setMessage("您有未保存的数据，继续重置吗？")
                    build.setPositiveButton("是") { dialog, which -> reset() }
                    build.setNegativeButton("否", null)
                    build.setCancelable(false)
                    build.show()

                } else {
                    reset()
                }
            }
        }
    }

    /**
     * 显示记录的本地仓库
     */
    private fun showLocalStockGroup() {
        // 调出仓库
        val saveOther = getResStr(R.string.saveDefaultStock)
        val spfStock = spf(saveOther)
        if(spfStock.contains("BIND_ZYDB_OUT_STOCK")) {
            stock2 = showObjectByXml(Stock_App::class.java, "BIND_ZYDB_OUT_STOCK", saveOther)
        }
        if(spfStock.contains("BIND_ZYDB_OUT_STOCKPOS")) {
            stockPlace2 = showObjectByXml(StockPlace_App::class.java, "BIND_ZYDB_OUT_STOCKPOS", saveOther)
        }
        getStockGroup2(null)
        // 调入仓库
        if(spfStock.contains("BIND_ZYDB_IN_STOCK")) {
            stock = showObjectByXml(Stock_App::class.java, "BIND_ZYDB_IN_STOCK", saveOther)
        }
        if(spfStock.contains("BIND_ZYDB_IN_STOCKPOS")) {
            stockPlace = showObjectByXml(StockPlace_App::class.java, "BIND_ZYDB_IN_STOCKPOS", saveOther)
        }
        getStockGroup(null)
    }

    /**
     *  自动保存信息
     */
    private fun autoSave(icEntry: ICStockBillEntry_App) {
        // 上次扫的和这次的不同，就自动保存
        if(!checkSave()) return
        icstockBillEntry.icstockBillId = parent!!.fragment1.icstockBill.id

        autoICStockBillEntry = icEntry // 加到自动保存对象
        run_save(null)
    }

    /**
     * 检查数据
     */
    fun checkSave() : Boolean {
//        if(icstockBillEntry.id == 0) {
        if(icstockBillEntry.fitemId == 0) {
            Comm.showWarnDialog(mContext, "请扫码物料条码，或点击表体列表！")
            return false
        }
        if (icstockBillEntry.fscStockId == 0 || stock2 == null) {
            Comm.showWarnDialog(mContext, "请选择或扫描调出位置！")
            return false
        }
        if (icstockBillEntry.fdcStockId == 0 || stock == null) {
            Comm.showWarnDialog(mContext, "请选择或扫描调入位置！")
            return false
        }
        if(icstockBillEntry.fscStockId == icstockBillEntry.fdcStockId) {
            Comm.showWarnDialog(mContext, "调出仓库和调入仓库不能一样！")
            return false
        }
//        if (icstockBillEntry.fprice == 0.0) {
//            Comm.showWarnDialog(mContext, "请输入单价！")
//            return false;
//        }
        if(icstockBillEntry.icItem.batchManager == 1 && icstockBillEntry.icstockBillEntryBarcodes.size == 0) {
            Comm.showWarnDialog(mContext, "请输入批次！")
            return false
        }
        if (icstockBillEntry.fqty == 0.0) {
            Comm.showWarnDialog(mContext, "请输入调拨数！")
            return false
        }
        return true
    }

    /**
     * 选择了物料没有点击保存，点击了重置，需要提示
     */
    fun checkSaveHint() : Boolean {
        if(icstockBillEntry.fitemId > 0) {
            return true
        }
        return false
    }

    override fun setListener() {
        val click = View.OnClickListener { v ->
            setFocusable(et_getFocus)
            when (v.id) {
                R.id.et_outPositionCode -> setFocusable(et_outPositionCode)
                R.id.et_inPositionCode -> setFocusable(et_inPositionCode)
                R.id.et_code -> setFocusable(et_code)
            }
        }
        et_outPositionCode!!.setOnClickListener(click)
        et_inPositionCode!!.setOnClickListener(click)
        et_code!!.setOnClickListener(click)

        // 调出仓库---数据变化
        et_outPositionCode!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    smqFlag = '1'
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 调出仓库---长按输入条码
        et_outPositionCode!!.setOnLongClickListener {
            smqFlag = '1'
            showInputDialog("输入条码", "", "none", WRITE_CODE)
            true
        }
        // 调出仓库---焦点改变
        et_outPositionCode.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                lin_outFocusPosition.setBackgroundResource(R.drawable.back_style_red_focus)
            } else {
                if (lin_outFocusPosition != null) {
                    lin_outFocusPosition!!.setBackgroundResource(R.drawable.back_style_gray4)
                }
            }
        }

        // 调入仓库---数据变化
        et_inPositionCode!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    smqFlag = '2'
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 调入仓库---长按输入条码
        et_inPositionCode!!.setOnLongClickListener {
            smqFlag = '2'
            showInputDialog("输入条码", "", "none", WRITE_CODE)
            true
        }
        // 调入仓库---焦点改变
        et_inPositionCode.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                lin_inFocusPosition.setBackgroundResource(R.drawable.back_style_red_focus)
            } else {
                if (lin_inFocusPosition != null) {
                    lin_inFocusPosition!!.setBackgroundResource(R.drawable.back_style_gray4)
                }
            }
        }

        // 物料---数据变化
        et_code!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    smqFlag = '3'
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 物料---长按输入条码
        et_code!!.setOnLongClickListener {
            smqFlag = '3'
            showInputDialog("输入条码号", getValues(et_code), "none", WRITE_CODE)
            true
        }
        // 物料---焦点改变
        et_code.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if(hasFocus) {
                lin_focusMtl.setBackgroundResource(R.drawable.back_style_red_focus)
            } else {
                if (lin_focusMtl != null) {
                    lin_focusMtl.setBackgroundResource(R.drawable.back_style_gray4)
                }
            }
        }

    }

    /**
     * 0：表示点击重置，1：表示保存后重置
     */
    private fun reset() {
        setEnables(tv_batchNo, R.drawable.back_style_blue, true)
        setEnables(tv_num, R.drawable.back_style_blue, true)
        btn_save.text = "添加"
        tv_mtlName.text = ""
        tv_mtlNumber.text = "物料代码："
        tv_fmodel.text = "规格型号："
        tv_unitName.text = "单位："
        tv_stockQty.text = "即时库存：0"
        tv_batchNo.text = ""
        tv_num.text = ""
        tv_remark.text = ""

        icstockBillEntry.id = 0
        icstockBillEntry.icstockBillId = parent!!.fragment1.icstockBill.id
        icstockBillEntry.fitemId = 0
//        icstockBillEntry.fdcStockId = 0
//        icstockBillEntry.fdcSPId = 0
        icstockBillEntry.fqty = 0.0
        icstockBillEntry.fprice = 0.0
        icstockBillEntry.funitId = 0
        icstockBillEntry.remark = ""

        icstockBillEntry.icItem = null
        icstockBillEntry.icstockBillEntryBarcodes.clear()
        smICStockBillEntry = null
        smICStockBillEntry_Barcodes.clear()
//        stock = null
//        stockPlace = null
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        parent!!.isChange = false
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    /**
     *  扫码之后    物料启用批次
     */
    fun setBatchCode(fqty : Double) {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = smICStockBillEntry!!.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = smICStockBillEntry!!.smBatchCode
        entryBarcode.snCode = ""
        entryBarcode.fqty = fqty
        entryBarcode.isUniqueness = 'Y'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = parent!!.fragment1.icstockBill.billType

        smICStockBillEntry_Barcodes.add(entryBarcode)
        getICStockBillEntry(smICStockBillEntry!!)
    }

    /**
     *  扫码之后    物料启用序列号
     */
    fun setSnCode() {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = smICStockBillEntry!!.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = ""
        entryBarcode.snCode = smICStockBillEntry!!.smSnCode
        entryBarcode.fqty = 1.0
        entryBarcode.isUniqueness = 'Y'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = parent!!.fragment1.icstockBill.billType

        smICStockBillEntry_Barcodes.add(entryBarcode)
        getICStockBillEntry(smICStockBillEntry!!)
    }

    /**
     *  扫码之后    物料未启用
     */
    fun unSetBatchOrSnCode(fqty : Double) {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = smICStockBillEntry!!.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = ""
        entryBarcode.snCode = ""
        entryBarcode.fqty = fqty
        entryBarcode.isUniqueness = 'N'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = parent!!.fragment1.icstockBill.billType

        smICStockBillEntry_Barcodes.add(entryBarcode)
        getICStockBillEntry(smICStockBillEntry!!)
    }

    fun getMaterial(icEntry: ICStockBillEntry_App) {
        smICStockBillEntry = icEntry

        btn_save.text = "保存"
        // 判断条码是否存在（启用批次，序列号）
        if (icstockBillEntry.icstockBillEntryBarcodes.size > 0 && (icEntry.icItem.batchManager == 1 || icEntry.icItem.snManager == 1)) {
            icstockBillEntry.icstockBillEntryBarcodes.forEach {
                if (getValues(et_code).length > 0 && getValues(et_code) == it.barcode) {
                    Comm.showWarnDialog(mContext,"条码已使用！")
                    return
                }
            }
        }
        if(icEntry.icItem.batchManager == 1) { // 启用批次号
            val showInfo:String = "<font color='#666666'>批次号：</font>" + icEntry.smBatchCode+"<font color='#666666'>可用库存：</font>"+"<font color='#FF2200'>"+df.format(icEntry.inventoryNowQty)+"</font>"
            showInputDialog("数量", showInfo, icEntry.smQty.toString(), "0.0", SM_RESULT_NUM)

        } else if(icEntry.icItem.snManager == 1) { // 启用序列号
            setSnCode()

        } else { // 未启用
            unSetBatchOrSnCode(icEntry.smQty)
        }
        if(icEntry.icstockBillEntryBarcodes.size > 0) {
            if (smICStockBillEntry_Barcodes.size > 0) {
                var isBool = true
                icEntry.icstockBillEntryBarcodes.forEach {
                    isBool = false
                    for (it2 in smICStockBillEntry_Barcodes) {
                        if(it.barcode == it2.barcode) {
                            isBool = false
                            break
                        }
                    }
                    if(isBool) {
                        smICStockBillEntry_Barcodes.add(it)
                    }
                }
            } else {
                smICStockBillEntry_Barcodes.addAll(icEntry.icstockBillEntryBarcodes)
            }
        } else {
            smICStockBillEntry_Barcodes.addAll(icEntry.icstockBillEntryBarcodes)
        }
    }

    fun getICStockBillEntry(icEntry: ICStockBillEntry_App) {
        icstockBillEntry.id = icEntry.id
        icstockBillEntry.icstockBillId = icEntry.icstockBillId
        icstockBillEntry.fitemId = icEntry.fitemId
        icstockBillEntry.fentryId = icEntry.fentryId
        icstockBillEntry.fsourceQty = icEntry.fsourceQty
        icstockBillEntry.fprice = icEntry.fprice
        icstockBillEntry.funitId = icEntry.funitId
        icstockBillEntry.remark = icEntry.remark

        icstockBillEntry.icItem = icEntry.icItem
        icstockBillEntry.unit = icEntry.unit
        icstockBillEntry.icstockBillEntryBarcodes = icEntry.icstockBillEntryBarcodes

        tv_mtlName.text = icEntry.icItem.fname
        tv_mtlNumber.text = Html.fromHtml("物料代码：<font color='#6a5acd'>"+icEntry.icItem.fnumber+"</font>")
        tv_fmodel.text = Html.fromHtml("规格型号：<font color='#6a5acd'>"+ isNULLS(icEntry.icItem.fmodel) +"</font>")
        tv_unitName.text = Html.fromHtml("单位：<font color='#6a5acd'>"+icEntry.unit.fname+"</font>")
        if(icEntry.icItem.batchManager == 1) {
            setEnables(tv_batchNo, R.drawable.back_style_blue, true)
        } else {
            setEnables(tv_batchNo, R.drawable.back_style_gray3, false)
        }
        if(icEntry.icItem.batchManager == 1 || icEntry.icItem.snManager == 1) {
            setEnables(tv_num, R.drawable.back_style_gray3, false)
        } else {
            setEnables(tv_num, R.drawable.back_style_blue, true)
        }
        tv_remark.text = icEntry.remark

        // 显示调出仓库，仓位
        if(icEntry.stock2 != null) { // 出入库保存的仓库
            stock2 = icEntry.stock2
            stockPlace2 = icEntry.stockPlace2

        } else if(icEntry.icItem.stock != null) { // 物料默认仓库
            stock2 = icEntry.icItem.stock
            stockPlace2 = icEntry.icItem.stockPlace
        }
        getStockGroup2(null)

        // 显示调入仓库
        if(icEntry.fdcStockId > 0) {
            stock = icEntry.stock
            stockPlace = icEntry.stockPlace
        }
        getStockGroup(null)

        // 物料未启用
        if(icEntry.icstockBillEntryBarcodes.size > 0 && icEntry.icItem.batchManager == 0 && icEntry.icItem.snManager == 0) {
            showBatch_Qty(null, icEntry.fqty)
        } else {
            // 显示多批次
            showBatch_Qty(smICStockBillEntry_Barcodes, icEntry.fqty)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SEL_POSITION -> {// 调入仓库	返回
                    resetStockGroup()
                    stock = data!!.getSerializableExtra("stock") as Stock_App
                    if (data!!.getSerializableExtra("stockPlace") != null) {
                        stockPlace = data!!.getSerializableExtra("stockPlace") as StockPlace_App
                    }
                    getStockGroup(null)
                }
                SEL_POSITION2 -> {// 调出仓库	返回
                    resetStockGroup2()
                    stock2 = data!!.getSerializableExtra("stock") as Stock_App
                    if (data!!.getSerializableExtra("stockPlace") != null) {
                        stockPlace2 = data!!.getSerializableExtra("stockPlace") as StockPlace_App
                    }
                    getStockGroup2(null)
                }
                SEL_MTL -> { //查询物料	返回
                    val icItem = data!!.getSerializableExtra("obj") as ICItem_App
                    smqFlag = '3'
                    run_smDatas(icItem.fitemId)
                }
                RESULT_PRICE -> { // 单价	返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        val price = parseDouble(value)
//                        tv_price.text = df.format(price)
//                        icstockBillEntry.fprice = price
//                        if(icstockBillEntry.fqty > 0) {
//                            val mul = BigdecimalUtil.mul(price, icstockBillEntry.fqty)
//                            tv_sumMoney.text = df.format(mul)
//                        }
                    }
                }
                RESULT_NUM -> { // 数量	返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        val num = parseDouble(value)
                        tv_num.text = df.format(num)
                        icstockBillEntry.fqty = num
                    }
                }
                SM_RESULT_NUM -> { // 扫码数量	    返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        val num = parseDouble(value)
                        setBatchCode(num)
                    }
                }
                RESULT_BATCH -> { // 批次号	返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val list = bundle.getSerializable("icstockBillEntryBarcodes") as List<ICStockBillEntryBarcode_App>
                        smICStockBillEntry_Barcodes.clear()
                        smICStockBillEntry_Barcodes.addAll(list)
                        showBatch_Qty(smICStockBillEntry_Barcodes, 0.0)
                    }
                }
                RESULT_REMAREK -> { // 备注	返回
                    val bundle = data!!.getExtras()
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        tv_remark.text = value
                        icstockBillEntry.remark = value
                    }
                }
                WRITE_CODE -> {// 输入条码  返回
                    val bundle = data!!.extras
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        when (smqFlag) {
                            '1' -> setTexts(et_outPositionCode, value.toUpperCase())
                            '2' -> setTexts(et_inPositionCode, value.toUpperCase())
                            '3' -> setTexts(et_code, value.toUpperCase())
                        }
                    }
                }
            }
        }
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    /**
     * 调用华为扫码接口，返回的值
     */
    fun getScanData(barcode :String) {
        when (smqFlag) {
            '1' -> setTexts(et_outPositionCode, barcode)
            '2' -> setTexts(et_inPositionCode, barcode)
            '3' -> setTexts(et_code, barcode)
        }
    }

    /**
     *  显示批次号和数量
     */
    fun showBatch_Qty(list : List<ICStockBillEntryBarcode_App>?, fqty : Double) {
        if(list != null && list.size > 0) {
            val strBatch = StringBuffer()
            var sumQty = 0.0
            val listBatch = ArrayList<String>()

            list.forEach{
                if(Comm.isNULLS(it.batchCode).length > 0 && !listBatch.contains(it.batchCode)) {
                    listBatch.add(it.batchCode)
                }
                sumQty += it.fqty
            }
            listBatch.forEach {
                strBatch.append(it + "，")
            }
            // 删除最后一个，
            if (strBatch.length > 0) {
                strBatch.delete(strBatch.length - 1, strBatch.length)
            }
            tv_batchNo.text = strBatch.toString()
            tv_num.text = df.format(sumQty)

            icstockBillEntry.fqty = sumQty
            icstockBillEntry.icstockBillEntryBarcodes.clear()
            icstockBillEntry.icstockBillEntryBarcodes.addAll(list)
        } else {
            icstockBillEntry.fqty = fqty
            tv_batchNo.text = ""
            tv_num.text = if(fqty > 0) df.format(fqty) else ""
        }
    }

    fun resetStockGroup() {
        stock = null
        stockPlace = null
    }

    fun resetStockGroup2() {
        stock2 = null
        stockPlace2 = null
    }

    /**
     * 得到调入仓库组
     */
    fun getStockGroup(msgObj : String?) {
        // 重置数据
        icstockBillEntry.fdcStockId = 0
        icstockBillEntry.fdcSPId = 0
        icstockBillEntry.stock = null
        icstockBillEntry.stockPlace = null

        if(msgObj != null) {
            stock = null
            stockPlace = null

            var caseId:Int = 0
            if(msgObj.indexOf("Stock_CaseId=1") > -1) {
                caseId = 1
            } else if(msgObj.indexOf("StockPlace_CaseId=2") > -1) {
                caseId = 2
            }

            when(caseId) {
                1 -> {
                    stock = JsonUtil.strToObject(msgObj, Stock_App::class.java)
                    tv_inPositionName.text = stock!!.fname
                }
                2 -> {
                    stockPlace = JsonUtil.strToObject(msgObj, StockPlace_App::class.java)
                    tv_inPositionName.text = stockPlace!!.fname
                    if(stockPlace!!.stock != null) stock = stockPlace!!.stock
                }
            }
        }

        if(stock != null ) {
            tv_inPositionName.text = stock!!.fname
            icstockBillEntry.fdcStockId = stock!!.fitemId
            icstockBillEntry.stock = stock
        }
        if(stockPlace != null ) {
            tv_inPositionName.text = stockPlace!!.fname
            icstockBillEntry.fdcSPId = stockPlace!!.fspId
            icstockBillEntry.stockPlace = stockPlace
        }

        if(stock != null) {
            // 自动跳到物料焦点
            smqFlag = '3'
            mHandler.sendEmptyMessage(SETFOCUS)
        }
    }

    /**
     * 得到调出仓库组
     */
    fun getStockGroup2(msgObj : String?) {
        // 重置数据
        icstockBillEntry.fscStockId = 0
        icstockBillEntry.fscSPId = 0
        icstockBillEntry.stock2 = null
        icstockBillEntry.stockPlace2 = null

        if(msgObj != null) {
            stock2 = null
            stockPlace2 = null

            var caseId:Int = 0
            if(msgObj.indexOf("Stock_CaseId=1") > -1) {
                caseId = 1
            } else if(msgObj.indexOf("StockPlace_CaseId=2") > -1) {
                caseId = 2
            }

            when(caseId) {
                1 -> {
                    stock2 = JsonUtil.strToObject(msgObj, Stock_App::class.java)
                    tv_outPositionName.text = stock2!!.fname
                }
                2 -> {
                    stockPlace2 = JsonUtil.strToObject(msgObj, StockPlace_App::class.java)
                    tv_outPositionName.text = stockPlace2!!.fname
                    if(stockPlace2!!.stock != null) stock2 = stockPlace2!!.stock
                }
            }
        }

        if(stock2 != null ) {
            tv_outPositionName.text = stock2!!.fname
            icstockBillEntry.fscStockId = stock2!!.fitemId
            icstockBillEntry.stock2 = stock2
        }
        if(stockPlace2 != null ) {
            tv_outPositionName.text = stockPlace2!!.fname
            icstockBillEntry.fscSPId = stockPlace2!!.fspId
            icstockBillEntry.stockPlace2 = stockPlace2
        }

        if(stock2 != null) {
            // 自动跳到调入仓焦点
            smqFlag = '2'
            mHandler.sendEmptyMessage(SETFOCUS)
        }
        if(icstockBillEntry.fitemId > 0 && icstockBillEntry.fscStockId > 0) {
            // 查询即时库存
            run_findQty()
        }
    }

    /**
     * 扫码查询对应的方法
     */
    private fun run_smDatas(mtlId : Int) {
        showLoadDialog("加载中...", false)
        var mUrl:String? = null
        var barcode = ""
        var icstockBillId = ""
        var billType = "" // 单据类型
        when(smqFlag) {
            '1' -> {
                mUrl = getURL("stockPosition/findBarcodeGroup")
                barcode = getValues(et_outPositionCode)
            }
            '2' -> {
                mUrl = getURL("stockPosition/findBarcodeGroup")
                barcode = getValues(et_inPositionCode)
            }
            '3' -> {
                mUrl = getURL("stockBill_WMS/findBarcode_EntryItem")
                barcode = getValues(et_code)
                icstockBillId = parent!!.fragment1.icstockBill.id.toString()
                billType = parent!!.fragment1.icstockBill.billType
            }
        }
        val formBody = FormBody.Builder()
                .add("barcode", barcode)
                .add("icstockBillId", icstockBillId)
                .add("billType", billType)
                .add("fitemId", mtlId.toString())
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC1)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_smDatas --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC1, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SUCC1, result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 保存
     */
    private fun run_save(list: List<ICStockBillEntry_App>?) {
        showLoadDialog("保存中...", false)
        var mUrl:String? = null
        var mJson:String? = null
        if(list != null) {
            mUrl = getURL("stockBill_WMS/saveEntryList")
            mJson = JsonUtil.objectToString(list)
        } else {
            mUrl = getURL("stockBill_WMS/saveEntry")
            mJson = JsonUtil.objectToString(icstockBillEntry)
        }
        val formBody = FormBody.Builder()
                .add("strJson", mJson)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSAVE)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSAVE, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SAVE, result)
                LogUtil.e("run_save --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 查询库存
     */
    private fun run_findQty() {
        showLoadDialog("加载中...", false)
        val mUrl = getURL("icInventory/findQty")
        val formBody = FormBody.Builder()
                .add("fitemId", icstockBillEntry.fitemId.toString())
                .add("fstockId", icstockBillEntry.fscStockId.toString())
                .add("fstockPlaceId",  icstockBillEntry.fscSPId.toString())
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC2)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_findQty --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC2, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SUCC2, result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 得到用户对象
     */
    private fun getUserInfo() {
        if (user == null) user = showUserByXml()
    }

    override fun onDestroyView() {
        closeHandler(mHandler)
        mBinder!!.unbind()
        EventBus.getDefault().unregister(this);
        super.onDestroyView()
    }
}