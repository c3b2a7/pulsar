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
package org.apache.pulsar.broker;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.bookkeeper.client.api.DigestType;
import org.apache.bookkeeper.util.BookKeeperConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider;
import org.apache.pulsar.common.configuration.Category;
import org.apache.pulsar.common.configuration.FieldContext;
import org.apache.pulsar.common.configuration.PulsarConfiguration;
import org.apache.pulsar.common.nar.NarClassLoader;
import org.apache.pulsar.common.policies.data.BacklogQuota;
import org.apache.pulsar.common.policies.data.InactiveTopicDeleteMode;
import org.apache.pulsar.common.policies.data.OffloadedReadPriority;
import org.apache.pulsar.common.policies.data.SchemaCompatibilityStrategy;
import org.apache.pulsar.common.policies.data.TopicType;
import org.apache.pulsar.common.protocol.Commands;
import org.apache.pulsar.common.sasl.SaslConstants;
import org.apache.pulsar.common.topics.TopicsPattern;
import org.apache.pulsar.common.util.DefaultPulsarSslFactory;
import org.apache.pulsar.common.util.DirectMemoryUtils;
import org.apache.pulsar.metadata.api.MetadataStoreFactory;
import org.apache.pulsar.metadata.impl.ZKMetadataStore;

/**
 * Pulsar service configuration object.
 */
@Getter
@Setter
@ToString
public class ServiceConfiguration implements PulsarConfiguration {

    @Category
    private static final String CATEGORY_SERVER = "Server";
    @Category
    private static final String CATEGORY_PROTOCOLS = "Protocols";
    @Category
    private static final String CATEGORY_STORAGE_BK = "Storage (BookKeeper)";
    @Category
    private static final String CATEGORY_STORAGE_ML = "Storage (Managed Ledger)";
    @Category
    private static final String CATEGORY_STORAGE_OFFLOADING = "Storage (Ledger Offloading)";
    @Category
    private static final String CATEGORY_POLICIES = "Policies";
    @Category
    private static final String CATEGORY_WEBSOCKET = "WebSocket";
    @Category
    private static final String CATEGORY_SCHEMA = "Schema";
    @Category
    private static final String CATEGORY_METRICS = "Metrics";
    @Category
    private static final String CATEGORY_REPLICATION = "Replication";
    @Category
    private static final String CATEGORY_LOAD_BALANCER = "Load Balancer";
    @Category
    private static final String CATEGORY_FUNCTIONS = "Functions";
    @Category
    private static final String CATEGORY_TLS = "TLS";
    @Category
    private static final String CATEGORY_KEYSTORE_TLS = "KeyStoreTLS";
    @Category
    private static final String CATEGORY_AUTHENTICATION = "Authentication";
    @Category
    private static final String CATEGORY_AUTHORIZATION = "Authorization";
    @Category
    private static final String CATEGORY_TOKEN_AUTH = "Token Authentication Provider";
    @Category
    private static final String CATEGORY_SASL_AUTH = "SASL Authentication Provider";
    @Category
    private static final String CATEGORY_HTTP = "HTTP";
    @Category
    private static final String CATEGORY_TRANSACTION = "Transaction";
    @Category
    private static final String CATEGORY_PACKAGES_MANAGEMENT = "Packages Management";
    @Category
    private static final String CATEGORY_PLUGIN = "Broker Plugin";

    private static final double MIN_ML_CACHE_EVICTION_FREQUENCY = 0.001;
    private static final double MAX_ML_CACHE_EVICTION_FREQUENCY = 1000.0;
    private static final long MAX_ML_CACHE_EVICTION_INTERVAL_MS = 1000000L;

    /***** --- pulsar configuration. --- ****/
    @FieldContext(
        category = CATEGORY_SERVER,
        required = false,
        deprecated = true,
        doc = "The Zookeeper quorum connection string (as a comma-separated list). Deprecated in favour of "
                + "metadataStoreUrl"
    )
    @Getter(AccessLevel.NONE)
    @Deprecated
    private String zookeeperServers;

    @FieldContext(
            category = CATEGORY_SERVER,
            required = false,
            doc = "The metadata store URL. \n"
                    + " Examples: \n"
                    + "  * zk:my-zk-1:2181,my-zk-2:2181,my-zk-3:2181\n"
                    + "  * my-zk-1:2181,my-zk-2:2181,my-zk-3:2181 (will default to ZooKeeper when the schema is not "
                    + "specified)\n"
                    + "  * zk:my-zk-1:2181,my-zk-2:2181,my-zk-3:2181/my-chroot-path (to add a ZK chroot path)\n"
    )
    private String metadataStoreUrl;

    @FieldContext(
        category = CATEGORY_SERVER,
        required = false,
        deprecated = true,
        doc = "Global Zookeeper quorum connection string (as a comma-separated list)."
            + " Deprecated in favor of using `configurationStoreServers`"
    )
    @Getter(AccessLevel.NONE)
    @Deprecated
    private String globalZookeeperServers;

    @FieldContext(
        category = CATEGORY_SERVER,
        required = false,
        deprecated = true,
        doc = "Configuration store connection string (as a comma-separated list). Deprecated in favor of "
                + "`configurationMetadataStoreUrl`"
    )
    @Getter(AccessLevel.NONE)
    @Deprecated
    private String configurationStoreServers;

