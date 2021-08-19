package ykk.xc.com.shwms.sales

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED
import android.os.Handler
import android.os.Message
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import butterknife.OnClick
import com.gprinter.command.EscCommand
import com.gprinter.command.LabelCommand
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import kotlinx.android.synthetic.main.sal_ds_out_main.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.ExpressNoData
import ykk.xc.com.shwms.comm.BaseActivity
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.Base64Utils
import ykk.xc.com.shwms.util.adapter.BaseFragmentAdapter
import ykk.xc.com.shwms.util.blueTooth.*
import ykk.xc.com.shwms.util.blueTooth.Constant.MESSAGE_UPDATE_PARAMETER
import ykk.xc.com.shwms.util.blueTooth.DeviceConnFactoryManager.CONN_STATE_FAILED
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * 日期：2019-10-16 09:14
 * 描述：电商销售出库
 * 作者：ykk
 */
class Sal_DS_OutStockMainActivity : BaseActivity() {

    private val context = this
    private val TAG = "Sal_DS_OutStockMainActivity"
    private var curRadio: View? = null
    private var curRadioName: TextView? = null
    var isChange: Boolean = false // 返回的时候是否需要判断数据是否保存了
//    private val listMaps = ArrayList<Map<String, Any>>()
    private val df = DecimalFormat("#.####")
    val fragment1 = Sal_DS_OutStockFragment1()
    var isMainSave = false // 主表信息是否保存
    // 蓝牙打印用到的
    private var isConnected: Boolean = false // 蓝牙是否连接标识
    private val id = 0 // 设备id
    private var threadPool: ThreadPool? = null
    private val CONN_STATE_DISCONN = 0x007 // 连接状态断开
    private val PRINTER_COMMAND_ERROR = 0x008 // 使用打印机指令错误
    private val CONN_PRINTER = 0x12
    private var listMap = ArrayList<ExpressNoData>() // 打印保存的数据
    var cainiaoPrintData :String? = null // 菜鸟打印数据

    override fun setLayoutResID(): Int {
        return R.layout.sal_ds_out_main
    }

