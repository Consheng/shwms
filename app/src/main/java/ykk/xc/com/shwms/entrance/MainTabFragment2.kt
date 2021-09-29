package ykk.xc.com.shwms.entrance


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.OnClick
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.bean.User
import ykk.xc.com.shwms.comm.BaseFragment
import ykk.xc.com.shwms.comm.Comm
import ykk.xc.com.shwms.produce.*
import ykk.xc.com.shwms.sales.Sal_OrderRemarkSearchActivity
import ykk.xc.com.shwms.warehouse.OutInStock_Search_MainActivity

/**
 * 生产
 */
class MainTabFragment2 : BaseFragment() {

    private var user: User? = null

    override fun setLayoutResID(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.aa_main_item2, container, false)
    }

    override fun initView() {
        getUserInfo()
    }

    @OnClick(R.id.relative1, R.id.relative2, R.id.relative3, R.id.relative4, R.id.relative5, R.id.relative6)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.relative1 -> {  // 待上传
                val bundle = Bundle()
                bundle.putInt("pageId", 4)
                bundle.putString("billType", "SCRK")
                show(OutInStock_Search_MainActivity::class.java, bundle)
//                show(Prod_Box_MainActivity::class.java, null)
            }
            R.id.relative2 -> { // 生产入库
                show(Prod_InStock_MainActivity::class.java, null)
//                show(Prod_Box_UnBind_MainActivity::class.java, null)
            }
            R.id.relative3  -> { // 工序汇报
                if(user!!.deptId == 0) {
                    Comm.showWarnDialog(activity,"您没有维护用户的部门信息，请在PC端维护！")
                    return
                }
                show(Prod_Report_MainActivity::class.java, null)
            }
            R.id.relative4  -> { // 报工查询
                show(Prod_Report_SearchActivity::class.java, null)
            }
            R.id.relative5  -> { // 图纸查看
                show(Prod_ProcessFlowImageShow_Activity::class.java, null)
            }
            R.id.relative6 -> {// 备注查询
                show(Sal_OrderRemarkSearchActivity::class.java, null)
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
