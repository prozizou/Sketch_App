package pro.sketchware.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.export.CiWorkflowGenerator.Options;

public class CiWorkflowGeneratorTest {
    @Test
    public void theWorkflowAlwaysBuildsAndKeepsTheApk() {
        String yaml = CiWorkflowGenerator.androidWorkflow(new Options(false, false));
        assertTrue(yaml.startsWith("name: Android CI\n"));
        assertTrue(yaml.contains("run: gradle assembleDebug"));
        assertTrue(yaml.contains("gradle-version: " + CiWorkflowGenerator.GRADLE_VERSION));
        assertTrue(yaml.contains("java-version: " + CiWorkflowGenerator.JAVA_VERSION));
        assertTrue(yaml.contains("actions/upload-artifact@v4"));
        assertTrue(yaml.contains("path: app/build/outputs/apk/debug/*.apk"));
        assertFalse(yaml.contains("lintDebug"));
        assertFalse(yaml.contains("testDebugUnitTest"));
    }

    @Test
    public void lintAndTestsAreOptional() {
        String lint = CiWorkflowGenerator.androidWorkflow(new Options(true, false));
        assertTrue(lint.contains("run: gradle lintDebug"));
        assertFalse(lint.contains("testDebugUnitTest"));
        String tests = CiWorkflowGenerator.androidWorkflow(new Options(false, true));
        assertTrue(tests.contains("run: gradle testDebugUnitTest"));
        assertFalse(tests.contains("lintDebug"));
        String both = CiWorkflowGenerator.androidWorkflow(new Options(true, true));
        assertTrue(both.indexOf("assembleDebug") < both.indexOf("lintDebug"));
        assertTrue(both.indexOf("lintDebug") < both.indexOf("testDebugUnitTest"));
        assertTrue(both.indexOf("testDebugUnitTest") < both.indexOf("upload-artifact"));
    }

    @Test
    public void theFileIsPlainYamlWithTwoSpaceIndentation() {
        for (Options options : new Options[]{new Options(false, false), new Options(true, true)}) {
            String yaml = CiWorkflowGenerator.androidWorkflow(options);
            assertFalse("no tabs allowed in YAML", yaml.contains("\t"));
            assertFalse("no Windows line endings", yaml.contains("\r"));
            assertTrue(yaml.endsWith("\n"));
            for (String line : yaml.split("\n")) {
                int indent = line.length() - line.stripLeading().length();
                assertEquals("indentation of '" + line + "'", 0, indent % 2);
            }
        }
        assertEquals(".github/workflows/android.yml", CiWorkflowGenerator.WORKFLOW_PATH);
    }
}
