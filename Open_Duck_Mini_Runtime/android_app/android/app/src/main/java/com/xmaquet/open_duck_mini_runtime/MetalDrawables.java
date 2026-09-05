package com.xmaquet.open_duck_mini_runtime;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ScrollView;
import androidx.core.content.ContextCompat;

/** Usine des fonds métal écaillé, pour boutons, cartes et bandeau. */
public final class MetalDrawables {
  private MetalDrawables() {}

  public static Drawable button(Context ctx, int color, int pressColor) {
    return stateList(ctx, color, pressColor, false);
  }

  public static Drawable plate(Context ctx, int color, int pressColor) {
    return stateList(ctx, color, pressColor, true);
  }

  public static Drawable fromRes(Context ctx, int resId) {
    if (resId == R.drawable.btn_flat_blue) {
      return button(ctx, color(ctx, R.color.bs_blue), color(ctx, R.color.bs_blue_press));
    }
    if (resId == R.drawable.btn_flat_green) {
      return button(ctx, color(ctx, R.color.bs_green), color(ctx, R.color.bs_green_press));
    }
    if (resId == R.drawable.btn_flat_red) {
      return button(ctx, color(ctx, R.color.bs_red), color(ctx, R.color.bs_red_press));
    }
    if (resId == R.drawable.btn_flat_yellow) {
      return button(ctx, color(ctx, R.color.bs_yellow), color(ctx, R.color.bs_yellow_press));
    }
    if (resId == R.drawable.btn_flat_cyan) {
      return button(ctx, color(ctx, R.color.bs_cyan), color(ctx, R.color.bs_cyan_press));
    }
    if (resId == R.drawable.btn_flat_teal) {
      return button(ctx, color(ctx, R.color.bs_teal), color(ctx, R.color.bs_teal_press));
    }
    if (resId == R.drawable.btn_flat_gray) {
      return button(ctx, color(ctx, R.color.bs_gray), color(ctx, R.color.bs_gray_press));
    }
    if (resId == R.drawable.ble_banner_on) {
      return plate(ctx, color(ctx, R.color.bs_green), color(ctx, R.color.bs_green_press));
    }
    if (resId == R.drawable.ble_banner_off) {
      return plate(ctx, color(ctx, R.color.bs_gray), color(ctx, R.color.bs_gray_press));
    }
    if (resId == R.drawable.ble_banner_error) {
      return plate(ctx, color(ctx, R.color.bs_red), color(ctx, R.color.bs_red_press));
    }
    return ContextCompat.getDrawable(ctx, resId);
  }

  public static void install(View view, int resId) {
    if (view == null) {
      return;
    }
    boolean rivets = !(view instanceof Button);
    Drawable drawn = fromRes(view.getContext(), resId);
    if (rivets && drawn instanceof StateListDrawable) {
      int[] pair = colorsFor(view.getContext(), resId);
      if (pair != null) {
        drawn = plate(view.getContext(), pair[0], pair[1]);
      }
    }
    view.setBackground(drawn);
  }

