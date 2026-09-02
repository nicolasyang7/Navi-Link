package com.navi.link.utils;
import com.navi.link.R;
import com.navi.link.BuildConfig;
import com.navi.link.activity.*;
import com.navi.link.delegate.*;
import com.navi.link.window.*;
import com.navi.link.view.*;
import com.navi.link.receiver.*;
import com.navi.link.service.*;
import com.navi.link.utils.*;


import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.RequiresApi;

/**
 * Compatibility wrapper for the overlay permission model introduced in Android 6.0.
 *
 * <p>On Android 4.2-5.1 the manifest permission is granted during installation,
 * and {@link Settings#canDrawOverlays(Context)} does not exist.</p>
 */
public final class OverlayPermissionCompat {

    private OverlayPermissionCompat() {}

    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Api23Impl.canDrawOverlays(context);
    }

    private static final class Api23Impl {
        private Api23Impl() {}

        @RequiresApi(Build.VERSION_CODES.M)
        static boolean canDrawOverlays(Context context) {
            return Settings.canDrawOverlays(context);
        }
    }
}
