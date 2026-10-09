package pro.sketchware.flags;

import mod.hilal.saif.activities.tools.ConfigActivity;

public final class FeatureFlags {
    private FeatureFlags() {
    }

    public static boolean isEnabled(FeatureFlag flag) {
        return ConfigActivity.DataStore.getInstance().getBoolean(flag.key(), flag.enabledByDefault());
    }
}
