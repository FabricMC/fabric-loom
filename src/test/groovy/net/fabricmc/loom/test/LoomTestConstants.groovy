/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.test

import org.gradle.util.GradleVersion

class LoomTestConstants {
    private final static String NIGHTLY_VERSION = "7.6-20220519002827+0000"
    private final static boolean NIGHTLY_EXISTS = nightlyExists(NIGHTLY_VERSION)

    public final static String DEFAULT_GRADLE = GradleVersion.current().getVersion()
    // Tests that depend specifically on the nightly will run on the current version when the nightly is not available.
    public final static String PRE_RELEASE_GRADLE = NIGHTLY_EXISTS ? "7.6-20220516224938+0000" : DEFAULT_GRADLE
    public final static String[] STANDARD_TEST_VERSIONS = NIGHTLY_EXISTS ? [DEFAULT_GRADLE, PRE_RELEASE_GRADLE] : [DEFAULT_GRADLE]

    /**
     * Nightly gradle versions get removed after a certain amount of time, lets check to see if its still online before running the tests.
     */
    private static boolean nightlyExists(String version) {
        def url = "https://services.gradle.org/distributions-snapshots/gradle-${version}-bin.zip"
        def con = new URL(url).openConnection() as HttpURLConnection
        con.setRequestMethod("HEAD") // No need to request the whole file.

        return con.getResponseCode() == HttpURLConnection.HTTP_OK
    }
}
