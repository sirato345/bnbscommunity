package com.finance.model.net;

import android.app.Activity;
import android.net.ConnectivityManager;

import com.finance.common.Const;
import com.finance.model.db.DBOperator;
import com.finance.model.file.FileOperator;

import java.util.List;
import java.util.Properties;

/**
 * ネット操作クラス
 */
public class NetOperatorGoogle {

    private DBOperator dbOperator;

    private Activity view;

    public NetOperatorGoogle(DBOperator dbOperator, Activity view) {
        // 監視者を設定
        this.dbOperator = dbOperator;
        this.view = view;
    }

    // 指定した銘柄の日足データを更新
    public void loadCalendarData(String symbol) {
        if (hasNetwork()) {
            // 日足のデータを取得する
            String url = this.getUrl(symbol);
            // 对于不需要收盘后立刻更新的品种，不取得日内数据
            if (url == null) {
                return;
            }
            NetConnectorGoogle netConnector = new NetConnectorGoogle(this);
            netConnector.execute(symbol, url);
        }
    }

    // ネットリアルデータ取得用のURL
    private String getUrl(String symbol) {
        Properties urlProperty = FileOperator.getGoogleCalendarURLMap();
        String url = (String)urlProperty.get(symbol);
        return url;
    }

    // ネットデータによりＤＢデータ更新
    public void updateDB(String symbol, List<String> calendarDataList) {
        // 画面切り替えはネットデータ返信により早い場合も、取得したデータでDBを更新
        // しかし、画面側は表示中の銘柄とタイムフレーム一致する場合のみ刷新
        dbOperator.updateDB(symbol, calendarDataList);
    }

    /* ネットワック有無判定 */
    private boolean hasNetwork() {
        ConnectivityManager manager = (ConnectivityManager) view.
                getSystemService(Activity.CONNECTIVITY_SERVICE);
        boolean wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).
                isConnectedOrConnecting();
        boolean internet = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                .isConnectedOrConnecting();
        if (wifi || internet) {
            return true;
        } else {
            return false;
        }
    }
}