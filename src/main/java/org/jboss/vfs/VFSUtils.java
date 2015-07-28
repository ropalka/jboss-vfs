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

import static org.jboss.vfs.VFSMessages.MESSAGES;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * VFS Utilities
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="ales.justin@jboss.com">Ales Justin</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @version $Revision: 1.1 $
 */
public class VFSUtils {

    /**
     * Constant representing the URL vfs protocol
     */
    static final String VFS_PROTOCOL = "vfs";

    /**
     * Constant representing the system property for forcing case sensitive
     */
    static final String FORCE_CASE_SENSITIVE_KEY = "jboss.vfs.forceCaseSensitive";

    /**
     * The {@link URLStreamHandler} for the 'vfs' protocol
     */
    static final URLStreamHandler VFS_URL_HANDLER = new VirtualFileURLStreamHandler();

    /**
     * The {@link URLStreamHandler} for the 'file' protocol
     */
    private static final URLStreamHandler FILE_URL_HANDLER = new FileURLStreamHandler();

    /**
     * The default buffer size to use for copies
     */
    private static final int DEFAULT_BUFFER_SIZE = 65536;

    /**
     * This variable indicates if the FileSystem should force case sensitive independently if
     * the underlying file system is case sensitive or not
     */
    private static boolean forceCaseSensitive;

    static {
        forceCaseSensitive = AccessController.doPrivileged(new PrivilegedAction<Boolean> () {
            public Boolean run() {
               String forceString = System.getProperty(VFSUtils.FORCE_CASE_SENSITIVE_KEY, "false");
               return Boolean.valueOf(forceString);
            }
       });
    }

    private VFSUtils() {
    }

    /**
     * Get a manifest from a virtual file, assuming the virtual file is the root of an archive
     *
     * @param archive the root the archive
     * @return the manifest or null if not found
     * @throws IOException              if there is an error reading the manifest or the virtual file is closed
     * @throws IllegalArgumentException for a null archive
     */
    public static Manifest getManifest(VirtualFile archive) throws IOException {
        if (archive == null) {
            throw MESSAGES.nullArgument("archive");
        }
        VirtualFile manifest = archive.getChild(JarFile.MANIFEST_NAME);
        if (manifest == null || !manifest.exists()) {
            if (VFSLogger.ROOT_LOGGER.isTraceEnabled()) {
                VFSLogger.ROOT_LOGGER.tracef("Can't find manifest for %s", archive.getPathName());
            }
            return null;
        }
        return readManifest(manifest);
    }

    /**
     * Read the manifest from given manifest VirtualFile.
     *
     * @param manifest the VF to read from
     * @return JAR's manifest
     * @throws IOException if problems while opening VF stream occur
     */
    static Manifest readManifest(VirtualFile manifest) throws IOException {
        if (manifest == null) {
            throw MESSAGES.nullArgument("manifest file");
        }
        InputStream stream = new PaddedManifestStream(manifest.openStream());
        try {
            return new Manifest(stream);
        } finally {
            safeClose(stream);
        }
    }

