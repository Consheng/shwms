package ykk.xc.com.shwms.comm;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import ykk.xc.com.shwms.R;

/**
 * 弹出框显示
 */
public class BaseDialog extends Dialog {

    protected Context context;
    protected View view;
    private LayoutParams layoutParams;

    public BaseDialog(Context context, View view, LayoutParams layoutParams) {
        super(context, R.style.BaseDialog);
        this.context = context;
        this.view = view;
        this.layoutParams = layoutParams;
        initBase();
    }

    private void initBase() {
        setContentView(view, layoutParams);
        setCanceledOnTouchOutside(false);
    }
}
