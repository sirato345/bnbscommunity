package com.finance.model.file;

import android.util.Log;

import com.finance.common.Const;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * ファイル操作：初期銘柄データファイル、銘柄プロパティファイル
 */
public class FileOperator {

    private BufferedReader dataReader;

    // DBテーブルデータ毎に、インスタンスを生成する
    public FileOperator(String fileName) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(fileName + ".csv");
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        try {
            // ファイルのタイトル行を除外
            bufferedReader.readLine();
        } catch (IOException e) {
            Log.e(this.getClass().getName(), Const.MESSAGE_2, e);
            throw new RuntimeException(e);
        }
        this.dataReader = bufferedReader;
    }

    // 該当インスタンスのテーブルから一行のデータを取得する
    public String[] getFileRow() {
        String dataOfLine;
        try {
            dataOfLine = dataReader.readLine();
        } catch (IOException e) {
            Log.e(this.getClass().getName(), Const.MESSAGE_2, e);
            throw new RuntimeException(e);
        }
        return dataOfLine == null ? null : dataOfLine.split(",");
    }

    // 銘柄定義ファイルから銘柄表示名と銘柄コードのセットを取得する
    public static Properties getSymbolMap() {
        Properties properties = new Properties();
        try {
            properties.load(FileOperator.class.getClassLoader().getResourceAsStream(Const.SYMBOL_PROPERTY));
        } catch (IOException e) {
            Log.e(FileOperator.class.getName(), Const.MESSAGE_1, e);
            throw new RuntimeException(e);
        }
        return properties;
    }

    // 銘柄定義ファイルから銘柄表示名のリストを取得する
    public static List<String> getSymbols() {
        List<String> keyList = new ArrayList<>();
        InputStream inputStream = FileOperator.class.getClassLoader().getResourceAsStream(Const.SYMBOL_PROPERTY);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                // 銘柄表示名のみ取得
                keyList.add(line.split("=")[0]);
            }
        } catch (IOException e) {
            Log.e(FileOperator.class.getName(), Const.MESSAGE_1, e);
            throw new RuntimeException(e);
        }
        return keyList;
    }

    // テーブル名称リスト取得
    public static List<String> getTableList() {
        List<String> tableList = new ArrayList<>();
        for (String symbol: getSymbols()) {
            for (String timeFrame : Const.timeFrames) {
                tableList.add(symbol + "_" + timeFrame);
            }
        }
        return tableList;
    }
}
