package ykk.xc.com.shwms.purchase

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.pur_receive_in_stock_fragment4.*
import kotlinx.android.synthetic.main.pur_receive_in_stock_main.*
import okhttp3.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.EventBusEntity
import ykk.xc.com.shwms.bean.ICStockBillEntry_Old
import ykk.xc.com.shwms.bean.ICStockBillEntryBarcode_Old
import ykk.xc.com.shwms.bean.User
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.purchase.adapter.Pur_Receive_InStock_Fragment4_Adapter
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 日期：2019-10-16 09:50
 * 描述：外购收料
 * 作者：ykk
 */
class Pur_Receive_InStock_Fragment4 : BaseFragment() {

    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 500
        private val DELETE = 201
        private val UNDELETE = 501

    }
    private val context = this
    private var parent: Pur_Receive_InStock_MainActivity? = null

    val checkDatas = ArrayList<ICStockBillEntryBarcode_Old>()
    private var okHttpClient: OkHttpClient? = null
    private var mAdapter: Pur_Receive_InStock_Fragment4_Adapter? = null
    private var user: User? = null
    private var mContext: Activity? = null
    private var curPos:Int = -1 // 当前行
    private val df = DecimalFormat("#.######")
    private var icstockBillEntry: ICStockBillEntry_Old? = null

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Pur_Receive_InStock_Fragment4) : Handler() {
        private val mActivity: WeakReference<Pur_Receive_InStock_Fragment4>

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
                    SUCC1 -> { // 查询分录 进入
                        m.checkDatas.clear()
                        val list = JsonUtil.strToList(msgObj, ICStockBillEntryBarcode_Old::class.java)
                        m.checkDatas.addAll(list)

                        m.mAdapter!!.notifyDataSetChanged()
                    }
                    UNSUCC1 -> { // 查询分录  失败
                    }
                    DELETE -> { // 删除分录 进入
                        m.run_findEntry_BarcodeList()
                    }
                    UNDELETE -> { // 删除分录  失败
                        Comm.showWarnDialog(m.mContext,"服务器繁忙，请稍后再试！")
                    }
                }
            }
        }
    }

    @Subscribe
    fun onEventBus(entity: EventBusEntity) {
        when (entity.caseId) {
            32 -> { // 接收第三个页面（32）发来的指令
                icstockBillEntry = entity.obj as ICStockBillEntry_Old

                tv_row.text = entity.obj2.toString()
                tv_mtlName.text = icstockBillEntry!!.icItem.fname
                tv_mtlNumber.text = Html.fromHtml("代码:&nbsp;<font color='#6a5acd'>"+icstockBillEntry!!.icItem.fnumber+"</font>")
                if(Comm.isNULLS(icstockBillEntry!!.strBatchCode).length > 0) {
                    tv_batchNo.visibility = View.VISIBLE
                    tv_batchNo.text = Html.fromHtml("批次:&nbsp;<font color='#6a5acd'>" + icstockBillEntry!!.strBatchCode + "</font>")
                } else {
                    tv_batchNo.visibility = View.INVISIBLE
                }
                tv_fmodel.text = Html.fromHtml("规格型号:&nbsp;<font color='#6a5acd'>"+ Comm.isNULLS(icstockBillEntry!!.icItem.fmodel)+"</font>")

                tv_num.text = Html.fromHtml("入库数:&nbsp;<font color='#FF0000'>"+ df.format(icstockBillEntry!!.fqty) +"</font>")
                tv_sourceQty.text = Html.fromHtml("源单数:&nbsp;<font color='#6a5acd'>"+ df.format(icstockBillEntry!!.fsourceQty) +"</font>&nbsp;<font color='#666666'>"+ icstockBillEntry!!.unit.unitName +"</font>")

                // 显示仓库组信息
                if(icstockBillEntry!!.stock != null ) {
                    tv_stockName.visibility = View.VISIBLE
                    tv_stockName.text = Html.fromHtml("仓库:&nbsp;<font color='#000000'>"+icstockBillEntry!!.stock!!.stockName+"</font>")
                } else {
                    tv_stockName.visibility = View.INVISIBLE
                }
                if(icstockBillEntry!!.stockArea != null ) {
                    tv_stockAreaName.visibility = View.VISIBLE
                    tv_stockAreaName.text = Html.fromHtml("库区:&nbsp;<font color='#000000'>"+icstockBillEntry!!.stockArea!!.fname+"</font>")
                } else {
                    tv_stockAreaName.visibility = View.INVISIBLE
                }
                if(icstockBillEntry!!.storageRack != null ) {
                    tv_storageRackName.visibility = View.VISIBLE
                    tv_storageRackName.text = Html.fromHtml("货架:&nbsp;<font color='#000000'>"+icstockBillEntry!!.storageRack!!.fnumber+"</font>")
                } else {
                    tv_storageRackName.visibility = View.INVISIBLE
                }
                if(icstockBillEntry!!.stockPos != null ) {
                    tv_stockPosName.visibility = View.VISIBLE
                    tv_stockPosName.text = Html.fromHtml("库位:&nbsp;<font color='#000000'>"+icstockBillEntry!!.stockPos!!.stockPositionName+"</font>")
                } else {
                    tv_stockPosName.visibility = View.INVISIBLE
                }

                run_findEntry_BarcodeList()
            }
        }
    }

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.pur_receive_in_stock_fragment4, container, false)
    }

    override fun initView() {
        mContext = getActivity()
        parent = mContext as Pur_Receive_InStock_MainActivity

        recyclerView.addItemDecoration(DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(mContext)
        mAdapter = Pur_Receive_InStock_Fragment4_Adapter(mContext!!, checkDatas)
        recyclerView.adapter = mAdapter
        // 设值listview空间失去焦点
        recyclerView.isFocusable = false

        // 行事件
        mAdapter!!.setCallBack(object : Pur_Receive_InStock_Fragment4_Adapter.MyCallBack {
//            override fun onModify(entity: ICStockBillEntry, position: Int) {
//                EventBus.getDefault().post(EventBusEntity(31, entity))
//                // 滑动第二个页面
//                parent!!.viewPager!!.setCurrentItem(1, false)
//            }
            override fun onDelete(entity: ICStockBillEntry_Old, position: Int) {
                curPos = position
                run_removeEntry(entity.id)
            }
        })

        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            EventBus.getDefault().post(EventBusEntity(31, checkDatas[pos]))
            // 滑动第二个页面
            parent!!.viewPager!!.setCurrentItem(1, false)
        }
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
        EventBus.getDefault().register(this)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
        }
    }

