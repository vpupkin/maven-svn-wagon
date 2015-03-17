# Hosting Maven artifact repository inside Google Code SVN #

The Google Code SVN can be used to host the Maven artifacts repository and the Maven SVN Wagon can be used to automatically deploy artifacts created by the Maven into such a repository.

Configure the maven-svn-wagon as a dependency of the deploy plugin:
```
<build>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-deploy-plugin</artifactId>
        <version><!-- please use the latest version of the deploy plugin --></version>
        <dependencies>
            <dependency>
                <groupId>com.google.code.maven-svn-wagon</groupId>
                <artifactId>maven-svn-wagon</artifactId>
                <version><!-- please use the latest version of the wagon --></version>
            </dependency>
        </dependencies>
    </plugin>
</build>
```
Additional information on how to configure the wagon can be found in the [documentation](http://maven-svn-wagon.googlecode.com/svn/site/index.html).

Configure the `distributionManagement` section in your pom file:

```
<distributionManagement>
    <repository>
        <id>yourproject.googlecode.com</id>
        <url>svn:https://yourproject.googlecode.com/svn/m2/releases</url>
    </repository>
    <snapshotRepository>
        <id>yourproject.googlecode.com</id>
        <url>svn:https://yourproject.googlecode.com/svn/m2/snapshots</url>
    </snapshotRepository>
</distributionManagement>
```

Just replace the `yourproject` with your project name, and, if you want, configure the path in the release and snapshot urls.

You may also need to configure user credentials (user name and password) inside the Maven's setiings.xml file for a server corresponding to the `id` specified in the `distributionManagement` section.

Now the [deploy](http://maven.apache.org/plugins/maven-deploy-plugin/) and [release](http://maven.apache.org/plugins/maven-release-plugin/) plugins will automatically deploy your artifacts inside your Google Code subversion repository.

People who use your artifacts will need to configure an additional repository:

```
<repositories>
   <repository>
      <id>yourproject.googlecode.com</id>
      <url>http://yourproject.googlecode.com/svn/m2/releases</url>
   </repository>
</repositories>
```

Note that they do not need the Maven SVN Wagon to access this repository.

Snapshot or plugin repositories are configred in the same way, please refer to the Maven [POM reference](http://maven.apache.org/pom.html#Repositories).