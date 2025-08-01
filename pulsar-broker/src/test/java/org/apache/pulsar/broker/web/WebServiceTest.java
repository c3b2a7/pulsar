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
package org.apache.pulsar.broker.web;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.apache.pulsar.broker.stats.BrokerOpenTelemetryTestUtil.assertMetricLongSumValue;
import static org.apache.pulsar.broker.stats.prometheus.PrometheusMetricsClient.Metric;
import static org.apache.pulsar.broker.stats.prometheus.PrometheusMetricsClient.parseMetrics;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import lombok.Cleanup;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.PrometheusMetricsTestUtil;
import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.testcontext.PulsarTestContext;
import org.apache.pulsar.broker.web.RateLimitingFilter.Result;
import org.apache.pulsar.broker.web.WebExecutorThreadPoolStats.LimitType;
import org.apache.pulsar.broker.web.WebExecutorThreadPoolStats.UsageType;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminBuilder;
import org.apache.pulsar.client.admin.PulsarAdminException.ConflictException;
import org.apache.pulsar.client.impl.auth.AuthenticationTls;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.ClusterDataImpl;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.common.util.ObjectMapperFactory;
import org.apache.pulsar.common.util.SecurityUtility;
import org.apache.pulsar.utils.ResourceUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Tests for the {@code WebService} class. Note that this test only covers the newly added ApiVersionFilter related
 * tests for now as this test class was added quite a bit after the class was written.
 */
@Test(groups = "broker")
public class WebServiceTest {

    private PulsarTestContext pulsarTestContext;
    private PulsarService pulsar;
    private String brokerLookUpUrl;
    private String brokerLookUpUrlTls;

    private static final String CA_CERT_FILE_PATH =
            ResourceUtils.getAbsolutePath("certificate-authority/certs/ca.cert.pem");
    private static final String BROKER_CERT_FILE_PATH =
            ResourceUtils.getAbsolutePath("certificate-authority/server-keys/broker.cert.pem");
    private static final String BROKER_KEY_FILE_PATH =
            ResourceUtils.getAbsolutePath("certificate-authority/server-keys/broker.key-pk8.pem");
    private static final String CLIENT_CERT_FILE_PATH =
            ResourceUtils.getAbsolutePath("certificate-authority/client-keys/admin.cert.pem");
    private static final String CLIENT_KEY_FILE_PATH =
            ResourceUtils.getAbsolutePath("certificate-authority/client-keys/admin.key-pk8.pem");


    @Test
    public void testWebExecutorMetrics() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        var otelMetrics = pulsarTestContext.getOpenTelemetryMetricReader().collectAllMetrics();
        assertMetricLongSumValue(otelMetrics, WebExecutorThreadPoolStats.LIMIT_COUNTER, LimitType.MAX.attributes,
                value -> assertThat(value).isPositive());
        assertMetricLongSumValue(otelMetrics, WebExecutorThreadPoolStats.LIMIT_COUNTER, LimitType.MIN.attributes,
                value -> assertThat(value).isPositive());
        assertMetricLongSumValue(otelMetrics, WebExecutorThreadPoolStats.USAGE_COUNTER, UsageType.ACTIVE.attributes,
                value -> assertThat(value).isNotNegative());
        assertMetricLongSumValue(otelMetrics, WebExecutorThreadPoolStats.USAGE_COUNTER, UsageType.CURRENT.attributes,
                value -> assertThat(value).isPositive());
        assertMetricLongSumValue(otelMetrics, WebExecutorThreadPoolStats.USAGE_COUNTER, UsageType.IDLE.attributes,
                value -> assertThat(value).isNotNegative());

        ByteArrayOutputStream statsOut = new ByteArrayOutputStream();
        PrometheusMetricsTestUtil.generate(pulsar, false, false, false, statsOut);
        String metricsStr = statsOut.toString();
        Multimap<String, Metric> metrics = parseMetrics(metricsStr);

