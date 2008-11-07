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

package org.compass.core.test.component.cascade.collection;

import java.util.ArrayList;

import org.compass.core.CompassSession;
import org.compass.core.CompassTransaction;
import org.compass.core.test.AbstractTestCase;

/**
 * Simple tests for cascading operation between two root objects (A and B)
 * with component mapping on a collection property.
 *
 * @author kimchy
 */
public class CollectionCascadeTests extends AbstractTestCase {

    protected String[] getMappings() {
        return new String[]{"component/cascade/collection/mapping.cpm.xml"};
    }

    public void testCascadeAll() {
        CompassSession session = openSession();
        CompassTransaction tr = session.beginTransaction();

        B b1 = new B(1, "bvalue1");
        B b2 = new B(2, "bvalue2");
        ArrayList list = new ArrayList();
        list.add(b1);
        list.add(b2);
        A a = new A(1, "avalue", list);
        // this should cause cascading for b as well
        session.create("a", a);

        a = (A) session.load("a", "1");
        session.load("b", "1");
        session.load("b", "2");

        ((B) a.b.get(0)).value = "bupdated1";
        ((B) a.b.get(1)).value = "bupdated2";
        session.save("a", a);
        b1 = (B) session.load("b", "1");
        assertEquals("bupdated1", b1.value);
        b2 = (B) session.load("b", "2");
        assertEquals("bupdated2", b2.value);

        session.delete("a", a);
        assertNull(session.get("a", "1"));
        assertNull(session.get("b", "1"));
        assertNull(session.get("b", "2"));

        tr.commit();
        session.close();
    }
}
