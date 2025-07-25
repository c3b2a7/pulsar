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

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;
import com.google.common.collect.Sets;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Cleanup;
import org.apache.pulsar.broker.service.persistent.DispatchRateLimiter;
import org.apache.pulsar.broker.service.persistent.PersistentTopic;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.MessageRoutingMode;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.common.policies.data.DispatchRate;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Starts 3 brokers that are in 3 different clusters.
 */
@Test(groups = "broker")
public class ReplicatorRateLimiterTest extends ReplicatorTestBase {

    protected String methodName;

    @BeforeMethod
    public void beforeMethod(Method m) throws Exception {
        methodName = m.getName();
    }

    @Override
    @BeforeClass(timeOut = 300000)
    public void setup() throws Exception {
        super.setup();
    }

    @Override
    @AfterClass(alwaysRun = true, timeOut = 300000)
    public void cleanup() throws Exception {
        super.cleanup();
    }

    enum DispatchRateType {
        messageRate, byteRate
    }

    @DataProvider(name = "dispatchRateType")
    public Object[][] dispatchRateProvider() {
        return new Object[][] { { DispatchRateType.messageRate }, { DispatchRateType.byteRate } };
    }

    @Test
    public void testReplicatorRateLimiterWithOnlyTopicLevel() throws Exception {
        cleanup();
        config1.setDispatchThrottlingRatePerReplicatorInMsg(0); // disable broker level
        config1.setDispatchThrottlingRatePerReplicatorInByte(0L);
        setup();

        final String namespace = "pulsar/replicatorchange-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/testReplicatorRateLimiterWithOnlyTopicLevel";

        admin1.namespaces().createNamespace(namespace);
        // set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));
        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString())
            .statsInterval(0, TimeUnit.SECONDS).build();
        client1.newProducer().topic(topicName).create().close();
        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        // rate limiter disable by default
        assertFalse(getRateLimiter(topic).isPresent());

