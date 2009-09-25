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

import org.apache.maven.wagon.WagonTestCase;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public abstract class AbstractSVNWagonTest extends WagonTestCase {

    @Override
    protected String getProtocol() {
        return "svn";
    }

    @Override
    protected long getExpectedLastModifiedOnGet(Repository wagonRepository, Resource wagonResource) {
        String wagonRepositoryUrl = wagonRepository.getUrl();
        if (!wagonRepositoryUrl.startsWith("svn:")) {
            throw new AssertionError("unexpected wagon protocol: " + wagonRepositoryUrl);
        }
        try {
            SVNURL wagonRepositoryRoot = SVNURL.parseURIDecoded(wagonRepositoryUrl.substring("svn:".length()));
            SVNRepository svnRepository = SVNRepositoryFactory.create(wagonRepositoryRoot);
            svnRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
            SVNDirEntry entry = svnRepository.info(wagonResource.getName(), -1);
            svnRepository.closeSession();
            return entry.getDate().getTime();
        } catch (SVNAuthenticationException e) {
            AssertionError ae = new AssertionError("svn authentication failed");
            ae.initCause(e);
            throw ae;
        } catch (SVNException e) {
            AssertionError ae = new AssertionError("svn connection failed");
            ae.initCause(e);
            throw ae;
        }
    }

}
