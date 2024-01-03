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

import java.util.Properties;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.util.CheckUtils;

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
    @SuppressWarnings("resource")
    public EmailIncomingSession connectIncoming() throws MessagingException {
        final var protocol = m_imapUseSecurePortocol ? "imaps" : "imap";
        final var props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", m_imapHost);
        props.put("mail." + protocol + ".port", m_imapPort);
        //use the user settings last to allow for more flexibility by allowing users to overwrite our standard settings
        props.putAll(m_properties);
        EmailIncomingSession.LOGGER.debugWithFormat("Connecting email client to %s:%d via %s using following properties %s",
            m_imapHost, m_imapPort, protocol, m_properties);

        final Thread t = Thread.currentThread();
        final ClassLoader orig = t.getContextClassLoader();
        //use the ClassLoader of the Session.class from the email libs project which contains the meta_inf with the
        //service definitions
        t.setContextClassLoader(Session.class.getClassLoader());
        try {
            final var emailSession = Session.getInstance(props);
            final var emailStore = emailSession.getStore();
            try {
                emailStore.connect(m_user, m_password);
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
    @SuppressWarnings("resource")
    public EmailOutgoingSession connectOutgoing() throws MessagingException {
        //TODO: implement
        return new EmailOutgoingSession(null);
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
        public Builder withProperties(final Properties properties) {
            m_properties = properties;
            return this;
        }

        @Override
        public EmailSessionKey build() {
            CheckUtils.checkArgument(StringUtils.isNotBlank(m_imapHost) && StringUtils.isNotBlank(m_smtpHost),
                "One of SMTP or IMAP connection must be specified");
            return new EmailSessionKey(this);
        }

    }
}