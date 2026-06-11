package com.example.smarthomeapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.view.Gravity;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.text.ParseException;

public class MainActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://smarthome-bjb2avf3d9craehs.eastasia-01.azurewebsites.net/api/v1";
    private static final String[] PORTIONS = {"small", "medium", "large"};
    private static final long AUTO_REFRESH_MS = 30_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Schedule> schedules = new ArrayList<>();

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_FEED_NOW = 1;
    private static final int SCREEN_SCHEDULES = 2;
    private static final int SCREEN_FEEDING_LOGS = 3;
    private static final int SCREEN_DEVICE_LOGS = 4;
    private static final int SCREEN_WIFI = 5;

    private int currentScreen = SCREEN_HOME;

    private View screenHome;
    private View screenFeedNow;
    private View screenSchedules;
    private View screenFeedingLogs;
    private View screenDeviceLogs;
    private View screenWifi;
    private TextView wifiWarningText;

    private DrawerLayout drawerLayout;
    private android.widget.ImageView menuButton;
    private TextView toolbarTitle;
    private LinearLayout devicesListContainer;
    private Button addDeviceButton;
    private SwipeRefreshLayout refreshLayout;
    private TextView baseUrlText;

    private final List<Device> devices = new ArrayList<>();
    private int activeDeviceIndex = 0;
    private TextView deviceNameText;
    private TextView deviceStatusText;
    private TextView offlineWarningText;
    private TextView commandStatusText;
    private TextView errorText;
    private RadioGroup feedPortionGroup;
    private EditText newScheduleTimeInput;
    private Spinner newSchedulePortionSpinner;
    private LinearLayout schedulesContainer;
    private LinearLayout feedingLogsContainer;
    private LinearLayout deviceLogsContainer;
    private Button feedNowButton;
    private Button addScheduleButton;
    private Button resetWifiButton;

    private String currentStatus = "";

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            refreshAll(false);
            mainHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bindViews();
        configureControls();
        refreshAll(true);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen != SCREEN_HOME) {
                    showScreen(SCREEN_HOME);
                } else if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.closeDrawer(Gravity.LEFT);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.postDelayed(autoRefresh, AUTO_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(autoRefresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void bindViews() {
        screenHome = findViewById(R.id.screenHome);
        screenFeedNow = findViewById(R.id.screenFeedNow);
        screenSchedules = findViewById(R.id.screenSchedules);
        screenFeedingLogs = findViewById(R.id.screenFeedingLogs);
        screenDeviceLogs = findViewById(R.id.screenDeviceLogs);
        screenWifi = findViewById(R.id.screenWifi);
        wifiWarningText = findViewById(R.id.wifiWarningText);

        drawerLayout = findViewById(R.id.drawerLayout);
        menuButton = findViewById(R.id.menuButton);
        toolbarTitle = findViewById(R.id.toolbarTitle);
        devicesListContainer = findViewById(R.id.devicesListContainer);
        addDeviceButton = findViewById(R.id.addDeviceButton);
        refreshLayout = findViewById(R.id.refresh);
        baseUrlText = findViewById(R.id.baseUrlText);
        deviceNameText = findViewById(R.id.deviceNameText);
        deviceStatusText = findViewById(R.id.deviceStatusText);
        offlineWarningText = findViewById(R.id.offlineWarningText);
        commandStatusText = findViewById(R.id.commandStatusText);
        errorText = findViewById(R.id.errorText);
        feedPortionGroup = findViewById(R.id.feedPortionGroup);
        newScheduleTimeInput = findViewById(R.id.newScheduleTimeInput);
        newSchedulePortionSpinner = findViewById(R.id.newSchedulePortionSpinner);
        schedulesContainer = findViewById(R.id.schedulesContainer);
        feedingLogsContainer = findViewById(R.id.feedingLogsContainer);
        deviceLogsContainer = findViewById(R.id.deviceLogsContainer);
        feedNowButton = findViewById(R.id.feedNowButton);
        addScheduleButton = findViewById(R.id.addScheduleButton);
        resetWifiButton = findViewById(R.id.resetWifiButton);
    }

    private void configureControls() {
        baseUrlText.setText(BASE_URL);
        String[] displayPortions = {
            getString(R.string.portion_small),
            getString(R.string.portion_medium),
            getString(R.string.portion_large)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, displayPortions);
        newSchedulePortionSpinner.setAdapter(adapter);
        newSchedulePortionSpinner.setSelection(1);

        newScheduleTimeInput.setFocusableInTouchMode(false);
        newScheduleTimeInput.setClickable(true);
        newScheduleTimeInput.setOnClickListener(v -> showTimePickerDialog(newScheduleTimeInput, null));

        menuButton.setOnClickListener(v -> {
            if (currentScreen != SCREEN_HOME) {
                showScreen(SCREEN_HOME);
            } else {
                if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    drawerLayout.closeDrawer(Gravity.LEFT);
                } else {
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            }
        });
        refreshLayout.setOnRefreshListener(() -> refreshAll(true));
        feedNowButton.setOnClickListener(v -> feedNow());
        addScheduleButton.setOnClickListener(v -> addSchedule());
        resetWifiButton.setOnClickListener(v -> confirmResetWifi());
        addDeviceButton.setOnClickListener(v -> showAddDeviceDialog());

        findViewById(R.id.tileFeed).setOnClickListener(v -> showScreen(SCREEN_FEED_NOW));
        findViewById(R.id.tileSchedules).setOnClickListener(v -> showScreen(SCREEN_SCHEDULES));
        findViewById(R.id.tileFeedingLogs).setOnClickListener(v -> showScreen(SCREEN_FEEDING_LOGS));
        findViewById(R.id.tileDeviceLogs).setOnClickListener(v -> showScreen(SCREEN_DEVICE_LOGS));
        findViewById(R.id.tileWifi).setOnClickListener(v -> showScreen(SCREEN_WIFI));

        loadDevices();
        renderSidebar();
        if (devices.isEmpty()) {
            mainHandler.post(this::showAddDeviceDialog);
        }
    }

    private void refreshAll(boolean showSpinner) {
        String deviceCode = getDeviceCode();
        if (deviceCode.isEmpty()) {
            setError(getString(R.string.err_enter_device_code));
            refreshLayout.setRefreshing(false);
            return;
        }
        if (showSpinner) {
            refreshLayout.setRefreshing(true);
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject device = requestObject("GET", "/devices/" + deviceCode, null);
                JSONArray scheduleArray = requestArray("GET", "/devices/" + deviceCode + "/schedules", null);
                JSONArray feedingLogs = requestArray("GET", "/devices/" + deviceCode + "/feeding-logs?limit=20", null);
                JSONArray deviceLogs = requestArray("GET", "/devices/" + deviceCode + "/device-logs?limit=50", null);
                mainHandler.post(() -> {
                    setError("");
                    renderDevice(device);
                    renderSchedules(scheduleArray);
                    renderFeedingLogs(feedingLogs);
                    renderDeviceLogs(deviceLogs);
                    setBusy(false);
                    refreshLayout.setRefreshing(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setError(e.getMessage());
                    setBusy(false);
                    refreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void renderDevice(JSONObject device) {
        String code = device.optString("device_code", getDeviceCode());
        String name = device.optString("device_name", getString(R.string.default_device_name));
        currentStatus = device.optString("status", "unknown").toLowerCase(Locale.US);
        String lastSeen = formatDateTime(device.optString("last_seen", ""));
        if (lastSeen.isEmpty()) lastSeen = getString(R.string.no_last_seen);

        deviceNameText.setText(name + " (" + code + ")");
        deviceStatusText.setText(getString(R.string.device_status_fmt, labelStatus(currentStatus), lastSeen));
        boolean offline = "offline".equals(currentStatus) || "error".equals(currentStatus);
        offlineWarningText.setVisibility(offline ? View.VISIBLE : View.GONE);
        offlineWarningText.setText(getString(R.string.offline_warning_fmt, labelStatus(currentStatus).toLowerCase(Locale.US)));
    }

    private void renderSchedules(JSONArray array) {
        schedules.clear();
        schedulesContainer.removeAllViews();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Schedule schedule = Schedule.fromJson(item);
            schedules.add(schedule);
            schedulesContainer.addView(createScheduleView(schedule));
        }
        if (schedules.isEmpty()) {
            schedulesContainer.addView(simpleText(getString(R.string.no_schedules), true));
        }
    }

    private View createScheduleView(Schedule schedule) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(226, 232, 240)); // Slate-200
        card.setRadius(dp(16));
        card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(14), 0, 0);
        card.setLayoutParams(cardParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Top Row: Time Text and Switch
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText timeInput = new EditText(this);
        timeInput.setFocusableInTouchMode(false);
        timeInput.setClickable(true);
        timeInput.setSingleLine(true);
        timeInput.setText(schedule.feedTime);
        timeInput.setTextSize(26);
        timeInput.setTypeface(null, android.graphics.Typeface.BOLD);
        timeInput.setTextColor(Color.rgb(15, 23, 42)); // Slate-900
        timeInput.setBackground(null); // Borderless
        timeInput.setPadding(0, 0, 0, 0);
        topRow.addView(timeInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch activeSwitch = new Switch(this);
        activeSwitch.setChecked(schedule.isActive);
        topRow.addView(activeSwitch, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(topRow);

        // Middle Row: Portion Badge and Spinner
        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        middleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        middleRow.setPadding(0, dp(8), 0, 0);

        TextView portionBadge = new TextView(this);
        portionBadge.setText("Khẩu phần: " + labelPortion(schedule.portionSize));
        portionBadge.setTextSize(12);
        portionBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        portionBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setCornerRadius(dp(6));
        if ("small".equals(schedule.portionSize)) {
            badgeBg.setColor(Color.rgb(239, 246, 255)); // Blue-50
            portionBadge.setTextColor(Color.rgb(29, 78, 216)); // Blue-700
        } else if ("medium".equals(schedule.portionSize)) {
            badgeBg.setColor(Color.rgb(255, 247, 237)); // Orange-50
            portionBadge.setTextColor(Color.rgb(194, 65, 12)); // Orange-700
        } else {
            badgeBg.setColor(Color.rgb(250, 245, 255)); // Purple-50
            portionBadge.setTextColor(Color.rgb(126, 34, 206)); // Purple-700
        }
        portionBadge.setBackground(badgeBg);
        middleRow.addView(portionBadge);

        // Spinner to change portion
        Spinner portionSpinner = new Spinner(this);
        String[] displayPortions = {
            getString(R.string.portion_small),
            getString(R.string.portion_medium),
            getString(R.string.portion_large)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, displayPortions);
        portionSpinner.setAdapter(adapter);
        portionSpinner.setSelection(portionIndex(schedule.portionSize));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        spinnerParams.setMargins(dp(8), 0, 0, 0);
        middleRow.addView(portionSpinner, spinnerParams);
        body.addView(middleRow);

        // Meta Info
        TextView meta = simpleText(getString(R.string.schedule_meta_fmt,
                formatDateTime(schedule.createdAt), formatDateTime(schedule.updatedAt)), true);
        meta.setTextSize(11);
        meta.setPadding(0, dp(8), 0, 0);
        body.addView(meta);

        // Bottom Row: Action Buttons
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(android.view.Gravity.END);
        actionsRow.setPadding(0, dp(12), 0, 0);

        Button deleteButton = new Button(this);
        deleteButton.setText("✕ Xóa");
        deleteButton.setTextSize(13);
        deleteButton.setAllCaps(false);
        android.graphics.drawable.GradientDrawable deleteBg = new android.graphics.drawable.GradientDrawable();
        deleteBg.setCornerRadius(dp(8));
        deleteBg.setColor(Color.rgb(239, 68, 68)); // Rose-500
        deleteButton.setBackground(deleteBg);
        deleteButton.setTextColor(Color.WHITE);
        
        Button updateButton = new Button(this);
        updateButton.setText("Lưu");
        updateButton.setTextSize(13);
        updateButton.setAllCaps(false);
        updateButton.setEnabled(false);
        android.graphics.drawable.GradientDrawable updateBg = new android.graphics.drawable.GradientDrawable();
        updateBg.setCornerRadius(dp(8));
        updateBg.setColor(Color.rgb(241, 245, 249)); // Slate-100
        updateButton.setBackground(updateBg);
        updateButton.setTextColor(Color.rgb(148, 163, 184)); // Slate-400

        LinearLayout.LayoutParams deleteBtnParams = new LinearLayout.LayoutParams(dp(80), dp(36));
        actionsRow.addView(deleteButton, deleteBtnParams);

        LinearLayout.LayoutParams updateBtnParams = new LinearLayout.LayoutParams(dp(80), dp(36));
        updateBtnParams.setMargins(dp(8), 0, 0, 0);
        actionsRow.addView(updateButton, updateBtnParams);
        body.addView(actionsRow);

        // Change detection: compare current field values vs saved schedule
        Runnable checkDirty = () -> {
            boolean timeChanged = !timeInput.getText().toString().trim().equals(schedule.feedTime);
            boolean portionChanged = portionIndex(PORTIONS[portionSpinner.getSelectedItemPosition()]) != portionIndex(schedule.portionSize);
            boolean activeChanged = activeSwitch.isChecked() != schedule.isActive;
            boolean dirty = timeChanged || portionChanged || activeChanged;
            updateButton.setEnabled(dirty);
            
            android.graphics.drawable.GradientDrawable uBg = new android.graphics.drawable.GradientDrawable();
            uBg.setCornerRadius(dp(8));
            if (dirty) {
                uBg.setColor(Color.rgb(79, 70, 229)); // Indigo-600
                updateButton.setBackground(uBg);
                updateButton.setTextColor(Color.WHITE);
            } else {
                uBg.setColor(Color.rgb(241, 245, 249)); // Slate-100
                updateButton.setBackground(uBg);
                updateButton.setTextColor(Color.rgb(148, 163, 184)); // Slate-400
            }
        };

        // Hook time picker — run checkDirty after user confirms
        timeInput.setOnClickListener(v -> showTimePickerDialog(timeInput, checkDirty));

        // Hook spinner
        final boolean[] firstSpinnerCall = {true};
        portionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (firstSpinnerCall[0]) { firstSpinnerCall[0] = false; return; }
                checkDirty.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Hook switch
        activeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkDirty.run();
        });

        updateButton.setOnClickListener(v -> updateSchedule(
                schedule,
                timeInput.getText().toString().trim(),
                PORTIONS[portionSpinner.getSelectedItemPosition()],
                activeSwitch.isChecked()
        ));
        deleteButton.setOnClickListener(v -> deleteSchedule(schedule));

        card.addView(body);
        return card;
    }

    private void showTimePickerDialog(EditText editText, Runnable onPicked) {
        int initHour = 18;
        int initMinute = 0;
        String currentText = editText.getText().toString().trim();
        if (currentText.contains(":")) {
            try {
                String[] parts = currentText.split(":");
                initHour = Integer.parseInt(parts[0]);
                initMinute = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Use default
            }
        } else {
            java.util.Calendar c = java.util.Calendar.getInstance();
            initHour = c.get(java.util.Calendar.HOUR_OF_DAY);
            initMinute = c.get(java.util.Calendar.MINUTE);
        }

        // Track scrolled values reliably via listener (getValue() alone can miss in-flight scroll)
        final int[] selectedHour = {initHour};
        final int[] selectedMinute = {initMinute};

        // Build the scroll-wheel container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        // Hour picker
        NumberPicker hourPicker = new NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(initHour);
        hourPicker.setFormatter(value -> String.format(java.util.Locale.US, "%02d", value));
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> selectedHour[0] = newVal);

        // Separator label
        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextSize(28);
        colon.setTypeface(null, android.graphics.Typeface.BOLD);
        colon.setTextColor(Color.rgb(15, 23, 42));
        colon.setPadding(dp(12), 0, dp(12), 0);
        colon.setGravity(android.view.Gravity.CENTER);

        // Minute picker
        NumberPicker minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(initMinute);
        minutePicker.setFormatter(value -> String.format(java.util.Locale.US, "%02d", value));
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> selectedMinute[0] = newVal);

        container.addView(hourPicker);
        container.addView(colon);
        container.addView(minutePicker);

        new AlertDialog.Builder(this)
                .setTitle("Ch\u1ecdn gi\u1edd")
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    String timeStr = String.format(java.util.Locale.US, "%02d:%02d",
                            selectedHour[0], selectedMinute[0]);
                    editText.setText(timeStr);
                    editText.invalidate();
                    if (onPicked != null) onPicked.run();
                })
                .setNegativeButton("H\u1ee7y", null)
                .show();
    }

    private void renderFeedingLogs(JSONArray array) {
        feedingLogsContainer.removeAllViews();
        if (array.length() == 0) {
            feedingLogsContainer.addView(simpleText(getString(R.string.no_feeding_history), true));
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject log = array.optJSONObject(i);
            if (log == null) {
                continue;
            }
            String text = formatDateTime(log.optString("fed_at", "")) + "\n"
                    + labelFeedType(log.optString("feed_type", "")) + " | "
                    + labelPortion(log.optString("portion_size", "")) + " | "
                    + labelResult(log.optString("status", "")) + "\n"
                    + log.optString("message", "");
            feedingLogsContainer.addView(logText(text));
        }
    }

    private void renderDeviceLogs(JSONArray array) {
        deviceLogsContainer.removeAllViews();
        if (array.length() == 0) {
            deviceLogsContainer.addView(simpleText(getString(R.string.no_device_logs), true));
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject log = array.optJSONObject(i);
            if (log == null) {
                continue;
            }
            String text = formatDateTime(log.optString("created_at", "")) + "\n"
                    + log.optString("log_type", "info") + ": " + log.optString("message", "");
            deviceLogsContainer.addView(logText(text));
        }
    }

    /** Parse ISO-8601 UTC timestamp and return "HH:mm, dd/MM/yyyy" in local time. */
    private String formatDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            // Handle optional fractional seconds and Z suffix
            String clean = iso.replaceAll("\\.\\d+", "").replace("Z", "");
            java.util.Date date = parser.parse(clean);
            if (date == null) return iso;
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm, dd/MM/yyyy", new Locale("vi", "VN"));
            formatter.setTimeZone(TimeZone.getDefault());
            return formatter.format(date);
        } catch (ParseException e) {
            return iso; // Return raw if parsing fails
        }
    }

    private void feedNow() {
        String portion = selectedFeedPortion();
        if (portion.isEmpty()) {
            setError(getString(R.string.err_choose_portion));
            return;
        }
        if ("offline".equals(currentStatus)) {
            Toast.makeText(this, getString(R.string.warn_device_offline), Toast.LENGTH_LONG).show();
        }
        setBusy(true);
        commandStatusText.setText(getString(R.string.status_sending_feed));
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("portion_size", portion);
                JSONObject response = requestObject("POST", "/devices/" + getDeviceCode() + "/commands/feed-now", body);
                mainHandler.post(() -> {
                    commandStatusText.setText(getString(R.string.command_status_fmt, response.optInt("command_id"), response.optString("status", "pending")));
                    setError("");
                    setBusy(false);
                    refreshAll(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    commandStatusText.setText("");
                    setError(e.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private void addSchedule() {
        String time = newScheduleTimeInput.getText().toString().trim();
        String portion = PORTIONS[newSchedulePortionSpinner.getSelectedItemPosition()];
        if (!validateScheduleInput(0, time, true)) {
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("feed_time", time)
                        .put("portion_size", portion);
                requestObject("POST", "/devices/" + getDeviceCode() + "/schedules", body);
                mainHandler.post(() -> {
                    newScheduleTimeInput.setText("");
                    setError("");
                    setBusy(false);
                    refreshAll(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setError(e.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private void updateSchedule(Schedule schedule, String time, String portion, boolean active) {
        if (!validateScheduleInput(schedule.id, time, active)) {
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("feed_time", time)
                        .put("portion_size", portion)
                        .put("is_active", active);
                requestObject("PATCH", "/devices/" + getDeviceCode() + "/schedules/" + schedule.id, body);
                mainHandler.post(() -> {
                    setError("");
                    setBusy(false);
                    refreshAll(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setError(e.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private void deleteSchedule(Schedule schedule) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_delete))
                .setMessage(getString(R.string.dialog_msg_delete, schedule.id))
                .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
                .setPositiveButton(getString(R.string.dialog_btn_delete), (dialog, which) -> {
                    setBusy(true);
                    executor.execute(() -> {
                        try {
                            requestObject("DELETE", "/devices/" + getDeviceCode() + "/schedules/" + schedule.id, null);
                            mainHandler.post(() -> {
                                setError("");
                                setBusy(false);
                                refreshAll(true);
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                setError(e.getMessage());
                                setBusy(false);
                            });
                        }
                    });
                })
                .show();
    }

    private void confirmResetWifi() {
        String warning = getString(R.string.dialog_msg_reset_wifi_warn);
        if ("offline".equals(currentStatus)) {
            warning += getString(R.string.dialog_msg_reset_wifi_offline);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_reset_wifi))
                .setMessage(warning)
                .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
                .setPositiveButton(getString(R.string.dialog_btn_reset), (dialog, which) -> resetWifi())
                .show();
    }

    private void resetWifi() {
        setBusy(true);
        commandStatusText.setText(getString(R.string.status_sending_reset));
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("confirm", true);
                JSONObject response = requestObject("POST", "/devices/" + getDeviceCode() + "/commands/reset-wifi", body);
                mainHandler.post(() -> {
                    commandStatusText.setText(getString(R.string.reset_status_fmt, response.optInt("command_id"), response.optString("status", "pending")));
                    setError("");
                    setBusy(false);
                    refreshAll(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setError(e.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private boolean validateScheduleInput(long editingId, String time, boolean willBeActive) {
        int minutes = parseTimeMinutes(time);
        if (minutes < 0) {
            setError(getString(R.string.err_invalid_time_fmt));
            return false;
        }
        if (!willBeActive) {
            setError("");
            return true;
        }
        for (Schedule existing : schedules) {
            if (existing.id == editingId || !existing.isActive) {
                continue;
            }
            int other = parseTimeMinutes(existing.feedTime);
            if (other >= 0 && Math.abs(minutes - other) < 5) {
                setError(getString(R.string.err_schedule_conflict));
                return false;
            }
        }
        setError("");
        return true;
    }

    private JSONObject requestObject(String method, String path, JSONObject body) throws IOException, JSONException {
        String response = request(method, path, body);
        if (response.isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(response);
    }

    private JSONArray requestArray(String method, String path, JSONObject body) throws IOException, JSONException {
        String response = request(method, path, body);
        if (response.isEmpty()) {
            return new JSONArray();
        }
        return new JSONArray(response);
    }

    private String request(String method, String path, JSONObject body) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        String response = readStream(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code >= 400) {
            throw new IOException(errorMessage(response, code));
        }
        return response;
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String errorMessage(String response, int code) {
        if (response == null || response.isEmpty()) {
            return "API error " + code;
        }
        try {
            JSONObject object = new JSONObject(response);
            if (object.has("error")) {
                return object.optString("error");
            }
            if (object.has("message")) {
                return object.optString("message");
            }
        } catch (JSONException ignored) {
            return response;
        }
        return response;
    }

    private TextView simpleText(String text, boolean muted) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(muted ? Color.rgb(100, 116, 139) : Color.rgb(15, 23, 42));
        textView.setPadding(0, dp(4), 0, dp(4));
        return textView;
    }

    private TextView logText(String text) {
        TextView textView = simpleText(text, false);
        textView.setBackgroundResource(R.drawable.log_item_background);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        textView.setLayoutParams(params);
        return textView;
    }

    private void setBusy(boolean busy) {
        feedNowButton.setEnabled(!busy);
        addScheduleButton.setEnabled(!busy);
        resetWifiButton.setEnabled(!busy);
    }

    private void setError(String message) {
        errorText.setText(message == null ? "" : message);
    }

    private String getDeviceCode() {
        if (devices.isEmpty() || activeDeviceIndex >= devices.size()) {
            return "";
        }
        return devices.get(activeDeviceIndex).code;
    }

    private void showScreen(int screen) {
        currentScreen = screen;
        
        screenHome.setVisibility(View.GONE);
        screenFeedNow.setVisibility(View.GONE);
        screenSchedules.setVisibility(View.GONE);
        screenFeedingLogs.setVisibility(View.GONE);
        screenDeviceLogs.setVisibility(View.GONE);
        screenWifi.setVisibility(View.GONE);

        setError("");

        switch (screen) {
            case SCREEN_HOME:
                screenHome.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_menu);
                if (!devices.isEmpty()) {
                    toolbarTitle.setText(devices.get(activeDeviceIndex).name);
                } else {
                    toolbarTitle.setText(R.string.app_name);
                }
                break;
            case SCREEN_FEED_NOW:
                screenFeedNow.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_back);
                toolbarTitle.setText(R.string.title_feed_now);
                break;
            case SCREEN_SCHEDULES:
                screenSchedules.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_back);
                toolbarTitle.setText(R.string.title_schedules);
                break;
            case SCREEN_FEEDING_LOGS:
                screenFeedingLogs.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_back);
                toolbarTitle.setText(R.string.title_feeding_history);
                break;
            case SCREEN_DEVICE_LOGS:
                screenDeviceLogs.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_back);
                toolbarTitle.setText(R.string.title_device_logs);
                break;
            case SCREEN_WIFI:
                screenWifi.setVisibility(View.VISIBLE);
                menuButton.setImageResource(R.drawable.ic_back);
                toolbarTitle.setText(R.string.btn_reset_wifi);
                String warn = getString(R.string.dialog_msg_reset_wifi_warn);
                if ("offline".equals(currentStatus)) {
                    warn += getString(R.string.dialog_msg_reset_wifi_offline);
                }
                wifiWarningText.setText(warn);
                break;
        }
    }

    private void loadDevices() {
        devices.clear();
        SharedPreferences prefs = getSharedPreferences("SmartHomeAppPrefs", MODE_PRIVATE);
        String json = prefs.getString("devices", null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    devices.add(Device.fromJson(array.getJSONObject(i)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // Do not add default device PF001 on empty load. Keep it empty to trigger "Add Device" on startup.
        activeDeviceIndex = prefs.getInt("active_device_index", 0);
        if (activeDeviceIndex >= devices.size()) {
            activeDeviceIndex = 0;
        }
    }

    private void saveDevices() {
        SharedPreferences prefs = getSharedPreferences("SmartHomeAppPrefs", MODE_PRIVATE);
        JSONArray array = new JSONArray();
        try {
            for (Device dev : devices) {
                array.put(dev.toJson());
            }
            prefs.edit()
                 .putString("devices", array.toString())
                 .putInt("active_device_index", activeDeviceIndex)
                 .apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void renderSidebar() {
        devicesListContainer.removeAllViews();
        for (int i = 0; i < devices.size(); i++) {
            final int index = i;
            Device dev = devices.get(i);

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(dp(16), dp(12), dp(16), dp(12));
            
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(dp(8), dp(4), dp(8), dp(4));
            itemLayout.setLayoutParams(itemParams);

            if (i == activeDeviceIndex) {
                itemLayout.setBackgroundResource(R.drawable.active_item_background);
            } else {
                itemLayout.setBackgroundResource(android.R.drawable.list_selector_background);
            }

            TextView infoText = new TextView(this);
            infoText.setText(dev.name + " (" + dev.code + ")");
            infoText.setTextColor(Color.rgb(15, 23, 42));
            infoText.setTextSize(16);
            if (i == activeDeviceIndex) {
                infoText.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            infoText.setLayoutParams(textParams);
            itemLayout.addView(infoText);

            TextView deleteBtn = new TextView(this);
            deleteBtn.setText("✕");
            deleteBtn.setTextSize(18);
            deleteBtn.setTextColor(Color.rgb(239, 68, 68));
            deleteBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            deleteBtn.setOnClickListener(v -> confirmRemoveDevice(index));
            itemLayout.addView(deleteBtn);

            itemLayout.setOnClickListener(v -> {
                activeDeviceIndex = index;
                saveDevices();
                renderSidebar();
                drawerLayout.closeDrawer(Gravity.LEFT);
                showScreen(SCREEN_HOME);
                refreshAll(true);
            });

            devicesListContainer.addView(itemLayout);
        }

        if (!devices.isEmpty()) {
            Device active = devices.get(activeDeviceIndex);
            toolbarTitle.setText(active.name);
        }
    }

    private void confirmRemoveDevice(int index) {
        Device dev = devices.get(index);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_remove_device))
                .setMessage(getString(R.string.dialog_msg_remove_device, dev.name, dev.code))
                .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
                .setPositiveButton(getString(R.string.btn_delete), (dialog, which) -> {
                    devices.remove(index);
                    if (activeDeviceIndex >= devices.size()) {
                        activeDeviceIndex = Math.max(0, devices.size() - 1);
                    }
                    if (devices.isEmpty()) {
                        activeDeviceIndex = 0;
                        saveDevices();
                        renderSidebar();
                        refreshAll(true);
                        showAddDeviceDialog();
                    } else {
                        saveDevices();
                        renderSidebar();
                        refreshAll(true);
                    }
                })
                .show();
    }

    private void showAddDeviceDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));

        final EditText codeInput = new EditText(this);
        codeInput.setHint(getString(R.string.hint_device_code));
        codeInput.setSingleLine(true);
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        layout.addView(codeInput);

        final EditText nameInput = new EditText(this);
        nameInput.setHint(getString(R.string.hint_device_name));
        nameInput.setSingleLine(true);
        nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, dp(12), 0, 0);
        nameInput.setLayoutParams(nameParams);
        layout.addView(nameInput);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_add_device))
                .setView(layout)
                .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
                .setPositiveButton(getString(R.string.dialog_btn_add), (dialog, which) -> {
                    String code = codeInput.getText().toString().trim().toUpperCase(Locale.US);
                    String name = nameInput.getText().toString().trim();
                    if (code.isEmpty()) {
                        Toast.makeText(this, getString(R.string.err_code_required), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (name.isEmpty()) {
                        name = getString(R.string.default_device_name);
                    }
                    Device dev = new Device(code, name);
                    devices.add(dev);
                    activeDeviceIndex = devices.size() - 1;
                    saveDevices();
                    renderSidebar();
                    drawerLayout.closeDrawer(Gravity.LEFT);
                    refreshAll(true);
                })
                .show();
    }

    private static class Device {
        final String code;
        final String name;

        Device(String code, String name) {
            this.code = code;
            this.name = name;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("code", code).put("name", name);
        }

        static Device fromJson(JSONObject json) {
            return new Device(json.optString("code"), json.optString("name"));
        }
    }

    private String selectedFeedPortion() {
        int selected = feedPortionGroup.getCheckedRadioButtonId();
        if (selected == R.id.feedSmall) {
            return "small";
        }
        if (selected == R.id.feedMedium) {
            return "medium";
        }
        if (selected == R.id.feedLarge) {
            return "large";
        }
        return "";
    }

    private int parseTimeMinutes(String value) {
        if (value == null || !value.matches("^\\d{2}:\\d{2}$")) {
            return -1;
        }
        int hour = Integer.parseInt(value.substring(0, 2));
        int minute = Integer.parseInt(value.substring(3, 5));
        if (hour > 23 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private int portionIndex(String portion) {
        for (int i = 0; i < PORTIONS.length; i++) {
            if (PORTIONS[i].equals(portion)) {
                return i;
            }
        }
        return 1;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String labelStatus(String status) {
        switch (status) {
            case "online":
                return getString(R.string.status_online);
            case "offline":
                return getString(R.string.status_offline);
            case "feeding":
                return getString(R.string.status_feeding);
            case "error":
                return getString(R.string.status_error);
            default:
                return getString(R.string.status_unknown);
        }
    }

    private String labelPortion(String portion) {
        switch (portion) {
            case "small":
                return getString(R.string.portion_small);
            case "medium":
                return getString(R.string.portion_medium);
            case "large":
                return getString(R.string.portion_large);
            default:
                return portion;
        }
    }

    private String labelFeedType(String type) {
        if ("manual".equals(type)) {
            return getString(R.string.type_manual);
        }
        if ("scheduled".equals(type)) {
            return getString(R.string.type_scheduled);
        }
        return type;
    }

    private String labelResult(String status) {
        if ("success".equals(status)) {
            return getString(R.string.result_success);
        }
        if ("failed".equals(status)) {
            return getString(R.string.result_failed);
        }
        return status;
    }

    private static final class Schedule {
        final long id;
        final String feedTime;
        final String portionSize;
        final boolean isActive;
        final String createdAt;
        final String updatedAt;

        Schedule(long id, String feedTime, String portionSize, boolean isActive, String createdAt, String updatedAt) {
            this.id = id;
            this.feedTime = feedTime;
            this.portionSize = portionSize;
            this.isActive = isActive;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        static Schedule fromJson(JSONObject object) {
            return new Schedule(
                    object.optLong("schedule_id"),
                    object.optString("feed_time"),
                    object.optString("portion_size", "medium"),
                    object.optBoolean("is_active", true),
                    object.optString("created_at", ""),
                    object.optString("updated_at", "")
            );
        }
    }
}
