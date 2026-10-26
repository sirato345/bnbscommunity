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
        String intradayInterval = bundle.getString("IntradayInterval");
        boolean isCrypto = isCryptoSymbol(symbol);
        Button button = null;

        if (symbol.equals(Const.Symbol.btc.toString())) {
            button = (Button)this.findViewById(R.id.button_btc);
        }
        if (symbol.equals(Const.Symbol.eth.toString())) {
            button = (Button)this.findViewById(R.id.button_eth);
        }
        if (symbol.equals(Const.Symbol.trump.toString())) {
            button = (Button)this.findViewById(R.id.button_trump);
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
            button = null;
        }
        if (symbol.equals(Const.Symbol.usdx.toString())) {
            button = (Button)this.findViewById(R.id.button_dollor);
        }
        if (symbol.equals(Const.Symbol.usbond.toString())) {
            button = (Button)this.findViewById(R.id.button_usbond);
        }
        if (symbol.equals(Const.Symbol.fng.toString())) {
            button = (Button)this.findViewById(R.id.button_fng);
        }
        if (button != null) {
            Drawable drawable = getResources().getDrawable(R.drawable.button_symbol_select);
            button.setBackground(drawable);
        }
        if (macd.equals(Const.Macd.KDJ.toString())) {
            button = (Button)this.findViewById(R.id.button_kdj);
        } else {
            button = (Button)this.findViewById(R.id.button_macd);
        }
        Drawable drawable= getResources().getDrawable(R.drawable.button_macd_select);
        button.setBackground(drawable);
        if (kLine.equals(Const.K_Line.Average.toString())) {
            button = (Button)this.findViewById(R.id.button_k_line_average);
        }
        if (kLine.equals(Const.K_Line.Normal.toString())) {
            button = (Button)this.findViewById(R.id.button_k_line_normal);
        }
        drawable= getResources().getDrawable(R.drawable.button_k_line_select);
        button.setBackground(drawable);
        String selectedTimeFrame = intradayInterval == null ? timeFrame : intradayInterval;
        if (selectedTimeFrame.equals(Const.TimeFrame.d3.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_3day);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.d.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_day);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.w.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_week);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.m.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_month);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.q.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_quarter);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.y.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_year);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.h4.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_4h);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.h1.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_1h);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.m15.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_15m);
        }
        if (selectedTimeFrame.equals(Const.TimeFrame.m5.toString())) {
            button = (Button)this.findViewById(R.id.button_timeframe_5m);
        }
        if (button != null) {
            drawable= getResources().getDrawable(R.drawable.button_timeframe_select);
            button.setBackground(drawable);
        }
        setCryptoTimeframeEnabled(R.id.button_timeframe_3day, isCrypto);
        setCryptoTimeframeEnabled(R.id.button_timeframe_4h, isCrypto);
        setCryptoTimeframeEnabled(R.id.button_timeframe_1h, isCrypto);
        setCryptoTimeframeEnabled(R.id.button_timeframe_15m, isCrypto);
        setCryptoTimeframeEnabled(R.id.button_timeframe_5m, isCrypto);
        if (Const.Symbol.fng.toString().equals(symbol)) {
            setCryptoTimeframeEnabled(R.id.button_timeframe_week, false);
            setCryptoTimeframeEnabled(R.id.button_timeframe_month, false);
            setCryptoTimeframeEnabled(R.id.button_timeframe_quarter, false);
            setCryptoTimeframeEnabled(R.id.button_timeframe_year, false);
        }
    }

    private void setCryptoTimeframeEnabled(int buttonId, boolean enabled) {
        Button button = findViewById(buttonId);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
    }

    private boolean isCryptoSymbol(String symbol) {
        return Const.Symbol.btc.toString().equals(symbol)
                || Const.Symbol.eth.toString().equals(symbol)
                || Const.Symbol.trump.toString().equals(symbol);
    }

    public void onClick_Btc(View v) {
        setResult(Const.Symbol.btc);
    }
    public void onClick_Eth(View v) {
        setResult(Const.Symbol.eth);
    }
    public void onClick_Trump(View v) {
        setResult(Const.Symbol.trump);
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
    public void onClick_Fng(View v) {
        setResult(Const.Symbol.fng);
    }
    public void onClick_Macd(View v) {
        setResult(Const.Macd.Single);
    }
    public void onClick_Kdj(View v) {
        setResult(Const.Macd.KDJ);
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
    public void onClick_Timeframe_3D(View v) {
        setResult(Const.TimeFrame.d3);
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
    public void onClick_Timeframe_4H(View v) {
        setResult(Const.TimeFrame.h4);
    }
    public void onClick_Timeframe_1H(View v) {
        setResult(Const.TimeFrame.h1);
    }
    public void onClick_Timeframe_15M(View v) {
        setResult(Const.TimeFrame.m15);
    }
    public void onClick_Timeframe_5M(View v) {
        setResult(Const.TimeFrame.m5);
    }
}
