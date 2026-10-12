package pro.sketchware.unityads;

import android.content.Context;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.LibraryItemView;

import pro.sketchware.R;

/** The "Unity Ads" row of the Manage libraries screen. */
public class UnityAdsLibraryItemView extends LibraryItemView {
    private final String scId;

    public UnityAdsLibraryItemView(Context context, String scId) {
        super(context);
        this.scId = scId;
    }

    @Override
    public void setData(@Nullable ProjectLibraryBean ignored) {
        boolean on = UnityAdsActivity.isEnabled(scId);
        icon.setImageResource(R.drawable.ic_mtrl_ad);
        title.setText(R.string.unity_ads_title);
        description.setText(R.string.unity_ads_description);
        enabled.setText(on ? "ON" : "OFF");
        enabled.setSelected(on);
    }
}
