package ykk.xc.com.shwms.util

import android.os.Handler
import android.os.Message
import android.view.KeyEvent
import android.view.View
import butterknife.OnClick
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.imageshower.*
import ykk.xc.com.shwms.R
import ykk.xc.com.shwms.comm.BaseActivity
import java.lang.ref.WeakReference

class ImageLoadActivity : BaseActivity() {

    private var context = this
    private var imgDialog: ImageLoadingDialog? = null
    private var url: String? = null

    // 消息处理
    private val mHandler = MyHandler(this)
    private class MyHandler(activity: ImageLoadActivity) : Handler() {
        private val mActivity: WeakReference<ImageLoadActivity>

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

                }
            }
        }
    }

    override fun setLayoutResID(): Int {
        return R.layout.imageshower
    }

    /**
     * 初始化数据
     */
    override fun initData() {
        imgDialog = ImageLoadingDialog(this)
        imgDialog!!.setCancelable(false)
        imgDialog!!.setCanceledOnTouchOutside(false)
        imgDialog!!.show()

        bundle()
    }

    @OnClick(R.id.imgView)
    fun onViewClicked(view: View) {
        when (view.id) {
            R.id.imgView -> context!!.finish()
        }
    }

    /**
     * 得到上个页面传来的值
     */
    private fun bundle() {
        val bundle = context!!.intent.extras
        if (bundle != null) {
            val imageUrl = bundle.getString("imageUrl", "")

            if (imageUrl.length > 6) { // 图片地址都大于6
                // 网络地址包含small的就替换成空字符串
                val samllExist = imageUrl.indexOf("_small") // 是否有small文件夹

                if (samllExist > -1) {
                    url = imageUrl.replace("_small", "") // 来自于网络地址
                } else {
                    url = imageUrl
                }

                // 加载各资源对应的常量
                //				url = Scheme.FILE.wrap(url);
                //				url = Scheme.ASSETS.wrap(url);
                //				url = Scheme.DRAWABLE.wrap(url);
                //				url = Scheme.HTTP.wrap(url);
                //				url = Scheme.HTTPS.wrap(url);

                //				Glide.with(context)
                //						.load(imageUrl)
                //						.placeholder(R.drawable.image_before)
                //						.error(R.drawable.image_error)
                //						.into(imgView);

                imgDialog!!.dismiss()
                // 延迟读取图片
                mHandler.postDelayed({
                    Glide.with(context)
                            .load(url)
                            .placeholder(R.drawable.image_wait)
                            .error(R.drawable.image_null)
                            .into(imgView!!)
                    imgDialog!!.dismiss()
                }, 200)
            }
        }
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
            context!!.finish()
        }
        return false
    }
}
