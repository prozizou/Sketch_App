package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a plan into the project's {@code res} folder all together or not at all. Everything is checked first,
 * against the exact bytes to be written and the current state of the folder; nothing existing is ever overwritten
 * (a values file only gets entries added). The operation is recorded in the {@link ResourceJournal} before the first
 * file is touched, and if anything fails along the way what was done is undone, so the project is as it was.
 */
public final class ResourceTransaction {
    /**
     * @param success   whether the changes were written
     * @param message   one line for the person: what was done, or what stopped it
     * @param failures  per file, why it was refused (nothing was written then)
     * @param journal   the record of the operation (null when nothing was written)
     * @param created   the files created
     * @param modified  the values files that got entries
     */
    public record Result(boolean success, @NonNull String message, @NonNull List<Failure> failures, @Nullable ResourceJournal.Entry journal,
                         @NonNull List<String> created, @NonNull List<String> modified) {
        static Result failed(String message, List<Failure> failures) {
            return new Result(false, message, failures, null, List.of(), List.of());
        }
    }

    /** A change that was refused. */
    public record Failure(@NonNull String key, @NonNull String path, @NonNull String message) {
    }

    private ResourceTransaction() {
    }

    /**
     * @param resDir      the project's {@code res} folder
     * @param journalRoot where the operation's record goes
     * @param changes     what to write
     * @param validator   decides whether the build would accept each file and each values file
     */
    @NonNull
    public static Result apply(File resDir, File journalRoot, List<PlannedChange> changes, ResourceValidator validator) {
        if (changes.isEmpty()) return Result.failed("There is nothing to apply", List.of());

        // ---- 1. check everything; nothing is written yet ----
        List<Failure> failures = new ArrayList<>();
        Map<String, PlannedChange> files = new LinkedHashMap<>();
        Map<String, List<PlannedChange>> values = new LinkedHashMap<>();
        for (PlannedChange change : changes) {
            if (change.kind() == PlannedChange.Kind.FILE) {
                if (files.putIfAbsent(change.path().toLowerCase(Locale.ROOT), change) != null) {
                    failures.add(new Failure(change.key(), change.path(), "Two changes write " + change.path()));
                }
            } else {
                values.computeIfAbsent(change.path(), k -> new ArrayList<>()).add(change);
            }
        }
        for (PlannedChange change : files.values()) {
            String problem = fileProblem(resDir, change);
            if (problem != null) {
                failures.add(new Failure(change.key(), change.path(), problem));
                continue;
            }
            ResourceValidator.Result verdict = validator.validateFile(change.path(), change.content());
            if (!verdict.ok()) failures.add(new Failure(change.key(), change.path(), verdict.message()));
        }
        Map<String, String> newValuesText = new LinkedHashMap<>();
        Map<String, String> oldValuesText = new LinkedHashMap<>();
        for (Map.Entry<String, List<PlannedChange>> group : values.entrySet()) {
            String path = group.getKey();
            File file = safe(resDir, path);
            if (file == null) {
                for (PlannedChange change : group.getValue()) failures.add(new Failure(change.key(), path, "Not a path under res"));
                continue;
            }
            try {
                String current = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : null;
                List<ValuesFile.Entry> entries = new ArrayList<>();
                List<ValuesFile.Entry> existing = current == null ? List.of() : ValuesFile.parse(current);
                boolean problem = false;
                for (PlannedChange change : group.getValue()) {
                    ValuesFile.Entry entry = change.entry();
                    if (existing.stream().anyMatch(e -> e.type().equals(entry.type()) && ResourceNames.foldedKey(e.name()).equals(ResourceNames.foldedKey(entry.name())))) {
                        failures.add(new Failure(change.key(), path, entry.name() + " is already defined in " + path));
                        problem = true;
                    }
                    entries.add(entry);
                }
                if (problem) continue;
                String updated = ValuesFile.append(current, entries);
                ResourceValidator.Result verdict = validator.validateValues(path, updated);
                if (!verdict.ok()) {
                    for (PlannedChange change : group.getValue()) failures.add(new Failure(change.key(), path, verdict.message()));
                    continue;
                }
                newValuesText.put(path, updated);
                oldValuesText.put(path, current);
            } catch (IOException | SafeXml.InvalidXml e) {
                for (PlannedChange change : group.getValue()) failures.add(new Failure(change.key(), path, path + " can't be edited: " + e.getMessage()));
            }
        }
        if (!failures.isEmpty()) {
            return Result.failed(failures.size() == 1 ? failures.get(0).message() : failures.size() + " changes were refused; nothing was changed", failures);
        }

        // ---- 2. record the operation, then write ----
        long time = System.currentTimeMillis();
        File dir;
        List<ResourceJournal.Created> createdRecords = new ArrayList<>();
        List<ResourceJournal.Modified> modifiedRecords = new ArrayList<>();
        try {
            dir = ResourceJournal.newDirectory(journalRoot, time);
            for (PlannedChange change : files.values()) createdRecords.add(new ResourceJournal.Created(change.path(), ResourceJournal.hash(change.content())));
            for (Map.Entry<String, String> entry : newValuesText.entrySet()) {
                String before = oldValuesText.get(entry.getKey());
                List<String> lines = new ArrayList<>();
                for (PlannedChange change : values.get(entry.getKey())) lines.add(ValuesFile.render(change.entry()));
                if (before == null) {
                    // a values file made by this operation is a created file to undo
                    modifiedRecords.add(new ResourceJournal.Modified(entry.getKey(), "", ResourceJournal.hash(entry.getValue().getBytes(StandardCharsets.UTF_8)), "-", lines));
                } else {
                    String backup = "values-" + modifiedRecords.size() + ".bak";
                    Files.write(new File(new File(dir, ResourceJournal.BACKUPS), backup).toPath(), before.getBytes(StandardCharsets.UTF_8));
                    modifiedRecords.add(new ResourceJournal.Modified(entry.getKey(), ResourceJournal.hash(before.getBytes(StandardCharsets.UTF_8)),
                            ResourceJournal.hash(entry.getValue().getBytes(StandardCharsets.UTF_8)), backup, lines));
                }
            }
            ResourceJournal.write(dir, time, ResourceJournal.State.PENDING, createdRecords, modifiedRecords);
        } catch (IOException e) {
            return Result.failed("Couldn't prepare the undo record, so nothing was changed: " + e.getMessage(), List.of());
        }

        List<File> createdFiles = new ArrayList<>();
        List<File> createdDirs = new ArrayList<>();
        Map<File, byte[]> restore = new LinkedHashMap<>();
        try {
            for (PlannedChange change : files.values()) {
                File file = safe(resDir, change.path());
                createdDirs.addAll(makeDirs(file.getParentFile(), resDir));
                writeAtomically(file, change.content());
                createdFiles.add(file);
                if (!ResourceJournal.hash(Files.readAllBytes(file.toPath())).equals(ResourceJournal.hash(change.content()))) {
                    throw new IOException(change.path() + " wasn't written completely");
                }
            }
            for (Map.Entry<String, String> entry : newValuesText.entrySet()) {
                File file = safe(resDir, entry.getKey());
                String before = oldValuesText.get(entry.getKey());
                if (before == null) {
                    createdDirs.addAll(makeDirs(file.getParentFile(), resDir));
                    createdFiles.add(file);
                } else {
                    restore.put(file, before.getBytes(StandardCharsets.UTF_8));
                }
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                writeAtomically(file, bytes);
                if (!ResourceJournal.hash(Files.readAllBytes(file.toPath())).equals(ResourceJournal.hash(bytes))) {
                    throw new IOException(entry.getKey() + " wasn't written completely");
                }
            }
            ResourceJournal.write(dir, time, ResourceJournal.State.DONE, createdRecords, modifiedRecords);
        } catch (IOException | RuntimeException e) {
            rollback(createdFiles, createdDirs, restore);
            ResourceJournal.deleteRecursively(dir);
            return Result.failed("Couldn't write the changes (" + e.getMessage() + "); the project was left as it was", List.of());
        }
        ResourceJournal.prune(journalRoot);

        List<String> created = new ArrayList<>();
        for (ResourceJournal.Created c : createdRecords) created.add(c.path());
        List<String> modified = new ArrayList<>();
        for (ResourceJournal.Modified m : modifiedRecords) (m.hashBefore().isEmpty() ? created : modified).add(m.path());
        int count = changes.size();
        return new Result(true, count == 1 ? "1 resource added" : count + " resources added", List.of(), ResourceJournal.read(dir), created, modified);
    }

