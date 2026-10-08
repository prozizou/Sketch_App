package a.a.a;

import android.view.View;
import android.view.animation.Animation;

/**
 * Stand-in for a class the prebuilt a.a.a jars refer to (in mB) but do not contain. A phone resolves it only
 * when an animation actually runs; the JVM wants it while loading mB, which every property row does.
 * Tests only: the real app never sees this.
 */
public class lB implements Animation.AnimationListener {
    public lB(View view, int visibility) {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    @Override
    public void onAnimationEnd(Animation animation) {
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }
}
