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

import java.util.List;
import java.util.Properties;

import org.knime.core.node.util.CheckUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;

/**
 * {@link EmailSession} provider that establishes a connection to the {@link EmailSession} when necessary.
 * Retrieved {@link EmailSession} should be closed once the processing is done!
 * @author wiswedel
 */
public final class EmailSessionKey {

    private final String m_host;
    private final int m_port;
    private final String m_user;
    private final String m_password;
    private final String m_protocol;
    private final boolean m_useSecurePortocol;
    private Properties m_properties;

    private EmailSessionKey(final Builder builder) {
        m_host = builder.m_host;
        m_port = builder.m_port;
        m_user = builder.m_user;
        m_password = builder.m_password;
        m_protocol = builder.m_protocol;
        m_useSecurePortocol = builder.m_useSecureProtocol;
        m_properties = builder.m_properties;
    }

    String getHost() {
        return m_host;
    }

    int getPort() {
        return m_port;
    }

    String getUser() {
        return m_user;
    }

    String getPassword() {
        return m_password;
    }

    String getProtocol() {
        return m_protocol;
    }

    boolean isUseSecurePortocol() {
        return m_useSecurePortocol;
    }

    /**
     * @return the properties
     */
    public Properties getProperties() {
        return m_properties;
    }

    public static WithHost builder() {
        return new Builder();
    }

    /**
     * Connects to the underlying email store and returns the session to work with it. Please close the session
     * once the work  is done!
     * @return a new {@link EmailSession} which should be closed when done
     * @throws MessagingException if the connection fails
     */
    @SuppressWarnings("resource")
    public EmailSession connect() throws MessagingException {
        CheckUtils.checkArgument(List.of("imap", "pop3").contains(m_protocol), "Invalid protocol: %s", m_protocol);
        final var protocol = m_useSecurePortocol ? m_protocol.concat("s") : m_protocol;
        final var props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", m_host);
        props.put("mail." + protocol + ".port", m_port);
        //use the user settings last to allow for more flexibility by allowing users to overwrite our standard settings
        props.putAll(m_properties);
        EmailSession.LOGGER.debugWithFormat("Connecting email client to %s:%d via %s using following properties %s",
            m_host, m_port, protocol, m_properties);
        final var emailSession = Session.getInstance(props);
        final var emailStore = emailSession.getStore();
        try {
            emailStore.connect(m_user, m_password);
        } catch (MessagingException me) {
            emailStore.close();
            throw me;
        }
        return new EmailSession(emailStore);
    }

    @FunctionalInterface
    public interface WithHost {
        WithUser host(final String host, final int port);
    }

    @FunctionalInterface
    public interface WithUser {
        WithProtocol user(final String user, final String password);
    }

    @FunctionalInterface
    public interface WithProtocol {
        WithProperties protocol(final String protocol, final boolean isUseSecure);
    }

    @FunctionalInterface
    public interface WithProperties {
        FinalStageBuilder properties(final Properties properties);
    }

    public interface FinalStageBuilder {

        EmailSessionKey build();
    }

    private static final class Builder implements WithHost, WithUser, WithProtocol, WithProperties, FinalStageBuilder {

        private String m_host;
        private int m_port;
        private String m_user;
        private String m_password;
        private String m_protocol;
        private boolean m_useSecureProtocol;
        private Properties m_properties;


        @Override
        public WithUser host(final String host, final int port) {
            m_host = host;
            m_port = port;
            return this;
        }

        @Override
        public WithProtocol user(final String user, final String password) {
            m_user = user;
            m_password = password;
            return this;
        }

        @Override
        public WithProperties protocol(final String protocol, final boolean isUseSecure) {
            m_protocol = protocol;
            m_useSecureProtocol = isUseSecure;
            return this;
        }

        @Override
        public FinalStageBuilder properties(final Properties properties) {
            m_properties = properties;
            return this;
        }

        @Override
        public EmailSessionKey build() {
            return new EmailSessionKey(this);
        }
    }
}