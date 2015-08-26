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

/**
 * Tests functionality of the MountHandle retrieving mount source.
 *
 * @author <a href=mailto:jbailey@redhat.com">John Bailey</a>
 */
public class MountHandleTestCase extends AbstractVFSTest {

    public MountHandleTestCase(final String name) {
        super(name);
    }

    public void testZipGetMountSource() throws Exception {
        VirtualFile jar = getVirtualFile("/vfs/test/jar1.jar");
        File origin = jar.getPhysicalFile();
        Closeable mountHandle = VFS.mountZip(jar, jar);
        try {
            File mounted = jar.getPhysicalFile();
            File source = getMountSource(mountHandle);

            assertNotNull(origin);
            assertNotNull(mounted);
            assertNotNull(source);
            assertFalse(origin.equals(mounted));
            assertFalse(origin.equals(source));
            assertFalse(mounted.equals(source));

            assertTrue(origin.isFile());
            assertTrue(source.isFile());
            assertTrue(mounted.isDirectory());

            assertEquals(origin.length(), source.length());
        } finally {
            VFSUtils.safeClose(mountHandle);
        }
    }

    private static File getMountSource(Closeable handle) {
        return (handle instanceof MountHandle) ? MountHandle.class.cast(handle).getMountSource() : null;
    }

}
