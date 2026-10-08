package pro.sketchware.activities.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Version control for one Sketchware project, on top of JGit.
 * <p>
 * The project's data folder is the working tree. The repository itself is kept outside of it, so that
 * backups and snapshots of the project never contain it and restoring a project can't delete it.
 */
public final class ProjectGit implements AutoCloseable {
    public enum ChangeKind {ADDED, MODIFIED, DELETED}

    public record Change(String path, ChangeKind kind) {
    }

    public record Commit(String id, String message, String author, long time) {
        public String shortId() {
            return id.substring(0, 7);
        }
    }

    /** Where the files go; the token is a password or a personal access token. */
    public record Credentials(String user, String token) {
    }

    /** Raised for problems the user can fix, with a message fit to show. */
    public static final class GitProblem extends Exception {
        public GitProblem(String message) {
            super(message);
        }

        public GitProblem(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final String DEFAULT_BRANCH = "main";
    private static final int MAX_DIFF_CHARS = 200_000;
    /** Things Sketchware writes into the data folder that aren't part of the project. */
    private static final String IGNORE_FILE = "compile_log\n*.tmp\n";

    private final Git git;
    private final Repository repository;

    private ProjectGit(Git git) {
        this.git = git;
        this.repository = git.getRepository();
    }

    /** Where a project's repository is kept: next to its data, but not inside it. */
    public static File gitDirOf(File sketchRoot, String scId) {
        return new File(sketchRoot, "git/" + scId + ".git");
    }

    /** The project's data folder, which Git treats as the working tree. */
    public static File workTreeOf(File sketchRoot, String scId) {
        return new File(sketchRoot, "data/" + scId);
    }

    public static boolean exists(File gitDir) {
        return new File(gitDir, "HEAD").isFile();
    }

    /** Starts version control for the project, with no commit yet. */
    public static ProjectGit init(File gitDir, File workTree) throws GitProblem {
        try {
            if (!exists(gitDir)) {
                Git.init().setGitDir(gitDir).setDirectory(workTree).setInitialBranch(DEFAULT_BRANCH).call().close();
            }
            // JGit leaves a pointer file in the working tree; the repository is always opened by explicit paths
            File pointer = new File(workTree, ".git");
            if (pointer.isFile()) pointer.delete();
            File ignore = new File(workTree, ".gitignore");
            if (!ignore.exists()) java.nio.file.Files.writeString(ignore.toPath(), IGNORE_FILE);
            return open(gitDir, workTree);
        } catch (GitAPIException | IOException e) {
            throw new GitProblem("Couldn't start version control: " + e.getMessage(), e);
        }
    }

    public static ProjectGit open(File gitDir, File workTree) throws GitProblem {
        try {
            Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(workTree).setMustExist(true).build();
            makeIndependentOfUserConfig(repository);
            return new ProjectGit(new Git(repository));
        } catch (IOException e) {
            throw new GitProblem("Couldn't open the repository: " + e.getMessage(), e);
        }
    }

    /** Commits are never signed here, whatever a global Git configuration on the device says. */
    private static void makeIndependentOfUserConfig(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();
        if ("false".equals(config.getString("commit", null, "gpgsign")) && "openpgp".equals(config.getString("gpg", null, "format"))) return;
        config.setString("commit", null, "gpgsign", "false");
        config.setString("gpg", null, "format", "openpgp");
        config.save();
    }

    @Override
    public void close() {
        git.close();
    }

    // ---- state

    /** The current branch, or the short commit ID if HEAD is detached. */
    public String branch() throws GitProblem {
        try {
            String branch = repository.getBranch();
            return branch == null ? "?" : branch.length() == 40 ? branch.substring(0, 7) : branch;
        } catch (IOException e) {
            throw new GitProblem(e.getMessage(), e);
        }
    }

    public boolean hasCommits() throws GitProblem {
        try {
            return repository.resolve(Constants.HEAD) != null;
        } catch (IOException e) {
            throw new GitProblem(e.getMessage(), e);
        }
    }

    /**
     * Makes Git compare file contents instead of trusting file sizes and timestamps. A file rewritten with
     * the same size in the fraction of a second after a commit would otherwise look unchanged.
     */
    private void distrustFileStats() throws GitProblem {
        org.eclipse.jgit.dircache.DirCache cache = null;
        try {
            if (!new File(repository.getDirectory(), "index").isFile()) return;
            cache = repository.lockDirCache();
            for (int i = 0; i < cache.getEntryCount(); i++) cache.getEntry(i).setLength(0);
            cache.write();
            cache.commit();
        } catch (IOException e) {
            if (cache != null) cache.unlock();
            throw new GitProblem("Couldn't read the project files: " + e.getMessage(), e);
        }
    }

    public List<Change> status() throws GitProblem {
        distrustFileStats();
        try {
            Status status = git.status().call();
            List<Change> changes = new ArrayList<>();
            for (String path : new TreeSet<>(status.getAdded())) changes.add(new Change(path, ChangeKind.ADDED));
            for (String path : new TreeSet<>(status.getUntracked())) changes.add(new Change(path, ChangeKind.ADDED));
            Set<String> modified = new TreeSet<>(status.getChanged());
            modified.addAll(status.getModified());
            for (String path : modified) changes.add(new Change(path, ChangeKind.MODIFIED));
            Set<String> deleted = new TreeSet<>(status.getRemoved());
            deleted.addAll(status.getMissing());
            for (String path : deleted) changes.add(new Change(path, ChangeKind.DELETED));
            changes.sort((a, b) -> a.path().compareTo(b.path()));
            return changes;
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't read the status: " + e.getMessage(), e);
        }
    }

    // ---- commits

    /** Stages everything, including deletions, and commits it. @return null if there was nothing to commit */
    public Commit commitAll(String message, String authorName, String authorEmail) throws GitProblem {
        if (message == null || message.isBlank()) throw new GitProblem("Write a message that says what changed.");
        if (authorName == null || authorName.isBlank()) throw new GitProblem("Set your name first.");
        distrustFileStats();
        try {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call(); // also stage deletions
            if (git.status().call().isClean()) return null;
            RevCommit commit = git.commit().setMessage(message.strip())
                    .setAuthor(new PersonIdent(authorName.strip(), authorEmail == null ? "" : authorEmail.strip()))
                    .setCommitter(new PersonIdent(authorName.strip(), authorEmail == null ? "" : authorEmail.strip()))
                    .call();
            return toCommit(commit);
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't commit: " + e.getMessage(), e);
        }
    }

    /** Newest first. */
    public List<Commit> log(int max) throws GitProblem {
        if (!hasCommits()) return Collections.emptyList();
        try {
            List<Commit> commits = new ArrayList<>();
            for (RevCommit commit : git.log().setMaxCount(max).call()) commits.add(toCommit(commit));
            return commits;
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't read the history: " + e.getMessage(), e);
        }
    }

    private static Commit toCommit(RevCommit commit) {
        return new Commit(commit.getName(), commit.getShortMessage(), commit.getAuthorIdent().getName(), commit.getCommitTime() * 1000L);
    }

    // ---- diffs

    /** What a commit changed compared with its parent, as a unified diff. */
    public String diffOfCommit(String commitId) throws GitProblem {
        try (RevWalk walk = new RevWalk(repository); ObjectReader reader = repository.newObjectReader()) {
            RevCommit commit = walk.parseCommit(repository.resolve(commitId));
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            if (commit.getParentCount() > 0) {
                oldTree.reset(reader, walk.parseCommit(commit.getParent(0)).getTree());
            }
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, commit.getTree());
            return format(oldTree, newTree);
        } catch (IOException e) {
            throw new GitProblem("Couldn't read the changes: " + e.getMessage(), e);
        }
    }

