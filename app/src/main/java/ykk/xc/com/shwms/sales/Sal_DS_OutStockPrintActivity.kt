package ykk.xc.com.shwms.sales

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED
import android.os.Handler
import android.os.Message
import android.text.Editable
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
import kotlinx.android.synthetic.main.sal_ds_out_print.*
import okhttp3.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.ExpressNoData
import ykk.xc.com.shwms.bean.User
import ykk.xc.com.shwms.comm.BaseActivity
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.Base64Utils
import ykk.xc.com.shwms.util.JsonUtil
import ykk.xc.com.shwms.util.LogUtil
import ykk.xc.com.shwms.util.blueTooth.*
import ykk.xc.com.shwms.util.blueTooth.Constant.MESSAGE_UPDATE_PARAMETER
import ykk.xc.com.shwms.util.blueTooth.DeviceConnFactoryManager.CONN_STATE_FAILED
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

/**
 * 日期：2019-10-16 09:14
 * 描述：电商销售出库打印
 * 作者：ykk
 */
class Sal_DS_OutStockPrintActivity : BaseActivity() {
    companion object {
        private val SUCC1 = 200
        private val UNSUCC1 = 500

        private val SETFOCUS = 1
        private val SAOMA = 2
        private val WRITE_CODE = 3
    }

    private val context = this
    private val TAG = "Sal_DS_OutStockPrintActivity"
    val fragment1 = Sal_DS_OutStockFragment1()
    // 蓝牙打印用到的
    private var isConnected: Boolean = false // 蓝牙是否连接标识
    private val id = 0 // 设备id
    private var threadPool: ThreadPool? = null
    private val CONN_STATE_DISCONN = 0x007 // 连接状态断开
    private val PRINTER_COMMAND_ERROR = 0x008 // 使用打印机指令错误
    private val CONN_PRINTER = 0x12
    private var listMap = ArrayList<ExpressNoData>() // 打印保存的数据
    private var cainiaoPrintData :String? = null // 菜鸟打印数据

    private var okHttpClient: OkHttpClient? = null
    private var isTextChange: Boolean = false // 是否进入TextChange事件
    private var user: User? = null

    // 消息处理
    private val mHandler = MyHandler(this)
    private class MyHandler(activity: Sal_DS_OutStockPrintActivity) : Handler() {
        private val mActivity: WeakReference<Sal_DS_OutStockPrintActivity>

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
                    SUCC1 -> { // 得到打印数据 进入
                        if(msgObj!!.indexOf("ykk_jsonArr") > -1) {
                            val list = JsonUtil.strToList(msgObj, ExpressNoData::class.java)
                            m.cainiaoPrintData = null
                            m.setPrintData(list) // 打印

                        } else {
                            m.cainiaoPrintData = JsonUtil.strToString(msgObj)
                            m.setPrintData(null)
                        }

                    }
                    UNSUCC1 -> { // 得到打印数据  失败
                        errMsg = JsonUtil.strToString(msgObj)
                        if (m.isNULLS(errMsg).length == 0) errMsg = "很抱歉，没有找到数据！"
                        Comm.showWarnDialog(m.context, errMsg)
                    }
                    SETFOCUS -> { // 当弹出其他窗口会抢夺焦点，需要跳转下，才能正常得到值
                        m.setFocusable(m.et_getFocus)
                        m.setFocusable(m.et_code)
                    }
                    SAOMA -> { // 扫码之后
                        m.run_findPrintData()
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
        return R.layout.sal_ds_out_print
    }

    override fun initData() {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    //                .connectTimeout(10, TimeUnit.SECONDS) // 设置连接超时时间（默认为10秒）
                    .writeTimeout(120, TimeUnit.SECONDS) // 设置写的超时时间
                    .readTimeout(120, TimeUnit.SECONDS) //设置读取超时时间
                    .build()
        }

        hideSoftInputMode(et_code)
        mHandler.sendEmptyMessageDelayed(SETFOCUS, 200)

        bundle()
        getUserInfo()
    }

    private fun bundle() {
        val bundle = context.intent.extras
        if (bundle != null) {
        }
    }

