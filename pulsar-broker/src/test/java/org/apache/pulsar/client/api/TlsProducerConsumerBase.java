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
package org.apache.pulsar.client.api;

import static org.mockito.Mockito.spy;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.impl.auth.AuthenticationTls;
import org.apache.pulsar.common.policies.data.ClusterData;
import org.apache.pulsar.common.policies.data.TenantInfoImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "broker-api")
public abstract class TlsProducerConsumerBase extends ProducerConsumerBase {
    private final String clusterName = "use";

    @BeforeMethod
    @Override
    protected void setup() throws Exception {
        // TLS configuration for Broker
        internalSetUpForBroker();

        // Start Broker
        super.init();
    }

    @AfterMethod(alwaysRun = true)
    @Override
    protected void cleanup() throws Exception {
        super.internalCleanup();
    }

    protected void internalSetUpForBroker() {
        conf.setBrokerServicePortTls(Optional.of(0));
        conf.setWebServicePortTls(Optional.of(0));
        conf.setTlsCertificateFilePath(BROKER_CERT_FILE_PATH);
        conf.setTlsKeyFilePath(BROKER_KEY_FILE_PATH);
        conf.setTlsTrustCertsFilePath(CA_CERT_FILE_PATH);
        conf.setClusterName(clusterName);
        conf.setTlsRequireTrustedClientCertOnConnect(true);
        Set<String> tlsProtocols = Sets.newConcurrentHashSet();
        tlsProtocols.add("TLSv1.3");
        tlsProtocols.add("TLSv1.2");
        conf.setTlsProtocols(tlsProtocols);
        conf.setNumExecutorThreadPoolSize(5);
    }

    protected void internalSetUpForClient(boolean addCertificates, String lookupUrl) throws Exception {
        if (pulsarClient != null) {
            pulsarClient.close();
        }
        ClientBuilder clientBuilder = PulsarClient.builder().serviceUrl(lookupUrl)
                .tlsTrustCertsFilePath(CA_CERT_FILE_PATH).enableTls(true).allowTlsInsecureConnection(false)
                .operationTimeout(1000, TimeUnit.MILLISECONDS);
        if (addCertificates) {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("tlsCertFile", getTlsFileForClient("admin.cert"));
            authParams.put("tlsKeyFile", getTlsFileForClient("admin.key-pk8"));
            clientBuilder.authentication(AuthenticationTls.class.getName(), authParams);
        }
        replacePulsarClient(clientBuilder);
    }

    protected void internalSetUpForNamespace() throws Exception {
        Map<String, String> authParams = new HashMap<>();
        authParams.put("tlsCertFile", getTlsFileForClient("admin.cert"));
        authParams.put("tlsKeyFile", getTlsFileForClient("admin.key-pk8"));

        closeAdmin();
        admin = spy(PulsarAdmin.builder().serviceHttpUrl(brokerUrlTls.toString())
                .tlsTrustCertsFilePath(CA_CERT_FILE_PATH).allowTlsInsecureConnection(false)
                .authentication(AuthenticationTls.class.getName(), authParams).build());
        admin.clusters().createCluster(clusterName,
                ClusterData.builder()
                        .serviceUrl(brokerUrl.toString())
                        .serviceUrlTls(brokerUrlTls.toString())
                        .brokerServiceUrl(pulsar.getBrokerServiceUrl())
                        .brokerServiceUrlTls(pulsar.getBrokerServiceUrlTls())
                        .build());
        admin.tenants().createTenant("my-property",
                new TenantInfoImpl(Sets.newHashSet("appid1", "appid2"), Sets.newHashSet("use")));
        admin.namespaces().createNamespace("my-property/my-ns");
    }
}
