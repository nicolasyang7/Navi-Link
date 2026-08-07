package com.navi.link.receiver;
import com.navi.link.window.*;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONArray;

public class AmapNaviReceiver extends BroadcastReceiver {

    private static final String TAG = "AmapNavi";
    private boolean isLog = true;

    // ===== 广播调试 =====
    // 白名单：仅展示代码实际用到的字段（handleNaviInfo/handleCruiseInfo/handleTrafficLight/updateLaneLines）
    private static final String[] FIELDS_10001 = {
            "NEW_ICON", "ICON", "SEG_REMAIN_DIS_AUTO", "ROUTE_REMAIN_DIS_AUTO", "ROUTE_REMAIN_TIME_AUTO",
            "ETA_TEXT", "NEXT_ROAD_NAME", "CUR_ROAD_NAME", "ROUTE_REMAIN_DIS", "ROUTE_ALL_DIS",
            "CUR_SPEED", "LIMITED_SPEED", "CAMERA_DIST", "CAMERA_SPEED", "CAMERA_TYPE", "endPOIName",
            "TRAFFIC_LIGHT_NUM", "routeRemainTrafficLightNum", "CAR_DIRECTION",
            "EXIT_NAME_INFO", "EXIT_DIRECTION_INFO",
            "SAPA_NAME", "SAPA_DIST_AUTO", "SAPA_TYPE", "NEXT_SAPA_NAME", "NEXT_SAPA_DIST_AUTO", "NEXT_SAPA_TYPE"
    };
    private static final String[] FIELDS_60073 = {
            "trafficLightStatus", "dir", "redLightCountDownSeconds", "lightsData"
    };
    private static final String[] FIELDS_13012 = {
            "EXTRA_DRIVE_WAY"
    };

    /** 广播调试监听：页面打开时注册，收到白名单字段后回调（主线程） */
    public interface BroadcastDebugListener {
        void onBroadcastData(int keyType, java.util.Map<String, String> fields);
    }

    private static volatile BroadcastDebugListener sDebugListener;

    public static void setBroadcastDebugListener(BroadcastDebugListener listener) {
        sDebugListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"AUTONAVI_STANDARD_BROADCAST_SEND".equals(intent.getAction())) return;

        FloatingWindowManager manager = FloatingWindowManager.getInstance(context);
        if (manager == null) return;

        int keyType = intent.getIntExtra("KEY_TYPE", 0);

        // 转发白名单字段给调试页面（页面未打开时零开销）
        forwardDebug(keyType, intent);


