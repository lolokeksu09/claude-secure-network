package dev.lolokeksu.csn.guardian;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * DPC receiver. Deliberately does not enable VPN or apply lockdown on enrollment.
 * A user must first configure a supported VPN and explicitly authorize the policy.
 */
public final class GuardianAdminReceiver extends DeviceAdminReceiver {
    public static ComponentName component(Context context) {
        return new ComponentName(context, GuardianAdminReceiver.class);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        ComponentName admin = component(context);
        if (dpm != null && dpm.isProfileOwnerApp(context.getPackageName())) {
            dpm.setProfileName(admin, "CSN Secure");
            dpm.setProfileEnabled(admin);
        }
    }
}
