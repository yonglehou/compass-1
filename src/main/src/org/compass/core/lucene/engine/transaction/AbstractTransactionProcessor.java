/*
 * Copyright 2004-2008 the original author or authors.
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

package org.compass.core.lucene.engine.transaction;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.compass.core.engine.SearchEngineException;
import org.compass.core.engine.SearchEngineQuery;
import org.compass.core.lucene.engine.LuceneSearchEngine;
import org.compass.core.lucene.engine.LuceneSearchEngineHits;
import org.compass.core.lucene.engine.LuceneSearchEngineInternalSearch;
import org.compass.core.lucene.engine.LuceneSearchEngineQuery;
import org.compass.core.lucene.engine.analyzer.LuceneAnalyzerManager;
import org.compass.core.lucene.engine.manager.LuceneIndexHolder;
import org.compass.core.lucene.engine.manager.LuceneSearchEngineIndexManager;
import org.compass.core.lucene.search.CacheableMultiReader;
import org.compass.core.mapping.CompassMapping;
import org.compass.core.mapping.ResourceMapping;
import org.compass.core.spi.InternalResource;
import org.compass.core.spi.ResourceKey;

/**
 * A base class for all Lucene based transactions. Provides helper methods for
 * Lucene index transaction management, and default state management for the
 * transcational operations.
 *
 * @author kimchy
 */
public abstract class AbstractTransactionProcessor implements TransactionProcessor {

    protected final LuceneSearchEngine searchEngine;

    protected final LuceneSearchEngineIndexManager indexManager;

    protected final CompassMapping mapping;

    protected final LuceneAnalyzerManager analyzerManager;

    protected boolean dirty;

    protected AbstractTransactionProcessor(LuceneSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        this.indexManager = searchEngine.getSearchEngineFactory().getLuceneIndexManager();
        this.mapping = searchEngine.getSearchEngineFactory().getMapping();
        this.analyzerManager = searchEngine.getSearchEngineFactory().getAnalyzerManager();
    }

    public void begin() throws SearchEngineException {
        doBegin();
    }

    protected abstract void doBegin() throws SearchEngineException;

    public void rollback() throws SearchEngineException {
        doRollback();
    }

    protected abstract void doRollback() throws SearchEngineException;

    public void prepare() throws SearchEngineException {
        doPrepare();
    }

    protected abstract void doPrepare() throws SearchEngineException;

    public void commit(boolean onePhase) throws SearchEngineException {
        doCommit(onePhase);
    }

    protected abstract void doCommit(boolean onePhase) throws SearchEngineException;

    public LuceneSearchEngineHits find(SearchEngineQuery query) throws SearchEngineException {
        return doFind((LuceneSearchEngineQuery) query);
    }

    protected abstract LuceneSearchEngineHits doFind(LuceneSearchEngineQuery query) throws SearchEngineException;

    public LuceneSearchEngineInternalSearch internalSearch(String[] subIndexes, String[] aliases) throws SearchEngineException {
        return doInternalSearch(subIndexes, aliases);
    }