    /** What is changed since the last commit, as a unified diff. */
    public String diffOfWorkingTree() throws GitProblem {
        distrustFileStats();
        try (ObjectReader reader = repository.newObjectReader()) {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            ObjectId head = repository.resolve(Constants.HEAD + "^{tree}");
            if (head != null) oldTree.reset(reader, head);
            var newTree = new org.eclipse.jgit.dircache.DirCacheIterator(repository.readDirCache());
            return format(head == null ? new org.eclipse.jgit.treewalk.EmptyTreeIterator() : oldTree, newTree);
        } catch (IOException | GitAPIException e) {
            throw new GitProblem("Couldn't read the changes: " + e.getMessage(), e);
        }
    }

    private String format(org.eclipse.jgit.treewalk.AbstractTreeIterator oldTree, org.eclipse.jgit.treewalk.AbstractTreeIterator newTree) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            formatter.format(entries);
        }
        String text = out.toString(StandardCharsets.UTF_8);
        return text.length() > MAX_DIFF_CHARS ? text.substring(0, MAX_DIFF_CHARS) + "\n… (the rest is not shown)\n" : text;
    }

    // ---- branches

    public List<String> branches() throws GitProblem {
        try {
            List<String> names = new ArrayList<>();
            for (Ref ref : git.branchList().call()) names.add(Repository.shortenRefName(ref.getName()));
            Collections.sort(names);
            return names;
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't list the branches: " + e.getMessage(), e);
        }
    }

    /** Creates a branch at the current commit and switches to it. */
    public void createBranch(String name) throws GitProblem {
        if (!Repository.isValidRefName(Constants.R_HEADS + name)) throw new GitProblem("'" + name + "' isn't a valid branch name.");
        if (!hasCommits()) throw new GitProblem("Make a first commit before creating a branch.");
        requireClean("Commit or discard your changes before creating a branch.");
        try {
            git.checkout().setCreateBranch(true).setName(name).call();
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't create the branch: " + e.getMessage(), e);
        }
    }

    public void switchBranch(String name) throws GitProblem {
        requireClean("Commit or discard your changes before switching branch.");
        try {
            git.checkout().setName(name).call();
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't switch branch: " + e.getMessage(), e);
        }
    }

    // ---- going back

    /**
     * Makes the files match what they were at {@code commitId}, as uncommitted changes on top of the
     * current branch, so the history stays intact and the result can be reviewed and committed.
     */
    public void restoreFilesFrom(String commitId) throws GitProblem {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit target = walk.parseCommit(repository.resolve(commitId));
            Set<String> inTarget = new TreeSet<>();
            try (TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(target.getTree());
                tree.setRecursive(true);
                while (tree.next()) inTarget.add(tree.getPathString());
            }
            for (String tracked : trackedPaths()) {
                if (!inTarget.contains(tracked)) {
                    File file = new File(repository.getWorkTree(), tracked);
                    if (file.exists() && !file.delete()) throw new GitProblem("Couldn't delete " + tracked);
                }
            }
            if (!inTarget.isEmpty()) {
                git.checkout().setStartPoint(target).setAllPaths(true).call();
                git.reset().setMode(ResetCommand.ResetType.MIXED).call(); // keep the result as plain working-tree changes
            }
        } catch (IOException | GitAPIException e) {
            throw new GitProblem("Couldn't restore the files: " + e.getMessage(), e);
        }
    }

    /** Throws away all changes since the last commit, including new files. */
    public void discardChanges() throws GitProblem {
        try {
            if (hasCommits()) git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).call();
        } catch (GitAPIException e) {
            throw new GitProblem("Couldn't discard the changes: " + e.getMessage(), e);
        }
    }

    private Set<String> trackedPaths() throws IOException {
        Set<String> paths = new TreeSet<>();
        ObjectId head = repository.resolve(Constants.HEAD + "^{tree}");
        if (head == null) return paths;
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(head);
            tree.setRecursive(true);
            while (tree.next()) paths.add(tree.getPathString());
        }
        return paths;
    }

    private void requireClean(String message) throws GitProblem {
        if (!status().isEmpty()) throw new GitProblem(message);
    }

    // ---- remote

    public String remoteUrl() {
        return repository.getConfig().getString("remote", "origin", "url");
    }

    public void setRemoteUrl(String url) throws GitProblem {
        String trimmed = url == null ? "" : url.strip();
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("file://")) {
            throw new GitProblem("Use an https:// address, like https://github.com/you/project.git");
        }
        String authority = trimmed.startsWith("https://") ? trimmed.substring("https://".length()).split("/", 2)[0] : "";
        if (authority.contains("@")) {
            throw new GitProblem("Don't put your password or token in the address. You'll be asked for it when you push or pull.");
        }
        try {
            StoredConfig config = repository.getConfig();
            config.setString("remote", "origin", "url", trimmed);
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
        } catch (IOException e) {
            throw new GitProblem("Couldn't save the address: " + e.getMessage(), e);
        }
    }

    /** Sends the current branch to {@code origin}. */
    public String push(Credentials credentials) throws GitProblem {
        requireRemote();
        if (!hasCommits()) throw new GitProblem("Make a first commit before pushing.");
        try {
            String branch = repository.getBranch();
            var command = git.push().setRemote("origin").add(branch);
            if (credentials != null) command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentials.user(), credentials.token()));
            for (PushResult result : command.call()) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    switch (update.getStatus()) {
                        case OK -> {
                            StoredConfig config = repository.getConfig();
                            config.setString("branch", branch, "remote", "origin");
                            config.setString("branch", branch, "merge", Constants.R_HEADS + branch);
                            config.save();
                            return "Pushed " + branch;
                        }
                        case UP_TO_DATE -> {
                            return "Already up to date";
                        }
                        case REJECTED_NONFASTFORWARD -> throw new GitProblem("The remote has newer changes. Pull first, then push again.");
                        case REJECTED_OTHER_REASON, REJECTED_REMOTE_CHANGED, REJECTED_NODELETE ->
                                throw new GitProblem("The remote refused the push" + (update.getMessage() == null ? "." : ": " + update.getMessage()));
                        default -> throw new GitProblem("Push failed: " + update.getStatus());
                    }
                }
            }
            return "Pushed " + branch;
        } catch (GitAPIException | IOException e) {
            throw new GitProblem(describeNetworkError(e), e);
        }
    }

    /**
     * Fetches {@code origin} and merges its version of the current branch. Needs a clean tree. If the merge
     * conflicts it is undone and nothing changes.
     *
     * @return what happened, in a sentence
     */
    public String pull(Credentials credentials) throws GitProblem {
        requireRemote();
        // The ignore file written when version control started would clash with the one in the remote
        File ignore = new File(repository.getWorkTree(), ".gitignore");
        try {
            if (ignore.isFile() && !trackedPaths().contains(".gitignore") && IGNORE_FILE.equals(java.nio.file.Files.readString(ignore.toPath()))) {
                ignore.delete();
            }
        } catch (IOException e) {
            throw new GitProblem("Couldn't prepare the pull: " + e.getMessage(), e);
        }
        try {
            return pullClean(credentials);
        } finally {
            if (!ignore.exists()) {
                try {
                    java.nio.file.Files.writeString(ignore.toPath(), IGNORE_FILE);
                } catch (IOException ignored) {
                    // Only a convenience: the project still works without it
                }
            }
        }
    }

    private String pullClean(Credentials credentials) throws GitProblem {
        requireClean("Commit or discard your changes before pulling.");
        try {
            String branch = repository.getBranch();
            var command = git.pull().setRemote("origin").setRemoteBranchName(branch);
            if (credentials != null) command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(credentials.user(), credentials.token()));
            ObjectId before = repository.resolve(Constants.HEAD);
            PullResult result = command.call();
            if (result.isSuccessful()) {
                ObjectId after = repository.resolve(Constants.HEAD);
                return before != null && before.equals(after) ? "Already up to date" : "Pulled the latest changes";
            }
            MergeResult merge = result.getMergeResult();
            if (merge != null && merge.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING) {
                git.reset().setMode(ResetCommand.ResetType.HARD).call();
                throw new GitProblem("Your changes and the remote's changes touch the same files. The pull was cancelled and nothing was changed.");
            }
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            throw new GitProblem("The pull couldn't be completed" + (merge == null ? "." : ": " + merge.getMergeStatus()));
        } catch (GitAPIException | IOException e) {
            throw new GitProblem(describeNetworkError(e), e);
        }
    }

    private void requireRemote() throws GitProblem {
        if (remoteUrl() == null) throw new GitProblem("Set the remote address first.");
    }

    private static String describeNetworkError(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("not authorized") || lower.contains("authentication") || lower.contains("401") || lower.contains("403")) {
            return "The server refused your user name or token.";
        }
        if (lower.contains("unknownhost") || lower.contains("unable to resolve") || lower.contains("timed out") || lower.contains("connect")) {
            return "Couldn't reach the server. Check your connection and the address.";
        }
        if (lower.contains("not found") || lower.contains("404")) {
            return "The repository wasn't found at that address.";
        }
        return message;
    }
}
