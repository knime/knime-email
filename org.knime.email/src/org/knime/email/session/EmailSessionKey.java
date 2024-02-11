/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Sep 27, 2023 (wiswedel): created
 */
package org.knime.email.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.util.CheckUtils;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;

import com.google.common.collect.ImmutableMap;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;

/**
 * {@link EmailIncomingSession} provider that establishes a connection to the {@link EmailIncomingSession} when necessary.
 * Retrieved {@link EmailIncomingSession} should be closed once the processing is done!
 *
 * @author Bernd Wiswedel
 */
@SuppressWarnings("javadoc")
public final class EmailSessionKey {

    /**
     * Default connect timeout in seconds. Default is {@value #DEF_TIMEOUT_CONNECT_S}.
     */
    public static final int DEF_TIMEOUT_CONNECT_S = 2;
    /**
     * Default read timeout in seconds. Default is {@value #DEF_TIMEOUT_READ_S}.
     */
    public static final int DEF_TIMEOUT_READ_S = 10;

    /** SMTP Connection Security as specified in the builder. */
    public enum SmtpConnectionSecurity {
        NONE,
        SSL,
        STARTTLS
    }

    enum AuthType {
        USERNAME_PASSWORD,
        OAUTH,
        NONE;
    }

    private final String m_imapHost;
    private final int m_imapPort;
    private final boolean m_imapUseSecurePortocol;

    private final String m_smtpHost;
    private final int m_smtpPort;
    private final Optional<String> m_smtpEmailAddress;
    private final SmtpConnectionSecurity m_smtpConnectionSecurity;

    private final int m_connectTimeoutS;
    private final int m_readTimeoutS;
    private Properties m_properties;


    private final AuthType m_authType;
    private final String m_user;
    private final String m_password;
    private AccessTokenAccessor m_tokenAccessor;

    private EmailSessionKey(final EmailSessionKeyBuilder builder) {
        m_imapHost = builder.m_imapHost;
        m_imapPort = builder.m_imapPort;
        m_imapUseSecurePortocol = builder.m_imapUseSecureProtocol;

        m_smtpHost = builder.m_smtpHost;
        m_smtpPort = builder.m_smtpPort;
        if (StringUtils.isBlank(builder.m_smtpEmailAddress)) {
            m_smtpEmailAddress = Optional.empty();
        } else {
            m_smtpEmailAddress = Optional.of(builder.m_smtpEmailAddress);
        }
        m_smtpConnectionSecurity = builder.m_smtpConnectionSecurity;

        m_authType = builder.m_authType;
        m_user = builder.m_user;
        m_password = builder.m_password;
        m_tokenAccessor = builder.m_accessor;

        m_connectTimeoutS = builder.m_connectTimeoutS;
        m_readTimeoutS = builder.m_readTimeoutS;
        m_properties = builder.m_properties;
    }

    public static Builder builder() {
        return new EmailSessionKeyBuilder();
    }

    /**
     * Specifies if an incoming connection is available.
     * @return <code>true</code> if an incoming connection is available
     */
    public boolean incomingAvailable() {
        return StringUtils.isNotBlank(m_imapHost);
    }

    /**
     * Connects to the underlying email store and returns the incoming session to work with it.
     * Please close the session once the work is done!
     * @return a new {@link EmailIncomingSession} which should be closed when done
     * @throws MessagingException if the connection fails
     * @see #incomingAvailable()
     */
    @SuppressWarnings({"resource", "java:S1192"}) // java:S1192 - string duplication of "mail."
    public EmailIncomingSession connectIncoming() throws MessagingException {
        if (!incomingAvailable()) {
            throw new MessagingException("No incoming server settings available");
        }
        final var protocol = m_imapUseSecurePortocol ? "imaps" : "imap";
        final var properties = new Properties();
        properties.put("mail.store.protocol", protocol);
        properties.put("mail." + protocol + ".host", m_imapHost);
        properties.put("mail." + protocol + ".port", m_imapPort);
        properties.put("mail." + protocol + ".connectiontimeout", String.valueOf(1000 * m_connectTimeoutS));
        properties.put("mail." + protocol + ".timeout", String.valueOf(1000 * m_readTimeoutS));

        //use the user settings last to allow for more flexibility by allowing users to overwrite our standard settings
        properties.putAll(m_properties);
        EmailIncomingSession.LOGGER.debugWithFormat(
            "Connecting email client to %s:%d via %s using following properties %s", m_imapHost, m_imapPort, protocol,
            m_properties);

        final Session emailSession;
        Store emailStore = null;
        try {
            switch (m_authType) {
                case NONE:
                    emailSession = Session.getInstance(properties);
                    emailStore = emailSession.getStore();
                    emailStore.connect();
                    break;
                case OAUTH:
                    properties.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
                    emailSession = Session.getInstance(properties);
                    emailStore = emailSession.getStore();
                    emailStore.connect(m_user, m_tokenAccessor.getAccessToken());
                    break;
                case USERNAME_PASSWORD:
                    emailSession = Session.getInstance(properties);
                    emailStore = emailSession.getStore();
                    emailStore.connect(m_user, m_password);
                    break;
                default:
                    throw new IllegalStateException(m_authType + " not implemented");
            }
            return new EmailIncomingSession(emailStore);
        } catch (MessagingException | IOException me) {
            if (emailStore != null) {
                try {
                    emailStore.close();
                } catch (final Exception e) {
                    EmailIncomingSession.LOGGER.warn("Failed to close email store", e);
                }
            }
            if (me instanceof MessagingException) {
                throw (MessagingException)me;
            }
            throw new MessagingException(
                "Failed to connect to Email server " + m_imapHost + ":" + m_imapPort, me);
        }
    }

