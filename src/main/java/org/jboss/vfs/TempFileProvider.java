/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.vfs;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A provider for temporary physical files and directories.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TempFileProvider implements Closeable {

    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final String PROVIDER_TYPE = "vfs";
    private static final File TMP_ROOT;
    private static final int RETRIES = 10;
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final Random rng = new Random();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final File providerRoot;
    static final TempFileProvider INSTANCE;

    static {
        String configTmpDir = System.getProperty(JBOSS_TMP_DIR_PROPERTY);
        if (configTmpDir == null) { configTmpDir = System.getProperty(JVM_TMP_DIR_PROPERTY); }
        try {
            TMP_ROOT = new File(configTmpDir);
            TMP_ROOT.mkdirs();
            try {
                // The "clean existing" logic is as follows:
                // 1) Rename the root directory "foo" corresponding to the provider type to "bar"
                // 2) Submit a task to delete "bar" and its contents, in a background thread, to the the passed executor.
                // 3) Create a "foo" root directory for the provider type and return that TempFileProvider (while at the same time the background task is in progress)
                // This ensures that the "foo" root directory for the providerType is empty and the older content is being cleaned up in the background (without affecting the current processing),
                // thus simulating a "cleanup existing content"
                final File possiblyExistingProviderRoot = new File(TMP_ROOT, PROVIDER_TYPE);
                if (possiblyExistingProviderRoot.exists()) {
                    // rename it so that it can be deleted as a separate (background) task
                    final File toBeDeletedProviderRoot = new File(TMP_ROOT, createTempName(PROVIDER_TYPE + "-to-be-deleted-", ""));
                    final boolean renamed = possiblyExistingProviderRoot.renameTo(toBeDeletedProviderRoot);
                    if (!renamed) {
                        throw new IOException("Failed to rename " + possiblyExistingProviderRoot.getAbsolutePath() + " to " + toBeDeletedProviderRoot.getAbsolutePath());
                    } else {
                        // delete in the background
                        executor.submit(new DeleteTask(toBeDeletedProviderRoot, executor));
                    }
                }
            } catch (Throwable t) {
                // just log a message if existing contents couldn't be deleted
                VFSLogger.ROOT_LOGGER.failedToCleanExistingContentForTempFileProvider(PROVIDER_TYPE);
                // log the cause of the failure
                VFSLogger.ROOT_LOGGER.debug("Failed to clean existing content for temp file provider of type " + PROVIDER_TYPE, t);
            }
            // now create and return the TempFileProvider for the providerType
            final File providerRoot = new File(TMP_ROOT, PROVIDER_TYPE);
            INSTANCE = new TempFileProvider(createTempDir("", "", providerRoot));
        } catch (Exception e) {
            throw VFSMessages.MESSAGES.cantSetupTempFileProvider(e);
        }
    }

    private TempFileProvider(File providerRoot) {
        this.providerRoot = providerRoot;
    }

    /**
     * Create a temp directory, into which temporary files may be placed.
     *
     * @param originalName the original file name
     * @return the temp directory
     * @throws IOException for any error
     */
    TempDir createTempDir(String originalName) throws IOException {
        if (!open.get()) {
            throw VFSMessages.MESSAGES.tempFileProviderClosed();
        }
        final String name = createTempName(originalName + "-", "");
        final File f = new File(providerRoot, name);
        for (int i = 0; i < RETRIES; i++) {
            if (f.mkdirs()) {
                return new TempDir(this, f);
            }
        }
        throw VFSMessages.MESSAGES.couldNotCreateDirectory(originalName,RETRIES);
    }

    private static File createTempDir(String prefix, String suffix, File root) throws IOException {
        for (int i = 0; i < RETRIES; i++) {
            final File f = new File(root, createTempName(prefix, suffix));
            if (f.mkdirs()) {
                if (f.isDirectory()&&f.getParent()!=null){
                    f.delete();
                }
                return f;
            }
        }
        throw VFSMessages.MESSAGES.couldNotCreateDirectoryForRoot(
                root,
                prefix,
                suffix,
                RETRIES);
    }

    static String createTempName(String prefix, String suffix) {
        return prefix + Long.toHexString(rng.nextLong()) + suffix;
    }

    /**
     * Close this provider and delete any temp files associated with it.
     */
    public void close() throws IOException {
        if (open.getAndSet(false)) {
            delete(this.providerRoot);
        }
    }

    protected void finalize() {
        VFSUtils.safeClose(this);
    }

    /**
     * Deletes any temp files associated with this provider
     *
     * @throws IOException
     */
    void delete(final File root) throws IOException {
        new DeleteTask(root, executor).run();
    }

    static final class DeleteTask implements Runnable {

        private final File root;
        private ScheduledExecutorService retryExecutor;

        public DeleteTask(final File root, final ScheduledExecutorService retryExecutor) {
            this.root = root;
            this.retryExecutor = retryExecutor;
        }

        public void run() {
            if (VFSUtils.recursiveDelete(root) == false) {
                if (retryExecutor != null) {
                    VFSLogger.ROOT_LOGGER.tracef("Failed to delete root (%s), retrying in 30sec.", root);
                    retryExecutor.schedule(this, 30L, TimeUnit.SECONDS);
                } else {
                    VFSLogger.ROOT_LOGGER.tracef("Failed to delete root (%s).", root);
                }
            }
        }
    }
}
