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

package org.compass.gps.device.jpa.entities;

import javax.persistence.EntityManagerFactory;

import org.compass.gps.device.jpa.JpaGpsDevice;
import org.compass.gps.device.jpa.JpaGpsDeviceException;

/**
 * Resposible for locating entities for the index process. Most of the times the {@link DefaultJpaEntitiesLocator}
 * should suffice, but it only works with JPA annotations. For JPA implementations that uses a combination of
 * both annotations and other means of configuration (like xml), an implementaiton of this locator is required.
 * <p/>
 * Assume that the <code>EntityManagerFactory</code> is the native one, since the
 * {@link org.compass.gps.device.jpa.NativeJpaExtractor} of the
 * {@link JpaGpsDevice} was used to extract it.
 *
 * @author kimchy
 * @see JpaEntitiesLocatorDetector
 * @see org.compass.gps.device.jpa.entities.HibernateJpaEntitiesLocator
 */
public interface JpaEntitiesLocator {

    /**
     * Locates the entities used for the index operation.
     *
     * @param entityManagerFactory The <code>EntityManagerFactory<code> to be optionally used for locating the entities.
     * @param device               The Jpa device that called this locator.
     * @return An array of the enteties that need to be indexed during the index operation.
     * @throws JpaGpsDeviceException
     */
    EntityInformation[] locate(EntityManagerFactory entityManagerFactory, JpaGpsDevice device) throws JpaGpsDeviceException;
}