    /**
     * Specifies if an outgoing connection is available.
     * @return <code>true</code> if an outgoing connection is available
     */
    public boolean outgoingAvailable() {
        return StringUtils.isNotBlank(m_smtpHost);
    }

    /**
     * Connects to the underlying email transport and returns the outgoing session to work with it.
     * Please close the session once the work is done!
     * @return a new {@link EmailIncomingSession} which should be closed when done
     * @throws MessagingException if the connection fails
     * @see #outgoingAvailable()
     */
    @SuppressWarnings({"resource", "java:S1192"}) // java:S1192 - string duplication of "mail."
    public EmailOutgoingSession connectOutgoing() throws MessagingException {
        if (!outgoingAvailable()) {
            throw new MessagingException("No outgoing server settings available");
        }
        CheckUtils.checkState(StringUtils.isNotBlank(m_smtpHost), "No outgoing server (smtp) specified");
        final var properties = new Properties();
        var protocol = "smtp";
        switch (m_smtpConnectionSecurity) {
            case NONE:
                break;
            case STARTTLS:
                properties.setProperty("mail.smtp.starttls.enable", "true");
                // we need to require STARTTLS as well to enforce a TLS connection -- fixes AP-19571
                properties.setProperty("mail.smtp.starttls.required", "true");
                break;
            case SSL:
                // this is the way to do it in javax.mail 1.4.5+ (default is currently (Aug '13) 1.4.0):
                // www.oracle.com/technetwork/java/javamail145sslnotes-1562622.html
                // 'First, and perhaps the simplest, is to set a property to enable use
                // of SSL.  For example, to enable use of SSL for SMTP connections, set
                // the property "mail.smtp.ssl.enable" to "true".'
                properties.setProperty("mail.smtp.ssl.enable", "true");
                // this is an alternative/backup, which works also:
                // http://javakiss.blogspot.ch/2010/10/smtp-in-java-with-javaxmail.html
                // I verify it's actually using SSL:
                //  - it hid a breakpoint in sun.security.ssl.SSLSocketFactoryImpl
                //  - Hostpoint (knime.com mail server) is rejecting any smtp request on their ssl port (465)
                //    without this property
                properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                // a third (and most transparent) option would be to use a different protocol:
                protocol = "smtps";
                break;
            default:
                throw new IllegalStateException("unsupported connection security: " + m_smtpConnectionSecurity);
        }

        properties.setProperty("mail.transport.protocol", protocol);
        // only support secure versions of TLS -- changed with AP-19572
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        properties.setProperty("mail." + protocol + ".host", m_smtpHost);
        properties.setProperty("mail." + protocol + ".port", Integer.toString(m_smtpPort));
        properties.setProperty("mail." + protocol + ".auth", Boolean.toString(AuthType.NONE == m_authType));
        properties.setProperty("mail." + protocol + ".connectiontimeout", String.valueOf(1000 * m_connectTimeoutS));
        properties.setProperty("mail." + protocol + ".timeout", String.valueOf(1000 * m_readTimeoutS));

        //use the user settings last to allow for more flexibility by allowing users to overwrite our standard settings
        properties.putAll(m_properties);

        final Session session;
        Transport transport = null;
        try {
            switch (m_authType) {
                case NONE:
                    session = Session.getInstance(properties);
                    transport = session.getTransport();
                    transport.connect();
                    break;
                case OAUTH:
                    properties.put("mail." + protocol + ".auth.mechanisms", "XOAUTH2");
                    session = Session.getInstance(properties);
                    transport = session.getTransport();
                    transport.connect(m_user, m_tokenAccessor.getAccessToken());
                    break;
                case USERNAME_PASSWORD:
                    session = Session.getInstance(properties);
                    transport = session.getTransport();
                    transport.connect(m_user, m_password);
                    break;
                default:
                    throw new IllegalStateException(m_authType + " not implemented");
            }
            return new EmailOutgoingSession(session, transport, m_smtpEmailAddress);
        } catch (MessagingException | IOException me) {
            if (transport != null) {
                transport.close();
            }
            if (me instanceof MessagingException) {
                throw (MessagingException)me;
            }
            throw new MessagingException("Failed to connect to Email server " + m_smtpHost + ":" + m_smtpPort, me);
        }
    }

