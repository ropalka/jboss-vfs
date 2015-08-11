/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
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

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.test.BaseTestCase;
import org.junit.internal.ArrayComparisonFailure;

/**
 * AbstractVFSTest.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
public abstract class AbstractVFSTest extends BaseTestCase {
    protected TempFileProvider provider;

    static void safeClose(final Closeable... closeables) {
        safeClose(Arrays.asList(closeables));
    }

    static void safeClose(final Iterable<? extends Closeable> ci) {
        if (ci != null) {
            for (Closeable closeable : ci) {
                safeClose(closeable);
            }
        }
    }

    static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                VFSLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    public AbstractVFSTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();

        provider = TempFileProvider.create("test");
    }

    protected void tearDown() throws Exception {
        provider.close();
    }

    public URL getResource(String name) {
        URL url = super.getResource(name);
        assertNotNull("Resource not found: " + name, url);
        return url;
    }

    public VirtualFile getVirtualFile(String name) {
        VirtualFile virtualFile = VFS.getChild(getResource(name).getPath());
        assertTrue("VirtualFile does not exist: " + name, virtualFile.exists());
        return virtualFile;
    }

    public List<Closeable> recursiveMount(VirtualFile file) throws IOException {
        ArrayList<Closeable> mounts = new ArrayList<Closeable>();

        if (!file.isDirectory() && file.getName().matches("^.*\\.([EeWwJj][Aa][Rr]|[Zz][Ii][Pp])$")) { mounts.add(VFS.mountZip(file, file, provider)); }

        if (file.isDirectory()) { for (VirtualFile child : file.getChildren()) { mounts.addAll(recursiveMount(child)); } }

        return mounts;
    }

    protected void assertContentEqual(VirtualFile expected, VirtualFile actual) throws ArrayComparisonFailure, IOException {
        assertArrayEquals("Expected content must mach actual content", getContent(expected), getContent(actual));
    }

    protected byte[] getContent(VirtualFile virtualFile) throws IOException {
        InputStream is = virtualFile.openStream();
        return getContent(is);
    }

    protected byte[] getContent(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        VFSUtils.copyStreamAndClose(is, bos);
        return bos.toByteArray();
    }
}
