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

package org.compass.core.impl;

import java.io.Reader;
import java.io.Serializable;

import org.compass.core.CompassAnalyzerHelper;
import org.compass.core.CompassException;
import org.compass.core.CompassHits;
import org.compass.core.CompassQuery;
import org.compass.core.CompassQueryBuilder;
import org.compass.core.CompassQueryFilterBuilder;
import org.compass.core.CompassSession;
import org.compass.core.CompassTermInfoVector;
import org.compass.core.CompassTransaction;
import org.compass.core.Property;
import org.compass.core.Resource;
import org.compass.core.spi.InternalCompass;
import org.compass.core.spi.InternalCompassSession;
import org.compass.core.CompassTransaction.TransactionIsolation;
import org.compass.core.cache.first.FirstLevelCache;
import org.compass.core.cache.first.NullFirstLevelCache;
import org.compass.core.engine.SearchEngine;
import org.compass.core.engine.SearchEngineAnalyzerHelper;
import org.compass.core.engine.SearchEngineQueryBuilder;
import org.compass.core.engine.SearchEngineQueryFilterBuilder;
import org.compass.core.mapping.CompassMapping;
import org.compass.core.marshall.DefaultMarshallingStrategy;
import org.compass.core.marshall.MarshallingContext;
import org.compass.core.marshall.MarshallingStrategy;
import org.compass.core.metadata.CompassMetaData;
import org.compass.core.transaction.TransactionFactory;

/**
 * 
 * @author kimchy
 * 
 */
// TODO we need to support multiple resource with ResourceIdKey and first cache
public class DefaultCompassSession implements InternalCompassSession {

    private InternalCompass compass;

    private CompassMapping mapping;

    private CompassMetaData compassMetaData;

    private SearchEngine searchEngine;

    private TransactionFactory transactionFactory;

    private MarshallingStrategy marshallingStrategy;

    private FirstLevelCache firstLevelCache;

    public DefaultCompassSession(InternalCompass compass, SearchEngine searchEngine, FirstLevelCache firstLevelCache) {
        this.compass = compass;
        this.mapping = compass.getMapping();
        this.compassMetaData = compass.getMetaData();
        this.transactionFactory = compass.getTransactionFactory();
        this.searchEngine = searchEngine;
        this.firstLevelCache = firstLevelCache;
        this.marshallingStrategy = new DefaultMarshallingStrategy(mapping, searchEngine, compass.getConverterLookup(),
                this);
    }

    public Resource createResource(String alias) throws CompassException {
        return searchEngine.createResource(alias);
    }

    public Property createProperty(String name, String value, Property.Store store, Property.Index index)
            throws CompassException {
        return searchEngine.createProperty(name, value, store, index);
    }

    public Property createProperty(String name, String value, Property.Store store, Property.Index index,
            Property.TermVector termVector) throws CompassException {
        return searchEngine.createProperty(name, value, store, index, termVector);
    }

    public Property createProperty(String name, Reader value) throws CompassException {
        return searchEngine.createProperty(name, value);
    }

    public Property createProperty(String name, Reader value, Property.TermVector termVector) throws CompassException {
        return searchEngine.createProperty(name, value, termVector);
    }

    public Property createProperty(String name, byte[] value, Property.Store store) throws CompassException {
        return searchEngine.createProperty(name, value, store);
    }


    // TODO DELETE in the next version
    public CompassQueryBuilder createQueryBuilder() throws CompassException {
        return queryBuilder();
    }

    public CompassQueryBuilder queryBuilder() throws CompassException {
        SearchEngineQueryBuilder searchEngineQueryBuilder = searchEngine.queryBuilder();
        return new DefaultCompassQueryBuilder(searchEngineQueryBuilder, this);
    }

    // TODO DELETE in the next version
    public CompassQueryFilterBuilder createQueryFilterBuilder() throws CompassException {
        return queryFilterBuilder();
    }

    public CompassQueryFilterBuilder queryFilterBuilder() throws CompassException {
        SearchEngineQueryFilterBuilder searchEngineQueryFilterBuilder = searchEngine.queryFilterBuilder();
        return new DefaultCompassQueryFilterBuilder(searchEngineQueryFilterBuilder, this);
    }

    public CompassAnalyzerHelper analyzerHelper() throws CompassException {
        SearchEngineAnalyzerHelper analyzerHelper = searchEngine.analyzerHelper();
        return new DefaultCompassAnalyzerHelper(analyzerHelper, this);
    }

    public CompassTransaction beginTransaction() throws CompassException {
        return transactionFactory.beginTransaction(this, null);
    }
    
    public CompassTransaction beginTransaction(TransactionIsolation transactionIsolation) throws CompassException {
        if (transactionIsolation == TransactionIsolation.BATCH_INSERT) {
            firstLevelCache = new NullFirstLevelCache();
        }
        return transactionFactory.beginTransaction(this, transactionIsolation);
    }

    public void flush() throws CompassException {
        searchEngine.flush();
    }

    public void delete(Resource resource) throws CompassException {
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.evict(key);
        searchEngine.delete(resource);
    }

