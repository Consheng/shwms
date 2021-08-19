package ykk.xc.com.shwms.produce.adapter

import android.app.Activity
import android.widget.TextView

import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.Procedure
import ykk.xc.com.shwms.bean.ProcessflowEntry
import ykk.xc.com.shwms.util.basehelper.BaseArrayRecyclerAdapter
import ykk.xc.com.shwms.util.basehelper.BaseRecyclerAdapter

class Procedure_Sel_DialogAdapter(private val context: Activity, private val datas: List<ProcessflowEntry>) : BaseArrayRecyclerAdapter<ProcessflowEntry>(datas) {
    private var callBack: MyCallBack? = null

    override fun bindView(viewtype: Int): Int {
        return R.layout.procedure_sel_dialog_item
    }

    override fun onBindHoder(holder: BaseRecyclerAdapter.RecyclerHolder, entity: ProcessflowEntry, pos: Int) {
        // 初始化id
        val tv_fname = holder.obtainView<TextView>(R.id.tv_fname)
        // 赋值
        tv_fname!!.setText(entity.procedureName)
    }

    fun setCallBack(callBack: MyCallBack) {
        this.callBack = callBack
    }

    interface MyCallBack {
        fun onClick(entity: Procedure, position: Int)
    }

}
