package dev.lolokeksu.csn.guardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal standalone DPC UI. This is NOT a VPN client or a guarantee of tunnel connectivity.
 * All policy writes require the app to be the actual profile owner in this Android user.
 */
public final class MainActivity extends Activity {
    private static final int COLOR_BG = Color.rgb(7, 9, 13);
    private static final int COLOR_TEXT = Color.rgb(233, 241, 244);
    private static final int COLOR_MUTED = Color.rgb(145, 160, 170);
    private static final int COLOR_ACCENT = Color.rgb(93, 227, 181);

    private DevicePolicyManager dpm;
    private ComponentName admin;
    private TextView statusText;
    private TextView messageText;
    private Spinner vpnChoice;
    private Button enforceButton;
    private Button emergencyButton;
    private Button provisionButton;
    private final List<String> vpnPackages = new ArrayList<>();
    private final Map<String, String> serviceLabels = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dpm = getSystemService(DevicePolicyManager.class);
        admin = GuardianAdminReceiver.component(this);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private TextView label(LinearLayout parent, String value, int size, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (strong) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private Button button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(button, lp);
        return button;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(36), dp(24), dp(36));
        scroll.addView(container);

        label(container, "CSN / GUARDIAN", 13, COLOR_ACCENT, true);
        label(container, "Контроль защищённого профиля", 25, COLOR_TEXT, true);
        label(container, "Android Device Policy Controller • без root", 13, COLOR_MUTED, false);

        statusText = label(container, "Проверка прав…", 15, COLOR_TEXT, true);
        label(container, "Доступные VPN-клиенты в текущем профиле", 14, COLOR_TEXT, true);
        vpnChoice = new Spinner(this);
        container.addView(vpnChoice, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        enforceButton = button(container, "Включить постоянный VPN + Lockdown", this::confirmEnforce);
        emergencyButton = button(container, "Аварийное отключение политики", this::confirmEmergency);
        provisionButton = button(container, "Создать защищённый рабочий профиль", this::confirmProvision);
        button(container, "Обновить состояние", this::refresh);

        messageText = label(container, "", 13, COLOR_MUTED, false);
        label(container, "Защита сети задаётся Android, а не этой кнопкой. "
                + "Настроенный Lockdown не доказывает, что выбранный VPN реально подключён. "
                + "Системный трафик может иметь исключения. "
                + "Часовой пояс устройства общий для личного и рабочего профилей.", 13, COLOR_MUTED, false);

        setContentView(scroll);
    }

    private boolean isOwner() {
        return dpm != null && dpm.isProfileOwnerApp(getPackageName());
    }

