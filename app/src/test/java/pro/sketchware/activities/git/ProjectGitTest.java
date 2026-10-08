package pro.sketchware.activities.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.api.Git;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.git.ProjectGit.Change;
import pro.sketchware.activities.git.ProjectGit.ChangeKind;
import pro.sketchware.activities.git.ProjectGit.Commit;
import pro.sketchware.activities.git.ProjectGit.GitProblem;

public class ProjectGitTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final List<ProjectGit> opened = new ArrayList<>();
    private File work;
    private ProjectGit git;

    private ProjectGit start(String name) throws Exception {
        File workTree = temp.newFolder(name + "-data");
        File gitDir = new File(temp.getRoot(), name + ".git");
        ProjectGit result = ProjectGit.init(gitDir, workTree);
        opened.add(result);
        return result;
    }

    private static void write(File root, String path, String content) throws Exception {
        File f = new File(root, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content);
    }

    private static String read(File root, String path) throws Exception {
        return Files.readString(new File(root, path).toPath());
    }

    @Before
    public void setUp() throws Exception {
        git = start("mine");
        work = new File(temp.getRoot(), "mine-data");
    }

    @After
    public void tearDown() {
        for (ProjectGit g : opened) g.close();
    }

    private Commit commit(ProjectGit g, String message) throws Exception {
        Commit c = g.commitAll(message, "Ada", "ada@example.com");
        assertNotNull(c);
        return c;
    }

    @Test
    public void repositoryLivesOutsideTheProjectFolder() {
        assertFalse(new File(work, ".git").exists());
        assertTrue(ProjectGit.exists(new File(temp.getRoot(), "mine.git")));
        assertEquals("main", assertDoesNotThrow(() -> git.branch()));
    }

    private static <T> T assertDoesNotThrow(ThrowingSupplier<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @Test
    public void statusShowsAddedModifiedAndDeletedFilesAndIgnoresTheCompileLog() throws Exception {
        write(work, "logic", "v1");
        write(work, "files/java/Main.java", "class Main {}");
        write(work, "compile_log", "noise");
        commit(git, "first");
        assertEquals(List.of(), git.status());

        write(work, "logic", "v2");
        write(work, "files/new.txt", "new");
        write(work, "compile_log", "more noise");
        Files.delete(new File(work, "files/java/Main.java").toPath());

        assertEquals(List.of(
                        new Change("files/java/Main.java", ChangeKind.DELETED),
                        new Change("files/new.txt", ChangeKind.ADDED),
                        new Change("logic", ChangeKind.MODIFIED)),
                git.status());
    }

    @Test
    public void noticesAnEditOfTheSameSizeRightAfterACommit() throws Exception {
        write(work, "logic", "v1");
        commit(git, "first");
        write(work, "logic", "v2");
        assertEquals(List.of(new Change("logic", ChangeKind.MODIFIED)), git.status());
        write(work, "logic", "v3");
        assertNotNull("the edit must make it into the commit", commit(git, "second"));
        write(work, "logic", "v4");
        assertTrue(git.diffOfWorkingTree().contains("+v4"));
    }

    @Test
    public void commitsAppearInTheLogNewestFirstAndNothingToCommitReturnsNull() throws Exception {
        assertEquals(List.of(), git.log(10));
        write(work, "logic", "v1");
        Commit first = commit(git, "first");
        write(work, "logic", "v2");
        Commit second = commit(git, "second change\n\nwith details");

        List<Commit> log = git.log(10);
        assertEquals(List.of(second.id(), first.id()), List.of(log.get(0).id(), log.get(1).id()));
        assertEquals("second change", log.get(0).message());
        assertEquals("Ada", log.get(0).author());
        assertNull(git.commitAll("again", "Ada", "ada@example.com"));
    }

    @Test
    public void commitNeedsAMessageAndAName() {
        try {
            git.commitAll("  ", "Ada", "a@b.c");
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("message"));
        }
        try {
            git.commitAll("x", "", "a@b.c");
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("name"));
        }
    }

    @Test
    public void commitRecordsDeletions() throws Exception {
        write(work, "a.txt", "a");
        write(work, "b.txt", "b");
        commit(git, "both");
        Files.delete(new File(work, "a.txt").toPath());
        commit(git, "drop a");
        assertEquals(List.of(), git.status());
        assertTrue(git.diffOfCommit(git.log(1).get(0).id()).contains("deleted file mode"));
    }

    @Test
    public void diffsShowTheChanges() throws Exception {
        write(work, "notes.txt", "one\n");
        commit(git, "first");
        write(work, "notes.txt", "one\ntwo\n");
        write(work, "other.txt", "x\n");

        String pending = git.diffOfWorkingTree();
        assertTrue(pending, pending.contains("+two"));
        assertTrue(pending.contains("other.txt"));

        Commit second = commit(git, "second");
        String committed = git.diffOfCommit(second.id());
        assertTrue(committed.contains("+two"));
        assertTrue("first commit diff against nothing", git.diffOfCommit(git.log(2).get(1).id()).contains("+one"));

        write(work, "bin.dat", "\0\1\2binary");
        assertTrue(git.diffOfWorkingTree().contains("Binary files"));
    }

    @Test
    public void branchesCanBeCreatedAndSwitchedWhenTheTreeIsClean() throws Exception {
        write(work, "logic", "v1");
        commit(git, "first");
        git.createBranch("feature");
        assertEquals("feature", git.branch());
        write(work, "logic", "feature work");
        commit(git, "on feature");

        try {
            write(work, "logic", "dirty");
            git.switchBranch("main");
            fail("a dirty tree must not switch branch");
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("Commit or discard"));
        }
        git.discardChanges();
        git.switchBranch("main");
        assertEquals("v1", read(work, "logic"));
        assertEquals(List.of("feature", "main"), git.branches());
        git.switchBranch("feature");
        assertEquals("feature work", read(work, "logic"));
    }

    @Test
    public void rejectsBadBranchNames() throws Exception {
        write(work, "logic", "v1");
        commit(git, "first");
        try {
            git.createBranch("not a branch");
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("valid"));
        }
    }

    @Test
    public void restoreFilesBringsBackAnOldStateAsUncommittedChanges() throws Exception {
        write(work, "logic", "v1");
        write(work, "keep.txt", "keep");
        write(work, "old.txt", "old");
        Commit first = commit(git, "first");

        write(work, "logic", "v2");
        Files.delete(new File(work, "old.txt").toPath());
        write(work, "added-later.txt", "later");
        commit(git, "second");

        git.restoreFilesFrom(first.id());
        assertEquals("v1", read(work, "logic"));
        assertEquals("old", read(work, "old.txt"));
        assertFalse(new File(work, "added-later.txt").exists());
        assertEquals(2, git.log(10).size());

        List<Change> pending = git.status();
        assertTrue(pending.toString(), pending.contains(new Change("added-later.txt", ChangeKind.DELETED)));
        assertTrue(pending.contains(new Change("old.txt", ChangeKind.ADDED)) || pending.contains(new Change("old.txt", ChangeKind.MODIFIED)));
        commit(git, "Restore to first");
        assertEquals(List.of(), git.status());
        assertEquals("v1", read(work, "logic"));
    }

    @Test
    public void discardChangesRemovesEditsAndNewFiles() throws Exception {
        write(work, "logic", "v1");
        commit(git, "first");
        write(work, "logic", "edited");
        write(work, "new.txt", "new");
        git.discardChanges();
        assertEquals("v1", read(work, "logic"));
        assertFalse(new File(work, "new.txt").exists());
        assertEquals(List.of(), git.status());
    }

    @Test
    public void remoteAddressIsValidatedAndNeverHoldsASecret() throws Exception {
        for (String bad : new String[]{"", "git@github.com:me/p.git", "http://example.com/p.git", "https://me:secret@github.com/me/p.git", "https://token@github.com/me/p.git"}) {
            try {
                git.setRemoteUrl(bad);
                fail(bad);
            } catch (GitProblem expected) {
                // fine
            }
        }
        assertNull(git.remoteUrl());
        git.setRemoteUrl("https://github.com/me/project.git");
        assertEquals("https://github.com/me/project.git", git.remoteUrl());
    }

    @Test
    public void pushAndPullBetweenTwoCopiesThroughARemote() throws Exception {
        File remote = new File(temp.getRoot(), "remote.git");
        Git.init().setBare(true).setGitDir(remote).setInitialBranch("main").call().close();
        String url = remote.toURI().toString().replace("file:/", "file:///");

        write(work, "logic", "v1");
        commit(git, "first");
        git.setRemoteUrl(url);
        assertEquals("Pushed main", git.push(null));
        assertEquals("Already up to date", git.push(null));

        ProjectGit other = start("other");
        File otherWork = new File(temp.getRoot(), "other-data");
        other.setRemoteUrl(url);
        assertEquals("Pulled the latest changes", other.pull(null));
        assertEquals("v1", read(otherWork, "logic"));

        write(work, "logic", "v2");
        commit(git, "second");
        git.push(null);
        assertEquals("Pulled the latest changes", other.pull(null));
        assertEquals("v2", read(otherWork, "logic"));
        assertEquals("Already up to date", other.pull(null));
    }

    @Test
    public void pullRefusesADirtyTreeAndUndoesAConflictingMerge() throws Exception {
        File remote = new File(temp.getRoot(), "remote2.git");
        Git.init().setBare(true).setGitDir(remote).setInitialBranch("main").call().close();
        String url = remote.toURI().toString().replace("file:/", "file:///");

        write(work, "notes.txt", "line\n");
        commit(git, "first");
        git.setRemoteUrl(url);
        git.push(null);

        ProjectGit other = start("other2");
        File otherWork = new File(temp.getRoot(), "other2-data");
        other.setRemoteUrl(url);
        other.pull(null);

        write(work, "notes.txt", "mine\n");
        commit(git, "mine");
        git.push(null);

        write(otherWork, "notes.txt", "theirs\n");
        try {
            other.pull(null);
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("Commit or discard"));
        }
        commit(other, "theirs");
        try {
            other.pull(null);
            fail("same line changed on both sides");
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("same files"));
        }
        assertEquals("theirs\n", read(otherWork, "notes.txt"));
        assertEquals(List.of(), other.status());

        try {
            other.push(null);
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Pull first"));
        }
    }

    @Test
    public void pushWithoutARemoteOrCommitsExplainsWhy() throws Exception {
        try {
            git.push(null);
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("remote address"));
        }
        git.setRemoteUrl("https://github.com/me/p.git");
        try {
            git.push(null);
            fail();
        } catch (GitProblem expected) {
            assertTrue(expected.getMessage().contains("first commit"));
        }
    }
}
