package com.navi.link.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.navi.link.R;
import com.navi.link.receiver.AmapNaviReceiver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * 广播调试页面：实时显示 10001 / 60073 / 13012 的白名单字段。
 * 打开时注册 AmapNaviReceiver 静态监听，关闭时注销（页面未开零开销）。
 */
public class BroadcastDebugDialog extends Dialog implements AmapNaviReceiver.BroadcastDebugListener {

    private static final int MAX_BLOCKS = 100;
    private static final int FILTER_ALL = 0, FILTER_10001 = 1, FILTER_60073 = 2, FILTER_13012 = 3;

    private LinearLayout container;
    private ScrollView scrollView;
    private int filter = FILTER_ALL;
    private int blockCount = 0;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private GradientDrawable blockBg;

    public BroadcastDebugDialog(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_broadcast_debug);

        // 全屏 + 无标题
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF14171C));
            window.setDimAmount(0f);
        }

        container = findViewById(R.id.ll_bcast_container);
        scrollView = findViewById(R.id.scroll_bcast_content);

        int radius = Math.round(10 * getContext().getResources().getDisplayMetrics().density);
        blockBg = new GradientDrawable();
        blockBg.setColor(0xFF232830);
        blockBg.setCornerRadius(radius);

        // 清空
        findViewById(R.id.btn_bcast_clear).setOnClickListener(v -> {
            container.removeAllViews();
            blockCount = 0;
        });
        // 关闭
        findViewById(R.id.btn_bcast_close).setOnClickListener(v -> dismiss());

        // 过滤 chips
        bindChip(R.id.chip_bcast_all, FILTER_ALL);
        bindChip(R.id.chip_bcast_10001, FILTER_10001);
        bindChip(R.id.chip_bcast_60073, FILTER_60073);
        bindChip(R.id.chip_bcast_13012, FILTER_13012);

        // 注册调试监听
        AmapNaviReceiver.setBroadcastDebugListener(this);
        setOnDismissListener(d -> AmapNaviReceiver.setBroadcastDebugListener(null));
    }

    private void bindChip(int id, final int filterId) {
        View chip = findViewById(id);
        chip.setOnClickListener(v -> setFilter(filterId));
        if (filterId == filter) {
            applyChipSelected(chip, true);
        }
    }

    private void setFilter(int filterId) {
        filter = filterId;
        applyChipSelected(findViewById(R.id.chip_bcast_all), filter == FILTER_ALL);
        applyChipSelected(findViewById(R.id.chip_bcast_10001), filter == FILTER_10001);
        applyChipSelected(findViewById(R.id.chip_bcast_60073), filter == FILTER_60073);
        applyChipSelected(findViewById(R.id.chip_bcast_13012), filter == FILTER_13012);
    }

    private void applyChipSelected(View chip, boolean selected) {
        if (chip == null) return;
        TextView tv = (TextView) chip;
        tv.setBackgroundResource(selected ? R.drawable.bg_bcast_chip_selected : R.drawable.bg_bcast_chip);
        tv.setTextColor(selected ? 0xFF14171C : 0xFFC9D1D9);
    }

    @Override
    public void onBroadcastData(int keyType, Map<String, String> fields) {
        if (!isShowing()) return;
        if (filter != FILTER_ALL) {
            int typeFilter = (keyType == 10001) ? FILTER_10001 : (keyType == 60073) ? FILTER_60073 : FILTER_13012;
            if (typeFilter != filter) return;
        }

        // 超上限移除最旧
        if (blockCount >= MAX_BLOCKS) {
            container.removeViewAt(0);
            blockCount--;
        }

        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackground(blockBg);
        int pad = Math.round(10 * getContext().getResources().getDisplayMetrics().density);
        block.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(8 * getContext().getResources().getDisplayMetrics().density);
        block.setLayoutParams(lp);

        // 头部：时间 + KEY_TYPE
        TextView header = new TextView(getContext());
        header.setText("[" + timeFormat.format(new Date()) + "] KEY_TYPE=" + keyType);
        header.setTextSize(13f);
        header.setTextColor(0xFF4FC3F7);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        block.addView(header);

        // 字段行
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            TextView row = new TextView(getContext());
            row.setText(entry.getKey() + " = " + entry.getValue());
            row.setTextSize(12f);
            row.setTextColor(0xFFC9D1D9);
            row.setPadding(0, Math.round(3 * getContext().getResources().getDisplayMetrics().density), 0, 0);
            block.addView(row);
        }

        container.addView(block);
        blockCount++;

        // 自动滚到底部
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
