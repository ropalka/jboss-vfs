package org.wildfly.protocol.deployment;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class DeploymentURLStreamHandler extends AbstractLocalJarURLStreamHandler {

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        ensureLocal(url);
        return new DeploymentURLConnection(url);
    }

}
