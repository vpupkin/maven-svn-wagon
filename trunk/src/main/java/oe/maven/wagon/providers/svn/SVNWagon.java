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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * This wagon implementation uses SVNKit library to access resources hosted inside Subversion repository.
 *
 * @see <a href="http://svnkit.com/">http://svnkit.com/</a>
 */
public class SVNWagon extends AbstractWagon {

    static {
        DAVRepositoryFactory.setup();   // http, https
        SVNRepositoryFactoryImpl.setup();   // svn, svn+xxx
        FSRepositoryFactory.setup();    // file
    }


    /** The Subversion repository root. */
    private SVNURL svnRepositoryRoot;

    /** The path of the requested wagon repository relative to the Subversion repository root. */
    private String wagonRepositoryPath;


    /** The repository for read operations. */
    private SVNRepository readRepository;


    /** The repository for write operations. */
    private SVNRepository writeRepository;

    /** The editor for write operations. */
    private ISVNEditor writeEditor;

    /** The Subversion options used for write operations. */
    private ISVNOptions writeOptions;

    /** Indicates whether some write operation was actually attempted. */
    private boolean writeAttempted;

    /** Indicates whether all attempted write operation were successful. */
    private boolean writeSuccessful;

    /** The set of repository paths that were added during the current write session. */
    private Set<String> addedEntries;


    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
        String wagonRepositoryUrl = repository.getUrl();
        if (!wagonRepositoryUrl.startsWith("svn:")) {
            throw new AssertionError("unexpected wagon protocol: " + wagonRepositoryUrl);
        }
        try {
            SVNURL wagonRepositoryRoot = SVNURL.parseURIDecoded(wagonRepositoryUrl.substring("svn:".length()));
            SVNRepository svnRepository = SVNRepositoryFactory.create(wagonRepositoryRoot);
            svnRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
            svnRepositoryRoot = svnRepository.getRepositoryRoot(true);
            svnRepository.closeSession();
            wagonRepositoryPath = wagonRepositoryRoot.getPath().substring(svnRepositoryRoot.getPath().length());
            if (wagonRepositoryPath.startsWith("/")) {
                wagonRepositoryPath = wagonRepositoryPath.substring(1);
            }
            if (wagonRepositoryPath.endsWith("/")) {
                wagonRepositoryPath = wagonRepositoryPath.substring(0, wagonRepositoryPath.length() - 1);
            }
        } catch (SVNAuthenticationException e) {
            throw new AuthenticationException(e.getMessage(), e);
        } catch (SVNException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    protected void closeConnection() throws ConnectionException {
        svnRepositoryRoot = null;
        wagonRepositoryPath = null;
        try {
            commitWriteSession();
        } catch (SVNException e) {
            throw new ConnectionException(e.getMessage(), e);
        } finally {
            if (writeRepository != null) {
                writeRepository.closeSession();
                writeRepository = null;
            }
            if (readRepository != null) {
                readRepository.closeSession();
                readRepository = null;
            }
        }
    }


    @Override
    public boolean resourceExists(String repositoryResourceName) throws TransferFailedException, AuthorizationException {
        String repositoryResourcePath = getResourcePath(repositoryResourceName);
        try {
            SVNNodeKind repositoryResourceKind = getReadRepository().checkPath(repositoryResourcePath, -1);
            return !SVNNodeKind.NONE.equals(repositoryResourceKind);
        } catch (SVNAuthenticationException e) {
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            throw new TransferFailedException(e.getMessage(), e);
        }
    }


    public void get(String repositoryResourceName, File localFile) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String repositoryResourcePath = getResourcePath(repositoryResourceName);
        try {
            if (addedEntries != null && addedEntries.contains(repositoryResourcePath)) {
                commitWriteSession();
            }
            SVNNodeKind repositoryResourceKind = getReadRepository().checkPath(repositoryResourcePath, -1);
            if (SVNNodeKind.FILE.equals(repositoryResourceKind)) {
                getInternal(repositoryResourcePath, localFile, new Resource(repositoryResourceName));
            } else if (SVNNodeKind.NONE.equals(repositoryResourceKind)) {
                throw new ResourceDoesNotExistException(repositoryResourceName + " does not exist");
            } else {
                throw new ResourceDoesNotExistException(repositoryResourceName + " is not a file");
            }
        } catch (SVNAuthenticationException e) {
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            throw new TransferFailedException(e.getMessage(), e);
        }
    }

