package pro.sketchware.widgets.registry;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;
import com.besome.sketch.editor.view.palette.IconButton;
import com.besome.sketch.editor.view.palette.IconEditText;
import com.besome.sketch.editor.view.palette.IconImageView;
import com.besome.sketch.editor.view.palette.IconLinearHorizontal;
import com.besome.sketch.editor.view.palette.IconLinearVertical;
import com.besome.sketch.editor.view.palette.IconScrollViewVertical;
import com.besome.sketch.editor.view.palette.IconSwitch;
import com.besome.sketch.editor.view.palette.IconTextView;

import dev.aldi.sayuti.editor.view.palette.IconCardView;
import dev.aldi.sayuti.editor.view.palette.IconMaterialButton;
import mod.agus.jcoderz.editor.view.palette.IconAutoCompleteTextView;
import pro.sketchware.R;
import pro.sketchware.widgets.registry.WidgetSpec.Base;

/**
 * Palette tile of a {@link WidgetRegistry} component.
 *
 * <p>The new widget starts from the built-in tile of its {@link WidgetSpec#base() base type}, so margins, padding,
 * sizes and text defaults are the ones of the widget it behaves like, and keeps that type for preview, properties
 * and events. Only the class written to the layout and to the Java code ({@code convert}) and the default
 * attributes ({@code inject}) come from the spec.
 */
public class IconRegistryWidget extends IconBase {

    private final WidgetSpec spec;
    private final IconBase base;

    protected IconRegistryWidget(Context context, WidgetSpec spec) {
        super(context);
        this.spec = spec;
        base = createBase(context, spec);
        String text = spec.defaultText().isEmpty() ? spec.title() : spec.defaultText();
        base.setName(text);
        setName(text);
        setWidgetImage(iconFor(spec.base()));
        setText(spec.title());
        if (base instanceof IconImageView image) {
            // The same placeholder image the built-in ImageView tile starts with.
            image.setResourceName("default_image");
        }
    }

    /**
     * @return a tile for {@code spec}; those that need AndroidX or Material are marked so the editor first asks for
     * that library to be enabled in the project, exactly as it does for the built-in Material widgets.
     */
    public static IconRegistryWidget create(Context context, WidgetSpec spec) {
        return spec.dependency().isPlatform()
                ? new IconRegistryWidget(context, spec)
                : new NeedsLibrary(context, spec);
    }

    public WidgetSpec getSpec() {
        return spec;
    }

    @Override
    public ViewBean getBean() {
        ViewBean bean = base.getBean();
        bean.convert = spec.className();
        bean.inject = spec.defaultInject();
        return bean;
    }

    @NonNull
    private static IconBase createBase(Context context, WidgetSpec spec) {
        return switch (spec.base()) {
            case LINEAR_VERTICAL -> new IconLinearVertical(context);
            case LINEAR_HORIZONTAL -> new IconLinearHorizontal(context);
            case SCROLL_VERTICAL -> new IconScrollViewVertical(context);
            case BUTTON -> new IconButton(context);
            case MATERIAL_BUTTON -> new IconMaterialButton(context);
            case TEXT_VIEW -> new IconTextView(context);
            case EDIT_TEXT -> new IconEditText(context);
            case SWITCH -> new IconSwitch(context);
            case IMAGE_VIEW -> new IconImageView(context);
            case CARD_VIEW -> new IconCardView(context);
            case AUTO_COMPLETE_TEXT_VIEW -> new IconAutoCompleteTextView(context);
            case NONE -> throw new IllegalArgumentException(spec.id() + " has no base view type, so it can't be a palette tile");
        };
    }

    @DrawableRes
    private static int iconFor(Base base) {
        return switch (base) {
            case LINEAR_VERTICAL -> R.drawable.ic_mtrl_view_vertical;
            case LINEAR_HORIZONTAL -> R.drawable.ic_mtrl_view_horizontal;
            case SCROLL_VERTICAL -> R.drawable.ic_mtrl_swipe_vertical;
            case BUTTON, MATERIAL_BUTTON -> R.drawable.ic_mtrl_button_click;
            case TEXT_VIEW -> R.drawable.ic_mtrl_formattext;
            case EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW -> R.drawable.ic_mtrl_edittext;
            case SWITCH -> R.drawable.ic_mtrl_toggle;
            case IMAGE_VIEW -> R.drawable.ic_mtrl_image;
            case CARD_VIEW -> R.drawable.ic_mtrl_rectangle;
            case NONE -> R.drawable.ic_mtrl_rectangle;
        };
    }

    /** A tile whose widget needs the project's AppCompat/Material library. */
    private static final class NeedsLibrary extends IconRegistryWidget implements AndroidxOrMaterialView {
        NeedsLibrary(Context context, WidgetSpec spec) {
            super(context, spec);
        }
    }
}
