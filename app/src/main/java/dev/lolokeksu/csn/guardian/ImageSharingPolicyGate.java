package dev.lolokeksu.csn.guardian;

/** Authorizes changes to the work -> personal image forwarding policy. */
public final class ImageSharingPolicyGate {
    private ImageSharingPolicyGate() {}

    public static boolean allowed(boolean isProfileOwner, boolean isManagedProfile) {
        return isProfileOwner && isManagedProfile;
    }
}
