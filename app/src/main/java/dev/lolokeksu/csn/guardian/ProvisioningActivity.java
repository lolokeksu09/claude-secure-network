package dev.lolokeksu.csn.guardian;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

/** Required hooks for modern Android managed-profile provisioning. */
public final class ProvisioningActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getAction();
        Intent result = new Intent();
        if (DevicePolicyManager.ACTION_GET_PROVISIONING_MODE.equals(action)) {
            result.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE);
            setResult(RESULT_OK, result);
        } else if (DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE.equals(action)) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
