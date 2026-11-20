package dev.lolokeksu.csn.guardian;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.Collections;
import java.util.Set;

public final class VpnPolicyGateTest {
    private static final Set<String> APPROVED = Collections.singleton("org.example.vpn");

    @Test public void deniesWithoutProfileOwnership() {
        assertEquals(VpnPolicyGate.Decision.NOT_PROFILE_OWNER,
                VpnPolicyGate.evaluate(false, "org.example.vpn", APPROVED));
    }

    @Test public void deniesEmptySelection() {
        assertEquals(VpnPolicyGate.Decision.NO_VPN_SELECTED,
                VpnPolicyGate.evaluate(true, "", APPROVED));
    }

    @Test public void deniesUnknownPackage() {
        assertEquals(VpnPolicyGate.Decision.NOT_A_VPN_SERVICE,
                VpnPolicyGate.evaluate(true, "org.example.other", APPROVED));
    }

    @Test public void allowsOnlyActualVpnServiceOwnedByAdmin() {
        assertEquals(VpnPolicyGate.Decision.ALLOW,
                VpnPolicyGate.evaluate(true, "org.example.vpn", APPROVED));
    }
}