    /** An optional titled map, return type of {@link EmailSessionKey#toViewContent()}. */
    public record ViewContentSection(String header, Optional<Map<String, String>> properties) {}

    /** Provides the content of the port view in a collection of named maps. Not to be called by clients. */
    public Collection<ViewContentSection> toViewContent() {
        List<ViewContentSection> result = new ArrayList<>();
        Map<String, String> imapPropMap = null;
        if (incomingAvailable()) {
            imapPropMap = ImmutableMap.of( // NOSONAR, guava order preserving
                "IMAP Host", m_imapHost, //
                "IMAP Port", Integer.toString(m_imapPort), //
                "IMAP Secure Connection", Boolean.toString(m_imapUseSecurePortocol));
        }
        result.add(new ViewContentSection("Incoming (IMAP)", Optional.ofNullable(imapPropMap)));

        Map<String, String> smtpPropMap = null;
        if (outgoingAvailable()) {
            smtpPropMap = ImmutableMap.of( // NOSONAR, guava order preserving
                "SMTP Host", m_smtpHost, //
                "SMTP Port", Integer.toString(m_smtpPort), //
                "SMTP EMail Address", m_smtpEmailAddress.orElse(""), //
                "SMTP Security", m_smtpConnectionSecurity.name());
        }
        result.add(new ViewContentSection("Outgoing (SMTP)", Optional.ofNullable(smtpPropMap)));

        Map<String, String> authPropMap = null;
        if (StringUtils.isNotBlank(m_user)) {
            authPropMap = ImmutableMap.of( // NOSONAR, guava order preserving
                "User", m_user, //
                "Password", StringUtils.isEmpty(m_password) ? "<none>" : StringUtils.repeat('\u2022', 5));
        }
        result.add(new ViewContentSection("Authentication", Optional.ofNullable(authPropMap)));

        Map<String, String> propertiesPropMap = null;
        if (m_properties != null && !m_properties.isEmpty()) {
            propertiesPropMap = m_properties.entrySet().stream() //
                .collect(Collectors.toMap(Objects::toString, Objects::toString, (s1, s2) -> s1, LinkedHashMap::new));
        }
        result.add(new ViewContentSection("Additional Properties", Optional.ofNullable(propertiesPropMap)));

        return result;
    }

    /**
     * Builder for a session, as returned by {@link EmailSessionKey#builder()}. Method names should be self-explanatory.
     * One of imap or smtp server must be specified (though both are optional).
     */
    public sealed interface Builder permits EmailSessionKeyBuilder {

        WithImapFinalBuilder withImap(Function<WithImapBaseBuilder, WithImapFinalBuilder> builderFunction);

        WithSmtpFinalBuilder withSmtp(Function<WithSmtpBaseBuilder, WithSmtpFinalBuilder> builderFunction);

    }

    public sealed interface OptionalBuilder permits WithImapFinalBuilder, WithSmtpFinalBuilder {
        OptionalBuilder withProperties(Properties properties);

        OptionalBuilder withAuth(String user, String password);

        OptionalBuilder withOAuth(String user, AccessTokenAccessor accessor);

        OptionalBuilder withTimeouts(int connectTimeoutS, int readTimeoutS);

        EmailSessionKey build();
    }

    public sealed interface WithImapBaseBuilder permits EmailSessionKeyBuilder {
        WithImapProtocolBuilder imapHost(final String host, final int port);
    }

    public sealed interface WithImapProtocolBuilder permits EmailSessionKeyBuilder {
        WithImapFinalBuilder imapSecureConnection(final boolean isUseSecure);
    }

    public sealed interface WithImapFinalBuilder extends OptionalBuilder permits EmailSessionKeyBuilder {