    /** Why a new file can't go where planned, or null: its place is taken (by any extension, in any letter case). */
    private static String fileProblem(File resDir, PlannedChange change) {
        File file = safe(resDir, change.path());
        if (file == null) return "Not a path under res";
        File parent = file.getParentFile();
        String name = file.getName();
        String base = name.substring(0, name.indexOf('.') < 0 ? name.length() : name.indexOf('.'));
        String[] siblings = parent.list();
        if (siblings != null) {
            for (String sibling : siblings) {
                String siblingBase = sibling.substring(0, sibling.indexOf('.') < 0 ? sibling.length() : sibling.indexOf('.'));
                if (siblingBase.equalsIgnoreCase(base)) return sibling + " already exists in " + parent.getName();
            }
        }
        return null;
    }

    /** The file under {@code resDir}, or null if the path would leave it. */
    @Nullable
    private static File safe(File resDir, String path) {
        try {
            File file = new File(resDir, path);
            return file.getCanonicalPath().startsWith(resDir.getCanonicalPath() + File.separator) ? file : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<File> makeDirs(File dir, File resDir) throws IOException {
        List<File> made = new ArrayList<>();
        List<File> missing = new ArrayList<>();
        for (File d = dir; d != null && !d.isDirectory(); d = d.getParentFile()) missing.add(0, d);
        for (File d : missing) {
            if (!d.mkdir() && !d.isDirectory()) throw new IOException("Couldn't create " + d.getName());
            made.add(d);
        }
        return made;
    }

    private static void writeAtomically(File file, byte[] bytes) throws IOException {
        File temp = new File(file.getParentFile(), "." + file.getName() + ".resolver.tmp");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            temp.delete();
        }
    }

    private static void rollback(List<File> createdFiles, List<File> createdDirs, Map<File, byte[]> restore) {
        for (File file : createdFiles) file.delete();
        for (Map.Entry<File, byte[]> entry : restore.entrySet()) {
            try {
                Files.write(entry.getKey().toPath(), entry.getValue());
            } catch (IOException ignored) {
                // The record of the operation was removed; the values file still has the lines of a failed write at worst.
            }
        }
        for (int i = createdDirs.size() - 1; i >= 0; i--) createdDirs.get(i).delete();
    }
}
