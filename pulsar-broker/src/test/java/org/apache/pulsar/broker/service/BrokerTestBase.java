/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.broker.service;

import com.google.common.collect.Sets;
import java.util.Random;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.auth.MockedPulsarServiceBaseTest;
import org.apache.pulsar.common.naming.SystemTopicNames;
import org.apache.pulsar.common.partition.PartitionedTopicMetadata;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.TenantInfoImpl;
import org.apache.pulsar.metadata.api.MetadataStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BrokerTestBase extends MockedPulsarServiceBaseTest {
    protected static final int ASYNC_EVENT_COMPLETION_WAIT = 100;

    public void baseSetup() throws Exception {
        super.internalSetup();
        baseSetupCommon();
        afterSetup();
    }

    public void baseSetup(ServiceConfiguration serviceConfiguration) throws Exception {
        super.internalSetup(serviceConfiguration);
        baseSetupCommon();
        afterSetup();
    }

    protected void afterSetup() throws Exception {
        // NOP
    }

    private void baseSetupCommon() throws Exception {
        admin.clusters().createCluster("test", ClusterData.builder().serviceUrl(brokerUrl.toString()).build());
        admin.tenants().createTenant("prop",
                new TenantInfoImpl(Sets.newHashSet("appid1"), Sets.newHashSet("test")));
        admin.namespaces().createNamespace("prop/ns-abc");
        admin.namespaces().setNamespaceReplicationClusters("prop/ns-abc", Sets.newHashSet("test"));
    }

    protected void createTransactionCoordinatorAssign() throws MetadataStoreException {
        createTransactionCoordinatorAssign(1);
    }

    protected void createTransactionCoordinatorAssign(int partitions) throws MetadataStoreException {
        pulsar.getPulsarResources()
                .getNamespaceResources()
                .getPartitionedTopicResources()
                .createPartitionedTopic(SystemTopicNames.TRANSACTION_COORDINATOR_ASSIGN,
                        new PartitionedTopicMetadata(partitions));
    }

    void rolloverPerIntervalStats() {
        try {
            pulsar.getExecutor().submit(() -> pulsar.getBrokerService().updateRates()).get();
        } catch (Exception e) {
            LOG.error("Stats executor error", e);
        }
    }

    protected void runGC() {
        try {
            pulsar.getBrokerService().forEachTopic(topic -> {
                if (topic instanceof AbstractTopic) {
                    ((AbstractTopic) topic).getInactiveTopicPolicies().setMaxInactiveDurationSeconds(0);
                }
            });
            pulsar.getExecutor().submit(() -> pulsar.getBrokerService().checkGC()).get();
            Thread.sleep(ASYNC_EVENT_COMPLETION_WAIT);
        } catch (Exception e) {
            LOG.error("GC executor error", e);
        }
    }

    void runMessageExpiryCheck() {
        try {
            pulsar.getExecutor().submit(() -> pulsar.getBrokerService().checkMessageExpiry()).get();
            Thread.sleep(ASYNC_EVENT_COMPLETION_WAIT);
        } catch (Exception e) {
            LOG.error("Error running message expiry check", e);
        }
    }

    private static final Random random = new Random();

    protected String newTopicName() {
        return "prop/ns-abc/topic-" + Long.toHexString(random.nextLong());
    }

    private static final Logger LOG = LoggerFactory.getLogger(BrokerTestBase.class);
}
