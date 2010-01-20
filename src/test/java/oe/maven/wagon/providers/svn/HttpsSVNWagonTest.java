/*-
 * Copyright (c) 2009-2010, Oleg Estekhin
 * All rights reserved.
 */

package oe.maven.wagon.providers.svn;

import java.io.IOException;

public class HttpsSVNWagonTest extends AbstractSVNWagonTest {

    @Override
    protected String getTestRepositoryUrl() throws IOException {
        return "svn:" + "https://localhost/svn/maven-svn-wagon-test-repository";
    }

}
