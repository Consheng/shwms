package ykk.xc.com.shwms.entrance


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import butterknife.OnClick
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.User
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.sales.*
import ykk.xc.com.shwms.warehouse.OutInStock_Search_MainActivity

/**
 * 销售
 */
class MainTabFragment3 : BaseFragment() {

    private var user: User? = null

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.aa_main_item3, container, false)
    }

    override fun initView() {
        getUserInfo()
    }

    @OnClick(R.id.relative1, R.id.relative2, R.id.relative3, R.id.relative4, R.id.relative5, R.id.relative6)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.relative1 -> {// 待上传
                val bundle = Bundle()
                bundle.putInt("pageId", 3)
                bundle.putString("billType", "DS_XSCK")
                show(OutInStock_Search_MainActivity::class.java, bundle)
//                show(Sal_OutStockMainActivity::class.java, null)
            }
            R.id.relative2 -> {// 销售出库
                /*if(user!!.deptId == 0) {
                    Comm.showWarnDialog(activity,"您没有维护用户的部门信息，请在PC端维护！")
                    return
                }*/
                show(Sal_DS_OutStockMainActivity::class.java, null)
            }
            R.id.relative3 -> {// 快递单出库
                show(Sal_DS_OutStock_ExpressNo_Activity::class.java, null)
            }
            R.id.relative4 -> {// 销售退货
                show(Sal_DS_OutStock_RED_MainActivity::class.java, null)
            }
            R.id.relative5 -> {// 快递打印
                show(Sal_DS_OutStockPrintActivity::class.java, null)
            }
            R.id.relative6 -> {// 打印解锁
                show(Sal_DS_OutStockUnLockActivity::class.java, null)
            }
        }
    }

    /**
     * 得到用户对象
     */
    private fun getUserInfo() {
        if (user == null) user = showUserByXml()
    }
}
