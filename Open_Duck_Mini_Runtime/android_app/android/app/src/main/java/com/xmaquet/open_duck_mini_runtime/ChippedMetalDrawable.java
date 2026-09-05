package com.xmaquet.open_duck_mini_runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Plaque métal peint écaillé : chamfreins, grain, rivets si assez grande. */
public class ChippedMetalDrawable extends Drawable {
  private static Bitmap chipTile;

  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rivetFill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint rivetRing = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private final RectF tmp = new RectF();
  private final int color;
  private final boolean pressed;
  private final boolean rivets;

  public ChippedMetalDrawable(Context ctx, int color, boolean pressed, boolean rivets) {
    this.color = color;
    this.pressed = pressed;
    this.rivets = rivets;
    ensureChip(ctx);
    fillPaint.setStyle(Paint.Style.FILL);
    chipPaint.setStyle(Paint.Style.FILL);
    if (chipTile != null) {
      chipPaint.setShader(
          new BitmapShader(chipTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
      chipPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setColor(0xFF1A1A1A);
    rivetFill.setStyle(Paint.Style.FILL);
    rivetFill.setColor(0xFF3A3A3A);
    rivetRing.setStyle(Paint.Style.STROKE);
    rivetRing.setColor(0xFF141414);
    highlightPaint.setStyle(Paint.Style.STROKE);
    highlightPaint.setColor(0x55FFFFFF);
  }

  private static synchronized void ensureChip(Context ctx) {
    if (chipTile != null && !chipTile.isRecycled()) {
      return;
    }
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
    opts.inScaled = false;
    Bitmap decoded = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.tex_paint_chips, opts);
    if (decoded == null) {
      return;
    }
    if (decoded.getConfig() == Bitmap.Config.HARDWARE) {
      Bitmap copy = decoded.copy(Bitmap.Config.ARGB_8888, false);
      decoded.recycle();
      decoded = copy;
    }
    chipTile = decoded;
  }

  @Override
  public void draw(Canvas canvas) {
    Rect b = getBounds();
    if (b.width() <= 2 || b.height() <= 2) {
      return;
    }
    float inset = pressed ? 2f : 0.5f;
    tmp.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
    if (tmp.width() <= 2f || tmp.height() <= 2f) {
      return;
    }
    float shortSide = Math.min(tmp.width(), tmp.height());
    float chamfer = rivets ? Math.min(16f, shortSide * 0.14f) : Math.min(7f, shortSide * 0.1f);
    if (chamfer * 2f >= shortSide) {
      chamfer = shortSide * 0.2f;
    }
    buildChamfer(path, tmp, chamfer);

    fillPaint.setColor(pressed ? darken(color, 0.84f) : color);
    canvas.drawPath(path, fillPaint);

    if (chipPaint.getShader() != null) {
      int save = canvas.save();
      canvas.clipPath(path);
      canvas.drawRect(tmp, chipPaint);
      canvas.restoreToCount(save);
    }

    highlightPaint.setStrokeWidth(2.2f);
    canvas.drawLine(
        tmp.left + chamfer + 2f, tmp.top + 2f, tmp.right - chamfer - 2f, tmp.top + 2f, highlightPaint);

    strokePaint.setStrokeWidth(rivets ? 2.8f : 2f);
    canvas.drawPath(path, strokePaint);

    if (rivets && tmp.width() > 110f && tmp.height() > 52f) {
      float r = Math.min(8.5f, shortSide * 0.085f);
      float m = chamfer + r + 5f;
      drawRivet(canvas, tmp.left + m, tmp.top + m, r);
      drawRivet(canvas, tmp.right - m, tmp.top + m, r);
      drawRivet(canvas, tmp.left + m, tmp.bottom - m, r);
      drawRivet(canvas, tmp.right - m, tmp.bottom - m, r);
    }
  }

  private static void buildChamfer(Path path, RectF r, float c) {
    path.reset();
    path.moveTo(r.left + c, r.top);
    path.lineTo(r.right - c, r.top);
    path.lineTo(r.right, r.top + c);
    path.lineTo(r.right, r.bottom - c);
    path.lineTo(r.right - c, r.bottom);
    path.lineTo(r.left + c, r.bottom);
    path.lineTo(r.left, r.bottom - c);
    path.lineTo(r.left, r.top + c);
    path.close();
  }

  private void drawRivet(Canvas canvas, float x, float y, float r) {
    canvas.drawCircle(x, y, r, rivetFill);
    rivetRing.setStrokeWidth(1.7f);
    canvas.drawCircle(x, y, r, rivetRing);
    rivetRing.setStrokeWidth(1.2f);
    canvas.drawCircle(x, y, r * 0.42f, rivetRing);
  }

  private static int darken(int color, float f) {
    return Color.argb(
        Color.alpha(color),
        Math.round(Color.red(color) * f),
        Math.round(Color.green(color) * f),
        Math.round(Color.blue(color) * f));
  }

  @Override
  public void setAlpha(int alpha) {
    fillPaint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {
    fillPaint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
