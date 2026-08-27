package com.donutsmp.rtpmapper.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Crash-resistant sibling-temp writes with a one-generation recovery journal. */
public final class AtomicFileIO {
    private AtomicFileIO() {
    }

    public static void writeUtf8(Path target, String content) throws IOException {
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path target, byte[] content) throws IOException {
        writeInternal(target, content, true, true);
    }

    public static void writeNew(Path target, byte[] content) throws IOException {
        writeInternal(target, content, false, false);
    }

    public static Path journalPath(Path target) {
        Path absolute = target.toAbsolutePath();
        return absolute.resolveSibling(absolute.getFileName() + ".journal");
    }

    public static boolean recoverMissingTarget(Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path journal = journalPath(absolute);
        if (Files.exists(absolute) || !Files.exists(journal)) {
            return false;
        }
        Files.copy(journal, absolute, StandardCopyOption.REPLACE_EXISTING);
        forceFile(absolute);
        return true;
    }

    public static void restoreJournal(Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path journal = journalPath(absolute);
        if (!Files.exists(journal)) {
            throw new IOException("No recovery journal exists for " + absolute);
        }
        Files.copy(journal, absolute, StandardCopyOption.REPLACE_EXISTING);
        forceFile(absolute);
        Files.deleteIfExists(journal);
    }

    public static void discardJournal(Path target) throws IOException {
        Files.deleteIfExists(journalPath(target));
    }

    private static void writeInternal(Path target, byte[] content, boolean replace, boolean journal)
            throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + target);
        }
        Files.createDirectories(parent);

        Path temporary = Files.createTempFile(parent, "." + absolute.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            Path recoveryJournal = journalPath(absolute);
            if (journal && Files.exists(absolute)) {
                Files.copy(absolute, recoveryJournal, StandardCopyOption.REPLACE_EXISTING);
                forceFile(recoveryJournal);
            }

            move(temporary, absolute, replace);
            moved = true;
            if (journal) {
                Files.deleteIfExists(recoveryJournal);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        StandardCopyOption[] atomicOptions = replace
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        StandardCopyOption[] fallbackOptions = replace
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{};
        try {
            Files.move(source, target, atomicOptions);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, fallbackOptions);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }
}