        Collection<Metric> maxThreads = metrics.get("pulsar_web_executor_max_threads");
        Collection<Metric> minThreads = metrics.get("pulsar_web_executor_min_threads");
        Collection<Metric> activeThreads = metrics.get("pulsar_web_executor_active_threads");
        Collection<Metric> idleThreads = metrics.get("pulsar_web_executor_idle_threads");
        Collection<Metric> currentThreads = metrics.get("pulsar_web_executor_current_threads");

        for (Metric metric : maxThreads) {
            Assert.assertNotNull(metric.tags.get("cluster"));
            Assert.assertTrue(metric.value > 0);
        }
        for (Metric metric : minThreads) {
            Assert.assertNotNull(metric.tags.get("cluster"));
            Assert.assertTrue(metric.value > 0);
        }
        for (Metric metric : activeThreads) {
            Assert.assertNotNull(metric.tags.get("cluster"));
            Assert.assertTrue(metric.value >= 0);
        }
        for (Metric metric : idleThreads) {
            Assert.assertNotNull(metric.tags.get("cluster"));
            Assert.assertTrue(metric.value >= 0);
        }
        for (Metric metric : currentThreads) {
            Assert.assertNotNull(metric.tags.get("cluster"));
            Assert.assertTrue(metric.value > 0);
        }
    }

    /**
     * Test that the {@WebService} class properly passes the allowUnversionedClients value. We do this by setting
     * allowUnversionedClients to true, then making a request with no version, which should go through.
     *
     */
    @Test
    public void testDefaultClientVersion() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        try {
            // Make an HTTP request to lookup a namespace. The request should
            // succeed
            makeHttpRequest(false, false);
        } catch (Exception e) {
            Assert.fail("HTTP request to lookup a namespace shouldn't fail ", e);
        }
    }

    /**
     * Test that if enableTls option is enabled, WebService is available both on HTTP and HTTPS.
     *
     * @throws Exception
     */
    @Test
    public void testTlsEnabled() throws Exception {
        setupEnv(false, true, false, false, -1, false);

        // Make requests both HTTP and HTTPS. The requests should succeed
        try {
            makeHttpRequest(false, false);
        } catch (Exception e) {
            Assert.fail("HTTP request shouldn't fail ", e);
        }
        try {
            makeHttpRequest(true, false);
        } catch (Exception e) {
            Assert.fail("HTTPS request shouldn't fail ", e);
        }
    }

    /**
     * Test that if enableTls option is disabled, WebService is available only on HTTP.
     *
     * @throws Exception
     */
    @Test
    public void testTlsDisabled() throws Exception {
        setupEnv(false, false, false, false, -1, false);

        // Make requests both HTTP and HTTPS. Only the HTTP request should succeed
        try {
            makeHttpRequest(false, false);
        } catch (Exception e) {
            Assert.fail("HTTP request shouldn't fail ", e);
        }
        try {
            makeHttpRequest(true, false);
            Assert.fail("HTTPS request should fail ");
        } catch (Exception e) {
            // Expected
        }
    }

    /**
     * Test that if enableAuth option and allowInsecure option are enabled, WebService requires trusted/untrusted client
     * certificate.
     *
     * @throws Exception
     */
    @Test
    public void testTlsAuthAllowInsecure() throws Exception {
        setupEnv(false, true, true, true, -1, false);

        // Only the request with client certificate should succeed
        try {
            makeHttpRequest(true, false);
            Assert.fail("Request without client certficate should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("HTTP response code: 401"));
        }
        try {
            makeHttpRequest(true, true);
        } catch (Exception e) {
            Assert.fail("Request with client certificate shouldn't fail", e);
        }
    }

    /**
     * Test that if enableAuth option is enabled, WebService requires trusted client certificate.
     *
     * @throws Exception
     */
    @Test
    public void testTlsAuthDisallowInsecure() throws Exception {
        setupEnv(false, true, true, false, -1, false);

        // Only the request with trusted client certificate should succeed
        try {
            makeHttpRequest(true, false);
            Assert.fail("Request without client certficate should fail");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("HTTP response code: 401"));
        }
        try {
            makeHttpRequest(true, true);
        } catch (Exception e) {
            Assert.fail("Request with client certificate shouldn't fail", e);
        }
    }

    @Test
    public void testRateLimiting() throws Exception {
        setupEnv(false, false, false, false, 10.0, false);

        // setupEnv makes a HTTP call to create the cluster.
        var metrics = pulsarTestContext.getOpenTelemetryMetricReader().collectAllMetrics();
        assertMetricLongSumValue(metrics, RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME,
                Result.ACCEPTED.attributes, 1);
        assertThat(metrics).noneSatisfy(metricData -> assertThat(metricData)
                .hasName(RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME)
                .hasLongSumSatisfying(
                        sum -> sum.hasPointsSatisfying(point -> point.hasAttributes(Result.REJECTED.attributes))));

        // Make requests without exceeding the max rate
        for (int i = 0; i < 5; i++) {
            makeHttpRequest(false, false);
            Thread.sleep(200);
        }

        metrics = pulsarTestContext.getOpenTelemetryMetricReader().collectAllMetrics();
        assertMetricLongSumValue(metrics, RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME,
                Result.ACCEPTED.attributes, 6);
        assertThat(metrics).noneSatisfy(metricData -> assertThat(metricData)
                .hasName(RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME)
                .hasLongSumSatisfying(
                        sum -> sum.hasPointsSatisfying(point -> point.hasAttributes(Result.REJECTED.attributes))));

        try {
            for (int i = 0; i < 500; i++) {
                makeHttpRequest(false, false);
            }

            fail("Some request should have failed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("429"));
        }

        metrics = pulsarTestContext.getOpenTelemetryMetricReader().collectAllMetrics();
        assertMetricLongSumValue(metrics, RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME,
                Result.ACCEPTED.attributes, value -> assertThat(value).isGreaterThan(6));
        assertMetricLongSumValue(metrics, RateLimitingFilter.RATE_LIMIT_REQUEST_COUNT_METRIC_NAME,
                Result.REJECTED.attributes, value -> assertThat(value).isPositive());
    }

    @Test
    public void testSplitPath() {
        String result = PulsarWebResource.splitPath("prop/cluster/ns/topic1", 4);
        Assert.assertEquals(result, "topic1");
    }

    @Test
    public void testDisableHttpTraceAndTrackMethods() throws Exception {
        setupEnv(true, false, false, false, -1, true);

        String url = pulsar.getWebServiceAddress() + "/admin/v2/tenants/my-tenant" + System.currentTimeMillis();

        @Cleanup
        AsyncHttpClient client = new DefaultAsyncHttpClient();

        BoundRequestBuilder builder = client.prepare("TRACE", url);

        Response res = builder.execute().get();

        // This should have failed
        assertEquals(res.getStatusCode(), 405);

        builder = client.prepare("TRACK", url);

        res = builder.execute().get();

        // This should have failed
        assertEquals(res.getStatusCode(), 405);
    }

    @Test
    public void testMaxRequestSize() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        String url = pulsar.getWebServiceAddress() + "/admin/v2/tenants/my-tenant" + System.currentTimeMillis();

        @Cleanup
        AsyncHttpClient client = new DefaultAsyncHttpClient();

        BoundRequestBuilder builder = client.preparePut(url)
                .setHeader("Accept", "application/json")
                .setHeader("Content-Type", "application/json");

        // HTTP server is configured to reject everything > 10K
        TenantInfo info1 = TenantInfo.builder()
                .adminRoles(Collections.singleton(StringUtils.repeat("*", 20 * 1024)))
                .build();
        builder.setBody(ObjectMapperFactory.getMapper().writer().writeValueAsBytes(info1));
        Response res = builder.execute().get();

        // This should have failed
        assertEquals(res.getStatusCode(), 400);

        // Create local cluster
        String localCluster = "test";
        pulsar.getPulsarResources().getClusterResources().createCluster(localCluster,
                ClusterDataImpl.builder().build());
        TenantInfo info2 = TenantInfo.builder()
                .adminRoles(Collections.singleton(StringUtils.repeat("*", 1 * 1024)))
                .allowedClusters(Sets.newHashSet(localCluster))
                .build();
        builder.setBody(ObjectMapperFactory.getMapper().writer().writeValueAsBytes(info2));

        Response res2 = builder.execute().get();
        assertEquals(res2.getStatusCode(), 204);

        // Simple GET without content size should go through
        Response res3 = client.prepareGet(url)
            .setHeader("Accept", "application/json")
            .setHeader("Content-Type", "application/json")
            .execute()
            .get();
        assertEquals(res3.getStatusCode(), 200);
    }

    @Test
    public void testBrokerReady() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        String url = pulsar.getWebServiceAddress() + "/admin/v2/brokers/ready";

        @Cleanup
        AsyncHttpClient client = new DefaultAsyncHttpClient();

        Response res = client.prepareGet(url).execute().get();
        assertEquals(res.getStatusCode(), 200);
        assertEquals(res.getResponseBody(), "ok");
    }

    @Test
    public void testCompressOutputMetricsInPrometheus() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        String metricsUrl = pulsar.getWebServiceAddress() + "/metrics/";

        URL url = new URL(metricsUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept-Encoding", "gzip");

        StringBuilder content = new StringBuilder();

        try (InputStream inputStream = connection.getInputStream()) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                // Process the decompressed content
                int data;
                while ((data = gzipInputStream.read()) != -1) {
                    content.append((char) data);
                }
            }

            log.info("Response Content: {}", content);
            assertTrue(content.toString().contains("process_cpu_seconds_total"));
        } catch (IOException e) {
            log.error("Failed to decompress the content, likely the content is not compressed ", e);
            fail();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    public void testUnCompressOutputMetricsInPrometheus() throws Exception {
        setupEnv(true, false, false, false, -1, false);

        String metricsUrl = pulsar.getWebServiceAddress() + "/metrics/";

        URL url = new URL(metricsUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        StringBuilder content = new StringBuilder();

        try (InputStream inputStream = connection.getInputStream()) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                fail();
            } catch (IOException e) {
                assertTrue(e instanceof ZipException);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line + "\n");
            }
        } finally {
            connection.disconnect();
        }

        log.info("Response Content: {}", content);

        assertTrue(content.toString().contains("process_cpu_seconds_total"));
    }

    private String makeHttpRequest(boolean useTls, boolean useAuth) throws Exception {
        InputStream response = null;
        try {
            if (useTls) {
                KeyManager[] keyManagers = null;
                if (useAuth) {
                    Certificate[] tlsCert = SecurityUtility.loadCertificatesFromPemFile(CLIENT_CERT_FILE_PATH);
                    PrivateKey tlsKey = SecurityUtility.loadPrivateKeyFromPemFile(CLIENT_KEY_FILE_PATH);

                    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                    ks.load(null, null);
                    ks.setKeyEntry("private", tlsKey, "".toCharArray(), tlsCert);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, "".toCharArray());
                    keyManagers = kmf.getKeyManagers();
                }
                TrustManager[] trustManagers = InsecureTrustManagerFactory.INSTANCE.getTrustManagers();
                SSLContext sslCtx = SSLContext.getInstance("TLS");
                sslCtx.init(keyManagers, trustManagers, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.getSocketFactory());
                response = new URL(brokerLookUpUrlTls).openStream();
            } else {
                response = new URL(brokerLookUpUrl).openStream();
            }
            String resp = CharStreams.toString(new InputStreamReader(response));
            log.info("Response: {}", resp);
            return resp;
        } finally {
            Closeables.close(response, false);
        }
    }

    private void setupEnv(boolean enableFilter, boolean enableTls, boolean enableAuth, boolean allowInsecure,
                          double rateLimit, boolean disableTrace) throws Exception {
        if (pulsar != null) {
            throw new Exception("broker already started");
        }
        Set<String> providers = new HashSet<>();
        providers.add("org.apache.pulsar.broker.authentication.AuthenticationProviderTls");

        Set<String> roles = new HashSet<>();
        roles.add("client");

        ServiceConfiguration config = new ServiceConfiguration();
        config.setAdvertisedAddress("localhost");
        config.setBrokerShutdownTimeoutMs(0L);
        config.setLoadBalancerOverrideBrokerNicSpeedGbps(Optional.of(1.0d));
        config.setBrokerServicePort(Optional.of(0));
        config.setWebServicePort(Optional.of(0));
        if (enableTls) {
            config.setWebServicePortTls(Optional.of(0));
        }
        config.setClientLibraryVersionCheckEnabled(enableFilter);
        config.setAuthenticationEnabled(enableAuth);
        config.setAuthenticationProviders(providers);
        config.setAuthorizationEnabled(false);
        config.setSuperUserRoles(roles);
        config.setTlsCertificateFilePath(BROKER_CERT_FILE_PATH);
        config.setTlsKeyFilePath(BROKER_KEY_FILE_PATH);
        config.setTlsAllowInsecureConnection(allowInsecure);
        config.setTlsTrustCertsFilePath(allowInsecure ? "" : CA_CERT_FILE_PATH);
        config.setClusterName("local");
        config.setAdvertisedAddress("localhost"); // TLS certificate expects localhost
        config.setMetadataStoreUrl("zk:localhost:2181");
        config.setHttpMaxRequestSize(10 * 1024);
        config.setDisableHttpDebugMethods(disableTrace);
        if (rateLimit > 0) {
            config.setHttpRequestsLimitEnabled(true);
            config.setHttpRequestsMaxPerSecond(rateLimit);
        }

        pulsarTestContext = PulsarTestContext.builder()
                .spyByDefault()
                .config(config)
                .enableOpenTelemetry(true)
                .build();

        pulsar = pulsarTestContext.getPulsarService();

        String brokerUrlBase = "http://localhost:" + pulsar.getListenPortHTTP().get();
        String brokerUrlBaseTls = "https://localhost:" + pulsar.getListenPortHTTPS().orElse(-1);
        String serviceUrl = brokerUrlBase;

        PulsarAdminBuilder adminBuilder = PulsarAdmin.builder();
        if (enableTls && enableAuth) {
            serviceUrl = brokerUrlBaseTls;

            Map<String, String> authParams = new HashMap<>();
            authParams.put("tlsCertFile", CLIENT_CERT_FILE_PATH);
            authParams.put("tlsKeyFile", CLIENT_KEY_FILE_PATH);

            adminBuilder.authentication(AuthenticationTls.class.getName(), authParams).allowTlsInsecureConnection(true);
        }

        brokerLookUpUrl = brokerUrlBase
                + "/lookup/v2/destination/persistent/my-property/local/my-namespace/my-topic";
        brokerLookUpUrlTls = brokerUrlBaseTls
                + "/lookup/v2/destination/persistent/my-property/local/my-namespace/my-topic";
        @Cleanup
        PulsarAdmin pulsarAdmin = adminBuilder.serviceHttpUrl(serviceUrl).build();

        try {
            pulsarAdmin.clusters().createCluster(config.getClusterName(),
                    ClusterData.builder().serviceUrl(pulsar.getWebServiceAddress()).build());
        } catch (ConflictException ce) {
            // This is OK.
        }
    }

    @AfterMethod(alwaysRun = true)
    void teardown() {
        if (pulsarTestContext != null) {
            try {
                pulsarTestContext.close();
                pulsarTestContext = null;
            } catch (Exception e) {
                Assert.fail("Got exception while closing the pulsar instance ", e);
            }
        }
        pulsar = null;
    }

    private static final Logger log = LoggerFactory.getLogger(WebServiceTest.class);
}
