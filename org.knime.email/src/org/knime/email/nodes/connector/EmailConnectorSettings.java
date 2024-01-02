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

import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.HorizontalLayout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.DefaultProvider;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.field.Persist;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.OneOfEnumCondition;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Or;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.rule.TrueCondition;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.Credentials;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;

/**
 * Settings class.
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class EmailConnectorSettings implements DefaultNodeSettings {

    @Widget(title = "Email address",
            description = "The email address.")
    String m_emailAddress;

    @Widget(title = "Connection type", description = "Choose the type of connection.")
    @Signal(id = IncomingServerSection.class, condition = IsReadSelected.class)
    @Signal(id = OutgoingServerSection.class, condition = IsWriteSelected.class)
    @Persist(defaultProvider = TypeDefaultProvider.class)
    @ValueSwitchWidget()
    ConnectionType m_type = ConnectionType.READ_ONLY;


//  INCOMING SERVER SETTINGS
    @Section(title = "Incoming Mail Server")
    @Effect(signals = IncomingServerSection.class, type = EffectType.SHOW)
    interface IncomingServerSection {}


    @Layout(IncomingServerSection.class)
    @Widget(title = "Server", description = "The address of the incoming email server (IMAP).") //
    @TextInputWidget(pattern = "[^ ]+")
    String m_server;


    @Layout(IncomingServerSection.class)
    @Widget(title = "Port", description = "The port of the incoming email server (e.g. 993).") //
    @NumberInputWidget(min = 1, max = 0xFFFF) // 65635
    int m_port = 993;

    @Layout(IncomingServerSection.class)
    @Widget(title = "Use secure protocol",
            description = "Choose whether to use an encrypted or unencrypted connection.", advanced = true)
    boolean m_useSecureProtocol = true;


//  OUTGOING SERVER SETTINGS
    @Section(title = "Outgoing Mail Server")
    @After(IncomingServerSection.class)
    @Effect(signals = OutgoingServerSection.class, type = EffectType.SHOW)
    interface OutgoingServerSection {}

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Server", description = "The address of the outgoing email server (SMTP).")
    @TextInputWidget(pattern = "^\\w[\\w\\.]*")
    @Persist(defaultProvider = EmptyHostProvider.class)
    String m_smtpHost;

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Port", description = "The port of the incoming email server (e.g. 587).")
    @NumberInputWidget(min = 1)
    @Persist(defaultProvider = DefaultPortProvider.class)
    int m_smtpPort = 587;

    interface RequiresAuthentication {}
    @Layout(OutgoingServerSection.class)
    @Widget(title = "Outgoing mail server requires authentication")
    @Persist(defaultProvider = DeafultAuthenticationProvider.class)
    @Signal(id=RequiresAuthentication.class, condition = TrueCondition.class)
    boolean m_smtpRequiresAuthentication = true;

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Connection Security")
    @ValueSwitchWidget
    @Persist(defaultProvider = DeafultSecurityProvider.class)
    ConnectionSecurity m_smtpSecurity = ConnectionSecurity.NONE;


    @Section(title = "Authentication")
    @After(OutgoingServerSection.class)
    interface AuthenticationSection {}
    /**The email server login.*/
    @Layout(AuthenticationSection.class)
    @Widget(title = "Login",
            description = "The email server login.")
    Credentials m_login;


//  CONNECTION PROPERTIES
    @Section(title = "Connection Properties", advanced = true)
    @Effect(signals = {IncomingServerSection.class, RequiresAuthentication.class}, type = EffectType.SHOW,
    operation = Or.class)
    @After(AuthenticationSection.class)
    interface ConnectionPropertySection {}


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


//  HELPER SECTION
    enum ConnectionType {
        @Label("Read-only")
        READ_ONLY,
        @Label("Write-only")
        WRITE_ONLY,
        @Label("Read-write")
        READ_WRITE
    }

    // OUTGOING SERVER SETTINGS
    enum ConnectionSecurity {
        @Label("None")
        NONE,
        @Label("SSL")
        SSL,
        @Label("STARTTLS")
        STARTTLS
    }

    enum EmailProtocol {
        @Label("IMAP(4)")
        IMAP,
        @Label("POP3")
        POP3
    }

    static class IsReadSelected extends OneOfEnumCondition<ConnectionType> {
        @Override
        public ConnectionType[] oneOf() {
            return new ConnectionType[]{ConnectionType.READ_ONLY, ConnectionType.READ_WRITE};
        }
    }

    static class IsWriteSelected extends OneOfEnumCondition<ConnectionType> {
        @Override
        public ConnectionType[] oneOf() {
            return new ConnectionType[]{ConnectionType.WRITE_ONLY, ConnectionType.READ_WRITE};
        }
    }

    private static final class TypeDefaultProvider implements DefaultProvider<ConnectionType> {
        @Override
        public ConnectionType getDefault() {
            return ConnectionType.READ_ONLY;
        }
    }

    private static final class EmptyHostProvider implements DefaultProvider<Object> {
        @Override
        public Object getDefault() {
            return null;
        }
    }

    private static final class DefaultPortProvider implements DefaultProvider<Integer> {
        @Override
        public Integer getDefault() {
            return 587;
        }
    }

    private static final class DeafultAuthenticationProvider implements DefaultProvider<Boolean> {
        @Override
        public Boolean getDefault() {
            return Boolean.TRUE;
        }
    }

    private static final class DeafultSecurityProvider implements DefaultProvider<ConnectionSecurity> {
        @Override
        public ConnectionSecurity getDefault() {
            return ConnectionSecurity.NONE;
        }
    }
}