    private void findVpnServices() {
        vpnPackages.clear();
        serviceLabels.clear();
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(VpnService.SERVICE_INTERFACE);
        List<ResolveInfo> services = pm.queryIntentServices(intent, 0);
        if (services != null) {
            for (ResolveInfo entry : services) {
                if (entry.serviceInfo == null) continue;
                if (!"android.permission.BIND_VPN_SERVICE".equals(entry.serviceInfo.permission)) continue;
                String pkg = entry.serviceInfo.packageName;
                if (!serviceLabels.containsKey(pkg)) {
                    CharSequence name = entry.loadLabel(pm);
                    serviceLabels.put(pkg, (name == null ? pkg : name.toString()) + "  (" + pkg + ")");
                }
            }
        }
        vpnPackages.addAll(serviceLabels.keySet());
        Collections.sort(vpnPackages);
        List<String> choices = new ArrayList<>();
        for (String pkg : vpnPackages) choices.add(serviceLabels.get(pkg));
        if (choices.isEmpty()) choices.add("VPN-службы не обнаружены");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, choices);
        vpnChoice.setAdapter(adapter);
    }

    private void refresh() {
        findVpnServices();
        boolean owner = isOwner();
        enforceButton.setEnabled(owner && !vpnPackages.isEmpty());
        emergencyButton.setEnabled(owner);
        boolean canProvision = !owner
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
                && dpm != null
                && dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        provisionButton.setVisibility(canProvision ? View.VISIBLE : View.GONE);

        if (!owner) {
            statusText.setText("Режим просмотра — нет полномочий Profile Owner");
            messageText.setText("Текущий рабочий профиль контролируется другим администратором "
                    + "или CSN запущен в личном профиле. Нельзя применить Lockdown без статуса "
                    + "Profile Owner. Никаких изменений не выполнено. "
                    + "Не удаляйте профиль Shelter без резервной копии.");
            return;
        }
        try {
            String current = dpm.getAlwaysOnVpnPackage(admin);
            boolean lockdown = dpm.isAlwaysOnVpnLockdownEnabled(admin);
            statusText.setText("Profile Owner: CSN ✓\nПостоянный VPN: "
                    + (current == null ? "не задан" : current)
                    + "\nLockdown: " + (lockdown ? "ВКЛ" : "ВЫКЛ"));
            if (current != null && vpnPackages.contains(current)) {
                vpnChoice.setSelection(vpnPackages.indexOf(current));
            }
            messageText.setText(lockdown
                    ? "Политика Android установлена. Проверьте реальный сетевой выход и обрыв VPN."
                    : "Туннель должен быть предварительно настроен внутри VPN-клиента.");
        } catch (SecurityException ex) {
            statusText.setText("Ошибка полномочий Profile Owner");
            messageText.setText("Системная политика недоступна; операция не выполнена.");
            enforceButton.setEnabled(false);
            emergencyButton.setEnabled(false);
        }
    }

    private void confirmEnforce() {
        if (!isOwner()) return;
        int i = vpnChoice.getSelectedItemPosition();
        String selected = i >= 0 && i < vpnPackages.size() ? vpnPackages.get(i) : null;
        VpnPolicyGate.Decision gate = VpnPolicyGate.evaluate(isOwner(), selected, serviceLabels.keySet());
        if (gate != VpnPolicyGate.Decision.ALLOW) {
            showMessage("Операция запрещена: " + gate.name());
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Включить строгую защиту?")
                .setMessage("Android назначит " + selected + " постоянным VPN рабочего профиля. "
                        + "Если туннель не работает, весь доступный для политики трафик рабочих "
                        + "приложений будет заблокирован. Убедитесь, что VPN настроен и проверен.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить Lockdown", (d, w) -> applyLockdown(selected))
                .show();
    }

    private void applyLockdown(String selected) {
        if (!isOwner()) return;
        // Repeat authorization at the mutation boundary, not only in UI.
        findVpnServices();
        if (VpnPolicyGate.evaluate(isOwner(), selected, serviceLabels.keySet())
                != VpnPolicyGate.Decision.ALLOW) {
            showMessage("VPN-сервис больше не доступен. Политика не изменена.");
            return;
        }
        try {
            dpm.setAlwaysOnVpnPackage(admin, selected, true);
            String assigned = dpm.getAlwaysOnVpnPackage(admin);
            boolean lockdown = dpm.isAlwaysOnVpnLockdownEnabled(admin);
            refresh();
            if (selected.equals(assigned) && lockdown) {
                showMessage("Политика активирована и прочитана обратно. "
                        + "Сетевые проверки всё ещё обязательны.");
            } else {
                showMessage("Не удалось подтвердить Lockdown после записи.");
            }
        } catch (Exception ex) {
            refresh();
            showMessage("Android не применил политику: " + ex.getClass().getSimpleName()
                    + ". Проверьте поддержку Always-on VPN в выбранном клиенте.");
        }
    }

    private void confirmEmergency() {
        if (!isOwner()) return;
        new AlertDialog.Builder(this)
                .setTitle("Аварийно отключить защиту?")
                .setMessage("Это удалит постоянный VPN, установленный CSN, "
                        + "и может разрешить рабочим приложениям прямой интернет. "
                        + "Используйте только при необходимости восстановления.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Отключить защиту", (d, w) -> {
                    if (!isOwner()) return;
                    try {
                        dpm.setAlwaysOnVpnPackage(admin, null, false);
                        refresh();
                        showMessage("Запрос на отключение отправлен. "
                                + "Проверьте состояние политики выше.");
                    } catch (Exception ex) {
                        showMessage("Отключение не удалось: " + ex.getClass().getSimpleName());
                    }
                }).show();
    }

    private void confirmProvision() {
        if (isOwner() || dpm == null
                || !dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            showMessage("Создание профиля недоступно. Возможно, уже существует профиль Shelter.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Создать отдельный рабочий профиль?")
                .setMessage("Android откроет системный мастер создания нового рабочего профиля. "
                        + "Если уже существует профиль Shelter, не удаляйте его автоматически: "
                        + "при удалении теряются данные и VPN-настройки внутри профиля. "
                        + "Программа не переносит приложения и секреты из Shelter.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть мастер Android", (d, w) -> {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
                    intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin);
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        showMessage("Системный мастер подготовки рабочего профиля недоступен.");
                        return;
                    }
                    startActivity(intent);
                }).show();
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this)
                .setTitle("CSN Guardian")
                .setMessage(message)
                .setPositiveButton("ОК", null)
                .show();
    }
}
