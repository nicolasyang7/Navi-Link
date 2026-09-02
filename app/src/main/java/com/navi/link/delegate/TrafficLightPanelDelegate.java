package com.navi.link.delegate;

import com.navi.link.R;
import com.navi.link.activity.MainActivity;
import com.navi.link.window.FloatingWindowManager;

import android.graphics.Color;
import android.widget.RadioButton;
import android.widget.TextView;
import com.navi.link.view.SwitchButton;
import com.google.android.material.card.MaterialCardView;

public class TrafficLightPanelDelegate {
    private final MainActivity activity;

    private MaterialCardView cardTrafficLightFillToggle;
    private SwitchButton cbTrafficLightFillEnabled;
    private TextView tvTrafficLightFillStatus;

    private MaterialCardView[] cardTrafficLightStyle = new MaterialCardView[7];

    private MaterialCardView cardTrafficLightCapsuleToggle;
    private SwitchButton cbTrafficLightCapsuleEnabled;
    private TextView tvTrafficLightCapsuleStatus;

    private MaterialCardView cardTrafficLightIconToggle;
    private SwitchButton cbTrafficLightIconEnabled;
    private TextView tvTrafficLightIconStatus;

    private MaterialCardView cardFontDefault, cardFontOne, cardFontTwo, cardFontThree;
    private RadioButton rbFontDefault, rbFontOne, rbFontTwo, rbFontThree;

    public TrafficLightPanelDelegate(MainActivity activity) {
        this.activity = activity;
    }

    public void initViews() {
        cardTrafficLightFillToggle = activity.findViewById(R.id.card_traffic_light_fill_toggle);
        cbTrafficLightFillEnabled = activity.findViewById(R.id.cb_traffic_light_fill_enabled);
        tvTrafficLightFillStatus = activity.findViewById(R.id.tv_traffic_light_fill_status);

        cardTrafficLightCapsuleToggle = activity.findViewById(R.id.card_traffic_light_capsule_toggle);
        cbTrafficLightCapsuleEnabled = activity.findViewById(R.id.cb_traffic_light_capsule_enabled);
        tvTrafficLightCapsuleStatus = activity.findViewById(R.id.tv_traffic_light_capsule_status);

        cardTrafficLightIconToggle = activity.findViewById(R.id.card_traffic_light_icon_toggle);
        cbTrafficLightIconEnabled = activity.findViewById(R.id.cb_traffic_light_icon_enabled);
        tvTrafficLightIconStatus = activity.findViewById(R.id.tv_traffic_light_icon_status);

        cardFontDefault = activity.findViewById(R.id.card_font_default);
        cardFontOne = activity.findViewById(R.id.card_font_one);
        cardFontTwo = activity.findViewById(R.id.card_font_two);
        cardFontThree = activity.findViewById(R.id.card_font_three);
        rbFontDefault = activity.findViewById(R.id.rb_font_default);
        rbFontOne = activity.findViewById(R.id.rb_font_one);
        rbFontTwo = activity.findViewById(R.id.rb_font_two);
        rbFontThree = activity.findViewById(R.id.rb_font_three);

        cardTrafficLightStyle[0] = activity.findViewById(R.id.card_traffic_light_style_0);
        cardTrafficLightStyle[1] = activity.findViewById(R.id.card_traffic_light_style_1);
        cardTrafficLightStyle[2] = activity.findViewById(R.id.card_traffic_light_style_2);
        cardTrafficLightStyle[3] = activity.findViewById(R.id.card_traffic_light_style_3);
        cardTrafficLightStyle[4] = activity.findViewById(R.id.card_traffic_light_style_4);
        cardTrafficLightStyle[5] = activity.findViewById(R.id.card_traffic_light_style_5);
        cardTrafficLightStyle[6] = activity.findViewById(R.id.card_traffic_light_style_6);

        setupListeners();
    }

