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

package org.compass.core.test.transactionisolation.lucene;

import org.compass.core.CompassHits;
import org.compass.core.CompassSession;
import org.compass.core.CompassTransaction;
import org.compass.core.config.CompassEnvironment;
import org.compass.core.config.CompassSettings;
import org.compass.core.spi.InternalCompassSession;
import org.compass.core.spi.InternalResource;
import org.compass.core.test.AbstractTestCase;

/**
 * 
 * @author kimchy
 * 
 */
public class LuceneTransactionIsolationTests extends AbstractTestCase {

    protected String[] getMappings() {
        return new String[] { "transactionisolation/lucene/mapping.cpm.xml" };
    }

    protected void addSettings(CompassSettings settings) {
        super.addSettings(settings);
        settings.setSetting(CompassEnvironment.Transaction.ISOLATION, CompassEnvironment.Transaction.ISOLATION_LUCENE);
    }

    public void testBatch() {
        CompassSession session = openSession();
        CompassTransaction tr = session.beginTransaction();

        Long id = new Long(1);
        Cyclic1 cyclic1 = new Cyclic1();
        cyclic1.setId(id);
        cyclic1.setValue("cyclic1");

        Cyclic2 cyclic2 = new Cyclic2();
        cyclic2.setId(id);
        cyclic2.setValue("cyclic2");

        cyclic1.setCyclic2(cyclic2);
        cyclic2.setCyclic1(cyclic1);

        session.create(cyclic2);
        session.create(cyclic1);

        // verify they are not in the first level cache
        InternalResource key = (InternalResource) session.createResource("cyclic1");
        key.addProperty("id", id);
        Object val = ((InternalCompassSession) session).getFirstLevelCache().get(key.resourceKey());
        assertNull(val);
        key = (InternalResource) session.createResource("cyclic2");
        key.addProperty("id", id);
        val = ((InternalCompassSession) session).getFirstLevelCache().get(key.resourceKey());
        assertNull(val);

        tr.commit();

        tr = session.beginTransaction();

        CompassHits hits = session.find("value:cyclic1");
        assertEquals(1, hits.length());
        hits = session.find("value:cyclic2");
        assertEquals(1, hits.length());

        cyclic2.setValue("updated");
        session.save(cyclic2);
        tr.commit();

        tr = session.beginTransaction();
        hits = session.find("value:cyclic2");
        assertEquals(0, hits.length());
        hits = session.find("value:updated");
        assertEquals(1, hits.length());
        tr.commit();

        tr = session.beginTransaction();
        cyclic2 = session.load(Cyclic2.class, 1);
        assertNotNull(cyclic2);
        tr.commit();

        tr = session.beginTransaction();
        session.delete(cyclic2);
        tr.commit();

        tr = session.beginTransaction();
        cyclic2 = session.get(Cyclic2.class, 1);
        assertNull(cyclic2);
        tr.commit();
    }
}