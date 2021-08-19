package ykk.xc.com.shwms.purchase.adapter

import android.app.Activity
import android.widget.TextView
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.ICStockBillEntry_Old
import ykk.xc.com.shwms.bean.ICStockBillEntryBarcode_Old
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.util.basehelper.BaseArrayRecyclerAdapter
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import java.text.DecimalFormat

class Pur_Receive_InStock_Fragment4_Adapter(private val context: Activity, datas: List<ICStockBillEntryBarcode_Old>) : BaseArrayRecyclerAdapter<ICStockBillEntryBarcode_Old>(datas) {
    private val df = DecimalFormat("#.######")
    private var callBack: MyCallBack? = null

    override fun bindView(viewtype: Int): Int {
        return R.layout.pur_receive_in_stock_fragment4_item
    }

    override fun onBindHoder(holder: BaseRecyclerAdapter.RecyclerHolder, entity: ICStockBillEntryBarcode_Old, pos: Int) {
        // 初始化id
        val tv_row = holder.obtainView<TextView>(R.id.tv_row)
        val tv_barcode = holder.obtainView<TextView>(R.id.tv_barcode)
        val tv_batchCode = holder.obtainView<TextView>(R.id.tv_batchCode)
        val tv_snCode = holder.obtainView<TextView>(R.id.tv_snCode)

        // 赋值
        tv_row.text = (pos+1).toString()
        tv_barcode.text = entity.barcode
        tv_batchCode.text = Comm.isNULLS(entity.batchCode)
        tv_snCode.text = Comm.isNULLS(entity.snCode)
    }

    fun setCallBack(callBack: MyCallBack) {
        this.callBack = callBack
    }

    interface MyCallBack {
        fun onDelete(entity: ICStockBillEntry_Old, position: Int)
    }

}
