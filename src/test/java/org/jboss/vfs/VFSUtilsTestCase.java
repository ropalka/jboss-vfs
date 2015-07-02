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

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.Test;

/**
 * Test to ensure the functionality of {@link VFSUtils} methods
 *
 * @author <a href="baileyje@gmail.com">John Bailey</a>
 */
public class VFSUtilsTestCase extends AbstractVFSTest {

    public VFSUtilsTestCase(String name) {
        super(name);
    }

    @Test
    public void testReadManifest() throws Exception {
        VirtualFile correctManifest = getVirtualFile("/vfs/test/manifest/correct.mf");
        Manifest manifest = VFSUtils.readManifest(correctManifest);
        assertManifest(manifest);
        VirtualFile incorrectManifest = getVirtualFile("/vfs/test/manifest/incorrect.mf");
        manifest = VFSUtils.readManifest(incorrectManifest);
        assertManifest(manifest);
    }

    private void assertManifest(Manifest manifest) {
        Attributes attributes = manifest.getMainAttributes();
        assertEquals(9, attributes.size());
        assertEquals("Manifest-Version", "1.0", attributes.getValue("Manifest-Version"));
        assertEquals("Specification-Title", "filesonly-war", attributes.getValue("Specification-Title"));
        assertEquals("Specification-Version", "1.0.0.GA", attributes.getValue("Specification-Version"));
        assertEquals("Specification-Vendor", "JBoss Inc.", attributes.getValue("Specification-Vendor"));
        assertEquals("Implementation-Title", "filesonly-war", attributes.getValue("Implementation-Title"));
        assertEquals("Implementation-URL", "http://www.jboss.org", attributes.getValue("Implementation-URL"));
        assertEquals("Implementation-Version", "1.0.0.GA-jboss", attributes.getValue("Implementation-Version"));
        assertEquals("Implementation-Vendor", "JBoss Inc.", attributes.getValue("Implementation-Vendor"));
        assertEquals("Created-By", "${java.runtime.version} (${java.vendor})", attributes.getValue("Created-By"));
    }
}
