package com.finance.view.component;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gu zihan on 08/30/2015.
 */
public class DateView extends View {

    // 共通情報
    private ComnInfo comnInfo;
    // ローソク線の情報
    private List<StickInfo> stickList;
    // 横竖屏判断
    private int orientation;
    // 滑动柱体数量
    private int offset;
    // 日付表示の間隔
    private int interval;
    // アクティビティ
    private ChartActivity activity;

    public DateView(Context context) {
        super(context);
        this.setBackgroundColor(Color.BLACK);
        this.setOrientation();
        this.activity = (ChartActivity)context;
    }

    /* 横竖屏设定 */
    private void setOrientation() {
        orientation = this.getResources().getConfiguration().orientation;
    }

    public void setData(ComnInfo comnInfo, List<StickInfo> stickList, int offset) {
        this.comnInfo = comnInfo;
        this.stickList = stickList;
        this.offset = offset;
    }

    static String formatDateForDisplay(String date, boolean intraday) {
        if (intraday && date != null && date.length() >= 16
                && date.charAt(4) == '-' && date.charAt(10) == ' ') {
            return date.substring(5, 7) + date.substring(8, 10) + date.substring(10, 16);
        }
        return date;
    }

    public boolean isDatePickerTap(Point point) {
        int localY = point.y - comnInfo.getStickHeight() - comnInfo.getMacdHeight();
        if (localY < comnInfo.getDateHeight() * 2 / 3
                || localY >= comnInfo.getDateHeight()) {
            return false;
        }
        for (int markerX : getDateMarkerPositions()) {
            if (Math.abs(point.x - markerX) <= 8) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> getDateMarkerPositions() {
        int maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        int dateOffset = offset;
        if (maxStickCount < comnInfo.getMinDispCount()) {
            dateOffset = 0;
        } else {
            dateOffset = Math.min(dateOffset, Const.DEFAULT_OFFSET);
            if (Const.ZOOM_IN == activity.getStickWidth()) {
                dateOffset += 4;
            }
        }
        int interval = Const.ZOOM_OUT == activity.getStickWidth() ? 11 : 22;
        List<Integer> markerPositions = new ArrayList<>();
        for (int i = dateOffset; i < maxStickCount; i += interval) {
            StickInfo stickInfo = stickList.get(i);
            int markerX = stickInfo.getStickRect().centerX();
            if (Const.ZOOM_IN == activity.getStickWidth()) {
                markerX -= (int)(0.5 * Const.ZOOM_IN);
            }
            markerPositions.add(markerX);
        }
        return markerPositions;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4);
        paint.setAntiAlias(true);
        paint.setTextSize(Const.FONT_SIZE);

        // 画面表示最大のローソクの数
        int maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        // 实际K线数量少于最小表示数量，从第一根K线开始表示日期
        if (maxStickCount < comnInfo.getMinDispCount()) {
            offset = 0;
        } else {
            // 偏移超过右侧空白数量，将偏移固定为空白数量，因为超过空白部分的数据没有检索
            if (offset > Const.DEFAULT_OFFSET) {
                offset = Const.DEFAULT_OFFSET;
            }
            if (Const.ZOOM_IN == activity.getStickWidth()) {
                // 缩小后，右侧空白位置不变，则日期表示不全，所以表示向左移动4个柱体的日期
                offset = offset + 4;
            }
        }
        // 日付の間隔を設定
        if (Const.ZOOM_OUT == activity.getStickWidth()) {
            interval = 11;
        } else {
            interval = 22;
        }
        // 画面右側（４）、ローソク間（11）の間隔を取る
        for (int i = offset; i < maxStickCount; i += interval) {
            StickInfo stickInfo = stickList.get(i);
            String date = formatDateForDisplay(stickInfo.getDate(),
                    activity.getIntradayInterval() != null);
            Rect lineRect = new Rect();
            lineRect.right = stickInfo.getStickRect().centerX();
            if (Const.ZOOM_IN == activity.getStickWidth()) {
                // 缩小后，表示位置向左微调整，与放大后表示位置一致
                lineRect.right = lineRect.right - (int)(0.5 * Const.ZOOM_IN);
            }
            lineRect.left = lineRect.right;
            lineRect.top = 0;
            lineRect.bottom = comnInfo.getDateHeight() / 3;
            Point datePoint = new Point();
            datePoint.x = lineRect.right - 92;
            if (activity.getIntradayInterval() != null) {
                datePoint.x = lineRect.right - (int)(paint.measureText(date) / 2) - 4;
            }
            // 竖屏
            if (this.orientation == Configuration.ORIENTATION_PORTRAIT) {
                datePoint.y = lineRect.bottom + 40;
            } else {
                datePoint.y = lineRect.bottom + 30;
            }
            canvas.drawLine(lineRect.right, lineRect.top, lineRect.left,
                    lineRect.bottom, paint);
            canvas.drawText(date, datePoint.x, datePoint.y, paint);
        }
    }
}
