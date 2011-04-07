/*-
 * Copyright (c) 2011, Oleg Estekhin
 * All rights reserved.
 */

package oe.maven.wagon.providers.svn;

import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

class MavenAuthenticationProvider implements ISVNAuthenticationProvider {

    private final AuthenticationInfo authenticationInfo;


    MavenAuthenticationProvider( AuthenticationInfo authenticationInfo ) {
        if ( authenticationInfo == null ) {
            throw new IllegalArgumentException( "authenticationInfo is null" );
        }
        this.authenticationInfo = authenticationInfo;
    }


    public SVNAuthentication requestClientAuthentication( String kind, SVNURL url, String realm, SVNErrorMessage errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored ) {
        if ( ISVNAuthenticationManager.PASSWORD.equals( kind ) ) {
            return new SVNPasswordAuthentication( authenticationInfo.getUserName(), authenticationInfo.getPassword(), false );
        } else {
            return null;
        }
    }

    public int acceptServerAuthentication( SVNURL url, String realm, Object certificate, boolean resultMayBeStored ) {
        return ACCEPTED_TEMPORARY;
    }

}
