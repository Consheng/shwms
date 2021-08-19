package ykk.xc.com.shwms.warehouse.adapter

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.ICStockBill_App
import ykk.xc.com.shwms.util.basehelper.BaseArrayRecyclerAdapter
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter
import java.text.DecimalFormat

class OutInStockSearchFragment14_XSCK_BTOR_Adapter(private val context: Activity, datas: List<ICStockBill_App>) : BaseArrayRecyclerAdapter<ICStockBill_App>(datas) {
    private val df = DecimalFormat("#.######")
    private var callBack: MyCallBack? = null

    override fun bindView(viewtype: Int): Int {
        return R.layout.ware_outin_stock_search_fragment2__other_out_stock_item2
    }

    override fun onBindHoder(holder: BaseRecyclerAdapter.RecyclerHolder, entity: ICStockBill_App, pos: Int) {
        // 初始化id
        val tv_pdaNo = holder.obtainView<TextView>(R.id.tv_pdaNo)
        val tv_fdate = holder.obtainView<TextView>(R.id.tv_fdate)
        val tv_baoguanMan = holder.obtainView<TextView>(R.id.tv_baoguanMan)
        val tv_custName = holder.obtainView<TextView>(R.id.tv_custName)
        val tv_deptName = holder.obtainView<TextView>(R.id.tv_deptName)
        val tv_search = holder.obtainView<TextView>(R.id.tv_search)
        val tv_upload = holder.obtainView<TextView>(R.id.tv_upload)
        val tv_del = holder.obtainView<TextView>(R.id.tv_del)
        val lin_button = holder.obtainView<LinearLayout>(R.id.lin_button)

        // 赋值
        tv_pdaNo.text = entity.pdaNo
        tv_fdate.text = entity.fdate
        tv_baoguanMan.text = entity.baoguanMan
        if(entity.cust != null) {
            tv_custName.text = entity.cust.fname
        } else {
            tv_custName.text = ""
        }
        if(entity.department != null) {
            tv_deptName.text = entity.department.fname
        } else {
            tv_deptName.text = ""
        }

        if (entity.isShowButton) {
            lin_button!!.setVisibility(View.VISIBLE)
        } else {
            lin_button!!.setVisibility(View.GONE)
        }

        val click = View.OnClickListener { v ->
            when (v.id) {
                R.id.tv_search -> { // 查询
                    if (callBack != null) {
                        callBack!!.onSearch(entity, pos)
                    }
                }
                R.id.tv_upload -> {// 上传
                    if (callBack != null) {
                        callBack!!.onUpload(entity, pos)
                    }
                }
                R.id.tv_del -> { // 删除行
                    if (callBack != null) {
                        callBack!!.onDelete(entity, pos)
                    }
                }
            }
        }
        tv_search.setOnClickListener(click)
        tv_upload.setOnClickListener(click)
        tv_del!!.setOnClickListener(click)
    }

    fun setCallBack(callBack: MyCallBack) {
        this.callBack = callBack
    }

    interface MyCallBack {
        fun onSearch(entity: ICStockBill_App, position: Int)
        fun onUpload(entity: ICStockBill_App, position: Int)
        fun onDelete(entity: ICStockBill_App, position: Int)
    }

}
