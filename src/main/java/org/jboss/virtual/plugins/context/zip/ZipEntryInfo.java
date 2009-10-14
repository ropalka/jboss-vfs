/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.virtual.plugins.context.zip;

import java.util.zip.ZipEntry;

/**
 * ZipEntryInfo.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
interface ZipEntryInfo
{
   /**
    * Get name.
    *
    * @return the name
    */
   String getName();

   /**
    * Is underlying entry directory.
    *
    * @return true if underlying entry is directory, false otherwise
    */
   boolean isDirectory();

   /**
    * Do we need to update the entry.
    *
    * @return true if update is required, false otherwise
    */
   boolean requiresUpdate();

   /**
    * Read the certificates.
    */
   void readCertificates();

   /**
    * Get entry.
    *
    * @return the entry
    */
   ZipEntry getEntry();

   /**
    * Set entry.
    *
    * @param entry the entry
    */
   void setEntry(ZipEntry entry);
}
