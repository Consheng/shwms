package ykk.xc.com.shwms.warehouse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.OnClick
import kotlinx.android.synthetic.main.ware_other_in_stock_fragment1.*
import kotlinx.android.synthetic.main.ware_other_in_stock_main.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.basics.Dept_DialogActivity
import ykk.xc.com.shwms.basics.Emp_DialogActivity
import ykk.xc.com.shwms.basics.Supplier_DialogActivity
import ykk.xc.com.shwms.bean.*
import ykk.xc.com.shwms.bean.k3Bean.Department_App
import ykk.xc.com.shwms.bean.k3Bean.Emp
import ykk.xc.com.shwms.bean.k3Bean.Supplier_App
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
 * 描述：其他入库
 * 作者：ykk
 */
class OtherInStock_Fragment1 : BaseFragment() {

    companion object {
        private val SEL_DEPT = 10
        private val SEL_SUPP = 11
        private val SEL_EMP1 = 12
        private val SEL_EMP2 = 13
        private val SEL_EMP3 = 14
        private val SEL_EMP4 = 15
        private val SAVE = 201
        private val UNSAVE = 501
        private val FIND_ICSTOCKBILL = 204
        private val UNFIND_ICSTOCKBILL = 504
    }

    private val context = this
    private var okHttpClient: OkHttpClient? = null
    private var user: User? = null
    private var mContext: Activity? = null
    private var parent: OtherInStock_MainActivity? = null
    private val df = DecimalFormat("#.###")
    private var timesTamp:String? = null // 时间戳
    var icstockBill = ICStockBill_App() // 保存的对象
    //    var isReset = false // 是否点击了重置按钮.
    private var icstockBillId = 0 // 上个页面传来的id

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: OtherInStock_Fragment1) : Handler() {
        private val mActivity: WeakReference<OtherInStock_Fragment1>

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
                        // 滑动第二个页面
                        m.parent!!.viewPager!!.setCurrentItem(1, false)
                        m.parent!!.isChange = if(m.icstockBillId == 0) true else false
                    }
                    UNSAVE -> { // 保存失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "保存失败！"
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

        icstockBill.supplier = m.supplier
        icstockBill.department = m.department

        tv_pdaNo.text = m.pdaNo
        tv_inDateSel.text = m.fdate
        if(m.supplier != null) {
            tv_suppSel.text = m.supplier.fname
        }
        if(m.department != null) {
            tv_deptSel.text = m.department.fname
        }
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
        return inflater.inflate(R.layout.ware_other_in_stock_fragment1, container, false)
    }

    override fun initView() {
        mContext = getActivity()
        parent = mContext as OtherInStock_MainActivity

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
        tv_operationManName.text = user!!.erpUserName
        tv_emp1Sel.text = user!!.empName
        tv_emp2Sel.text = user!!.empName
        tv_emp3Sel.text = user!!.empName
        tv_emp4Sel.text = user!!.empName

        icstockBill.billType = "QTRK" // 采购收货入库
        icstockBill.ftranType = 10
        icstockBill.frob = 1
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
                icstockBillId = bundle.getInt("id") // ICStockBill主表id
                // 查询主表信息
                run_findStockBill(icstockBillId)
            }
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
        }
    }

    @OnClick(R.id.tv_inDateSel, R.id.tv_suppSel, R.id.tv_deptSel, R.id.tv_emp1Sel, R.id.tv_emp2Sel, R.id.tv_emp3Sel, R.id.tv_emp4Sel,
            R.id.btn_save, R.id.btn_clone)
    fun onViewClicked(view: View) {
        var bundle: Bundle? = null
        when (view.id) {
            R.id.tv_inDateSel -> { // 选择日期
                Comm.showDateDialog(mContext, tv_inDateSel, 0)
            }
            R.id.tv_suppSel -> { // 选择供应商
                showForResult(Supplier_DialogActivity::class.java, SEL_SUPP, null)
            }
            R.id.tv_deptSel -> { // 选择部门
                showForResult(Dept_DialogActivity::class.java, SEL_DEPT, null)
            }
            R.id.tv_emp1Sel -> { // 选择业务员
                bundle = Bundle()
                showForResult(Emp_DialogActivity::class.java, SEL_EMP1, bundle)
            }
            R.id.tv_emp2Sel -> { // 选择保管者
                bundle = Bundle()
                showForResult(Emp_DialogActivity::class.java, SEL_EMP2, bundle)
            }
            R.id.tv_emp3Sel -> { // 选择负责人
                bundle = Bundle()
                showForResult(Emp_DialogActivity::class.java, SEL_EMP3, bundle)
            }
            R.id.tv_emp4Sel -> { // 选择验收人
                bundle = Bundle()
                showForResult(Emp_DialogActivity::class.java, SEL_EMP4, bundle)
            }
            R.id.btn_save -> { // 保存
                if(!checkSave(true)) return
                run_save();
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
        if (icstockBill.fsupplyId == 0 && icstockBill.fdeptId == 0) {
            if(isHint) Comm.showWarnDialog(mContext, "请选择供应商或部门！")
            return false;
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

    }

    fun reset() {
        setEnables(tv_suppSel, R.drawable.back_style_blue2, true)
        parent!!.isMainSave = false
        parent!!.viewPager.setScanScroll(false) // 禁止滑动
        tv_pdaNo.text = ""
        tv_inDateSel.text = Comm.getSysDate(7)
        tv_suppSel.text = ""
        tv_deptSel.text = ""
        icstockBill.id = 0
        icstockBill.fselTranType = 0
        icstockBill.pdaNo = ""
        icstockBill.fsupplyId = 0
        icstockBill.fdeptId = 0
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
        icstockBill.department = null
        timesTamp = user!!.getId().toString() + "-" + Comm.randomUUID()
        parent!!.isChange = false
        EventBus.getDefault().post(EventBusEntity(11)) // 发送指令到fragment2，告其清空
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SEL_SUPP -> {//查询供应商	返回
                    val supp = data!!.getSerializableExtra("obj") as Supplier_App
                    tv_suppSel.text = supp!!.fname
                    icstockBill.fsupplyId = supp.fitemId
                    icstockBill.supplier = supp
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
            }
        }
        // 是否可以自动保存
        if(checkSave(false)) run_save()
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