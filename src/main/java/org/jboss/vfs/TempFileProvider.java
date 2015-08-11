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
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A provider for temporary physical files and directories.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class TempFileProvider implements Closeable {

    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final File TMP_ROOT;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final File providerRoot;

    static {
        String configTmpDir = System.getProperty(JBOSS_TMP_DIR_PROPERTY);
        if (configTmpDir == null) { configTmpDir = System.getProperty(JVM_TMP_DIR_PROPERTY); }
        try {
            TMP_ROOT = new File(configTmpDir, "vfs");
            TMP_ROOT.mkdirs();
        } catch (Exception e) {
            throw VFSMessages.MESSAGES.cantSetupTempFileProvider(e);
        }
    }

    /**
     * Create a temporary file provider for a given type.
     * @param providerType the provider type string (used as a prefix in the temp file dir name)
     * @return the new provider
     * @throws IOException if an I/O error occurs
     */
    public static TempFileProvider create(String providerType) throws IOException {
        final File providerRoot = new File(TMP_ROOT, providerType);
        return new TempFileProvider(createTempDir(providerType, providerRoot));
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
        final String name = createTempName(originalName);
        final File f = new File(providerRoot, name);
        if (f.mkdirs()) {
            return new TempDir(this, f);
        }
        throw VFSMessages.MESSAGES.couldNotCreateDirectory(originalName);
    }

    private static File createTempDir(String prefix, File root) throws IOException {
        final File f = new File(root, createTempName(prefix));
        if (f.mkdirs()) {
            if (f.isDirectory()&&f.getParent()!=null){
                f.delete();
            }
            return f;
        }
        throw VFSMessages.MESSAGES.couldNotCreateDirectoryForRoot(root, prefix);
    }

    static String createTempName(final String prefix) {
        return prefix + LocalDateTime.now();
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
        if (!VFSUtils.recursiveDelete(root)) {
            VFSLogger.ROOT_LOGGER.tracef("Failed to delete root (%s).", root);
            root.deleteOnExit();
        }
    }
}
