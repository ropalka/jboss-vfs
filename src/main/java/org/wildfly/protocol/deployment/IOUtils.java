/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, JBoss Inc., and individual contributors as indicated
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
package org.wildfly.protocol.deployment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * I/O utilities.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class IOUtils {

    private static final String CURRENT_PATH = ".";
    private static final String REVERSE_PATH = "..";
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    private IOUtils() {
        // forbidden instantiation
    }

    /**
     * Extracts specified source recursively to the target directory.
     * Nested .jar, .sar, .rar, .war and .wab archives found in given source will be recursively extracted as well.
     * @param source source file or directory to be copied/extracted from
     * @param target target directory to be copied/extracted to
     * @throws IOException if some I/O error occurs
     */
    public static void extract(final File source, final File target) throws IOException {
        // preconditions
        if (source == null) throw new IllegalArgumentException("Source cannot be null");
        if (target == null) throw new IllegalArgumentException("Target cannot be null");
        if (!source.exists()) throw new IllegalArgumentException("Source '" + source.getAbsolutePath() + "' does not exist");
        if (!target.exists() && !target.mkdirs()) throw new IllegalArgumentException("Could not create target directory '" + target.getAbsolutePath() + "'");
        if (target.exists() && !target.isDirectory()) throw new IllegalArgumentException("Target '" + target.getAbsolutePath() + "' is not directory");
        if (target.exists() && !target.canWrite()) throw new IllegalArgumentException("Target directory '" + target.getAbsolutePath() + "' must be writable");

        if (source.isDirectory()) {
            extractDirectory(source, target);
        } else {
            JarInputStream jis = null;
            try {
                jis = new JarInputStream(new FileInputStream(source));
                extractArchive(jis, target);
            } finally {
                safeClose(jis);
            }
        }
    }

    static void extractDirectory(final File sourceDir, final File targetDir) throws IllegalArgumentException, IOException, SecurityException {
        File newFile;
        for (final File child : sourceDir.listFiles()) {
            newFile = new File(targetDir, child.getName());
            newFile.getParentFile().mkdirs();
            if (child.isDirectory()) {
                // copy directory recursively
                extract(child, newFile);
            } else {
                if (isNestedArchive(child.getName())) {
                    // extract nested archive
                    JarInputStream jis = null;
                    try {
                        jis = new JarInputStream(new FileInputStream(child));
                        extractArchive(jis, newFile);
                    } finally {
                        safeClose(jis);
                    }
                } else {
                    // copy file
                    FileInputStream fis = null;
                    FileOutputStream fos = null;
                    try {
                        fis = new FileInputStream(child);
                        fos = new FileOutputStream(newFile);
                        copy(fis, fos);
                    } finally {
                        safeClose(fis);
                        safeClose(fos);
                    }
                }
            }
        }
    }

    static void extractArchive(final JarInputStream jis, final File targetDir) throws IOException {
        JarEntry entry;
        File newFile;
        while ((entry = jis.getNextJarEntry()) != null) {
            String fileName = entry.getName();
            if (fileName.equals(CURRENT_PATH) || fileName.equals(REVERSE_PATH)) continue;
            // create directory structure
            newFile = new File(targetDir + File.separator + fileName);
            // extract zip
            if (!entry.isDirectory()) {
                new File(newFile.getParent()).mkdirs();
                if (isNestedArchive(entry.getName())) {
                    // extract nested archive recursively
                    ByteArrayOutputStream baos = null;
                    try {
                        baos = new ByteArrayOutputStream();
                        copy(jis, baos);
                        extractArchive(new JarInputStream(new ByteArrayInputStream(baos.toByteArray())), newFile);
                    } finally {
                        safeClose(baos);
                    }
                } else {
                    // extract zip entry to the disk
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(newFile);
                        copy(jis, fos);
                    } finally {
                        safeClose(fos);
                    }
                }
            }
        }
        // JarInputStream swallows manifest files - we need to extract them to the file system
        final Manifest manifest = jis.getManifest();
        if (manifest != null) {
            FileOutputStream fos = null;
            try {
                final File manifestFile = new File(targetDir, MANIFEST_PATH);
                manifestFile.getParentFile().mkdirs();
                manifestFile.createNewFile();
                fos = new FileOutputStream(manifestFile);
                manifest.write(fos);
            } finally {
                safeClose(fos);
            }
        }
    }

    /**
     * Deletes specified file or directory (recursively).
     * @param file file or directory to be deleted
     */
    public static boolean delete(final File file) {
        if (file == null) return true;
        if (!file.exists()) return true;
        if (file.isDirectory()) {
            for (final File child : file.listFiles()) {
                if (child.isDirectory()) {
                    delete(child);
                } else {
                    child.delete();
                }
            }
        }
        return file.delete();
    }

    /**
     * Safely closes the closeable object. All exceptions will be ignored.
     * @param closeable object that can be closed
     */
    public static void safeClose(final Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (final Throwable ignored) {}
    }

    private static boolean isNestedArchive(final String childName) {
        return childName.endsWith(".rar") || childName.endsWith(".sar") || childName.endsWith(".wab") || childName.endsWith(".war");
    }

    private static void copy(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
    }

}
