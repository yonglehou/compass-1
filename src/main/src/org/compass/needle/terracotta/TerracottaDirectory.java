/*
 * Copyright 2004-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.compass.needle.terracotta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * A Terracota based directory based on Lucene RAM directory improved to support
 * better concurrency.
 *
 * <p>Basically, the direcotry stores {@link org.compass.needle.terracotta.TerracottaFile}
 * which are broken into one or more byte arrays. The size of the byte array can be configured
 * but should not be changed once the index created.
 *
 * @author kimchy
 */
public class TerracottaDirectory extends Directory {

    public static final transient int DEFAULT_BUFFER_SIZE = 4096;

    public static final transient int DEFAULT_FLUSH_RATE = 10;

    private static final transient Log log = LogFactory.getLog(TerracottaDirectory.class);

    private Map<String, TerracottaFile> fileMap = new ConcurrentHashMap<String, TerracottaFile>();

    private final int bufferSize;

    private final int flushRate;

    public TerracottaDirectory() {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_FLUSH_RATE);
    }

    /**
     * Constructs an empty {@link Directory}.
     */
    public TerracottaDirectory(int bufferSize, int flushRate) {
        this.bufferSize = bufferSize;
        this.flushRate = flushRate;
        try {
            Class.forName("com.tc.object.bytecode.ManagerUtil", true, Thread.currentThread().getContextClassLoader());
//            setLockFactory(new TerracottaManagerUtilLockFactory());
            // Use the default lock factory for now as there are some problems with ManagerUtil
            setLockFactory(new TerracottaLockFactory());
        } catch (ClassNotFoundException e) {
            setLockFactory(new TerracottaLockFactory());
        }
        if (log.isDebugEnabled()) {
            log.debug("Using Terracota lock factory [" + getLockFactory() + "]");
        }
    }

    /**
     */
    public TerracottaDirectory(Directory dir) throws IOException {
        this(dir, false);
    }

    private TerracottaDirectory(Directory dir, boolean closeDir) throws IOException {
        this(DEFAULT_BUFFER_SIZE, DEFAULT_FLUSH_RATE);
        Directory.copy(dir, this, closeDir);
    }

    /**
     */
    public TerracottaDirectory(File dir) throws IOException {
        this(FSDirectory.getDirectory(dir), true);
    }

    /**
     */
    public TerracottaDirectory(String dir) throws IOException {
        this(FSDirectory.getDirectory(dir), true);
    }

    /**
     * Returns an array of strings, one for each file in the directory.
     */
    public final String[] list() {
        Set<String> fileNames = fileMap.keySet();
        return fileNames.toArray(new String[fileNames.size()]);
    }

    /**
     * Returns true iff the named file exists in this directory.
     */
    public final boolean fileExists(String name) {
        return fileMap.containsKey(name);
    }

    /**
     * Returns the time the named file was last modified.
     *
     * @throws IOException if the file does not exist
     */
    public final long fileModified(String name) throws IOException {
        TerracottaFile file = fileMap.get(name);
        if (file == null) {
            throw new FileNotFoundException(name);
        }
        return file.getLastModified();
    }

    /**
     * Set the modified time of an existing file to now.
     *
     * @throws IOException if the file does not exist
     */
    public void touchFile(String name) throws IOException {
        TerracottaFile file = fileMap.get(name);
        if (file == null) {
            throw new FileNotFoundException(name);
        }

        long ts2, ts1 = System.currentTimeMillis();
        do {
            try {
                Thread.sleep(0, 1);
            } catch (InterruptedException e) {
                // do nothing
            }
            ts2 = System.currentTimeMillis();
        } while (ts1 == ts2);

        file.setLastModified(ts2);
    }

    /**
     * Returns the length in bytes of a file in the directory.
     *
     * @throws IOException if the file does not exist
     */
    public final long fileLength(String name) throws IOException {
        TerracottaFile file = fileMap.get(name);
        if (file == null) {
            throw new FileNotFoundException(name);
        }
        return file.getLength();
    }

    /**
     * Removes an existing file in the directory.
     *
     * @throws IOException if the file does not exist
     */
    public void deleteFile(String name) throws IOException {
        TerracottaFile file = fileMap.remove(name);
        if (file == null) {
            throw new FileNotFoundException(name);
        }
    }

    /**
     * Renames an existing file in the directory.
     *
     * @throws FileNotFoundException if from does not exist
     * @deprecated
     */
    public final void renameFile(String from, String to) throws IOException {
        TerracottaFile fromFile = fileMap.get(from);
        if (fromFile == null) {
            throw new FileNotFoundException(from);
        }
        // not thread safe (dough), but it is deprecated...
        fileMap.remove(from);
        fileMap.put(to, fromFile);
    }

    /**
     * Creates a new, empty file in the directory with the given name. Returns a stream writing this file.
     */
    public IndexOutput createOutput(String name) throws IOException {
        return new TerracottaIndexOutput(this, name);
    }

    /**
     * Returns a stream reading an existing file.
     */
    public IndexInput openInput(String name) throws IOException {
        TerracottaFile file = fileMap.get(name);
        if (file == null) {
            throw new FileNotFoundException(name);
        }
        return new TerracottaIndexInput(file, bufferSize);
    }

    void addFile(String name, TerracottaFile file) {
        fileMap.put(name, file);
    }

    int getBufferSize() {
        return bufferSize;
    }

    int getFlushRate() {
        return flushRate;
    }

    /**
     * Closes the store to future operations, releasing associated memory.
     */
    public void close() {
        // don't null it since we want to keep it shared bettween tc instances.
//        fileMap = null;
    }
}