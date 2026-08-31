package com.navi.link.window;

import com.navi.link.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 副屏自定义模块注册表：模块 ID ↔ 中文名称 ↔ 布局资源
 */
public class ModuleRegistry {

    public static class ModuleInfo {
        public final String id;       // 模块 ID
        public final String name;     // 中文名称
        public final int layoutRes;   // 模块布局资源

        public ModuleInfo(String id, String name, int layoutRes) {
            this.id = id;
            this.name = name;
            this.layoutRes = layoutRes;
        }
    }

    private static final List<ModuleInfo> MODULES = new ArrayList<>();

    static {
        // 第一阶段：从主屏窗口迁移的模块
        MODULES.add(new ModuleInfo("module_speed", "速度", R.layout.module_speed));
        MODULES.add(new ModuleInfo("module_road_name", "道路名", R.layout.module_road_name));
        MODULES.add(new ModuleInfo("module_lane_line", "车道线", R.layout.module_lane_line));
        MODULES.add(new ModuleInfo("module_traffic_light", "红绿灯", R.layout.module_traffic_light));
        MODULES.add(new ModuleInfo("module_turn_icon", "转向图标", R.layout.module_turn_icon));
        MODULES.add(new ModuleInfo("module_turn_distance", "转向距离", R.layout.module_turn_distance));
        MODULES.add(new ModuleInfo("module_tmc_progress", "TMC路况进度条", R.layout.module_tmc_progress));
        MODULES.add(new ModuleInfo("module_eta", "预计到达时间", R.layout.module_eta));
        MODULES.add(new ModuleInfo("module_exit_info", "出口信息", R.layout.module_exit_info));
        MODULES.add(new ModuleInfo("module_speed_limit", "限速", R.layout.module_speed_limit));
        MODULES.add(new ModuleInfo("module_camera_distance", "电子眼距离", R.layout.module_camera_distance));
        // 第二阶段：独立巡航模式数据模块（复用第一阶段布局，数据源为高德巡航广播）
        MODULES.add(new ModuleInfo("module_cruise_traffic_light", "巡航红绿灯", R.layout.module_traffic_light));
        MODULES.add(new ModuleInfo("module_cruise_lane_line", "巡航车道线", R.layout.module_lane_line));
        MODULES.add(new ModuleInfo("module_cruise_road_name", "巡航道路名", R.layout.module_road_name));
    }

    public static List<ModuleInfo> getAll() {
        return MODULES;
    }

    public static ModuleInfo get(String id) {
        for (ModuleInfo info : MODULES) {
            if (info.id.equals(id)) return info;
        }
        return null;
    }

    public static String getName(String id) {
        ModuleInfo info = get(id);
        return info != null ? info.name : id;
    }
}
