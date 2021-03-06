package ykk.xc.com.shwms.basics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

import java.io.IOException
import java.lang.ref.WeakReference
import java.util.ArrayList

import butterknife.BindView
import butterknife.OnClick
import kotlinx.android.synthetic.main.ab_supplier_dialog.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.basics.adapter.Supplier_DialogAdapter
import ykk.xc.com.shwms.bean.Supplier
import ykk.xc.com.shwms.bean.k3Bean.Supplier_App
import ykk.xc.com.shwms.comm.BaseDialogActivity
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import ykk.xc.com.shwms.util.xrecyclerview.XRecyclerView

/**
 * 选择供应商dialog
 */
class Supplier_DialogActivity : BaseDialogActivity(), XRecyclerView.LoadingListener {

    private val context = this
    private val listDatas = ArrayList<Supplier_App>()
    private var mAdapter: Supplier_DialogAdapter? = null
    private val okHttpClient = OkHttpClient()
    private var limit = 1
    private var isRefresh: Boolean = false
    private var isLoadMore: Boolean = false
    private var isNextPage: Boolean = false
    private var checkDatas: List<Supplier_App>? = null // 上个界面传过来的供应商列表

    // 消息处理
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: Supplier_DialogActivity) : Handler() {
        private val mActivity: WeakReference<Supplier_DialogActivity>

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
                        val list = JsonUtil.strToList2(msg.obj as String, Supplier_App::class.java)
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
        return R.layout.ab_supplier_dialog
    }

    override fun initView() {
        xRecyclerView!!.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        xRecyclerView!!.layoutManager = LinearLayoutManager(context)
        mAdapter = Supplier_DialogAdapter(context, listDatas)
        xRecyclerView!!.adapter = mAdapter
        xRecyclerView!!.setLoadingListener(context)

        xRecyclerView!!.isPullRefreshEnabled = false // 上啦刷新禁用
        //        xRecyclerView.setLoadingMoreEnabled(false); // 不显示下拉刷新的view

        mAdapter!!.onItemClickListener = BaseRecyclerAdapter.OnItemClickListener { adapter, holder, view, pos ->
            val m = listDatas[pos - 1]
            val intent = Intent()
            intent.putExtra("obj", m)
            context.setResult(Activity.RESULT_OK, intent)
            context.finish()
        }
    }

    override fun initData() {
        val bundle = context.intent.extras
        if (bundle != null) {
            //            isAll = bundle.getInt("isAll");
            //            caseId = bundle.getInt("caseId");
            checkDatas = bundle.getSerializable("checkDatas") as List<Supplier_App>
            // 如果有供应商数据，就直接显示
            if (checkDatas != null && checkDatas!!.size > 0) {
                xRecyclerView!!.isPullRefreshEnabled = false // 上啦刷新禁用
                xRecyclerView!!.isLoadingMoreEnabled = false // 不显示下拉刷新的view
                lin_search!!.visibility = View.GONE
                listDatas.addAll(checkDatas!!)
                mHandler.postDelayed({ mAdapter!!.notifyDataSetChanged() }, 300)

            } else {
                initLoadDatas()
            }
        } else {
            initLoadDatas()
        }
    }

    // 监听事件
    @OnClick(R.id.btn_close, R.id.btn_search)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {
                closeHandler(mHandler)
                context.finish()
            }
            R.id.btn_search -> initLoadDatas()
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
        val mUrl = getURL("supplier/findListByPage")
        val formBody = FormBody.Builder()
                .add("fnumberOrName", getValues(et_search).trim { it <= ' ' })
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
                Log.e("Supplier_DialogActivity --> onResponse", result)
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
