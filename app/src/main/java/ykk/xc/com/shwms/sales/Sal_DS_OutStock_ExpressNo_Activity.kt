package ykk.xc.com.shwms.sales

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import com.gprinter.command.EscCommand
import com.gprinter.command.LabelCommand
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import kotlinx.android.synthetic.main.sal_ds_out_expressno.*
import okhttp3.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.*
import ykk.xc.com.shwms.bean.k3Bean.BarcodeTable_App
import ykk.xc.com.shwms.bean.k3Bean.SeOrderEntry_App
import ykk.xc.com.shwms.bean.k3Bean.StockPlace_App
import ykk.xc.com.shwms.bean.k3Bean.Stock_App
import ykk.xc.com.shwms.comm.BaseActivity
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.sales.adapter.Sal_DS_OutStockFragment1Adapter
import ykk.xc.com.shwms.util.BigdecimalUtil
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.shwms.util.blueTooth.*
import ykk.xc.com.shwms.util.blueTooth.Constant.MESSAGE_UPDATE_PARAMETER
import ykk.xc.com.shwms.util.blueTooth.DeviceConnFactoryManager.CONN_STATE_FAILED
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/**
 * 日期：2019-10-16 09:14
 * 描述：电商销售出库（根据快递单）
 * 作者：ykk
 */
class Sal_DS_OutStock_ExpressNo_Activity : BaseActivity() {
    companion object {
        private val SEL_POSITION = 61
        private val SUCC1 = 200
        private val UNSUCC1 = 500
        private val SUCC2 = 201
        private val UNSUCC2 = 501
        private val SUCC3 = 202
        private val UNSUCC3 = 502
        private val SAVE = 204
        private val UNSAVE = 504
        private val FIND_PRINT = 205
        private val UNFIND_PRINT = 505

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
        private val RESULT_NUM = 4
    }

    private val context = this
    private val TAG = "Sal_DS_OutStock_ExpressNo_Activity"
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private val df = DecimalFormat("#.######")

    // 蓝牙打印用到的
    private var isConnected: Boolean = false // 蓝牙是否连接标识
    private val id = 0 // 设备id
    private var threadPool: ThreadPool? = null
    private val CONN_STATE_DISCONN = 0x007 // 连接状态断开
    private val PRINTER_COMMAND_ERROR = 0x008 // 使用打印机指令错误
    private val CONN_PRINTER = 0x12
    private var listMap = ArrayList<ExpressNoData>() // 打印保存的数据

    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var timesTamp:String? = null // 时间戳
    private var smqFlag = '1' // 扫描类型1：位置扫描，2：物料扫描
    private var curPos:Int = -1 // 当前行
    private val checkDatas = ArrayList<ICStockBillEntry_App>()
    private var mAdapter: Sal_DS_OutStockFragment1Adapter? = null

