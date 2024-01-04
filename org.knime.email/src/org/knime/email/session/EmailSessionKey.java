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

import com.google.common.collect.ImmutableMap;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;

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

    private final String m_imapHost;
    private final int m_imapPort;
    private final boolean m_imapUseSecurePortocol;

    private final String m_smtpHost;
    private final int m_smtpPort;
    private final String m_smtpEmailAddress;
    private final SmtpConnectionSecurity m_smtpConnectionSecurity;

    private final int m_connectTimeoutS;
    private final int m_readTimeoutS;
    private Properties m_properties;

    private final String m_user;
    private final String m_password;

    private EmailSessionKey(final EmailSessionKeyBuilder builder) {
        m_imapHost = builder.m_imapHost;
        m_imapPort = builder.m_imapPort;
        m_imapUseSecurePortocol = builder.m_imapUseSecureProtocol;

        m_smtpHost = builder.m_smtpHost;
        m_smtpPort = builder.m_smtpPort;
        m_smtpEmailAddress = builder.m_smtpEmailAddress;
        m_smtpConnectionSecurity = builder.m_smtpConnectionSecurity;

        m_user = builder.m_user;
        m_password = builder.m_password;

        m_connectTimeoutS = builder.m_connectTimeoutS;
        m_readTimeoutS = builder.m_readTimeoutS;
        m_properties = builder.m_properties;
    }

    public static Builder builder() {
        return new EmailSessionKeyBuilder();
    }

    /**
     * Connects to the underlying email store and returns the incoming session to work with it.
     * Please close the session once the work is done!
     * @return a new {@link EmailIncomingSession} which should be closed when done
     * @throws MessagingException if the connection fails
     */
    @SuppressWarnings({"resource", "java:S1192"}) // java:S1192 - string duplication of "mail."
    public EmailIncomingSession connectIncoming() throws MessagingException {
        final var protocol = m_imapUseSecurePortocol ? "imaps" : "imap";
        final var props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", m_imapHost);
        props.put("mail." + protocol + ".port", m_imapPort);
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(1000 * m_connectTimeoutS));
        props.put("mail." + protocol + ".timeout", String.valueOf(1000 * m_readTimeoutS));
        //use the user settings last to allow for more flexibility by allowing users to overwrite our standard settings
        props.putAll(m_properties);
        EmailIncomingSession.LOGGER.debugWithFormat(
            "Connecting email client to %s:%d via %s using following properties %s", m_imapHost, m_imapPort, protocol,
            m_properties);

        final Thread t = Thread.currentThread();
        final ClassLoader orig = t.getContextClassLoader();
        //use the ClassLoader of the Session.class from the email libs project which contains the meta_inf with the
        //service definitions
        t.setContextClassLoader(Session.class.getClassLoader());
        try {
            final var emailSession = Session.getInstance(props);
            final var emailStore = emailSession.getStore();
            try {
                final boolean isRequireAuth = StringUtils.isNotBlank(m_user);
                if (isRequireAuth) {
                    emailStore.connect(m_user, m_password);
                } else {
                    emailStore.connect();
                }
                return new EmailIncomingSession(emailStore);
            } catch (MessagingException me) {
                emailStore.close();
                throw me;
            }
        } finally {
            t.setContextClassLoader(orig);
        }
    }

    /**
     * Connects to the underlying email transport and returns the outgoing session to work with it.
     * Please close the session once the work is done!
     * @return a new {@link EmailIncomingSession} which should be closed when done
     * @throws MessagingException if the connection fails
     */
    @SuppressWarnings({"resource", "java:S1192"}) // java:S1192 - string duplication of "mail."
    public EmailOutgoingSession connectOutgoing() throws MessagingException {
        final var oldContextClassloader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Session.class.getClassLoader());
        try {
            CheckUtils.checkState(StringUtils.isNotBlank(m_smtpHost), "No outgoing server (smtp) specified");
            final var properties = new Properties(m_properties);
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
            final boolean isRequireAuth = StringUtils.isNotBlank(m_user);

            properties.setProperty("mail.transport.protocol", protocol);
            // only support secure versions of TLS -- changed with AP-19572
            properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            properties.setProperty("mail." + protocol + ".host", m_smtpHost);
            properties.setProperty("mail." + protocol + ".port", Integer.toString(m_smtpPort));
            properties.setProperty("mail." + protocol + ".auth", Boolean.toString(isRequireAuth));
            properties.setProperty("mail." + protocol + ".connectiontimeout", String.valueOf(1000 * m_connectTimeoutS));
            properties.setProperty("mail." + protocol + ".timeout", String.valueOf(1000 * m_readTimeoutS));
            final var session = Session.getInstance(properties);
            final var transport = session.getTransport();
            if (isRequireAuth) {
                transport.connect(m_user, m_password);
            } else {
                transport.connect();
            }
            return new EmailOutgoingSession(transport);
        } finally {
            Thread.currentThread().setContextClassLoader(oldContextClassloader);
        }
    }

    /** An optional titled map, return type of {@link EmailSessionKey#toViewContent()}. */
    public record ViewContentSection(String header, Optional<Map<String, String>> properties) {}

    /** Provides the content of the port view in a collection of named maps. Not to be called by clients. */
    public Collection<ViewContentSection> toViewContent() {
        List<ViewContentSection> result = new ArrayList<>();
        Map<String, String> imapPropMap = null;
        if (StringUtils.isNotBlank(m_imapHost)) {
            imapPropMap = ImmutableMap.of( // NOSONAR, guava order preserving
                "IMAP Host", m_imapHost, //
                "IMAP Port", Integer.toString(m_imapPort), //
                "IMAP Secure Connection", Boolean.toString(m_imapUseSecurePortocol));
        }
        result.add(new ViewContentSection("Incoming (IMAP)", Optional.ofNullable(imapPropMap)));

        Map<String, String> smtpPropMap = null;
        if (StringUtils.isNotBlank(m_smtpHost)) {
            smtpPropMap = ImmutableMap.of( // NOSONAR, guava order preserving
                "SMTP Host", m_smtpHost, //
                "SMTP Port", Integer.toString(m_smtpPort), //
                "SMTP EMail Address", m_smtpEmailAddress, //
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

        Builder withSmtp(Function<WithSmtpBaseBuilder, WithSmtpFinalBuilder> builderFunction);

        EmailSessionKey build();

        Builder withProperties(Properties properties);

        Builder withAuth(String user, String password);

        Builder withTimeouts(int connectTimeoutS, int readTimeoutS);

        Builder withImap(Function<WithImapBaseBuilder, WithImapFinalBuilder> builderFunction);

    }

    public sealed interface WithImapBaseBuilder permits EmailSessionKeyBuilder {
        WithImapProtocolBuilder imapHost(final String host, final int port);
    }

    public sealed interface WithImapProtocolBuilder permits EmailSessionKeyBuilder {
        WithImapFinalBuilder imapSecureConnection(final boolean isUseSecure);
    }

    public sealed interface WithImapFinalBuilder permits EmailSessionKeyBuilder {

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

    public sealed interface WithSmtpFinalBuilder {

    }

    private static final class EmailSessionKeyBuilder
        implements Builder, WithImapBaseBuilder, WithImapFinalBuilder, WithImapProtocolBuilder, WithSmtpBaseBuilder,
        WithSmtpEMailAddressBuilder, WithSmtpSecurityBuilder, WithSmtpFinalBuilder {

        private String m_imapHost;
        private int m_imapPort;
        private boolean m_imapUseSecureProtocol;

        private String m_smtpHost;
        private int m_smtpPort;
        private String m_smtpEmailAddress;
        private SmtpConnectionSecurity m_smtpConnectionSecurity;

        private String m_user;
        private String m_password;

        private int m_readTimeoutS = DEF_TIMEOUT_READ_S;
        private int m_connectTimeoutS = DEF_TIMEOUT_CONNECT_S;
        private Properties m_properties;

        @Override
        public Builder
            withImap(final Function<WithImapBaseBuilder, WithImapFinalBuilder> builderFunction) {
            builderFunction.apply(this);
            return this;
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
        public Builder
            withSmtp(final Function<WithSmtpBaseBuilder, WithSmtpFinalBuilder> builderFunction) {
            builderFunction.apply(this);
            return this;
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
        public Builder withAuth(final String user, final String password) {
            m_user = user;
            m_password = password;
            return this;
        }

        @Override
        public Builder withTimeouts(final int connectTimeoutS, final int readTimeoutS) {
            m_connectTimeoutS = connectTimeoutS;
            m_readTimeoutS = readTimeoutS;
            return this;
        }

        @Override
        public Builder withProperties(final Properties properties) {
            m_properties = properties;
            return this;
        }

        @Override
        public EmailSessionKey build() {
            CheckUtils.checkArgument(StringUtils.isNotBlank(m_imapHost) || StringUtils.isNotBlank(m_smtpHost),
                "One of SMTP or IMAP connection must be specified");
            return new EmailSessionKey(this);
        }

    }
}

