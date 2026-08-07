package com.navi.link.delegate;
import com.navi.link.R;
import com.navi.link.BuildConfig;
import com.navi.link.activity.*;
import com.navi.link.delegate.*;
import com.navi.link.window.*;
import com.navi.link.view.*;
import com.navi.link.receiver.*;
import com.navi.link.service.*;
import com.navi.link.utils.*;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class AboutUsPanelDelegate {
    private final MainActivity activity;

    private TextView tvAboutAppVersion;
    private TextView tvAboutCpuInfo;
    private TextView tvAboutRamInfo;
    private TextView tvAboutRomInfo;
    private TextView tvAboutApiLevel;
    private TextView tvDisplayPhysicalRes;
    private TextView tvDisplayAppRes;
    private TextView tvDisplayDensity;
    private TextView tvDisplayRefreshRate;
    private TextView tvAboutQqGroup;
    private TextView tvAboutGitUrl;

    public AboutUsPanelDelegate(MainActivity activity) {
        this.activity = activity;
    }

    public void initViews() {
        tvAboutAppVersion = activity.findViewById(R.id.tv_about_app_version);
        tvAboutCpuInfo = activity.findViewById(R.id.tv_about_cpu_info);
        tvAboutRamInfo = activity.findViewById(R.id.tv_about_ram_info);
        tvAboutRomInfo = activity.findViewById(R.id.tv_about_rom_info);
        tvAboutApiLevel = activity.findViewById(R.id.tv_about_api_level);
        tvDisplayPhysicalRes = activity.findViewById(R.id.tv_display_physical_res);
        tvDisplayAppRes = activity.findViewById(R.id.tv_display_app_res);
        tvDisplayDensity = activity.findViewById(R.id.tv_display_density);
        tvDisplayRefreshRate = activity.findViewById(R.id.tv_display_refresh_rate);
        tvAboutQqGroup = activity.findViewById(R.id.tv_about_qq_group);
        tvAboutGitUrl = activity.findViewById(R.id.tv_about_git_url);

        setupListeners();
    }

    public void setupListeners() {
        if (tvAboutQqGroup != null) {
            tvAboutQqGroup.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("QQ Group", "1106923186");
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, "QQ交流群已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (tvAboutGitUrl != null) {
            tvAboutGitUrl.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Git Repo", "https://github.com/shuhao1022/Navi-Link");
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(activity, "开源地址已复制到剪贴板", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 广播调试入口：全屏实时显示 10001/60073/13012 白名单字段
        View llBroadcastDebug = activity.findViewById(R.id.ll_about_broadcast_debug);
        if (llBroadcastDebug != null) {
            llBroadcastDebug.setOnClickListener(v -> new BroadcastDebugDialog(activity).show());
        }
    }

    public void loadSettings() {
        // App Version
        if (tvAboutAppVersion != null) {
            try {
                String versionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
                tvAboutAppVersion.setText("v" + versionName);
            } catch (Exception e) {
                tvAboutAppVersion.setText("未知");
            }
        }

        // CPU Info
        if (tvAboutCpuInfo != null) {
            tvAboutCpuInfo.setText(getAboutCpuInfo());
        }

        // RAM Info
        if (tvAboutRamInfo != null) {
            tvAboutRamInfo.setText(getAboutRamInfo());
        }

        // ROM Info
        if (tvAboutRomInfo != null) {
            tvAboutRomInfo.setText(getAboutRomInfo());
        }

        // Android API Level
        if (tvAboutApiLevel != null) {
            tvAboutApiLevel.setText("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        }

        // Display Info
        initDisplayInfo();
    }

    private String getAboutCpuInfo() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Hardware") || line.contains("model name") || line.contains("Processor")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        String hardware = parts[1].trim();
                        int cores = Runtime.getRuntime().availableProcessors();
                        String arch = System.getProperty("os.arch");
                        return hardware + " (" + cores + "核 / " + arch + ")";
                    }
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        int cores = Runtime.getRuntime().availableProcessors();
        String arch = System.getProperty("os.arch");
        return Build.HARDWARE + " (" + cores + "核 / " + arch + ")";
    }

    private String getAboutRamInfo() {
        android.app.ActivityManager am = (android.app.ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(mi);
            long totalMem = mi.totalMem;
            long availMem = mi.availMem;
            return formatByteSize(availMem) + " 可用 / 共 " + formatByteSize(totalMem);
        }
        return "未知";
    }

    private String getAboutRomInfo() {
        try {
            java.io.File path = android.os.Environment.getDataDirectory();
            android.os.StatFs stat = new android.os.StatFs(path.getPath());
            long[] storageStats = PlatformCompat.getStorageStats(stat);
            long blockSize = storageStats[0];
            long totalBlocks = storageStats[1];
            long availableBlocks = storageStats[2];
            long totalRom = totalBlocks * blockSize;
            long availRom = availableBlocks * blockSize;
            return formatByteSize(availRom) + " 可用 / 共 " + formatByteSize(totalRom);
        } catch (Exception e) {
            return "未知";
        }
    }

    private String formatByteSize(long size) {
        double gb = size / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format(java.util.Locale.US, "%.2f GB", gb);
        }
        double mb = size / (1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", mb);
    }

    private void initDisplayInfo() {
        try {
            android.view.WindowManager wm = (android.view.WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                android.view.Display display = wm.getDefaultDisplay();
                android.util.DisplayMetrics realMetrics = new android.util.DisplayMetrics();
                android.util.DisplayMetrics appMetrics = new android.util.DisplayMetrics();

                display.getRealMetrics(realMetrics);
                display.getMetrics(appMetrics);

                // 1. 物理分辨率
                if (tvDisplayPhysicalRes != null) {
                    tvDisplayPhysicalRes.setText(realMetrics.widthPixels + " × " + realMetrics.heightPixels);
                }

                // 2. 应用分辨率
                if (tvDisplayAppRes != null) {
                    tvDisplayAppRes.setText(appMetrics.widthPixels + " × " + appMetrics.heightPixels);
                }

                // 3. 屏幕密度
                if (tvDisplayDensity != null) {
                    tvDisplayDensity.setText(realMetrics.densityDpi + " dpi (" + String.format(java.util.Locale.US, "%.2f", realMetrics.density) + ")");
                }

                // 4. 刷新率
                if (tvDisplayRefreshRate != null) {
                    float refreshRate = display.getRefreshRate();
                    tvDisplayRefreshRate.setText(String.format(java.util.Locale.US, "%.0f Hz", refreshRate));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateThemeColors() {
    }
}
