package com.yxw.gooview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

public class GooView extends View {
    private Paint mPaint;
    //连接点数组
    PointF[] mStickPoints;
    PointF[] mDragPoints;
    //控制点
    PointF mControlPoint;
    //拖拽圆圆心
    PointF mDragCenter = new PointF(450f, 650f);
    //拖拽圆半径
    float mDragRadius = 30f;
    //固定圆圆心
    PointF mStickCenter = new PointF(450f, 650f);
    //固定圆半径
    float mStickRadius = 30f;
    //状态栏高度
    private int statusBarHeight;
    //最远距离
    float farestDistance = 380f;
    //是否超过最远距离，是否需要断开
    private boolean isOutofRange;
    //拖拽圆是否消失
    private boolean isDisappear;

    public GooView(Context context) {
        this(context, null);
    }

    public GooView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GooView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 做初始化操作，定义画笔
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.RED);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 计算连接点值, 控制点, 固定圆半径

        //获取固定圆半径(根据两圆圆心距离)，距离越大，固定圆越小
        float tempStickRadius = getTempStickRadius();
        float yOffset = mStickCenter.y - mDragCenter.y;
        float xOffset = mStickCenter.x - mDragCenter.x;
        //直线斜率
        Double lineK = null;
        if (xOffset != 0) {
            lineK = (double) (yOffset / xOffset);
        }
        //通过几何图形工具获取交点坐标，圆心，半径，斜率，过圆心斜率为lineK的直线与圆的交点
        mDragPoints = GeometryUtil.getIntersectionPoints(mDragCenter, mDragRadius, lineK);
        mStickPoints = GeometryUtil.getIntersectionPoints(mStickCenter, tempStickRadius, lineK);
        //获取控制点坐标，两圆心中点
        mControlPoint = GeometryUtil.getMiddlePoint(mDragCenter, mStickCenter);
        //保存画布状态
        canvas.save();
        //画布向上平移状态栏的高度
        canvas.translate(0, -statusBarHeight);

        // 画出最大范围(参考用)
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(mStickCenter.x, mStickCenter.y, farestDistance, mPaint);
        mPaint.setStyle(Paint.Style.FILL);

        //拖拽圆是否消失
        if (!isDisappear) {
            //是否超过最远距离，是否需要断开
            if (!isOutofRange) {
                //画连接封闭部分
                Path path = new Path();
                //跳到起始点
                path.moveTo(mStickPoints[0].x, mStickPoints[0].y);
                //画二阶bezier曲线
                path.quadTo(mControlPoint.x, mControlPoint.y, mDragPoints[0].x, mDragPoints[0].y);
                //画直线
                path.lineTo(mDragPoints[1].x, mDragPoints[1].y);
                //画二阶bezier曲线
                path.quadTo(mControlPoint.x, mControlPoint.y, mStickPoints[1].x, mStickPoints[1].y);
                //自动封闭区域
                path.close();
                //画封闭区域
                canvas.drawPath(path, mPaint);
                //画固定圆
                canvas.drawCircle(mStickCenter.x, mStickCenter.y, tempStickRadius, mPaint);
            }
            //画拖拽圆
            canvas.drawCircle(mDragCenter.x, mDragCenter.y, mDragRadius, mPaint);
        }
        //恢复上次的保存状态
        canvas.restore();
    }

    // 获取固定圆半径(根据两圆圆心距离)，距离越大，固定圆越小
    private float getTempStickRadius() {
        float distance = GeometryUtil.getDistanceBetween2Points(mDragCenter, mStickCenter);
        distance = Math.min(distance, farestDistance);
        float percent = distance / farestDistance;
        return mStickRadius - percent * 0.8f * mStickRadius;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x;
        float y;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isOutofRange = false;
                isDisappear = false;
                x = event.getRawX();
                y = event.getRawY();
                PointF p = new PointF(x,y);
                //没有触摸在固定圆上，触摸事件不响应
                if(GeometryUtil.getDistanceBetween2Points(p,mStickCenter)>mStickRadius){
                    return false;
                }
                mDragCenter.set(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                x = event.getRawX();
                y = event.getRawY();
                mDragCenter.set(x, y);
                invalidate();
                //处理断开事件
                float distance = GeometryUtil.getDistanceBetween2Points(mDragCenter, mStickCenter);
                if (distance > farestDistance) {
                    isOutofRange = true;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isOutofRange) {
                    float d = GeometryUtil.getDistanceBetween2Points(mDragCenter, mStickCenter);
                    if (d > farestDistance) {
                        //拖拽超出范围,断开, 松手, 消失
                        isDisappear = true;
                        invalidate();
                    } else {
                        //拖拽超出范围,断开,放回去了,恢复
                        mDragCenter.set(mStickCenter.x, mStickCenter.y);
                        invalidate();
                    }
                } else {
                    //拖拽没超出范围, 松手,弹回去
                    final PointF tempDragCenter = new PointF(mDragCenter.x, mDragCenter.y);
                    //使用值动画
                    ValueAnimator mAnim = ValueAnimator.ofFloat(1.0f);
                    mAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                        @Override
                        public void onAnimationUpdate(ValueAnimator mAnim) {
                            float percent = mAnim.getAnimatedFraction();
                            PointF p = GeometryUtil.getPointByPercent(tempDragCenter, mStickCenter, percent);
                            mDragCenter.set(p.x, p.y);
                            invalidate();
                        }
                    });
                    mAnim.setInterpolator(new OvershootInterpolator(3));
                    mAnim.setDuration(500);
                    mAnim.start();
                }
                break;
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //获取状态栏高度
        statusBarHeight = Utils.getStatusBarHeight(this);
    }
}