//    @OnClick(R.id.btn_upload)
//    fun onViewClicked(view: View) {
//        when (view.id) {
//            R.id.btn_upload -> { // 上传
//            }
//        }
//    }

    override fun setListener() {

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
//            SEL_MTL //查询物料	返回
//            -> if (resultCode == Activity.RESULT_OK) {
//                val list = data!!.getSerializableExtra("obj") as List<ICInventory>
//
//                getMtlAfter(list)
//            }

        }
    }

    /**
     * 历史查询
     */
    private fun run_findEntry_BarcodeList() {
        showLoadDialog("加载中...", false)
        val mUrl = getURL("stockBill_WMS/findEntry_BarcodeList")
        val formBody = FormBody.Builder()
                .add("icstockBillEntryId", icstockBillEntry!!.id.toString())
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
                LogUtil.e("run_findEntry_BarcodeList --> onResponse", result)
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
     * 删除
     */
    private fun run_removeEntry(id : Int) {
        showLoadDialog("加载中...", false)
        val mUrl = getURL("stockBill_WMS/removeEntry")
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
                mHandler.sendEmptyMessage(UNDELETE)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_findEntryList --> onResponse", result)
                if (!JsonUtil.isSuccess(result)) {
                    val msg = mHandler.obtainMessage(UNDELETE, result)
                    mHandler.sendMessage(msg)
                    return
                }
                val msg = mHandler.obtainMessage(DELETE, result)
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
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }
}