    override fun initData() {
        bundle()
        curRadio = viewRadio1
//        curRadioName = tv_radioName1
        val listFragment = ArrayList<Fragment>()
//        Bundle bundle2 = new Bundle();
//        bundle2.putSerializable("customer", customer);
//        fragment1.setArguments(bundle2); // 传参数
//        fragment2.setArguments(bundle2); // 传参数
//        Pur_ScInFragment1 fragment1 = new Pur_ScInFragment1();
//        Sal_OutFragment2 fragment2 = new Sal_OutFragment2();
//        Sal_OutFragment3 fragment3 = new Sal_OutFragment3();

        listFragment.add(fragment1)
        viewPager.setScanScroll(false); // 禁止左右滑动
        //ViewPager设置适配器
        viewPager.setAdapter(BaseFragmentAdapter(supportFragmentManager, listFragment))
        //设置ViewPage缓存界面数，默认为1
        viewPager.offscreenPageLimit = 1
        //ViewPager显示第一个Fragment
        viewPager!!.setCurrentItem(0)

        //ViewPager页面切换监听
        viewPager!!.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {
//                when (position) {
//                    0 -> tabChange(viewRadio1!!, tv_radioName1, "表头", 0)
//                    1 -> tabChange(viewRadio2!!, tv_radioName2, "添加分录", 1)
//                    2 -> tabChange(viewRadio3!!, tv_radioName3, "表体", 2)
//                }
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })

    }

    private fun bundle() {
        val bundle = context.intent.extras
        if (bundle != null) {
        }
    }

    @OnClick(R.id.btn_close, R.id.lin_tab1, R.id.lin_tab2, R.id.lin_tab3, R.id.btn_appointment)
    fun onViewClicked(view: View) {
        // setCurrentItem第二个参数控制页面切换动画
        //  true:打开/false:关闭
        //  viewPager.setCurrentItem(0, false);

        when (view.id) {
            R.id.btn_close // 关闭
            -> {
                if (isChange) {
                    val build = AlertDialog.Builder(context)
                    build.setIcon(R.drawable.caution)
                    build.setTitle("系统提示")
                    build.setMessage("您有未保存的数据，继续关闭吗？")
                    build.setPositiveButton("是", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            context.finish()
                        }
                    })
                    build.setNegativeButton("否", null)
                    build.setCancelable(false)
                    build.show()

                } else {
                    context.finish()
                }
            }
            R.id.btn_appointment -> { // 预约
//                context.fragment1.appointment()
            }
            R.id.lin_tab1 -> {
//                tabChange(viewRadio1!!, tv_radioName1, "表头", 0)
            }
            R.id.lin_tab2 -> {
                if(isMainSave) {
//                    tabChange(viewRadio2!!, tv_radioName2, "添加分录", 1)
                } else {
                    Comm.showWarnDialog(context,"请先完善（表头）信息！")
                }
            }
            R.id.lin_tab3 -> {
                if(isMainSave) {
//                    tabChange(viewRadio3!!, tv_radioName3, "表体", 2)
                } else {
                    Comm.showWarnDialog(context,"请先完善（表头）信息！")
                }
            }
        }
    }

    /**
     * 选中之后改变样式
     */
    private fun tabSelected(v: View, tv: TextView) {
        curRadio!!.setBackgroundResource(R.drawable.check_off2)
        v.setBackgroundResource(R.drawable.check_on)
        curRadio = v
        curRadioName!!.setTextColor(Color.parseColor("#000000"))
        tv.setTextColor(Color.parseColor("#FF4400"))
        curRadioName = tv
    }

    private fun tabChange(view: View, tv: TextView, str: String, page: Int) {
        tabSelected(view, tv)
//        tv_title.text = str
        viewPager!!.setCurrentItem(page, false)
    }

    /**
     * Fragment回调得到数据
     */
    fun setPrintData(list: List<ExpressNoData>?) {
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

            var itemVal = isNULLS(it.properties_value)
            if(itemVal.length > 0) {
                val listItem = itemVal.split("<br>")
                if(listItem.size == 1) {
                    tsc.addText(40, 830, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫

                } else if(listItem.size == 2) {
                    tsc.addText(40, 830, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[0]+"") // 高档脚垫
                    tsc.addText(40, 850, LabelCommand.FONTTYPE.SIMPLIFIED_CHINESE, LabelCommand.ROTATION.ROTATION_0, LabelCommand.FONTMUL.MUL_1, LabelCommand.FONTMUL.MUL_1, listItem[1]+"") // 高档脚垫

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
     *
     */
    private fun byteFormatPrint() {
//        val str = "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"
        // 发送数据
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] == null) {
            return
        }
        var b = Base64Utils.decode(cainiaoPrintData!!.toCharArray())
        b = Base64Utils.compress(b)
        val vbr = Vector<Byte>()
        for(bt in b.iterator()) {
            vbr.add(bt)
        }
        val tsc = LabelCommand()
        // 设置打印方向
        tsc.addDirection(LabelCommand.DIRECTION.FORWARD, LabelCommand.MIRROR.NORMAL)
        vbr.addAll(tsc.command)
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
                        fragment1.getScanData(hmsScan.originalValue)
                    }
                }
            }
        }
        mHandler.postDelayed(Runnable {
            context.fragment1.refreshOnActivityResult()
        },200)
    }

    /**
     * 打印前段配置（顺丰）
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
     * 打印前段配置（申通）
     * @param tsc
     */
    private fun setTscBegin_ST(tsc: LabelCommand) {
        // 设置标签尺寸，按照实际尺寸设置
        tsc.addSize(100, 180)
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

    @SuppressLint("HandlerLeak")
    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                CONN_STATE_DISCONN -> if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id] != null) {
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].closePort(id)
                }
                PRINTER_COMMAND_ERROR -> Utils.toast(context, getString(R.string.str_choice_printer_command))
                CONN_PRINTER -> Utils.toast(context, getString(R.string.str_cann_printer))
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
                            .setId(id)
                            //设置连接的热点端口号
                            .setPort(Integer.parseInt(strPort))
                            .build()
                    threadPool = ThreadPool.getInstantiation()
                    threadPool!!.addTask(Runnable { DeviceConnFactoryManager.getDeviceConnFactoryManagers()[id].openPort() })
                }
                else -> {
                }
            }
        }
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