/*-
 * Copyright (c) 2009, Oleg Estekhin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  * Neither the names of the copyright holders nor the names of their
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */

package oe.maven.wagon.providers.svn;

import java.io.File;
import java.io.IOException;

import org.codehaus.plexus.util.FileUtils;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

public class FileSVNWagonTest extends AbstractSVNWagonTest {

    private String tempRepositoryPath;

    private SVNURL tempRepositoryUrl;


    @Override
    protected void setUp() throws Exception {
        super.setUp();
        File file = new File( getBasedir(), "target/test/svn/maven-svn-wagon-test-repository" );
        tempRepositoryPath = file.getAbsolutePath();
        if ( !file.exists() && !file.mkdirs() ) {
            throw new AssertionError( "failed to create " + file );
        }
        FileUtils.cleanDirectory( tempRepositoryPath );
        tempRepositoryUrl = SVNRepositoryFactory.createLocalRepository( file, true, false );
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtils.deleteDirectory( tempRepositoryPath );
        super.tearDown();
    }


    @Override
    protected String getTestRepositoryUrl() throws IOException {
        return "svn:" + tempRepositoryUrl.toString();
    }

}