        //set topic-level policy, which should take effect
        DispatchRate topicRate = DispatchRate.builder()
            .dispatchThrottlingRateInMsg(10)
            .dispatchThrottlingRateInByte(20)
            .ratePeriodInSecond(30)
            .build();
        admin1.topics().setReplicatorDispatchRate(topicName, topicRate);
        Awaitility.await().untilAsserted(() -> {
            assertEquals(admin1.topics().getReplicatorDispatchRate(topicName), topicRate);
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 10);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 20L);
        });

        //remove topic-level policy
        admin1.topics().removeReplicatorDispatchRate(topicName);
        Awaitility.await().untilAsserted(() -> {
            assertNull(admin1.topics().getReplicatorDispatchRate(topicName));
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), -1);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), -1L);
        });
    }

    @Test
    public void testReplicatorRateLimiterWithOnlyNamespaceLevel() throws Exception {
        cleanup();
        config1.setDispatchThrottlingRatePerReplicatorInMsg(0); // disable broker level
        config1.setDispatchThrottlingRatePerReplicatorInByte(0L);
        setup();

        final String namespace = "pulsar/replicatorchange-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/testReplicatorRateLimiterWithOnlyNamespaceLevel";

        admin1.namespaces().createNamespace(namespace);
        // set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));
        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString())
            .statsInterval(0, TimeUnit.SECONDS).build();
        client1.newProducer().topic(topicName).create().close();
        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        // rate limiter disable by default
        assertFalse(getRateLimiter(topic).isPresent());

        //set namespace-level policy, which should take effect
        DispatchRate topicRate = DispatchRate.builder()
            .dispatchThrottlingRateInMsg(10)
            .dispatchThrottlingRateInByte(20)
            .ratePeriodInSecond(30)
            .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, topicRate);
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 10);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 20L);
        });

        //remove topic-level policy
        admin1.namespaces().removeReplicatorDispatchRate(namespace);
        assertNull(admin1.namespaces().getReplicatorDispatchRate(namespace));
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), -1);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), -1L);
        });
    }

    @Test
    public void testReplicatorRateLimiterWithOnlyBrokerLevel() throws Exception {
        cleanup();
        config1.setDispatchThrottlingRatePerReplicatorInMsg(0); // disable broker level when init
        config1.setDispatchThrottlingRatePerReplicatorInByte(0L);
        setup();

        final String namespace = "pulsar/replicatorchange-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/testReplicatorRateLimiterWithOnlyBrokerLevel";

        admin1.namespaces().createNamespace(namespace);
        // set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));
        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString())
            .statsInterval(0, TimeUnit.SECONDS).build();
        client1.newProducer().topic(topicName).create().close();
        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        // rate limiter disable by default
        assertFalse(getRateLimiter(topic).isPresent());

        //set broker-level policy, which should take effect
        admin1.brokers().updateDynamicConfiguration("dispatchThrottlingRatePerReplicatorInMsg", "10");
        admin1.brokers().updateDynamicConfiguration("dispatchThrottlingRatePerReplicatorInByte", "20");
        Awaitility.await().untilAsserted(() -> {
            assertTrue(admin1.brokers()
                .getAllDynamicConfigurations().containsKey("dispatchThrottlingRatePerReplicatorInByte"));
            assertEquals(admin1.brokers()
                .getAllDynamicConfigurations().get("dispatchThrottlingRatePerReplicatorInMsg"), "10");
            assertEquals(admin1.brokers()
                .getAllDynamicConfigurations().get("dispatchThrottlingRatePerReplicatorInByte"), "20");
        });

        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(getRateLimiter(topic).get().getDispatchRateOnMsg(), 10);
            assertEquals(getRateLimiter(topic).get().getDispatchRateOnByte(), 20L);
        });
    }

    @Test
    public void testReplicatorRatePriority() throws Exception {
        cleanup();
        config1.setDispatchThrottlingRatePerReplicatorInMsg(100);
        config1.setDispatchThrottlingRatePerReplicatorInByte(200L);
        setup();

        final String namespace = "pulsar/replicatorchange-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/ratechange";

        admin1.namespaces().createNamespace(namespace);
        // set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));
        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString())
                .statsInterval(0, TimeUnit.SECONDS).build();
        client1.newProducer().topic(topicName).create().close();
        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        //use broker-level by default
        assertTrue(getRateLimiter(topic).isPresent());
        assertEquals(getRateLimiter(topic).get().getDispatchRateOnMsg(), 100);
        assertEquals(getRateLimiter(topic).get().getDispatchRateOnByte(), 200L);

        //set namespace-level policy, which should take effect
        DispatchRate nsDispatchRate = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(50)
                .dispatchThrottlingRateInByte(60)
                .ratePeriodInSecond(60)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, nsDispatchRate);
        assertEquals(admin1.namespaces().getReplicatorDispatchRate(namespace), nsDispatchRate);
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 50);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 60L);
        });

        //set topic-level policy, which should take effect
        DispatchRate topicRate = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(10)
                .dispatchThrottlingRateInByte(20)
                .ratePeriodInSecond(30)
                .build();
        admin1.topics().setReplicatorDispatchRate(topicName, topicRate);
        Awaitility.await().untilAsserted(() -> {
            assertEquals(admin1.topics().getReplicatorDispatchRate(topicName), topicRate);
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 10);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 20L);
        });

        //Set the namespace-level policy, which should not take effect
        DispatchRate nsDispatchRate2 = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(500)
                .dispatchThrottlingRateInByte(600)
                .ratePeriodInSecond(700)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, nsDispatchRate2);
        assertEquals(admin1.namespaces().getReplicatorDispatchRate(namespace), nsDispatchRate2);
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 20L);
        });

        //remove topic-level policy, namespace-level should take effect
        admin1.topics().removeReplicatorDispatchRate(topicName);
        Awaitility.await().untilAsserted(() -> {
            assertNull(admin1.topics().getReplicatorDispatchRate(topicName));
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 500);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 600L);
        });

        //remove namespace-level policy, broker-level should take effect
        admin1.namespaces().setReplicatorDispatchRate(namespace, null);
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 100);
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 200L);
        });
    }

    /**
     * verifies dispatch rate for replicators get changed once namespace policies changed.
     *
     * 1. verify default replicator not configured.
     * 2. change namespace setting of replicator dispatchRateMsg, verify topic changed.
     * 3. change namespace setting of replicator dispatchRateByte, verify topic changed.
     *
     * @throws Exception
     */
    @Test
    public void testReplicatorRateLimiterDynamicallyChange() throws Exception {
        log.info("--- Starting ReplicatorTest::{} --- ", methodName);

        final String namespace = "pulsar/replicatorchange-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/ratechange";

        admin1.namespaces().createNamespace(namespace);
        // 0. set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));

        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString()).statsInterval(0, TimeUnit.SECONDS)
            .build();

        Producer<byte[]> producer = client1.newProducer().topic(topicName)
            .enableBatching(false)
            .messageRoutingMode(MessageRoutingMode.SinglePartition)
            .create();
        producer.close();
        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        // 1. default replicator throttling not configured
        Assert.assertFalse(getRateLimiter(topic).isPresent());

        // 2. change namespace setting of replicator dispatchRateMsg, verify topic changed.
        int messageRate = 100;
        DispatchRate dispatchRateMsg = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(messageRate)
                .dispatchThrottlingRateInByte(-1)
                .ratePeriodInSecond(360)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, dispatchRateMsg);

        Awaitility.await().untilAsserted(()->{
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), 100);
        });

        // 3. change namespace setting of replicator dispatchRateByte, verify topic changed.
        messageRate = 500;
        DispatchRate dispatchRateByte = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(-1)
                .dispatchThrottlingRateInByte(messageRate)
                .ratePeriodInSecond(360)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, dispatchRateByte);
        assertEquals(admin1.namespaces().getReplicatorDispatchRate(namespace), dispatchRateByte);
        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), 500);
        });
    }

    /**
     * verifies dispatch rate for replicators works well for both Message limit and Byte limit .
     *
     * 1. verify topic replicator get configured.
     * 2. namespace setting of replicator dispatchRate, verify consumer in other cluster could not receive all messages.
     *
     * @throws Exception
     */
    @Test(dataProvider =  "dispatchRateType")
    public void testReplicatorRateLimiterMessageNotReceivedAllMessages(DispatchRateType dispatchRateType)
            throws Exception {
        log.info("--- Starting ReplicatorTest::{} --- ", methodName);

        final String namespace = "pulsar/replicatorbyteandmsg-" + dispatchRateType.toString() + "-"
                + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/notReceivedAll";

        admin1.namespaces().createNamespace(namespace);
        // 0. set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));

        final int messageRate = 100;
        DispatchRate dispatchRate;
        if (DispatchRateType.messageRate.equals(dispatchRateType)) {
            dispatchRate = DispatchRate.builder()
                    .dispatchThrottlingRateInMsg(messageRate)
                    .dispatchThrottlingRateInByte(-1)
                    .ratePeriodInSecond(360)
                    .build();
        } else {
            dispatchRate = DispatchRate.builder()
                    .dispatchThrottlingRateInMsg(-1)
                    .dispatchThrottlingRateInByte(messageRate)
                    .ratePeriodInSecond(360)
                    .build();
        }
        admin1.namespaces().setReplicatorDispatchRate(namespace, dispatchRate);

        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString()).statsInterval(0, TimeUnit.SECONDS)
            .build();

        Producer<byte[]> producer = client1.newProducer().topic(topicName)
            .enableBatching(false)
            .messageRoutingMode(MessageRoutingMode.SinglePartition)
            .create();

        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            if (DispatchRateType.messageRate.equals(dispatchRateType)) {
                assertEquals(rateLimiter.get().getDispatchRateOnMsg(), messageRate);
            } else {
                assertEquals(rateLimiter.get().getDispatchRateOnByte(), messageRate);
            }
        });

        @Cleanup
        PulsarClient client2 = PulsarClient.builder().serviceUrl(url2.toString()).statsInterval(0, TimeUnit.SECONDS)
            .build();
        final AtomicInteger totalReceived = new AtomicInteger(0);

        Consumer<byte[]> consumer = client2.newConsumer().topic(topicName)
                .subscriptionName("sub2-in-cluster2").messageListener((c1, msg) -> {
            Assert.assertNotNull(msg, "Message cannot be null");
            String receivedMessage = new String(msg.getData());
            log.debug("Received message [{}] in the listener", receivedMessage);
            totalReceived.incrementAndGet();
        }).subscribe();

        int numMessages = 500;
        // Asynchronously produce messages
        for (int i = 0; i < numMessages; i++) {
            producer.send(new byte[80]);
        }

        log.info("Received message number: [{}]", totalReceived.get());

        Assert.assertTrue(totalReceived.get() < messageRate * 2);

        consumer.close();
        producer.close();
    }

    /**
     * verifies dispatch rate for replicators works well for both Message limit.
     *
     * 1. verify topic replicator get configured.
     * 2. namespace setting of replicator dispatchRate,
     *      verify consumer in other cluster could receive all messages < message limit.
     * 3. verify consumer in other cluster could not receive all messages > message limit.
     *
     * @throws Exception
     */
    @Test
    public void testReplicatorRateLimiterMessageReceivedAllMessages() throws Exception {
        log.info("--- Starting ReplicatorTest::{} --- ", methodName);

        final String namespace = "pulsar/replicatormsg-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/notReceivedAll";

        admin1.namespaces().createNamespace(namespace);
        // 0. set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));

        final int messageRate = 100;
        DispatchRate dispatchRate = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(messageRate)
                .dispatchThrottlingRateInByte(-1)
                .ratePeriodInSecond(360)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, dispatchRate);

        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString()).statsInterval(0, TimeUnit.SECONDS)
            .build();

        Producer<byte[]> producer = client1.newProducer().topic(topicName)
            .enableBatching(false)
            .messageRoutingMode(MessageRoutingMode.SinglePartition)
            .create();

        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnMsg(), messageRate);
        });

        @Cleanup
        PulsarClient client2 = PulsarClient.builder().serviceUrl(url2.toString()).statsInterval(0, TimeUnit.SECONDS)
            .build();
        final AtomicInteger totalReceived = new AtomicInteger(0);

        Consumer<byte[]> consumer = client2.newConsumer().topic(topicName)
                .subscriptionName("sub2-in-cluster2").messageListener((c1, msg) -> {
            Assert.assertNotNull(msg, "Message cannot be null");
            String receivedMessage = new String(msg.getData());
            log.debug("Received message [{}] in the listener", receivedMessage);
            totalReceived.incrementAndGet();
        }).subscribe();

        int numMessages = 50;
        // Asynchronously produce messages
        for (int i = 0; i < numMessages; i++) {
            producer.send(new byte[80]);
        }

        Awaitility.await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(()->{
            log.info("Received message number: [{}]", totalReceived.get());

            Assert.assertEquals(totalReceived.get(), numMessages);
        });

        // Asynchronously produce messages
        for (int i = 0; i < 200; i++) {
            producer.send(new byte[80]);
        }
        Awaitility.await().pollDelay(1, TimeUnit.SECONDS).untilAsserted(() -> {
            log.info("Received message number: [{}]", totalReceived.get());

            Assert.assertEquals(totalReceived.get(), messageRate);
        });

        consumer.close();
        producer.close();
    }

    @Test
    public void testReplicatorRateLimiterByBytes() throws Exception {
        final String namespace = "pulsar/replicatormsg-" + System.currentTimeMillis();
        final String topicName = "persistent://" + namespace + "/RateLimiterByBytes";

        admin1.namespaces().createNamespace(namespace);
        // 0. set 2 clusters, there will be 1 replicator in each topic
        admin1.namespaces().setNamespaceReplicationClusters(namespace, Sets.newHashSet("r1", "r2"));

        final int byteRate = 400;
        final int payloadSize = 100;
        DispatchRate dispatchRate = DispatchRate.builder()
                .dispatchThrottlingRateInMsg(-1)
                .dispatchThrottlingRateInByte(byteRate)
                .ratePeriodInSecond(360)
                .build();
        admin1.namespaces().setReplicatorDispatchRate(namespace, dispatchRate);

        @Cleanup
        PulsarClient client1 = PulsarClient.builder().serviceUrl(url1.toString()).build();
        @Cleanup
        Producer<byte[]> producer = client1.newProducer().topic(topicName)
                .enableBatching(false)
                .messageRoutingMode(MessageRoutingMode.SinglePartition)
                .create();

        PersistentTopic topic = (PersistentTopic) pulsar1.getBrokerService().getOrCreateTopic(topicName).get();

        Awaitility.await().untilAsserted(() -> {
            Optional<DispatchRateLimiter> rateLimiter = getRateLimiter(topic);
            assertTrue(rateLimiter.isPresent());
            assertEquals(rateLimiter.get().getDispatchRateOnByte(), byteRate);
        });

        @Cleanup
        PulsarClient client2 = PulsarClient.builder().serviceUrl(url2.toString())
                .build();
        final AtomicInteger totalReceived = new AtomicInteger(0);

        @Cleanup
        Consumer<byte[]> ignored = client2.newConsumer().topic(topicName).subscriptionName("sub2-in-cluster2")
                .messageListener((c1, msg) -> {
                    Assert.assertNotNull(msg, "Message cannot be null");
                    String receivedMessage = new String(msg.getData());
                    log.debug("Received message [{}] in the listener", receivedMessage);
                    totalReceived.incrementAndGet();
                }).subscribe();

        // The total bytes is 5 times the rate limit value.
        int numMessages = byteRate / payloadSize * 5;
        for (int i = 0; i < numMessages * payloadSize; i++) {
            producer.send(new byte[payloadSize]);
        }

        Awaitility.await().pollDelay(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // The rate limit occurs in the next reading cycle, so a value fault tolerance needs to be added.
                    assertThat(totalReceived.get()).isLessThan((byteRate / payloadSize) + 2);
                });
    }

    private static Optional<DispatchRateLimiter> getRateLimiter(PersistentTopic topic) {
        return topic.getReplicators().values().stream().findFirst().map(Replicator::getRateLimiter).orElseThrow();
    }

    private static final Logger log = LoggerFactory.getLogger(ReplicatorRateLimiterTest.class);
}
