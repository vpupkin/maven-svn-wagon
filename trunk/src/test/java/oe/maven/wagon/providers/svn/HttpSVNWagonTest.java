/*-
 * Copyright (c) 2009-2011, Oleg Estekhin
 * All rights reserved.
 */

package oe.maven.wagon.providers.svn;

import java.io.IOException;

public class HttpSVNWagonTest extends AbstractSVNWagonTest {

    @Override
    protected String getTestRepositoryUrl() throws IOException {
        return "svn:" + "http://localhost/svn/maven-svn-wagon-test-repository";
    }

}