  public static void skin(View root) {
    if (root == null) {
      return;
    }
    try {
      skinOne(root);
      if (root instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
          skin(group.getChildAt(i));
        }
      }
    } catch (RuntimeException ignored) {
      // Un fond plat vaut mieux qu’un plantage au lancement.
    }
  }

  private static void skinOne(View view) {
    if (view instanceof Button) {
      Integer color = extractColor(view.getBackground());
      if (color != null) {
        view.setBackground(button(view.getContext(), color, pressFor(view.getContext(), color)));
      }
      return;
    }
    if (view instanceof GridLayout || view instanceof ScrollView) {
      return;
    }
    if (!(view instanceof ViewGroup)) {
      return;
    }
    Integer color = extractColor(view.getBackground());
    if (color == null) {
      return;
    }
    view.setBackground(
        stateList(view.getContext(), color, pressFor(view.getContext(), color), true));
  }

  private static Drawable stateList(Context ctx, int color, int pressColor, boolean rivets) {
    StateListDrawable sld = new StateListDrawable();
    sld.addState(
        new int[] {android.R.attr.state_pressed},
        new ChippedMetalDrawable(ctx, pressColor, true, rivets));
    sld.addState(StateSet.WILD_CARD, new ChippedMetalDrawable(ctx, color, false, rivets));
    return sld;
  }

  private static Integer extractColor(Drawable bg) {
    try {
      if (bg == null || bg instanceof ChippedMetalDrawable) {
        return null;
      }
      if (bg instanceof ColorDrawable) {
        return ((ColorDrawable) bg).getColor();
      }
      if (bg instanceof InsetDrawable) {
        return extractColor(((InsetDrawable) bg).getDrawable());
      }
      if (bg instanceof RippleDrawable) {
        RippleDrawable ripple = (RippleDrawable) bg;
        if (ripple.getNumberOfLayers() > 0) {
          return extractColor(ripple.getDrawable(0));
        }
        return null;
      }
      if (bg instanceof StateListDrawable) {
        bg.setState(StateSet.WILD_CARD);
        Drawable current = bg.getCurrent();
        if (current instanceof ColorDrawable) {
          return ((ColorDrawable) current).getColor();
        }
        if (current != null && current != bg) {
          return extractColor(current);
        }
      }
    } catch (RuntimeException ignored) {
      return null;
    }
    return null;
  }

  private static int pressFor(Context ctx, int color) {
    if (color == color(ctx, R.color.bs_blue)) {
      return color(ctx, R.color.bs_blue_press);
    }
    if (color == color(ctx, R.color.bs_green)) {
      return color(ctx, R.color.bs_green_press);
    }
    if (color == color(ctx, R.color.bs_red)) {
      return color(ctx, R.color.bs_red_press);
    }
    if (color == color(ctx, R.color.bs_yellow)) {
      return color(ctx, R.color.bs_yellow_press);
    }
    if (color == color(ctx, R.color.bs_cyan)) {
      return color(ctx, R.color.bs_cyan_press);
    }
    if (color == color(ctx, R.color.bs_teal)) {
      return color(ctx, R.color.bs_teal_press);
    }
    if (color == color(ctx, R.color.bs_gray)) {
      return color(ctx, R.color.bs_gray_press);
    }
    if (color == color(ctx, R.color.bs_dark_2)) {
      return color(ctx, R.color.bs_dark_3);
    }
    return color;
  }

  private static int[] colorsFor(Context ctx, int resId) {
    if (resId == R.drawable.btn_flat_blue || resId == R.drawable.card_menu) {
      return new int[] {color(ctx, R.color.bs_blue), color(ctx, R.color.bs_blue_press)};
    }
    if (resId == R.drawable.btn_flat_green) {
      return new int[] {color(ctx, R.color.bs_green), color(ctx, R.color.bs_green_press)};
    }
    if (resId == R.drawable.btn_flat_red) {
      return new int[] {color(ctx, R.color.bs_red), color(ctx, R.color.bs_red_press)};
    }
    if (resId == R.drawable.btn_flat_yellow) {
      return new int[] {color(ctx, R.color.bs_yellow), color(ctx, R.color.bs_yellow_press)};
    }
    if (resId == R.drawable.btn_flat_cyan) {
      return new int[] {color(ctx, R.color.bs_cyan), color(ctx, R.color.bs_cyan_press)};
    }
    if (resId == R.drawable.btn_flat_teal) {
      return new int[] {color(ctx, R.color.bs_teal), color(ctx, R.color.bs_teal_press)};
    }
    if (resId == R.drawable.btn_flat_gray) {
      return new int[] {color(ctx, R.color.bs_gray), color(ctx, R.color.bs_gray_press)};
    }
    if (resId == R.drawable.ble_banner_on) {
      return new int[] {color(ctx, R.color.bs_green), color(ctx, R.color.bs_green_press)};
    }
    if (resId == R.drawable.ble_banner_off) {
      return new int[] {color(ctx, R.color.bs_gray), color(ctx, R.color.bs_gray_press)};
    }
    if (resId == R.drawable.ble_banner_error) {
      return new int[] {color(ctx, R.color.bs_red), color(ctx, R.color.bs_red_press)};
    }
    return null;
  }

  private static int color(Context ctx, int resId) {
    return ContextCompat.getColor(ctx, resId);
  }
}
