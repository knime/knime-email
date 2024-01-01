/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */

package org.knime.email.janitor;


import java.util.ArrayList;
import java.util.List;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.workflow.CredentialsStore;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.email.util.EmailUtil;
import org.knime.testing.core.TestrunJanitor;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.imap.ImapServer;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.Session;

/**
 * Email server janitor.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
public class EmailServerJanitor extends TestrunJanitor {


    /** First email user. */
    public static final String USER1 = "knime-1@localhost";

    /** First email user password. */
    public static final String PWD1 = "knime1234";

    /** Second email user. */
    public static final String USER2 = "knime-2@localhost";

    /** Second email login. */
    public static final String PWD2 = "knime1234";

    /** Second email user. */
    public static final String USER3 = "knime-3@localhost";

    /** Second email login. */
    public static final String PWD3 = "knime1234";

    /** {@link GreenMailConfiguration} with the two test users. */
    public static final GreenMailConfiguration CONFIG = new GreenMailConfiguration() //
        .withUser(USER1, PWD1) //
        .withUser(USER2, PWD2) //
        .withUser(USER3, PWD3);

    /** {@link ServerSetup} for SMTP and IMAP tests. */
    public static final ServerSetup[] SETUP = new ServerSetup[]{ //
        ServerSetup.SMTP.dynamicPort().setVerbose(true), //
        ServerSetup.IMAP.dynamicPort().setVerbose(true)};

    private static final String VAR_PREFIX = "email-";

    private GreenMail m_mail;
    private String m_bindAddress;
    private int m_imapPort;
    private int m_smtpPort;

    @Override
    public void after() throws Exception {
        if (m_mail != null) {
            m_mail.stop();
            m_mail = null;
        }
    }

    @Override
    public void before() throws Exception {
        try (final var closeable = EmailUtil.runWithContextClassloader(Session.class)) {
            m_mail = new GreenMail(SETUP);
            m_mail.withConfiguration(CONFIG);
            m_mail.start();
            final ImapServer imap = m_mail.getImap();
            final ServerSetup serverSetup = imap.getServerSetup();
            m_bindAddress = serverSetup.getBindAddress();
            m_imapPort = serverSetup.getPort();
            final var smtp = m_mail.getSmtp();
            m_smtpPort = smtp.getPort();
        }
    }

    @Override
    public String getDescription() {
        return "Test janitor that allows to test email functionality";
    }

    @Override
    public List<FlowVariable> getFlowVariables() {
        final List<FlowVariable> flowVariables = new ArrayList<>();
        try {
            flowVariables.add(new FlowVariable(VAR_PREFIX + "credentials", VAR_PREFIX + "login-1"));
            flowVariables
            .add(CredentialsStore.newCredentialsFlowVariable(VAR_PREFIX + "login-1", USER1, PWD1, false, false));
            flowVariables.add(new FlowVariable(VAR_PREFIX + "user-1", USER1));

            flowVariables.add(new FlowVariable(VAR_PREFIX + "credentials", VAR_PREFIX + "login-2"));
            flowVariables
            .add(CredentialsStore.newCredentialsFlowVariable(VAR_PREFIX + "login-2", USER2, PWD2, false, false));
            flowVariables.add(new FlowVariable(VAR_PREFIX + "user-2", USER2));
            flowVariables.add(new FlowVariable(VAR_PREFIX + "credentials", VAR_PREFIX + "login-3"));
            flowVariables
            .add(CredentialsStore.newCredentialsFlowVariable(VAR_PREFIX + "login-3", USER3, PWD3, false, false));
            flowVariables.add(new FlowVariable(VAR_PREFIX + "user-3", USER3));
        } catch (final InvalidSettingsException e) {
            throw new RuntimeException("Unable to create janitor credentials for email server", e);
        }
        flowVariables.add(new FlowVariable(VAR_PREFIX + "host", m_bindAddress));
        flowVariables.add(new FlowVariable(VAR_PREFIX + "imap-port", m_imapPort));
        flowVariables.add(new FlowVariable(VAR_PREFIX + "smtp-port", m_smtpPort));
        return flowVariables;
    }


    @Override
    public String getID() {
        return this.getName();
    }

    @Override
    public String getName() {
        return "Email server";
    }

}
