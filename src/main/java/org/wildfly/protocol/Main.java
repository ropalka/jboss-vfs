package org.wildfly.protocol;

import org.wildfly.protocol.deployment.DeploymentURLConnection;
import org.wildfly.protocol.deployment.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by ropalka on 8/4/15.
 */
public final class Main {

    private static final String JAVA_PROTOCOL_HANDLER_PKGS = "java.protocol.handler.pkgs";
    private static final String WILDFLY_DEPLOYMENT_PROTOCOL = "org.wildfly.protocol";

    static {
        init();
    }

    /**
     * Initialize DEPLOYMENT protocol handlers package property.
     */
    private static void init() {
        final String pkgs = System.getProperty(JAVA_PROTOCOL_HANDLER_PKGS, "");
        if ("".equals(pkgs)) {
            System.setProperty(JAVA_PROTOCOL_HANDLER_PKGS, WILDFLY_DEPLOYMENT_PROTOCOL);
        } else if (!pkgs.contains(WILDFLY_DEPLOYMENT_PROTOCOL)) {
            System.setProperty(JAVA_PROTOCOL_HANDLER_PKGS, pkgs + "|" + WILDFLY_DEPLOYMENT_PROTOCOL);
        }
    }

    private static final String origEarPath = "/home/ropalka/TODO/AAA-WFCORE-680/foo.ear";
    private static final String targetEarPath = "/tmp/foo.ear-orig";
    private static final String targetEarCopy = "/tmp/foo.ear-copy";

    public static void main(final String... args) throws Exception {
        testRoot();
        IOUtils.extract(new ZipInputStream(new FileInputStream(origEarPath)), new File(targetEarPath));
        IOUtils.extract(new File(targetEarPath), new File(targetEarCopy));
        File manifest = new File(new File(targetEarCopy), "META-INF/MANIFEST.MF");
        System.out.println(manifest.getAbsolutePath() + " exists");
    }

    private static final String earPath = "deployment:/foo.ear!/lib/bar.war";

    public static void testRoot() throws Exception {
        final URL url = new URL(earPath);
        url.getFile();
        DeploymentURLConnection urlConnection = (DeploymentURLConnection)url.openConnection();
        System.out.println("file : " + urlConnection.getJarFileURL());
        System.out.println("entry: " + urlConnection.getEntryName());
        System.out.println("name:  " + urlConnection.getJarFile().getName());
        System.out.println("xxx  : " + urlConnection.getJarEntry().getName() + " is directory: " + urlConnection.getJarEntry().isDirectory());
        System.out.println(urlConnection.getJarFile().size()); // count of entries in the jar file
        System.out.println(urlConnection.getJarEntry());
        final Enumeration<JarEntry> entries = urlConnection.getJarFile().entries();
        while (entries.hasMoreElements()) {
            JarEntry e = entries.nextElement();
            System.out.println(e.getName() + " is directory: " + e.isDirectory());
        }
    }

    public static void unzip() throws Exception {

        byte[] buffer = new byte[1024];

        try {

            //create output directory is not exists
            File folder = new File("/tmp");
            String outputFolder = "foo.ear";

            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream("/home/ropalka/TODO/AAA-WFCORE-680/foo.ear"));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {

                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                System.out.println("file unzip : " + newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            System.out.println("Done");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
