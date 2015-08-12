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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * I/O utilities.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class IOUtils {

    private IOUtils() {
        // forbidden instantiation
    }

    /**
     * Extracts specified source directory recursively to the target directory.
     * Nested .war, .jar, .sar or .rar archives found in given source directory
     * will be recursively extracted as well.
     * @param sourceDir source directory to be copied/extracted from
     * @param targetDir target directory to be copied/extracted to
     * @throws IOException if some I/O error occurs
     */
    public static void extract(final File sourceDir, final File targetDir) throws IllegalArgumentException, IOException, SecurityException {
        // preconditions
        if (sourceDir == null) throw new IllegalArgumentException("Source directory cannot be null");
        if (targetDir == null) throw new IllegalArgumentException("Target directory cannot be null");
        if (!sourceDir.exists()) throw new IllegalArgumentException("Source directory '" + sourceDir.getAbsolutePath() + "' does not exist");
        if (sourceDir.isFile()) throw new IllegalArgumentException("Source '" + sourceDir.getAbsolutePath() + "' is not directory");
        if (targetDir.exists() && !targetDir.isDirectory()) throw new IllegalArgumentException("Target '" + targetDir.getAbsolutePath() + "' is not directory");
        if (targetDir.exists() && !targetDir.canWrite()) throw new IllegalArgumentException("Target directory '" + targetDir.getAbsolutePath() + "' must be writable");

        File newFile;
        for (final File child : sourceDir.listFiles()) {
            newFile =  new File(targetDir, child.getName());
            newFile.getParentFile().mkdirs();
            if (child.isDirectory()) {
                // copy directory recursively
                extract(child, newFile);
            } else {
                if (isNestedArchive(child.getName())) {
                    // extract nested archive
                    ZipInputStream zis = null;
                    try {
                        zis = new ZipInputStream(new FileInputStream(child));
                        extract(zis, newFile);
                    } finally {
                        safeClose(zis);
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

    /**
     * Extracts zip stream to the specified target directory.
     * Nested .war, .jar, .sar or .rar archives found in given zip archive
     * will be recursively extracted as well.
     * This method does not close ZipInputStream object passed as parameter.
     * @param zis zip input stream to be extracted
     * @param targetDir directory where to recursively extract the zip archive
     * @throws IOException if some I/O error occurs
     */
    public static void extract(final ZipInputStream zis, final File targetDir) throws IOException {
        // preconditions
        if (zis == null) throw new IllegalArgumentException("Zip input stream cannot be null");
        if (targetDir == null) throw new IllegalArgumentException("Target directory cannot be null");
        if (targetDir.exists() && !targetDir.isDirectory()) throw new IllegalArgumentException("Target '" + targetDir.getAbsolutePath() + "' is not directory");
        if (targetDir.exists() && !targetDir.canWrite()) throw new IllegalArgumentException("Target directory '" + targetDir.getAbsolutePath() + "' must be writable");

        ZipEntry entry;
        File newFile;
        while ((entry = zis.getNextEntry()) != null) {
            String fileName = entry.getName();
            // create directory structure
            newFile = new File(targetDir + File.separator + fileName);
            new File(newFile.getParent()).mkdirs();
            // extract zip
            if (!entry.isDirectory()) {
                if (isNestedArchive(entry.getName())) {
                    // extract nested archive recursively
                    ByteArrayOutputStream baos = null;
                    try {
                        baos = new ByteArrayOutputStream();
                        copy(zis, baos);
                        extract(new ZipInputStream(new ByteArrayInputStream(baos.toByteArray())), newFile);
                    } finally {
                        safeClose(baos);
                    }
                } else {
                    // extract zip entry to the disk
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(newFile);
                        copy(zis, fos);
                    } finally {
                        safeClose(fos);
                    }
                }
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
        return childName.endsWith(".jar") || childName.endsWith(".rar") || childName.endsWith(".sar") || childName.endsWith(".war");
    }

    private static void copy(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
    }

}
