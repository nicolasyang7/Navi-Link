package com.navi.link.window;
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
import android.view.View;

public class FloatingWindowFactory {
    public static BaseFloatingWindow createWindow(int currentMode, int styleMode, Context context, View floatingView) {
        if (currentMode == FloatingWindowManager.MODE_CRUISE) {
            if (styleMode == 1) {
                return new MinimalCruiseWindow(context, floatingView);
            } else if (styleMode == 2) {
                return new FullCruiseWindow(context, floatingView);
            } else {
                return new NormalCruiseWindow(context, floatingView);
            }
        } else {
            switch (styleMode) {
                case 1:
                    return new MinimalNaviWindow(context, floatingView);
                case 2:
                    return new FullNaviWindow(context, floatingView);
                case 0:
                default:
                    return new NormalNaviWindow(context, floatingView);
            }
        }
    }
}