    // 消息处理
    private val mHandler = MyHandler(this)
    private class MyHandler(activity: Sal_DS_OutStock_ExpressNo_Activity) : Handler() {
        private val mActivity: WeakReference<Sal_DS_OutStock_ExpressNo_Activity>

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
                    SUCC1 -> { // 扫码（显示列表）成功 进入
                        val list = JsonUtil.strToList(msgObj, SeOrderEntry_App::class.java)
                        m.setICStockEntry_SalOrder(list)
                    }
                    UNSUCC1 -> { // 扫码失败
                        m.et_code.setText("")
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SUCC2 -> { // 扫码（匹配数量）成功 进入
                        val entry = JsonUtil.strToObject(msgObj, ICStockBillEntry_App::class.java)
                        m.setICStockBill_Row(entry)
                    }
                    UNSUCC2 -> { // 扫码失败
                        m.et_code.setText("")
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    FIND_PRINT -> { // 得到打印数据 进入
                        val list = JsonUtil.strToList(msgObj, ExpressNoData::class.java)
                        m.setPrintData(list) // 打印
                    }
                    UNFIND_PRINT -> { // 得到打印数据  失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SAVE -> { // 保存成功 进入
                        m.toasts("保存成功✔")
                        m.btn_scan.isEnabled = false
                        m.et_code.isEnabled = false
                        // 延时执行，因为输入框失去焦点会改变样式
                        m.mHandler.postDelayed(Runnable {
                            m.lin_focusMtl.setBackgroundResource(R.drawable.back_style_gray3)
                        },300)

                        // 保存完成，就查询打印数据
//                        m.run_findPrintData(m.checkDatas[0].icstockBill.expressNo)
                        /*
                        // 汇报最后一个工序
                        if(m.btTmp != null) {
                            m.run_saveProdReprot()
                        }*/
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        m.setFocusable(m.et_code)
                    }
                    SAOMA -> { // 扫码之后
                        // 执行查询方法
                        if(m.checkDatas.size == 0) {
                            m.run_smDatas(SUCC1, UNSUCC1)
                        } else {
                            m.run_smDatas(SUCC2, UNSUCC2)
                        }
                    }
                    m.CONN_STATE_DISCONN -> if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[m.id] != null) {
                        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[m.id].closePort(m.id)
                    }
                    m.PRINTER_COMMAND_ERROR -> Utils.toast(m.context, m.getString(R.string.str_choice_printer_command))
                    m.CONN_PRINTER -> Utils.toast(m.context, m.getString(R.string.str_cann_printer))
                    MESSAGE_UPDATE_PARAMETER -> {
                        val strIp = msg.data.getString("Ip")
                        val strPort = msg.data.getString("Port")
                        //初始化端口信息
                        DeviceConnFactoryManager.Build()
                                //设置端口连接方式
                                .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.WIFI)
                                //设置端口IP地址
                                .setIp(strIp)
                                //设置端口ID（主要用于连接多设备）
                                .setId(m.id)
                                //设置连接的热点端口号
                                .setPort(Integer.parseInt(strPort))
                                .build()
                        m.threadPool = ThreadPool.getInstantiation()
                        m.threadPool!!.addTask(Runnable { DeviceConnFactoryManager.getDeviceConnFactoryManagers()[m.id].openPort() })
                    }
                }
            }
        }
    }

    override fun setLayoutResID(): Int {
        return R.layout.sal_ds_out_expressno
    }

    override fun initView() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(120, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(120, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }
        
        recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(context)
        mAdapter = Sal_DS_OutStockFragment1Adapter(context, checkDatas)
        recyclerView.adapter = mAdapter
        // 设值listview空间失去焦点
        recyclerView.isFocusable = false

        // 行事件
        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            val entry = checkDatas.get(pos)
            // 是否赠品，2000005代表是，2000006代表否
            if(entry.free == 1 || entry.icItem.snManager == 1) return@OnItemClickListener

            curPos = pos
            showInputDialog("数量", entry.fqty.toString(), "0.0", RESULT_NUM)
        }
    }

    override fun initData() {
        getUserInfo()
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        hideSoftInputMode(et_code)

        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    @OnClick(R.id.btn_close, R.id.btn_scan, R.id.btn_save, R.id.btn_upload, R.id.btn_clone)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {// 关闭
                context.finish()
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                smqFlag = '2'
                ScanUtil.startScan(context, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create());
            }
            R.id.btn_save -> { // 保存
                if(!checkSave()) return
                run_save()
            }
            R.id.btn_upload -> { // 保存
                if(!checkSave()) return
                run_save()
            }
            R.id.btn_clone -> { // 重置
                /*if (checkSaveHint()) {
                    val build = AlertDialog.Builder(context)
                    build.setIcon(R.drawable.caution)
                    build.setTitle("系统提示")
                    build.setMessage("您有未保存的数据，继续重置吗？")
                    build.setPositiveButton("是") { dialog, which -> reset() }
                    build.setNegativeButton("否", null)
                    build.setCancelable(false)
                    build.show()

                } else {
                    reset()
                }*/
                reset()
            }
        }
    }

    override fun setListener() {
        val click = View.OnClickListener { v ->
            setFocusable(et_getFocus)
            when (v.id) {
                R.id.et_code -> setFocusable(et_code)
            }
        }
        et_code!!.setOnClickListener(click)

        // 物料---数据变化
        et_code!!.addTextChangedListener(object : TextWatcher {
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
        // 物料---长按输入条码
        et_code!!.setOnLongClickListener {
            smqFlag = '2'
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
     * 检查数据
     */
    fun checkSave() : Boolean {
        if(checkDatas.size == 0) {
            Comm.showWarnDialog(context, "请扫码物料！")
            return false
        }
        if(!isFinish()) {
            Comm.showWarnDialog(context, "订单数量未扫完！")
            return false
        }
        return true;
    }

    /**
     * 重置数据
     */
    private fun reset() {
        curPos = -1
        tv_codeName.text = "快递单"
        et_code.setHint("扫描快递单条码")
        et_code.setText("")
        tv_expressNo.text = "快递单："
        tv_custName.text = "客户："
        tv_needNum.text = "0"
        tv_finishNum.text = "0"

        btn_scan.isEnabled = true
        et_code.isEnabled = true
        isTextChange = false
        checkDatas.clear()
        mAdapter!!.notifyDataSetChanged()

        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        smqFlag = '2'
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    /**
     * 扫码销售订单的条码
     */
    private fun setICStockEntry_SalOrder(list :List<SeOrderEntry_App>) {
        tv_codeName.text = "条码"
        et_code.setHint("扫描物料条码")
        tv_expressNo.text = Html.fromHtml("快递单:&nbsp;<font color='#6a5acd'>"+getValues(et_code)+"</font>")
        tv_custName.text = list[0].seOrder.cust.fname
        et_code.setText("")

        val icstockBill = ICStockBill_App()
        icstockBill.billType = "DS_XSCK" // 电商销售出库
        icstockBill.ftranType = 21
        icstockBill.frob = 1
        icstockBill.fselTranType = 81
        icstockBill.fcustId = list[0].seOrder.fcustId
//        icstockBill.fdeptId = list[0].seOrder.fdeptId
        icstockBill.fdeptId = list[0]!!.seOrder.fdeptId
        icstockBill.fempId = user!!.empId
        icstockBill.yewuMan = user!!.empName
        icstockBill.fsmanagerId = user!!.empId
        icstockBill.baoguanMan = user!!.empName
        icstockBill.fmanagerId = user!!.empId
        icstockBill.fuzheMan = user!!.empName
        icstockBill.ffmanagerId = user!!.empId
        icstockBill.yanshouMan = user!!.empName
        icstockBill.fbillerId = user!!.erpUserId
        icstockBill.createUserId = user!!.id
        icstockBill.createUserName = user!!.username
        icstockBill.expressNo = getValues(et_code)

//        var isManProduct = false // 是否主产品
        list!!.forEachIndexed { index, it ->
            val entry = ICStockBillEntry_App()
            entry.icstockBill = icstockBill
            entry.icstockBillId = 0
            entry.fitemId = it.icItem.fitemId
//            entry.fentryId = it.fentryid
            // 锁库仓库
            entry.fdcStockId = it.lockStock.fitemId
            entry.stock = it.lockStock
            // 锁库仓位
            entry.fdcSPId = it.lockStockPos.fspId
            entry.stockPlace = it.lockStockPos

            if(it.free == 1) { // 是否赠品
                entry.fqty = it.fqty
            }
//            entry.fqty = it.useableQty
            entry.fprice = it.fprice
            entry.funitId = it.funitId
            entry.fsourceInterId = it.finterId
            entry.fsourceEntryId = it.fentryId
            entry.fsourceBillNo = it.seOrder.fbillNo
            entry.fsourceQty = it.fqty
            entry.fsourceTranType = 81
            entry.forderInterId = it.finterId
            entry.forderEntryId = it.fentryId
            entry.forderBillNo = it.seOrder.fbillNo
            entry.fdetailId = it.fdetailId
            entry.free = it.free
            entry.tcjdFitemId = it.tcjdFitemId
            entry.tcwdFitemId = it.tcwdFitemId
            entry.lockFitemId = it.lockFitemId

            entry.icItem = it.icItem
            entry.unit = it.unit

            entry.remark = ""
            entry.free = it.free

            checkDatas.add(entry)
            mAdapter!!.notifyDataSetChanged()
        }
    }


    /**
     * 扫描每一行的处理
     */
    private fun setICStockBill_Row(entry :ICStockBillEntry_App) {
        // 判断条码是否存在（启用批次，序列号）
        var isExist = false // 是否匹配

        checkDatas.forEachIndexed { index, it ->
            if (it.icstockBillEntryBarcodes.size > 0 && (it.icItem.batchManager == 1 || it.icItem.snManager == 1)) {
                it.icstockBillEntryBarcodes.forEach {
                    if (getValues(et_code).length > 0 && getValues(et_code) == it.barcode) {
                        Comm.showWarnDialog(context,"条码已使用！")
                        return
                    }
                }
            }

        if(it.lockFitemId == entry.fitemId) {
                isExist = true
                if(it.fqty >= it.fsourceQty) {
                    Comm.showWarnDialog(context,"第（"+(index+1)+"）行，数量已扫完！")
                    return
                }
                val addVal = BigdecimalUtil.add(it.fqty, 1.0)
                it.fqty = addVal

                // 记录条码
                if(it.icItem.batchManager == 1) { // 启用批次号
                    setBatchCode(it, 1.0, entry.smBatchCode)

                } else if(it.icItem.snManager == 1) { // 启用序列号
                    setSnCode(it, entry.smSnCode)

                } else { // 未启用
                    unStartBatchOrSnCode(it, 1.0)
                }

            }
        }
        if(!isExist) {
            Comm.showWarnDialog(context, "扫码的条码与订单不匹配！")
            return
        }

        countNum()
        mAdapter!!.notifyDataSetChanged()

        if(!checkSave()) return
        // 1秒后执行保存功能
        mHandler.postDelayed(Runnable {
            run_save()
        },1000)
    }

    /**
     *  扫码之后    物料启用批次
     */
    fun setBatchCode(entry : ICStockBillEntry_App, fqty :Double, batchCode :String) {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = entry.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = batchCode
        entryBarcode.snCode = ""
        entryBarcode.fqty = fqty
        entryBarcode.isUniqueness = 'Y'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = "DS_XSCK"

        entry.icstockBillEntryBarcodes.add(entryBarcode)
        entry.strBarcode = setStrBarcode(entry.icstockBillEntryBarcodes)
    }

    /**
     *  扫码之后    物料启用序列号
     */
    fun setSnCode(entry : ICStockBillEntry_App, snCode :String) {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = entry.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = ""
        entryBarcode.snCode = snCode
        entryBarcode.fqty = 1.0
        entryBarcode.isUniqueness = 'Y'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = "DS_XSCK"

        entry.icstockBillEntryBarcodes.add(entryBarcode)
        entry.strBarcode = setStrBarcode(entry.icstockBillEntryBarcodes)
    }

    /**
     *  扫码之后    物料未启用
     */
    fun unStartBatchOrSnCode(entry : ICStockBillEntry_App, fqty :Double) {
        val entryBarcode = ICStockBillEntryBarcode_App()
        entryBarcode.parentId = entry.id
        entryBarcode.barcode = getValues(et_code)
        entryBarcode.batchCode = ""
        entryBarcode.snCode = ""
        entryBarcode.fqty = fqty
        entryBarcode.isUniqueness = 'N'
        entryBarcode.againUse = 0
        entryBarcode.createUserName = user!!.username
        entryBarcode.billType = "DS_XSCK"

        entry.icstockBillEntryBarcodes.add(entryBarcode)
        entry.strBarcode = setStrBarcode(entry.icstockBillEntryBarcodes)
    }

    /**
     * 设置拼接strBarcode的值
     */
    private fun setStrBarcode(list :List<ICStockBillEntryBarcode_App>) :String {
        val sb = StringBuffer()
        list.forEach {
            if(sb.indexOf(it.barcode) == -1 && sb.length == 0) sb.append(it.barcode)
            else if(sb.indexOf(it.barcode) == -1 && sb.length > 0) sb.append("，"+it.barcode)
        }
        return sb.toString()
    }

    /**
     * 统计数量
     */
    private fun countNum() {
        var needNum = 0.0
        var finishNum = 0.0
        checkDatas.forEach {
            needNum = BigdecimalUtil.add(needNum, it.fsourceQty)
            finishNum = BigdecimalUtil.add(finishNum, it.fqty)
        }
        tv_needNum.setText(df.format(needNum))
        tv_finishNum.setText(df.format(finishNum))
    }

    /**
     * 判断是否扫完数
     */
    private fun isFinish(): Boolean {
        checkDatas.forEach {
            if(it.fqty < it.fsourceQty) {
                return false
            }
        }
        return true
    }

    /**
     * 扫码查询对应的方法
     */
    private fun run_smDatas(SUCC : Int, UNSUCC : Int) {
        isTextChange = false
        showLoadDialog("加载中...", false)

        var mUrl :String? = null
        if(checkDatas.size == 0) {
            mUrl = getURL("seOrder/findBarcodeByExpressNo")
        } else {
            mUrl = getURL("seOrder/findBarcodeByExpressNo2")
        }

        val formBody = FormBody.Builder()
                .add("barcode", getValues(et_code))
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_smDatas --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNSUCC, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(SUCC, result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 保存
     */
    private fun run_save() {
        showLoadDialog("保存中...", false)
        var mUrl = getURL("stockBill_WMS/save_SalOutStock2")
        val formBody = FormBody.Builder()
                .add("strJson", JsonUtil.objectToString(checkDatas))
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
     * 查询打印数据
     */
    private fun run_findPrintData(expressNo :String) {
        showLoadDialog("准备打印...", false)
        val mUrl = getURL("appPrint/printExpressNo")
        val formBody = FormBody.Builder()
                .add("expressNo", expressNo)
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNFIND_PRINT)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_findPrintData --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNFIND_PRINT, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(FIND_PRINT, result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 设置打印数据
     */
    private fun setPrintData(list: List<ExpressNoData>) {
        listMap.clear()
        listMap.addAll(list)

        if (isConnected) {
            setStartPrint(list)
        } else {
            // 打开蓝牙配对页面
            startActivityForResult(Intent(this, BluetoothDeviceListDialog::class.java), Constant.BLUETOOTH_REQUEST_CODE)
        }
    }

    /**
     * 开始打印
     */
    private fun setStartPrint(list: List<ExpressNoData>) {
        list.forEach {
            val curDate = Comm.getSysDate(0)
            val tsc = LabelCommand()
            setTscBegin(tsc, 10)
            // --------------- 打印区-------------Begin

            // 上下流水结构，先画线后打印其他
            // （左）画竖线
            tsc.addBar(20, 290, 2, 900)
            // （右上）画横线
            tsc.addBar(20, 290, 766, 2)
            // （右）竖线
            tsc.addBar(786, 290, 2, 900)
            // （右下）画横线
            tsc.addBar(20, 500, 766, 2)
            // 面横线（寄方月结下边）
            tsc.addBar(20, 565, 273, 2)
            // 画竖线（寄方月结右边）
            tsc.addBar(290, 498, 2, 228)
            // 画竖线（二维码右面）
            tsc.addBar(540, 498, 2, 228)
            // 画竖线（已验视右边）
            tsc.addBar(615, 498, 2, 228)
            // 画横线（AB表下面）
            tsc.addBar(615, 640, 160, 2)
            // 画横线（整行）
            tsc.addBar(20, 723, 766, 2)
            // 画横线（寄件人下面）
            tsc.addBar(20, 820, 766, 2)
            /*// 画竖线（寄托物右边）
            tsc.addBar(425, 820, 2, 100)
            // 画横线（寄托物中间）
            tsc.addBar(20, 870, 405, 2)
            // 画横线（增值服务下边）
//            tsc.addBar(425, 870, 356, 2)
            // 画横线（寄托物下面）
            tsc.addBar(20, 920, 766, 2)
            */
            // 画横线（备注下面）
            tsc.addBar(20, 1190, 766, 2)

            // 热线电话图片
            val phoneBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_phone)
            tsc.addBitmap(620, 30, LabelCommand.BITMAP_MODE.OVERWRITE, 140, phoneBit)
            // 支付时间
            tsc.addText(280, 90, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "支付时间："+it.t14)
            tsc.addText(60, 120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "ZJ ")   // 顺丰快递单上面的打印时间
            tsc.addText(120, 120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "第"+(it.printCount+1)+"次打印 ")   // 顺丰快递单上面的打印时间
            tsc.addText(280, 120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "打印时间："+curDate+" ")   // 顺丰快递单上面的打印时间

            // （左）条码
            tsc.add1DBarcode(50, 156, LabelCommand.BARCODETYPE.CODE39, 100, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, 2, 6, it.getT01())   // 顺丰快递单
            // 时效图片
            /*val shixiaoBit = getShiXiaoBitMap(it.proCode)
            if(shixiaoBit != null) {
                tsc.addBitmap(630, 156, LabelCommand.BITMAP_MODE.OVERWRITE, 120, shixiaoBit)
            }*/
            // 特惠
            tsc.addText(650, 160, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "特惠")   // 特惠
            // 目的地
            tsc.addText(50, 300, LabelCommand.FONTTYPE.FONT_3, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_3, LabelCommand.FONTMUL.MUL_3, it.getT02()+"")   // 目的地
            // 收件人图片
            val shouBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shou)
            if(shouBit != null) {
                tsc.addBitmap(40, 390, LabelCommand.BITMAP_MODE.OVERWRITE, 53, shouBit)
            }
            tsc.addText(130, 390, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT10()+"") // 收方人
            tsc.addText(270, 390, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT11()+"") // 收方电话
//            tsc.addText(150, 360, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT12()+"") // 收方地址
            // 收件地址超长，计算自动换行（计算两行）
            val t12 = it.getT12()
            val t12Len = t12!!.length
            if(t12Len > 28) {
                tsc.addText(130, 420, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(0, 28)+ "") // 收方地址
                if(t12.substring(28, t12Len).trim().length > 0) {
                    tsc.addText(130, 450, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(28, t12Len)+ "") // 收方地址
                }
            } else {
                tsc.addText(130, 420, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12+ "") // 收方地址
            }
            // 寄付月结
            tsc.addText(90, 510, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT05()+"")
            // 寄付月结下面
            tsc.addText(60, 580, LabelCommand.FONTTYPE.FONT_3, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_3, LabelCommand.FONTMUL.MUL_3, it.codingMapping+"")
            // 顺丰二维码图片
            if(it.twoDimensionCode.length > 0) {
                tsc.addQRCode(310, 510, LabelCommand.EEC.LEVEL_L, 5, LabelCommand.ROTATION.ROTATION_0, it.twoDimensionCode)
                /*var qrCodeBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_qrcode)
                if(qrCodeBit != null) {
                    tsc.addBitmap(310, 520, LabelCommand.BITMAP_MODE.OVERWRITE, 178, qrCodeBit)
                }*/
            }
            tsc.addText(550, 510, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "已 ")
            tsc.addText(550, 580, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "验 ")
            tsc.addText(550, 650, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "视 ")
            // AB标图片
            if(it.abFlag.equals("A")) {
                tsc.addBitmap(640, 516, LabelCommand.BITMAP_MODE.OVERWRITE, 90, BitmapFactory.decodeResource(resources, R.drawable.shunfeng_a))
            } else if(it.abFlag.equals("B")) {
                tsc.addBitmap(640, 516, LabelCommand.BITMAP_MODE.OVERWRITE, 90, BitmapFactory.decodeResource(resources, R.drawable.shunfeng_b))
            }
            // 出港映射码
            if(it.codingMappingOut.length > 0 ) {
                tsc.addText(620, 650, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.codingMappingOut+"")
            }

            // 寄件人图片
            val jiBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_ji)
            tsc.addBitmap(40, 740, LabelCommand.BITMAP_MODE.OVERWRITE, 53, jiBit)
            // 寄方人
            tsc.addText(130, 740, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT15()+"")
            // 寄方电话
            tsc.addText(270, 740, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT16()+"")
            // 寄方地址
            // 寄方地址超长，计算自动换行（计算两行）
            val t17 = it.getT17()
            val t17Len = t17!!.length
            if(t17Len > 14) {
                tsc.addText(430, 740, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17.substring(0, 14)+ "") // 收方地址
                if(t17.substring(14, t17Len).trim().length > 0) {
                    tsc.addText(130, 775, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17.substring(14, t17Len)+ "") // 收方地址
                }
            } else {
                tsc.addText(430, 740, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17+ "") // 收方地址
            }

            tsc.addText(40, 860, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.properties_value+"") // 高档脚垫
            // 备注
            // 备注超长，计算自动换行（计算四行）
            val t13 = it.getT13()
            val t13Len = t13!!.length
            if(t13Len > 30) {
                // 第一行
                tsc.addText(40, 930, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "备注："+t13.substring(0, 30)+"") // 卖家备注
                if(t13.substring(30, t13Len).length > 30) { // 第二行
                    tsc.addText(110, 960, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, 60) + "") // 卖家备注
                    if(t13.substring(60, t13Len).length > 30) { // 第三行
                        tsc.addText(110, 990, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, 90) + "") // 卖家备注
                        if(t13.substring(90, t13Len).length > 30) { // 第四行
                            tsc.addText(110, 1020, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, 120) + "") // 卖家备注
                            if(t13.substring(120, t13Len).length > 30) { // 第五行
                                tsc.addText(110, 1050, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, 150) + "") // 卖家备注
                                // 第六行
                                tsc.addText(110, 1080, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(150, t13Len) + "") // 卖家备注
                            } else {
                                tsc.addText(110, 1050, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, t13Len) + "") // 卖家备注
                            }
                        } else {
                            tsc.addText(110, 1020, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, t13Len) + "") // 卖家备注
                        }
                    } else {
                        tsc.addText(110, 990, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, t13Len) + "") // 卖家备注
                    }
                } else {
                    tsc.addText(110, 960, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, t13Len) + "") // 卖家备注
                }
            } else {
                // 第一行
                tsc.addText(40, 930, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "备注："+t13+"") // 卖家备注
            }
            // 留底（顺丰快递单）
            tsc.addText(40, 1280, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t01+"")
            // 留底（商品名称）
            tsc.addText(40, 1360, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t18+"")
            // 留底（备注）
            if(t13Len > 30) {
                // 第一行
                tsc.addText(40, 1440, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "备注："+t13.substring(0, 30)+"") // 卖家备注
                if(t13.substring(30, t13Len).length > 30) { // 第二行
                    tsc.addText(110, 1470, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, 60) + "") // 卖家备注
                    if(t13.substring(60, t13Len).length > 30) { // 第三行
                        tsc.addText(110, 1500, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, 90) + "") // 卖家备注
                        if(t13.substring(90, t13Len).length > 30) { // 第四行
                            tsc.addText(110, 1530, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, 120) + "") // 卖家备注
                            if(t13.substring(120, t13Len).length > 30) { // 第五行
                                tsc.addText(110, 1560, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, 150) + "") // 卖家备注
                                // 第六行
                                tsc.addText(110, 1590, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(150, t13Len) + "") // 卖家备注
                            } else {
                                tsc.addText(110, 1560, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, t13Len) + "") // 卖家备注
                            }
                        } else {
                            tsc.addText(110, 1530, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, t13Len) + "") // 卖家备注
                        }
                    } else {
                        tsc.addText(110, 1500, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, t13Len) + "") // 卖家备注
                    }
                } else {
                    tsc.addText(110, 1470, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, t13Len) + "") // 卖家备注
                }
            } else {
                // 第一行
                tsc.addText(40, 1440, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "备注："+t13+"") // 卖家备注
            }

            // --------------- 打印区-------------End
            setTscEnd(tsc)
        }
    }

    /**
     * 得到时效图片
     */
    private fun getShiXiaoBitMap(proCode :String): Bitmap? {
        when(proCode) {
            "T1" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t1)
            "T4" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t4)
            "T5" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t5)
            "T6" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t6)
            "T8" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t8)
            "T9" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t9)
            "T13" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t13)
            "T14" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t14)
            "T23" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t23)
            "T29" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t29)
            "T36" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t36)
            "T68" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t68)
            "T77" -> return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t77)
        }
        if(proCode.length == 0) {
            return BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shixiao_t1)
        }
        return null
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            // 当选择蓝牙的时候按了返回键
            if (data == null) return
            when (requestCode) {
                /*蓝牙连接*/
                Constant.BLUETOOTH_REQUEST_CODE -> {
                    /*获取蓝牙mac地址*/
                    val macAddress = data.getStringExtra(BluetoothDeviceListDialog.EXTRA_DEVICE_ADDRESS)
                    //初始化话DeviceConnFactoryManager
                    DeviceConnFactoryManager.Build()
                            .setId(id)
                            //设置连接方式
                            .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                            //设置连接的蓝牙mac地址
                            .setMacAddress(macAddress)
                            .build()
                    //打开端口
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort()
                }
                BaseFragment.CAMERA_SCAN -> {// 扫一扫成功  返回
                    val hmsScan = data!!.getParcelableExtra(ScanUtil.RESULT) as HmsScan
                    if (hmsScan != null) {
                        setTexts(et_code, hmsScan.originalValue)
                    }
                }
                WRITE_CODE -> {// 输入条码  返回
                    val bundle = data!!.extras
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        et_code!!.setText(value.toUpperCase())
                    }
                }
            }

        }
        mHandler.sendEmptyMessageDelayed(SETFOCUS,200)
    }

    /**
     * 打印前段配置
     * @param tsc
     */
    private fun setTscBegin(tsc: LabelCommand, gap: Int) {
        // 设置标签尺寸，按照实际尺寸设置
        tsc.addSize(100, 210)
        // 设置标签间隙，按照实际尺寸设置，如果为无间隙纸则设置为0
        tsc.addGap(gap)
        // 设置打印方向
        tsc.addDirection(LabelCommand.DIRECTION.BACKWARD, LabelCommand.MIRROR.NORMAL)
        // 开启带Response的打印，用于连续打印
        tsc.addQueryPrinterStatus(LabelCommand.RESPONSE_MODE.ON)
        // 设置原点坐标
        tsc.addReference(0, 0)
        // 撕纸模式开启
        tsc.addTear(EscCommand.ENABLE.ON)
        // 清除打印缓冲区
        tsc.addCls()
    }

    /**
     * 打印后段配置
     * @param tsc
     */
    private fun setTscEnd(tsc: LabelCommand) {
        // 打印标签
        tsc.addPrint(1, 1)
        // 打印标签后 蜂鸣器响

        tsc.addSound(2, 100)
        tsc.addCashdrwer(LabelCommand.FOOT.F5, 255, 255)
        val datas = tsc.command
        // 发送数据
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null) {
            return
        }
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(datas)
    }

    /**
     * 蓝牙监听广播
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                // 蓝牙连接断开广播
                ACTION_USB_DEVICE_DETACHED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> mHandler.obtainMessage(CONN_STATE_DISCONN).sendToTarget()
                DeviceConnFactoryManager.ACTION_CONN_STATE -> {
                    val state = intent.getIntExtra(DeviceConnFactoryManager.STATE, -1)
                    val deviceId = intent.getIntExtra(DeviceConnFactoryManager.DEVICE_ID, -1)
                    when (state) {
                        DeviceConnFactoryManager.CONN_STATE_DISCONNECT -> if (id == deviceId) {
                            tv_connState.setText(getString(R.string.str_conn_state_disconnect))
                            tv_connState.setTextColor(Color.parseColor("#666666")) // 未连接-灰色
                            isConnected = false
                        }
                        DeviceConnFactoryManager.CONN_STATE_CONNECTING -> {
                            tv_connState.setText(getString(R.string.str_conn_state_connecting))
                            tv_connState.setTextColor(Color.parseColor("#6a5acd")) // 连接中-紫色
                            isConnected = false
                        }
                        DeviceConnFactoryManager.CONN_STATE_CONNECTED -> {
                            //                            tv_connState.setText(getString(R.string.str_conn_state_connected) + "\n" + getConnDeviceInfo());
                            tv_connState.setText(getString(R.string.str_conn_state_connected))
                            tv_connState.setTextColor(Color.parseColor("#008800")) // 已连接-绿色
                            // 连接成功，开始打印
                            setStartPrint(listMap)

                            isConnected = true
                        }
                        CONN_STATE_FAILED -> {
                            Utils.toast(context, getString(R.string.str_conn_fail))
                            tv_connState.setText(getString(R.string.str_conn_state_disconnect))
                            tv_connState.setTextColor(Color.parseColor("#666666")) // 未连接-灰色
                            isConnected = false
                        }
                        else -> {
                        }
                    }
                }
            }
        }
    }

    /**
     * 得到用户对象
     */
    private fun getUserInfo() {
        if (user == null) user = showUserByXml()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_DEVICE_DETACHED)
        filter.addAction(DeviceConnFactoryManager.ACTION_CONN_STATE)
        registerReceiver(receiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "onDestroy()")
        DeviceConnFactoryManager.closeAllPort()
        if (threadPool != null) {
            threadPool!!.stopThreadPool()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            context.finish()
        }
        return false
    }
}