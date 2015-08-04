package org.wildfly.protocol.deployment;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

abstract class AbstractLocalJarURLStreamHandler extends URLStreamHandler {

    private static final Set<String> locals;

    static {
        final Set<String> set = new HashSet<String>();
        set.add(null);
        set.add("");
        set.add("~");
        set.add("localhost");
        locals = Collections.unmodifiableSet(set);
    }

    private static String toLower(final String str) {
        return str == null ? null : str.toLowerCase();
    }

    @Override
    protected URLConnection openConnection(final URL u, final Proxy p) throws IOException {
        return openConnection(u);
    }

    @Override
    protected boolean hostsEqual(final URL url1, final URL url2) {
        return locals.contains(toLower(url1.getHost())) && locals.contains(toLower(url2.getHost())) || super.hostsEqual(url1, url2);
    }

    protected void ensureLocal(final URL url) throws IOException {
        if (!locals.contains(toLower(url.getHost()))) {
            throw new IOException("Remote host access not supported for URLs of type '" + url.getProtocol() + url.getHost() + "'");
        }
    }

}
