package com.finance.view.component;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.ParabolicSarCalculator;
import com.finance.view.component.stick.StickInfo;
import com.finance.view.component.trendline.TrendLine;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StickView extends View {

    private static final int SNAP_DISTANCE = 30;
    private static final int NO_X = Integer.MIN_VALUE;
    private static final long NO_TIME = Long.MIN_VALUE;

    private ChartActivity activity;
    // 共通情報
    private ComnInfo comnInfo;
    // ローソク線の情報
    private List<StickInfo> stickList;
    // 10日移動平均線
    private List<Point> average10;
    // 20日移動平均線
    private List<Point> average20;
    // 50日移動平均線
    private List<Point> average50;
    // Parabolic SAR values aligned with the candle list
    private List<Float> sarValues;
    // 画面表示最大のローソクの数
    private int maxStickCount;
    // 当前10均线价格
    private float current10;
    // 当前20均线价格
    private float current20;
    // 当前50均线价格
    private float current50;
    // 对小数四舍五入
    private final DecimalFormat digitFormat = new DecimalFormat(
            "#.###", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat btcDigitFormat = new DecimalFormat(
            "#0.00", DecimalFormatSymbols.getInstance(Locale.US));
    // タッチ箇所
    private Point touchPoint;
    // 編集モードの押下ポイント
    private Point downPoint;
    // 編集モードの当面ポイント
    private Point currentPoint;
    // タッチ箇所の日付
    private String touchedDate;
    // タッチ位置の価格
    private Float currentValue;
    // 编辑中趋势线
    private TrendLine editLine;
    private Bitmap loupeBitmap;
    private Canvas loupeCanvas;
    // 正在调整宽度的通道线及当前偏移（像素）
    private TrendLine channelLine;
    private float channelOffsetPx;
    private boolean channelMove;
    private boolean tapOnLine;
    // 已有线附近此范围内的点击不新画线，双击删除也用此范围
    private static final int NEAR_LINE_PX = 100;
    // 点击时手指的自然抖动范围，小于此距离不当作拖动
    private static final int TAP_SLOP_PX = 30;
    private float channelStartOffsetPx;
    private static final float[] FIB_LEVELS = {0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f};
    private static final float FNG_AXIS_MAX = 100f;
    private static final float FNG_AXIS_MIN = 0f;
    private List<TrendLine> trendLines = new ArrayList<>();

    public StickView(Context context) {
        super(context);
        // 背景色
        super.setBackgroundColor(Color.BLACK);
        // 画面対象
        this.activity = (ChartActivity)context;
    }

    /* ローソク足のデータを設定し、自動計算させる */
    public void setData(ComnInfo comnInfo, List<StickInfo> stickList, List<TrendLine> trendLines) {
        this.comnInfo = comnInfo;
        this.stickList = stickList;
        this.trendLines = trendLines;
        this.average10 = new ArrayList<>();
        this.average20 = new ArrayList<>();
        this.average50 = new ArrayList<>();
        this.current10 = 0;
        this.current20 = 0;
        this.current50 = 0;
        this.calculate(this.stickList);
        this.calAverageLine(this.stickList, average10, 10);
        this.calAverageLine(this.stickList, average20, 20);
        this.calAverageLine(this.stickList, average50, 50);
        this.calculateSar(this.stickList);
        this.calTDtdSequential();
    }

    private void calculateSar(List<StickInfo> sticks) {
        sarValues = ParabolicSarCalculator.calculate(sticks);
    }

    /* ローソク足の自身情報を計算 */
    public void calculate(List<StickInfo> stickList) {
        // 最大値
        float maxValue = 0.0f;
        // 最小値
        float minValue = 0.0f;
        // 画面表示最大のローソクの数
        this.maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        // 画面表示最大値と最小値を計算、表示可能なローソクのみを統計
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            float low = stickInfo.getLow();
            float high = stickInfo.getHigh();
            if (minValue == 0) {
                minValue = low;
            } else {
                minValue = minValue <= low ? minValue : low;
            }
            if (maxValue == 0) {
                maxValue = high;
            } else {
                maxValue = maxValue >= high ? maxValue : high;
            }
        }
        // 贪婪恐慌指数固定为0-100的纵轴，上下各留一点空白
        if (activity.isFearGreedSymbol()) {
            maxValue = FNG_AXIS_MAX;
            minValue = FNG_AXIS_MIN;
        }
        // ローソク足に計算値を設定し、ローソク自身情報も計算
        for (int i = 0; i < maxStickCount; i++) {
            // ローソク足
            StickInfo stickInfo = stickList.get(i);
            // 表示番号設定（左から計算）
            stickInfo.setIndex(maxStickCount - i);
            // 最大上限値
            stickInfo.setMaxValue(maxValue);
            // 最小下限値
            stickInfo.setMinValue(minValue);
            // 最大値ローソク足のフラグを設定
            if (stickInfo.getHigh() == maxValue) {
                stickInfo.setIsMaxValueStick(true);
            }
            // 最小値ローソク足のフラグを設定
            if (stickInfo.getLow() == minValue) {
                stickInfo.setIsMinValueStick(true);
            }
            // ローソク足自身情報を計算
            stickInfo.calculate();
        }
    }

    /* 移動平均線計算 */
    public void calAverageLine(List<StickInfo> stickList, List<Point> averageList, int average) {
        int size = stickList.size() - average;
        StickInfo stickInfo;
        float averageValue;
        Point point = null;
        for (int i = 0; i < size; i++) {
            float averageClose = 0.0f;
            for (int j = i; j < i + average; j++) {
                point = new Point();
                stickInfo = stickList.get(j);
                averageClose = averageClose + stickInfo.getClose();
            }
            stickInfo = stickList.get(i);
            averageValue = averageClose / average;
            if (i == 0) {
                if (average == 10) {
                    current10 = averageValue;
                } else if (average == 20) {
                    current20 = averageValue;
                } else if (average == 50) {
                    current50 = averageValue;
                }else {
                }
            }
            point.y = stickInfo.getYPoint(averageValue);
            point.x = stickInfo.getLineRect().centerX();
            averageList.add(point);
        }
    }

    /* TD序列 */
    private void calTDtdSequential() {
        int size = stickList.size() - 4 > 60 ? 60 : stickList.size() - 4;
        for (int i = 0; i < size; i ++) {
            int up = 0;
            int down = 0;
            for (int j = i; j < i + 9; j ++) {
                if (stickList.size() < j+5) {
                    break;
                }
                if (stickList.get(j).getClose() > stickList.get(j+4).getClose()) {
                    up ++;
                } else {
                    break;
                }
            }
            for (int j = i; j < i + 9; j ++) {
                if (stickList.size() < j+5) {
                    break;
                }
                if (stickList.get(j).getClose() < stickList.get(j+4).getClose()) {
                    down ++;
                } else {
                    break;
                }
            }
            if (up >= 8) {
                stickList.get(i).setTdSequentialUp(up);
            }
            if (down >= 8) {
                stickList.get(i).setTdSequentialDown(down);
            }
        }
    }

    public void onTouchEvent(int action, Point point) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                this.editLine = this.getEditLine(point);
                this.channelLine = editLine == null ? this.getChannelLine(point) : null;
                this.channelOffsetPx = channelLine == null ? 0 : this.getChannelOffsetPx(channelLine);
                this.channelStartOffsetPx = channelOffsetPx;
                this.channelMove = channelLine != null && this.isOnChannelBase(channelLine, point);
                this.currentPoint = null;
                this.tapOnLine = editLine == null && channelLine == null
                        && this.isNearAnyLine(point);
                this.downPoint = editLine == null && channelLine == null
                        ? this.snap(point) : point;
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.channelLine != null) {
                    if (!channelMove) {
                        // 以按下时的间距为起点按位移调整，避免线跳到手指位置
                        boolean moved = this.getDistance(downPoint, point) >= TAP_SLOP_PX;
                        this.channelOffsetPx = moved
                                ? channelStartOffsetPx + (point.y - downPoint.y)
                                : channelStartOffsetPx;
                    }
                    this.currentPoint = point;
                    break;
                }
                if (this.editLine != null && editLine.getType() != TrendLine.TYPE_HORIZONTAL) {
                    Point fixed = editLine.getEidtPoint() == 1
                            ? this.getVirtualPoint(editLine.getDate2(), editLine.getPrice2())
                            : this.getVirtualPoint(editLine.getDate1(), editLine.getPrice1());
                    if (fixed != null) {
                        this.downPoint = fixed;
                    }
                }
                this.currentPoint = this.snap(point);
                break;
            case MotionEvent.ACTION_UP:
                if (channelLine != null) {
                    if (currentPoint != null) {
                        if (channelMove) {
                            this.saveChannelMove();
                        } else {
                            this.saveChannelOffset();
                        }
                    }
                    channelMove = false;
                    channelLine = null;
                    downPoint = null;
                    currentPoint = null;
                    editLine = null;
                    break;
                }
                if (downPoint != null && editLine == null && !tapOnLine
                        && activity.getTrendLineType() == TrendLine.TYPE_HORIZONTAL) {
                    // 水平线：点击或拖动后松手，画在手指停下的位置
                    if (currentPoint == null) {
                        currentPoint = downPoint;
                    }
                    this.saveTrendLine();
                } else if (downPoint != null && currentPoint != null) {
                    int length = this.getDistance(downPoint, currentPoint);
                    // 低于一定长度,不画线（编辑时保留原线；水平线只需上下移动）
                    if (length > 120 || (editLine != null
                            && editLine.getType() == TrendLine.TYPE_HORIZONTAL)) {
                        if (editLine != null) {
                            activity.deleteTrendLine(editLine.getId());
                        }
                        this.saveTrendLine();
                    }
                }
                downPoint = null;
                currentPoint = null;
                editLine = null;
                break;
        }
    }

    private boolean isNearAnyLine(Point point) {
        for (TrendLine trendLine : trendLines) {
            if (this.getLineDistance(trendLine, point) <= NEAR_LINE_PX) {
                return true;
            }
        }
        return false;
    }

    /* 双击：删除线 */
    public void onDoubleTap(Point point) {
        TrendLine nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TrendLine trendLine : trendLines) {
            double distance = this.getLineDistance(trendLine, point);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = trendLine;
            }
        }
        if (nearest != null && nearestDistance <= NEAR_LINE_PX) {
            int index = this.getTrendLineIndex(nearest);
            if (index != -1) {
                trendLines.remove(index);
                activity.deleteTrendLine(nearest.getId());
            }
        }
    }

    // 删除线ID取得
    private int getTrendLineIndex(TrendLine editLine) {
        for (int i = 0; i < trendLines.size(); i++) {
            if (trendLines.get(i).getId() == editLine.getId()) {
                return i;
            }
        }
        return -1;
    }

    /* 触摸点到线的距离：通道线含平行线，斐波那契线取各水平线 */
    private double getLineDistance(TrendLine trendLine, Point point) {
        Point p1 = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
        Point p2 = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
        if (p1 == null || p2 == null) {
            return Double.MAX_VALUE;
        }
        if (trendLine.getType() == TrendLine.TYPE_FIBONACCI) {
            double best = Double.MAX_VALUE;
            for (float level : FIB_LEVELS) {
                best = Math.min(best, Math.abs(point.y - this.getFibY(p1, p2, level)));
            }
            return best;
        }
        double distance = this.pointToLine(p1.x, p1.y, p2.x, p2.y, point.x, point.y);
        if (trendLine.getType() == TrendLine.TYPE_CHANNEL) {
            int offsetPx = Math.round(this.getChannelOffsetPx(trendLine));
            distance = Math.min(distance, this.pointToLine(p1.x, p1.y + offsetPx,
                    p2.x, p2.y + offsetPx, point.x, point.y));
        } else {
            float[] ends = this.getLineEnds(p1.x, p1.y, p2.x, p2.y, trendLine.getType());
            distance = this.pointToLine((int) ends[0], (int) ends[1],
                    (int) ends[2], (int) ends[3], point.x, point.y);
        }
        return distance;
    }

    private float getFibY(Point p1, Point p2, float level) {
        return p2.y + (p1.y - p2.y) * level;
    }

    /* 通道线平行线相对基准线的像素偏移 */
    private float getChannelOffsetPx(TrendLine trendLine) {
        if (stickList == null || stickList.isEmpty()) {
            return 0;
        }
        StickInfo latest = stickList.get(0);
        return latest.getYPoint(trendLine.getPrice1() + trendLine.getOffset())
                - latest.getYPoint(trendLine.getPrice1());
    }

    /* 触摸点附近的通道线（基准线或平行线），用于拖动调整宽度 */
    private TrendLine getChannelLine(Point point) {
        TrendLine best = null;
        double bestDistance = Const.EDIT_DISTANCE;
        for (TrendLine trendLine : trendLines) {
            if (trendLine.getType() != TrendLine.TYPE_CHANNEL) {
                continue;
            }
            double distance = this.getLineDistance(trendLine, point);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = trendLine;
            }
        }
        return best;
    }

    /* 手指所在位置相对基准线的垂直偏移 */
    private float getChannelDragOffsetPx(TrendLine trendLine, Point point) {
        Point p1 = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
        Point p2 = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
        if (p1 == null || p2 == null) {
            return 0;
        }
        float baseY = p2.x == p1.x ? p1.y
                : p1.y + (float) (p2.y - p1.y) * (point.x - p1.x) / (p2.x - p1.x);
        return point.y - baseY;
    }

    /* 已有平行线时，按在基准线上才整体移动；尚无平行线时拖动用于拉出平行线 */
    private boolean isOnChannelBase(TrendLine trendLine, Point point) {
        float offsetPx = this.getChannelOffsetPx(trendLine);
        if (Math.abs(offsetPx) < 10) {
            return false;
        }
        Point p1 = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
        Point p2 = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
        if (p1 == null || p2 == null) {
            return false;
        }
        double base = this.pointToLine(p1.x, p1.y, p2.x, p2.y, point.x, point.y);
        double parallel = this.pointToLine(p1.x, p1.y + Math.round(offsetPx),
                p2.x, p2.y + Math.round(offsetPx), point.x, point.y);
        return base <= parallel;
    }

    /* 整体平移通道线（基准线和平行线一起） */
    private void saveChannelMove() {
        Point p1 = this.getVirtualPoint(channelLine.getDate1(), channelLine.getPrice1());
        Point p2 = this.getVirtualPoint(channelLine.getDate2(), channelLine.getPrice2());
        if (p1 == null || p2 == null || downPoint == null) {
            return;
        }
        int dx = currentPoint.x - downPoint.x;
        int dy = currentPoint.y - downPoint.y;
        if (this.getDistance(downPoint, currentPoint) < TAP_SLOP_PX) {
            return;
        }
        Point n1 = new Point(p1.x + dx, p1.y + dy);
        Point n2 = new Point(p2.x + dx, p2.y + dy);
        TrendLine trendLine = new TrendLine();
        trendLine.setSymbol(channelLine.getSymbol());
        trendLine.setTimeFrame(channelLine.getTimeFrame());
        trendLine.setType(channelLine.getType());
        trendLine.setColor(channelLine.getColor());
        trendLine.setDate1(this.getDate(n1));
        trendLine.setPrice1(this.getPrice(n1));
        trendLine.setDate2(this.getDate(n2));
        trendLine.setPrice2(this.getPrice(n2));
        trendLine.setOffset(channelLine.getOffset());
        activity.deleteTrendLine(channelLine.getId());
        activity.saveTrendLine(trendLine);
    }

    private void saveChannelOffset() {
        Point p1 = this.getVirtualPoint(channelLine.getDate1(), channelLine.getPrice1());
        if (p1 == null || Math.abs(channelOffsetPx) < 10
                || (downPoint != null && currentPoint != null
                && this.getDistance(downPoint, currentPoint) < TAP_SLOP_PX)) {
            return;
        }
        TrendLine trendLine = new TrendLine();
        trendLine.setSymbol(channelLine.getSymbol());
        trendLine.setTimeFrame(channelLine.getTimeFrame());
        trendLine.setType(channelLine.getType());
        trendLine.setColor(channelLine.getColor());
        trendLine.setDate1(channelLine.getDate1());
        trendLine.setPrice1(channelLine.getPrice1());
        trendLine.setDate2(channelLine.getDate2());
        trendLine.setPrice2(channelLine.getPrice2());
        trendLine.setOffset(this.getPrice(new Point(0, Math.round(p1.y + channelOffsetPx)))
                - channelLine.getPrice1());
        activity.deleteTrendLine(channelLine.getId());
        activity.saveTrendLine(trendLine);
    }

    // 计算点之间的距离
    private int getDistance(Point startPoint, Point endPoint) {
        return (int) Math.sqrt(Math.pow(startPoint.x - endPoint.x, 2) +
                                Math.pow(startPoint.y - endPoint.y, 2));
    }

    // 计算触摸位置是否有可编辑的趋势线（取距离最近的端点）
    private TrendLine getEditLine(Point point) {
        TrendLine best = null;
        int bestPoint = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (TrendLine trendLine : trendLines) {
            Point startPoint = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
            Point endPoint = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
            if (trendLine.getType() == TrendLine.TYPE_HORIZONTAL && startPoint != null) {
                // 水平线横跨整个画面，线上任意位置都可抓取
                int distance = Math.abs(point.y - startPoint.y);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = trendLine;
                    bestPoint = 1;
                }
                continue;
            }
            if (startPoint != null) {
                int distance = this.getDistance(point, startPoint);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = trendLine;
                    bestPoint = 1;
                }
            }
            if (endPoint != null) {
                int distance = this.getDistance(point, endPoint);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = trendLine;
                    bestPoint = 2;
                }
            }
        }
        if (best == null || bestDistance > Const.EDIT_DISTANCE) {
            return null;
        }
        best.setEidtPoint(bestPoint);
        return best;
    }

    /* 吸附到触摸位置K线的开高低收价 */
    private Point snap(Point point) {
        boolean horizontal = editLine != null
                ? editLine.getType() == TrendLine.TYPE_HORIZONTAL
                : activity.getTrendLineType() == TrendLine.TYPE_HORIZONTAL;
        // 水平线需要设置到任意价格，不吸附到K线价格
        if (horizontal || !activity.isTrendSnapEnabled()
                || stickList == null || stickList.isEmpty()) {
            return point;
        }
        StickInfo stick = this.getRelatedStick(point.x);
        if (stick == null) {
            return point;
        }
        float[] prices = {stick.getOpen(), stick.getHigh(), stick.getLow(), stick.getClose()};
        int bestY = point.y;
        int bestGap = SNAP_DISTANCE + 1;
        for (float price : prices) {
            int y = stick.getYPoint(price);
            int gap = Math.abs(y - point.y);
            if (gap < bestGap) {
                bestGap = gap;
                bestY = y;
            }
        }
        if (bestGap > SNAP_DISTANCE) {
            return point;
        }
        return new Point(stick.getLineRect().centerX(), bestY);
    }

    /* 线的实际绘制端点：射线向第二点方向延伸，水平线横跨整个画面 */
    private float[] getLineEnds(TrendLine trendLine) {
        Point p1 = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
        Point p2 = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
        if (p1 == null || p2 == null) {
            return null;
        }
        return this.getLineEnds(p1.x, p1.y, p2.x, p2.y, trendLine.getType());
    }

    private float[] getLineEnds(float x1, float y1, float x2, float y2, int type) {
        if (type == TrendLine.TYPE_HORIZONTAL) {
            return new float[] {0, y1, comnInfo.getScreenWidth(), y1};
        }
        if (type == TrendLine.TYPE_RAY) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1) {
                return new float[] {x1, y1, x2, y2};
            }
            float k = 3f * (comnInfo.getScreenWidth() + comnInfo.getStickHeight()) / length;
            return new float[] {x1, y1, x1 + dx * k, y1 + dy * k};
        }
        return new float[] {x1, y1, x2, y2};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        this.drawContent(canvas);
        this.drawLoupe(canvas);
    }

    /* 拖动画线端点时，在手指上方显示放大镜，便于对准 */
    private void drawLoupe(Canvas canvas) {
        if (currentPoint == null || channelLine != null || downPoint == null
                || stickList == null || stickList.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float radius = 60 * density;
        float gap = 40 * density;
        float scale = 2.5f;
        float fx = currentPoint.x;
        float fy = currentPoint.y;
        float cx = Math.max(radius, Math.min(fx, comnInfo.getScreenWidth() - radius));
        float cy = fy - radius - gap;
        if (cy < radius) {
            cy = fy + radius + gap;
        }
        // 放大镜必须完整留在画面内，不被K线区域的上下边沿截断
        if (getHeight() > 2 * radius) {
            cy = Math.max(radius, Math.min(cy, getHeight() - radius));
        }
        // 先把手指周围的内容画到独立位图，不受K线区域边沿裁剪，再贴进圆形放大镜
        int size = Math.max(1, Math.round(radius * 2));
        if (loupeBitmap == null || loupeBitmap.getWidth() != size) {
            loupeBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            loupeCanvas = new Canvas(loupeBitmap);
        }
        loupeBitmap.eraseColor(Color.BLACK);
        loupeCanvas.save();
        loupeCanvas.translate(radius, radius);
        loupeCanvas.scale(scale, scale);
        loupeCanvas.translate(-fx, -fy);
        this.drawContent(loupeCanvas);
        loupeCanvas.restore();
        canvas.save();
        Path circle = new Path();
        circle.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(circle);
        canvas.drawBitmap(loupeBitmap, cx - radius, cy - radius, null);
        canvas.restore();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStrokeWidth(1);
        paint.setColor(Color.RED);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
    }

    private void drawContent(Canvas canvas) {
        if (activity.isFearGreedSymbol()) {
            this.drawFearGreedBands(canvas);
            this.drawFearGreedLine(canvas);
            this.drawTouchedLine(canvas);
            this.drawTrandLine(canvas);
            return;
        }
        this.drawStick(canvas);
        if(activity.isAvg10Disp) {
            this.drawAverageLine(canvas, average10, Color.parseColor("#FFFF00"));
        }
        if(activity.isAvg20Disp) {
            this.drawAverageLine(canvas, average20, Color.parseColor("#FFA500"));
        }
        if(activity.isAvg50Disp) {
            this.drawAverageLine(canvas, average50, Color.parseColor("#00FFFF"));
        }
        if (activity.isSarDisp) {
            this.drawSar(canvas);
        }
        this.drawText(canvas);
        this.drawTouchedLine(canvas);
        this.drawTrandLine(canvas);
        this.drawTDtdSequential(canvas);
    }

    /* 贪婪恐慌指数：黑底，80以上淡绿、20以下淡红，上下边界（100/0）各画一条白线 */
    private void drawFearGreedBands(Canvas canvas) {
        if (stickList == null || stickList.isEmpty()) {
            return;
        }
        Paint background = new Paint();
        background.setColor(Const.FEAR_GREED_BACKGROUND);
        canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        StickInfo latest = stickList.get(0);
        int width = comnInfo.getScreenWidth();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1100C07A"));
        canvas.drawRect(0, latest.getYPoint(100), width, latest.getYPoint(80), paint);
        paint.setColor(Color.parseColor("#11FF3B3B"));
        canvas.drawRect(0, latest.getYPoint(20), width, latest.getYPoint(0), paint);
        paint.setColor(Color.DKGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(0, latest.getYPoint(100), width, latest.getYPoint(100), paint);
        canvas.drawLine(0, latest.getYPoint(0), width, latest.getYPoint(0), paint);
        Paint dash = new Paint(paint);
        dash.setStrokeWidth(2);
        dash.setStyle(Paint.Style.STROKE);
        dash.setPathEffect(new DashPathEffect(new float[] {10, 8}, 0));
        for (int v = 20; v <= 80; v += 20) {
            Path path = new Path();
            path.moveTo(0, latest.getYPoint(v));
            path.lineTo(width, latest.getYPoint(v));
            canvas.drawPath(path, dash);
        }
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setTextAlign(Paint.Align.RIGHT);
        float margin = 4 * getResources().getDisplayMetrics().density;
        for (int v = 0; v <= 100; v += 20) {
            float y = latest.getYPoint(v);
            // 0在分界线上方，其余在分界线下方
            float textY = v == 0 ? y - margin : y + paint.getTextSize();
            canvas.drawText(String.valueOf(v), width - margin, textY, paint);
        }
    }

    private int getFearGreedColor(float value) {
        if (value >= 80) {
            return Color.parseColor("#16C784");
        } else if (value >= 60) {
            return Color.parseColor("#7CFF00");
        } else if (value >= 40) {
            return Color.parseColor("#FFE600");
        } else if (value >= 20) {
            return Color.parseColor("#FF9500");
        }
        return Color.parseColor("#EA3943");
    }

    /* 贪婪恐慌指数按数值区间分色的折线 */
    private void drawFearGreedLine(Canvas canvas) {
        if (stickList == null || stickList.isEmpty()) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i + 1 < maxStickCount; i++) {
            StickInfo from = stickList.get(i);
            StickInfo to = stickList.get(i + 1);
            float v1 = from.getClose();
            float v2 = to.getClose();
            float x1 = from.getLineRect().left;
            float x2 = to.getLineRect().left;
            // 线段跨过20/40/60/80时在分界线处切开，各段按所在区间着色
            List<Float> cuts = new ArrayList<>();
            cuts.add(0f);
            for (int level = 20; level <= 80; level += 20) {
                if ((v1 - level) * (v2 - level) < 0) {
                    cuts.add((level - v1) / (v2 - v1));
                }
            }
            cuts.add(1f);
            Collections.sort(cuts);
            for (int c = 0; c + 1 < cuts.size(); c++) {
                float t1 = cuts.get(c);
                float t2 = cuts.get(c + 1);
                float va = v1 + (v2 - v1) * t1;
                float vb = v1 + (v2 - v1) * t2;
                paint.setColor(getFearGreedColor((va + vb) / 2));
                canvas.drawLine(x1 + (x2 - x1) * t1, from.getYPoint(va),
                        x1 + (x2 - x1) * t2, from.getYPoint(vb), paint);
            }
        }
    }

    private void drawSar(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float radius = 3 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < maxStickCount && i < sarValues.size(); i++) {
            float sar = sarValues.get(i);
            if (Float.isNaN(sar)) {
                continue;
            }
            StickInfo stick = stickList.get(i);
            paint.setColor(sar < stick.getClose()
                    ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));
            canvas.drawCircle(stick.getLineRect().centerX(), stick.getYPoint(sar),
                    radius, paint);
        }
    }

    /* ローソク足を描く */
    private void drawStick(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            // ローソク足を描く
            Rect stickReck = stickInfo.getStickRect();
            paint.setColor(stickInfo.getColor());
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(stickReck, paint);
            // ローソク足の線を描く
            Rect lineRect = stickInfo.getLineRect();
            paint.setStrokeWidth(3);
            paint.setTextSize(Const.FONT_SIZE);
            canvas.drawLine(lineRect.left, lineRect.top, lineRect.right, lineRect.bottom, paint);
        }
    }

    /* 移動平均線を描く */
    private void drawAverageLine(Canvas canvas, List<Point> averageList, int color) {
        Path path = new Path();
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        // 計算した移動平均線のポイントの数が表示可能最大ローソク数により少ない場合
        int pointCount = maxStickCount > averageList.size() ? averageList.size() : maxStickCount;
        // ＊K線の数が移動平均線の計算最小限を下回る場合、描かないとする＊
        if (pointCount < 3) {
            // 可表示的点小于三个时，不画线
            return;
        }
        for (int i = 0; i < pointCount - 2; i += 2) {
            Point start = averageList.get(i);
            Point control = averageList.get(i + 1);
            Point next = averageList.get(i + 2);
            path.moveTo(start.x, start.y);
            path.quadTo(control.x, control.y, next.x, next.y);
        }
        Point lastPoint = averageList.get(pointCount - 1);
        path.lineTo(lastPoint.x, lastPoint.y);
        canvas.drawPath(path, paint);
    }

    /* 最高と最低、現在価格の水平線と価格を描く */
    private void drawText(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setStrokeWidth(2);
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            Rect stickReck = stickInfo.getStickRect();
            Rect lineRect = stickInfo.getLineRect();
            // 最新の移動平均価格
            if (i == 0) {
                // 文字粗体
                paint.setFakeBoldText(true);
                // 10平均線の色
                paint.setColor(Color.parseColor("#FFFF00"));
                if (current10 != 0) {
                    // 10平均線設定
                    canvas.drawText("(10) " + getLimitDigit(current10), 0, Const.FONT_SIZE_MIDDLE * 2, paint);
                }
                // 20平均線の色
                paint.setColor(Color.parseColor("#FF9900"));
                if (current20 != 0) {
                    // 20平均線設定
                    canvas.drawText("(20) " + getLimitDigit(current20), 0, Const.FONT_SIZE_MIDDLE * 3, paint);
                }
                // 50平均線の色
                paint.setColor(Color.parseColor("#00FFFF"));
                if (current50 != 0) {
                    // 50平均線設定
                    canvas.drawText("(50) " + getLimitDigit(current50), 0, Const.FONT_SIZE_MIDDLE * 4, paint);
                }
                if (activity.isSarDisp && !sarValues.isEmpty()
                        && !Float.isNaN(sarValues.get(0))) {
                    paint.setColor(sarValues.get(0) < stickInfo.getClose()
                            ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));
                    canvas.drawText("(SAR) " + getLimitDigit(sarValues.get(0)), 0,
                            Const.FONT_SIZE_MIDDLE * 5, paint);
                }
            }
            // 最大値の線
            if (stickInfo.isMaxValueStick()) {
                // 白色
                paint.setColor(Color.WHITE);
                // 不设置粗体
                paint.setFakeBoldText(false);
                // 最大値の線
                canvas.drawLine(0, lineRect.top, comnInfo.getScreenWidth(), lineRect.top, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 最新価格設定
                canvas.drawText(getLimitDigit(stickInfo.getMaxValue()), 0, lineRect.top + Const.FONT_SIZE_MIDDLE, paint);
            }
            // 最小値の線
            if (stickInfo.isMinValueStick()) {
                // 白色
                paint.setColor(Color.WHITE);
                // 不设置粗体
                paint.setFakeBoldText(false);
                // 最小値の線
                canvas.drawLine(0, lineRect.bottom, comnInfo.getScreenWidth(), lineRect.bottom, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 最新価格設定
                canvas.drawText(getLimitDigit(stickInfo.getMinValue()), 0, lineRect.bottom - 8, paint);
            }
        }
    }

    /* タッチ箇所の価格と日付を描く */
    private void drawTouchedLine(Canvas canvas) {
        if (touchPoint == null) {
            return;
        }
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setColor(activity.isFearGreedSymbol() ? Color.BLACK : Color.WHITE);
        if (touchPoint.y <= comnInfo.getStickHeight()) {
            if (currentValue != null) {
                // 价格线
                canvas.drawLine(getDigitLength(currentValue),
                        touchPoint.y, comnInfo.getScreenWidth(), touchPoint.y, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 文字坐标
                int y;
                // 判定文字是否超出屏幕显示
                if (touchPoint.y - Const.FONT_SIZE_HALF < 0) {
                    y = touchPoint.y + Const.FONT_SIZE_MIDDLE - 10;
                } else if (touchPoint.y + Const.FONT_SIZE_HALF > comnInfo.getStickHeight()) {
                    y = touchPoint.y;
                } else {
                    y = touchPoint.y + Const.FONT_SIZE_HALF - 5;
                }
                // 最新価格設定
                canvas.drawText(getLimitDigit(currentValue), 0, y, paint);
            } else {
                canvas.drawLine(0, touchPoint.y, comnInfo.getScreenWidth(), touchPoint.y, paint);
            }

        }
        if (touchedDate != null) {
            // 日期线
            canvas.drawLine(touchPoint.x, 0, touchPoint.x, comnInfo.getStickHeight(), paint);
        }
    }

    // 保存趋势线（编辑时保留未被拖动端点的原日期和价格，以及线型和颜色）
    private void saveTrendLine() {
        TrendLine trendLine = new TrendLine();
        trendLine.setSymbol(activity.getSymbol());
        trendLine.setTimeFrame(activity.getTrendLineTimeFrame());
        String date1;
        float price1;
        String date2;
        float price2;
        if (editLine == null) {
            trendLine.setType(activity.getTrendLineType());
            trendLine.setColor(activity.getTrendLineColor());
            date1 = this.getDate(downPoint);
            price1 = this.getPrice(downPoint);
            date2 = this.getDate(currentPoint);
            price2 = this.getPrice(currentPoint);
        } else {
            trendLine.setType(editLine.getType());
            trendLine.setColor(editLine.getColor());
            if (editLine.getEidtPoint() == 1) {
                date1 = this.getDate(currentPoint);
                price1 = this.getPrice(currentPoint);
                date2 = editLine.getDate2();
                price2 = editLine.getPrice2();
            } else {
                date1 = editLine.getDate1();
                price1 = editLine.getPrice1();
                date2 = this.getDate(currentPoint);
                price2 = this.getPrice(currentPoint);
            }
        }
        if (trendLine.getType() == TrendLine.TYPE_HORIZONTAL) {
            float price = this.getPrice(currentPoint);
            price1 = price;
            price2 = price;
        }
        trendLine.setDate1(date1);
        trendLine.setPrice1(price1);
        trendLine.setDate2(date2);
        trendLine.setPrice2(price2);
        if (trendLine.getType() == TrendLine.TYPE_CHANNEL) {
            // 新通道线先只画基准线，平行线之后从线中间拖出
            trendLine.setOffset(editLine != null ? editLine.getOffset() : 0f);
        }
        activity.saveTrendLine(trendLine);
    }

    private String getDate(Point point) {
        // 画面表示最大のローソクの数
        int maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stick = stickList.get(i);
            Rect stickRect = stick.getStickRect();
            if (point.x >= stickRect.left && point.x <=
                    (stickRect.right + StickInfo.BETWEEN_SPACE)) {
                return stick.getDate();
            }
        }
        return extrapolateDate(point.x, maxStickCount);
    }

    /* 画面外（右侧空白、左侧屏幕外）的X坐标换算成日期，与getX互为逆运算 */
    private String extrapolateDate(int x, int maxStickCount) {
        int visible = Math.min(maxStickCount, stickList.size());
        StickInfo newest = stickList.get(0);
        if (visible < 2) {
            return newest.getDate();
        }
        int x0 = newest.getLineRect().centerX();
        int xN = stickList.get(visible - 1).getLineRect().centerX();
        if (x0 == xN) {
            return newest.getDate();
        }
        float index = (x0 - x) * (float) (visible - 1) / (x0 - xN);
        int rounded = Math.round(index);
        if (rounded >= 0 && rounded < stickList.size()) {
            return stickList.get(rounded).getDate();
        }
        long t0 = parseTime(newest.getDate());
        long tN = parseTime(stickList.get(visible - 1).getDate());
        if (t0 == NO_TIME || tN == NO_TIME || t0 == tN) {
            return newest.getDate();
        }
        long t = Math.round(t0 - index * (double) (t0 - tN) / (visible - 1));
        boolean daily = newest.getDate().length() == 8;
        SimpleDateFormat format = new SimpleDateFormat(
                daily ? "yyyyMMdd" : "yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date(t));
    }

    private float getPrice(Point point) {
        StickInfo stick = stickList.get(0);
        return stick.getYValue(point.y);
    }

    /* 編集モードの線を描く */
    private void drawTrandLine(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3);
        // 描画已经保存的线（超出画面的部分由画布裁剪，线的角度保持不变）
        for (TrendLine trendLine : trendLines) {
            boolean dragging = currentPoint != null && (editLine != null
                    ? editLine.getId() == trendLine.getId()
                    : channelLine != null && channelLine.getId() == trendLine.getId());
            if (dragging && editLine != null) {
                continue;
            }
            Point p1 = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
            Point p2 = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
            if (p1 == null || p2 == null) {
                continue;
            }
            float offsetPx = dragging && !channelMove
                    ? channelOffsetPx : this.getChannelOffsetPx(trendLine);
            if (dragging && channelMove && downPoint != null) {
                int dx = currentPoint.x - downPoint.x;
                int dy = currentPoint.y - downPoint.y;
                p1 = new Point(p1.x + dx, p1.y + dy);
                p2 = new Point(p2.x + dx, p2.y + dy);
            }
            paint.setColor(trendLine.getColor());
            this.drawLineShape(canvas, paint, p1.x, p1.y, p2.x, p2.y,
                    trendLine.getType(), offsetPx,
                    dragging && channelMove ? null : Float.valueOf(trendLine.getPrice1()));
        }
        // 描画当前正在画的线
        if (downPoint == null || currentPoint == null || channelLine != null) {
            return;
        }
        int type = editLine != null ? editLine.getType() : activity.getTrendLineType();
        paint.setColor(editLine != null ? editLine.getColor() : activity.getTrendLineColor());
        float y1 = type == TrendLine.TYPE_HORIZONTAL ? currentPoint.y : downPoint.y;
        float y2 = type == TrendLine.TYPE_HORIZONTAL ? y1 : currentPoint.y;
        float offsetPx = editLine != null ? this.getChannelOffsetPx(editLine) : 0;
        if (editLine != null && type == TrendLine.TYPE_RAY && editLine.getEidtPoint() == 1) {
            // 编辑射线起点：起点跟随手指，方向仍指向固定的第二点
            this.drawLineShape(canvas, paint, currentPoint.x, y2, downPoint.x, y1, type, offsetPx);
            return;
        }
        this.drawLineShape(canvas, paint, downPoint.x, y1, currentPoint.x, y2, type, offsetPx);
    }

    /* 按线型绘制：通道线带平行线，斐波那契线为水平回撤线（0在第二点，1在第一点） */
    private void drawLineShape(Canvas canvas, Paint paint, float x1, float y1,
                               float x2, float y2, int type, float offsetPx) {
        this.drawLineShape(canvas, paint, x1, y1, x2, y2, type, offsetPx, null);
    }

    private void drawLineShape(Canvas canvas, Paint paint, float x1, float y1,
                               float x2, float y2, int type, float offsetPx, Float fixedPrice) {
        if (type == TrendLine.TYPE_FIBONACCI) {
            this.drawFibonacci(canvas, paint, x1, y1, x2, y2);
            return;
        }
        float[] ends = this.getLineEnds(x1, y1, x2, y2, type);
        canvas.drawLine(ends[0], ends[1], ends[2], ends[3], paint);
        if (type == TrendLine.TYPE_HORIZONTAL) {
            this.drawHorizontalPrice(canvas, paint, y1, fixedPrice);
        }
        if (type == TrendLine.TYPE_CHANNEL) {
            canvas.drawLine(x1, y1 + offsetPx, x2, y2 + offsetPx, paint);
        }
    }

    /* 水平线的价格：显示在线上方，上方放不下（超出上边沿）时显示在线下方 */
    private void drawHorizontalPrice(Canvas canvas, Paint linePaint, float y, Float fixedPrice) {
        float price = fixedPrice != null ? fixedPrice
                : this.getPrice(new Point(0, Math.round(y)));
        String text = getLimitDigit(price);
        Paint textPaint = new Paint(linePaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(Const.FONT_SIZE_MIDDLE);
        textPaint.setFakeBoldText(true);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float margin = 2;
        float baseline = y - margin - fm.descent;
        if (baseline + fm.ascent < 0) {
            baseline = y + margin - fm.ascent;
        }
        canvas.drawText(text, 0, baseline, textPaint);
    }

    private void drawFibonacci(Canvas canvas, Paint paint, float x1, float y1,
                               float x2, float y2) {
        float textSize = paint.getTextSize();
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setFakeBoldText(true);
        canvas.drawLine(x1, y1, x2, y2, paint);
        float left = Math.min(x1, x2);
        for (float level : FIB_LEVELS) {
            float y = y2 + (y1 - y2) * level;
            canvas.drawLine(left, y, comnInfo.getScreenWidth(), y, paint);
            String label = String.format(Locale.US, "%s (%s)", level,
                    getLimitDigit(this.getPrice(new Point(0, Math.round(y)))));
            canvas.drawText(label, left + 4, y - 4, paint);
        }
        paint.setFakeBoldText(false);
        paint.setTextSize(textSize);
    }

    /* TD序列の数字を描く */
    private void drawTDtdSequential(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setStrokeWidth(2);
        // 文字粗体
        paint.setFakeBoldText(true);
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            if (i == 60) {
                break;
            }
            if (stickInfo.getTdSequentialUp() == 0 && stickInfo.getTdSequentialDown() == 0) {
                continue;
            }
            // 文字坐标
            int x = stickInfo.getStickRect().left;
            if (stickInfo.getTdSequentialUp() >= 8) {
                // LIME
                paint.setColor(Color.parseColor("#00FF00"));
                // 最新価格設定
                canvas.drawText(String.valueOf(stickInfo.getTdSequentialUp()), x, comnInfo.getStickHeight() - 8, paint);
            }
            if (stickInfo.getTdSequentialDown() >= 8) {
                // PINK
                paint.setColor(Color.parseColor("#FFB6C1"));
                // 最新価格設定
                canvas.drawText(String.valueOf(stickInfo.getTdSequentialDown()), x, comnInfo.getStickHeight() - 8, paint);
            }
        }
    }

    /* 日期对应的X坐标：画面内取K线中心；画面外按K线间距或时间比例外推 */
    private int getX(String date) {
        if (date == null || stickList == null || stickList.isEmpty()) {
            return NO_X;
        }
        int visible = Math.min(comnInfo.getMaxStickCount(stickList.size()), stickList.size());
        for (int i = 0; i < visible; i++) {
            StickInfo stick = stickList.get(i);
            if (date.equals(stick.getDate())) {
                return stick.getLineRect().centerX();
            }
        }
        if (visible < 2) {
            return NO_X;
        }
        StickInfo newest = stickList.get(0);
        StickInfo oldest = stickList.get(visible - 1);
        int x0 = newest.getLineRect().centerX();
        int xN = oldest.getLineRect().centerX();
        // 已加载但在画面左侧的K线按序号计算
        for (int i = visible; i < stickList.size(); i++) {
            if (date.equals(stickList.get(i).getDate())) {
                return Math.round(x0 - i * (float) (x0 - xN) / (visible - 1));
            }
        }
        long t = parseTime(date);
        long t0 = parseTime(newest.getDate());
        long tN = parseTime(oldest.getDate());
        if (t == NO_TIME || t0 == NO_TIME || tN == NO_TIME || t0 == tN) {
            return NO_X;
        }
        return (int) Math.round(xN + (double) (t - tN) * (x0 - xN) / (t0 - tN));
    }

    private long parseTime(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    date.length() == 8 ? "yyyyMMdd" : "yyyy-MM-dd HH:mm", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(date).getTime();
        } catch (Exception e) {
            return NO_TIME;
        }
    }

    /* 超过价格边界线,按照边界线计算 */
    private int getY(float price) {
        StickInfo stick = stickList.get(0);
        if (stick.getMaxValue() < price || stick.getMinValue() > price) {
            return -1;
        } else {
            return stick.getYPoint(price);
        }
    }

    /* 表示时,MACD区域内画线也可以表示 */
    private Point getVirtualPoint(String date, float price) {
        int x = this.getX(date);
        if (x == NO_X) {
            return null;
        }
        return new Point(x, stickList.get(0).getYPoint(price));
    }

    /* 单击事件，由Activity转发事件 */
    public void onSingleTapConfirmed(MotionEvent e) {
        if (activity.kLine.equals(Const.K_Line.Normal.toString())) {// K線図
            activity.kLine = Const.K_Line.Average.toString();
        } else {// 平均K線図
            activity.kLine = Const.K_Line.Normal.toString();
        }
        activity.showUI();
    }

    /* 取得数值的像素长度 */
    private int getDigitLength(float value) {
        String digit = getLimitDigit(value);
        if (digit.indexOf("－") == -1) {
            return digit.length() * 23;
        } else {
            return digit.length() * 23;
        }
    }

    /* 价格显示精度 */
    public String getLimitDigit(float value) {
        DecimalFormat format = "btc".equals(activity.getSymbol())
                || "eth".equals(activity.getSymbol()) ? btcDigitFormat : digitFormat;
        return format.format(value).replaceAll("-", "－");
    }

    public void setTouchPoint(Point touchPoint) {
        this.touchPoint = touchPoint;
    }

    public void setTouchedDate(String date) {
        this.touchedDate = date;
    }

    // 根据触摸位置,将x轴调整为同位置K线的中心
    public StickInfo getRelatedStick(float x) {
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            Rect stickReck = stickInfo.getStickRect();
            if (x >= stickReck.left && x <= stickReck.right + 2) {
                return stickInfo;
            }
        }
        return null;
    }

    public void setCurrentValue(Float currentValue) {
        this.currentValue = currentValue;
    }

    // 点到直线的最短距离的判断 点（x0,y0） 到由两点组成的线段（x1,y1） ,( x2,y2 )
    private double pointToLine(int x1, int y1, int x2, int y2, int x0,
                               int y0) {
        double space = 0;
        double a, b, c;
        a = lineSpace(x1, y1, x2, y2);// 线段的长度
        b = lineSpace(x1, y1, x0, y0);// (x1,y1)到点的距离
        c = lineSpace(x2, y2, x0, y0);// (x2,y2)到点的距离
        if (c <= 0.000001 || b <= 0.000001) {
            space = 0;
            return space;
        }
        if (a <= 0.000001) {
            space = b;
            return space;
        }
        if (c * c >= a * a + b * b) {
            space = b;
            return space;
        }
        if (b * b >= a * a + c * c) {
            space = c;
            return space;
        }
        double p = (a + b + c) / 2;// 半周长
        double s = Math.sqrt(p * (p - a) * (p - b) * (p - c));// 海伦公式求面积
        space = 2 * s / a;// 返回点到线的距离（利用三角形面积公式求高）
        return space;
    }

    // 计算两点之间的距离
    private double lineSpace(int x1, int y1, int x2, int y2) {
        double lineLength = 0;
        lineLength = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2)
                * (y1 - y2));
        return lineLength;
    }
}