    @FieldContext(
            category = CATEGORY_SERVER,
            required = false,
            doc = "The metadata store URL for the configuration data. If empty, we fall back to use metadataStoreUrl"
    )
    private String configurationMetadataStoreUrl;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "The port for serving binary protobuf requests."
            + " If set, defines a server binding for bindAddress:brokerServicePort."
            + " The Default value is 6650."
    )

    private Optional<Integer> brokerServicePort = Optional.of(6650);
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "The port for serving TLS-secured binary protobuf requests."
            + " If set, defines a server binding for bindAddress:brokerServicePortTls."
    )
    private Optional<Integer> brokerServicePortTls = Optional.empty();
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "The port for serving http requests"
    )
    private Optional<Integer> webServicePort = Optional.of(8080);
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "The port for serving https requests"
    )
    private Optional<Integer> webServicePortTls = Optional.empty();

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Specify the TLS provider for the web service: SunJSSE, Conscrypt and etc."
    )
    private String webServiceTlsProvider = "Conscrypt";

    @FieldContext(
            category = CATEGORY_TLS,
            doc = "Specify the tls protocols the proxy's web service will use to negotiate during TLS Handshake.\n\n"
                    + "Example:- [TLSv1.3, TLSv1.2]"
    )
    private Set<String> webServiceTlsProtocols = new TreeSet<>();

    @FieldContext(
            category = CATEGORY_TLS,
            doc = "Specify the tls cipher the proxy's web service will use to negotiate during TLS Handshake.\n\n"
                    + "Example:- [TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256]"
    )
    private Set<String> webServiceTlsCiphers = new TreeSet<>();

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Hostname or IP address the service binds on"
    )
    private String bindAddress = "0.0.0.0";

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Hostname or IP address the service advertises to the outside world."
            + " If not set, the value of `InetAddress.getLocalHost().getCanonicalHostName()` is used."
    )
    private String advertisedAddress;

    @FieldContext(category = CATEGORY_SERVER,
            doc = "Used to specify multiple advertised listeners for the broker."
                    + " The value must format as <listener_name>:pulsar://<host>:<port>,"
                    + "multiple listeners should separate with commas."
                    + "Do not use this configuration with advertisedAddress and brokerServicePort."
                    + "The Default value is absent means use advertisedAddress and brokerServicePort.")
    private String advertisedListeners;

    @FieldContext(category = CATEGORY_SERVER,
            doc = "Used to specify the internal listener name for the broker."
                    + "The listener name must contain in the advertisedListeners."
                    + "The Default value is absent, the broker uses the first listener as the internal listener.")
    private String internalListenerName;

    @FieldContext(category = CATEGORY_SERVER,
            doc = "Used to specify additional bind addresses for the broker."
                    + " The value must format as <listener_name>:<scheme>://<host>:<port>,"
                    + " multiple bind addresses should be separated with commas."
                    + " Associates each bind address with an advertised listener and protocol handler."
                    + " Note that the brokerServicePort, brokerServicePortTls, webServicePort, and"
                    + " webServicePortTls properties define additional bindings.")
    private String bindAddresses;

    @FieldContext(category = CATEGORY_SERVER,
            doc = "Enable or disable the proxy protocol."
                    + " If true, the real IP addresses of consumers and producers can be obtained"
                    + " when getting topic statistics data.")
    private boolean haProxyProtocolEnabled;

    @FieldContext(category = CATEGORY_SERVER,
            doc = "Enable or disable the use of HA proxy protocol for resolving the client IP for http/https "
                    + "requests. Default is false.")
    private boolean webServiceHaProxyProtocolEnabled = false;

    @FieldContext(category = CATEGORY_SERVER, doc =
            "Trust X-Forwarded-For header for resolving the client IP for http/https requests.\n"
                    + "Default is false.")
    private boolean webServiceTrustXForwardedFor = false;

    @FieldContext(category = CATEGORY_SERVER, doc =
            "Add detailed client/remote and server/local addresses and ports to http/https request logging.\n"
                    + "Defaults to true when either webServiceHaProxyProtocolEnabled or webServiceTrustXForwardedFor "
                    + "is enabled.")
    private Boolean webServiceLogDetailedAddresses;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Number of threads to use for Netty Acceptor."
                    + " Default is set to `1`"
    )
    private int numAcceptorThreads = 1;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Number of threads to use for Netty IO."
            + " Default is set to `2 * Runtime.getRuntime().availableProcessors()`"
    )
    private int numIOThreads = 2 * Runtime.getRuntime().availableProcessors();

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Number of threads to use for orderedExecutor."
            + " The ordered executor is used to operate with zookeeper, such as init zookeeper client,"
            + " get namespace policies from zookeeper etc. It also used to split bundle. Default is 8"
    )
    private int numOrderedExecutorThreads = 8;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Number of threads to use for HTTP requests processing"
                + " Default is set to `2 * Runtime.getRuntime().availableProcessors()`"
        )
    // Use at least 8 threads to avoid having Jetty go into threads starving and
    // having the possibility of getting into a deadlock where a Jetty thread is
    // waiting for another HTTP call to complete in same thread.
    private int numHttpServerThreads = Math.max(8, 2 * Runtime.getRuntime().availableProcessors());

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Number of threads to use for pulsar broker service."
            + " The executor in thread pool will do basic broker operation like load/unload bundle,"
            + " update managedLedgerConfig, update topic/subscription/replicator message dispatch rate,"
            + " do leader election etc. Default is set to 20 "
    )
    private int numExecutorThreadPoolSize = Runtime.getRuntime().availableProcessors();

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Number of thread pool size to use for pulsar zookeeper callback service."
            + "The cache executor thread pool is used for restarting global zookeeper session. "
            + "Default is 10"
    )
    @Deprecated
    private int numCacheExecutorThreadPoolSize = 10;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Option to enable busy-wait settings. Default is false. "
                    + "WARNING: This option will enable spin-waiting on executors and IO threads in order "
                    + "to reduce latency during context switches. The spinning will consume 100% CPU even "
                    + "when the broker is not doing any work. It is recommended to reduce the number of IO threads "
                    + "and BK client threads to only have few CPU cores busy."
    )
    private boolean enableBusyWait = false;

    @FieldContext(category = CATEGORY_SERVER, doc = "Max concurrent web requests")
    private int maxConcurrentHttpRequests = 1024;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Capacity for thread pool queue in the HTTP server"
                    + " Default is set to 8192."
    )
    private int httpServerThreadPoolQueueSize = 8192;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Capacity for accept queue in the HTTP server"
                    + " Default is set to 8192."
    )
    private int httpServerAcceptQueueSize = 8192;

    @FieldContext(category = CATEGORY_SERVER, doc = "Maximum number of inbound http connections. "
            + "(0 to disable limiting)")
    private int maxHttpServerConnections = 2048;

    @FieldContext(category = CATEGORY_SERVER, doc =
            "Gzip compression is enabled by default. Specific paths can be excluded from compression.\n"
                    + "There are 2 syntaxes supported, Servlet url-pattern based, and Regex based.\n"
                    + "If the spec starts with '^' the spec is assumed to be a regex based path spec and will match "
                    + "with normal Java regex rules.\n"
                    + "If the spec starts with '/' then spec is assumed to be a Servlet url-pattern rules path spec "
                    + "for either an exact match or prefix based match.\n"
                    + "If the spec starts with '*.' then spec is assumed to be a Servlet url-pattern rules path spec "
                    + "for a suffix based match.\n"
                    + "All other syntaxes are unsupported.\n"
                    + "Disable all compression with ^.* or ^.*$")
    private List<String> httpServerGzipCompressionExcludedPaths = new ArrayList<>();

    @FieldContext(category = CATEGORY_SERVER, doc = "Whether to enable the delayed delivery for messages.")
    private boolean delayedDeliveryEnabled = true;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            Class name of the factory that implements the delayed deliver tracker.
            If value is "org.apache.pulsar.broker.delayed.BucketDelayedDeliveryTrackerFactory", \
            will create bucket based delayed message index tracker.
            """)
    private String delayedDeliveryTrackerFactoryClassName = "org.apache.pulsar.broker.delayed"
            + ".InMemoryDelayedDeliveryTrackerFactory";

    @FieldContext(category = CATEGORY_SERVER, doc = "Control the tick time for when retrying on delayed delivery, "
            + "affecting the accuracy of the delivery time compared to the scheduled time. Default is 1 second. "
            + "Note that this time is used to configure the HashedWheelTimer's tick time.")
    private long delayedDeliveryTickTimeMillis = 1000;

    @FieldContext(category = CATEGORY_SERVER, doc = "Whether the deliverAt time is strictly followed. "
            + "When false (default), messages may be sent to consumers before the deliverAt time by as much "
            + "as the tickTimeMillis. This can reduce the overhead on the broker of maintaining the delayed index "
            + "for a potentially very short time period. When true, messages will not be sent to consumer until the "
            + "deliverAt time has passed, and they may be as late as the deliverAt time plus the tickTimeMillis for "
            + "the topic plus the delayedDeliveryTickTimeMillis.")
    private boolean isDelayedDeliveryDeliverAtTimeStrict = false;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            The delayed message index bucket min index count. When the index count of the current bucket is more than \
            this value and all message indexes of current ledger have already been added to the tracker \
            we will seal the bucket.""")
    private long delayedDeliveryMinIndexCountPerBucket = 50000;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            The delayed message index time step(in seconds) in per bucket snapshot segment, \
            after reaching the max time step limitation, the snapshot segment will be cut off.""")
    private int delayedDeliveryMaxTimeStepPerBucketSnapshotSegmentSeconds = 300;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            The max number of delayed message index in per bucket snapshot segment, -1 means no limitation, \
            after reaching the max number limitation, the snapshot segment will be cut off.""")
    private int delayedDeliveryMaxIndexesPerBucketSnapshotSegment = 5000;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            The max number of delayed message index bucket, \
            after reaching the max buckets limitation, the adjacent buckets will be merged.\
            (disable with value -1)""")
    private int delayedDeliveryMaxNumBuckets = -1;

    @FieldContext(category = CATEGORY_SERVER, doc = "Size of the lookahead window to use "
            + "when detecting if all the messages in the topic have a fixed delay for "
            + "InMemoryDelayedDeliveryTracker (the default DelayedDeliverTracker). "
            + "Default is 50,000. Setting the lookahead window to 0 will disable the "
            + "logic to handle fixed delays in messages in a different way.")
    private long delayedDeliveryFixedDelayDetectionLookahead = 50_000;

    @FieldContext(category = CATEGORY_SERVER, doc = """
            The max allowed delay for delayed delivery (in milliseconds). If the broker receives a message which \
            exceeds this max delay, then it will return an error to the producer. \
            The default value is 0 which means there is no limit on the max delivery delay.""")
    private long delayedDeliveryMaxDelayInMillis = 0;

    @FieldContext(category = CATEGORY_SERVER, doc = "Whether to enable the acknowledge of batch local index")
    private boolean acknowledgmentAtBatchIndexLevelEnabled = true;

    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "Enable the WebSocket API service in broker"
    )
    private boolean webSocketServiceEnabled = false;

    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "Flag indicates whether to run broker in standalone mode"
    )
    private boolean isRunningStandalone = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        required = true,
        doc = "Name of the cluster to which this broker belongs to"
    )
    private String clusterName;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "The maximum number of tenants that each pulsar cluster can create."
                + "This configuration is not precise control, in a concurrent scenario, the threshold will be exceeded."
    )
    private int maxTenants = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Enable cluster's failure-domain which can distribute brokers into logical region"
    )
    private boolean failureDomainsEnabled = false;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Metadata store session timeout in milliseconds."
    )
    private long metadataStoreSessionTimeoutMillis = 30_000;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Metadata store operation timeout in seconds."
    )
    private int metadataStoreOperationTimeoutSeconds = 30;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Metadata store cache expiry time in seconds."
    )
    private int metadataStoreCacheExpirySeconds = 300;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Is metadata store read-only operations."
    )
    private boolean metadataStoreAllowReadOnlyOperations;

    @Deprecated
    @FieldContext(
        category = CATEGORY_SERVER,
        deprecated = true,
        doc = "ZooKeeper session timeout in milliseconds. "
                + "@deprecated - Use metadataStoreSessionTimeoutMillis instead."
    )
    private long zooKeeperSessionTimeoutMillis = -1;

    @Deprecated
    @FieldContext(
            category = CATEGORY_SERVER,
            deprecated = true,
            doc = "ZooKeeper operation timeout in seconds. "
                    + "@deprecated - Use metadataStoreOperationTimeoutSeconds instead."
        )
    private int zooKeeperOperationTimeoutSeconds = -1;

    @Deprecated
    @FieldContext(
            category = CATEGORY_SERVER,
            deprecated = true,
            doc = "ZooKeeper cache expiry time in seconds. "
                    + "@deprecated - Use metadataStoreCacheExpirySeconds instead."
        )
    private int zooKeeperCacheExpirySeconds = -1;

    @Deprecated
    @FieldContext(
            category = CATEGORY_SERVER,
            deprecated = true,
            doc = "Is zookeeper allow read-only operations."
    )
    private boolean zooKeeperAllowReadOnlyOperations;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Time to wait for broker graceful shutdown. After this time elapses, the process will be killed"
    )
    private long brokerShutdownTimeoutMs = 60000;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Flag to skip broker shutdown when broker handles Out of memory error"
    )
    private boolean skipBrokerShutdownOnOOM = false;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Amount of seconds to timeout when loading a topic. In situations with many geo-replicated clusters, "
                    + "this may need raised."
    )
    private long topicLoadTimeoutSeconds = 60;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Whether we should enable metadata operations batching"
    )
    private boolean metadataStoreBatchingEnabled = true;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Maximum delay to impose on batching grouping"
    )
    private int metadataStoreBatchingMaxDelayMillis = 5;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Maximum number of operations to include in a singular batch"
    )
    private int metadataStoreBatchingMaxOperations = 1_000;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Maximum size of a batch"
    )
    private int metadataStoreBatchingMaxSizeKb = 128;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Configuration file path for local metadata store."
    )
    private String metadataStoreConfigPath = null;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Configuration file path for configuration metadata store."
    )
    private String configurationStoreConfigPath = null;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_SERVER,
            doc = "Event topic to sync metadata between separate pulsar "
                    + "clusters on different cloud platforms."
    )
    private String metadataSyncEventTopic = null;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_SERVER,
            doc = "Event topic to sync configuration-metadata between separate pulsar "
                    + "clusters on different cloud platforms."
    )
    private String configurationMetadataSyncEventTopic = null;

    @FieldContext(
            dynamic = true,
            doc = "Factory class-name to create topic with custom workflow"
        )
    private String topicFactoryClassName;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Enable backlog quota check. Enforces actions on topic when the quota is reached"
    )
    private boolean backlogQuotaCheckEnabled = true;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Max capacity of the topic name cache. -1 means unlimited cache; 0 means broker will clear all cache"
                + " per maxSecondsToClearTopicNameCache, it does not mean broker will not cache TopicName."
    )
    private int topicNameCacheMaxCapacity = 100_000;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "A Specifies the minimum number of seconds that the topic name stays in memory, to avoid clear cache"
                + " frequently when there are too many topics are in use."
    )
    private int maxSecondsToClearTopicNameCache = 3600 * 2;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Whether to enable precise time based backlog quota check. "
                  + "Enabling precise time based backlog quota check will cause broker to read first entry in backlog "
                  + "of the slowest cursor on a ledger which will mostly result in reading entry from BookKeeper's "
                  + "disk which can have negative impact on overall performance. "
                  + "Disabling precise time based backlog quota check will just use the timestamp indicating when a "
                  + "ledger was closed, which is of coarser granularity."
    )
    private boolean preciseTimeBasedBacklogQuotaCheck = false;

    @FieldContext(
        category = CATEGORY_POLICIES,
        minValue = 1,
        doc = "How often to check for topics that have reached the quota."
            + " It only takes effects when `backlogQuotaCheckEnabled` is true"
    )
    private int backlogQuotaCheckIntervalInSeconds = 60;

    @Deprecated
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "@deprecated - Use backlogQuotaDefaultLimitBytes instead."
    )
    private double backlogQuotaDefaultLimitGB = -1;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Default per-topic backlog quota limit by size, less than 0 means no limitation. default is -1."
                + " Increase it if you want to allow larger msg backlog"
    )
    private long backlogQuotaDefaultLimitBytes = -1;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Default per-topic backlog quota limit by time in second, less than 0 means no limitation. "
                    + "default is -1. Increase it if you want to allow larger msg backlog"
    )
    private int backlogQuotaDefaultLimitSecond = -1;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Default backlog quota retention policy. Default is producer_request_hold\n\n"
            + "'producer_request_hold' Policy which holds producer's send request until the"
            + "resource becomes available (or holding times out)\n"
            + "'producer_exception' Policy which throws javax.jms.ResourceAllocationException to the producer\n"
            + "'consumer_backlog_eviction' Policy which evicts the oldest message from the slowest consumer's backlog"
    )
    private BacklogQuota.RetentionPolicy backlogQuotaDefaultRetentionPolicy = BacklogQuota.RetentionPolicy
            .producer_request_hold;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Default ttl for namespaces if ttl is not already configured at namespace policies. "
                    + "(disable default-ttl with value 0)"
        )
    private int ttlDurationDefaultInSeconds = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Additional system subscriptions that will be ignored by ttl check. "
                + "The cursor names are comma separated. Default is empty."
    )
    private Set<String> additionalSystemCursorNames = new TreeSet<>();

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Enable the deletion of inactive topics.\n"
        + "If only enable this option, will not clean the metadata of partitioned topic."
    )
    private boolean brokerDeleteInactiveTopicsEnabled = true;
    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Metadata of inactive partitioned topic will not be automatically cleaned up by default.\n"
            + "Note 1: If `allowAutoTopicCreation` and this option are enabled at the same time,\n"
            + "it may appear that a partitioned topic has just been deleted but is automatically created as a "
            + "non-partitioned topic.\n"
            + "Note 2: Activating bidirectional geo-replication under global ZooKeeper configuration may lead to schema"
            + " remnants and abnormal topic-level policies.\n"
            + "Note 3: Activating bidirectional geo-replication under global configuration ZooKeeper may lead"
            + " to a consumption issue."
    )
    private boolean brokerDeleteInactivePartitionedTopicMetadataEnabled = false;
    @FieldContext(
        category = CATEGORY_POLICIES,
        minValue = 1,
        dynamic = true,
        doc = "How often to check for inactive topics"
    )
    private int brokerDeleteInactiveTopicsFrequencySeconds = 60;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Set the inactive topic delete mode. Default is delete_when_no_subscriptions\n"
        + "'delete_when_no_subscriptions' mode only delete the topic which has no subscriptions and no active "
        + "producers\n"
        + "'delete_when_subscriptions_caught_up' mode only delete the topic that all subscriptions has no "
        + "backlogs(caught up) and no active producers/consumers"
    )
    private InactiveTopicDeleteMode brokerDeleteInactiveTopicsMode = InactiveTopicDeleteMode.
            delete_when_no_subscriptions;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Max duration of topic inactivity in seconds, default is not present\n"
        + "If not present, 'brokerDeleteInactiveTopicsFrequencySeconds' will be used\n"
        + "Topics that are inactive for longer than this value will be deleted"
    )
    private Integer brokerDeleteInactiveTopicsMaxInactiveDurationSeconds = null;

    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Allow forced deletion of tenants. Default is false."
    )
    private boolean forceDeleteTenantAllowed = false;

    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Allow forced deletion of namespaces. Default is false."
    )
    private boolean forceDeleteNamespaceAllowed = false;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Max pending publish requests per connection to avoid keeping large number of pending "
                + "requests in memory. Default: 1000"
    )
    private int maxPendingPublishRequestsPerConnection = 1000;

    @FieldContext(
        category = CATEGORY_POLICIES,
        minValue = 1,
        doc = "How frequently to proactively check and purge expired messages"
    )
    private int messageExpiryCheckIntervalInMinutes = 5;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "How long to delay rewinding cursor and dispatching messages when active consumer is changed"
    )
    private int activeConsumerFailoverDelayTimeMillis = 1000;
    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Enable consistent hashing for selecting the active consumer in partitioned "
                    + "topics with Failover subscription type."
                    + "For non-partitioned topics, consistent hashing is used by default."
    )
    private boolean activeConsumerFailoverConsistentHashing = false;
    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Maximum time to spend while scanning a subscription to calculate the accurate backlog"
    )
    private long subscriptionBacklogScanMaxTimeMs = 1000 * 60 * 2L;
    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Maximum number of entries to process while scanning a subscription to calculate the accurate backlog"
    )
    private long subscriptionBacklogScanMaxEntries = 10_000;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "How long to delete inactive subscriptions from last consuming."
            + " When it is 0, inactive subscriptions are not deleted automatically"
    )
    private int subscriptionExpirationTimeMinutes = 0;
    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Enable subscription message redelivery tracker to send redelivery "
                    + "count to consumer (default is enabled)"
        )
    private boolean subscriptionRedeliveryTrackerEnabled = true;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "How frequently to proactively check and purge expired subscription"
    )
    private int subscriptionExpiryCheckIntervalInMinutes = 5;

    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Enable subscription types (default is all type enabled)"
    )
    private Set<String> subscriptionTypesEnabled =
            Sets.newHashSet("Exclusive", "Shared", "Failover", "Key_Shared");

    @Deprecated
    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Enable Key_Shared subscription (default is enabled).\n"
            + "@deprecated - use subscriptionTypesEnabled instead."
    )
    private boolean subscriptionKeySharedEnable = true;

    @FieldContext(category = CATEGORY_POLICIES,
            doc = "On KeyShared subscriptions, with default AUTO_SPLIT mode, use splitting ranges or "
                    + "consistent hashing to reassign keys to new consumers (default is consistent hashing)")
    private boolean subscriptionKeySharedUseConsistentHashing = true;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "On KeyShared subscriptions, number of points in the consistent-hashing ring. "
                + "The higher the number, the more equal the assignment of keys to consumers")
    private int subscriptionKeySharedConsistentHashingReplicaPoints = 100;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "For persistent Key_Shared subscriptions, enables the use of the classic implementation of the "
                    + "Key_Shared subscription that was used before Pulsar 4.0.0 and PIP-379.",
            dynamic = true
    )
    private boolean subscriptionKeySharedUseClassicPersistentImplementation = false;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "For persistent Shared subscriptions, enables the use of the classic implementation of the Shared "
                    + "subscription that was used before Pulsar 4.0.0.",
            dynamic = true
    )
    private boolean subscriptionSharedUseClassicPersistentImplementation = false;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Set the default behavior for message deduplication in the broker.\n\n"
            + "This can be overridden per-namespace. If enabled, broker will reject"
            + " messages that were already stored in the topic"
    )
    private boolean brokerDeduplicationEnabled = false;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Maximum number of producer information that it's going to be persisted for deduplication purposes"
    )
    private int brokerDeduplicationMaxNumberOfProducers = 10000;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "How often is the thread pool scheduled to check whether a snapshot needs to be taken."
                + "(disable with value 0)"
    )
    private int brokerDeduplicationSnapshotFrequencyInSeconds = 120;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "If this time interval is exceeded, a snapshot will be taken."
            + "It will run simultaneously with `brokerDeduplicationEntriesInterval`"
    )
    private Integer brokerDeduplicationSnapshotIntervalSeconds = 120;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Number of entries after which a dedup info snapshot is taken.\n\n"
            + "A bigger interval will lead to less snapshots being taken though it would"
            + " increase the topic recovery time, when the entries published after the"
            + " snapshot need to be replayed"
    )
    private int brokerDeduplicationEntriesInterval = 1000;

    @FieldContext(
        category = CATEGORY_POLICIES,
        minValue = 1,
        doc = "Time of inactivity after which the broker will discard the deduplication information"
            + " relative to a disconnected producer. Default is 6 hours.")
    private int brokerDeduplicationProducerInactivityTimeoutMinutes = 360;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "When a namespace is created without specifying the number of bundle, this"
            + " value will be used as the default")
    private int defaultNumberOfNamespaceBundles = 4;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "The maximum number of namespaces that each tenant can create."
            + "This configuration is not precise control, in a concurrent scenario, the threshold will be exceeded")
    private int maxNamespacesPerTenant = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Max number of topics allowed to be created in the namespace. "
                + "When the topics reach the max topics of the namespace, the broker should reject "
                + "the new topic request(include topic auto-created by the producer or consumer) until "
                + "the number of connected consumers decrease. "
                + " Using a value of 0, is disabling maxTopicsPerNamespace-limit check."
    )
    private int maxTopicsPerNamespace = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "The maximum number of connections in the broker. If it exceeds, new connections are rejected."
    )
    private int brokerMaxConnections = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "The maximum number of connections per IP. If it exceeds, new connections are rejected."
    )
    private int brokerMaxConnectionsPerIp = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Allow schema to be auto updated at broker level. User can override this by 'is_allow_auto_update_schema'"
            + " of namespace policy. This is enabled by default."
    )
    private boolean isAllowAutoUpdateSchemaEnabled = true;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Enable check for minimum allowed client library version"
    )
    private boolean clientLibraryVersionCheckEnabled = false;
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Path for the file used to determine the rotation status for the broker"
            + " when responding to service discovery health checks")
    private String statusFilePath;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Max number of unacknowledged messages allowed to receive messages by a consumer on"
            + " a shared subscription.\n\n Broker will stop sending messages to consumer once,"
            + " this limit reaches until consumer starts acknowledging messages back and unack count"
            + " reaches to `maxUnackedMessagesPerConsumer/2`. Using a value of 0, it is disabling "
            + " unackedMessage-limit check and consumer can receive messages without any restriction")
    private int maxUnackedMessagesPerConsumer = 50000;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Max number of unacknowledged messages allowed per shared subscription. \n\n"
            + " Broker will stop dispatching messages to all consumers of the subscription once this "
            + " limit reaches until consumer starts acknowledging messages back and unack count reaches"
            + " to `limit/2`. Using a value of 0, is disabling unackedMessage-limit check and dispatcher"
            + " can dispatch messages without any restriction")
    private int maxUnackedMessagesPerSubscription = 4 * 50000;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Max number of unacknowledged messages allowed per broker. \n\n"
            + " Once this limit reaches, broker will stop dispatching messages to all shared subscription "
            + " which has higher number of unack messages until subscriptions start acknowledging messages "
            + " back and unack count reaches to `limit/2`. Using a value of 0, is disabling unackedMessage-limit"
            + " check and broker doesn't block dispatchers")
    private int maxUnackedMessagesPerBroker = 0;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "For Key_Shared subscriptions, if messages cannot be dispatched to consumers due to a slow consumer"
                    + " or a blocked key hash (because of ordering constraints), the broker will continue reading more"
                    + " messages from the backlog and attempt to dispatch them to consumers until the number of replay"
                    + " messages reaches the calculated threshold.\n"
                    + "Formula: threshold = min(keySharedLookAheadMsgInReplayThresholdPerConsumer *"
                    + " connected consumer count, keySharedLookAheadMsgInReplayThresholdPerSubscription)"
                    + ".\n"
                    + "Setting this value to 0 will disable the limit calculated per consumer.",
            dynamic = true
    )
    private int keySharedLookAheadMsgInReplayThresholdPerConsumer = 2000;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "For Key_Shared subscriptions, if messages cannot be dispatched to consumers due to a slow consumer"
                    + " or a blocked key hash (because of ordering constraints), the broker will continue reading more"
                    + " messages from the backlog and attempt to dispatch them to consumers until the number of replay"
                    + " messages reaches the calculated threshold.\n"
                    + "Formula: threshold = min(keySharedLookAheadMsgInReplayThresholdPerConsumer *"
                    + " connected consumer count, keySharedLookAheadMsgInReplayThresholdPerSubscription)"
                    + ".\n"
                    + "This value should be set to a value less than 2 * managedLedgerMaxUnackedRangesToPersist.\n"
                    + "Setting this value to 0 will disable the limit calculated per subscription.\n",
            dynamic = true
    )
    private int keySharedLookAheadMsgInReplayThresholdPerSubscription = 20000;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "For Key_Shared subscriptions, when a blocked key hash gets unblocked,"
                    + " a redelivery will be attempted after a delay. This setting controls the delay."
                    + " The reason to have the delay is to batch multiple unblocking events instead of triggering"
                    + " redelivery for each unblocking event.",
            dynamic = true
    )
    private long keySharedUnblockingIntervalMs = 10L;

    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Once broker reaches maxUnackedMessagesPerBroker limit, it blocks subscriptions which has higher "
            + " unacked messages than this percentage limit and subscription will not receive any new messages "
            + " until that subscription acks back `limit/2` messages")
    private double maxUnackedMessagesPerSubscriptionOnBrokerBlocked = 0.16;
    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Maximum size of Consumer metadata")
    private int maxConsumerMetadataSize = 1024;
    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Broker periodically checks if subscription is stuck and unblock if flag is enabled. "
                    + "(Default is disabled)"
        )
    private boolean unblockStuckSubscriptionEnabled = false;
    @FieldContext(
            category = CATEGORY_POLICIES,
            dynamic = true,
            doc = "Tick time to schedule task that checks topic publish rate limiting across all topics  "
                    + "Reducing to lower value can give more accuracy while throttling publish but "
                    + "it uses more CPU to perform frequent check. (Disable publish throttling with value 0)"
        )
    private int topicPublisherThrottlingTickTimeMillis = 10;

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Tick time to schedule task that checks broker publish rate limiting across all topics  "
            + "Reducing to lower value can give more accuracy while throttling publish but "
            + "it uses more CPU to perform frequent check. (Disable publish throttling with value 0)"
    )
    private int brokerPublisherThrottlingTickTimeMillis = 50;
    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Max Rate(in 1 seconds) of Message allowed to publish for a broker "
            + "when broker publish rate limiting enabled. (Disable message rate limit with value 0)"
    )
    private int brokerPublisherThrottlingMaxMessageRate = 0;
    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Max Rate(in 1 seconds) of Byte allowed to publish for a broker "
            + "when broker publish rate limiting enabled. (Disable byte rate limit with value 0)"
    )
    private long brokerPublisherThrottlingMaxByteRate = 0;

    @FieldContext(category = CATEGORY_SERVER, doc =
            "The class name of the factory that creates DispatchRateLimiter implementations. Current options are "
                    + "org.apache.pulsar.broker.service.persistent.DispatchRateLimiterFactoryAsyncTokenBucket "
                    + "(default, PIP-322 implementation) "
                    + "org.apache.pulsar.broker.service.persistent.DispatchRateLimiterFactoryClassic (legacy "
                    + "implementation)")
    private String dispatchRateLimiterFactoryClassName =
            "org.apache.pulsar.broker.service.persistent.DispatchRateLimiterFactoryAsyncTokenBucket";

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "Default messages per second dispatch throttling-limit for whole broker. "
                    + "Using a value of 0, is disabling default message-byte dispatch-throttling"
    )
    private int dispatchThrottlingRateInMsg = 0;
    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "Default bytes per second dispatch throttling-limit for whole broker. "
                    + "Using a value of 0, is disabling default message-byte dispatch-throttling"
    )
    private long dispatchThrottlingRateInByte = 0;
    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Max Rate(in 1 seconds) of Message allowed to publish for a topic "
            + "when topic publish rate limiting enabled. (Disable byte rate limit with value 0)"
    )
    private int maxPublishRatePerTopicInMessages = 0;
    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Max Rate(in 1 seconds) of Byte allowed to publish for a topic "
            + "when topic publish rate limiting enabled. (Disable byte rate limit with value 0)"
    )
    private long maxPublishRatePerTopicInBytes = 0;

    @FieldContext(
        category = CATEGORY_POLICIES,
        dynamic = true,
        doc = "Too many subscribe requests from a consumer can cause broker rewinding consumer cursors "
            + " and loading data from bookies, hence causing high network bandwidth usage When the positive"
            + " value is set, broker will throttle the subscribe requests for one consumer. Otherwise, the"
            + " throttling will be disabled. The default value of this setting is 0 - throttling is disabled.")
    private int subscribeThrottlingRatePerConsumer = 0;
    @FieldContext(
        minValue = 1,
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Rate period for {subscribeThrottlingRatePerConsumer}. Default is 30s."
    )
    private int subscribeRatePeriodPerConsumerInSecond = 30;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message dispatching throttling-limit for every topic. \n\n"
            + "Using a value of 0, is disabling default message dispatch-throttling")
    private int dispatchThrottlingRatePerTopicInMsg = 0;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message-bytes dispatching throttling-limit for every topic. \n\n"
            + "Using a value of 0, is disabling default message-byte dispatch-throttling")
    private long dispatchThrottlingRatePerTopicInByte = 0;
    @FieldContext(
            dynamic = true,
            category = CATEGORY_POLICIES,
            doc = "Apply dispatch rate limiting on batch message instead individual "
                    + "messages with in batch message. (Default is disabled)")
        private boolean dispatchThrottlingOnBatchMessageEnabled = false;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message dispatching throttling-limit for a subscription. \n\n"
            + "Using a value of 0, is disabling default message dispatch-throttling.")
    private int dispatchThrottlingRatePerSubscriptionInMsg = 0;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message-bytes dispatching throttling-limit for a subscription. \n\n"
            + "Using a value of 0, is disabling default message-byte dispatch-throttling.")
    private long dispatchThrottlingRatePerSubscriptionInByte = 0;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message dispatching throttling-limit for every replicator in replication. \n\n"
            + "Using a value of 0, is disabling replication message dispatch-throttling")
    private int dispatchThrottlingRatePerReplicatorInMsg = 0;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default number of message-bytes dispatching throttling-limit for every replicator in replication. \n\n"
            + "Using a value of 0, is disabling replication message-byte dispatch-throttling")
    private long dispatchThrottlingRatePerReplicatorInByte = 0;
    @FieldContext(
            dynamic = true,
            category = CATEGORY_POLICIES,
            doc = "Dispatch rate-limiting relative to publish rate. (Enabling flag will make broker to dynamically "
                    + "update dispatch-rate relatively to publish-rate: "
                    + "throttle-dispatch-rate = (publish-rate + configured dispatch-rate) ")
    private boolean dispatchThrottlingRateRelativeToPublishRate = false;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_POLICIES,
        doc = "Default dispatch-throttling is disabled for consumers which already caught-up with"
            + " published messages and don't have backlog. This enables dispatch-throttling for "
            + " non-backlog consumers as well.")
    private boolean dispatchThrottlingOnNonBacklogConsumerEnabled = true;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "Default policy for publishing usage reports to system topic is disabled."
            + "This enables publishing of usage reports"
    )
    private String resourceUsageTransportClassName = "";

    @FieldContext(
            dynamic = true,
            category = CATEGORY_POLICIES,
            doc = "Default interval to publish usage reports if resourceUsagePublishToTopic is enabled."
    )
    private int resourceUsageTransportPublishIntervalInSecs = 60;

    @FieldContext(
            dynamic = false,
            category = CATEGORY_POLICIES,
            doc = "Enables evaluating subscription pattern on broker side."
    )
    private boolean enableBrokerSideSubscriptionPatternEvaluation = true;

    @FieldContext(
            dynamic = false,
            category = CATEGORY_POLICIES,
            doc = "Max length of subscription pattern"
    )
    private int subscriptionPatternMaxLength = 50;

    // <-- dispatcher read settings -->
    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Max number of entries to read from bookkeeper. By default it is 100 entries."
    )
    private int dispatcherMaxReadBatchSize = 100;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_SERVER,
            doc = "Dispatch messages and execute broker side filters in a per-subscription thread"
    )
    private boolean dispatcherDispatchMessagesInSubscriptionThread = true;

    @FieldContext(
        dynamic = false,
        category = CATEGORY_SERVER,
        doc = "Whether the broker should count filtered entries in dispatch rate limit calculations. When disabled, "
            + "only messages sent to a consumer count towards a dispatch rate limit at the broker, topic, and "
            + "subscription level. When enabled, messages filtered out due to entry filter logic are counted towards "
            + "each relevant rate limit."
    )
    private boolean dispatchThrottlingForFilteredEntriesEnabled = false;

    // <-- dispatcher read settings -->
    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Max size in bytes of entries to read from bookkeeper. By default it is 5MB."
    )
    private int dispatcherMaxReadSizeBytes = 5 * 1024 * 1024;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Min number of entries to read from bookkeeper. By default it is 1 entries."
            + "When there is an error occurred on reading entries from bookkeeper, the broker"
            + " will backoff the batch size to this minimum number."
    )
    private int dispatcherMinReadBatchSize = 1;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "The read failure backoff initial time in milliseconds. By default it is 15s."
    )
    private int dispatcherReadFailureBackoffInitialTimeInMs = 15000;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "The read failure backoff max time in milliseconds. By default it is 60s."
    )
    private int dispatcherReadFailureBackoffMaxTimeInMs = 60000;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "The read failure backoff mandatory stop time in milliseconds. By default it is 0s."
    )
    private int dispatcherReadFailureBackoffMandatoryStopTimeInMs = 0;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "On Shared and KeyShared subscriptions, if all available messages in the subscription are filtered "
                    + "out and not dispatched to any consumer, message dispatching will be rescheduled with a backoff "
                    + "delay. This parameter sets the initial backoff delay in milliseconds.")
    private int dispatcherRetryBackoffInitialTimeInMs = 1;

    @FieldContext(
            category = CATEGORY_POLICIES,
            doc = "On Shared and KeyShared subscriptions, if all available messages in the subscription are filtered "
                    + "out and not dispatched to any consumer, message dispatching will be rescheduled with a backoff "
                    + "delay. This parameter sets the maximum backoff delay in milliseconds.")
    private int dispatcherRetryBackoffMaxTimeInMs = 10;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_SERVER,
            doc = "Time in milliseconds to delay the new delivery of a message when an EntryFilter returns RESCHEDULE."
    )
    private int dispatcherEntryFilterRescheduledMessageDelay = 1000;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Max number of entries to dispatch for a shared subscription. By default it is 20 entries."
    )
    private int dispatcherMaxRoundRobinBatchSize = 20;

    @FieldContext(
         dynamic = true,
         category = CATEGORY_SERVER,
         doc = "Precise dispatcher flow control according to history message number of each entry"
    )
    private boolean preciseDispatcherFlowControl = false;

    @FieldContext(
         dynamic = true,
         category = CATEGORY_SERVER,
         doc = " Class name of pluggable entry filter that decides whether the entry needs to be filtered."
                 + "You can use this class to decide which entries can be sent to consumers."
                 + "Multiple names need to be separated by commas."
    )
    private List<String> entryFilterNames = new ArrayList<>();

    @FieldContext(
         dynamic = true,
         category = CATEGORY_SERVER,
         doc = " The directory for all the entry filter implementations."
    )
    private String entryFiltersDirectory = "";

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "Whether allow topic level entry filters policies overrides broker configuration."
    )
    private boolean allowOverrideEntryFilters = false;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Max number of concurrent lookup request broker allows to throttle heavy incoming lookup traffic")
    private int maxConcurrentLookupRequest = 50000;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "Max number of concurrent topic loading request broker allows to control number of zk-operations"
    )
    private int maxConcurrentTopicLoadRequest = 5000;
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max concurrent non-persistent message can be processed per connection")
    private int maxConcurrentNonPersistentMessagePerConnection = 1000;

    @Deprecated
    @FieldContext(
        category = CATEGORY_SERVER,
        deprecated = true,
        doc = "Number of worker threads to serve non-persistent topic.\n"
                + "@deprecated - use topicOrderedExecutorThreadNum instead.")
    private int numWorkerThreadsForNonPersistentTopic = -1;
    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Number of worker threads to serve topic ordered executor")
    private int topicOrderedExecutorThreadNum = Runtime.getRuntime().availableProcessors();

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enable broker to load persistent topics"
    )
    private boolean enablePersistentTopics = true;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enable broker to load non-persistent topics"
    )
    private boolean enableNonPersistentTopics = true;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enable to run bookie along with broker"
    )
    private boolean enableRunBookieTogether = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enable to run bookie autorecovery along with broker"
    )
    private boolean enableRunBookieAutoRecoveryTogether = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of producers allowed to connect to topic. \n\nOnce this limit reaches,"
            + " Broker will reject new producers until the number of connected producers decrease."
            + " Using a value of 0, is disabling maxProducersPerTopic-limit check.")
    private int maxProducersPerTopic = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of producers with the same IP address allowed to connect to topic."
            + " \n\nOnce this limit reaches, Broker will reject new producers until the number of"
            + " connected producers with the same IP address decrease."
            + " Using a value of 0, is disabling maxSameAddressProducersPerTopic-limit check.")
    private int maxSameAddressProducersPerTopic = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enforce producer to publish encrypted messages.(default disable).")
    private boolean encryptionRequireOnProducer = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of consumers allowed to connect to topic. \n\nOnce this limit reaches,"
            + " Broker will reject new consumers until the number of connected consumers decrease."
            + " Using a value of 0, is disabling maxConsumersPerTopic-limit check.")
    private int maxConsumersPerTopic = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of consumers with the same IP address allowed to connect to topic."
            + " \n\nOnce this limit reaches, Broker will reject new consumers until the number of"
            + " connected consumers with the same IP address decrease."
            + " Using a value of 0, is disabling maxSameAddressConsumersPerTopic-limit check.")
    private int maxSameAddressConsumersPerTopic = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of subscriptions allowed to subscribe to topic. \n\nOnce this limit reaches, "
                + " broker will reject new subscription until the number of subscribed subscriptions decrease.\n"
                + " Using a value of 0, is disabling maxSubscriptionsPerTopic limit check."
    )
    private int maxSubscriptionsPerTopic = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max number of consumers allowed to connect to subscription. \n\nOnce this limit reaches,"
            + " Broker will reject new consumers until the number of connected consumers decrease."
            + " Using a value of 0, is disabling maxConsumersPerSubscription-limit check.")
    private int maxConsumersPerSubscription = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Max size of messages.",
        maxValue = Integer.MAX_VALUE - Commands.MESSAGE_SIZE_FRAME_PADDING)
    private int maxMessageSize = Commands.DEFAULT_MAX_MESSAGE_SIZE;

    @FieldContext(
            category = CATEGORY_SERVER,
        doc = "Enable tracking of replicated subscriptions state across clusters.")
    private boolean enableReplicatedSubscriptions = true;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Frequency of snapshots for replicated subscriptions tracking.")
    private int replicatedSubscriptionsSnapshotFrequencyMillis = 1_000;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Timeout for building a consistent snapshot for tracking replicated subscriptions state. ")
    private int replicatedSubscriptionsSnapshotTimeoutSeconds = 30;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Max number of snapshot to be cached per subscription.")
    private int replicatedSubscriptionsSnapshotMaxCachedPerSubscription = 10;

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "The position that replication task start at, it can be set to earliest or latest (default).")
    private String replicationStartAt = "latest";

    @FieldContext(
        category = CATEGORY_SERVER,
        dynamic = true,
        doc = "Max memory size for broker handling messages sending from producers.\n\n"
            + " If the processing message size exceed this value, broker will stop read data"
            + " from the connection. The processing messages means messages are sends to broker"
            + " but broker have not send response to client, usually waiting to write to bookies.\n\n"
            + " It's shared across all the topics running in the same broker.\n\n"
            + " Use -1 to disable the memory limitation. Default is 1/2 of direct memory.\n\n")
    private int maxMessagePublishBufferSizeInMB = Math.max(64,
        (int) (DirectMemoryUtils.jvmMaxDirectMemory() / 2 / (1024 * 1024)));

    @FieldContext(category = CATEGORY_SERVER, doc = "Whether to recover cursors lazily when trying to recover a "
            + "managed ledger backing a persistent topic. It can improve write availability of topics.\n"
            + "The caveat is now when recovered ledger is ready to write we're not sure if all old consumers last mark "
            + "delete position can be recovered or not.")
    private boolean lazyCursorRecovery = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Check between intervals to see if consumed ledgers need to be trimmed"
    )
    private int retentionCheckIntervalInSeconds = 120;

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "The number of partitions per partitioned topic.\n"
                + "If try to create or update partitioned topics by exceeded number of partitions, then fail.\n"
                + "Use 0 or negative number to disable the check."
    )
    private int maxNumPartitionsPerPartitionedTopic = 0;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "The directory to locate broker interceptors"
    )
    private String brokerInterceptorsDirectory = "./interceptors";

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "List of broker interceptor to load, which is a list of broker interceptor names"
    )
    private Set<String> brokerInterceptors = new TreeSet<>();

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "List of interceptors for payload processing.")
    private Set<String> brokerEntryPayloadProcessors = new LinkedHashSet<>();

    @FieldContext(
        doc = "There are two policies to apply when broker metadata session expires: session expired happens, "
        + "\"shutdown\" or \"reconnect\". \n\n"
        + " With \"shutdown\", the broker will be restarted.\n\n"
        + " With \"reconnect\", the broker will keep serving the topics, while attempting to recreate a new session."
    )
    private MetadataSessionExpiredPolicy zookeeperSessionExpiredPolicy = MetadataSessionExpiredPolicy.reconnect;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "If a topic remains fenced for this number of seconds, it will be closed forcefully.\n"
                + " If it is set to 0 or a negative number, the fenced topic will not be closed."
    )
    private int topicFencingTimeoutSeconds = 0;

    /**** --- Messaging Protocol. --- ****/

    @FieldContext(
        category = CATEGORY_PROTOCOLS,
        doc = "The directory to locate messaging protocol handlers"
    )
    private String protocolHandlerDirectory = "./protocols";

    @FieldContext(
            category = CATEGORY_PROTOCOLS,
            doc = "Use a separate ThreadPool for each Protocol Handler"
    )
    private boolean useSeparateThreadPoolForProtocolHandlers = true;

    @FieldContext(
        category = CATEGORY_PROTOCOLS,
        doc = "List of messaging protocols to load, which is a list of protocol names"
    )
    private Set<String> messagingProtocols = new TreeSet<>();

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Enable or disable system topic.")
    private boolean systemTopicEnabled = true;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "# Enable strict topic name check. Which includes two parts as follows:\n"
                    + "# 1. Mark `-partition-` as a keyword.\n"
                    + "# E.g.\n"
                    + "    Create a non-partitioned topic.\n"
                    + "      No corresponding partitioned topic\n"
                    + "       - persistent://public/default/local-name (passed)\n"
                    + "       - persistent://public/default/local-name-partition-z (rejected by keyword)\n"
                    + "       - persistent://public/default/local-name-partition-0 (rejected by keyword)\n"
                    + "      Has corresponding partitioned topic, partitions=2 and topic partition name "
                    + "is persistent://public/default/local-name\n"
                    + "       - persistent://public/default/local-name-partition-0 (passed,"
                    + " Because it is the partition topic's sub-partition)\n"
                    + "       - persistent://public/default/local-name-partition-z (rejected by keyword)\n"
                    + "       - persistent://public/default/local-name-partition-4 (rejected,"
                    + " Because it exceeds the number of maximum partitions)\n"
                    + "    Create a partitioned topic(topic metadata)\n"
                    + "       - persistent://public/default/local-name (passed)\n"
                    + "       - persistent://public/default/local-name-partition-z (rejected by keyword)\n"
                    + "       - persistent://public/default/local-name-partition-0 (rejected by keyword)\n"
                    + "# 2. Allowed alphanumeric (a-zA-Z_0-9) and these special chars -=:. for topic name.\n"
                    + "# NOTE: This flag will be removed in some major releases in the future.\n")
    private boolean strictTopicNameEnabled = false;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "The regular expression implementation to use for topic pattern matching. \n"
                    + "RE2J_WITH_JDK_FALLBACK is the default. It uses the RE2J implementation and falls back to "
                    + "the JDK implementation for backwards compatibility reasons when the pattern compilation fails "
                    + "with the RE2/j library.\n"
                    + "RE2J is more performant but does not support all regex features (e.g. negative lookaheads). \n"
                    + "JDK uses the standard Java regex implementation which supports all features but can be slower.\n"
                    + "Bad or malicious regex patterns requiring extensive backtracing could cause high resource usage "
                    + "with RE2J_WITH_JDK_FALLBACK or JDK implementations."
    )
    private TopicsPattern.RegexImplementation topicsPatternRegexImplementation =
            TopicsPattern.RegexImplementation.RE2J_WITH_JDK_FALLBACK;

    @FieldContext(
            category = CATEGORY_SCHEMA,
            doc = "The schema compatibility strategy to use for system topics"
    )
    private SchemaCompatibilityStrategy systemTopicSchemaCompatibilityStrategy =
            SchemaCompatibilityStrategy.ALWAYS_COMPATIBLE;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Enable or disable topic level policies, topic level policies depends on the system topic, "
                + "please enable the system topic first.")
    private boolean topicLevelPoliciesEnabled = true;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "The class name of the topic policies service. The default config only takes affect when the "
                    + "systemTopicEnable config is true"
    )
    private String topicPoliciesServiceClassName =
            "org.apache.pulsar.broker.service.SystemTopicBasedTopicPoliciesService";

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "List of interceptors for entry metadata.")
    private Set<String> brokerEntryMetadataInterceptors = new HashSet<>();

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Enable or disable exposing broker entry metadata to client.")
    private boolean exposingBrokerEntryMetadataToClientEnabled = false;

    @Deprecated
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "This config never takes effect and will be removed in the next release"
    )
    private boolean enableNamespaceIsolationUpdateOnTime = false;

    @FieldContext(category = CATEGORY_SERVER, doc = "Enable or disable strict bookie affinity.")
    private boolean strictBookieAffinityEnabled = false;

    /***** --- TLS. --- ****/
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Enable TLS"
    )
    @Deprecated
    private boolean tlsEnabled = false;
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Tls cert refresh duration in seconds (set 0 to check on every new connection)"
    )
    private long tlsCertRefreshCheckDurationSec = 300;
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Path for the TLS certificate file"
    )
    private String tlsCertificateFilePath;
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Path for the TLS private key file"
    )
    private String tlsKeyFilePath;
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Path for the trusted TLS certificate file"
    )
    private String tlsTrustCertsFilePath = "";
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Accept untrusted TLS certificate from client"
    )
    private boolean tlsAllowInsecureConnection = false;
    @FieldContext(
            category = CATEGORY_TLS,
            doc = "Whether the hostname is validated when the broker creates a TLS connection with other brokers"
    )
    private boolean tlsHostnameVerificationEnabled = false;
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Specify the tls protocols the broker will use to negotiate during TLS Handshake.\n\n"
            + "Example:- [TLSv1.3, TLSv1.2]"
    )
    private Set<String> tlsProtocols = new TreeSet<>();
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Specify the tls cipher the broker will use to negotiate during TLS Handshake.\n\n"
            + "Example:- [TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256]"
    )
    private Set<String> tlsCiphers = new TreeSet<>();
    @FieldContext(
        category = CATEGORY_TLS,
        doc = "Specify whether Client certificates are required for TLS Reject.\n"
            + "the Connection if the Client Certificate is not trusted")
    private boolean tlsRequireTrustedClientCertOnConnect = false;
    @FieldContext(
            category = CATEGORY_TLS,
            doc = "SSL Factory Plugin class to provide SSLEngine and SSLContext objects. The default "
                    + " class used is DefaultSslFactory.")
    private String sslFactoryPlugin = DefaultPulsarSslFactory.class.getName();
    @FieldContext(
            category = CATEGORY_TLS,
            doc = "SSL Factory plugin configuration parameters.")
    private String sslFactoryPluginParams = "";

    /***** --- Authentication. --- ****/
    @FieldContext(
        category = CATEGORY_AUTHENTICATION,
        doc = "Enable authentication"
    )
    private boolean authenticationEnabled = false;
    @FieldContext(
        category = CATEGORY_AUTHENTICATION,
        doc = "Authentication provider name list, which is a list of class names"
    )
    private Set<String> authenticationProviders = new TreeSet<>();

    @FieldContext(
        category = CATEGORY_AUTHENTICATION,
        doc = "Interval of time for checking for expired authentication credentials"
    )
    private int authenticationRefreshCheckSeconds = 60;

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "Enforce authorization"
    )
    private boolean authorizationEnabled = false;
    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "Authorization provider fully qualified class-name"
    )
    private String authorizationProvider = PulsarAuthorizationProvider.class.getName();

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        dynamic = true,
        doc = "Role names that are treated as `super-user`, meaning they will be able to"
            + " do all admin operations and publish/consume from all topics"
    )
    private Set<String> superUserRoles = new TreeSet<>();

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "Role names that are treated as `proxy roles`. \n\nIf the broker sees"
            + " a request with role as proxyRoles - it will demand to see the original"
            + " client role or certificate.")
    private Set<String> proxyRoles = new TreeSet<>();

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "If this flag is set then the broker authenticates the original Auth data"
            + " else it just accepts the originalPrincipal and authorizes it (if required)")
    private boolean authenticateOriginalAuthData = false;

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "Allow wildcard matching in authorization\n\n"
            + "(wildcard matching only applicable if wildcard-char: * presents at first"
            + " or last position eg: *.pulsar.service, pulsar.service.*)")
    private boolean authorizationAllowWildcardsMatching = false;

    @FieldContext(
        category = CATEGORY_AUTHORIZATION,
        doc = "When this parameter is not empty, unauthenticated users perform as anonymousUserRole"
    )
    private String anonymousUserRole = null;

    @FieldContext(
        category =  CATEGORY_HTTP,
        doc = "If >0, it will reject all HTTP requests with bodies larged than the configured limit"
    )
    private long httpMaxRequestSize = -1;

    @FieldContext(
            category = CATEGORY_HTTP,
            doc = """
                The maximum size in bytes of the request header.
                Larger headers will allow for more and/or larger cookies plus larger form content encoded in a URL.
                However, larger headers consume more memory and can make a server more vulnerable to denial of service
                attacks.
              """
    )
    private int httpMaxRequestHeaderSize = 8 * 1024;

    @FieldContext(
        category =  CATEGORY_HTTP,
        doc = "If true, the broker will reject all HTTP requests using the TRACE and TRACK verbs.\n"
        + " This setting may be necessary if the broker is deployed into an environment that uses http port\n"
        + " scanning and flags web servers allowing the TRACE method as insecure."
    )
    private boolean disableHttpDebugMethods = false;

    @FieldContext(
            category =  CATEGORY_HTTP,
            doc = "Enable the enforcement of limits on the incoming HTTP requests"
        )
    private boolean httpRequestsLimitEnabled = false;

    @FieldContext(
            category =  CATEGORY_HTTP,
            doc = "Max HTTP requests per seconds allowed. The excess of requests will be rejected with HTTP code 429 "
                    + "(Too many requests)"
        )
    private double httpRequestsMaxPerSecond = 100.0;

    @FieldContext(
            category =  CATEGORY_HTTP,
            dynamic = true,
            doc = "Admin API fail on unknown request parameter in request-body. see PIP-179. Default false."
        )
    private boolean httpRequestsFailOnUnknownPropertiesEnabled = false;

    @FieldContext(
        category = CATEGORY_SASL_AUTH,
        doc = "This is a regexp, which limits the range of possible ids which can connect to the Broker using SASL.\n"
            + " Default value is: \".*pulsar.*\", so only clients whose id contains 'pulsar' are allowed to connect."
    )
    private String saslJaasClientAllowedIds = SaslConstants.JAAS_CLIENT_ALLOWED_IDS_DEFAULT;

    @FieldContext(
        category = CATEGORY_SASL_AUTH,
        doc = "Service Principal, for login context name. Default value is \"PulsarBroker\"."
    )
    private String saslJaasServerSectionName = SaslConstants.JAAS_DEFAULT_BROKER_SECTION_NAME;

    @FieldContext(
            category = CATEGORY_SASL_AUTH,
            doc = "Path to file containing the secret to be used to SaslRoleTokenSigner\n"
                    + "The secret can be specified like:\n"
                    + "saslJaasServerRoleTokenSignerSecretPath=file:///my/saslRoleTokenSignerSecret.key."
    )
    private String saslJaasServerRoleTokenSignerSecretPath;

    @FieldContext(
        category = CATEGORY_SASL_AUTH,
        doc = "kerberos kinit command."
    )
    private String kinitCommand = "/usr/bin/kinit";

    @FieldContext(
            category = CATEGORY_SASL_AUTH,
            doc = "how often the broker expires the inflight SASL context."
    )
    private long inflightSaslContextExpiryMs = 30_000L;

    @FieldContext(
            category = CATEGORY_SASL_AUTH,
            doc = "Maximum number of inflight sasl context."
    )
    private long maxInflightSaslContext = 50_000L;

    /**** --- BookKeeper Client. --- ****/
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Metadata service uri that bookkeeper is used for loading corresponding metadata driver"
            + " and resolving its metadata service location"
    )
    @Getter(AccessLevel.NONE)
    private String bookkeeperMetadataServiceUri;

    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Authentication plugin to use when connecting to bookies"
    )
    private String bookkeeperClientAuthenticationPlugin;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "BookKeeper auth plugin implementation specifics parameters name and values"
    )
    private String bookkeeperClientAuthenticationParametersName;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Parameters for bookkeeper auth plugin"
    )
    @ToString.Exclude
    private String bookkeeperClientAuthenticationParameters;

    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Timeout for BK add / read operations"
    )
    private long bookkeeperClientTimeoutInSeconds = 30;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Speculative reads are initiated if a read request doesn't complete within"
            + " a certain time Using a value of 0, is disabling the speculative reads")
    private int bookkeeperClientSpeculativeReadTimeoutInMillis = 0;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Number of channels per bookie"
    )
    private int bookkeeperNumberOfChannelsPerBookie = 16;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_BK,
        doc = "Use older Bookkeeper wire protocol with bookie"
    )
    private boolean bookkeeperUseV2WireProtocol = true;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Enable bookies health check. \n\n Bookies that have more than the configured"
            + " number of failure within the interval will be quarantined for some time."
            + " During this period, new ledgers won't be created on these bookies")
    private boolean bookkeeperClientHealthCheckEnabled = true;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Bookies health check interval in seconds"
    )
    private long bookkeeperClientHealthCheckIntervalSeconds = 60;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Bookies health check error threshold per check interval"
    )
    private long bookkeeperClientHealthCheckErrorThresholdPerInterval = 5;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Bookie health check quarantined time in seconds"
    )
    private long bookkeeperClientHealthCheckQuarantineTimeInSeconds = 1800;
    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "bookie quarantine ratio to avoid all clients quarantine "
                    + "the high pressure bookie servers at the same time"
    )
    private double bookkeeperClientQuarantineRatio = 1.0;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Enable rack-aware bookie selection policy. \n\nBK will chose bookies from"
            + " different racks when forming a new bookie ensemble")
    private boolean bookkeeperClientRackawarePolicyEnabled = true;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Enable region-aware bookie selection policy. \n\nBK will chose bookies from"
            + " different regions and racks when forming a new bookie ensemble")
    private boolean bookkeeperClientRegionawarePolicyEnabled = false;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Minimum number of racks per write quorum. \n\nBK rack-aware bookie selection policy will try to"
            + " get bookies from at least 'bookkeeperClientMinNumRacksPerWriteQuorum' racks for a write quorum.")
    private int bookkeeperClientMinNumRacksPerWriteQuorum = 2;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Enforces rack-aware bookie selection policy to pick bookies from "
            + "'bookkeeperClientMinNumRacksPerWriteQuorum' racks for  a writeQuorum. \n\n"
            + "If BK can't find bookie then it would throw BKNotEnoughBookiesException instead of picking random one.")
    private boolean bookkeeperClientEnforceMinNumRacksPerWriteQuorum = false;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "Enable/disable reordering read sequence on reading entries")
    private boolean bookkeeperClientReorderReadSequenceEnabled = true;
    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        required = false,
        doc = "Enable bookie isolation by specifying a list of bookie groups to choose from. \n\n"
            + "Any bookie outside the specified groups will not be used by the broker")
    private String bookkeeperClientIsolationGroups;
    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            required = false,
            doc = "Enable bookie secondary-isolation group if bookkeeperClientIsolationGroups doesn't have enough "
                    + "bookie available."
                )
    private String bookkeeperClientSecondaryIsolationGroups;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Set the interval to periodically check bookie info")
    private int bookkeeperClientGetBookieInfoIntervalSeconds = 60 * 60 * 24; // defaults to 24 hours

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Set the interval to retry a failed bookie info lookup")
    private int bookkeeperClientGetBookieInfoRetryIntervalSeconds = 60;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Enable/disable having read operations for a ledger to be "
            + "sticky to a single bookie.\n"
            + "If this flag is enabled, the client will use one single bookie (by "
            + "preference) to read all entries for a ledger.")
    private boolean bookkeeperEnableStickyReads = true;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Set the client security provider factory class name. "
            + "Default: org.apache.bookkeeper.tls.TLSContextFactory")
    private String bookkeeperTLSProviderFactoryClass = "org.apache.bookkeeper.tls.TLSContextFactory";

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Enable tls authentication with bookie")
    private boolean bookkeeperTLSClientAuthentication = false;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Supported type: PEM, JKS, PKCS12. Default value: PEM")
    private String bookkeeperTLSKeyFileType = "PEM";

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Supported type: PEM, JKS, PKCS12. Default value: PEM")
    private String bookkeeperTLSTrustCertTypes = "PEM";

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Path to file containing keystore password, "
            + "if the client keystore is password protected.")
    private String bookkeeperTLSKeyStorePasswordPath;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Path to file containing truststore password, "
            + "if the client truststore is password protected.")
    private String bookkeeperTLSTrustStorePasswordPath;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Path for the TLS private key file")
    private String bookkeeperTLSKeyFilePath;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Path for the TLS certificate file")
    private String bookkeeperTLSCertificateFilePath;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Path for the trusted TLS certificate file")
    private String bookkeeperTLSTrustCertsFilePath;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Tls cert refresh duration at bookKeeper-client in seconds (0 "
            + "to disable check)")
    private int bookkeeperTlsCertFilesRefreshDurationSeconds = 300;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Enable/disable disk weight based placement. Default is false")
    private boolean bookkeeperDiskWeightBasedPlacementEnabled = false;

    @FieldContext(category = CATEGORY_STORAGE_BK, doc = "Set the interval to check the need for sending an explicit "
            + "LAC")
    private int bookkeeperExplicitLacIntervalInMills = 0;

    @FieldContext(
        category = CATEGORY_STORAGE_BK,
        doc = "whether expose managed ledger client stats to prometheus"
    )
    private boolean bookkeeperClientExposeStatsToPrometheus = false;

    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "whether limit per_channel_bookie_client metrics of bookkeeper client stats"
    )
    private boolean bookkeeperClientLimitStatsLogging = true;

    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "Throttle value for bookkeeper client"
    )
    private int bookkeeperClientThrottleValue = 0;

    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "Number of BookKeeper client worker threads. Default is Runtime.getRuntime().availableProcessors()"
    )
    private int bookkeeperClientNumWorkerThreads = Runtime.getRuntime().availableProcessors();

    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "Number of BookKeeper client IO threads. Default is Runtime.getRuntime().availableProcessors() * 2"
    )
    private int bookkeeperClientNumIoThreads = Runtime.getRuntime().availableProcessors() * 2;

    @FieldContext(
            category = CATEGORY_STORAGE_BK,
            doc = "Use separated IO threads for BookKeeper client. Default is false, which will use Pulsar IO threads"
    )
    private boolean bookkeeperClientSeparatedIoThreadsEnabled = false;

    /**** --- Managed Ledger. --- ****/
    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Ensemble (E) size, Number of bookies to use for storing entries in a ledger.\n"
            + "Please notice that sticky reads enabled by bookkeeperEnableStickyReads=true aren’t used "
            + " unless ensemble size (E) equals write quorum (Qw) size."
    )
    private int managedLedgerDefaultEnsembleSize = 2;
    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Write quorum (Qw) size, Replication factor for storing entries (messages) in a ledger."
    )
    private int managedLedgerDefaultWriteQuorum = 2;
    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Ack quorum (Qa) size, Number of guaranteed copies "
               + "(acks to wait for before a write is considered completed)"
    )
    private int managedLedgerDefaultAckQuorum = 2;

    @FieldContext(minValue = 1,
            category = CATEGORY_STORAGE_ML,
            doc = "How frequently to flush the cursor positions that were accumulated due to rate limiting. (seconds)."
                    + " Default is 60 seconds")
    private int managedLedgerCursorPositionFlushSeconds = 60;

    @FieldContext(minValue = 1,
            category = CATEGORY_STORAGE_ML,
            doc = "How frequently to refresh the stats. (seconds). Default is 60 seconds")
    private int managedLedgerStatsPeriodSeconds = 60;

    //
    //
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Default type of checksum to use when writing to BookKeeper. \n\nDefault is `CRC32C`."
            + " Other possible options are `CRC32`, `MAC` or `DUMMY` (no checksum)."
    )
    private DigestType managedLedgerDigestType = DigestType.CRC32C;

    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Default  password to use when writing to BookKeeper. \n\nDefault is ``."
        )
    @ToString.Exclude
    private String managedLedgerPassword = "";

    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of bookies to use when creating a ledger"
    )
    private int managedLedgerMaxEnsembleSize = 5;
    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of copies to store for each message"
    )
    private int managedLedgerMaxWriteQuorum = 5;
    @FieldContext(
        minValue = 1,
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of guaranteed copies (acks to wait before write is complete)"
    )
    private int managedLedgerMaxAckQuorum = 5;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        dynamic = true,
        doc = "Amount of memory to use for caching data payload in managed ledger. \n\nThis"
            + " memory is allocated from JVM direct memory and it's shared across all the topics"
            + " running in the same broker. By default, uses 1/5th of available direct memory")
    private int managedLedgerCacheSizeMB = Math.max(64,
            (int) (DirectMemoryUtils.jvmMaxDirectMemory() / 5 / (1024 * 1024)));

    @FieldContext(category = CATEGORY_STORAGE_ML, doc = "Whether we should make a copy of the entry payloads when "
            + "inserting in cache")
    private boolean managedLedgerCacheCopyEntries = false;

    @FieldContext(category = CATEGORY_STORAGE_ML, doc = "Maximum buffer size for bytes read from storage."
            + " This is the memory retained by data read from storage (or cache) until it has been delivered to the"
            + " Consumer Netty channel. Use O to disable")
    private long managedLedgerMaxReadsInFlightSizeInMB = 0;

    @FieldContext(category = CATEGORY_STORAGE_ML, doc = "Maximum time to wait for acquiring permits for max reads in "
            + "flight when managedLedgerMaxReadsInFlightSizeInMB is set (>0) and the limit is reached.")
    private long managedLedgerMaxReadsInFlightPermitsAcquireTimeoutMillis = 60000;

    @FieldContext(category = CATEGORY_STORAGE_ML, doc = "Maximum number of reads that can be queued for acquiring "
            + "permits for max reads in flight when managedLedgerMaxReadsInFlightSizeInMB is set (>0) and the limit "
            + "is reached.")
    private int managedLedgerMaxReadsInFlightPermitsAcquireQueueSize = 50000;

    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        dynamic = true,
        doc = "Threshold to which bring down the cache level when eviction is triggered"
    )
    private double managedLedgerCacheEvictionWatermark = 0.9;
    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Configure the cache eviction frequency for the managed ledger cache.")
    @Deprecated
    private double managedLedgerCacheEvictionFrequency = 0;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Configure the cache eviction interval in milliseconds for the managed ledger cache, default is 10ms")
    private long managedLedgerCacheEvictionIntervalMs = 10;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            dynamic = true,
            doc = "All entries that have stayed in cache for more than the configured time, will be evicted")
    private long managedLedgerCacheEvictionTimeThresholdMillis = 1000;
    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Configure the threshold (in number of entries) from where a cursor should be considered 'backlogged'"
                    + " and thus should be set as inactive.")
    private long managedLedgerCursorBackloggedThreshold = 1000;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Rate limit the amount of writes per second generated by consumer acking the messages"
    )
    private double managedLedgerDefaultMarkDeleteRateLimit = 1.0;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        dynamic = true,
        doc = "Allow automated creation of topics if set to true (default value)."
    )
    private boolean allowAutoTopicCreation = true;
    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            dynamic = true,
            doc = "The type of topic that is allowed to be automatically created.(partitioned/non-partitioned)"
    )
    private TopicType allowAutoTopicCreationType = TopicType.NON_PARTITIONED;
    @FieldContext(category = CATEGORY_SERVER, dynamic = true,
            doc = "If 'allowAutoTopicCreation' is true and the name of the topic contains 'cluster',"
                    + "the topic cannot be automatically created."
    )
    private boolean allowAutoTopicCreationWithLegacyNamingScheme = true;
    @FieldContext(category = CATEGORY_SERVER, dynamic = true,
            doc = "If 'strictSubscriptionNameVerification' is true, the new subscription name can only contain"
                + " (a-zA-Z_0-9) and these special chars -=:."
    )
    private boolean strictlyVerifySubscriptionName = false;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        dynamic = true,
        doc = "Allow automated creation of subscriptions if set to true (default value)."
    )
    private boolean allowAutoSubscriptionCreation = true;
    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            dynamic = true,
            doc = "The number of partitioned topics that is allowed to be automatically created"
                    + " if allowAutoTopicCreationType is partitioned."
    )
    private int defaultNumPartitions = 1;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "The class of the managed ledger storage"
    )
    private String managedLedgerStorageClassName = "org.apache.pulsar.broker.ManagedLedgerClientFactory";
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Number of threads to be used for managed ledger scheduled tasks"
    )
    private int managedLedgerNumSchedulerThreads = Runtime.getRuntime().availableProcessors();

    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of entries to append to a ledger before triggering a rollover.\n\n"
            + "A ledger rollover is triggered after the min rollover time has passed"
            + " and one of the following conditions is true:"
            + " the max rollover time has been reached,"
            + " the max entries have been written to the ledger, or"
            + " the max ledger size has been written to the ledger")
    private int managedLedgerMaxEntriesPerLedger = 50000;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Minimum time between ledger rollover for a topic"
    )
    private int managedLedgerMinLedgerRolloverTimeMinutes = 10;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Maximum time before forcing a ledger rollover for a topic"
    )
    private int managedLedgerMaxLedgerRolloverTimeMinutes = 240;
    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Maximum ledger size before triggering a rollover for a topic (MB)"
    )
    private int managedLedgerMaxSizePerLedgerMbytes = 2048;
    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "Delay between a ledger being successfully offloaded to long term storage,"
            + " and the ledger being deleted from bookkeeper"
    )
    private long managedLedgerOffloadDeletionLagMs = TimeUnit.HOURS.toMillis(4);
    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "The number of bytes before triggering automatic offload to long term storage"
    )
    private long managedLedgerOffloadAutoTriggerSizeThresholdBytes = -1L;
    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "The threshold to triggering automatic offload to long term storage"
    )
    private long managedLedgerOffloadThresholdInSeconds = -1L;
    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "Trigger offload on topic load or not. Default is false"
    )
    private boolean triggerOffloadOnTopicLoad = false;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of entries to append to a cursor ledger"
    )
    private int managedLedgerCursorMaxEntriesPerLedger = 50000;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Max time before triggering a rollover on a cursor ledger"
    )
    private int managedLedgerCursorRolloverTimeInSeconds = 14400;

    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            dynamic = true,
            doc = "When resetting a subscription by timestamp, the broker will use the"
                    + " ledger closing timestamp metadata to determine the range of ledgers"
                    + " to search for the message where the subscription position is reset to. "
                    + " Since by default, the search condition is based on the message publish time provided by the "
                    + " client at the publish time, there will be some clock skew between the ledger closing timestamp "
                    + " metadata and the publish time."
                    + " This configuration is used to set the max clock skew between the ledger closing"
                    + " timestamp and the message publish time for finding the range of ledgers to open for searching."
                    + " The default value is 60000 milliseconds (60 seconds). When set to -1, the broker will not"
                    + " use the ledger closing timestamp metadata to determine the range of ledgers to search for the"
                    + " message."
    )
    private int managedLedgerCursorResetLedgerCloseTimestampMaxClockSkewMillis = 60000;

    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "Max number of `acknowledgment holes` that are going to be persistently stored.\n\n"
            + "When acknowledging out of order, a consumer will leave holes that are supposed"
            + " to be quickly filled by acking all the messages. The information of which"
            + " messages are acknowledged is persisted by compressing in `ranges` of messages"
            + " that were acknowledged. After the max number of ranges is reached, the information"
            + " will only be tracked in memory and messages will be redelivered in case of"
            + " crashes.")
    private int managedLedgerMaxUnackedRangesToPersist = 10000;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Maximum number of partially acknowledged batch messages per subscription that will have their batch "
                + "deleted indexes persisted. Batch deleted index state is handled when "
                + "acknowledgmentAtBatchIndexLevelEnabled=true.\n\n"
                + "When this limit is exceeded, remaining batch message containing the batch deleted indexes will "
                + "only be tracked in memory. In case of broker restarts or load balancing events, the batch "
                + "deleted indexes will be cleared while redelivering the messages to consumers.")
    private int managedLedgerMaxBatchDeletedIndexToPersist = 10000;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "When storing acknowledgement state, choose a more compact serialization format that stores"
                    + " individual acknowledgements as a bitmap which is serialized to an array of long values.\n\n"
                    + "NOTE: This setting requires managedLedgerUnackedRangesOpenCacheSetEnabled=true to be effective.")
    private boolean managedLedgerPersistIndividualAckAsLongArray = true;

    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "If enabled, the maximum \"acknowledgment holes\" will not be limited and \"acknowledgment holes\" "
                + "are stored in multiple entries.")
    private boolean persistentUnackedRangesWithMultipleEntriesEnabled = false;
    @Deprecated
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        deprecated = true,
        doc = "Max number of `acknowledgment holes` that can be stored in Zookeeper.\n\n"
            + "If number of unack message range is higher than this limit then broker will persist"
            + " unacked ranges into bookkeeper to avoid additional data overhead into zookeeper.\n"
            + "@deprecated - use managedLedgerMaxUnackedRangesToPersistInMetadataStore.")
    private int managedLedgerMaxUnackedRangesToPersistInZooKeeper = -1;
    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Max number of `acknowledgment holes` that can be stored in MetadataStore.\n\n"
                    + "If number of unack message range is higher than this limit then broker will persist"
                    + " unacked ranges into bookkeeper to avoid additional data overhead into MetadataStore.")
    private int managedLedgerMaxUnackedRangesToPersistInMetadataStore = 1000;
    @FieldContext(
            category = CATEGORY_STORAGE_OFFLOADING,
            doc = "When set to true, a BitSet will be used to track acknowledged messages that come after the \"mark "
                    + "delete position\" for each subscription.\n\nRoaringBitmap is used as a memory efficient BitSet "
                    + "implementation for the acknowledged messages tracking. Unacknowledged ranges are the message "
                    + "ranges excluding the acknowledged messages.")
    private boolean managedLedgerUnackedRangesOpenCacheSetEnabled = true;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_ML,
        doc = "After enabling this feature, Pulsar will stop delivery messages to clients if the cursor metadata is"
            + " too large to persist, it will help to reduce the duplicates caused by the ack state that can not be"
            + " fully persistent. Default false.")
    private boolean dispatcherPauseOnAckStatePersistentEnabled = false;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_ML,
        doc = "Skip reading non-recoverable/unreadable data-ledger under managed-ledger's list.\n\n"
            + " It helps when data-ledgers gets corrupted at bookkeeper and managed-cursor is stuck at that ledger."
    )
    private boolean autoSkipNonRecoverableData = false;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_ML,
        doc = "Skip managed ledger failure to forcefully recover managed ledger."
    )
    private boolean managedLedgerForceRecovery = false;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_ML,
        doc = "Skip schema ledger failure to forcefully recover topic successfully."
    )
    private boolean schemaLedgerForceRecovery = false;
    @FieldContext(
        category = CATEGORY_STORAGE_ML,
        doc = "operation timeout while updating managed-ledger metadata."
    )
    private long managedLedgerMetadataOperationsTimeoutSeconds = 60;

    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Read entries timeout when broker tries to read messages from bookkeeper "
                    + "(0 to disable it)"
        )
    private long managedLedgerReadEntryTimeoutSeconds = 0;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Add entry timeout when broker tries to publish message to bookkeeper.(0 to disable it)")
    private long managedLedgerAddEntryTimeoutSeconds = 0;

    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Managed ledger prometheus stats latency rollover seconds"
    )
    private int managedLedgerPrometheusStatsLatencyRolloverSeconds = 60;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_STORAGE_ML,
            doc = "Whether trace managed ledger task execution time"
    )
    private boolean managedLedgerTraceTaskExecution = true;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "New entries check delay for the cursor under the managed ledger. \n"
                    + "If no new messages in the topic, the cursor will try to check again after the delay time. \n"
                    + "For consumption latency sensitive scenario, can set to a smaller value or set to 0.\n"
                    + "Of course, this may degrade consumption throughput. Default is 10ms.")
    private int managedLedgerNewEntriesCheckDelayInMillis = 10;

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "Read priority when ledgers exists in both bookkeeper and the second layer storage.")
    private String managedLedgerDataReadPriority = OffloadedReadPriority.TIERED_STORAGE_FIRST
            .getValue();

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "ManagedLedgerInfo compression type, option values (NONE, LZ4, ZLIB, ZSTD, SNAPPY). \n"
                    + "If value is invalid or NONE, then save the ManagedLedgerInfo bytes data directly.")
    private String managedLedgerInfoCompressionType = "NONE";

    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "ManagedLedgerInfo compression size threshold (bytes), "
                    + "only compress metadata when origin size more then this value.\n"
                    + "0 means compression will always apply.\n")
    private long managedLedgerInfoCompressionThresholdInBytes = 16 * 1024;


    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "ManagedCursorInfo compression type, option values (NONE, LZ4, ZLIB, ZSTD, SNAPPY). \n"
                    + "If value is NONE, then save the ManagedCursorInfo bytes data directly.")
    private String managedCursorInfoCompressionType = "NONE";


    @FieldContext(category = CATEGORY_STORAGE_ML,
            doc = "ManagedCursorInfo compression size threshold (bytes), "
                    + "only compress metadata when origin size more then this value.\n"
                    + "0 means compression will always apply.\n")
    private long managedCursorInfoCompressionThresholdInBytes = 16 * 1024;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_STORAGE_ML,
            doc = "Minimum cursors that must be in backlog state to cache and reuse the read entries."
                    + "(Default =0 to disable backlog reach cache)"
    )
    private int managedLedgerMinimumBacklogCursorsForCaching = 0;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_STORAGE_ML,
            doc = "Minimum backlog entries for any cursor before start caching reads"
    )
    private int managedLedgerMinimumBacklogEntriesForCaching = 1000;
    @FieldContext(
            dynamic = true,
            category = CATEGORY_STORAGE_ML,
            doc = "Maximum backlog entry difference to prevent caching entries that can't be reused"
    )
    private int managedLedgerMaxBacklogBetweenCursorsForCaching = 1000;

    /*** --- Load balancer. --- ****/
     @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "Enable load balancer"
    )
    private boolean loadBalancerEnabled = true;
    @Deprecated
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            deprecated = true,
            doc = "load placement strategy[weightedRandomSelection/leastLoadedServer] (only used by "
                    + "SimpleLoadManagerImpl)"
    )
    private String loadBalancerPlacementStrategy = "leastLoadedServer"; // weighted random selection

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "load balance load shedding strategy "
                + "(It requires broker restart if value is changed using dynamic config). "
                + "Default is ThresholdShedder since 2.10.0"
    )
    private String loadBalancerLoadSheddingStrategy = "org.apache.pulsar.broker.loadbalance.impl.ThresholdShedder";

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "When [current usage < average usage - threshold], "
                    + "the broker with the highest load will be triggered to unload"
    )
    private boolean lowerBoundarySheddingEnabled = false;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "load balance placement strategy"
    )
    private String loadBalancerLoadPlacementStrategy =
            "org.apache.pulsar.broker.loadbalance.impl.LeastLongTermMessageRate";

    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Percentage of change to trigger load report update"
    )
    private int loadBalancerReportUpdateThresholdPercentage = 10;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "maximum interval to update load report"
    )
    private int loadBalancerReportUpdateMinIntervalMillis = 5000;
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Min delay of load report to collect, in minutes"
    )
    private int loadBalancerReportUpdateMaxIntervalMinutes = 15;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "Frequency of report to collect, in minutes"
    )
    private int loadBalancerHostUsageCheckIntervalMinutes = 1;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Enable/disable automatic bundle unloading for load-shedding"
    )
    private boolean loadBalancerSheddingEnabled = true;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "Load shedding interval. \n\nBroker periodically checks whether some traffic"
            + " should be offload from some over-loaded broker to other under-loaded brokers"
    )
    private int loadBalancerSheddingIntervalMinutes = 1;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "enable/disable distribute bundles evenly"
    )
    private boolean loadBalancerDistributeBundlesEvenlyEnabled = true;

    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "Prevent the same topics to be shed and moved to other broker more than"
            + " once within this timeframe"
    )
    private long loadBalancerSheddingGracePeriodMinutes = 30;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        deprecated = true,
        doc = "Usage threshold to determine a broker as under-loaded (only used by SimpleLoadManagerImpl)"
    )
    @Deprecated
    private int loadBalancerBrokerUnderloadedThresholdPercentage = 50;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Usage threshold to allocate max number of topics to broker"
    )
    private int loadBalancerBrokerMaxTopics = 50000;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Usage threshold to determine a broker as over-loaded"
    )
    private int loadBalancerBrokerOverloadedThresholdPercentage = 85;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Usage threshold to determine a broker whether to start threshold shedder"
    )
    private int loadBalancerBrokerThresholdShedderPercentage = 10;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Average resource usage difference threshold to determine a broker whether to be a best candidate in "
                    + "LeastResourceUsageWithWeight.(eg: broker1 with 10% resource usage with weight "
                    + "and broker2 with 30% and broker3 with 80% will have 40% average resource usage. "
                    + "The placement strategy can select broker1 and broker2 as best candidates.)"
    )
    private int loadBalancerAverageResourceUsageDifferenceThresholdPercentage = 10;


    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "In FlowOrQpsEquallyDivideBundleSplitAlgorithm,"
                    + " if msgRate >= loadBalancerNamespaceBundleMaxMsgRate * "
                    + " (100 + flowOrQpsDifferenceThresholdPercentage)/100.0 "
                    + " or throughput >=  loadBalancerNamespaceBundleMaxBandwidthMbytes * "
                    + " (100 + flowOrQpsDifferenceThresholdPercentage)/100.0, "
                    + " execute split bundle"
    )
    private int flowOrQpsDifferenceThresholdPercentage = 10;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "The low threshold for the difference between the highest and lowest loaded brokers."
    )
    private int loadBalancerAvgShedderLowThreshold = 15;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "The high threshold for the difference between the highest and lowest loaded brokers."
    )
    private int loadBalancerAvgShedderHighThreshold = 40;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "The number of times the low threshold is triggered before the bundle is unloaded."
    )
    private int loadBalancerAvgShedderHitCountLowThreshold = 8;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "The number of times the high threshold is triggered before the bundle is unloaded."
    )
    private int loadBalancerAvgShedderHitCountHighThreshold = 2;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "In the UniformLoadShedder and AvgShedder strategy, the minimum message that triggers unload."
    )
    private int minUnloadMessage = 1000;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "In the UniformLoadShedder and AvgShedder strategy, the minimum throughput that triggers unload."
    )
    private int minUnloadMessageThroughput = 1 * 1024 * 1024;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "In the UniformLoadShedder and AvgShedder strategy, the maximum unload ratio."
                    + "For AvgShedder, recommend to set to 0.5, so that it will distribute the load "
                    + "evenly between the highest and lowest brokers."
    )
    private double maxUnloadPercentage = 0.2;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Message-rate percentage threshold between highest and least loaded brokers for "
                + "uniform load shedding. (eg: broker1 with 50K msgRate and broker2 with 30K msgRate "
                + "will have 66% msgRate difference and load balancer can unload bundles from broker-1 "
                + "to broker-2)"
    )
    private double loadBalancerMsgRateDifferenceShedderThreshold = 50;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Message-throughput threshold between highest and least loaded brokers for "
                + "uniform load shedding. (eg: broker1 with 450MB msgRate and broker2 with 100MB msgRate "
                + "will have 4.5 times msgThroughout difference and load balancer can unload bundles "
                + "from broker-1 to broker-2)"
    )
    private double loadBalancerMsgThroughputMultiplierDifferenceShedderThreshold = 4;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "For each uniform balanced unload, the maximum number of bundles that can be unloaded."
                    + " The default value is -1, which means no limit"
    )
    private int maxUnloadBundleNumPerShedding = -1;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Resource history Usage Percentage When adding new resource usage info"
    )
    private double loadBalancerHistoryResourcePercentage = 0.9;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "BandwidthIn Resource Usage Weight"
    )
    private double loadBalancerBandwidthInResourceWeight = 1.0;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "BandwidthOut Resource Usage Weight"
    )
    private double loadBalancerBandwidthOutResourceWeight = 1.0;

    @Deprecated
    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "BandwidthIn Resource Usage Weight, Deprecated: Use loadBalancerBandwidthInResourceWeight"
    )
    private double loadBalancerBandwithInResourceWeight = 1.0;

    @Deprecated
    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "BandwidthOut Resource Usage Weight, Deprecated: Use loadBalancerBandwidthOutResourceWeight"
    )
    private double loadBalancerBandwithOutResourceWeight = 1.0;

    /**
     * Get the load balancer bandwidth in resource weight.
     * To be compatible with the old configuration, we still support the old configuration.
     * If a configuration is not the default configuration, use that configuration.
     * If both the new and the old are configured different from the default value, use the new one.
     * @return
     */
    public double getLoadBalancerBandwidthInResourceWeight() {
        if (loadBalancerBandwidthInResourceWeight != 1.0) {
            return loadBalancerBandwidthInResourceWeight;
        }
        if (loadBalancerBandwithInResourceWeight != 1.0) {
            return loadBalancerBandwithInResourceWeight;
        }
        return 1.0;
    }

    public double getLoadBalancerBandwidthOutResourceWeight() {
        if (loadBalancerBandwidthOutResourceWeight != 1.0) {
            return loadBalancerBandwidthOutResourceWeight;
        }
        if (loadBalancerBandwithOutResourceWeight != 1.0) {
            return loadBalancerBandwithOutResourceWeight;
        }
        return 1.0;
    }

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "CPU Resource Usage Weight"
    )
    private double loadBalancerCPUResourceWeight = 1.0;

    @Deprecated(since = "3.0.0")
    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Memory Resource Usage Weight. Deprecated: Memory is no longer used as a load balancing item.",
            deprecated = true
    )
    private double loadBalancerMemoryResourceWeight = 0;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Direct Memory Resource Usage Weight. Direct memory usage cannot accurately reflect the "
                    + "machine's load, and it is not recommended to use it to score the machine's load."
    )
    private double loadBalancerDirectMemoryResourceWeight = 0;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Bundle unload minimum throughput threshold (MB)"
    )
    private double loadBalancerBundleUnloadMinThroughputThreshold = 10;

    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        doc = "Interval to flush dynamic resource quota to ZooKeeper"
    )
    private int loadBalancerResourceQuotaUpdateIntervalMinutes = 15;
    @Deprecated
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        deprecated = true,
        doc = "Usage threshold to determine a broker is having just right level of load"
            + " (only used by SimpleLoadManagerImpl)"
    )
    private int loadBalancerBrokerComfortLoadLevelPercentage = 65;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "enable/disable automatic namespace bundle split"
    )
    private boolean loadBalancerAutoBundleSplitEnabled = true;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "enable/disable automatic unloading of split bundles"
    )
    private boolean loadBalancerAutoUnloadSplitBundlesEnabled = true;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "maximum topics in a bundle, otherwise bundle split will be triggered"
    )
    private int loadBalancerNamespaceBundleMaxTopics = 1000;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "maximum sessions (producers + consumers) in a bundle, otherwise bundle split will be triggered"
                + "(disable threshold check with value -1)"
    )
    private int loadBalancerNamespaceBundleMaxSessions = 1000;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "maximum msgRate (in + out) in a bundle, otherwise bundle split will be triggered"
    )
    private int loadBalancerNamespaceBundleMaxMsgRate = 30000;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "maximum bandwidth (in + out) in a bundle, otherwise bundle split will be triggered"
    )
    private int loadBalancerNamespaceBundleMaxBandwidthMbytes = 100;
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "maximum number of bundles in a namespace"
    )
    private int loadBalancerNamespaceMaximumBundles = 128;
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Name of load manager to use"
    )
    private String loadManagerClassName = "org.apache.pulsar.broker.loadbalance.impl.ModularLoadManagerImpl";

    @FieldContext(category = CATEGORY_LOAD_BALANCER, doc = "Name of topic bundle assignment strategy to use")
    private String topicBundleAssignmentStrategy =
            "org.apache.pulsar.common.naming.ConsistentHashingTopicBundleAssigner";
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Supported algorithms name for namespace bundle split"
    )
    private List<String> supportedNamespaceBundleSplitAlgorithms = Lists.newArrayList("range_equally_divide",
            "topic_count_equally_divide", "specified_positions_divide", "flow_or_qps_equally_divide");
    @FieldContext(
        dynamic = true,
        category = CATEGORY_LOAD_BALANCER,
        doc = "Default algorithm name for namespace bundle split"
    )
    private String defaultNamespaceBundleSplitAlgorithm = "range_equally_divide";
    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        doc = "Option to override the auto-detected network interfaces max speed"
    )
    private Optional<Double> loadBalancerOverrideBrokerNicSpeedGbps = Optional.empty();

    @FieldContext(
        category = CATEGORY_LOAD_BALANCER,
        dynamic = true,
        doc = "Time to wait for the unloading of a namespace bundle"
    )
    private long namespaceBundleUnloadingTimeoutMs = 60000;

    /**** --- Load Balancer Extension. --- ****/
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Option to enable the debug mode for the load balancer logics. "
                    + "The debug mode prints more logs to provide more information "
                    + "such as load balance states and decisions. "
                    + "(only used in load balancer extension logics)"
    )
    private boolean loadBalancerDebugModeEnabled = false;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "The target standard deviation of the resource usage across brokers "
                    + "(100% resource usage is 1.0 load). "
                    + "The shedder logic tries to distribute bundle load across brokers to meet this target std. "
                    + "The smaller value will incur load balancing more frequently. "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private double loadBalancerBrokerLoadTargetStd = 0.25;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Threshold to the consecutive count of fulfilled shedding(unload) conditions. "
                    + "If the unload scheduler consecutively finds bundles that meet unload conditions "
                    + "many times bigger than this threshold, the scheduler will shed the bundles. "
                    + "The bigger value will incur less bundle unloading/transfers. "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private int loadBalancerSheddingConditionHitCountThreshold = 3;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Option to enable the bundle transfer mode when distributing bundle loads. "
                    + "On: transfer bundles from overloaded brokers to underloaded "
                    + "-- pre-assigns the destination broker upon unloading). "
                    + "Off: unload bundles from overloaded brokers "
                    + "-- post-assigns the destination broker upon lookups). "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private boolean loadBalancerTransferEnabled = true;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Maximum number of brokers to unload bundle load for each unloading cycle. "
                    + "The bigger value will incur more unloading/transfers for each unloading cycle. "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private int loadBalancerMaxNumberOfBrokerSheddingPerCycle = 3;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Delay (in seconds) to the next unloading cycle after unloading. "
                    + "The logic tries to give enough time for brokers to recompute load after unloading. "
                    + "The bigger value will delay the next unloading cycle longer. "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private long loadBalanceSheddingDelayInSeconds = 180;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Broker load data time to live (TTL in seconds). "
                    + "The logic tries to avoid (possibly unavailable) brokers with out-dated load data, "
                    + "and those brokers will be ignored in the load computation. "
                    + "When tuning this value, please consider loadBalancerReportUpdateMaxIntervalMinutes. "
                    + "The current default value is loadBalancerReportUpdateMaxIntervalMinutes * 120, reflecting "
                    + "twice the duration in seconds. "
                    + "(only used in load balancer extension TransferShedder)"
    )
    private long loadBalancerBrokerLoadDataTTLInSeconds = 1800;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Max number of bundles in bundle load report from each broker. "
                    + "The load balancer distributes bundles across brokers, "
                    + "based on topK bundle load data and other broker load data."
                    + "The bigger value will increase the overhead of reporting many bundles in load data. "
                    + "Used for ExtensibleLoadManagerImpl and ModularLoadManagerImpl, default value is 10. "
                    + "User can disable the bundle filtering feature of ModularLoadManagerImpl by setting to -1."
                    + "Enabling this feature can reduce the pressure on the zookeeper when doing load report."
                    + "WARNING: too small value could result in a long load balance time."
    )
    private int loadBalancerMaxNumberOfBundlesInBundleLoadReport = 10;
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "Service units'(bundles) split interval. Broker periodically checks whether "
                    + "some service units(e.g. bundles) should split if they become hot-spots. "
                    + "(only used in load balancer extension logics)"
    )
    private int loadBalancerSplitIntervalMinutes = 1;
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Max number of bundles to split to per cycle. "
                    + "(only used in load balancer extension logics)"
    )
    private int loadBalancerMaxNumberOfBundlesToSplitPerCycle = 10;
    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Threshold to the consecutive count of fulfilled split conditions. "
                    + "If the split scheduler consecutively finds bundles that meet split conditions "
                    + "many times bigger than this threshold, the scheduler will trigger splits on the bundles "
                    + "(if the number of bundles is less than loadBalancerNamespaceMaximumBundles). "
                    + "(only used in load balancer extension logics)"
    )
    private int loadBalancerNamespaceBundleSplitConditionHitCountThreshold = 3;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "After this delay, the service-unit state channel tombstones any service units (e.g., bundles) "
                    + "in semi-terminal states. For example, after splits, parent bundles will be `deleted`, "
                    + "and then after this delay, the parent bundles' state will be `tombstoned` "
                    + "in the service-unit state channel. "
                    + "Pulsar does not immediately remove such semi-terminal states "
                    + "to avoid unnecessary system confusion, "
                    + "as the bundles in the `tombstoned` state might temporarily look available to reassign. "
                    + "Rarely, one could lower this delay in order to aggressively clean "
                    + "the service-unit state channel when there are a large number of bundles. "
                    + "minimum value = 30 secs"
                    + "(only used in load balancer extension logics)"
    )
    private long loadBalancerServiceUnitStateTombstoneDelayTimeInSeconds = 3600;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            dynamic = true,
            doc = "Option to automatically unload namespace bundles with affinity(isolation) "
                    + "or anti-affinity group policies."
                    + "Such bundles are not ideal targets to auto-unload as destination brokers are limited."
                    + "(only used in load balancer extension logics)"
    )
    private boolean loadBalancerSheddingBundlesWithPoliciesEnabled = false;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "The namespaces to be excluded from load shedding"
    )
    private Set<String> loadBalancerSheddingExcludedNamespaces = new HashSet<>();

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "Time to wait before fixing any stuck in-flight service unit states. "
                    + "The leader monitor fixes any in-flight service unit(bundle) states "
                    + "by reassigning the ownerships if stuck too long, longer than this period."
                    + "(only used in load balancer extension logics)"
    )
    private long loadBalancerInFlightServiceUnitStateWaitingTimeInMillis = 30 * 1000;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "Interval between service unit state monitor checks. "
                    + "The service unit(bundle) state channel is periodically monitored"
                    + " by the leader broker at this interval"
                    + " to fix any orphan bundle ownerships, stuck in-flight states, and other cleanup jobs."
                    + "`loadBalancerServiceUnitStateTombstoneDelayTimeInSeconds` * 1000 must be bigger than "
                    + "`loadBalancerInFlightServiceUnitStateWaitingTimeInMillis`."
                    + "(only used in load balancer extension logics)"
    )
    private long loadBalancerServiceUnitStateMonitorIntervalInSeconds = 60;

    @FieldContext(
            category = CATEGORY_LOAD_BALANCER,
            doc = "Enables the multi-phase unloading of bundles. Set to true, forwards destination broker information "
                    + "to consumers and producers during bundle unload, allowing them to quickly reconnect to the "
                    + "broker without performing an additional topic lookup."
    )
    private boolean loadBalancerMultiPhaseBundleUnload = true;

    @FieldContext(
            dynamic = false,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Name of ServiceUnitStateTableView implementation class to use"
    )
    private String loadManagerServiceUnitStateTableViewClassName =
            "org.apache.pulsar.broker.loadbalance.extensions.channel.ServiceUnitStateTableViewImpl";

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Specify ServiceUnitTableViewSyncer to sync service unit(bundle) states between metadata store and "
                    + "system topic table views during migration from one to the other. One could enable this"
                    + " syncer before migration and disable it after the migration finishes. "
                    + "It accepts `MetadataStoreToSystemTopicSyncer` or `SystemTopicToMetadataStoreSyncer` to "
                    + "enable it. It accepts `None` to disable it."
    )
    private ServiceUnitTableViewSyncerType loadBalancerServiceUnitTableViewSyncer = ServiceUnitTableViewSyncerType.None;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_LOAD_BALANCER,
            doc = "Specify the maximum number of concurrent orphan bundle ownership overrides. "
                    + "The leader broker triggers these overrides upon detecting orphaned bundles. "
                    + "It identifies orphan bundle ownerships by periodically scanning ownership data "
                    + "and monitoring for broker shutdowns or inactive states."
    )
    private int loadBalancerServiceUnitStateMaxConcurrentOverrides = 64;

    /**** --- Replication. --- ****/
    @FieldContext(
        category = CATEGORY_REPLICATION,
        doc = "Enable replication metrics"
    )
    private boolean replicationMetricsEnabled = true;
    @FieldContext(
        category = CATEGORY_REPLICATION,
        doc = "Max number of connections to open for each broker in a remote cluster.\n\n"
            + "More connections host-to-host lead to better throughput over high-latency links"
    )
    private int replicationConnectionsPerBroker = 16;
    @FieldContext(
        required = false,
        category = CATEGORY_REPLICATION,
        doc = "replicator prefix used for replicator producer name and cursor name"
    )
    private String replicatorPrefix = "pulsar.repl";
    @FieldContext(
        category = CATEGORY_REPLICATION,
        dynamic = true,
        doc = "Replicator producer queue size. "
                + "When dynamically modified, it only takes effect for the newly added replicators"
    )
    private int replicationProducerQueueSize = 1000;
    @FieldContext(
            category = CATEGORY_REPLICATION,
            doc = "Duration to check replication policy to avoid replicator "
                    + "inconsistency due to missing ZooKeeper watch (disable with value 0)"
        )
    private int replicationPolicyCheckDurationSeconds = 600;
    @FieldContext(
            category = CATEGORY_REPLICATION,
            doc = "Whether the internal replicator will trigger topic auto-creation on the remote cluster."
        )
    private boolean createTopicToRemoteClusterForReplication = true;
    @Deprecated
    @FieldContext(
        category = CATEGORY_REPLICATION,
        deprecated = true,
        doc = "@deprecated - Use brokerClientTlsEnabled instead."
    )
    private boolean replicationTlsEnabled = false;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Default message retention time."
            + " 0 means retention is disabled. -1 means data is not removed by time quota"
    )
    private int defaultRetentionTimeInMinutes = 0;
    @FieldContext(
        category = CATEGORY_POLICIES,
        doc = "Default retention size."
            + " 0 means retention is disabled. -1 means data is not removed by size quota"
    )
    private int defaultRetentionSizeInMB = 0;
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "How often to check pulsar connection is still alive"
    )
    private int keepAliveIntervalSeconds = 30;
    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Timeout for connection liveness check used to check liveness of possible consumer or producer "
                    + "duplicates. Helps prevent ProducerFencedException with exclusive producer, "
                    + "ConsumerAssignException with range conflict for Key Shared with sticky hash ranges or "
                    + "ConsumerBusyException in the case of an exclusive consumer. Set to 0 to disable connection "
                    + "liveness check."
    )
    private long connectionLivenessCheckTimeoutMillis = 5000L;
    @Deprecated
    @FieldContext(
        category = CATEGORY_POLICIES,
        deprecated = true,
        doc = "How often broker checks for inactive topics to be deleted (topics with no subscriptions and no one "
                + "connected) Deprecated in favor of using `brokerDeleteInactiveTopicsFrequencySeconds`\n"
                + "@deprecated - unused."
    )
    private int brokerServicePurgeInactiveFrequencyInSeconds = 60;
    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "A comma-separated list of namespaces to bootstrap"
    )
    private List<String> bootstrapNamespaces = new ArrayList<String>();

    @ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Properties properties = new Properties();

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "The properties whose name starts with this prefix will be uploaded to the metadata store for "
                    + " the topic lookup"
    )
    private String lookupPropertyPrefix = "lookup.";

    @FieldContext(
        dynamic = true,
        category = CATEGORY_SERVER,
        doc = "If true, (and ModularLoadManagerImpl is being used), the load manager will attempt to "
            + "use only brokers running the latest software version (to minimize impact to bundles)"
    )
    private boolean preferLaterVersions = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Interval between checks to see if topics with compaction policies need to be compacted"
    )
    private int brokerServiceCompactionMonitorIntervalInSeconds = 60;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "The estimated backlog size is greater than this threshold, compression will be triggered.\n"
            + "Using a value of 0, is disabling compression check."
    )
    private long brokerServiceCompactionThresholdInBytes = 0;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Timeout for each read request in the compaction phase one loop, If the execution time of one "
                    + "single message read operation exceeds this time, the compaction will not proceed."
    )
    private long brokerServiceCompactionPhaseOneLoopTimeInSeconds = 30;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Whether retain null-key message during topic compaction."
    )
    private boolean topicCompactionRetainNullKey = false;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Interval between checks to see if cluster is migrated and marks topic migrated "
                + " if cluster is marked migrated. Disable with value 0. (Default disabled)."
    )
    private int clusterMigrationCheckDurationSeconds = 0;

    @FieldContext(
        category = CATEGORY_SERVER,
        doc = "Flag to start cluster migration for topic only after creating all topic's resources"
                + " such as tenant, namespaces, subscriptions at new green cluster. (Default disabled)."
    )
    private boolean clusterMigrationAutoResourceCreation = false;

    @FieldContext(
        category = CATEGORY_SCHEMA,
        doc = "Enforce schema validation on following cases:\n\n"
            + " - if a producer without a schema attempts to produce to a topic with schema, the producer will be\n"
            + "   failed to connect. PLEASE be carefully on using this, since non-java clients don't support schema.\n"
            + "   if you enable this setting, it will cause non-java clients failed to produce."
    )
    private boolean isSchemaValidationEnforced = false;

    @FieldContext(
        category = CATEGORY_SCHEMA,
        doc = "The schema storage implementation used by this broker"
    )
    private String schemaRegistryStorageClassName = "org.apache.pulsar.broker.service.schema"
            + ".BookkeeperSchemaStorageFactory";

    @FieldContext(
        category = CATEGORY_SCHEMA,
        doc = "The list compatibility checkers to be used in schema registry"
    )
    private Set<String> schemaRegistryCompatibilityCheckers = Sets.newHashSet(
            "org.apache.pulsar.broker.service.schema.JsonSchemaCompatibilityCheck",
            "org.apache.pulsar.broker.service.schema.AvroSchemaCompatibilityCheck",
            "org.apache.pulsar.broker.service.schema.ProtobufNativeSchemaCompatibilityCheck"
    );

    @FieldContext(
            category = CATEGORY_SCHEMA,
            doc = "The schema compatibility strategy in broker level"
    )
    private SchemaCompatibilityStrategy schemaCompatibilityStrategy = SchemaCompatibilityStrategy.FULL;

    /**** --- WebSocket. --- ****/
    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "Number of IO threads in Pulsar Client used in WebSocket proxy"
    )
    private int webSocketNumIoThreads = Runtime.getRuntime().availableProcessors();

    @FieldContext(category = CATEGORY_WEBSOCKET,
            doc = "Number of threads used by Websocket service")
    private int webSocketNumServiceThreads = 20;

    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "Number of connections per Broker in Pulsar Client used in WebSocket proxy"
    )
    private int webSocketConnectionsPerBroker = Runtime.getRuntime().availableProcessors();

    @FieldContext(
            category = CATEGORY_WEBSOCKET,
            doc = "Memory limit in MBs for direct memory in Pulsar Client used in WebSocket proxy"
    )
    private int webSocketPulsarClientMemoryLimitInMB = 0;

    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "Time in milliseconds that idle WebSocket session times out"
    )
    private int webSocketSessionIdleTimeoutMillis = 300000;

    @FieldContext(
            category = CATEGORY_WEBSOCKET,
            doc = "Interval of time to sending the ping to keep alive in WebSocket proxy. "
                    + "This value greater than 0 means enabled")
    private int webSocketPingDurationSeconds = -1;

    @FieldContext(
        category = CATEGORY_WEBSOCKET,
        doc = "The maximum size of a text message during parsing in WebSocket proxy."
    )
    private int webSocketMaxTextFrameSize = 1048576;

    /**** --- Metrics. --- ****/
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "Whether the '/metrics' endpoint requires authentication. Defaults to false."
                    + "'authenticationEnabled' must also be set for this to take effect."
    )
    private boolean authenticateMetricsEndpoint = false;
    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "If true, export topic level metrics otherwise namespace level"
    )
    private boolean exposeTopicLevelMetricsInPrometheus = true;
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "Set to true to enable the broker to cache the metrics response; the default is false. "
                    + "The caching period is defined by `managedLedgerStatsPeriodSeconds`. "
                    + "The broker returns the same response for subsequent requests within the same period. "
                    + "Ensure that the scrape interval of your monitoring system matches the caching period.")
    private boolean metricsBufferResponse = false;
    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "If true, export consumer level metrics otherwise namespace level"
    )
    private boolean exposeConsumerLevelMetricsInPrometheus = false;
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "If true, export producer level metrics otherwise namespace level"
    )
    private boolean exposeProducerLevelMetricsInPrometheus = false;
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "If true, export managed ledger metrics (aggregated by namespace)"
    )
    private boolean exposeManagedLedgerMetricsInPrometheus = true;
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "If true, export managed cursor metrics"
    )
    private boolean exposeManagedCursorMetricsInPrometheus = false;
    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "Classname of Pluggable JVM GC metrics logger that can log GC specific metrics")
    private String jvmGCMetricsLoggerClassName;

    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "Enable expose the precise backlog stats.\n"
                + " Set false to use published counter and consumed counter to calculate,\n"
                + " this would be more efficient but may be inaccurate. Default is false."
    )
    private boolean exposePreciseBacklogInPrometheus = false;

    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "Time in milliseconds that metrics endpoint would time out. Default is 30s.\n"
                + " Increase it if there are a lot of topics to expose topic-level metrics.\n"
                + " Set it to 0 to disable timeout."
    )
    private long metricsServletTimeoutMs = 30000;

    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "Enable expose the backlog size for each subscription when generating stats.\n"
                    + " Locking is used for fetching the status so default to false."
    )
    private boolean exposeSubscriptionBacklogSizeInPrometheus = false;

    @FieldContext(
            category = CATEGORY_METRICS,
            doc = "Enable splitting topic and partition label in Prometheus.\n"
                    + " If enabled, a topic name will split into 2 parts, one is topic name without partition index,\n"
                    + " another one is partition index, e.g. (topic=xxx, partition=0).\n"
                    + " If the topic is a non-partitioned topic, -1 will be used for the partition index.\n"
                    + " If disabled, one label to represent the topic and partition, e.g. (topic=xxx-partition-0)\n"
                    + " Default is false."
    )
    private boolean splitTopicAndPartitionLabelInPrometheus = false;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_METRICS,
            doc = "Enable expose the broker bundles metrics."
    )
    private boolean exposeBundlesMetricsInPrometheus = false;

    /**** --- Functions. --- ****/
    @FieldContext(
        category = CATEGORY_FUNCTIONS,
        doc = "Flag indicates enabling or disabling function worker on brokers"
    )
    private boolean functionsWorkerEnabled = false;

    @FieldContext(
        category = CATEGORY_FUNCTIONS,
        doc = "The nar package for the function worker service"
    )
    private String functionsWorkerServiceNarPackage = "";

    @FieldContext(
            category = CATEGORY_FUNCTIONS,
            doc = "Flag indicates enabling or disabling function worker using unified PackageManagement service."
    )
    private boolean functionsWorkerEnablePackageManagement = false;

    /**** --- Broker Web Stats. --- ****/
    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "If true, export publisher stats when returning topics stats from the admin rest api"
    )
    private boolean exposePublisherStats = true;
    @FieldContext(
        category = CATEGORY_METRICS,
        minValue = 1,
        doc = "Stats update frequency in seconds"
    )
    private int statsUpdateFrequencyInSecs = 60;
    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "Stats update initial delay in seconds"
    )
    private int statsUpdateInitialDelayInSecs = 60;
    @FieldContext(
            category = CATEGORY_METRICS,
            minValue = -1,
            doc = "HealthCheck update frequency in seconds. Disable health check with value -1 (Default value -1)"
    )
    private int healthCheckMetricsUpdateTimeInSeconds = -1;
    @FieldContext(
        category = CATEGORY_METRICS,
        doc = "If true, aggregate publisher stats of PartitionedTopicStats by producerName"
    )
    private boolean aggregatePublisherStatsByProducerName = false;

    /**** --- Ledger Offloading. --- ****/
    /****
     * NOTES: all implementation related settings should be put in implementation package.
     *        only common settings like driver name, io threads can be added here.
     ****/
    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "The directory to locate offloaders"
    )
    private String offloadersDirectory = "./offloaders";

    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "Driver to use to offload old data to long term storage"
    )
    private String managedLedgerOffloadDriver = null;

    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "Maximum number of thread pool threads for ledger offloading"
    )
    private int managedLedgerOffloadMaxThreads = 2;

    @FieldContext(
            category = CATEGORY_STORAGE_OFFLOADING,
            doc = "Maximum number of thread pool threads for offloaded ledger reading"
    )
    private int managedLedgerOffloadReadThreads = 2;

    @FieldContext(
        category = CATEGORY_STORAGE_OFFLOADING,
        doc = "The directory where nar Extraction of offloaders happens"
    )
    private String narExtractionDirectory = NarClassLoader.DEFAULT_NAR_EXTRACTION_DIR;

    @FieldContext(
            category = CATEGORY_STORAGE_OFFLOADING,
            doc = "Maximum prefetch rounds for ledger reading for offloading"
    )
    private int managedLedgerOffloadPrefetchRounds = 1;

    @FieldContext(
        dynamic = true,
        category = CATEGORY_STORAGE_ML,
        doc = "Time to rollover ledger for inactive topic (duration without any publish on that topic). "
                + "Disable rollover with value 0 (Default value 0)"
        )
    private int managedLedgerInactiveLedgerRolloverTimeSeconds = 0;

    @FieldContext(
            dynamic = true,
            category = CATEGORY_STORAGE_ML,
            doc = "Time to evict inactive offloaded ledger for inactive topic. "
                    + "Disable eviction with value 0 (Default value 600)"
    )
    private int managedLedgerInactiveOffloadedLedgerEvictionTimeSeconds = 600;

    @FieldContext(
            category = CATEGORY_STORAGE_ML,
            doc = "Evicting cache data by the slowest markDeletedPosition or readPosition. "
                    + "The default is to evict through readPosition."
    )
    private boolean cacheEvictionByMarkDeletedPosition = false;

    /**** --- Transaction config variables. --- ****/
    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Enable transaction coordinator in broker"
    )
    private boolean transactionCoordinatorEnabled = false;

    @FieldContext(
        category = CATEGORY_TRANSACTION,
            doc = "Class name for transaction metadata store provider"
    )
    private String transactionMetadataStoreProviderClassName =
            "org.apache.pulsar.transaction.coordinator.impl.MLTransactionMetadataStoreProvider";

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Class name for transaction buffer provider"
    )
    private String transactionBufferProviderClassName =
            "org.apache.pulsar.broker.transaction.buffer.impl.TopicTransactionBufferProvider";

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Class name for transaction pending ack store provider"
    )
    private String transactionPendingAckStoreProviderClassName =
            "org.apache.pulsar.broker.transaction.pendingack.impl.MLPendingAckStoreProvider";

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Number of threads to use for pulsar transaction replay PendingAckStore or TransactionBuffer."
                    + "Default is 5"
    )
    private int numTransactionReplayThreadPoolSize = Runtime.getRuntime().availableProcessors();

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Transaction buffer take snapshot transaction count"
                    + "If transaction buffer enables snapshot segment, transaction buffer updates snapshot metadata"
                    + "after the number of transaction operations reaches this value."
    )
    private int transactionBufferSnapshotMaxTransactionCount = 1000;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "The interval time for transaction buffer to take snapshots."
                    + "If transaction buffer enables snapshot segment, "
                    + "it is the interval time for transaction buffer to update snapshot metadata."
    )
    private int transactionBufferSnapshotMinTimeInMillis = 5000;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Transaction buffer stores the transaction ID of aborted transactions and takes snapshots."
                    + "This configuration determines the size of the snapshot segment. "
                    + "The default value is 256 KB (262144 bytes)."
    )
    private int transactionBufferSnapshotSegmentSize = 262144;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "Whether to enable segmented transaction buffer snapshot "
                    + "to handle a large number of aborted transactions."
    )
    private boolean transactionBufferSegmentedSnapshotEnabled = false;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "The max concurrent requests for transaction buffer client."
    )
    private int transactionBufferClientMaxConcurrentRequests = 1000;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "The transaction buffer client's operation timeout in milliseconds."
    )
    private long transactionBufferClientOperationTimeoutInMills = 3000L;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "The max active transactions per transaction coordinator, default value 0 indicates no limit."
    )
    private long maxActiveTransactionsPerCoordinator = 0L;

    @FieldContext(
            category = CATEGORY_TRANSACTION,
            doc = "MLPendingAckStore maintain a ConcurrentSkipListMap pendingAckLogIndex`,"
                    + "it store the position in pendingAckStore as value and save a position used to determine"
                    + "whether the previous data can be cleaned up as a key."
                    + "transactionPendingAckLogIndexMinLag is used to configure the minimum lag between indexes"
    )
    private long transactionPendingAckLogIndexMinLag = 500L;

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "Provide a mechanism allowing the Transaction Log Store to aggregate multiple records into a batched"
                    + " record and persist into a single BK entry. This will make Pulsar transactions work more"
                    + " efficiently, aka batched log. see: https://github.com/apache/pulsar/issues/15370. Default false"
    )
    private boolean transactionLogBatchedWriteEnabled = false;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction log batch, this attribute means maximum log records count"
                    + " in a batch, default 512."
    )
    private int transactionLogBatchedWriteMaxRecords = 512;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction log batch, this attribute means bytes size in a"
                    + " batch, default 4m."
    )
    private int transactionLogBatchedWriteMaxSize = 1024 * 1024 * 4;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction log batch, this attribute means maximum wait time(in millis)"
                    + " for the first record in a batch, default 1 millisecond."
    )
    private int transactionLogBatchedWriteMaxDelayInMillis = 1;

    @FieldContext(
            category = CATEGORY_SERVER,
            dynamic = true,
            doc = "Provide a mechanism allowing the transaction pending ack Log Store to aggregate multiple records"
                    + " into a batched record and persist into a single BK entry. This will make Pulsar transactions"
                    + " work more efficiently, aka batched log. see: https://github.com/apache/pulsar/issues/15370."
                    + " Default false."
    )
    private boolean transactionPendingAckBatchedWriteEnabled = false;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction log batch, this attribute means maximum log records count"
                    + " in a batch, default 512."
    )
    private int transactionPendingAckBatchedWriteMaxRecords = 512;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction pending ack log batch, this attribute means bytes size in"
                    + " a batch, default 4m."
    )
    private int transactionPendingAckBatchedWriteMaxSize = 1024 * 1024 * 4;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "If enabled the feature that transaction pending ack log batch, this attribute means maximum wait"
                    + " time(in millis) for the first record in a batch, default 1 millisecond."
    )
    private int transactionPendingAckBatchedWriteMaxDelayInMillis = 1;

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "The class name of the factory that implements the topic compaction service."
    )
    private String compactionServiceFactoryClassName = "org.apache.pulsar.compaction.PulsarCompactionServiceFactory";

    @FieldContext(
            category = CATEGORY_SERVER,
            doc = "Opt-out of topic-existence check when setting permissions"
    )
    private boolean allowAclChangesOnNonExistentTopics = false;

    /**** --- KeyStore TLS config variables. --- ****/
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "Enable TLS with KeyStore type configuration in broker"
    )
    private boolean tlsEnabledWithKeyStore = false;

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "Specify the TLS provider for the broker service: \n"
                    + "When using TLS authentication with CACert, the valid value is either OPENSSL or JDK.\n"
                    + "When using TLS authentication with KeyStore, available values can be SunJSSE, Conscrypt and etc."
    )
    private String tlsProvider = null;

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore type configuration in broker: JKS, PKCS12"
    )
    private String tlsKeyStoreType = "JKS";

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore path in broker"
    )
    private String tlsKeyStore = null;

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore password for broker"
    )
    @ToString.Exclude
    private String tlsKeyStorePassword = null;

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore type configuration in broker: JKS, PKCS12"
    )
    private String tlsTrustStoreType = "JKS";

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore path in broker"
    )
    private String tlsTrustStore = null;

    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore password for broker, null means empty password."
    )
    @ToString.Exclude
    private String tlsTrustStorePassword = null;

    /**** --- Config variables used for internal client/admin to auth with other broker. --- ****/
    @FieldContext(
            category = CATEGORY_AUTHENTICATION,
            dynamic = true,
            doc = "Authentication settings of the broker itself. \n\nUsed when the broker connects"
                    + " to other brokers, either in same or other clusters. "
                    + "Default uses plugin which disables authentication"
    )
    private String brokerClientAuthenticationPlugin = "org.apache.pulsar.client.impl.auth.AuthenticationDisabled";
    @FieldContext(
            category = CATEGORY_AUTHENTICATION,
            dynamic = true,
            doc = "Authentication parameters of the authentication plugin the broker is using to connect "
                    + "to other brokers"
    )
    @ToString.Exclude
    private String brokerClientAuthenticationParameters = "";
    @FieldContext(
            category = CATEGORY_REPLICATION,
            dynamic = true,
            doc = "Enable TLS when talking with other brokers in the same cluster (admin operation) "
                    + "or different clusters (replication)"
    )
    private boolean brokerClientTlsEnabled = false;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "Whether internal client use KeyStore type to authenticate with other Pulsar brokers"
    )
    private boolean brokerClientTlsEnabledWithKeyStore = false;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "The TLS Provider used by internal client to authenticate with other Pulsar brokers"
    )
    private String brokerClientSslProvider = null;
    @FieldContext(
            category = CATEGORY_AUTHENTICATION,
            doc = "TLS trusted certificate file for internal client, "
                    + "used by the internal client to authenticate with Pulsar brokers")
    private String brokerClientTrustCertsFilePath = "";
    @FieldContext(
            category = CATEGORY_AUTHENTICATION,
            doc = "TLS private key file for internal client, "
                    + "used by the internal client to authenticate with Pulsar brokers")
    private String brokerClientKeyFilePath = "";
    @FieldContext(
            category = CATEGORY_AUTHENTICATION,
            doc = "TLS certificate file for internal client, "
                    + "used by the internal client to authenticate with Pulsar brokers"
    )
    private String brokerClientCertificateFilePath = "";
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore type configuration for internal client: JKS, PKCS12 "
                    + " used by the internal client to authenticate with Pulsar brokers"
    )
    private String brokerClientTlsTrustStoreType = "JKS";
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore path for internal client, "
                    + " used by the internal client to authenticate with Pulsar brokers"
    )
    private String brokerClientTlsTrustStore = null;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS TrustStore password for internal client, "
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    @ToString.Exclude
    private String brokerClientTlsTrustStorePassword = null;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore type configuration for internal client: JKS, PKCS12,"
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    private String brokerClientTlsKeyStoreType = "JKS";
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore path for internal client, "
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    private String brokerClientTlsKeyStore = null;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "TLS KeyStore password for internal client, "
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    @ToString.Exclude
    private String brokerClientTlsKeyStorePassword = null;
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "Specify the tls cipher the internal client will use to negotiate during TLS Handshake"
                  + " (a comma-separated list of ciphers).\n\n"
                  + "Examples:- [TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256].\n"
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    private Set<String> brokerClientTlsCiphers = new TreeSet<>();
    @FieldContext(
            category = CATEGORY_KEYSTORE_TLS,
            doc = "Specify the tls protocols the broker will use to negotiate during TLS handshake"
                  + " (a comma-separated list of protocol names).\n\n"
                  + "Examples:- [TLSv1.3, TLSv1.2] \n"
                  + " used by the internal client to authenticate with Pulsar brokers"
    )
    private Set<String> brokerClientTlsProtocols = new TreeSet<>();
    @FieldContext(
            category = CATEGORY_TLS,
            doc = "SSL Factory Plugin class used by internal client to provide SSLEngine and SSLContext objects. "
                    + "The default class used is DefaultSslFactory.")
    private String brokerClientSslFactoryPlugin = DefaultPulsarSslFactory.class.getName();
    @FieldContext(
            category = CATEGORY_TLS,
            doc = "SSL Factory plugin configuration parameters used by internal client.")
    private String brokerClientSslFactoryPluginParams = "";

    /* packages management service configurations (begin) */

    @FieldContext(
        category = CATEGORY_PACKAGES_MANAGEMENT,
        doc = "Enable the packages management service or not"
    )
    private boolean enablePackagesManagement = false;

    @FieldContext(
        category = CATEGORY_PACKAGES_MANAGEMENT,
        doc = "The packages management service storage service provider"
    )
    private String packagesManagementStorageProvider = "org.apache.pulsar.packages.management.storage.bookkeeper"
            + ".BookKeeperPackagesStorageProvider";

    @FieldContext(
        category = CATEGORY_PACKAGES_MANAGEMENT,
        doc = "When the packages storage provider is bookkeeper, you can use this configuration to\n"
            + "control the number of replicas for storing the package"
    )
    private int packagesReplicas = 1;

    @FieldContext(
        category = CATEGORY_PACKAGES_MANAGEMENT,
        doc = "The bookkeeper ledger root path"
    )
    private String packagesManagementLedgerRootPath = "/ledgers";

    /* packages management service configurations (end) */

    @FieldContext(
            category = CATEGORY_PLUGIN,
            doc = "The directory to locate broker additional servlet"
    )
    private String additionalServletDirectory = "./brokerAdditionalServlet";

    @FieldContext(
            category = CATEGORY_PLUGIN,
            doc = "List of broker additional servlet to load, which is a list of broker additional servlet names"
    )
    private Set<String> additionalServlets = new TreeSet<>();

    /**
     * @deprecated Use {@link #getSubscriptionTypesEnabled()} instead
     */
    @Deprecated
    public boolean isSubscriptionKeySharedEnable() {
        return subscriptionKeySharedEnable && subscriptionTypesEnabled.contains("Key_Shared");
    }

    public String getMetadataStoreUrl() {
        if (StringUtils.isNotBlank(metadataStoreUrl)) {
            return metadataStoreUrl;
        } else if (StringUtils.isNotBlank(zookeeperServers)) {
            // Fallback to old setting
            return ZKMetadataStore.ZK_SCHEME_IDENTIFIER + zookeeperServers;
        } else {
            return "";
        }
    }

    /**
     * Tells whether the selected metadata store implementation is based on ZooKeeper.
     */
    public boolean isMetadataStoreBackedByZookeeper() {
        return MetadataStoreFactory.isBasedOnZookeeper(getMetadataStoreUrl());
    }

    public String getConfigurationMetadataStoreUrl() {
        if (StringUtils.isNotBlank(configurationMetadataStoreUrl)) {
            return configurationMetadataStoreUrl;
        } else if (StringUtils.isNotBlank(configurationStoreServers)) {
            return configurationStoreServers;
        } else if (StringUtils.isNotBlank(globalZookeeperServers)) {
            return globalZookeeperServers;
        } else {
            // Fallback to local zookeeper
            return getMetadataStoreUrl();
        }
    }

    public boolean isConfigurationStoreSeparated() {
        return !Objects.equals(getConfigurationMetadataStoreUrl(), getMetadataStoreUrl());
    }

    public boolean isBookkeeperMetadataStoreSeparated() {
        return StringUtils.isNotBlank(bookkeeperMetadataServiceUri);
    }

    public String getBookkeeperMetadataStoreUrl() {
        if (isBookkeeperMetadataStoreSeparated()) {
            return bookkeeperMetadataServiceUri;
        } else {
            // Fallback to same metadata service used by broker, adding the "metadata-store" to specify the BK
            // metadata adapter
            // Note: chroot is not settable by using 'zookeeperServers' config.
            return "metadata-store:" + getMetadataStoreUrl() + BookKeeperConstants.DEFAULT_ZK_LEDGERS_ROOT_PATH;
        }
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public boolean isDefaultTopicTypePartitioned() {
        return TopicType.PARTITIONED.equals(allowAutoTopicCreationType);
    }

    public int getBrokerDeleteInactiveTopicsMaxInactiveDurationSeconds() {
        if (brokerDeleteInactiveTopicsMaxInactiveDurationSeconds == null) {
            return brokerDeleteInactiveTopicsFrequencySeconds;
        } else {
            return brokerDeleteInactiveTopicsMaxInactiveDurationSeconds;
        }
    }

    public SchemaCompatibilityStrategy getSchemaCompatibilityStrategy() {
        if (SchemaCompatibilityStrategy.isUndefined(schemaCompatibilityStrategy)) {
            return SchemaCompatibilityStrategy.FULL;
        }
        return schemaCompatibilityStrategy;
    }

    public int getManagedLedgerMaxUnackedRangesToPersistInMetadataStore() {
        return managedLedgerMaxUnackedRangesToPersistInZooKeeper > 0
                ? managedLedgerMaxUnackedRangesToPersistInZooKeeper :
                managedLedgerMaxUnackedRangesToPersistInMetadataStore;
    }

    public long getMetadataStoreSessionTimeoutMillis() {
        return zooKeeperSessionTimeoutMillis > 0 ? zooKeeperSessionTimeoutMillis : metadataStoreSessionTimeoutMillis;
    }

    public int getMetadataStoreOperationTimeoutSeconds() {
        return zooKeeperOperationTimeoutSeconds > 0 ? zooKeeperOperationTimeoutSeconds
                : metadataStoreOperationTimeoutSeconds;
    }

    public int getMetadataStoreCacheExpirySeconds() {
        return zooKeeperCacheExpirySeconds > 0 ? zooKeeperCacheExpirySeconds : metadataStoreCacheExpirySeconds;
    }

    public boolean isMetadataStoreAllowReadOnlyOperations() {
        return zooKeeperAllowReadOnlyOperations || metadataStoreAllowReadOnlyOperations;
    }

    public long getManagedLedgerCacheEvictionIntervalMs() {
        return managedLedgerCacheEvictionFrequency > 0
                ? (long) (1000 / Math.max(
                        Math.min(managedLedgerCacheEvictionFrequency, MAX_ML_CACHE_EVICTION_FREQUENCY),
                                   MIN_ML_CACHE_EVICTION_FREQUENCY))
                : Math.min(MAX_ML_CACHE_EVICTION_INTERVAL_MS, managedLedgerCacheEvictionIntervalMs);
    }

    public int getTopicOrderedExecutorThreadNum() {
        return numWorkerThreadsForNonPersistentTopic > 0
                ? numWorkerThreadsForNonPersistentTopic : topicOrderedExecutorThreadNum;
    }

    public Map<String, String> lookupProperties() {
        final var map = new HashMap<String, String>();
        properties.forEach((key, value) -> {
            if (key instanceof String && value instanceof String && ((String) key).startsWith(lookupPropertyPrefix)) {
                map.put((String) key, (String) value);
            }
        });
        return map;
    }

    public boolean isLoadBalancerServiceUnitTableViewSyncerEnabled() {
        return loadBalancerServiceUnitTableViewSyncer != ServiceUnitTableViewSyncerType.None;
    }

    public enum ServiceUnitTableViewSyncerType {
        None,
        MetadataStoreToSystemTopicSyncer,
        SystemTopicToMetadataStoreSyncer;
    }
}
