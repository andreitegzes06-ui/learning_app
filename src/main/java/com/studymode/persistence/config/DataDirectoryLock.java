package com.studymode.persistence.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DataDirectoryLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private DataDirectoryLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
    public static DataDirectoryLock acquire(Path root) throws IOException {
        Files.createDirectories(root);
        FileChannel channel = FileChannel.open(root.resolve("studymode.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Study Mode is already using this data folder. Close the other instance or choose another --data-dir.");
            return new DataDirectoryLock(channel, lock);
        } catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Could not lock the data folder. Another Study Mode instance may be running.", e);
        }
    }
    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
