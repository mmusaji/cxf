/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.ws.policy.selector;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.ws.policy.AlternativeSelector;
import org.apache.cxf.ws.policy.Assertor;
import org.apache.cxf.ws.policy.PolicyEngine;
import org.apache.neethi.Assertion;
import org.apache.neethi.Policy;

/**
 * 
 */
public class MinimalAlternativeSelector implements AlternativeSelector {

    public Collection<Assertion> selectAlternative(Policy policy, PolicyEngine engine, Assertor assertor) {
        Collection<Assertion> choice = null;
        Iterator alternatives = policy.getAlternatives();
        while (alternatives.hasNext()) {
            List<Assertion> alternative = CastUtils.cast((List)alternatives.next(), Assertion.class);
            if (engine.supportsAlternative(alternative, assertor) 
                && (null == choice || alternative.size() < choice.size())) {
                choice = alternative;
            }
        }
        return choice;
    }
}