    public boolean getIfNewer(String repositoryResourceName, File localFile, long timestamp) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String repositoryResourcePath = getResourcePath(repositoryResourceName);
        try {
            if (addedEntries != null && addedEntries.contains(repositoryResourcePath)) {
                commitWriteSession();
            }
            SVNDirEntry repositoryResourceEntry = getReadRepository().info(repositoryResourcePath, -1);
            if (repositoryResourceEntry == null) {
                throw new ResourceDoesNotExistException(repositoryResourceName + " does not exist");
            } else if (SVNNodeKind.FILE.equals(repositoryResourceEntry.getKind())) {
                if (repositoryResourceEntry.getDate().getTime() <= timestamp) {
                    fireGetInitiated(new Resource(repositoryResourceName), localFile); // expected by the org.apache.maven.wagon.WagonTestCase
                    return false;
                } else {
                    getInternal(repositoryResourcePath, localFile, new Resource(repositoryResourceName));
                    return true;
                }
            } else {
                throw new ResourceDoesNotExistException(repositoryResourceName + " is not a file");
            }
        } catch (SVNAuthenticationException e) {
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            throw new TransferFailedException(e.getMessage(), e);
        }
    }


    public void put(File localFile, String repositoryResourceName) throws TransferFailedException, AuthorizationException {
        if (repositoryResourceName.endsWith(".asc.md5") || repositoryResourceName.endsWith(".asc.sha1")) {
            // HACK: unnecessary artifacts of maven-gpg-plugin and maven-deploy-plugin combination
            return;
        }
        String repositoryResourcePath = getResourcePath(repositoryResourceName);
        try {
            ISVNEditor editor = getWriteEditor(repositoryResourcePath);
            String[] pathComponents = repositoryResourcePath.split("/");
            openDirectoriesInternal(editor, pathComponents, 0, pathComponents.length - 1);
            putFileInternal(localFile, repositoryResourcePath, new Resource(repositoryResourceName));
            closeDirectoriesInternal(editor, pathComponents.length - 1);
        } catch (SVNAuthenticationException e) {
            writeSuccessful = false;
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            writeSuccessful = false;
            throw new TransferFailedException(e.getMessage(), e);
        } catch (TransferFailedException e) {
            writeSuccessful = false;
            throw e;
        }
    }


    @Override
    public boolean supportsDirectoryCopy() {
        return true;
    }

    @Override
    public void putDirectory(File localDirectory, String repositoryDirectoryName) throws TransferFailedException, AuthorizationException {
        String repositoryDirectoryPath = getResourcePath(repositoryDirectoryName);
        try {
            ISVNEditor editor = getWriteEditor(repositoryDirectoryPath);
            String[] pathComponents = repositoryDirectoryPath.split("/");
            openDirectoriesInternal(editor, pathComponents, 0, pathComponents.length - 1);
            putDirectoryInternal(localDirectory, repositoryDirectoryPath, new Resource(repositoryDirectoryName));
            closeDirectoriesInternal(editor, pathComponents.length - 1);
        } catch (SVNAuthenticationException e) {
            writeSuccessful = false;
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            writeSuccessful = false;
            throw new TransferFailedException(e.getMessage(), e);
        } catch (TransferFailedException e) {
            writeSuccessful = false;
            throw e;
        }
    }


    @Override
    public List<String> getFileList(String repositoryDirectoryName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String repositoryDirectoryPath = getResourcePath(repositoryDirectoryName);
        try {
            SVNNodeKind repositoryDirectoryKind = getReadRepository().checkPath(repositoryDirectoryPath, -1);
            if (SVNNodeKind.DIR.equals(repositoryDirectoryKind)) {
                @SuppressWarnings("unchecked")
                Collection<SVNDirEntry> repositoryDirectoryEntries = getReadRepository().getDir(repositoryDirectoryPath, -1, null, (Collection<SVNDirEntry>) null);
                List<String> list = new ArrayList<String>();
                for (SVNDirEntry entry : repositoryDirectoryEntries) {
                    if (SVNNodeKind.DIR.equals(entry.getKind())) {
                        list.add(entry.getRelativePath() + '/');
                    } else {
                        list.add(entry.getRelativePath());
                    }
                }
                return list;
            } else if (SVNNodeKind.NONE.equals(repositoryDirectoryKind)) {
                throw new ResourceDoesNotExistException(repositoryDirectoryName + " does not exist");
            } else {
                throw new ResourceDoesNotExistException(repositoryDirectoryName + " is not a directory");
            }
        } catch (SVNAuthenticationException e) {
            throw new AuthorizationException(e.getMessage(), e);
        } catch (SVNException e) {
            throw new TransferFailedException(e.getMessage(), e);
        }
    }


    /**
     * Returns the repository for read operations.
     * <p/>
     * This method lazily creates the read repository if needed.
     *
     * @return the repository for read operations
     *
     * @throws SVNException if an SVN error occurred
     */
    private SVNRepository getReadRepository() throws SVNException {
        if (readRepository == null) {
            readRepository = SVNRepositoryFactory.create(svnRepositoryRoot);
            readRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        }
        return readRepository;
    }

    /**
     * Returns the repository for write operations.
     * <p/>
     * This method lazily creates the write repository if needed.
     *
     * @return the repository for write operations
     *
     * @throws SVNException if an SVN error occurred
     */
    private SVNRepository getWriteRepository() throws SVNException {
        if (writeRepository == null) {
            writeRepository = SVNRepositoryFactory.create(svnRepositoryRoot);
            writeRepository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        }
        return writeRepository;
    }

    /**
     * Returns the editor for write operations.
     * <p/>
     * This method lazily creates the editor and supporting objects if needed.
     *
     * @param message the commit log message
     *
     * @return the editor for write operations
     *
     * @throws SVNException if an SVN error occurred
     */
    private ISVNEditor getWriteEditor(String message) throws SVNException {
        if (writeEditor == null) {
            writeAttempted = false;
            writeSuccessful = true;
            writeEditor = getWriteRepository().getCommitEditor("[maven-svn-wagon] " + message, null, false, null, null);
            writeEditor.openRoot(-1);
            writeOptions = SVNWCUtil.createDefaultOptions(true);
            addedEntries = new HashSet<String>();
        }
        return writeEditor;
    }

    /**
     * Commits the current write session.
     *
     * @throws SVNException if an SVN error occurred
     */
    private void commitWriteSession() throws SVNException {
        if (writeEditor != null) {
            try {
                if (writeAttempted && writeSuccessful) {
                    writeEditor.closeDir();
                    writeEditor.closeEdit();
                } else {
                    writeEditor.abortEdit();
                }
            } finally {
                writeEditor = null;
                writeOptions = null;
                addedEntries = null;
            }
        }
    }


    /**
     * Converts the wagon resource name to the Subversion repository path.
     *
     * @param repositoryResourceName the resource name relative to the wagon repository root
     *
     * @return the resource name relative to the Subversion repository root
     */
    private String getResourcePath(String repositoryResourceName) {
        if (".".equals(repositoryResourceName)) {
            return wagonRepositoryPath;
        }
        if (repositoryResourceName.startsWith(".")) {
            repositoryResourceName = repositoryResourceName.substring(1);
        }
        if (repositoryResourceName.startsWith("/") || repositoryResourceName.startsWith("\\")) {
            repositoryResourceName = repositoryResourceName.substring(1);
        }
        return wagonRepositoryPath.isEmpty() ? repositoryResourceName : wagonRepositoryPath + '/' + repositoryResourceName;
    }

    /**
     * Returns the auto-properties applicable for the file with specified name.
     *
     * @param repositoryResourcePath the resource name relative to the Subversion repository root
     *
     * @return the auto-properties for a file with specified name
     */
    private Map<String, String> getAutoProperties(String repositoryResourcePath) {
        @SuppressWarnings("unchecked")
        Map<String, String> autoProperties = writeOptions.applyAutoProperties(new File(repositoryResourcePath), null);
        if (!autoProperties.containsKey(SVNProperty.MIME_TYPE)) {
            int lastDot = repositoryResourcePath.lastIndexOf('.');
            if (lastDot >= 0) {
                String extension = repositoryResourcePath.substring(lastDot + 1);
                String mimeType = (String) writeOptions.getFileExtensionsToMimeTypes().get(extension);
                if (mimeType != null) {
                    autoProperties.put(SVNProperty.MIME_TYPE, mimeType);
                }
            }
        }
        return autoProperties;
    }


    private void openDirectoriesInternal(ISVNEditor editor, String[] pathComponents, int offset, int count) throws TransferFailedException, SVNException {
        String repositoryDirectoryPath = null;
        for (int i = 0; i < count; i++) {
            String pathComponent = pathComponents[offset + i];
            repositoryDirectoryPath = repositoryDirectoryPath == null ? pathComponent : repositoryDirectoryPath + '/' + pathComponent;
            openDirectoryInternal(editor, repositoryDirectoryPath);
        }
    }

    private void openDirectoryInternal(ISVNEditor editor, String repositoryDirectoryPath) throws TransferFailedException, SVNException {
        SVNNodeKind repositoryDirectoryKind = getReadRepository().checkPath(repositoryDirectoryPath, -1);
        boolean repositoryDirectoryExists;
        if (SVNNodeKind.DIR.equals(repositoryDirectoryKind)) {
            repositoryDirectoryExists = true;
        } else if (SVNNodeKind.NONE.equals(repositoryDirectoryKind)) {
            repositoryDirectoryExists = false;
        } else {
            throw new TransferFailedException(repositoryDirectoryPath + " is not a directory");
        }
        if (repositoryDirectoryExists || addedEntries.contains(repositoryDirectoryPath)) {
            editor.openDir(repositoryDirectoryPath, -1);
        } else {
            writeAttempted = true;
            addedEntries.add(repositoryDirectoryPath);
            editor.addDir(repositoryDirectoryPath, null, -1);
        }
    }

    private void closeDirectoriesInternal(ISVNEditor editor, int count) throws SVNException {
        for (int i = 0; i < count; i++) {
            closeDirectoryInternal(editor);
        }
    }

    private void closeDirectoryInternal(ISVNEditor editor) throws SVNException {
        editor.closeDir();
    }

    private void getInternal(String repositoryResourcePath, File localFile, Resource wagonResource) throws TransferFailedException, SVNException {
        if (addedEntries != null && addedEntries.contains(repositoryResourcePath)) {
            throw new AssertionError("unexpected wagon state");
        }
        fireGetInitiated(wagonResource, localFile);
        if (!localFile.getParentFile().exists() && !localFile.getParentFile().mkdirs()) {
            throw new TransferFailedException("failed to create " + localFile.getParentFile());
        }
        SVNDirEntry entry = getReadRepository().info(repositoryResourcePath, -1);
        wagonResource.setContentLength(entry.getSize());
        wagonResource.setLastModified(entry.getDate().getTime());
        fireGetStarted(wagonResource, localFile);
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(localFile);
            getReadRepository().getFile(repositoryResourcePath, -1, null, outputStream);
        } catch (FileNotFoundException e) {
            fireTransferError(wagonResource, e, TransferEvent.REQUEST_GET);
            throw new TransferFailedException(e.toString(), e);
        } catch (SVNException e) {
            fireTransferError(wagonResource, e, TransferEvent.REQUEST_GET);
            throw e;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        postProcessListeners(wagonResource, localFile, TransferEvent.REQUEST_GET);
        fireGetCompleted(wagonResource, localFile);
    }

    private void putFileInternal(File localFile, String repositoryResourcePath, Resource wagonResource) throws TransferFailedException, SVNException {
        if (repositoryResourcePath.startsWith("/")) {
            throw new AssertionError("unexpected repository path: " + repositoryResourcePath);
        }
        ISVNEditor editor = getWriteEditor(repositoryResourcePath);
        SVNNodeKind repositoryResourceKind = getReadRepository().checkPath(repositoryResourcePath, -1);
        boolean repositoryResourceExists;
        if (SVNNodeKind.FILE.equals(repositoryResourceKind)) {
            repositoryResourceExists = true;
        } else if (SVNNodeKind.NONE.equals(repositoryResourceKind)) {
            repositoryResourceExists = false;
        } else {
            throw new TransferFailedException(repositoryResourcePath + " exists and is not a file");
        }
        firePutInitiated(wagonResource, localFile);
        wagonResource.setContentLength(localFile.length());
        wagonResource.setLastModified(localFile.lastModified());
        firePutStarted(wagonResource, localFile);
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(localFile);
            if (repositoryResourceExists || addedEntries.contains(repositoryResourcePath)) {
                writeAttempted = true;
                editor.openFile(repositoryResourcePath, -1);
            } else {
                writeAttempted = true;
                addedEntries.add(repositoryResourcePath);
                editor.addFile(repositoryResourcePath, null, -1);
                Map<String, String> autoProperties = getAutoProperties(repositoryResourcePath);
                for (Map.Entry<String, String> entry : autoProperties.entrySet()) {
                    editor.changeFileProperty(repositoryResourcePath, entry.getKey(), SVNPropertyValue.create(entry.getValue()));
                }
            }
            editor.applyTextDelta(repositoryResourcePath, null);
            String checksum = deltaGenerator.sendDelta(repositoryResourcePath, inputStream, editor, true);
            editor.closeFile(repositoryResourcePath, checksum);
        } catch (FileNotFoundException e) {
            fireTransferError(wagonResource, e, TransferEvent.REQUEST_PUT);
            throw new TransferFailedException(e.toString(), e);
        } catch (SVNException e) {
            fireTransferError(wagonResource, e, TransferEvent.REQUEST_PUT);
            throw e;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        postProcessListeners(wagonResource, localFile, TransferEvent.REQUEST_PUT);
        firePutCompleted(wagonResource, localFile);
    }

    private void putDirectoryInternal(File localDirectory, String repositoryDirectoryPath, Resource wagonResource) throws TransferFailedException, SVNException {
        ISVNEditor editor = getWriteEditor(repositoryDirectoryPath);
        openDirectoryInternal(editor, repositoryDirectoryPath);
        File[] localDirectoryContents = localDirectory.listFiles();
        for (File file : localDirectoryContents) {
            Resource wagonFileResource = new Resource(wagonResource.getName() + '/' + file.getName());
            String repositoryFilePath = getResourcePath(wagonFileResource.getName());
            if (file.isDirectory()) {
                putDirectoryInternal(file, repositoryFilePath, wagonFileResource);
            } else {
                putFileInternal(file, repositoryFilePath, wagonFileResource);
            }
        }
        closeDirectoryInternal(editor);
    }

}
