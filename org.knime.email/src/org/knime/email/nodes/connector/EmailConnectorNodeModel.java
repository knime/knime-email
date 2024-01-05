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
 *   27 Sep 2023 (Tobias): created
 */
package org.knime.email.nodes.connector;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.message.Message;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.email.nodes.connector.EmailConnectorSettings.ConnectionProperties;
import org.knime.email.port.EmailSessionPortObject;
import org.knime.email.port.EmailSessionPortObjectSpec;
import org.knime.email.session.EmailSessionCache;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.session.EmailSessionKey.OptionalBuilder;

/**
 * Node model implementation which provides a generic email connector where the user has to specify all
 * connection details e.g. host and port.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class EmailConnectorNodeModel extends WebUINodeModel<EmailConnectorSettings> {

    private UUID m_cacheId;

    /**
     * Constructor.
     * @param configuration
     */
    protected EmailConnectorNodeModel(final WebUINodeConfiguration configuration) {
        super(configuration, EmailConnectorSettings.class);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs, final EmailConnectorSettings modelSettings)
        throws InvalidSettingsException {
        modelSettings.validate();
        return new PortObjectSpec[]{new EmailSessionPortObjectSpec()};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec,
        final EmailConnectorSettings modelSettings) throws Exception {
        final var mailSessionKey = createKey(modelSettings);
        //test incoming if set...
        if (mailSessionKey.incomingAvailable()) {
            exec.setMessage("Validating incoming mail server settings...");
            try (final var mailSession = mailSessionKey.connectIncoming()) {
                // try connect
            }
        }
        //...and/or outgoing if set
        if (mailSessionKey.outgoingAvailable()) {
            exec.setMessage("Validating outgoing mail server settings...");
            try (final var mailSession = mailSessionKey.connectOutgoing()) {
                // try connect
            }
        }
        m_cacheId = EmailSessionCache.store(mailSessionKey);
        return new PortObject[]{new EmailSessionPortObject(m_cacheId)};
    }

    @Override
    protected void validateSettings(final EmailConnectorSettings settings) throws InvalidSettingsException {
        settings.validate();
    }

    private static final EmailSessionKey createKey(final EmailConnectorSettings settings) {
        final OptionalBuilder optinalBuilder;
        switch (settings.m_type) {
            case INCOMING:
                optinalBuilder = EmailSessionKey.builder().withImap(b -> b //
                    .imapHost(settings.m_imapServer, settings.m_imapPort) //
                    .imapSecureConnection(settings.m_imapUseSecureProtocol)); //
                break;
            case OUTGOING:
                optinalBuilder = EmailSessionKey.builder().withSmtp(b -> b //
                    .smtpHost(settings.m_smtpHost, settings.m_smtpPort) //
                    .smtpEmailAddress(settings.m_smtpEmailAddress) //
                    .security(settings.m_smtpSecurity.toSmtpConnectionSecurity()));
                break;
            case INCOMING_OUTGOING:
                optinalBuilder = EmailSessionKey.builder().withImap(b -> b //
                    .imapHost(settings.m_imapServer, settings.m_imapPort) //
                    .imapSecureConnection(settings.m_imapUseSecureProtocol)).withSmtp(b -> b //
                        .smtpHost(settings.m_smtpHost, settings.m_smtpPort) //
                        .smtpEmailAddress(settings.m_smtpEmailAddress) //
                        .security(settings.m_smtpSecurity.toSmtpConnectionSecurity())); //
                break;
            default:
                throw new IllegalStateException("Unknown email connection type");
        }

        return optinalBuilder.withAuth(settings.m_login.getUsername(), settings.m_login.getPassword()) //
            .withTimeouts(settings.m_connectTimeout, settings.m_readTimeout) //
            .withProperties(extractProperties(settings.m_properties)) //
            .build();
    }

    private static Properties extractProperties(final ConnectionProperties[] properties) {
        final var prop = new Properties();
        for (ConnectionProperties property : properties) {
            prop.put(property.m_name, property.m_value);
        }
        return prop;
    }

    @Override
    protected void onLoadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        setWarning(Message.builder().withSummary("Email session is invalid.")
            .addResolutions("Re-execute the node to restore the email session.").build().orElseThrow());
    }

    @Override
    protected void onDispose() {
        removeFromCache();
    }

    @Override
    protected void onReset() {
        removeFromCache();
    }

    private void removeFromCache() {
        if (m_cacheId != null) {
            EmailSessionCache.delete(m_cacheId);
            m_cacheId = null;
        }
    }
}