    protected LuceneSearchEngineInternalSearch doInternalSearch(String[] subIndexes, String[] aliases) throws SearchEngineException {
        ArrayList<LuceneIndexHolder> indexHoldersToClose = new ArrayList<LuceneIndexHolder>();
        try {
            String[] calcSubIndexes = indexManager.getStore().calcSubIndexes(subIndexes, aliases);
            // currenly we disable search by multireader and do it with multisearcher so field cache
            // will work correclty (as we won't create a new "outer" reader each time, which will cause
            // the field cache to invalidate each time
            if (false) {
                ArrayList<IndexReader> readers = new ArrayList<IndexReader>(calcSubIndexes.length);
                LuceneIndexHolder lastNonEmptyIndexHolder = null;
                for (String subIndex : calcSubIndexes) {
                    LuceneIndexHolder indexHolder = indexManager.openIndexHolderBySubIndex(subIndex);
                    indexHoldersToClose.add(indexHolder);
                    if (indexHolder.getIndexReader().numDocs() > 0) {
                        readers.add(indexHolder.getIndexReader());
                        lastNonEmptyIndexHolder = indexHolder;
                    }
                }
                if (readers.size() == 0) {
                    return new LuceneSearchEngineInternalSearch(searchEngine);
                }
                // if we have just one reader, no need to create a multi reader on top of it
                if (readers.size() == 1) {
                    return new LuceneSearchEngineInternalSearch(searchEngine, lastNonEmptyIndexHolder, indexHoldersToClose);
                }
                MultiReader reader = new CacheableMultiReader(readers.toArray(new IndexReader[readers.size()]), false);
                IndexSearcher searcher = indexManager.openIndexSearcher(reader);
                return new LuceneSearchEngineInternalSearch(searchEngine, reader, searcher, indexHoldersToClose);
            } else {
                ArrayList<IndexSearcher> searchers = new ArrayList<IndexSearcher>(calcSubIndexes.length);
                LuceneIndexHolder lastNonEmptyIndexHolder = null;
                for (String subIndex : calcSubIndexes) {
                    LuceneIndexHolder indexHolder = indexManager.openIndexHolderBySubIndex(subIndex);
                    indexHoldersToClose.add(indexHolder);
                    if (indexHolder.getIndexReader().numDocs() > 0) {
                        searchers.add(indexHolder.getIndexSearcher());
                        lastNonEmptyIndexHolder = indexHolder;
                    }
                }
                if (searchers.size() == 0) {
                    return new LuceneSearchEngineInternalSearch(searchEngine);
                }
                // if we have just one reader, no need to create a multi reader on top of it
                if (searchers.size() == 1) {
                    return new LuceneSearchEngineInternalSearch(searchEngine, lastNonEmptyIndexHolder, indexHoldersToClose);
                }
                MultiSearcher searcher = indexManager.openMultiSearcher(searchers.toArray(new IndexSearcher[searchers.size()]));
                return new LuceneSearchEngineInternalSearch(searchEngine, searcher, indexHoldersToClose);
            }
        } catch (Exception e) {
            for (LuceneIndexHolder indexHolder : indexHoldersToClose) {
                indexHolder.release();
            }
            throw new SearchEngineException("Failed to open Lucene reader/searcher", e);
        }
    }


    public void create(final InternalResource resource) throws SearchEngineException {
        dirty = true;
        doCreate(resource);
    }

    protected abstract void doCreate(final InternalResource resource) throws SearchEngineException;

    public void delete(final ResourceKey resourceKey) throws SearchEngineException {
        dirty = true;
        doDelete(resourceKey);
    }

    protected abstract void doDelete(final ResourceKey resourceKey) throws SearchEngineException;

    public void update(InternalResource resource) throws SearchEngineException {
        dirty = true;
        doUpdate(resource);
    }

    protected void doUpdate(InternalResource resource) throws SearchEngineException {
        doDelete(resource.getResourceKey());
        doCreate(resource);
    }

    public boolean isDirty() {
        return dirty;
    }

    protected ResourceMapping getResourceMapping(String alias) {
        return mapping.getRootMappingByAlias(alias);
    }

    protected Hits findByQuery(LuceneSearchEngineInternalSearch internalSearch,
                               LuceneSearchEngineQuery searchEngineQuery, Filter filter) throws SearchEngineException {
        Query query = searchEngineQuery.getQuery();
        if (searchEngineQuery.isRewrite()) {
            try {
                query = query.rewrite(internalSearch.getReader());
            } catch (IOException e) {
                throw new SearchEngineException("Failed to rewrite query [" + query.toString() + "]", e);
            }
        }
        Sort sort = searchEngineQuery.getSort();
        Hits hits;
        try {
            if (filter == null) {
                hits = internalSearch.getSearcher().search(query, sort);
            } else {
                hits = internalSearch.getSearcher().search(query, filter, sort);
            }
        } catch (IOException e) {
            throw new SearchEngineException("Failed to search with query [" + query + "]", e);
        }
        return hits;
    }
}
