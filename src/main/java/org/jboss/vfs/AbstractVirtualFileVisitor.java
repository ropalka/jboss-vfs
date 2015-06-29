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

/**
 * AbstractVirtualFileVisitor.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 1.1 $
 */
abstract class AbstractVirtualFileVisitor implements VirtualFileVisitor {

    /**
     * The attributes
     */
    private final VisitorAttributes attributes;

    /**
     * Create a new AbstractVirtualFileVisitor using the default visitor attributes
     */
    protected AbstractVirtualFileVisitor() {
        this(null);
    }

    /**
     * Create a new AbstractVirtualFileVisitor using the default visitor attributes
     *
     * @param attributes the attributes, uses the default if null
     */
    protected AbstractVirtualFileVisitor(VisitorAttributes attributes) {
        if (attributes == null) { attributes = VisitorAttributes.DEFAULT; }
        this.attributes = attributes;
    }

    public VisitorAttributes getAttributes() {
        return attributes;
    }
}