        WithSmtpFinalBuilder withSmtp(Function<WithSmtpBaseBuilder, WithSmtpFinalBuilder> builderFunction);

    }

    public sealed interface WithSmtpBaseBuilder permits EmailSessionKeyBuilder {
        WithSmtpEMailAddressBuilder smtpHost(final String host, final int port);
    }

    public sealed interface WithSmtpEMailAddressBuilder permits EmailSessionKeyBuilder {
        WithSmtpSecurityBuilder smtpEmailAddress(final String email);
    }

    public sealed interface WithSmtpSecurityBuilder permits EmailSessionKeyBuilder {
        WithSmtpFinalBuilder security(SmtpConnectionSecurity security);
    }

    public sealed interface WithSmtpFinalBuilder extends OptionalBuilder permits EmailSessionKeyBuilder {

        WithImapFinalBuilder withImap(Function<WithImapBaseBuilder, WithImapFinalBuilder> builderFunction);

    }

    private static final class EmailSessionKeyBuilder
        implements Builder, WithImapBaseBuilder, WithImapFinalBuilder, WithImapProtocolBuilder,
        WithSmtpBaseBuilder, WithSmtpEMailAddressBuilder, WithSmtpSecurityBuilder, WithSmtpFinalBuilder {

        private String m_imapHost;
        private int m_imapPort;
        private boolean m_imapUseSecureProtocol;

        private String m_smtpHost;
        private int m_smtpPort;
        private String m_smtpEmailAddress;
        private SmtpConnectionSecurity m_smtpConnectionSecurity;

        private AuthType m_authType = AuthType.NONE;
        private String m_user;
        private String m_password;
        private AccessTokenAccessor m_accessor;

        private int m_readTimeoutS = DEF_TIMEOUT_READ_S;
        private int m_connectTimeoutS = DEF_TIMEOUT_CONNECT_S;
        private Properties m_properties;

        @Override
        public WithImapFinalBuilder
            withImap(final Function<WithImapBaseBuilder, WithImapFinalBuilder> builderFunction) {
            return builderFunction.apply(this);
        }

        @Override
        public WithImapProtocolBuilder imapHost(final String host, final int port) {
            CheckUtils.checkArgument(StringUtils.isNotBlank(host), "IMAP Server parameter must not be blank");
            checkPortRange(port);
            m_imapHost = host;
            m_imapPort = port;
            return this;
        }

        private static void checkPortRange(final int port) {
            CheckUtils.checkArgument(port > 0 && port <= 0xFFFF, "Server port out of range [0, %d]: %d", 0xFFFF, port);
        }

        @Override
        public WithImapFinalBuilder imapSecureConnection(final boolean isUseSecure) {
            m_imapUseSecureProtocol = isUseSecure;
            return this;
        }

        @Override
        public WithSmtpFinalBuilder
            withSmtp(final Function<WithSmtpBaseBuilder, WithSmtpFinalBuilder> builderFunction) {
            return builderFunction.apply(this);
        }

        @Override
        public WithSmtpEMailAddressBuilder smtpHost(final String host, final int port) {
            CheckUtils.checkArgument(StringUtils.isNotBlank(host), "SMTP Server parameter must not be blank");
            checkPortRange(port);
            m_smtpHost = host;
            m_smtpPort = port;
            return this;
        }

        @Override
        public WithSmtpSecurityBuilder smtpEmailAddress(final String email) {
            m_smtpEmailAddress = email;
            return this;
        }

        @Override
        public WithSmtpFinalBuilder security(final SmtpConnectionSecurity security) {
            m_smtpConnectionSecurity = CheckUtils.checkArgumentNotNull(security);
            return this;
        }

        @Override
        public OptionalBuilder withAuth(final String user, final String password) {
            if (StringUtils.isNotBlank(user)) {
                m_authType = AuthType.USERNAME_PASSWORD;
            } else {
                m_authType = AuthType.NONE;
            }
            m_user = user;
            m_password = password;
            return this;
        }

        @Override
        public OptionalBuilder withOAuth(final String user, final AccessTokenAccessor accessor) {
            m_authType = AuthType.OAUTH;
            m_user = user;
            m_accessor = accessor;
            return this;
        }

        @Override
        public OptionalBuilder withTimeouts(final int connectTimeoutS, final int readTimeoutS) {
            m_connectTimeoutS = connectTimeoutS;
            m_readTimeoutS = readTimeoutS;
            return this;
        }

        @Override
        public OptionalBuilder withProperties(final Properties properties) {
            m_properties = properties;
            return this;
        }

        @Override
        public EmailSessionKey build() {
            return new EmailSessionKey(this);
        }

    }
}

