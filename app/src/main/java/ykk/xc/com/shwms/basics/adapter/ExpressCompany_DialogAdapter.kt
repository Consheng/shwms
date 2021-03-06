package ykk.xc.com.shwms.basics.adapter

import android.app.Activity
import android.widget.TextView

import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.k3Bean.ExpressCompany
import ykk.xc.com.shwms.util.basehelper.BaseArrayRecyclerAdapter
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter

class ExpressCompany_DialogAdapter(private val context: Activity, private val datas: List<ExpressCompany>) : BaseArrayRecyclerAdapter<ExpressCompany>(datas) {
    private var callBack: MyCallBack? = null

    override fun bindView(viewtype: Int): Int {
        return R.layout.ab_express_company_dialog_item
    }

    override fun onBindHoder(holder: BaseRecyclerAdapter.RecyclerHolder, entity: ExpressCompany, pos: Int) {
        // 初始化id
        val tv_row = holder.obtainView<TextView>(R.id.tv_row)
        val tv_fnumber = holder.obtainView<TextView>(R.id.tv_fnumber)
        val tv_fname = holder.obtainView<TextView>(R.id.tv_fname)
        // 赋值
        tv_row!!.setText((pos + 1).toString())
        tv_fnumber!!.setText(entity.fnumber)
        tv_fname!!.setText(entity.fname)
    }

    fun setCallBack(callBack: MyCallBack) {
        this.callBack = callBack
    }

    interface MyCallBack {
        fun onClick(entity: ExpressCompany, position: Int)
    }

}
