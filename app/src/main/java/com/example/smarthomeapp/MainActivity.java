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
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://smarthome-bjb2avf3d9craehs.eastasia-01.azurewebsites.net";
    private static final String[] PORTIONS = {"small", "medium", "large"};
    private static final long AUTO_REFRESH_MS = 30_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Schedule> schedules = new ArrayList<>();

    private SwipeRefreshLayout refreshLayout;
    private EditText deviceCodeInput;
    private TextView baseUrlText;
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        configureControls();
        refreshAll(true);
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
        refreshLayout = findViewById(R.id.refresh);
        deviceCodeInput = findViewById(R.id.deviceCodeInput);
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PORTIONS);
        newSchedulePortionSpinner.setAdapter(adapter);
        newSchedulePortionSpinner.setSelection(1);

        findViewById(R.id.loadButton).setOnClickListener(v -> refreshAll(true));
        refreshLayout.setOnRefreshListener(() -> refreshAll(true));
        feedNowButton.setOnClickListener(v -> feedNow());
        addScheduleButton.setOnClickListener(v -> addSchedule());
        resetWifiButton.setOnClickListener(v -> confirmResetWifi());
    }

    private void refreshAll(boolean showSpinner) {
        String deviceCode = getDeviceCode();
        if (deviceCode.isEmpty()) {
            setError("Enter a device code.");
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
        String name = device.optString("device_name", "Pet feeder");
        currentStatus = device.optString("status", "unknown").toLowerCase(Locale.US);
        String lastSeen = device.optString("last_seen", "No last seen time");

        deviceNameText.setText(name + " (" + code + ")");
        deviceStatusText.setText("Status: " + labelStatus(currentStatus) + "\nLast seen: " + lastSeen);
        boolean offline = "offline".equals(currentStatus) || "error".equals(currentStatus);
        offlineWarningText.setVisibility(offline ? View.VISIBLE : View.GONE);
        offlineWarningText.setText("Warning: device is " + labelStatus(currentStatus).toLowerCase(Locale.US) + ". Commands may not run immediately.");
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
            schedulesContainer.addView(simpleText("No schedules yet.", true));
        }
    }

    private View createScheduleView(Schedule schedule) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(218, 225, 230));
        card.setRadius(dp(8));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(cardParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView title = simpleText("Schedule #" + schedule.id, false);
        title.setTextColor(Color.rgb(23, 32, 38));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        EditText timeInput = new EditText(this);
        timeInput.setSingleLine(true);
        timeInput.setText(schedule.feedTime);
        timeInput.setHint("HH:mm");
        row.addView(timeInput, new LinearLayout.LayoutParams(0, dp(48), 1));

        Spinner portionSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PORTIONS);
        portionSpinner.setAdapter(adapter);
        portionSpinner.setSelection(portionIndex(schedule.portionSize));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        spinnerParams.setMargins(dp(8), 0, 0, 0);
        row.addView(portionSpinner, spinnerParams);
        body.addView(row);

        Switch activeSwitch = new Switch(this);
        activeSwitch.setText(schedule.isActive ? "Active" : "Inactive");
        activeSwitch.setChecked(schedule.isActive);
        activeSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> activeSwitch.setText(isChecked ? "Active" : "Inactive"));
        body.addView(activeSwitch);

        TextView meta = simpleText("Created: " + schedule.createdAt + "\nUpdated: " + schedule.updatedAt, true);
        body.addView(meta);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button updateButton = new Button(this);
        updateButton.setText("Update");
        Button deleteButton = new Button(this);
        deleteButton.setText("Delete");
        actions.addView(updateButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(deleteButton, deleteParams);
        body.addView(actions);

        updateButton.setOnClickListener(v -> updateSchedule(
                schedule,
                timeInput.getText().toString().trim(),
                portionSpinner.getSelectedItem().toString(),
                activeSwitch.isChecked()
        ));
        deleteButton.setOnClickListener(v -> deleteSchedule(schedule));

        card.addView(body);
        return card;
    }

    private void renderFeedingLogs(JSONArray array) {
        feedingLogsContainer.removeAllViews();
        if (array.length() == 0) {
            feedingLogsContainer.addView(simpleText("No feeding history.", true));
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject log = array.optJSONObject(i);
            if (log == null) {
                continue;
            }
            String text = log.optString("fed_at", "") + "\n"
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
            deviceLogsContainer.addView(simpleText("No device logs.", true));
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject log = array.optJSONObject(i);
            if (log == null) {
                continue;
            }
            String text = log.optString("created_at", "") + "\n"
                    + log.optString("log_type", "info") + ": " + log.optString("message", "");
            deviceLogsContainer.addView(logText(text));
        }
    }

    private void feedNow() {
        String portion = selectedFeedPortion();
        if (portion.isEmpty()) {
            setError("Choose a portion size before feeding.");
            return;
        }
        if ("offline".equals(currentStatus)) {
            Toast.makeText(this, "Device is offline. The command may stay pending.", Toast.LENGTH_LONG).show();
        }
        setBusy(true);
        commandStatusText.setText("Sending feed command...");
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("portion_size", portion);
                JSONObject response = requestObject("POST", "/devices/" + getDeviceCode() + "/commands/feed-now", body);
                mainHandler.post(() -> {
                    commandStatusText.setText("Command #" + response.optInt("command_id") + ": " + response.optString("status", "pending"));
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
        String portion = newSchedulePortionSpinner.getSelectedItem().toString();
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
                .setTitle("Delete schedule")
                .setMessage("Delete schedule #" + schedule.id + "? Feeding history will stay available.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
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
        String warning = "Reset Wi-Fi will disconnect the device from the current network. You may need to reconnect to the device hotspot to configure new Wi-Fi.";
        if ("offline".equals(currentStatus)) {
            warning += "\n\nThe device is offline, so this command may not be processed immediately.";
        }
        new AlertDialog.Builder(this)
                .setTitle("Reset Wi-Fi")
                .setMessage(warning)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> resetWifi())
                .show();
    }

    private void resetWifi() {
        setBusy(true);
        commandStatusText.setText("Sending reset Wi-Fi command...");
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("confirm", true);
                JSONObject response = requestObject("POST", "/devices/" + getDeviceCode() + "/commands/reset-wifi", body);
                mainHandler.post(() -> {
                    commandStatusText.setText("Reset Wi-Fi command #" + response.optInt("command_id") + ": " + response.optString("status", "pending"));
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
            setError("Feed time is required in HH:mm format.");
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
                setError("Schedules must be at least 5 minutes apart.");
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
        textView.setTextColor(muted ? Color.rgb(98, 113, 122) : Color.rgb(23, 32, 38));
        textView.setPadding(0, dp(4), 0, dp(4));
        return textView;
    }

    private TextView logText(String text) {
        TextView textView = simpleText(text, false);
        textView.setBackgroundColor(Color.WHITE);
        textView.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
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
        return deviceCodeInput.getText().toString().trim().toUpperCase(Locale.US);
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
                return "Online";
            case "offline":
                return "Offline";
            case "feeding":
                return "Feeding";
            case "error":
                return "Error";
            default:
                return "Unknown";
        }
    }

    private String labelPortion(String portion) {
        switch (portion) {
            case "small":
                return "Small";
            case "medium":
                return "Medium";
            case "large":
                return "Large";
            default:
                return portion;
        }
    }

    private String labelFeedType(String type) {
        if ("manual".equals(type)) {
            return "Manual";
        }
        if ("scheduled".equals(type)) {
            return "Scheduled";
        }
        return type;
    }

    private String labelResult(String status) {
        if ("success".equals(status)) {
            return "Success";
        }
        if ("failed".equals(status)) {
            return "Failed";
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
