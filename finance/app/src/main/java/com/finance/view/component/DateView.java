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
    private String touchedDate;
    private int touchedDateX;
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
        return localY >= comnInfo.getDateHeight() / 4
                && localY < comnInfo.getDateHeight();
    }

    public void setTouchedDate(String date, int x) {
        touchedDate = date;
        touchedDateX = x;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (activity.isFearGreedSymbol()) {
            Paint background = new Paint();
            background.setColor(Const.FEAR_GREED_BACKGROUND);
            canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        }
        Paint paint = new Paint();
        paint.setColor(activity.isFearGreedSymbol() ? Color.BLACK : Color.WHITE);
        paint.setStrokeWidth(4);
        paint.setAntiAlias(true);
        paint.setTextSize(Const.FONT_SIZE);

        // 日期刻度固定在画面的5个等分位置，不随K线缩放移动；日期取该位置的K线
        int maxStickCount = Math.min(comnInfo.getMaxStickCount(stickList.size()),
                stickList.size());
        if (maxStickCount == 0) {
            drawTouchedDate(canvas);
            return;
        }
        int tickCount = 5;
        int width = getWidth();
        boolean intraday = activity.getIntradayInterval() != null;
        for (int t = 0; t < tickCount; t++) {
            int tickX = Math.round(width * (2 * t + 1) / (2f * tickCount));
            StickInfo stickInfo = stickList.get(0);
            int bestGap = Integer.MAX_VALUE;
            for (int i = 0; i < maxStickCount; i++) {
                int gap = Math.abs(stickList.get(i).getStickRect().centerX() - tickX);
                if (gap < bestGap) {
                    bestGap = gap;
                    stickInfo = stickList.get(i);
                }
            }
            String date = formatDateForDisplay(stickInfo.getDate(), intraday);
            float textWidth = paint.measureText(date);
            Point datePoint = new Point();
            datePoint.x = Math.round(Math.max(0, Math.min(width - textWidth,
                    tickX - textWidth / 2)));
            // 竖屏
            if (this.orientation == Configuration.ORIENTATION_PORTRAIT) {
                datePoint.y = comnInfo.getDateTickHeight() + 40;
            } else {
                datePoint.y = comnInfo.getDateTickHeight() + 30;
            }
            canvas.drawLine(tickX, 0, tickX, comnInfo.getDateTickHeight(), paint);
            canvas.drawText(date, datePoint.x, datePoint.y, paint);
        }
        drawTouchedDate(canvas);
    }

    private void drawTouchedDate(Canvas canvas) {
        if (touchedDate == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        String date = formatDateForDisplay(touchedDate,
                activity.getIntradayInterval() != null);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(Const.FONT_SIZE);
        textPaint.setFakeBoldText(true);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float horizontalPadding = 8 * density;
        float verticalPadding = 4 * density;
        float labelWidth = textPaint.measureText(date) + 2 * horizontalPadding;
        float labelHeight = fontMetrics.descent - fontMetrics.ascent
                + 2 * verticalPadding;
        float left = Math.max(0, Math.min(getWidth() - labelWidth,
                touchedDateX - labelWidth / 2));
        float top = Math.max(0, (getHeight() - labelHeight) / 2);
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.rgb(255, 193, 7));
        canvas.drawRoundRect(left, top, left + labelWidth, top + labelHeight,
                4 * density, 4 * density, backgroundPaint);
        textPaint.setColor(Color.BLACK);
        canvas.drawText(date, left + horizontalPadding, top + verticalPadding
                - fontMetrics.ascent, textPaint);
    }
}