    public void setupListeners() {
        if (cbTrafficLightFillEnabled != null) {
            cbTrafficLightFillEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                activity.isTrafficLightFillEnabled = isChecked;
                activity.savePreferences();
                if (tvTrafficLightFillStatus != null) {
                    tvTrafficLightFillStatus.setText(isChecked ? "红绿灯胶囊背景已填充灯色" : "深蓝胶囊背景");
                }
                FloatingWindowManager fwm = FloatingWindowManager.getInstance();
                if (fwm != null) {
                    fwm.refreshWindow();
                }
            });
        }
        if (cardTrafficLightFillToggle != null) {
            cardTrafficLightFillToggle.setOnClickListener(v -> {
                if (cbTrafficLightFillEnabled != null) {
                    cbTrafficLightFillEnabled.toggle();
                }
            });
        }

        if (cbTrafficLightCapsuleEnabled != null) {
            cbTrafficLightCapsuleEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                activity.isTrafficLightCapsuleEnabled = isChecked;
                activity.savePreferences();
                if (tvTrafficLightCapsuleStatus != null) {
                    tvTrafficLightCapsuleStatus.setText(isChecked ? "显示黑灰半透明胶囊框" : "隐藏胶囊框 (更干净)");
                }
                FloatingWindowManager fwm = FloatingWindowManager.getInstance();
                if (fwm != null) {
                    fwm.refreshWindow();
                }
            });
        }
        if (cardTrafficLightCapsuleToggle != null) {
            cardTrafficLightCapsuleToggle.setOnClickListener(v -> {
                if (cbTrafficLightCapsuleEnabled != null) {
                    cbTrafficLightCapsuleEnabled.toggle();
                }
            });
        }

        if (cbTrafficLightIconEnabled != null) {
            cbTrafficLightIconEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                activity.isTrafficLightIconEnabled = isChecked;
                activity.savePreferences();
                if (tvTrafficLightIconStatus != null) {
                    tvTrafficLightIconStatus.setText(isChecked ? "显示红绿灯方向图标" : "只显示数字不显示灯图");
                }
                FloatingWindowManager fwm = FloatingWindowManager.getInstance();
                if (fwm != null) {
                    fwm.refreshWindow();
                }
            });
        }
        if (cardTrafficLightIconToggle != null) {
            cardTrafficLightIconToggle.setOnClickListener(v -> {
                if (cbTrafficLightIconEnabled != null) {
                    cbTrafficLightIconEnabled.toggle();
                }
            });
        }

        if (cardFontDefault != null) cardFontDefault.setOnClickListener(v -> activity.selectCountdownFont(0));
        if (cardFontOne != null) cardFontOne.setOnClickListener(v -> activity.selectCountdownFont(1));
        if (cardFontTwo != null) cardFontTwo.setOnClickListener(v -> activity.selectCountdownFont(2));
        if (cardFontThree != null) cardFontThree.setOnClickListener(v -> activity.selectCountdownFont(3));

        for (int i = 0; i < cardTrafficLightStyle.length; i++) {
            final int styleIndex = i;
            if (cardTrafficLightStyle[i] != null) {
                cardTrafficLightStyle[i].setOnClickListener(v -> activity.selectTrafficLightStyle(styleIndex));
            }
        }
    }

    public void loadSettings() {
        if (cbTrafficLightFillEnabled != null) cbTrafficLightFillEnabled.setChecked(activity.isTrafficLightFillEnabled);
        if (tvTrafficLightFillStatus != null) {
            tvTrafficLightFillStatus.setText(activity.isTrafficLightFillEnabled ? "红绿灯胶囊背景已填充灯色" : "深蓝胶囊背景");
        }

        if (cbTrafficLightCapsuleEnabled != null) cbTrafficLightCapsuleEnabled.setChecked(activity.isTrafficLightCapsuleEnabled);
        if (tvTrafficLightCapsuleStatus != null) {
            tvTrafficLightCapsuleStatus.setText(activity.isTrafficLightCapsuleEnabled ? "显示黑灰半透明胶囊框" : "隐藏胶囊框 (更干净)");
        }

        if (cbTrafficLightIconEnabled != null) cbTrafficLightIconEnabled.setChecked(activity.isTrafficLightIconEnabled);
        if (tvTrafficLightIconStatus != null) {
            tvTrafficLightIconStatus.setText(activity.isTrafficLightIconEnabled ? "显示红绿灯方向图标" : "只显示数字不显示灯图");
        }

        updateTrafficLightStyleSelection();
        updateCountdownFontSelection();
    }

    public void updateTrafficLightStyleSelection() {
        int accentColor = activity.getAccentColor();
        int normalColor = Color.parseColor("#444444");
        for (int i = 0; i < cardTrafficLightStyle.length; i++) {
            if (cardTrafficLightStyle[i] != null) {
                cardTrafficLightStyle[i].setStrokeColor(activity.trafficLightStyle == i ? accentColor : normalColor);
            }
        }
    }

    public void updateCountdownFontSelection() {
        if (rbFontDefault != null) rbFontDefault.setChecked(activity.countdownFontIndex == 0);
        if (rbFontOne != null) rbFontOne.setChecked(activity.countdownFontIndex == 1);
        if (rbFontTwo != null) rbFontTwo.setChecked(activity.countdownFontIndex == 2);
        if (rbFontThree != null) rbFontThree.setChecked(activity.countdownFontIndex == 3);

        int accentColor = activity.getAccentColor();
        int normalColor = Color.parseColor("#444444");
        if (cardFontDefault != null) cardFontDefault.setStrokeColor(activity.countdownFontIndex == 0 ? accentColor : normalColor);
        if (cardFontOne != null) cardFontOne.setStrokeColor(activity.countdownFontIndex == 1 ? accentColor : normalColor);
        if (cardFontTwo != null) cardFontTwo.setStrokeColor(activity.countdownFontIndex == 2 ? accentColor : normalColor);
        if (cardFontThree != null) cardFontThree.setStrokeColor(activity.countdownFontIndex == 3 ? accentColor : normalColor);
    }

    public void updateThemeColors() {
        int accentColor = activity.getAccentColor();
        updateThemeColors(accentColor, android.content.res.ColorStateList.valueOf(accentColor));
    }

    public void updateThemeColors(int accentColor, android.content.res.ColorStateList accentColorStateList) {
        activity.updateSwitchTheme(cbTrafficLightFillEnabled, accentColor);
        activity.updateSwitchTheme(cbTrafficLightCapsuleEnabled, accentColor);
        activity.updateSwitchTheme(cbTrafficLightIconEnabled, accentColor);

        int normalColor = Color.parseColor("#444444");
        for (int i = 0; i < cardTrafficLightStyle.length; i++) {
            if (cardTrafficLightStyle[i] != null) {
                cardTrafficLightStyle[i].setStrokeColor(activity.trafficLightStyle == i ? accentColor : normalColor);
            }
        }

        if (cardFontDefault != null) cardFontDefault.setStrokeColor(activity.countdownFontIndex == 0 ? accentColor : normalColor);
        if (cardFontOne != null) cardFontOne.setStrokeColor(activity.countdownFontIndex == 1 ? accentColor : normalColor);
        if (cardFontTwo != null) cardFontTwo.setStrokeColor(activity.countdownFontIndex == 2 ? accentColor : normalColor);
        if (cardFontThree != null) cardFontThree.setStrokeColor(activity.countdownFontIndex == 3 ? accentColor : normalColor);

        if (rbFontDefault != null) androidx.core.widget.CompoundButtonCompat.setButtonTintList(rbFontDefault, accentColorStateList);
        if (rbFontOne != null) androidx.core.widget.CompoundButtonCompat.setButtonTintList(rbFontOne, accentColorStateList);
        if (rbFontTwo != null) androidx.core.widget.CompoundButtonCompat.setButtonTintList(rbFontTwo, accentColorStateList);
        if (rbFontThree != null) androidx.core.widget.CompoundButtonCompat.setButtonTintList(rbFontThree, accentColorStateList);
    }
}
