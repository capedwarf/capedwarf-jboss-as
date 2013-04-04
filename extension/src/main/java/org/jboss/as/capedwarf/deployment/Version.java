/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.capedwarf.deployment;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public final class Version implements Comparable<Version> {
    private int major;
    private int minor;
    private int micro;

    Version(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public static Version parse(String version) {
        try {
            String[] split = version.split("\\.");

            int major = Integer.parseInt(split[0]);
            int minor = 0;
            int micro = 0;

            if (split.length > 1)
                minor = Integer.parseInt(split[1]);
            if (split.length > 2)
                micro = Integer.parseInt(split[2]);

            return new Version(major, minor, micro);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getMicro() {
        return micro;
    }

    public int compareTo(Version other) {
        // we hope there won't be 1000 versions :-)
        int X = micro + 1000 * minor + 1000000 * major;
        int Y = other.getMicro() + 1000 * other.getMinor() + 1000000 * other.getMajor();
        return X - Y;
    }

    public int hashCode() {
        return micro + 1000 * minor + 1000000 * major;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Version) && (compareTo((Version) obj) == 0);

    }

    @Override
    public String toString() {
        return major + "." + minor + "." + micro;
    }
}
