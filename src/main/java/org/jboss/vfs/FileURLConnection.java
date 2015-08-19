/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;

/**
 * Implementation URLConnection that will delegate to the VFS RootFileSystem.
 *
 * @author <a href=mailto:jbailey@redhat.com">John Bailey</a>
 * @version $Revision$
 */
class FileURLConnection extends AbstractURLConnection {

    private final RootFileSystem rootFileSystem = RootFileSystem.ROOT_INSTANCE;

    private final VirtualFile mountPoint = VFS.getRootVirtualFile();

    private final VirtualFile file;

    public FileURLConnection(URL url) throws IOException {
        super(url);
        file = VFS.getChild(toURI(url));
    }

    public File getContent() throws IOException {
        return rootFileSystem.getFile(mountPoint, file);
    }

    public int getContentLength() {
        final long size = rootFileSystem.getSize(mountPoint, file);
        return size > (long) Integer.MAX_VALUE ? -1 : (int) size;
    }

    public long getLastModified() {
        return -1;
    }

    public InputStream getInputStream() throws IOException {
        return rootFileSystem.openInputStream(mountPoint, file);
    }

    @Override
    public Permission getPermission() throws IOException {
        return new FilePermission(file.getPathName(), "read");
    }

    public void connect() throws IOException {
    }

    @Override
    protected String getName() {
        return file.getName();
    }
}
