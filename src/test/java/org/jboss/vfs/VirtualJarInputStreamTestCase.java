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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.junit.Before;
import org.junit.Test;

/**
 * Test to ensure the functionality of {@link org.jboss.vfs.VirtualJarInputStream}
 *
 * @author <a href="baileyje@gmail.com">John Bailey</a>
 */
public class VirtualJarInputStreamTestCase extends AbstractVFSTest {

    private TempFileProvider provider;

    private VirtualFile testdir;

    public VirtualJarInputStreamTestCase(String name) {
        super(name);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        provider = TempFileProvider.create("test", new ScheduledThreadPoolExecutor(2));
        testdir = getVirtualFile("/vfs/test");
    }

    @Test
    public void testIteration() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            JarEntry next = null;

            List<String> entryNames = new LinkedList<String>();
            while ((next = jarInput.getNextJarEntry()) != null) {
                entryNames.add(next.getName());
            }
            JarFile jarFile = new JarFile(new File(getResource("/vfs/test/jar1.jar").toURI()));
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                assertTrue("JarEntry for " + entryName + " should be found in VirtualJarInputStream", entryNames.contains(entryName));
            }
        } finally {
            mount.close();
        }
    }

    @Test
    public void testIterationNonJar() throws Exception {
        VirtualFile jar = testdir.getChild("jar1");
        Closeable mount = VFS.mountReal(jar.getPhysicalFile(), jar);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            JarEntry next = null;

            List<String> entryNames = new LinkedList<String>();
            while ((next = jarInput.getNextJarEntry()) != null) {
                entryNames.add(next.getName());
            }
            assertTrue(entryNames.contains("META-INF/MANIFEST.MF"));
            assertTrue(entryNames.contains("org/jboss/test/vfs/support/jar1/ClassInJar1$InnerClass.class"));
            assertTrue(entryNames.contains("org/jboss/test/vfs/support/jar1/ClassInJar1.class"));
        } finally {
            mount.close();
        }
    }

    @Test
    public void testRead() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            JarEntry next = jarInput.getNextJarEntry();
            assertEquals("META-INF/MANIFEST.MF", next.getName());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            VFSUtils.copyStreamAndClose(jarInput, baos);
            byte[] actualBytes = baos.toByteArray();

            baos.reset();

            VFSUtils.copyStreamAndClose(jar.getChild("META-INF/MANIFEST.MF").openStream(), baos);
            byte[] expectedBytes = baos.toByteArray();

            assertTrue(Arrays.equals(expectedBytes, actualBytes));
        } finally {
            mount.close();
        }
    }

    @Test
    public void testReadNonJar() throws Exception {
        VirtualFile jar = testdir.getChild("jar1");
        Closeable mount = VFS.mountReal(jar.getPhysicalFile(), jar);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            JarEntry next = jarInput.getNextJarEntry();
            assertEquals("META-INF/MANIFEST.MF", next.getName());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            VFSUtils.copyStreamAndClose(jarInput, baos);
            byte[] actualBytes = baos.toByteArray();

            baos.reset();

            VFSUtils.copyStreamAndClose(jar.getChild("META-INF/MANIFEST.MF").openStream(), baos);
            byte[] expectedBytes = baos.toByteArray();

            assertTrue(Arrays.equals(expectedBytes, actualBytes));
        } finally {
            mount.close();
        }
    }

    @Test
    public void testReadClosed() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            JarEntry next = jarInput.getNextJarEntry();
            assertEquals("META-INF/MANIFEST.MF", next.getName());

            jarInput.close();
            try {
                jarInput.read();
                fail("Should have thrown IOException");
            } catch (IOException expected) {
            }
        } finally {
            mount.close();
        }
    }

    @Test
    public void testGetManifest() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            Manifest actual = jarInput.getManifest();
            Manifest expected = VFSUtils.getManifest(jar);
            assertEquals(expected, actual);
        } finally {
            mount.close();
        }
    }

    @Test
    public void testGetManifestNonJar() throws Exception {
        VirtualFile jar = testdir.getChild("jar1");
        Closeable mount = VFS.mountReal(jar.getPhysicalFile(), jar);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            Manifest actual = jarInput.getManifest();
            Manifest expected = VFSUtils.getManifest(jar);
            assertEquals(expected, actual);
        } finally {
            mount.close();
        }
    }

    @Test
    public void testManifestClosed() throws Exception {
        VirtualFile jar = testdir.getChild("closingManifest");
        VirtualFile manifestFile = jar.getChild(JarFile.MANIFEST_NAME);
        Path originalPath = manifestFile.getPhysicalFile().toPath();
        Path copy = originalPath.resolveSibling("manifest.bak");
        try {
            Manifest manifest = VFSUtils.getManifest(jar);
            assertNotNull(manifest);
        } finally {
            Files.copy(originalPath, copy, StandardCopyOption.REPLACE_EXISTING); //backup the original manifest
            Files.delete(originalPath); //delete original one to make sure there are no more open file handles to it
            Files.copy(copy, originalPath, StandardCopyOption.REPLACE_EXISTING); //copy the backup back, to make other tests that need it pass.
        }
    }

    @Test
    public void testGetAttributes() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            Manifest manifest = jarInput.getManifest();

            JarEntry next = null;
            while ((next = jarInput.getNextJarEntry()) != null) {
                Attributes expected = manifest.getAttributes(next.getName());
                Attributes actual = next.getAttributes();
                assertEquals(expected, actual);
            }
        } finally {
            mount.close();
        }
    }

    @Test
    public void testGetAttributesNonJar() throws Exception {
        VirtualFile jar = testdir.getChild("jar1");
        Closeable mount = VFS.mountReal(jar.getPhysicalFile(), jar);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            Manifest manifest = jarInput.getManifest();

            JarEntry next = null;
            while ((next = jarInput.getNextJarEntry()) != null) {
                Attributes expected = manifest.getAttributes(next.getName());
                Attributes actual = next.getAttributes();
                assertEquals(expected, actual);
            }
        } finally {
            mount.close();
        }
    }

    /**
     * Test to verify the VirtualJarInputStream correctly behaves when read is called
     * before a call to getNextEntry or getNextJarEntry.
     *
     * @throws Exception
     * @See https://jira.jboss.org/jira/browse/JBVFS-142
     */
    @Test
    public void testReadCallWithNoEntry() throws Exception {
        VirtualFile jar = testdir.getChild("jar1.jar");
        Closeable mount = VFS.mountZip(jar, jar, provider);
        try {
            JarInputStream jarInput = (JarInputStream) jar.openStream();
            assertEquals(-1, jarInput.read());
        } finally {
            mount.close();
        }
    }


}
