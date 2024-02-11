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
package org.knime.email.nodes.provider.gmail;

import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.HorizontalLayout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.Credentials;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.email.nodes.connector.EmailConnectorSettings.ConnectionSecurity;
import org.knime.email.nodes.connector.EmailConnectorSettings.ConnectionType;
import org.knime.email.session.EmailSessionKey;

/**
 * Settings class.
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class GmailConnectorSettings implements DefaultNodeSettings {

    @Widget(title = "Connection type", description =
            """
            Choose the type of connection you want to create. Depending on the selected connection type you will be
            able to work with your email and/or send email.
            <ul>
            <li>
            Select "Incoming" if you want to work with your email e.g. by using the
            <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.reader.EmailReaderNodeFactory/">Email Reader</a> node
            to read your email or the
            <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.mover.EmailMoverNodeFactory/">Email Mover</a> node
            to move email to different folders.
            </li>
            <li>
            Select "Outgoing" if you only want to send email via the SMTP protocol by using the
            <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.sender.EmailSenderNodeFactory/">Email Sender</a> node.
            </li>
            <li>Select "Incoming &amp; Outgoing" if you want to read and send email.</li>
            </ul>
            """)
    @Persist(optional = true)
    @ValueSwitchWidget()
    ConnectionType m_type = ConnectionType.INCOMING;

//  INCOMING SERVER SETTINGS
    String m_imapServer = "imap.gmail.com";
    int m_imapPort = 993;
    boolean m_imapUseSecureProtocol = true;


//  OUTGOING SERVER SETTINGS
    @Widget(title = "Email address", description = "Some SMTP servers require the sender's email address, other "
            + "accept this to be not specified and will automatically derive it from the user account.")

    String m_smtpEmailAddress;
    String m_smtpHost = "smtp.gmail.com";
    int m_smtpPort = 587;
    boolean m_smtpRequiresAuthentication = true;
    ConnectionSecurity m_smtpSecurity = ConnectionSecurity.STARTTLS;


    interface AuthenticationSection {}
    /**The email server login.*/
    @Layout(AuthenticationSection.class)
    @Widget(title = "Login", description = "The optional email server login.")
    Credentials m_login = new Credentials(); //set to empty credentials to prevent "No login set message"


//  CONNECTION PROPERTIES
    @Section(title = "Connection Properties", advanced = true)
    @After(AuthenticationSection.class)
    interface ConnectionPropertySection {}

    @Layout(ConnectionPropertySection.class)
    @Widget(title = "Connection timeout", advanced = true,
        description = "Timeout in seconds to establish a connection.")
    @NumberInputWidget(min = 1)
    @Persist(optional = true)
    int m_connectTimeout = EmailSessionKey.DEF_TIMEOUT_CONNECT_S;

    @Layout(ConnectionPropertySection.class)
    @Widget(title = "Read timeout", advanced = true,
    description = "Timeout in seconds to read a server response from a connection.")
    @NumberInputWidget(min = 1)
    @Persist(optional = true)
    int m_readTimeout = EmailSessionKey.DEF_TIMEOUT_READ_S;

    @Widget(title = "Custom properties", description =
            "Allows to define additional connection properties. For details about the supported properties see "
            + "<a href='https://javaee.github.io/javamail/docs/api//com/sun/mail/imap/package-summary.html#properties'>"
            + "here.</a>",
            advanced = true)
    @Layout(ConnectionPropertySection.class)
    @ArrayWidget(addButtonText = "Add custom property")
    ConnectionProperties[] m_properties = new ConnectionProperties[0];

    static final class ConnectionProperties implements DefaultNodeSettings {
        @HorizontalLayout
        interface ConnectionPropertiesLayout {
        }

        @Widget(title = "Name", description = "Custom property name e.g. mail.smtp.timeout.")
        @TextInputWidget(pattern = "\\S+.*")
        @Layout(ConnectionPropertiesLayout.class)
        public String m_name;

        @Widget(title = "Value",
            description = "Custom property value e.g. 10 or true.")
        @TextInputWidget(pattern = "\\S+.*")
        @Layout(ConnectionPropertiesLayout.class)
        public String m_value;
    }
}
