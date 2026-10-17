package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.evolve.PostRules.Problem;

public class PostRulesTest {
    @Test
    public void tagsAreLowercasedTrimmedAndDeduplicated() {
        assertEquals(List.of("java", "ui", "c++"), PostRules.normalizeTags(List.of(" Java ", "#UI", "java", "", "C++")));
        assertEquals(List.of("a1", "b2"), PostRules.splitTags("A1, #b2  a1"));
        assertTrue(PostRules.splitTags(null).isEmpty());
    }

    @Test
    public void tagLimits() {
        assertEquals(Problem.NONE, PostRules.checkTags(List.of("java", "ui", "kotlin-ext", "c#", "v1.2")));
        assertEquals(Problem.TOO_MANY_TAGS, PostRules.checkTags(List.of("aa", "bb", "cc", "dd", "ee", "ff")));
        assertEquals(Problem.TAG_INVALID, PostRules.checkTags(List.of("a")));
        assertEquals(Problem.TAG_INVALID, PostRules.checkTags(List.of("has space")));
        assertEquals(Problem.TAG_INVALID, PostRules.checkTags(List.of("<script>")));
        assertEquals(Problem.TAG_INVALID, PostRules.checkTags(List.of("x".repeat(25))));
    }

    @Test
    public void versionIsOptionalButShort() {
        assertEquals(Problem.NONE, PostRules.checkVersion(""));
        assertEquals(Problem.NONE, PostRules.checkVersion("1.2"));
        assertEquals(Problem.NONE, PostRules.checkVersion("2.0-beta"));
        assertEquals(Problem.VERSION_INVALID, PostRules.checkVersion("1 2"));
        assertEquals(Problem.VERSION_INVALID, PostRules.checkVersion("1".repeat(17)));
    }

    @Test
    public void minSdkIsZeroOrAPlausibleLevel() {
        assertEquals(Problem.NONE, PostRules.checkMinSdk(0));
        assertEquals(Problem.NONE, PostRules.checkMinSdk(26));
        assertEquals(Problem.MIN_SDK_INVALID, PostRules.checkMinSdk(5));
        assertEquals(Problem.MIN_SDK_INVALID, PostRules.checkMinSdk(99));
    }

