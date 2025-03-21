/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.netty.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.http.ssl.SslConfigurationException;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.util.Arrays;
import java.util.Optional;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a client that
 * supports SSL.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Internal
@BootstrapContextCompatible
public final class NettyClientSslBuilder extends SslBuilder<SslContext> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyClientSslBuilder.class);

    /**
     * @param resourceResolver The resource resolver
     */
    public NettyClientSslBuilder(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public Optional<SslContext> build(SslConfiguration ssl) {
        return build(ssl, HttpVersion.HTTP_1_1);
    }

    @Override
    public Optional<SslContext> build(SslConfiguration ssl, HttpVersion httpVersion) {
        return Optional.ofNullable(build(ssl, HttpVersionSelection.forLegacyVersion(httpVersion)));
    }

    @Nullable
    public SslContext build(SslConfiguration ssl, HttpVersionSelection versionSelection) {
        if (!ssl.isEnabled()) {
            return null;
        }
        SslContextBuilder sslBuilder = SslContextBuilder
            .forClient()
            .keyManager(getKeyManagerFactory(ssl))
            .trustManager(getTrustManagerFactory(ssl));
        if (ssl.getProtocols().isPresent()) {
            sslBuilder.protocols(ssl.getProtocols().get());
        }
        if (ssl.getCiphers().isPresent()) {
            sslBuilder = sslBuilder.ciphers(Arrays.asList(ssl.getCiphers().get()));
        } else if (versionSelection.isHttp2CipherSuites()) {
            sslBuilder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE);
        }
        if (ssl.getClientAuthentication().isPresent()) {
            ClientAuthentication clientAuth = ssl.getClientAuthentication().get();
            if (clientAuth == ClientAuthentication.NEED) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.REQUIRE);
            } else if (clientAuth == ClientAuthentication.WANT) {
                sslBuilder = sslBuilder.clientAuth(ClientAuth.OPTIONAL);
            }
        }
        if (versionSelection.isAlpn()) {
            SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
            sslBuilder.sslProvider(provider);
            sslBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                versionSelection.getAlpnSupportedProtocols()
            ));
        }

        try {
            return sslBuilder.build();
        } catch (SSLException ex) {
            throw new SslConfigurationException("An error occurred while setting up SSL", ex);
        }
    }

    @Override
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            if (this.getKeyStore(ssl).isPresent()) {
                return super.getKeyManagerFactory(ssl);
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    @Override
    protected TrustManagerFactory getTrustManagerFactory(SslConfiguration ssl) {
        try {
            if (this.getTrustStore(ssl).isPresent()) {
                return super.getTrustManagerFactory(ssl);
            } else {
                if (ssl instanceof AbstractClientSslConfiguration && ((AbstractClientSslConfiguration) ssl).isInsecureTrustAllCertificates()) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("HTTP Client is configured to trust all certificates ('insecure-trust-all-certificates' is set to true). Trusting all certificates is not secure and should not be used in production.");
                    }
                    return InsecureTrustManagerFactory.INSTANCE;
                } else {
                    // netty will use the JDK trust store
                    return null;
                }
            }
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }
}
