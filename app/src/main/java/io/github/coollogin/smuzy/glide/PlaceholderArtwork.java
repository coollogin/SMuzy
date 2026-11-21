package io.github.coollogin.smuzy.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import io.github.coollogin.smuzy.R;

/** Renders the SMuzy placeholder drawables for consumers that need a Bitmap (widgets, media art). */
public final class PlaceholderArtwork {
    public static final int SIZE = 512;

    private PlaceholderArtwork() {
    }

    @NonNull
    public static Bitmap songBitmap(@NonNull Context context) {
        return render(context, R.drawable.ic_placeholder_song, SIZE);
    }

    @NonNull
    public static Bitmap render(@NonNull Context context, @DrawableRes int drawableRes, int size) {
        Drawable drawable = AppCompatResources.getDrawable(context, drawableRes);
        if (drawable == null) {
            throw new IllegalStateException("Placeholder drawable is unavailable");
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        return bitmap;
    }
}
