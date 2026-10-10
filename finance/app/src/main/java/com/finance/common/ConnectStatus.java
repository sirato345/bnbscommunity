package com.finance.common;

/**
 * Created by gu zihan on 10/04/2015.
 */
public class ConnectStatus {

    // ネットアクセス状態（true：アクセス中、false：フリー）
    private boolean connecting;
    // ネットアクセス結果
    private boolean connectResult;

    public boolean isConnecting() {
        return connecting;
    }

    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
    }

    public boolean getConnectResult() {
        return connectResult;
    }

    public void setConnectResult(boolean connectResult) {
        this.connectResult = connectResult;
    }

}
