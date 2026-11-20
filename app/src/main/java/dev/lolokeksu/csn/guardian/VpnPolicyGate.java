package dev.lolokeksu.csn.guardian;

import java.util.Set;

/** Pure authorization gate; framework ownership is checked again at the mutation. */
public final class VpnPolicyGate {
    public enum Decision { ALLOW, NOT_PROFILE_OWNER, NO_VPN_SELECTED, NOT_A_VPN_SERVICE }
    private VpnPolicyGate() {}

    public static Decision evaluate(boolean isProfileOwner, String selectedPackage, Set<String> approvedVpnPackages) {
        if (!isProfileOwner) return Decision.NOT_PROFILE_OWNER;
        if (selectedPackage == null || selectedPackage.trim().isEmpty()) return Decision.NO_VPN_SELECTED;
        if (approvedVpnPackages == null || !approvedVpnPackages.contains(selectedPackage))
            return Decision.NOT_A_VPN_SERVICE;
        return Decision.ALLOW;
    }
}
