package com.finance.model.net;

import android.os.AsyncTask;

import com.finance.common.LogWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class NetConnectorBmnrHistory extends AsyncTask<Void, Void, String> {
    private static final String URL = "https://www.thetrading.tools/data/csv/BMNR.csv";
    private final NetOperatorYahoo operator;
    private final List<String> yahoo;
    private BmnrHistory.Result history;

    NetConnectorBmnrHistory(NetOperatorYahoo operator, List<String> yahoo) {
        this.operator = operator;
        this.yahoo = new ArrayList<>(yahoo);
    }

    @Override
    protected String doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "text/csv");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("BMNR history HTTP " + status);
            }
            StringBuilder csv = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    csv.append(line).append('\n');
                    if (csv.length() > 2_000_000) {
                        throw new IOException("BMNR history response is too large");
                    }
                }
            }
            history = BmnrHistory.parse(csv.toString(), yahoo);
            if (!history.invalidDates.isEmpty()) {
                LogWriter.getWriter().printLog("BMNR invalid historical candles skipped: "
                        + history.invalidDates);
            }
            return null;
        } catch (IOException e) {
            LogWriter.getWriter().printLog("BMNR historical data request failed: " + e.getMessage());
            return e.getMessage() == null ? "BMNR historical data request failed" : e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected void onPostExecute(String error) {
        if (error == null) {
            List<String> merged = new ArrayList<>(history.records);
            merged.addAll(yahoo);
            operator.updateBmnrHistory(merged);
        } else {
            operator.notifyBmnrBackfillError(yahoo, error);
        }
    }
}
