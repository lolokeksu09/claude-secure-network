package dev.lolokeksu.csn.guardian;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ImageSharingPolicyGateTest {
    @Test public void personalProfileCannotChangeSharingEvenWhenGuardianInstalled() {
        assertFalse(ImageSharingPolicyGate.allowed(false, false));
    }

    @Test public void nonOwnerInManagedProfileCannotChangeSharing() {
        assertFalse(ImageSharingPolicyGate.allowed(false, true));
    }

    @Test public void nonManagedProfileNeverChangesSharing() {
        assertFalse(ImageSharingPolicyGate.allowed(true, false));
    }

    @Test public void onlyManagedProfileOwnerCanChangeSharing() {
        assertTrue(ImageSharingPolicyGate.allowed(true, true));
    }
}
