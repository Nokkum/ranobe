package org.ranobe.ranobe.util;

import android.content.ContentResolver;
import android.net.Uri;

import org.ranobe.ranobe.database.RanobeDatabase;
import org.ranobe.ranobe.models.Novel;
import org.ranobe.ranobe.models.ReadHistory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Exports and imports the library bookmarks and the reading history as JSON files (see BackupCodec for the
// format, BackupMerge for what an import does). The work is blocking, so call it off the main thread. It never
// throws: whatever goes wrong comes back as the Result's problem.
public final class BackupManager {
    private BackupManager() {
    }

    public enum Kind {EXPORT_LIBRARY, EXPORT_HISTORY, IMPORT_LIBRARY, IMPORT_HISTORY}

    public enum Problem {
        NONE,
        READ_FAILED,
        WRITE_FAILED,
        NOT_BACKUP,
        // the file is a backup, but of the other kind than the one being imported
        FILE_IS_LIBRARY_BACKUP,
        FILE_IS_HISTORY_BACKUP,
        NEWER_VERSION,
        TOO_LARGE,
        FAILED
    }

    public static final class Result {
        public final Kind kind;
        public final Problem problem;
        // exports: how many entries were written. Imports: how many were added.
        public final int added;
        // history imports: entries that replaced an older copy of the same chapter
        public final int updated;
        // imports: entries left alone because the device already had them (history: with the same or a later time)
        public final int kept;
        // imports: entries in the file that were too broken to use
        public final int invalid;

        private Result(Kind kind, Problem problem, int added, int updated, int kept, int invalid) {
            this.kind = kind;
            this.problem = problem;
            this.added = added;
            this.updated = updated;
            this.kept = kept;
            this.invalid = invalid;
        }

        static Result done(Kind kind, int added, int updated, int kept, int invalid) {
            return new Result(kind, Problem.NONE, added, updated, kept, invalid);
        }

        static Result failed(Kind kind, Problem problem) {
            return new Result(kind, problem, 0, 0, 0, 0);
        }

        public boolean succeeded() {
            return problem == Problem.NONE;
        }
    }

    public static Result run(Kind kind, ContentResolver resolver, Uri uri) {
        switch (kind) {
            case EXPORT_LIBRARY:
                return exportLibrary(resolver, uri);
            case EXPORT_HISTORY:
                return exportHistory(resolver, uri);
            case IMPORT_LIBRARY:
                return importLibrary(resolver, uri);
            default:
                return importHistory(resolver, uri);
        }
    }

    // ---- export ----

    private static Result exportLibrary(ContentResolver resolver, Uri uri) {
        try {
            List<Novel> novels = new ArrayList<>();
            for (Novel novel : RanobeDatabase.database().novels().listSync()) {
                if (novel.url != null && !novel.url.isEmpty()) novels.add(novel);
            }
            Collections.sort(novels, (a, b) -> String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name)));
            write(resolver, uri, BackupCodec.libraryToJson(novels, System.currentTimeMillis()));
            return Result.done(Kind.EXPORT_LIBRARY, novels.size(), 0, 0, 0);
        } catch (IOException | SecurityException e) {
            return Result.failed(Kind.EXPORT_LIBRARY, Problem.WRITE_FAILED);
        } catch (Exception e) {
            return Result.failed(Kind.EXPORT_LIBRARY, Problem.FAILED);
        }
    }

    private static Result exportHistory(ContentResolver resolver, Uri uri) {
        try {
            // only the last chapter read of each novel, not every chapter ever opened
            List<ReadHistory> history = BackupMerge.lastReadPerNovel(RanobeDatabase.database().readHistory().listAllSync());
            write(resolver, uri, BackupCodec.historyToJson(history, System.currentTimeMillis()));
            return Result.done(Kind.EXPORT_HISTORY, history.size(), 0, 0, 0);
        } catch (IOException | SecurityException e) {
            return Result.failed(Kind.EXPORT_HISTORY, Problem.WRITE_FAILED);
        } catch (Exception e) {
            return Result.failed(Kind.EXPORT_HISTORY, Problem.FAILED);
        }
    }

    // "wt" empties the file first, so a shorter backup doesn't leave the tail of an older, longer one behind
    private static void write(ContentResolver resolver, Uri uri, String text) throws IOException {
        OutputStream out = resolver.openOutputStream(uri, "wt");
        if (out == null) throw new IOException("no output stream for " + uri);
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
    }

    // ---- import ----

    private static Result importLibrary(ContentResolver resolver, Uri uri) {
        final List<Novel> incoming;
        final int invalid;
        try {
            BackupCodec.Parsed<Novel> parsed = BackupCodec.parseLibrary(read(resolver, uri));
            incoming = parsed.items;
            invalid = parsed.invalid;
        } catch (BackupCodec.BackupException e) {
            return Result.failed(Kind.IMPORT_LIBRARY, problemFor(e));
        } catch (IOException | SecurityException e) {
            return Result.failed(Kind.IMPORT_LIBRARY, Problem.READ_FAILED);
        }

        try {
            RanobeDatabase database = RanobeDatabase.database();
            // one transaction: the library ends up with all of the file's new novels, or none of them
            BackupMerge.LibraryPlan plan = database.runInTransaction(() -> {
                Set<String> existing = new HashSet<>();
                for (Novel novel : database.novels().listSync()) existing.add(novel.url);
                BackupMerge.LibraryPlan merge = BackupMerge.planLibrary(existing, incoming);
                for (Novel novel : merge.toSave) database.novels().save(novel);
                return merge;
            });
            return Result.done(Kind.IMPORT_LIBRARY, plan.toSave.size(), 0, plan.alreadySaved, invalid);
        } catch (RuntimeException e) {
            return Result.failed(Kind.IMPORT_LIBRARY, Problem.FAILED);
        }
    }

    private static Result importHistory(ContentResolver resolver, Uri uri) {
        final List<ReadHistory> incoming;
        final int invalid;
        try {
            BackupCodec.Parsed<ReadHistory> parsed = BackupCodec.parseHistory(read(resolver, uri));
            incoming = parsed.items;
            invalid = parsed.invalid;
        } catch (BackupCodec.BackupException e) {
            return Result.failed(Kind.IMPORT_HISTORY, problemFor(e));
        } catch (IOException | SecurityException e) {
            return Result.failed(Kind.IMPORT_HISTORY, Problem.READ_FAILED);
        }

        try {
            RanobeDatabase database = RanobeDatabase.database();
            BackupMerge.HistoryPlan plan = database.runInTransaction(() -> {
                Map<String, Long> existing = new HashMap<>();
                for (ReadHistory entry : database.readHistory().listAllSync()) existing.put(entry.url, entry.timestamp);
                BackupMerge.HistoryPlan merge = BackupMerge.planHistory(existing, incoming);
                for (ReadHistory entry : merge.toSave) database.readHistory().save(entry);
                return merge;
            });
            return Result.done(Kind.IMPORT_HISTORY, plan.added, plan.updated, plan.kept, invalid);
        } catch (RuntimeException e) {
            return Result.failed(Kind.IMPORT_HISTORY, Problem.FAILED);
        }
    }

    private static String read(ContentResolver resolver, Uri uri) throws IOException, BackupCodec.BackupException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("no input stream for " + uri);
        try {
            return BackupCodec.readText(in);
        } finally {
            in.close();
        }
    }

    private static Problem problemFor(BackupCodec.BackupException e) {
        switch (e.reason) {
            case NEWER_VERSION:
                return Problem.NEWER_VERSION;
            case TOO_LARGE:
                return Problem.TOO_LARGE;
            case WRONG_TYPE:
                return BackupCodec.TYPE_LIBRARY.equals(e.foundType)
                        ? Problem.FILE_IS_LIBRARY_BACKUP : Problem.FILE_IS_HISTORY_BACKUP;
            default:
                return Problem.NOT_BACKUP;
        }
    }
}
