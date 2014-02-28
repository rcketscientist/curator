package com.anthonymandra.framework;

/**
 * Created by amand_000 on 9/10/13.
 */
public abstract class License {
    private static final String TAG = License.class.getSimpleName();
    public static final String KEY_LICENSE_RESPONSE = "licenseResponse";

    public static enum LicenseState
    {
        demo,
        pro,
        modified_0x000,
        modified_0x001,
        modified_0x002,
        modified_0x003,
        modified_0x004,
        error
    }
}
