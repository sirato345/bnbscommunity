package com.finance.model.net;

import android.os.AsyncTask;

import com.finance.common.Const;
import com.finance.common.LogWriter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * ネット接億クラス
 */
public class NetConnectorGoogle extends AsyncTask {

    private NetOperatorGoogle netOperatorGoogle;
    private List<String> calendarDataList;
    private String symbol;

    public NetConnectorGoogle(NetOperatorGoogle netOperatorGoogle) {
        this.netOperatorGoogle = netOperatorGoogle;
        this.calendarDataList = new ArrayList<>();
    }

    @Override
    protected Object doInBackground(Object[] params) {
        this.symbol = (String)params[0];
        String url = (String)params[1];
        URL getUrl;
        String netData;
        // URLオブジェクト取得
        try {
            getUrl = new URL(url);
        } catch (MalformedURLException e) {
            return Const.MESSAGE_6;
        }
        LogWriter.getWriter().printLog("アクセスURL：" + url);
        for (int i = 0; i < Const.CONNECT_TIMES; i ++) {
            try {
                URLConnection conn = getUrl.openConnection();
                conn.setConnectTimeout(3000);
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                while ((netData = reader.readLine()) != null) {
                    calendarDataList.add(netData);
                }
                break;
            } catch (Exception e) {
                LogWriter.getWriter().printLog("銘柄：" + symbol +
                                               "、接続失敗回数：" + i +1);
            }
        }
        // ネットアクセス正常
        return Const.SUCCESS;
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        if (o.equals(Const.SUCCESS)) {
            netOperatorGoogle.updateDB(symbol, calendarDataList);
        }
    }
}
