package mod.hey.studios.project.proguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import pro.sketchware.utility.FileUtil;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class ProguardHandlerTest {
    private static String newProject() {
        return "test" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    public void aNewProjectStartsWithShrinkingOff() {
        ProguardHandler handler = new ProguardHandler(newProject());

        assertEquals(OptimizationMode.OFF, handler.getMode());
        assertFalse(handler.isShrinkingEnabled());
    }

    @Test
    public void theSmallerLevelTurnsShrinkingOnWithR8AndNoRenaming() {
        ProguardHandler handler = new ProguardHandler(newProject());

        handler.setMode(OptimizationMode.SAFE);

        assertEquals(OptimizationMode.SAFE, handler.getMode());
        assertTrue(handler.isShrinkingEnabled());
        assertTrue("R8 is picked for the user", handler.isR8Enabled());
        assertTrue(FileUtil.readFile(handler.getModeRulesPath()).contains("-dontobfuscate"));
    }

    @Test
    public void theFullLevelTurnsTheCrashMapOnAndAddsNoExtraRule() {
        ProguardHandler handler = new ProguardHandler(newProject());

        handler.setMode(OptimizationMode.MAX);

        assertEquals(OptimizationMode.MAX, handler.getMode());
        assertTrue(handler.isDebugFilesEnabled());
        assertFalse(FileUtil.readFile(handler.getModeRulesPath()).contains("-dontobfuscate"));
    }

    @Test
    public void goingBackToOffStopsShrinking() {
        ProguardHandler handler = new ProguardHandler(newProject());
        handler.setMode(OptimizationMode.MAX);

        handler.setMode(OptimizationMode.OFF);

        assertFalse(handler.isShrinkingEnabled());
        assertEquals("", FileUtil.readFile(handler.getModeRulesPath()));
    }

    @Test
    public void anEngineChoiceAlreadyMadeIsNotOverridden() {
        ProguardHandler handler = new ProguardHandler(newProject());
        handler.setR8Enabled(false); // the user picked ProGuard in the advanced options

        handler.setMode(OptimizationMode.SAFE);

        assertFalse(handler.isR8Enabled());
    }

    @Test
    public void theOldSwitchStillWorksAndKeepsTheLevelsInStep() {
        ProguardHandler handler = new ProguardHandler(newProject());

        handler.setProguardEnabled(true);
        assertEquals(OptimizationMode.MAX, handler.getMode());

        handler.setProguardEnabled(false);
        assertEquals(OptimizationMode.OFF, handler.getMode());
    }

    @Test
    public void anOldConfigWithOnlyTheEnabledSwitchCountsAsTheFullLevel() {
        String project = newProject();
        new ProguardHandler(project); // creates the default config
        String configPath = FileUtil.getExternalStorageDir() + "/.sketch_nws/data/" + project + "/proguard";
        FileUtil.writeFile(configPath, "{\"enabled\":\"true\",\"debug\":\"false\"}");

        ProguardHandler handler = new ProguardHandler(project);

        assertEquals(OptimizationMode.MAX, handler.getMode());
        assertTrue(handler.isShrinkingEnabled());
    }
}
