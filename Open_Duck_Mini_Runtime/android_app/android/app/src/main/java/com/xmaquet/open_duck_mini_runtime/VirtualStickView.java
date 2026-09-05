package com.xmaquet.open_duck_mini_runtime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Stick analogique plat : nx/ny dans [-1, 1], origin au centre. */
public class VirtualStickView extends View {
  private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float nx;
  private float ny;
  private float cx;
  private float cy;
  private float radius;
  private float knobX;
  private float knobY;

  public VirtualStickView(Context context) {
    super(context);
    init();
  }

  public VirtualStickView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public VirtualStickView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    basePaint.setColor(0xFF2A2A2A);
    basePaint.setStyle(Paint.Style.FILL);
    ringPaint.setColor(0xFF8A8680);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(3.5f);
    knobPaint.setColor(0xFF6A6864);
    knobPaint.setStyle(Paint.Style.FILL);
  }

  public float getNx() {
    return nx;
  }

  public float getNy() {
    return ny;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    cx = w / 2f;
    cy = h / 2f;
    radius = Math.min(w, h) / 2f - 8f;
    knobX = cx;
    knobY = cy;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.drawCircle(cx, cy, radius, basePaint);
    canvas.drawCircle(cx, cy, radius, ringPaint);
    canvas.drawCircle(knobX, knobY, radius * 0.32f, knobPaint);
    canvas.drawCircle(knobX, knobY, radius * 0.32f, ringPaint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_MOVE:
        updateFrom(event.getX(), event.getY());
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        nx = 0f;
        ny = 0f;
        knobX = cx;
        knobY = cy;
        invalidate();
        return true;
      default:
        return super.onTouchEvent(event);
    }
  }

  private void updateFrom(float x, float y) {
    float dx = x - cx;
    float dy = y - cy;
    float dist = (float) Math.hypot(dx, dy);
    float max = radius * 0.68f;
    if (dist > max && dist > 0f) {
      dx = dx / dist * max;
      dy = dy / dist * max;
      dist = max;
    }
    knobX = cx + dx;
    knobY = cy + dy;
    nx = max > 0 ? dx / max : 0f;
    ny = max > 0 ? dy / max : 0f;
    nx = Math.max(-1f, Math.min(1f, nx));
    ny = Math.max(-1f, Math.min(1f, ny));
    invalidate();
  }
}
