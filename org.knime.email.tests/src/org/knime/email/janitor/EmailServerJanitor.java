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

import static org.knime.email.TestUtil.PWD1;
import static org.knime.email.TestUtil.PWD2;
import static org.knime.email.TestUtil.PWD3;
import static org.knime.email.TestUtil.USER1;
import static org.knime.email.TestUtil.USER2;
import static org.knime.email.TestUtil.USER3;

import java.util.ArrayList;
import java.util.List;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.workflow.CredentialsStore;
import org.knime.core.node.workflow.FlowVariable;
import org.knime.email.TestUtil;
import org.knime.testing.core.TestrunJanitor;

import com.icegreen.greenmail.imap.ImapServer;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/**
 * Email server janitor.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
public class EmailServerJanitor extends TestrunJanitor {

    private static final String VAR_PREFIX = "email-";

    private GreenMail m_mail;
    private String m_bindAddress;
    private int m_imapPort;
    private int m_smptPort;



    @Override
    public void after() throws Exception {
        if (m_mail != null) {
            m_mail.stop();
            m_mail = null;
        }
    }

    @Override
    public void before() throws Exception {

        m_mail = new GreenMail(TestUtil.SETUP);
        m_mail.withConfiguration(TestUtil.CONFIG);
        m_mail.start();
        final ImapServer imap = m_mail.getImap();
        final ServerSetup serverSetup = imap.getServerSetup();
        m_bindAddress = serverSetup.getBindAddress();
        m_imapPort = serverSetup.getPort();
        final var smtp = m_mail.getSmtp();
        m_smptPort = smtp.getPort();
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
        flowVariables.add(new FlowVariable(VAR_PREFIX + "smtp-port", m_smptPort));
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
