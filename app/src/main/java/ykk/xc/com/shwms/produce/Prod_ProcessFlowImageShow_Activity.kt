package ykk.xc.com.shwms.produce

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import com.bumptech.glide.Glide
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import kotlinx.android.synthetic.main.prod_processflow_image_show.*
import okhttp3.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.comm.BaseActivity
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.ImageLoadActivity
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

/**
 * 日期：2019-10-16 09:14
 * 描述：工艺图纸查看
 * 作者：ykk
 */
class Prod_ProcessFlowImageShow_Activity : BaseActivity() {
    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 500

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
    }

    private val context = this
    private val TAG = "Prod_ProcessFlowImageShow_Activity"
    private var okHttpClient: OkHttpClient? = null
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var imageUrl :String? = null

    // 消息处理
    private val mHandler = MyHandler(this)
    private class MyHandler(activity: Prod_ProcessFlowImageShow_Activity) : Handler() {
        private val mActivity: WeakReference<Prod_ProcessFlowImageShow_Activity>

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
                        m.imageUrl = JsonUtil.strToString(msgObj)
                        m.imgView.visibility = View.VISIBLE
                        Glide.with(m.context)
                                .load(m.imageUrl)
                                .placeholder(R.drawable.image_wait)
                                .error(R.drawable.image_null)
                                .into(m.imgView)
                    }
                    UNSUCC1 -> { // 扫码失败
                        m.imageUrl
                        m.imgView.visibility = View.GONE
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        m.setFocusable(m.et_code)
                    }
                    SAOMA -> { // 扫码之后
                        m.run_smDatas()
                    }
                }
            }
        }
    }

    override fun setLayoutResID(): Int {
        return R.layout.prod_processflow_image_show
    }

    override fun initView() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(120, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(120, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }
        
    }

    override fun initData() {
        hideSoftInputMode(et_code)

        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)
    }

    @OnClick(R.id.btn_close, R.id.btn_scan, R.id.imgView)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {// 关闭
                context.finish()
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                ScanUtil.startScan(context, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create());
            }
            R.id.imgView -> {
                val bundle = Bundle()
                bundle.putString("imageUrl", imageUrl)
                show(ImageLoadActivity::class.java, bundle)
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
                    mHandler.sendEmptyMessageDelayed(SAOMA, 300)
                }
            }
        })
        // 物料---长按输入条码
        et_code!!.setOnLongClickListener {
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
     * 查询图片地址
     */
    private fun run_smDatas() {
        isTextChange = false
        showLoadDialog("加载中...", false)
        val mUrl = getURL("fileLookOver/pdfShow_app")
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
                mHandler.sendEmptyMessage(UNSUCC1)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()
                val result = body.string()
                LogUtil.e("run_findPrintData --> onResponse", result)
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



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            // 当选择蓝牙的时候按了返回键
            if (data == null) return
            when (requestCode) {
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

    @Override
    override fun onDestroy() {
        super.onDestroy()
        Thread{
            // clearDiskCache()这个方法是要放在子线程去做
            Glide.get(context).clearDiskCache()
        }.start()
        // clearMemory()则要放在主线程
        Glide.get(context).clearMemory();
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            context.finish()
        }
        return false
    }
}