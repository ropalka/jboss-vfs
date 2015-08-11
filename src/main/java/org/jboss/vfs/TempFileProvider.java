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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * A provider for temporary physical files and directories.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
final class TempFileProvider {

    private static final String JBOSS_TMP_DIR_PROPERTY = "jboss.server.temp.dir";
    private static final String JVM_TMP_DIR_PROPERTY = "java.io.tmpdir";
    private static final File TMP_ROOT;
    private static final TempFileProvider INSTANCE = new TempFileProvider();

    static {
        String configTmpDir = System.getProperty(JBOSS_TMP_DIR_PROPERTY);
        if (configTmpDir == null) { configTmpDir = System.getProperty(JVM_TMP_DIR_PROPERTY); }
        try {
            TMP_ROOT = new File(configTmpDir, "wf_tmp");
            TMP_ROOT.mkdirs();
        } catch (Exception e) {
            throw VFSMessages.MESSAGES.cantSetupTempFileProvider(e);
        }
    }

    private TempFileProvider() {
        // forbidden instantiation
    }

    static TempFileProvider getInstance() {
        return INSTANCE;
    }

    TempDir createTempDir(String originalName) throws IOException {
        final String name = createTempName(originalName);
        final File f = new File(TMP_ROOT, name);
        if (f.exists() || f.mkdirs()) {
            return new TempDir(f);
        }
        throw VFSMessages.MESSAGES.couldNotCreateDirectory(originalName);
    }

    static String createTempName(final String prefix) {
        return prefix + LocalDateTime.now();
    }
}
