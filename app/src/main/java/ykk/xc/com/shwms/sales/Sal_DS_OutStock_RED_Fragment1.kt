package ykk.xc.com.shwms.sales

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.OnClick
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import kotlinx.android.synthetic.main.sal_ds_outstock_red_fragment1.*
import kotlinx.android.synthetic.main.sal_ds_outstock_red_main.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.basics.Cust_DialogActivity
import ykk.xc.com.shwms.basics.Dept_DialogActivity
import ykk.xc.com.shwms.bean.EventBusEntity
import ykk.xc.com.shwms.bean.ICStockBill_App
import ykk.xc.com.shwms.bean.User
import ykk.xc.com.shwms.bean.k3Bean.Customer_App
import ykk.xc.com.shwms.bean.k3Bean.Department_App
import ykk.xc.com.shwms.bean.k3Bean.Emp
import ykk.xc.com.shwms.bean.k3Bean.SeOrderEntry_App
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

/**
 * 日期：2019-10-16 09:50
 * 描述：销售退货
 * 作者：ykk
 */
class Sal_DS_OutStock_RED_Fragment1 : BaseFragment() {

    companion object {
        private val SEL_CUST = 10
        private val SEL_DEPT = 11
        private val SEL_EMP1 = 62
        private val SEL_EMP2 = 63
        private val SEL_EMP3 = 64
        private val SEL_EMP4 = 65

        private val SAVE = 201
        private val UNSAVE = 501
        private val FIND_SOURCE = 202
        private val UNFIND_SOURCE = 502
        private val FIND_ICSTOCKBILL = 204
        private val UNFIND_ICSTOCKBILL = 504

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
    }

