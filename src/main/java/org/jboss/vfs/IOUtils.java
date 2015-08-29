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

package org.jboss.vfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * I/O utilities.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class IOUtils {

    private static final String CURRENT_PATH = ".";
    private static final String REVERSE_PATH = "..";

    private IOUtils() {
        // forbidden instantiation
    }

    static void extractArchive(final ZipInputStream zis, final File targetDir) throws IOException {
        // preconditions
        if (zis == null) throw new IllegalArgumentException("Zip input stream cannot be null");
        if (targetDir == null) throw new IllegalArgumentException("Target directory cannot be null");
        if (!targetDir.exists() && !targetDir.mkdirs()) throw new IllegalArgumentException("Could not create target directory '" + targetDir.getAbsolutePath() + "'");
        if (targetDir.exists() && !targetDir.isDirectory()) throw new IllegalArgumentException("Target '" + targetDir.getAbsolutePath() + "' is not directory");
        if (targetDir.exists() && !targetDir.canWrite()) throw new IllegalArgumentException("Target directory '" + targetDir.getAbsolutePath() + "' must be writable");

        ZipEntry entry;
        File newFile;
        while ((entry = zis.getNextEntry()) != null) {
            String fileName = entry.getName();
            if (fileName.equals(CURRENT_PATH) || fileName.equals(REVERSE_PATH)) continue;
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
                        extractArchive(new ZipInputStream(new ByteArrayInputStream(baos.toByteArray())), newFile);
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

    static void safeClose(final Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (final Throwable ignored) {}
    }

    private static boolean isNestedArchive(final String childName) {
        final String lcName = childName.toLowerCase(Locale.ENGLISH);
        return lcName.endsWith(".rar") || lcName.endsWith(".sar") || lcName.endsWith(".war");
    }

    private static void copy(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
    }

}
