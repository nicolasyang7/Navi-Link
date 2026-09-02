package com.navi.link.window;

import com.navi.link.R;

import java.util.ArrayList;
import java.util.List;

/**副屏自定义模块注册表：模块ID ↔ 中文名 ↔ 布局资源 */
public class ModuleRegistry {

 public static class ModuleInfo {
 public final String id;
 public final String name;
 public final int layoutRes;
 public final int iconRes;
 public ModuleInfo(String id, String name, int layoutRes, int iconRes) {
 this.id = id;
 this.name = name;
 this.layoutRes = layoutRes;
 this.iconRes = iconRes;
 }
 }

 private static final List<ModuleInfo> MODULES = new ArrayList<>();

 static {
 MODULES.add(new ModuleInfo("module_road_name", "道路名", R.layout.module_road_name, R.drawable.ic_module_road_name));
 MODULES.add(new ModuleInfo("module_lane_line", "车道线", R.layout.module_lane_line, R.drawable.ic_module_lane_line));
 MODULES.add(new ModuleInfo("module_traffic_light", "红绿灯", R.layout.module_traffic_light, R.drawable.ic_module_traffic_light));
 MODULES.add(new ModuleInfo("module_turn_icon", "转向图标", R.layout.module_turn_icon, R.drawable.ic_module_turn_icon));
 MODULES.add(new ModuleInfo("module_turn_distance", "转向距离", R.layout.module_turn_distance, R.drawable.ic_module_turn_distance));
 MODULES.add(new ModuleInfo("module_tmc_progress", "TMC路况进度条", R.layout.module_tmc_progress, R.drawable.ic_module_tmc));
 MODULES.add(new ModuleInfo("module_eta", "ETA", R.layout.module_eta, R.drawable.ic_module_eta));
 MODULES.add(new ModuleInfo("module_exit_info", "出口信息", R.layout.module_exit_info, R.drawable.ic_module_exit));
 MODULES.add(new ModuleInfo("module_speed_limit", "限速", R.layout.module_speed_limit, R.drawable.ic_module_speed_limit));
 MODULES.add(new ModuleInfo("module_camera_distance", "电子眼距离", R.layout.module_camera_distance, R.drawable.ic_module_camera));
 MODULES.add(new ModuleInfo("module_cruise_traffic_light", "巡航红绿灯", R.layout.module_traffic_light, R.drawable.ic_module_traffic_light));
 MODULES.add(new ModuleInfo("module_cruise_lane_line", "巡航车道线", R.layout.module_lane_line, R.drawable.ic_module_lane_line));
 MODULES.add(new ModuleInfo("module_cruise_road_name", "巡航道路名", R.layout.module_road_name, R.drawable.ic_module_road_name));
 }

 public static List<ModuleInfo> getAll() {
 return MODULES;
 }

 public static ModuleInfo get(String id) {
 for (ModuleInfo i : MODULES) {
 if (i.id.equals(id)) return i;
 }
 return null;
 }

 public static String getName(String id) {
 ModuleInfo i = get(id);
 return i != null ? i.name : id;
 }
}
