package com.finance.view;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.finance.R;
import com.finance.common.Const;

/**
 * 設定画面：銘柄、MACD、ローソク線、タイムフレーム
 */
public class ChildActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // View設定
        super.setContentView(R.layout.activity_child);
        // 画面サイズ設定
        this.setSize();
        // 初期選択設定
        this.setInitSelect();
    }

    /* 横竖屏取得 */
    public int getOrientation() {
        return this.getResources().getConfiguration().orientation;
    }

    /* 画面サイズ設定 */
    private void setSize() {
        WindowManager windowManager = getWindowManager();
        // 为获取屏幕宽、高
        Display display = windowManager.getDefaultDisplay();
        // 获取对话框当前的参值
        WindowManager.LayoutParams p = getWindow().getAttributes();
        if (this.getOrientation() == Configuration.ORIENTATION_PORTRAIT){
            p.height = (int) (display.getHeight() * 0.4);
            p.width = (int) (display.getWidth() * 0.85);
        } else {
            p.height = (int) (display.getHeight() * 0.75);
            p.width = (int) (display.getWidth() * 0.50);
        }
        // 设置生效
        getWindow().setAttributes(p);
    }

    /* 画面選択結果を戻す */
    private void setResult(Enum result) {
        // 戻り値保持オブジェクト
        Intent intent = this.getIntent();
        intent.putExtra(Const.KEY_RESULT, (Enum)result);
        setResult(Const.REQUEST_CODE, intent);
        finish();
    }

    /* 画面初期ボタン選択状態設定 */
    private void setInitSelect() {
        //获取Intent中的Bundle数据
        Bundle bundle = this.getIntent().getExtras();
        String symbol = bundle.getString(Const.KEY_SYMBOL);
        String macd = bundle.getString(Const.KEY_MACD);
        String kLine = bundle.getString(Const.KEY_K_LINE);
        String timeFrame = bundle.getString(Const.KEY_TIME_FRAME);
        Button button = null;

        if (symbol.equals(Const.Symbol.btc.toString())) {
            button = (Button)this.findViewById(R.id.button_btc);
        }
        if (symbol.equals(Const.Symbol.eth.toString())) {
            button = (Button)this.findViewById(R.id.button_eth);
        }
        if (symbol.equals(Const.Symbol.dow.toString())) {
            button = (Button)this.findViewById(R.id.button_dow);
        }
        if (symbol.equals(Const.Symbol.sp500.toString())) {
            button = (Button)this.findViewById(R.id.button_sp500);
        }
        if (symbol.equals(Const.Symbol.nasdaq.toString())) {
            button = (Button)this.findViewById(R.id.button_nasdaq);
        }
        if (symbol.equals(Const.Symbol.nikkei.toString())) {
            button = (Button)this.findViewById(R.id.button_nikkei);
        }
        if (symbol.equals(Const.Symbol.usdx.toString())) {
            button = (Button)this.findViewById(R.id.button_dollor);
        }
        if (symbol.equals(Const.Symbol.usbond.toString())) {
            button = (Button)this.findViewById(R.id.button_usbond);
        }
        if (symbol.equals(Const.Symbol.xauusd.toString())) {
            button = (Button)this.findViewById(R.id.button_gold);
        }
        if (symbol.equals(Const.Symbol.xagusd.toString())) {
            button = (Button)this.findViewById(R.id.button_silver);
        }
        if (button != null) {
            Drawable drawable = getResources().getDrawable(R.drawable.button_symbol_select);
            button.setBackground(drawable);
        }
        Drawable drawable;
        if (macd.equals(Const.Macd.Single.toString())) {
            button = (Button)this.findViewById(R.id.button_macd_single);
        }
        if (macd.equals(Const.Macd.Double.toString())) {
            button = (Button)this.findViewById(R.id.button_macd_double);
        }
        drawable= getResources().getDrawable(R.drawable.button_macd_select);
        button.setBackground(drawable);
        if (kLine.equals(Const.K_Line.Average.toString())) {
            button = (Button)this.findViewById(R.id.button_k_line_average);
        }
        if (kLine.equals(Const.K_Line.Normal.toString())) {
            button = (Button)this.findViewById(R.id.button_k_line_normal);
        }
        drawable= getResources().getDrawable(R.drawable.button_k_line_select);
        button.setBackground(drawable);
        if (timeFrame.equals(Const.TimeFrame.d.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_day);
        }
        if (timeFrame.equals(Const.TimeFrame.w.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_week);
        }
        if (timeFrame.equals(Const.TimeFrame.m.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_month);
        }
        if (timeFrame.equals(Const.TimeFrame.q.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_quarter);
        }
        if (timeFrame.equals(Const.TimeFrame.y.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_year);
        }
        drawable= getResources().getDrawable(R.drawable.button_timeframe_select);
        button.setBackground(drawable);
    }

    public void onClick_Btc(View v) {
        setResult(Const.Symbol.btc);
    }
    public void onClick_Eth(View v) {
        setResult(Const.Symbol.eth);
    }
    public void onClick_Dow(View v) {
        setResult(Const.Symbol.dow);
    }
    public void onClick_Sp500(View v) {
        setResult(Const.Symbol.sp500);
    }
    public void onClick_Nasdaq(View v) {
        setResult(Const.Symbol.nasdaq);
    }

    public void onClick_Nikkei(View v) {
        setResult(Const.Symbol.nikkei);
    }
    public void onClick_Dollor(View v) {
        setResult(Const.Symbol.usdx);
    }
    public void onClick_Usbond(View v) {
        setResult(Const.Symbol.usbond);
    }
    public void onClick_Gold(View v) {
        setResult(Const.Symbol.xauusd);
    }
    public void onClick_Silver(View v) {
        setResult(Const.Symbol.xagusd);
    }
    public void onClick_Macd_Single(View v) {
        setResult(Const.Macd.Single);
    }
    public void onClick_Macd_Double(View v) {
        setResult(Const.Macd.Double);
    }
    public void onClick_K_Line_Normal(View v) {
        setResult(Const.K_Line.Normal);
    }
    public void onClick_K_Line_Average(View v) {
        setResult(Const.K_Line.Average);
    }
    public void onClick_Timeframe_D(View v) {
        setResult(Const.TimeFrame.d);
    }
    public void onClick_Timeframe_W(View v) {
        setResult(Const.TimeFrame.w);
    }
    public void onClick_Timeframe_M(View v) {
        setResult(Const.TimeFrame.m);
    }
    public void onClick_Timeframe_Q(View v) {
        setResult(Const.TimeFrame.q);
    }
    public void onClick_Timeframe_Y(View v) {
        setResult(Const.TimeFrame.y);
    }
}