    @Test
    public void dependencies() {
        assertEquals(List.of("com.squareup.okhttp3:okhttp:4.12.0", "gson"), PostRules.splitDependencies("com.squareup.okhttp3:okhttp:4.12.0,\n gson\n\ngson"));
        assertEquals(Problem.NONE, PostRules.checkDependencies(List.of("com.squareup.okhttp3:okhttp:4.12.0", "gson")));
        assertEquals(Problem.DEPENDENCY_INVALID, PostRules.checkDependencies(List.of("rm -rf /")));
        assertEquals(Problem.DEPENDENCY_INVALID, PostRules.checkDependencies(List.of("a".repeat(121))));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 11; i++) many.add("lib" + i);
        assertEquals(Problem.TOO_MANY_DEPENDENCIES, PostRules.checkDependencies(many));
    }

    @Test
    public void screenshotsMustBeInTheAppsOwnStorage() {
        String ok = "https://firebasestorage.googleapis.com/v0/b/app.appspot.com/o/evolve%2Fa.jpg?alt=media&token=x";
        assertEquals(Problem.NONE, PostRules.checkScreenshots(List.of(ok)));
        assertEquals(Problem.NONE, PostRules.checkScreenshots(List.of("https://my-app.firebasestorage.app/v0/b/x/o/a.jpg")));
        assertEquals(Problem.SCREENSHOT_INVALID, PostRules.checkScreenshots(List.of("http://firebasestorage.googleapis.com/a.jpg")));
        assertEquals(Problem.SCREENSHOT_INVALID, PostRules.checkScreenshots(List.of("https://evil.example.com/a.jpg")));
        assertEquals(Problem.SCREENSHOT_INVALID, PostRules.checkScreenshots(List.of("https://firebasestorage.googleapis.com.evil.com/a.jpg")));
        assertEquals(Problem.TOO_MANY_SCREENSHOTS, PostRules.checkScreenshots(List.of(ok, ok, ok, ok, ok)));
    }

    @Test
    public void javaMustParse() {
        assertEquals(Problem.NONE, PostRules.checkCode(SharedKind.JAVA, "package a;\npublic class A { void f() { int x = 1; } }"));
        assertEquals(Problem.JAVA_SYNTAX, PostRules.checkCode(SharedKind.JAVA, "public class A { void f( }"));
        assertEquals(Problem.CODE_EMPTY, PostRules.checkCode(SharedKind.JAVA, "  "));
        assertEquals(Problem.CODE_TOO_LARGE, PostRules.checkCode(SharedKind.KOTLIN, "x".repeat(SharedBlockRules.MAX_JSON_LENGTH + 1)));
    }

    @Test
    public void kotlinHasOnlyTheSizeLimits() {
        assertEquals(Problem.NONE, PostRules.checkCode(SharedKind.KOTLIN, "fun main() { println(\"hi\") }"));
    }

    private static final String COMPONENT = "[{\"name\":\"X\",\"id\":\"1\",\"icon\":\"i\",\"varName\":\"x\",\"typeName\":\"X\",\"buildClass\":\"X\",\"class\":\"X\","
            + "\"description\":\"d\",\"url\":\"u\",\"additionalVar\":\"\",\"defineAdditionalVar\":\"\",\"imports\":\"\"}]";

    @Test
    public void aComponentIsAListOfCompleteComponents() {
        assertEquals(Problem.NONE, PostRules.checkCode(SharedKind.COMPONENT, COMPONENT));
        assertEquals(Problem.COMPONENT_INVALID, PostRules.checkCode(SharedKind.COMPONENT, "[1,2]"));
        assertEquals(Problem.COMPONENT_INVALID, PostRules.checkCode(SharedKind.COMPONENT, "[]"));
        assertEquals(Problem.COMPONENT_INVALID, PostRules.checkCode(SharedKind.COMPONENT, "{\"name\":\"X\"}"));
        assertEquals(Problem.COMPONENT_INVALID, PostRules.checkCode(SharedKind.COMPONENT, "{broken"));
    }

    @Test
    public void fileNames() {
        assertEquals(Problem.NONE, PostRules.checkFileName(SharedKind.JAVA, "LoginHelper.java"));
        assertEquals(Problem.NONE, PostRules.checkFileName(SharedKind.KOTLIN, "Utils.kt"));
        assertEquals(Problem.FILE_NAME_INVALID, PostRules.checkFileName(SharedKind.JAVA, "Utils.kt"));
        assertEquals(Problem.FILE_NAME_INVALID, PostRules.checkFileName(SharedKind.JAVA, "../Evil.java"));
        assertEquals(Problem.FILE_NAME_INVALID, PostRules.checkFileName(SharedKind.JAVA, "9Lives.java"));
        assertEquals(Problem.NONE, PostRules.checkFileName(SharedKind.VIEW, "anything"));
    }

    @Test
    public void suggestedFileNamesAreValid() {
        assertEquals("LoginHelper.java", PostRules.suggestFileName(SharedKind.JAVA, "login helper"));
        assertEquals("Shared123.kt", PostRules.suggestFileName(SharedKind.KOTLIN, "123"));
        assertEquals("Shared.java", PostRules.suggestFileName(SharedKind.JAVA, "éé"));
        for (String name : new String[]{"a b c", "../x", "Été", "", "x".repeat(200)}) {
            assertEquals(name, Problem.NONE, PostRules.checkFileName(SharedKind.JAVA, PostRules.suggestFileName(SharedKind.JAVA, name)));
        }
    }

    @Test
    public void onlyThirdPartyImportsAreListed() {
        String code = "import android.os.Bundle;\nimport java.util.List;\nimport okhttp3.OkHttpClient;\nimport static com.x.Y.z;\nimport okhttp3.OkHttpClient;\nimport kotlin.io.println";
        assertEquals(List.of("okhttp3.OkHttpClient", "com.x.Y.z"), PostRules.thirdPartyImports(code));
    }

    @Test
    public void cleaningRemovesControlAndDirectionCharacters() {
        assertEquals("a b", PostRules.clean("a\u0000 b‮"));
        assertEquals("a\n\nb", PostRules.clean("a\r\n\n\n\n\nb  "));
        assertEquals("", PostRules.clean(null));
        assertEquals("<b>bold</b>", PostRules.clean("<b>bold</b>"));
    }

    @Test
    public void commentChecks() {
        assertEquals(PostRules.CommentProblem.NONE, PostRules.checkComment("Nice work!"));
        assertEquals(PostRules.CommentProblem.EMPTY, PostRules.checkComment(" \u0000 "));
        assertEquals(PostRules.CommentProblem.TOO_LONG, PostRules.checkComment("x".repeat(501)));
        assertEquals(PostRules.CommentProblem.SPAM, PostRules.checkComment("http://a.com http://b.com https://c.com"));
        assertEquals(PostRules.CommentProblem.SPAM, PostRules.checkComment("aaaaaaaaaaaaaaaaaaaa"));
        assertFalse(PostRules.looksLikeSpam("see https://a.com and https://b.com"));
    }
}
