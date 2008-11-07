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

package org.compass.core.mapping;

import org.compass.core.util.Parameter;

/**
 * @author kimchy
 */
public class SpellCheckType extends Parameter {

    protected SpellCheckType(String name) {
        super(name);
    }

    public static final SpellCheckType INCLUDE = new SpellCheckType("INCLUDE");

    public static final SpellCheckType EXCLUDE = new SpellCheckType("EXCLUDE");

    public static final SpellCheckType NA = new SpellCheckType("NA");

    public static SpellCheckType fromString(String spellCheckType) {
        if ("include".equalsIgnoreCase(spellCheckType)) {
            return SpellCheckType.INCLUDE;
        } else if ("exclude".equalsIgnoreCase(spellCheckType)) {
            return SpellCheckType.EXCLUDE;
        } else if ("na".equalsIgnoreCase(spellCheckType)) {
            return SpellCheckType.NA;
        }
        throw new IllegalArgumentException("Can't find spell check type for [" + spellCheckType + "]");
    }

    public static String toString(SpellCheckType spellCheckType) {
        if (spellCheckType == INCLUDE) {
            return "include";
        }
        if (spellCheckType == EXCLUDE) {
            return "exlcude";
        }
        if (spellCheckType == NA) {
            return "na";
        }
        return "na";
    }
}
