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
package org.apache.pulsar.common.policies.data;

import java.util.List;
import java.util.Map;

/**
 * Statistics about subscription.
 */
public interface SubscriptionStats {
    /** Total rate of messages delivered on this subscription (msg/s). */
    double getMsgRateOut();

    /** Total throughput delivered on this subscription (bytes/s). */
    double getMsgThroughputOut();

    /** Total bytes delivered to consumer (bytes). */
    long getBytesOutCounter();

    /** Total messages delivered to consumer (msg). */
    long getMsgOutCounter();

    /** Total rate of messages redelivered on this subscription (msg/s). */
    double getMsgRateRedeliver();

    /**
     * Total rate of message ack(msg/s).
     */
    double getMessageAckRate();

    /** Chunked message dispatch rate. */
    double getChunkedMessageRate();

    /** Number of entries in the subscription backlog. */
    long getMsgBacklog();

    /** Size of backlog in byte. **/
    long getBacklogSize();

    /** Get the publish time of the earliest message in the backlog. */
    long getEarliestMsgPublishTimeInBacklog();

    /** Number of entries in the subscription backlog that do not contain the delay messages. */
    long getMsgBacklogNoDelayed();

    /** Flag to verify if subscription is blocked due to reaching threshold of unacked messages. */
    boolean isBlockedSubscriptionOnUnackedMsgs();

    /** Number of delayed messages currently being tracked. */
    long getMsgDelayed();

    /** Number of messages registered for replay. */
    long getMsgInReplay();

    /**
     * Number of unacknowledged messages for the subscription, where an unacknowledged message is one that has been
     * sent to a consumer but not yet acknowledged. Calculated by summing all {@link ConsumerStats#getUnackedMessages()}
     * for this subscription. See {@link ConsumerStats#getUnackedMessages()} for additional details.
     */
    long getUnackedMessages();

    /** The subscription type as defined by {@link org.apache.pulsar.client.api.SubscriptionType}. */
    String getType();

    /** The name of the consumer that is active for single active consumer subscriptions i.e. failover or exclusive. */
    String getActiveConsumerName();

    /** Total rate of messages expired on this subscription (msg/s). */
    double getMsgRateExpired();

    /** Total messages expired on this subscription. */
    long getTotalMsgExpired();

    /** Last message expire execution timestamp. */
    long getLastExpireTimestamp();

    /** Last received consume flow command timestamp. */
    long getLastConsumedFlowTimestamp();

    /** Last consume message timestamp. */
    long getLastConsumedTimestamp();

    /** Last acked message timestamp. */
    long getLastAckedTimestamp();

    /** Last MarkDelete position advanced timesetamp. */
    long getLastMarkDeleteAdvancedTimestamp();

    /** List of connected consumers on this subscription w/ their stats. */
    List<? extends ConsumerStats> getConsumers();

    /** Tells whether this subscription is durable or ephemeral (eg.: from a reader). */
    boolean isDurable();

    /** Mark that the subscription state is kept in sync across different regions. */
    boolean isReplicated();

    /** Whether out of order delivery is allowed on the Key_Shared subscription. */
    boolean isAllowOutOfOrderDelivery();

    /** Whether the Key_Shared subscription mode is AUTO_SPLIT or STICKY. */
    String getKeySharedMode();

    /** This is for Key_Shared subscription to get the recentJoinedConsumers in the Key_Shared subscription. */
    Map<String, String> getConsumersAfterMarkDeletePosition();

    /**
     * For Key_Shared subscription in AUTO_SPLIT ordered mode:
     * Retrieves the current number of hashes in the draining state.
     *
     * @return the current number of hashes in the draining state
     */
    int getDrainingHashesCount();

    /**
     * For Key_Shared subscription in AUTO_SPLIT ordered mode:
     * Retrieves the total number of hashes cleared from the draining state for the connected consumers.
     *
     * @return the total number of hashes cleared from the draining state for the connected consumers
     */
    long getDrainingHashesClearedTotal();

    /**
     * For Key_Shared subscription in AUTO_SPLIT ordered mode:
     * Retrieves the total number of unacked messages for all draining hashes.
     *
     * @return the total number of unacked messages for all draining hashes
     */
    int getDrainingHashesUnackedMessages();

    /** SubscriptionProperties (key/value strings) associated with this subscribe. */
    Map<String, String> getSubscriptionProperties();

    /** The number of non-contiguous deleted messages ranges. */
    int getNonContiguousDeletedMessagesRanges();

    /** The serialized size of non-contiguous deleted messages ranges. */
    int getNonContiguousDeletedMessagesRangesSerializedSize();

    long getFilterProcessedMsgCount();

    long getFilterAcceptedMsgCount();

    long getFilterRejectedMsgCount();

    long getFilterRescheduledMsgCount();

    long getDelayedMessageIndexSizeInBytes();

    /**
     * Gets the total number of times message dispatching was throttled on a subscription
     * due to subscription rate limits.
     * @return the count of throttled message events by subscription limit, default is 0.
     */
    long getDispatchThrottledMsgEventsBySubscriptionLimit();

    /**
     * Gets the total number of times bytes dispatching was throttled on a subscription
     * due to subscription rate limits.
     * @return the count of throttled bytes by subscription limit, default is 0.
     */
    long getDispatchThrottledBytesEventsBySubscriptionLimit();

    /**
     * Gets the total number of times message dispatching was throttled on a subscription due to topic rate limits.
     * @return the count of throttled message events by topic limit, default is 0.
     */
    long getDispatchThrottledMsgEventsByTopicLimit();

    /**
     * Gets the total number of times bytes dispatching was throttled on a subscription due to topic rate limits.
     * @return the count of throttled bytes events by topic limit, default is 0.
     */
    long getDispatchThrottledBytesEventsByTopicLimit();

    /**
     * Gets the total number of times message dispatching was throttled on a subscription due to broker rate limits.
     * @return the count of throttled message events by broker limit, default is 0.
     */
    long getDispatchThrottledMsgEventsByBrokerLimit();

    /**
     * Gets the total number of times bytes dispatching was throttled on a subscription due to broker rate limits.
     * @return the count of throttled bytes count by broker limit, default is 0.
     */
    long getDispatchThrottledBytesEventsByBrokerLimit();
}
