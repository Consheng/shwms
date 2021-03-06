package ykk.xc.com.shwms.purchase

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import kotlinx.android.synthetic.main.pur_sel_order.*
import okhttp3.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.pur.POOrderEntry
import ykk.xc.com.shwms.comm.BaseDialogActivity
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.purchase.adapter.Pur_InStock_Sel_POOrderAdapter
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.shwms.util.xrecyclerview.XRecyclerView
import java.io.IOException
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.*

/**
 * 选择采购订单dialog
 */
class Pur_InStock_Sel_POOOrderActivity : BaseDialogActivity(), XRecyclerView.LoadingListener {

    private val context = this
    private val listDatas = ArrayList<POOrderEntry>()
    private var mAdapter: Pur_InStock_Sel_POOrderAdapter? = null
    private val okHttpClient = OkHttpClient()
    private var limit = 1
    private var isRefresh: Boolean = false
    private var isLoadMore: Boolean = false
    private var isNextPage: Boolean = false
    private var suppId = 0
    private var strFdetailId:String?  = null

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Pur_InStock_Sel_POOOrderActivity) : Handler() {
        private val mActivity: WeakReference<Pur_InStock_Sel_POOOrderActivity>

        init {
            mActivity = WeakReference(activity)
        }

        override fun handleMessage(msg: Message) {
            val m = mActivity.get()
            if (m != null) {
                m.hideLoadDialog()
                when (msg.what) {
                    SUCC1 // 成功
                    -> {
                        val list = JsonUtil.strToList2(msg.obj as String, POOrderEntry::class.java)
                        m.listDatas.addAll(list!!)
                        m.mAdapter!!.notifyDataSetChanged()

                        if (m.isRefresh) {
                            m.xRecyclerView!!.refreshComplete(true)
                        } else if (m.isLoadMore) {
                            m.xRecyclerView!!.loadMoreComplete(true)
                        }

                        m.xRecyclerView!!.isLoadingMoreEnabled = m.isNextPage
                    }
                    UNSUCC1 // 数据加载失败！
                    -> {
                        m.mAdapter!!.notifyDataSetChanged()
                        m.toasts("抱歉，没有加载到数据！")
                    }
                }
            }
        }
    }

    override fun setLayoutResID(): Int {
        return R.layout.pur_sel_order
    }

    override fun initView() {
        xRecyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        xRecyclerView!!.layoutManager = LinearLayoutManager(context)
        mAdapter = Pur_InStock_Sel_POOrderAdapter(context, listDatas)
        xRecyclerView!!.adapter = mAdapter
        xRecyclerView!!.setLoadingListener(context)

        xRecyclerView!!.isPullRefreshEnabled = false // 上啦刷新禁用
        //        xRecyclerView.setLoadingMoreEnabled(false); // 不显示下拉刷新的view

        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            val m = listDatas[pos - 1]
            if(m.isCheck) {
                m.isCheck = false
            } else {
                m.isCheck = true
            }
            mAdapter!!.notifyDataSetChanged()
        }
    }

    override fun initData() {
        val bundle = context.intent.extras
        if (bundle != null) {
            suppId = bundle.getInt("suppId")
            val suppName = bundle.getString("suppName")
            strFdetailId = bundle.getString("strFdetailId")
            tv_suppName.text = "供应商："+suppName
        }

        initLoadDatas()
    }

    // 监听事件
    @OnClick(R.id.btn_close, R.id.btn_refresh, R.id.tv_date, R.id.btn_confirm)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {
                closeHandler(mHandler)
                context.finish()
            }
            R.id.btn_refresh -> {
                initLoadDatas()
            }
            R.id.tv_date -> {
                Comm.showDateDialog(context, tv_date, 0)
            }
            R.id.btn_confirm -> { // 确定
                val size = listDatas.size
                if (size == 0) {
                    Comm.showWarnDialog(context, "请查询数据！")
                    return
                }
                val list = ArrayList<POOrderEntry>()
                for (i in 0 until size) {
                    val pur = listDatas[i]
                    if (pur.isCheck) {
                        list.add(pur)
                    }
                }
                if (list.size == 0) {
                    Comm.showWarnDialog(context, "请至少选择一行数据！")
                    return
                }
                val intent = Intent()
                intent.putExtra("obj", list as Serializable)
                context.setResult(Activity.RESULT_OK, intent)
                context.finish()
            }
        }
    }

    private fun initLoadDatas() {
        limit = 1
        listDatas.clear()
        run_okhttpDatas()
    }

    /**
     * 通过okhttp加载数据
     */
    private fun run_okhttpDatas() {
        showLoadDialog("加载中...", false)
        val mUrl = getURL("poOrder/findListByPage")
        val formBody = FormBody.Builder()
                .add("purFdateBeg", getValues(tv_date))
                .add("purFdateEnd", getValues(tv_date))
                .add("fsupplyId", suppId.toString())
                .add("fbillNo", getValues(et_purNo).trim ())
                .add("isPass", "1") // 0：未审核，1：审核，3：结案
                .add("fqtyGt0", "1") // 可用数大于0的
                .add("unClosed", "1")
                .add("limit", limit.toString())
                .add("pageSize", "30")
                .build()

        val request = Request.Builder()
                .addHeader("cookie", session)
                .url(mUrl)
                .post(formBody)
                .build()

        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mHandler.sendEmptyMessage(UNSUCC1)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                if (!JsonUtil.isSuccess(result)) {
                    mHandler.sendEmptyMessage(UNSUCC1)
                    return
                }
                isNextPage = JsonUtil.isNextPage(result)

                val msg = mHandler.obtainMessage(SUCC1, result)
                Log.e("run_okhttpDatas --> onResponse", result)
                mHandler.sendMessage(msg)
            }
        })
    }

    override fun onRefresh() {
        isRefresh = true
        isLoadMore = false
        initLoadDatas()
    }

    override fun onLoadMore() {
        isRefresh = false
        isLoadMore = true
        limit += 1
        run_okhttpDatas()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            closeHandler(mHandler)
            context.finish()
        }
        return false
    }

    override fun onDestroy() {
        closeHandler(mHandler)
        super.onDestroy()
    }

    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 501
    }
}