        Bundle extras = intent.getExtras();
        if (extras != null && isLog) {
            // 打印所有原始数据
            Log.d(TAG, "========== 🚥所有原始数据包==========");
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, "Key: " + key + " | Value: " + value + " | Type: " + (value != null ? value.getClass().getSimpleName() : "null"));
            }
            Log.d(TAG, "==========================================================");
        }

        // 昼夜模式切换及前后台、结束状态广播
        if (keyType == 10019) {
            int extraState = getIntSafe(intent, "EXTRA_STATE", -1);
            if (extraState == 37 || extraState == 38) {
                boolean isNight = (extraState == 38);
                manager.onDayNightChanged(isNight);
            } else if (extraState == 3 || extraState == 4) {
                boolean isForeground = (extraState == 3);
                manager.onAmapForegroundChanged(isForeground);
            } else if (extraState == 9) {
                manager.onNavigationEnded();
            } else if (extraState == 25) {
                manager.onCruiseEnded();
            }

            // 路口放大图状态（EXTRA_CROSS_MAP = 1 表示有路口放大图）
            if (intent.hasExtra("EXTRA_CROSS_MAP")) {
                int crossMap = getIntSafe(intent, "EXTRA_CROSS_MAP", 0);
                manager.updateCrossMapStatus(crossMap);
            }

            return;
        }

        if (!manager.isShowing()) return;

        if (keyType == 12110) {
            manager.resetWatchdog();
            // 区间测速广播不续导航超时：双高德共存时无法区分来源（导航/巡航字段相同），
            // 导航活跃只由 10001 ICON!=0 定义，避免后台巡航高德的区间测速广播无限续命
            int startDist = getIntSafe(intent, "START_DISTANCE", -1);
            String startDistText = intent.getStringExtra("START_DISTANCE_TEXT");
            int avgSpeed = getIntSafe(intent, "AVERAGE_SPEED", 0);
            String endDistText = intent.getStringExtra("END_DISTANCE_TEXT");
            int limitSpeed = getIntSafe(intent, "LIMITED_SPEED", 0);
            manager.updateIntervalSpeed(startDist, startDistText, avgSpeed, endDistText, limitSpeed);
            return;
        }

        if (keyType == 60073) {
            // 红绿灯数据也视为有活动数据
            manager.resetWatchdog();
            // 双高德共存：导航模式下忽略巡航多灯红绿灯广播（带非空 lightsData），
            // 只处理导航单灯红绿灯（trafficLightStatus），避免巡航灯组污染导航窗口
            String cruiseLights = intent.getStringExtra("lightsData");
            boolean hasCruiseLights = cruiseLights != null && !cruiseLights.trim().isEmpty();
            if (manager.getCurrentMode() != FloatingWindowManager.MODE_NAVI || !hasCruiseLights) {
                handleTrafficLight(intent, manager);
                if (manager.getCurrentMode() == FloatingWindowManager.MODE_NAVI) {
                    manager.resetNaviTimeout();
                }
            }
            return;
        }

        if (keyType == 13011) {
            // TMC 路况数据
            String tmcSegment = intent.getStringExtra("EXTRA_TMC_SEGMENT");
            if (tmcSegment != null) {
                manager.updateTmcData(tmcSegment);
            }
            return;
        }

        if (keyType == 13012) {
            // 车道线数据
            String driveWay = intent.getStringExtra("EXTRA_DRIVE_WAY");
            if (driveWay != null) {
                manager.updateLaneLines(driveWay);
            }
            return;
        }

        if (keyType == 10001) {
            // 导航或巡航信息
            if (manager.isNavigationJustEnded() || manager.isCruiseJustEnded()) {
                return;
            }
            manager.resetWatchdog();
            // 数据断流看门狗：任何 10001（无论 ICON）都视为高德存活心跳
            manager.resetDataWatchdog();

            int icon = getIntSafe(intent, "NEW_ICON", 0);
            if (icon == 0) {
                icon = getIntSafe(intent, "ICON", 0);
            }

            if (icon != 0) {
                // 有转向图标，说明在导航模式
                manager.switchToNaviMode();
                handleNaviInfo(intent, manager);
            } else {
                // ICON=0：巡航数据
                // 双高德共存时（预装版后台巡航 + 改装版前台导航），巡航广播会立即打断导航
                // 导致导航/巡航窗口来回闪，因此导航模式下忽略巡航广播：
                // 不切模式、不更新巡航缓存（避免污染导航速度与超速提醒逻辑）
                // 导航结束后由 6 秒导航超时（resetNaviTimeout）自动切回巡航
                if (manager.getCurrentMode() == FloatingWindowManager.MODE_CRUISE && manager.isCruiseEnabled()) {
                    // 导航结束信号（STATE=9）只切模式不重建窗口，此时窗口仍是导航布局：
                    // 巡航数据到达时先重建为巡航窗口再更新数据
                    if (manager.isNaviWindowActive()) {
                        manager.switchToCruiseMode();
                    }
                    handleCruiseInfo(intent, manager);
                }
            }
        }
    }

    private void handleTrafficLight(Intent intent, FloatingWindowManager manager) {
        if (manager.getCurrentMode() == FloatingWindowManager.MODE_NAVI) {
            int status = getIntSafe(intent, "trafficLightStatus", 0);
            int dir = getIntSafe(intent, "dir", 4);
            int countdown = getIntSafe(intent, "redLightCountDownSeconds", 0);
            manager.updateTrafficLight(status, dir, countdown);
            return;
        }
        // 巡航模式红绿灯数据
        String lightsData = intent.getStringExtra("lightsData");
        if (lightsData != null) {
            try {
                manager.updateCruiseTrafficLights(new JSONArray(lightsData));
            } catch (Exception e) {
                Log.e(TAG, "解析巡航红绿灯数据失败", e);
            }
        }
    }

    private void handleNaviInfo(Intent intent, FloatingWindowManager manager) {
        String segRemainDis = intent.getStringExtra("SEG_REMAIN_DIS_AUTO");
        String routeRemainDis = intent.getStringExtra("ROUTE_REMAIN_DIS_AUTO");
        String routeRemainTime = intent.getStringExtra("ROUTE_REMAIN_TIME_AUTO");
        String etaText = intent.getStringExtra("ETA_TEXT");
        String nextRoadName = intent.getStringExtra("NEXT_ROAD_NAME");
        String curRoadName = intent.getStringExtra("CUR_ROAD_NAME");

        int icon = getIntSafe(intent, "NEW_ICON", 0);
        if (icon == 0) {
            icon = getIntSafe(intent, "ICON", 0);
        }

        // 安全兜底防空指针
        if (segRemainDis == null) segRemainDis = "0米";
        if (routeRemainDis == null) routeRemainDis = "0公里";
        if (routeRemainTime == null) routeRemainTime = "0分钟";
        if (nextRoadName == null) nextRoadName = curRoadName;
        if (nextRoadName == null) nextRoadName = "未知道路";
        String roadName = nextRoadName;
        String eta = etaText != null ? etaText : "";

        // 智能拆分距离与单位
        String disUnit = "公里";
        if (segRemainDis.endsWith("公里")) {
            segRemainDis = segRemainDis.replace("公里", "");
        } else {
            disUnit = "米";
            if (segRemainDis.endsWith("米")) {
                segRemainDis = segRemainDis.replace("米", "");
            }
        }
        String disNum = segRemainDis;

        // 拼装底部 Summary 文本
        String summaryStr = routeRemainDis + " · " + routeRemainTime;

        // 进度条计算
        int routeRemainDisInt = getIntSafe(intent, "ROUTE_REMAIN_DIS", 0);
        int routeAllDis = getIntSafe(intent, "ROUTE_ALL_DIS", 1);
        int progressPercentage = routeAllDis > 0
                ? (int) ((1.0f - (float) routeRemainDisInt / routeAllDis) * 100)
                : 0;

        int curSpeed = getIntSafe(intent, "CUR_SPEED", 0);
        int limitedSpeed = getIntSafe(intent, "LIMITED_SPEED", 0);
        int cameraDist = getIntSafe(intent, "CAMERA_DIST", 0);
        int cameraSpeed = getIntSafe(intent, "CAMERA_SPEED", 0);
        int cameraType = getIntSafe(intent, "CAMERA_TYPE", 0);
        String endPoiName = intent.getStringExtra("endPOIName");
        int totalLightNum = getIntSafe(intent, "TRAFFIC_LIGHT_NUM", 0);
        int remainLightNum = getIntSafe(intent, "routeRemainTrafficLightNum", 0);
        int carDirection = getIntSafe(intent, "CAR_DIRECTION", -1);

        manager.updateNaviInfo(icon, disNum, disUnit, "进", roadName,
                summaryStr, eta, progressPercentage, curSpeed,
                limitedSpeed, cameraType, cameraDist, cameraSpeed,
                endPoiName, totalLightNum, remainLightNum, curRoadName, carDirection);

        // 出口信息
        String exitName = intent.getStringExtra("EXIT_NAME_INFO");
        String exitDirection = intent.getStringExtra("EXIT_DIRECTION_INFO");
        manager.updateExitInfo(exitName, exitDirection);

        // 服务区信息
        String sapaName = intent.getStringExtra("SAPA_NAME");
        String sapaDist = intent.getStringExtra("SAPA_DIST_AUTO");
        int sapaType = getIntSafe(intent, "SAPA_TYPE", 0);
        String nextSapaName = intent.getStringExtra("NEXT_SAPA_NAME");
        String nextSapaDist = intent.getStringExtra("NEXT_SAPA_DIST_AUTO");
        int nextSapaType = getIntSafe(intent, "NEXT_SAPA_TYPE", 0);
        manager.updateSapaInfo(sapaName, sapaDist, sapaType, nextSapaName, nextSapaDist, nextSapaType);
    }

    private void handleCruiseInfo(Intent intent, FloatingWindowManager manager) {
        int curSpeed = getIntSafe(intent, "CUR_SPEED", 0);
        String curRoadName = intent.getStringExtra("CUR_ROAD_NAME");
        int cameraSpeed = getIntSafe(intent, "CAMERA_SPEED", 0);
        int cameraDist = getIntSafe(intent, "CAMERA_DIST", 0);
        int cameraType = getIntSafe(intent, "CAMERA_TYPE", 0);
        int carDirection = getIntSafe(intent, "CAR_DIRECTION", -1);
        if (curRoadName == null) curRoadName = "未知道路";
        manager.updateCruiseInfo(curSpeed, curRoadName, cameraType, cameraSpeed, cameraDist, carDirection);
    }

    private int getIntSafe(Intent intent, String key, int defaultValue) {
        if (intent == null) return defaultValue;
        Bundle extras = intent.getExtras();
        if (extras == null || !extras.containsKey(key)) return defaultValue;
        Object val = extras.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return (int) Float.parseFloat((String) val);
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    /** 按白名单提取字段并转发给调试页面 */
    private void forwardDebug(int keyType, Intent intent) {
        BroadcastDebugListener listener = sDebugListener;
        if (listener == null) return;
        String[] whitelist;
        if (keyType == 10001) {
            whitelist = FIELDS_10001;
        } else if (keyType == 60073) {
            whitelist = FIELDS_60073;
        } else if (keyType == 13012) {
            whitelist = FIELDS_13012;
        } else {
            return;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        for (String key : whitelist) {
            if (extras.containsKey(key)) {
                Object v = extras.get(key);
                fields.put(key, v == null ? "null" : v.toString());
            }
        }
        if (!fields.isEmpty()) {
            listener.onBroadcastData(keyType, fields);
        }
    }
}
