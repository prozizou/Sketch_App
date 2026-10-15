package pro.sketchware.resolver.ui;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

import a.a.a.KB;
import a.a.a.wq;
import pro.sketchware.resolver.Aapt2Validator;
import pro.sketchware.resolver.CompositeValidator;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.PackIconLibrary;
import pro.sketchware.resolver.ResolverSession;
import pro.sketchware.resolver.ResourceInventory;
import pro.sketchware.utility.FilePathUtil;

/** Puts the resolver together for one project: where its resources are, which icons are on the device, which AAPT2 checks. */
public final class ResolverEnvironment {
    private ResolverEnvironment() {
    }

    /** The project's own resource folder, where fixes are written. */
    public static File resDir(String scId) {
        return new File(new FilePathUtil().getPathResource(scId));
    }

    /** Where the undo records of a project are kept. */
    public static File journalRoot(String scId) {
        return new File(wq.getAbsolutePathOf(".sketch_nws" + File.separator + "resolver_undo" + File.separator + scId));
    }

    /**
     * Opens the offline Material Icons, unpacking them the first time (the same pack the icon importer uses).
     *
     * @return the library, or null when the pack can't be unpacked (the resolver then offers project resources only)
     */
    @Nullable
    public static IconLibrary openIcons(Context context) {
        try {
            File svg = new File(wq.getExtractedIconPackStoreLocation(), "svg");
            if (!svg.isDirectory()) {
                KB.a(context, "icons" + File.separator + "icon_pack.zip", wq.getExtractedIconPackStoreLocation());
            }
            PackIconLibrary library = new PackIconLibrary(svg);
            return library.size() > 0 ? library : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Builds the session for the missing resources of one build log. Reads files: call it off the main thread. */
    public static ResolverSession open(Context context, String scId, List<MissingResource> problems, @Nullable IconLibrary icons) {
        File res = resDir(scId);
        ResourceInventory inventory = ResourceInventory.scan(res, new File(wq.g(), scId));
        File cache = context.getCacheDir();
        Aapt2Validator aapt2 = new Aapt2Validator(new File(cache, "aapt2"), new File(cache, "resolver-scratch"));
        return new ResolverSession(problems, inventory, icons, CompositeValidator.standard(aapt2), res, journalRoot(scId), "#000000");
    }
}