    /**
     * Deal with urls that may include spaces.
     *
     * @param url the url
     * @return uri the uri
     * @throws URISyntaxException for any error
     */
    static URI toURI(URL url) throws URISyntaxException {
        if (url == null) {
            throw MESSAGES.nullArgument("url");
        }
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            String urispec = url.toExternalForm();
            // Escape percent sign and spaces
            urispec = urispec.replaceAll("%", "%25");
            urispec = urispec.replaceAll(" ", "%20");
            return new URI(urispec);
        }
    }

    /**
     * Copy input stream to output stream and close them both
     *
     * @param is input stream
     * @param os output stream
     * @throws IOException for any error
     */
    static void copyStreamAndClose(InputStream is, OutputStream os) throws IOException {
        copyStreamAndClose(is, os, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copy input stream to output stream and close them both
     *
     * @param is         input stream
     * @param os         output stream
     * @param bufferSize the buffer size to use
     * @throws IOException for any error
     */
    private static void copyStreamAndClose(InputStream is, OutputStream os, int bufferSize)
            throws IOException {
        try {
            copyStream(is, os, bufferSize);
            // throw an exception if the close fails since some data might be lost
            is.close();
            os.close();
        } finally {
            // ...but still guarantee that they're both closed
            safeClose(is);
            safeClose(os);
        }
    }

    /**
     * Copy input stream to output stream without closing streams. Flushes output stream when done.
     *
     * @param is input stream
     * @param os output stream
     * @throws IOException for any error
     */
    static void copyStream(InputStream is, OutputStream os) throws IOException {
        copyStream(is, os, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Copy input stream to output stream without closing streams. Flushes output stream when done.
     *
     * @param is         input stream
     * @param os         output stream
     * @param bufferSize the buffer size to use
     * @throws IOException for any error
     */
    static void copyStream(InputStream is, OutputStream os, int bufferSize)
            throws IOException {
        if (is == null) {
            throw MESSAGES.nullArgument("input stream");
        }
        if (os == null) {
            throw MESSAGES.nullArgument("output stream");
        }
        byte[] buff = new byte[bufferSize];
        int rc;
        while ((rc = is.read(buff)) != -1) { os.write(buff, 0, rc); }
        os.flush();
    }

    /**
     * Get the virtual URL for a virtual file.  This URL can be used to access the virtual file; however, taking the file
     * part of the URL and attempting to use it with the {@link java.io.File} class may fail if the file is not present
     * on the physical filesystem, and in general should not be attempted.
     * <b>Note:</b> if the given VirtualFile refers to a directory <b>at the time of this
     * method invocation</b>, a trailing slash will be appended to the URL; this means that invoking
     * this method may require a filesystem access, and in addition, may not produce consistent results
     * over time.
     *
     * @param file the virtual file
     * @return the URL
     * @throws MalformedURLException if the file cannot be coerced into a URL for some reason
     * @see VirtualFile#asDirectoryURL()
     * @see VirtualFile#asFileURL()
     */
    static URL getVirtualURL(VirtualFile file) throws MalformedURLException {
        try {
            final URI uri = getVirtualURI(file);
            final String scheme = uri.getScheme();
            return AccessController.doPrivileged(new PrivilegedExceptionAction<URL>() {
                @Override
                public URL run() throws MalformedURLException{
                    if (VFS_PROTOCOL.equals(scheme)) {
                        return new URL(null, uri.toString(), VFS_URL_HANDLER);
                    } else if ("file".equals(scheme)) {
                        return new URL(null, uri.toString(), FILE_URL_HANDLER);
                    } else {
                        return uri.toURL();
                    }
                }
            });
        } catch (URISyntaxException e) {
            throw new MalformedURLException(e.getMessage());
        } catch (PrivilegedActionException e) {
            throw (MalformedURLException) e.getException();
        }
    }

    /**
     * Get the virtual URI for a virtual file.
     * <b>Note:</b> if the given VirtualFile refers to a directory <b>at the time of this
     * method invocation</b>, a trailing slash will be appended to the URI; this means that invoking
     * this method may require a filesystem access, and in addition, may not produce consistent results
     * over time.
     *
     * @param file the virtual file
     * @return the URI
     * @throws URISyntaxException if the file cannot be coerced into a URI for some reason
     * @see VirtualFile#asDirectoryURI()
     * @see VirtualFile#asFileURI()
     */
    static URI getVirtualURI(VirtualFile file) throws URISyntaxException {
        return new URI(VFS_PROTOCOL, "", file.getPathName(true), null);
    }

    /**
     * Get the physical root URL of the filesystem of a virtual file.  This URL is suitable for use as a class loader's
     * code source or in similar situations where only standard URL types ({@code jar} and {@code file}) are supported.
     *
     * @param file the virtual file
     * @return the root URL
     * @throws MalformedURLException if the URL is not valid
     */
    public static URL getRootURL(VirtualFile file) throws MalformedURLException {
        final URI uri;
        try {
            uri = VFS.getMount(file).getFileSystem().getRootURI();
        } catch (URISyntaxException e) {
            throw new MalformedURLException(e.getMessage());
        }
        return uri.toURL();
    }

    /**
     * Safely close some resource without throwing an exception.  Any exception will be logged at TRACE level.
     *
     * @param c the resource
     */
    static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                VFSLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    /**
     * Safely close some resource without throwing an exception.  Any exception will be logged at TRACE level.
     *
     * @param zipFile the resource
     */
    static void safeClose(final ZipFile zipFile) {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (Exception e) {
                VFSLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    /**
     * In case the file system is not case sensitive we compare the canonical path with
     * the absolute path of the file after normalized.
     * @param file
     * @return
     */
    static boolean exists(File file) {
        try {
            boolean fileExists = file.exists();
            if(!forceCaseSensitive || !fileExists) {
                return fileExists;
            }

            String absPath = canonicalize(file.getAbsolutePath());
            String canPath = canonicalize(file.getCanonicalPath());
            return fileExists && absPath.equals(canPath);
        } catch(IOException io) {
            return false;
        }
    }

    /**
     * Attempt to recursively delete a real file.
     *
     * @param root the real file to delete
     * @return {@code true} if the file was deleted
     */
    static boolean recursiveDelete(File root) {
        boolean ok = true;
        if (root.isDirectory()) {
            final File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    ok &= recursiveDelete(file);
                }
            }
            return ok && (root.delete() || !root.exists());
        } else {
            ok &= root.delete() || !root.exists();
        }
        return ok;
    }

    private static final InputStream EMPTY_STREAM = new InputStream() {
        public int read() throws IOException {
            return -1;
        }
    };

    /**
     * Get the empty input stream.  This stream always reports an immediate EOF.
     *
     * @return the empty input stream
     */
    static InputStream emptyStream() {
        return EMPTY_STREAM;
    }

    /**
     * Get an input stream that will always be consumable as a Zip/Jar file.  The input stream will not be an instance
     * of a JarInputStream, but will stream bytes according to the Zip specification.  Using this method, a VFS file
     * or directory can be written to disk as a normal jar/zip file.
     *
     * @param virtualFile The virtual to get a jar file input stream for
     * @return An input stream returning bytes according to the zip spec
     * @throws IOException if any problems occur
     */
    public static InputStream createJarFileInputStream(final VirtualFile virtualFile) throws IOException {
        if (virtualFile.isDirectory()) {
            final VirtualJarInputStream jarInputStream = new VirtualJarInputStream(virtualFile);
            return new VirtualJarFileInputStream(jarInputStream);
        }
        InputStream inputStream = null;
        try {
            final byte[] expectedHeader = new byte[4];

            expectedHeader[0] = (byte) (JarEntry.LOCSIG & 0xff);
            expectedHeader[1] = (byte) ((JarEntry.LOCSIG >>> 8) & 0xff);
            expectedHeader[2] = (byte) ((JarEntry.LOCSIG >>> 16) & 0xff);
            expectedHeader[3] = (byte) ((JarEntry.LOCSIG >>> 24) & 0xff);

            inputStream = virtualFile.openStream();
            final byte[] bytes = new byte[4];
            final int read = inputStream.read(bytes, 0, 4);
            if (read < 4 || !Arrays.equals(expectedHeader, bytes)) {
                throw MESSAGES.invalidJarSignature(Arrays.toString(bytes), Arrays.toString(expectedHeader));
            }
        } finally {
            safeClose(inputStream);
        }
        return virtualFile.openStream();
    }

    /**
     * Expand a zip file to a destination directory.  The directory must exist.  If an error occurs, the destination
     * directory may contain a partially-extracted archive, so cleanup is up to the caller.
     *
     * @param zipFile the zip file
     * @param destDir the destination directory
     * @throws IOException if an error occurs
     */
    static void unzip(File zipFile, File destDir) throws IOException {
        final ZipFile zip = new ZipFile(zipFile);
        try {
            final Set<File> createdDirs = new HashSet<File>();
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            FILES_LOOP:
            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = entries.nextElement();
                final String name = zipEntry.getName();
                final List<String> tokens = PathTokenizer.getTokens(name);
                final Iterator<String> it = tokens.iterator();
                File current = destDir;
                while (it.hasNext()) {
                    String token = it.next();
                    if (PathTokenizer.isCurrentToken(token) || PathTokenizer.isReverseToken(token)) {
                        // invalid file; skip it!
                        continue FILES_LOOP;
                    }
                    current = new File(current, token);
                    if ((it.hasNext() || zipEntry.isDirectory()) && createdDirs.add(current)) {
                        current.mkdir();
                    }
                }
                if (!zipEntry.isDirectory()) {
                    final InputStream is = zip.getInputStream(zipEntry);
                    try {
                        final FileOutputStream os = new FileOutputStream(current);
                        try {
                            VFSUtils.copyStream(is, os);
                            // allow an error on close to terminate the unzip
                            is.close();
                            os.close();
                        } finally {
                            VFSUtils.safeClose(os);
                        }
                    } finally {
                        VFSUtils.safeClose(is);
                    }
                    // exclude jsp files last modified time change. jasper jsp compiler Compiler.java depends on last modified time-stamp to re-compile jsp files
                    if (!current.getName().endsWith(".jsp"))
                        current.setLastModified(zipEntry.getTime());
                }
            }
        } finally {
            VFSUtils.safeClose(zip);
        }
    }

    /**
     * Return the mount source File for a given mount handle.
     *
     * @param handle The handle to get the source for
     * @return The mount source file or null if the handle does not have a source, or is not a MountHandle
     */
    public static File getMountSource(Closeable handle) {
        if (handle instanceof MountHandle) { return MountHandle.class.cast(handle).getMountSource(); }
        return null;
    }

    /**
     * Canonicalize the given path.  Removes all {@code .} and {@code ..} segments from the path.
     *
     * @param path the relative or absolute possibly non-canonical path
     * @return the canonical path
     */
    @SuppressWarnings("UnusedLabel") // for documentation
    static String canonicalize(final String path) {
        final int length = path.length();
        // 0 - start
        // 1 - got one .
        // 2 - got two .
        // 3 - got /
        int state = 0;
        if (length == 0) {
            return path;
        }
        final char[] targetBuf = new char[length];
        // string segment end exclusive
        int e = length;
        // string cursor position
        int i = length;
        // buffer cursor position
        int a = length - 1;
        // number of segments to skip
        int skip = 0;
        loop:
        while (--i >= 0) {
            char c = path.charAt(i);
            outer:
            switch (c) {
                case '/': {
                    inner:
                    switch (state) {
                        case 0:
                            state = 3;
                            e = i;
                            break outer;
                        case 1:
                            state = 3;
                            e = i;
                            break outer;
                        case 2:
                            state = 3;
                            e = i;
                            skip++;
                            break outer;
                        case 3:
                            e = i;
                            break outer;
                        default:
                            throw new IllegalStateException();
                    }
                    // not reached!
                }
                case '.': {
                    inner:
                    switch (state) {
                        case 0:
                            state = 1;
                            break outer;
                        case 1:
                            state = 2;
                            break outer;
                        case 2:
                            break inner; // emit!
                        case 3:
                            state = 1;
                            break outer;
                        default:
                            throw new IllegalStateException();
                    }
                    // fall thru
                }
                default: {
                    final int newE = e > 0 ? path.lastIndexOf('/', e - 1) : -1;
                    final int segmentLength = e - newE - 1;
                    if (skip > 0) {
                        skip--;
                    } else {
                        if (state == 3) {
                            targetBuf[a--] = '/';
                        }
                        path.getChars(newE + 1, e, targetBuf, (a -= segmentLength) + 1);
                    }
                    state = 0;
                    i = newE + 1;
                    e = newE;
                    break;
                }
            }
        }
        if (state == 3) {
            targetBuf[a--] = '/';
        }
        return new String(targetBuf, a + 1, length - a - 1);
    }
}