    @OnClick(R.id.btn_close, R.id.btn_scan)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.btn_close -> {// 关闭
                context.finish()
            }
            R.id.btn_scan -> { // 调用摄像头扫描（物料）
                ScanUtil.startScan(context, BaseFragment.CAMERA_SCAN, HmsScanAnalyzerOptions.Creator().setHmsScanTypes(HmsScan.ALL_SCAN_TYPE).create())
//                cainiaoPrintData = "abc"
//                setPrintData(null)
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
     * 查询打印数据
     */
    private fun run_findPrintData() {
        isTextChange = false
        showLoadDialog("准备打印...", false)
        val mUrl = getURL("appPrint/printExpressNoBySaoMa")
        val formBody = FormBody.Builder()
                .add("barcode", getValues(et_code))
                .add("userName", user!!.username)
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

    /**
     * 设置打印数据
     */
    private fun setPrintData(list: List<ExpressNoData>?) {
        if(cainiaoPrintData == null) {
            listMap.clear()
            listMap.addAll(list!!)
        }

        if (isConnected) {
            if(cainiaoPrintData != null) {
                byteFormatPrint()
            } else {
                /*if (listMap[0].logisticsName.indexOf("申通") > -1) {
                    setStartPrint_ST(list!!) // 申通格式

                } else {*/
                    setStartPrint_SF(list!!) // 顺丰格式
//                }
            }
        } else {
            // 打开蓝牙配对页面
            startActivityForResult(Intent(this, BluetoothDeviceListDialog::class.java), Constant.BLUETOOTH_REQUEST_CODE)
        }
    }

    /**
     * 开始打印（顺丰格式）
     */
    private fun setStartPrint_SF(list: List<ExpressNoData>) {
        list.forEach {
            val curDate = Comm.getSysDate(0)
            val tsc = LabelCommand()
            setTscBegin_SF(tsc)
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

            var itemVal = isNULLS(it.properties_value)
            if(itemVal.length > 0) {
                val listItem = itemVal.split("<br>")
                if(listItem.size == 1) {
                    tsc.addText(40, 830, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫

                } else if(listItem.size == 2) {
                    tsc.addText(40, 830, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫
                    tsc.addText(40, 860, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[1]+"") // 高档脚垫

                } else if(listItem.size >= 3) {
                    tsc.addText(40, 830, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫
                    tsc.addText(40, 860, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[1]+"") // 高档脚垫
                    tsc.addText(40, 890, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[2]+"") // 高档脚垫
                }
            }

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
     * 开始打印（申通格式）
     */
    private fun setStartPrint_ST(list: List<ExpressNoData>) {
        list.forEach {
            val curDate = Comm.getSysDate(0)
            val tsc = LabelCommand()
            setTscBegin_ST(tsc)
            // --------------- 打印区-------------Begin

            // 上下流水结构，先画线后打印其他
            // （标准快递）画竖线
            tsc.addBar(630, 20, 2, 100)
            // （上）画横线
            tsc.addBar(0, 120, 800, 2)
            //  （集_上）画横线
            tsc.addBar(0, 230, 800, 2)
            // （集方框_左）
            tsc.addBar(30, 240, 2, 70)
            // （集方框_上）
            tsc.addBar(30, 240, 70, 2)
            // （集方框_右）
            tsc.addBar(100, 240, 2, 70)
            // （集方框_下）
            tsc.addBar(30, 310, 70, 2)
            // （集_下）画横线
            tsc.addBar(0, 320, 800, 2)
            // （收_下）画横线
            tsc.addBar(0, 440, 800, 2)
            // （寄_下）画横线
            tsc.addBar(0, 540, 800, 2)
            // （条形码_下）画横线
            tsc.addBar(0, 690, 800, 2)
            // （打印日期_右边）画竖线
            tsc.addBar(160, 690, 2, 170)
            // （二维码_左边）画竖线
            tsc.addBar(630, 690, 2, 170)
            // （二栏）_上
            tsc.addBar(0, 955, 800, 2)
            // （二栏）_中间横线
            tsc.addBar(550, 955, 2, 100)
            // （二栏）_下
            tsc.addBar(0, 1060, 800, 2)

            // 标准快递
            tsc.addText(640, 20, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "标准")
            tsc.addText(640, 70, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "快递 ")
            tsc.addText(50, 130, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_3, LabelCommand.FONTMUL.MUL_3, ""+it.t02+" ")
            // 集
            tsc.addText(40, 253, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "集 ")
            tsc.addText(130, 250, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, ""+(it.twoDimensionCode+1)+" ")
            // 收件人图片
            val shouBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_shou)
            if(shouBit != null) {
                tsc.addBitmap(30, 350, LabelCommand.BITMAP_MODE.OVERWRITE, 53, shouBit)
            }
            tsc.addText(130, 330, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT10()+"") // 收方人
            tsc.addText(270, 330, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t11+"") // 收方电话
            // 收件地址超长，计算自动换行（计算两行）
            val t12 = it.getT12()
            val t12Len = t12!!.length
            if(t12Len > 28) {
                tsc.addText(130, 360, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(0, 28)+ "") // 收方地址
                if(t12.substring(28, t12Len).trim().length > 0) {
                    tsc.addText(130, 390, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(28, t12Len)+ "") // 收方地址
                }
            } else {
                tsc.addText(130, 360, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12+ "") // 收方地址
            }
            // 寄
            // 收件人图片
            val jiBit = BitmapFactory.decodeResource(resources, R.drawable.shunfeng_ji)
            if(jiBit != null) {
                tsc.addBitmap(30, 460, LabelCommand.BITMAP_MODE.OVERWRITE, 53, jiBit)
            }
            // 寄方人
            tsc.addText(130, 450, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT15()+"")
            // 寄方电话
            tsc.addText(270, 450, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT16()+"")
            // 寄方地址
            // 寄方地址超长，计算自动换行（计算两行）
            val t17 = it.getT17()
            /*
            val t17Len = t17!!.length
            if(t17Len > 28) {
                tsc.addText(130, 480, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17.substring(0, 28)+ "") // 收方地址
                if(t17.substring(14, t17Len).trim().length > 0) {
                    tsc.addText(130, 510, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17.substring(28, t17Len)+ "") // 收方地址
                }
            } else {
                tsc.addText(130, 480, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17+ "") // 收方地址
            }*/
            // 寄方地址
            tsc.addText(130, 480, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17+ "")
            // 快递条形码
            tsc.add1DBarcode(80, 550, LabelCommand.BARCODETYPE.CODE39, 100, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, 2, 6, it.getT01())
            // 打印日期
            tsc.addText(30, 700, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, curDate.substring(0,10)+ "")
            tsc.addText(30, 730, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, curDate.substring(11,19)+ "")
            tsc.addText(30, 800, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "打印时间")
            // 二维码
            tsc.addQRCode(640, 700, LabelCommand.EEC.LEVEL_L, 7, LabelCommand.ROTATION.ROTATION_0, it.twoDimensionCode)
            val summary = "快件送达收件人地址，经收件人或收件人（寄件人）允许的代收人签字，视为送达。您的签字代表您已经签收此包裹，并已确认商品信息无误、包装完好、没有划痕、破损等表面质量问题。"
            tsc.addText(165, 700, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, summary.substring(0, 19)+"")
            tsc.addText(165, 730, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, summary.substring(19, 38) + "")
            tsc.addText(165, 760, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, summary.substring(38, 57) + "")
            tsc.addText(165, 790, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, summary.substring(57, 76) + "")
            tsc.addText(165, 820, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, summary.substring(76, summary.length) + "")
            tsc.addText(520, 840, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "签收栏")

            // 二联快递条形码
            tsc.add1DBarcode(280, 876, LabelCommand.BARCODETYPE.CODE39, 55, LabelCommand.READABEL.EANBEL, LabelCommand.ROTATION.ROTATION_0, 2, 5, it.getT01())
            // 二联（收，寄信息）
            // 收
            tsc.addText(30, 975, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "收")
            tsc.addText(100, 960, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t10+"") // 收方人
            tsc.addText(240, 960, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t11+"") // 收方电话
            if(t12Len > 18) {
                tsc.addText(100, 990, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(0, 18)+ "") // 收方地址
                if(t12.substring(18, t12Len).trim().length > 18) {
                    tsc.addText(100, 1018, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(18, 36)+ "") // 收方地址
                    if(t12.substring(36, t12Len).trim().length > 0) {
                        tsc.addText(100, 1046, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(36, t12Len)+ "") // 收方地址
                    }
                } else {
                    tsc.addText(100, 1018, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12.substring(18, t12Len)+ "") // 收方地址
                }
            } else {
                tsc.addText(100, 990, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t12+ "") // 收方地址
            }
            // 寄
            tsc.addText(560, 975, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_2, LabelCommand.FONTMUL.MUL_2, "寄")
            tsc.addText(620, 960, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT15()+"")
            // 寄方电话
            tsc.addText(620, 990, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.getT16()+"")
            // 寄方地址
            tsc.addText(620, 1020, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t17+ "")
            // 已验视
            tsc.addText(550, 1065, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "已验视：汽车脚垫")
            // 商品名
            var itemVal = isNULLS(it.properties_value)
            if(itemVal.length > 0) {
                val listItem = itemVal.split("<br>")
                if(listItem.size == 1) {
                    tsc.addText(30, 1120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫

                } else if(listItem.size == 2) {
                    tsc.addText(30, 1120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫
                    tsc.addText(30, 1150, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[1]+"") // 高档脚垫

                } else if(listItem.size >= 3) {
                    tsc.addText(30, 1120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫
                    tsc.addText(30, 1150, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[1]+"") // 高档脚垫
                    tsc.addText(30, 1180, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[2]+"") // 高档脚垫
                }
            }
//            tsc.addText(30, 1120, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.properties_value)
            // 备注超长，计算自动换行（计算四行）
            val t13 = it.getT13()
            val t13Len = t13!!.length
            if(t13Len > 30) {
                // 第一行
                tsc.addText(30, 1230, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(0, 30)+"") // 卖家备注
                if(t13.substring(30, t13Len).length > 30) { // 第二行
                    tsc.addText(30, 1260, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, 60) + "") // 卖家备注
                    if(t13.substring(60, t13Len).length > 30) { // 第三行
                        tsc.addText(30, 1290, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, 90) + "") // 卖家备注
                        if(t13.substring(90, t13Len).length > 30) { // 第四行
                            tsc.addText(30, 1320, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, 120) + "") // 卖家备注
                            if(t13.substring(120, t13Len).length > 30) { // 第五行
                                tsc.addText(30, 1350, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, 150) + "") // 卖家备注
                                // 第六行
                                tsc.addText(30, 1380, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(150, t13Len) + "") // 卖家备注
                            } else {
                                tsc.addText(30, 1350, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(120, t13Len) + "") // 卖家备注
                            }
                        } else {
                            tsc.addText(30, 1320, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(90, t13Len) + "") // 卖家备注
                        }
                    } else {
                        tsc.addText(30, 1290, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(60, t13Len) + "") // 卖家备注
                    }
                } else {
                    tsc.addText(30, 1260, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13.substring(30, t13Len) + "") // 卖家备注
                }
            } else {
                // 第一行
                tsc.addText(30, 1230, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, t13) // 卖家备注
            }
            tsc.addText(30, 1380, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, it.t01) // 卖家备注
            tsc.addText(550, 1380, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, "已验视") // 卖家备注

            // --------------- 打印区-------------End
            setTscEnd(tsc)
        }
    }

    /**
     * 菜鸟打印方式
     */
    private fun byteFormatPrint() {
//        var str = "H4sIAAAAAAAAAKVVW08bRxR+R+I/rPwYDWHOzOxc8hYIlZDa0GI/JPkP+VG2lQstNEDsQGwnkOIaZx1sAzFEbdOHqorUKuRWVa0QtTq7y5rZ2WlK1ZkHe46+88135lw2P3tjxgOML2Lv5k0EMv4zPnZldn5mujA7d9XD42PTn+bHx/LnRhZmrhU8KZFSFOUK+TxhF6c+ITmEEeide7TU/SMoNnurfwX9B1/Vhtt/Zi29nWCjNnz4Q+6MDTAoF93mUeV2AgOhcdS3YFTvnM9weIFBSJi0gETvnBbyXWM4wjHEhI0zwli/c/Ay6O916q3ei6dL9dJm7/4d9s2tWrn16H618fNIGEOS2LoimsqH/WKwcoakWhmXzkg3XjeGnZ3K22DQGLY71RcHa+XFx0vxqf4hGJgUApy3hdDy3UYz+l14HvSPqs3KcTBod7YXDg/KiykKBi6KYL/7ZaiiWG4eV5uhW+V45ah5vLf7eGl7wVLBhTPmzvLJw5Py4mppb7e91u49fdZa63/bK3V/7P5WLC2fpCjcRXS4vPNrbatY2qrv9+tf174vllqtzrtqe+Vo69VB8GTDpJDuwukGz34qlkcZIkhg7MIRTGASy0nCTCx1ygK4hOkloCaSO2+HSRhp1CFKxl2o8EE311NdwJgL+OT3wfvKK8/zQAosFKNc0fRJk0xdnp+euzLj+VTXuq9lAZE55AtNEnaJJhYCFOYGlBJduTKBsvC2U+j1gi8AlMRMgXZLaZROjf+pY8LG+x+hJhyhfgdH7U2v3H7nRQskUEFBKCCmo/+RGM43vMzJxJWvJ5O7lvd2d38J9o0HVPw8oi/odSo6yRYwinxGTrOlIMoWQ/wfs6XnErPLM5qCz1d37m68rr5MgCSsGOe8bNwzyCizx048UwfmhZmXjXmaBkYJ8q88gH27X8+IpuauefGIFxxR3YYc4cRVZwKIs93CQbS5mt3mraCck3Vl/bD04Fa8R3CO9EXOETgVLxNJM5mIkNPJivPshV84HQ8N44nOOGOJz0IpRD9i0SkVkp4ZCLMM1EYwG+ETkTZwoUwDcByZKAJFEozyVdoJ9JtaFkwV4hQSC9fH0E3z8NMgqa6QDPWEDtOimtDkWVMGpTIgBWBZhJ8JLrKlJTBpPdJE5h0nmP7SWOFFflZ8JvsXcYdzJvR4EOgz5KPLukQcjf35/OxVXVEIUn//BsDRkFDpCQAA"
        // 发送数据
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null) {
            return
        }
//        var byteArr = Base64Utils.decode(str.toCharArray())
        var byteArr = Base64Utils.decode(cainiaoPrintData!!.toCharArray())
        byteArr = Base64Utils.compress(byteArr)
        // 解决打印重复打印的问题
        // PRINT 1,1对应的byte值（80,82,73,78,84,32,49,44,49,13,10），如果末尾有两份这种一样的值，就删除后面一段
        if(byteArr[byteArr.size-1].toInt() == 10 && byteArr[byteArr.size-2].toInt() == 13 && byteArr[byteArr.size-3].toInt() == 49 && byteArr[byteArr.size-4].toInt() == 44 && byteArr[byteArr.size-5].toInt() == 49 && byteArr[byteArr.size-6].toInt() == 32 && byteArr[byteArr.size-7].toInt() == 84 && byteArr[byteArr.size-8].toInt() == 78 && byteArr[byteArr.size-9].toInt() == 73 && byteArr[byteArr.size-10].toInt() == 82 && byteArr[byteArr.size-11].toInt() == 80 &&
	        		byteArr[byteArr.size-12].toInt() == 10 && byteArr[byteArr.size-13].toInt() == 13 && byteArr[byteArr.size-14].toInt() == 49 && byteArr[byteArr.size-15].toInt() == 44 && byteArr[byteArr.size-16].toInt() == 49 && byteArr[byteArr.size-17].toInt() == 32 && byteArr[byteArr.size-18].toInt() == 84 && byteArr[byteArr.size-19].toInt() == 78 && byteArr[byteArr.size-20].toInt() == 73 && byteArr[byteArr.size-21].toInt() == 82 && byteArr[byteArr.size-22].toInt() == 80) {

            val byteResult = ByteArray(byteArr.size-11)
            System.arraycopy(byteArr, 0, byteResult, 0, byteResult.size)
            byteArr = byteResult
        }

        val vbr = Vector<Byte>()
        for(bt in byteArr.iterator()) {
            vbr.add(bt)
        }
        DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].sendDataImmediately(vbr)
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
     * 打印前段配置（顺丰格式）
     * @param tsc
     */
    private fun setTscBegin_SF(tsc: LabelCommand) {
        // 设置标签尺寸，按照实际尺寸设置
        tsc.addSize(100, 210)
        // 设置标签间隙，按照实际尺寸设置，如果为无间隙纸则设置为0
        tsc.addGap(10)
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
     * 打印前段配置
     * @param tsc
     */
    private fun setTscBegin_ST(tsc: LabelCommand) {
        // 设置标签尺寸，按照实际尺寸设置
        tsc.addSize(100, 180)
        // 设置标签间隙，按照实际尺寸设置，如果为无间隙纸则设置为0
        tsc.addGap(10)
        // 设置打印方向
        tsc.addDirection(LabelCommand.DIRECTION.FORWARD, LabelCommand.MIRROR.NORMAL)
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
                            if(cainiaoPrintData != null) {
                                byteFormatPrint()

                            } else {
                                /*if (listMap[0].logisticsName.indexOf("申通") > -1) {
                                    setStartPrint_ST(listMap) // 申通格式

                                } else {*/
                                    setStartPrint_SF(listMap) // 顺丰格式
//                                }
                            }

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