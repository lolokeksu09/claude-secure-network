package dev.lolokeksu.csn.guardian;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.pm.PackageInfo;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.UserManager;
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
    private TextView discoveryText;
    private TextView vpnSectionTitle;
    private LinearLayout ownerControls;
    private int rawVpnServiceCount;
    private int filteredServiceCount;
    private boolean wireGuardInstalled;
    private Spinner vpnChoice;
    private Button enforceButton;
    private Button emergencyButton;
    private Button provisionButton;
    private TextView photoShareState;
    private static final String PHOTO_SHARE_PREFS = "guardian_photo_sharing";
    private static final String PHOTO_SHARE_ENABLED = "images_to_personal_last_applied";
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
        button.setMinHeight(dp(50));
        button.setTextColor(COLOR_TEXT);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(button, lp);
        return button;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(18), dp(14), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(18, 22, 27));
        background.setCornerRadius(dp(18));
        group.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        parent.addView(group, lp);
        return group;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(container);

        label(container, "CSN  /  GUARDIAN", 12, COLOR_ACCENT, true);
        label(container, "Защита профиля", 26, COLOR_TEXT, true);
        label(container, "Контроль политик Android · v0.1.3 alpha", 13, COLOR_MUTED, false);

        LinearLayout statusCard = card(container);
        label(statusCard, "СТАТУС ПОЛИТИКИ", 11, COLOR_ACCENT, true);
        statusText = label(statusCard, "Чтение состояния…", 17, COLOR_TEXT, true);
        messageText = label(statusCard, "", 13, COLOR_MUTED, false);

        LinearLayout vpnCard = card(container);
        label(vpnCard, "ДИАГНОСТИКА VPN", 11, COLOR_ACCENT, true);
        discoveryText = label(vpnCard, "Сканирование VPN-служб…", 14, COLOR_TEXT, false);
        button(vpnCard, "Системные настройки VPN", this::openVpnSettings);
        button(vpnCard, "Скопировать технический отчёт", this::copyDiagnostics);

        ownerControls = card(container);
        vpnSectionTitle = label(ownerControls, "ПОЛИТИКА РАБОЧЕГО ПРОФИЛЯ", 11, COLOR_ACCENT, true);
        label(ownerControls, "Приложение VPN", 14, COLOR_TEXT, true);
        vpnChoice = new Spinner(this);
        ownerControls.addView(vpnChoice, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        enforceButton = button(ownerControls, "Включить Always-on + Lockdown", this::confirmEnforce);
        emergencyButton = button(ownerControls, "Аварийно снять Lockdown", this::confirmEmergency);

        label(ownerControls, "ПЕРЕДАЧА ИЗОБРАЖЕНИЙ", 11, COLOR_ACCENT, true);
        photoShareState = label(ownerControls, "Последняя настройка: не применялась", 13, COLOR_MUTED, false);
        button(ownerControls, "Разрешить фото в личный профиль", () -> confirmPhotoSharing(true));
        button(ownerControls, "Запретить передачу фото", () -> confirmPhotoSharing(false));

        provisionButton = button(container, "Создать новый рабочий профиль", this::confirmProvision);
        button(container, "Повторить диагностику", this::refresh);

        label(container, "CSN не получает полномочия от Shizuku. Политики можно применять "
                + "только из рабочего профиля, где Guardian — Profile Owner. "
                + "Обмен фото требует явного разрешения; VPN-защита настраивается отдельно. "
                + "Часовой пояс общий для всего устройства.", 12, COLOR_MUTED, false);
        setContentView(scroll);
    }

    private void openVpnSettings() {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
        try {
            startActivity(intent);
        } catch (Exception ex) {
            showMessage("Системный экран VPN недоступен на этой прошивке.");
        }
    }

    private void copyDiagnostics() {
        String text = "CSN Guardian v0.1.3-alpha\n"
                + "Profile Owner: " + isOwner() + "\n"
                + "Current Android profile: " + currentProfileLabel() + "\n"
                + "VPN services returned: " + rawVpnServiceCount + "\n"
                + "VPN services rejected by permission/metadata: " + filteredServiceCount + "\n"
                + "Eligible VPN service packages: " + vpnPackages.size() + "\n"
                + "Eligible VPN package IDs: " + (vpnPackages.isEmpty() ? "none" : String.join(", ", vpnPackages)) + "\n"
                + "WireGuard package visible: " + wireGuardInstalled + "\n"
                + "Android: " + android.os.Build.VERSION.SDK_INT + "\n"
                + "Only eligible VPN package IDs included; no VPN keys or account tokens.\n";
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            showMessage("Буфер обмена недоступен.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CSN diagnostics", text));
        showMessage("Диагностика скопирована. Она не содержит подписок или VPN-ключей.");
    }

    private boolean isOwner() {
        return dpm != null && dpm.isProfileOwnerApp(getPackageName());
    }

    private String currentProfileLabel() {
        UserManager userManager = getSystemService(UserManager.class);
        if (userManager == null) return "unknown";
        return userManager.isManagedProfile() ? "work" : "personal";
    }

    private void findVpnServices() {
        vpnPackages.clear();
        serviceLabels.clear();
        rawVpnServiceCount = 0;
        filteredServiceCount = 0;
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(VpnService.SERVICE_INTERFACE);
        List<ResolveInfo> services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        if (services != null) {
            rawVpnServiceCount = services.size();
            for (ResolveInfo entry : services) {
                if (entry.serviceInfo == null
                        || !"android.permission.BIND_VPN_SERVICE".equals(entry.serviceInfo.permission)) {
                    filteredServiceCount++;
                    continue;
                }
                if (entry.serviceInfo.metaData != null
                        && !entry.serviceInfo.metaData.getBoolean(
                                VpnService.SERVICE_META_DATA_SUPPORTS_ALWAYS_ON, true)) {
                    filteredServiceCount++;
                    continue;
                }
                String pkg = entry.serviceInfo.packageName;
                if (!serviceLabels.containsKey(pkg)) {
                    CharSequence name = entry.loadLabel(pm);
                    serviceLabels.put(pkg, (name == null ? pkg : name.toString()) + "  (" + pkg + ")");
                }
            }
        }
        try {
            pm.getPackageInfo("com.wireguard.android", 0);
            wireGuardInstalled = true;
        } catch (PackageManager.NameNotFoundException ex) {
            wireGuardInstalled = false;
        }

        vpnPackages.addAll(serviceLabels.keySet());
        Collections.sort(vpnPackages);
        List<String> choices = new ArrayList<>();
        for (String pkg : vpnPackages) choices.add(serviceLabels.get(pkg));
        if (choices.isEmpty()) choices.add("Нет доступных VPN-служб");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, choices);
        vpnChoice.setAdapter(adapter);
    }

    private void refresh() {
        findVpnServices();
        boolean owner = isOwner();
        boolean workOwner = ImageSharingPolicyGate.allowed(owner, "work".equals(currentProfileLabel()));
        ownerControls.setVisibility(owner ? View.VISIBLE : View.GONE);
        boolean photoShareRecorded = getSharedPreferences(PHOTO_SHARE_PREFS, MODE_PRIVATE)
                .contains(PHOTO_SHARE_ENABLED);
        String photoShareLast = !photoShareRecorded ? "не применялась"
                : (getSharedPreferences(PHOTO_SHARE_PREFS, MODE_PRIVATE)
                .getBoolean(PHOTO_SHARE_ENABLED, false) ? "разрешена" : "запрещена");
        photoShareState.setText("Последняя настройка CSN: " + photoShareLast
                + ". Состояние Android и доступ к файлам нужно проверить передачей фото.");
        enforceButton.setEnabled(owner && !vpnPackages.isEmpty());
        emergencyButton.setEnabled(owner);
        boolean canProvision = !owner
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
                && dpm != null
                && dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        provisionButton.setVisibility(canProvision ? View.VISIBLE : View.GONE);

        StringBuilder info = new StringBuilder();
        info.append("Профиль: ").append("work".equals(currentProfileLabel()) ? "рабочий" : "личный")
                .append("\nСлужб в ответе Android: ").append(rawVpnServiceCount)
                .append("\nКандидатов для Always-on: ").append(vpnPackages.size());
        if (!vpnPackages.isEmpty()) {
            info.append("\nНайдены: ").append(String.join(", ", vpnPackages));
        }
        if (filteredServiceCount > 0) {
            info.append("\nИсключены по разрешению или поддержке Always-on: ")
                    .append(filteredServiceCount);
        }
        if (wireGuardInstalled) {
            info.append("\nWireGuard установлен в текущем профиле.");
        }
        if (vpnPackages.isEmpty()) {
            info.append("\nНет подходящих служб в результате PackageManager. ")
                    .append("Это НЕ доказывает отсутствие VPN на телефоне. ")
                    .append("Сравни с системными настройками VPN.");
        }
        discoveryText.setText(info.toString());

        if (!owner) {
            statusText.setText("Режим диагностики\nНет полномочий владельца");
            messageText.setText("Эта копия не является владельцем профиля. "
                    + "Открой CSN Guardian со значком портфеля в рабочем профиле. "
                    + "Политики Android здесь не изменяются.");
            return;
        }
        try {
            String current = dpm.getAlwaysOnVpnPackage(admin);
            boolean lockdown = dpm.isAlwaysOnVpnLockdownEnabled(admin);
            statusText.setText("CSN — владелец профиля\nПостоянный VPN: "
                    + (current == null ? "не задан" : current)
                    + "\nLockdown: " + (lockdown ? "ВКЛ" : "ВЫКЛ"));
            if (current != null && vpnPackages.contains(current)) {
                vpnChoice.setSelection(vpnPackages.indexOf(current));
            }
            messageText.setText(lockdown
                    ? "Политика сохранена в Android. Теперь требуются реальные проверки IP, DNS, IPv6 и отказа VPN."
                    : "Сначала настрой рабочий VPN-туннель; затем вручную включи политику Lockdown.");
        } catch (SecurityException ex) {
            statusText.setText("Нет доступа к политике");
            messageText.setText("Android запретил чтение административной политики. Ничего не изменено.");
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

    private void confirmPhotoSharing(boolean enable) {
        if (!ImageSharingPolicyGate.allowed(isOwner(), "work".equals(currentProfileLabel()))) {
            showMessage("Передачей фото может управлять только Guardian внутри рабочего профиля.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(enable ? "Разрешить передачу фото?" : "Запретить передачу фото?")
                .setMessage(enable
                        ? "После разрешения выбранное вручную изображение из рабочего профиля "
                        + "можно отправить в приложение личного профиля через Android «Поделиться». "
                        + "Это ослабляет изоляцию данных, но не отключает VPN. "
                        + "Передача не происходит автоматически."
                        : "Удалить добавленные Guardian правила пересылки изображений в личный "
                        + "профиль? Это не удаляет фото и не меняет VPN.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton(enable ? "Разрешить" : "Запретить",
                        (d, w) -> applyPhotoSharing(enable))
                .show();
    }

    private void applyPhotoSharing(boolean enable) {
        // Check at the policy write boundary, not only when the dialog opens.
        if (!ImageSharingPolicyGate.allowed(isOwner(), "work".equals(currentProfileLabel()))) {
            showMessage("Полномочия рабочего профиля отсутствуют; ничего не изменено.");
            return;
        }
        try {
            // Guardian currently owns no other custom cross-profile filters.
            // If more filters are added, rebuild them here instead of clearing independently.
            dpm.clearCrossProfileIntentFilters(admin);
            if (enable) {
                for (String action : new String[]{Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE}) {
                    IntentFilter filter = new IntentFilter(action);
                    filter.addCategory(Intent.CATEGORY_DEFAULT);
                    filter.addDataType("image/*");
                    // From work (managed) to personal (parent), not the reverse direction.
                    dpm.addCrossProfileIntentFilter(admin, filter,
                            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED);
                }
            }
            getSharedPreferences(PHOTO_SHARE_PREFS, MODE_PRIVATE)
                    .edit().putBoolean(PHOTO_SHARE_ENABLED, enable).apply();
            refresh();
            showMessage(enable
                    ? "Правило передачи изображений добавлено. Попробуй «Поделиться» "
                    + "из рабочей галереи и выбери личное приложение. "
                    + "Работу на этой прошивке ещё нужно проверить."
                    : "Правила Guardian для передачи фото удалены. "
                    + "Системные правила Android сохранены.");
        } catch (Exception ex) {
            // Do not report success when Android rejects a policy write.
            showMessage("Не удалось изменить передачу фото: "
                    + ex.getClass().getSimpleName() + ". Проверь настройки рабочего профиля.");
        }
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