    private val context = this
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private var mContext: Activity? = null
    private var parent: Sal_DS_OutStock_RED_MainActivity? = null
    private val df = DecimalFormat("#.###")
    private var timesTamp:String? = null // 时间戳
    var icstockBill = ICStockBill_App() // 保存的对象
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    //    var isReset = false // 是否点击了重置按钮.
    private var icstockBillId = 0 // 上个页面传来的id
    private var refundType = 1 // 扫描类型1：退货单号，2：物料条码
    var seOrderEntryList:List<SeOrderEntry_App>? = null

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Sal_DS_OutStock_RED_Fragment1) : Handler() {
        private val mActivity: WeakReference<Sal_DS_OutStock_RED_Fragment1>

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
                    SAVE -> {// 保存成功 进入
                        val strId_pdaNo = JsonUtil.strToString(msgObj)
                        if(m.icstockBill.id == 0) {
                            val arr = strId_pdaNo.split(":") // id和pdaNo数据拼接（1:IC201912121）
                            m.icstockBill.id = m.parseInt(arr[0])
                            m.icstockBill.pdaNo = arr[1]
                            m.tv_pdaNo.text = arr[1]
                        }
                        m.parent!!.isMainSave = true
                        m.parent!!.viewPager.setScanScroll(true); // 放开左右滑动
                        m.toasts("保存成功✔")
//                        m.lin_expressNo.visibility = View.GONE  // 把退货单号隐藏
                        m.et_code.isEnabled = false
                        m.btn_scan.isEnabled = false
                        m.btn_scan.visibility = View.GONE
                        m.setEnables(m.lin_focusNo, R.drawable.back_style_gray1c, false)
                        // 滑动第二个页面
                        m.parent!!.viewPager!!.setCurrentItem(1, false)
                        m.parent!!.isChange = if(m.icstockBillId == 0) true else false
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
                        Comm.showWarnDialog(m.mContext, errMsg)
                    }
                    FIND_SOURCE ->{ // 查询源单 返回
                        val list = JsonUtil.strToList(msgObj, SeOrderEntry_App::class.java)
                        m.seOrderEntryList = list
                        m.icstockBill.fcustId = list[0].seOrder.fcustId
                        m.icstockBill.fdeptId = list[0].seOrder.fdeptId
                        m.icstockBill.expressNo = m.getValues(m.et_expressCode)
//                            m.icstockBill.expressCompany = m.isNULLS(list[0].expressCompany)
                        m.tv_orderNo.text = list[0].seOrder.fbillNo
                        m.tv_custSel.text = list[0].seOrder.cust.fname
                        m.tv_deptSel.text = list[0].seOrder.department.fname
                    }
                    UNFIND_SOURCE ->{ // 查询源单失败！ 返回
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "扫描的快递单不正确，请检查！！！"
                        Comm.showWarnDialog(m.mContext, errMsg)
                    }
                    FIND_ICSTOCKBILL -> { // 查询主表信息 成功
                        val icsBill = JsonUtil.strToObject(msgObj, ICStockBill_App::class.java)
                        m.setICStockBill(icsBill)
                    }
                    UNFIND_ICSTOCKBILL -> { // 查询主表信息 失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "查询信息有错误！2秒后自动关闭..."
                        Comm.showWarnDialog(m.mContext, errMsg)
                        m.mHandler.postDelayed(Runnable {
                            m.mContext!!.finish()
                        },2000)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        if(m.refundType == 1) {
                            m.setFocusable(m.et_expressCode)
                        } else {
                            m.setFocusable(m.et_code)
                        }
                    }
                    SAOMA -> { // 扫码之后
                        if(m.refundType == 1) {
                            m.isTextChange = false
                            m.icstockBill.expressNo = m.getValues(m.et_expressCode)
                            m.refundType = 2
                            m.mHandler.sendEmptyMessage(SETFOCUS)

                        } else {
                            m.run_findBarcodeByBTOR()
                        }
                    }
                }
            }
        }
    }

    fun setICStockBill(m : ICStockBill_App) {
        icstockBill.id = m.id
        icstockBill.pdaNo = m.pdaNo
        icstockBill.fdate = m.fdate
        icstockBill.fsupplyId = m.fsupplyId
        icstockBill.fdeptId = m.fdeptId
        icstockBill.fempId = m.fempId
        icstockBill.fsmanagerId = m.fsmanagerId
        icstockBill.fmanagerId = m.fmanagerId
        icstockBill.ffmanagerId = m.ffmanagerId
        icstockBill.fbillerId = m.fbillerId
        icstockBill.fselTranType = m.fselTranType

        icstockBill.yewuMan = m.yewuMan          // 业务员
        icstockBill.baoguanMan = m.baoguanMan          // 保管人
        icstockBill.fuzheMan = m.fuzheMan           // 负责人
        icstockBill.yanshouMan = m.yanshouMan            // 验收人
        icstockBill.createUserId = m.createUserId        // 创建人id
        icstockBill.createUserName = m.createUserName        // 创建人
        icstockBill.createDate = m.createDate            // 创建日期
        icstockBill.isToK3 = m.isToK3                   // 是否提交到K3
        icstockBill.k3Number = m.k3Number                // k3返回的单号
        icstockBill.fcustId = m.fcustId
        icstockBill.expressNo = m.expressNo
        icstockBill.expressCompany = m.expressCompany

        icstockBill.supplier = m.supplier
        icstockBill.cust = m.cust
        icstockBill.department = m.department

        if(m.cust != null) {
            tv_custSel.text = m.cust.fname
        }
        if(m.department != null) {
            tv_deptSel.text = m.department.fname
        }
        tv_pdaNo.text = m.pdaNo
        tv_inDateSel.text = m.fdate
        isTextChange = true
        if(isNULLS(m.expressNo).length == 0) {
            lin_expressNo.visibility = View.GONE
        } else {
            lin_expressNo.visibility = View.VISIBLE
        }
        et_expressCode.setText(m.expressNo)
        lin_barcode.visibility = View.GONE
        isTextChange = false
        tv_emp1Sel.text = m.yewuMan
        tv_emp2Sel.text = m.baoguanMan
        tv_emp3Sel.text = m.fuzheMan
        tv_emp4Sel.text = m.yanshouMan

        parent!!.isChange = false
        parent!!.isMainSave = true
        parent!!.viewPager.setScanScroll(true); // 放开左右滑动
        EventBus.getDefault().post(EventBusEntity(12)) // 发送指令到fragment3，查询分类信息
    }

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.sal_ds_outstock_red_fragment1, container, false)
    }

    override fun initView() {
        mContext = getActivity()
        parent = mContext as Sal_DS_OutStock_RED_MainActivity
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
        tv_inDateSel.text = Comm.getSysDate(7)
        hideSoftInputMode(mContext, et_expressCode)
        hideSoftInputMode(mContext, et_code)

        tv_operationManName.text = user!!.erpUserName
        tv_emp1Sel.text = user!!.empName
        tv_emp2Sel.text = user!!.empName
        tv_emp3Sel.text = user!!.empName
        tv_emp4Sel.text = user!!.empName

        icstockBill.billType = "DS_XSCK_BTOR" // 电商销售退货
        icstockBill.ftranType = 21
        icstockBill.frob = -1
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

        bundle()
    }

    fun bundle() {
        val bundle = mContext!!.intent.extras
        if(bundle != null) {
            if(bundle.containsKey("id")) { // 查询过来的
                et_expressCode.isEnabled = false
                btn_scan.isEnabled = false
                btn_scan.visibility = View.GONE
                setEnables(lin_focusNo, R.drawable.back_style_gray1c, false)
//                lin_expressNo.visibility = View.GONE
                lin_barcode.visibility = View.GONE
                icstockBillId = bundle.getInt("id") // ICStockBill主表id
                // 查询主表信息
                run_findStockBill(icstockBillId)
            } else {
//                lin_expressNo.visibility = View.VISIBLE
                lin_barcode.visibility = View.VISIBLE
                et_expressCode.isEnabled = true
                btn_scan.isEnabled = true
                btn_scan.visibility = View.VISIBLE
                setEnables(lin_focusNo, R.drawable.back_style_blue2, true)
            }
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
        }
    }

    @OnClick(R.id.tv_inDateSel, R.id.btn_save, R.id.btn_clone, R.id.btn_scanExpressNo, R.id.btn_scan, R.id.tv_custSel, R.id.tv_deptSel)
    fun onViewClicked(view: View) {
        var bundle: Bundle? = null
        when (view.id) {
            R.id.tv_inDateSel -> { // 选择日期
                Comm.showDateDialog(mContext, tv_inDateSel, 0)
            }
            R.id.tv_custSel -> { // 选择供应商
                showForResult(Cust_DialogActivity::class.java, SEL_CUST, null)
            }
            R.id.tv_deptSel -> { // 选择部门
                showForResult(Dept_DialogActivity::class.java, SEL_DEPT, null)
            }
            R.id.btn_scanExpressNo -> { // 调用摄像头扫描（物料）
                refundType = 1
                ScanUtil.startScan(mContext, 10001, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create())
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                refundType = 2
                ScanUtil.startScan(mContext, 10001, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create())
            }
            R.id.btn_save -> { // 保存
                if(!checkSave(true)) return
                run_save()
            }
            R.id.btn_clone -> { // 重置
                if (parent!!.isChange) {
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
     * 保存检查数据判断
     */
    fun checkSave(isHint :Boolean) : Boolean {
        if(icstockBill.fcustId == 0) {
            if(isHint) Comm.showWarnDialog(mContext, "请选择客户！")
            return false
        }
        if(icstockBill.fsmanagerId == 0) {
            if(isHint) Comm.showWarnDialog(mContext, "请选择保管人！")
            return false
        }
        if(icstockBill.ffmanagerId == 0) {
            if(isHint) Comm.showWarnDialog(mContext, "请选择验收人！")
            return false
        }
        return true;
    }

    override fun setListener() {
        val click = View.OnClickListener { v ->
            setFocusable(et_getFocus)
            when (v.id) {
                R.id.et_expressCode -> setFocusable(et_expressCode)
                R.id.et_code -> setFocusable(et_code)
            }
        }
        et_expressCode!!.setOnClickListener(click)
        et_code!!.setOnClickListener(click)

        // 快递单---数据变化
        et_expressCode!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    refundType = 1
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 快递单---长按输入条码
        et_expressCode!!.setOnLongClickListener {
            refundType = 1
            showInputDialog("快递单", getValues(et_expressCode), "none", WRITE_CODE)
            true
        }

        // 条码---数据变化
        et_code!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (s.length == 0) return
                if (!isTextChange) {
                    isTextChange = true
                    refundType = 2
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 条码---长按输入条码
        et_code!!.setOnLongClickListener {
            refundType = 2
            showInputDialog("快递单", getValues(et_code), "none", WRITE_CODE)
            true
        }
    }

    fun reset() {
        lin_expressNo.visibility = View.VISIBLE
        lin_barcode.visibility = View.VISIBLE
        et_expressCode.isEnabled = true
        et_code.isEnabled = true
        btn_scanExpressNo.isEnabled = true
        btn_scan.isEnabled = true
        btn_scanExpressNo.visibility = View.VISIBLE
        btn_scan.visibility = View.VISIBLE
        setEnables(lin_focusExpressNo, R.drawable.back_style_blue2, true)
        setEnables(lin_focusNo, R.drawable.back_style_blue2, true)
        parent!!.isMainSave = false
        parent!!.viewPager.setScanScroll(false) // 禁止滑动
        tv_pdaNo.text = ""
        tv_inDateSel.text = Comm.getSysDate(7)
        et_expressCode.setText("")
        et_code.setText("")
        tv_orderNo.text = ""
        tv_custSel.text = ""
        tv_deptSel.text = ""
        icstockBill.id = 0
        icstockBill.fselTranType = 0
        icstockBill.pdaNo = ""
        icstockBill.fsupplyId = 0
        icstockBill.fdeptId = 0
        icstockBill.expressNo = ""
        icstockBill.expressCompany = ""
//        icstockBill.fempId = 0
//        icstockBill.fsmanagerId = 0
//        icstockBill.fmanagerId = 0
//        icstockBill.ffmanagerId = 0
//        icstockBill.yewuMan = ""
//        icstockBill.baoguanMan = ""
//        icstockBill.fuzheMan = ""
//        icstockBill.yanshouMan = ""

        icstockBillId = 0
        icstockBill.supplier = null
        icstockBill.cust = null
        icstockBill.department = null
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        parent!!.isChange = false
        EventBus.getDefault().post(EventBusEntity(11)) // 发送指令到fragment2，告其清空
        mHandler.sendEmptyMessageDelayed(SETFOCUS,200)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SEL_CUST -> {//查询供应商	返回
                    val cust = data!!.getSerializableExtra("obj") as Customer_App
                    tv_custSel.text = cust!!.fname
                    icstockBill.fcustId = cust.fitemId
                    icstockBill.cust = cust
                }
                SEL_DEPT -> {//查询部门	返回
                    val dept = data!!.getSerializableExtra("obj") as Department_App
                    tv_deptSel.text = dept!!.fname
                    icstockBill.fdeptId = dept.fitemId
                    icstockBill.department = dept
                }
                SEL_EMP1 -> {//查询业务员	返回
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp1Sel.text = emp!!.fname
                    icstockBill.fempId = emp.fitemId
                    icstockBill.yewuMan = emp.fname
                }
                SEL_EMP2 -> {//查询保管人	返回
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp2Sel.text = emp!!.fname
                    icstockBill.fsmanagerId = emp.fitemId
                    icstockBill.baoguanMan = emp.fname
                }
                SEL_EMP3 -> {//查询负责人	返回
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp3Sel.text = emp!!.fname
                    icstockBill.fmanagerId = emp.fitemId
                    icstockBill.fuzheMan = emp.fname
                }
                SEL_EMP4 -> {//查询验收人	返回
                    val emp = data!!.getSerializableExtra("obj") as Emp
                    tv_emp4Sel.text = emp!!.fname
                    icstockBill.ffmanagerId = emp.fitemId
                    icstockBill.yanshouMan = emp.fname
                }
                WRITE_CODE -> {// 输入条码  返回
                    val bundle = data!!.extras
                    if (bundle != null) {
                        val value = bundle.getString("resultValue", "")
                        setTexts(et_expressCode, value.toUpperCase())
                    }
                }
            }
        }
        // 是否可以自动保存
        if(checkSave(false)) run_save()
    }

    /**
     * 调用华为扫码接口，返回的值
     */
    fun getScanData(barcode :String) {
        if(refundType == 1) {
            setTexts(et_expressCode, barcode)
        } else {
            setTexts(et_code, barcode)
        }
    }

    /**
     * 根据快递单查询销售订单
     */
    private fun run_findBarcodeByBTOR() {
        isTextChange = false
        showLoadDialog("保存中...", false)
        val mUrl = getURL("seOrder/findBarcodeByBTOR")

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
                mHandler.sendEmptyMessage(UNFIND_SOURCE)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNFIND_SOURCE, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(FIND_SOURCE, result)
                LogUtil.e("run_save --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    /**
     * 保存
     */
    private fun run_save() {
        icstockBill.fdate = getValues(tv_inDateSel)

        showLoadDialog("保存中...", false)
        val mUrl = getURL("stockBill_WMS/save")

        val mJson = JsonUtil.objectToString(icstockBill)
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
     *  查询主表信息
     */
    private fun run_findStockBill(id: Int) {
        val mUrl = getURL("stockBill_WMS/findStockBill")

        val formBody = FormBody.Builder()
                .add("id", id.toString())
                .build()

        val request = Request.Builder()
                .addHeader("cookie", getSession())
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient!!.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNFIND_ICSTOCKBILL)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNFIND_ICSTOCKBILL, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(FIND_ICSTOCKBILL, result)
                LogUtil.e("run_missionBillModifyStatus --> onResponse", result)
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
        super.onDestroyView()
    }
}