    public void delete(String alias, Object obj) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(alias, obj);
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        firstLevelCache.evict(key);
        delete(idResource);
    }

    public void delete(Object obj) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(obj);
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        firstLevelCache.evict(key);
        delete(idResource);
    }

    public Resource getResource(Class clazz, Serializable id) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(clazz, id);
        return getResourceByIdResource(idResource);
    }

    public Resource getResource(String alias, Serializable id) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(alias, id);
        return getResourceByIdResource(idResource);
    }

    public Resource getResourceByIdResource(Resource idResource) {
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        Resource cachedValue = firstLevelCache.getResource(key);
        if (cachedValue != null) {
            return cachedValue;
        }
        Resource value = searchEngine.get(idResource);
        firstLevelCache.setResource(key, value);
        return value;
    }

    public Object get(Class clazz, Serializable id) throws CompassException {
        Resource resource = getResource(clazz, id);
        if (resource == null) {
            return null;
        }
        return getByResource(resource);
    }

    public Object get(String alias, Serializable id) throws CompassException {
        Resource resource = getResource(alias, id);
        if (resource == null) {
            return null;
        }
        return getByResource(resource);
    }

    public Object get(String alias, Serializable id, MarshallingContext context) throws CompassException {
        Resource resource = getResource(alias, id);
        if (resource == null) {
            return null;
        }
        return getByResource(resource, context);
    }

    public Object getByResource(Resource resource) {
        return getByResource(resource, null);
    }

    public Object getByResource(Resource resource, MarshallingContext context) {
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        Object cachedValue = firstLevelCache.get(key);
        if (cachedValue != null) {
            return cachedValue;
        }
        Object value;
        if (context == null) {
            value = marshallingStrategy.unmarshall(resource);
        } else {
            value = marshallingStrategy.unmarshall(resource, context);
        }
        firstLevelCache.set(key, value);
        return value;
    }

    public Resource loadResource(Class clazz, Serializable id) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(clazz, id);
        return loadResourceByIdResource(idResource);
    }

    public Resource loadResource(String alias, Serializable id) throws CompassException {
        Resource idResource = marshallingStrategy.marshallIds(alias, id);
        return loadResourceByIdResource(idResource);
    }

    public Resource loadResourceByIdResource(Resource idResource) {
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        Resource cachedValue = firstLevelCache.getResource(key);
        if (cachedValue != null) {
            return cachedValue;
        }
        Resource value = searchEngine.load(idResource);
        firstLevelCache.setResource(key, value);
        return value;
    }

    public Object load(Class clazz, Serializable id) throws CompassException {
        Resource resource = loadResource(clazz, id);
        return getByResource(resource);
    }

    public Object load(String alias, Serializable id) throws CompassException {
        Resource resource = loadResource(alias, id);
        return getByResource(resource);
    }

    public CompassHits find(String query) throws CompassException {
        return createQueryBuilder().queryString(query).toQuery().hits();
    }

    public void create(String alias, Object object) throws CompassException {
        Resource resource = marshallingStrategy.marshall(alias, object);
        searchEngine.create(resource);
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.set(key, object);
    }

    public void save(String alias, Object object) throws CompassException {
        Resource resource = marshallingStrategy.marshall(alias, object);
        searchEngine.save(resource);
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.set(key, object);
    }

    public void create(Object object) throws CompassException {
        Resource resource = marshallingStrategy.marshall(object);
        searchEngine.create(resource);
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.set(key, object);
    }

    public void save(Object object) throws CompassException {
        Resource resource = marshallingStrategy.marshall(object);
        searchEngine.save(resource);
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.set(key, object);
    }

    public void delete(CompassQuery query) throws CompassException {
        CompassHits hits = query.hits();
        for (int i = 0; i < hits.length(); i++) {
            delete(hits.resource(i));
        }
    }

    public CompassTermInfoVector getTermInfo(Resource resource, String propertyName) throws CompassException {
        return searchEngine.getTermInfo(resource, propertyName);
    }

    public CompassTermInfoVector[] getTermInfos(Resource resource) throws CompassException {
        return searchEngine.getTermInfos(resource);
    }

    public void evict(Object obj) {
        Resource idResource = marshallingStrategy.marshallIds(obj.getClass(), obj);
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        firstLevelCache.evict(key);
    }

    public void evict(String alias, Object id) {
        Resource idResource = marshallingStrategy.marshallIds(alias, id);
        ResourceIdKey key = new ResourceIdKey(mapping, idResource);
        firstLevelCache.evict(key);
    }

    public void evict(Resource resource) {
        ResourceIdKey key = new ResourceIdKey(mapping, resource);
        firstLevelCache.evict(key);
    }

    public void evictAll() {
        firstLevelCache.evictAll();
    }

    public void close() throws CompassException {
        CompassSession transactionBoundSession = transactionFactory.getTransactionBoundSession();
        if (transactionBoundSession == null || transactionBoundSession != this) {
            firstLevelCache.evictAll();
            searchEngine.close();
        }
    }
    
    public InternalCompass getCompass() {
        return compass;
    }

    public SearchEngine getSearchEngine() {
        return searchEngine;
    }

    public MarshallingStrategy getMarshallingStrategy() {
        return marshallingStrategy;
    }

    public FirstLevelCache getFirstLevelCache() {
        return firstLevelCache;
    }

    public CompassMapping getMapping() {
        return mapping;
    }

    public CompassMetaData getMetaData() {
        return compassMetaData;
    }
}
