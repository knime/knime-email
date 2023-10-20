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
import org.knime.core.webui.node.dialog.defaultdialog.layout.HorizontalLayout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.credentials.base.node.UsernamePasswordSettings;

/**
 * Settings class.
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class EmailConnectorSettings extends UsernamePasswordSettings {

    enum EmailProtocol {
        @Label("IMAP(4)")
        IMAP,
        @Label("POP3")
        POP3
    }

//    /**The email protocol.*/
//    @Widget(title = "Email protocol", description = "Choose the email protocol to use.")
//    @ValueSwitchWidget()
//    public EmailProtocol m_protocol = EmailProtocol.IMAP;

    /** The name of the lookup column in the data table */
    @Widget(title = "Email server", description = "The address of the email server.") //
    @TextInputWidget(pattern = "[^ ]+")
    String m_server;

    /** The name of the lookup column in the data table */
    @Widget(title = "Port", description = "Server port (e.g. 993).") //
    @NumberInputWidget(min = 1, max = 0xFFFF) // 65635
    int m_port = 993;

    enum Security {
        @Label("Yes")
        YES,
        @Label("No")
        NO
    }

    /**The email protocol.*/
    @Widget(title = "Use secure protocol",
                 description = "Choose whether to use an encrypted or unencrypted connection.")
    @ValueSwitchWidget()
    public Security m_useSecureProtocol = Security.YES;


    @Section(title = "Connection properties", advanced = true)
    interface ConnectionPropertySection {}

    @Widget(title = "Property list", description = "Allows to define additional connection properties.",
            advanced = true)
    @Layout(ConnectionPropertySection.class)
    @ArrayWidget(addButtonText = "Add property")
    public ConnectionProperties[] m_properties = new ConnectionProperties[0];

    static final class ConnectionProperties implements DefaultNodeSettings {

        @HorizontalLayout
        interface ConnectionPropertiesLayout {
        }

        @Widget(title = "Name", description = "Property name e.g. mail.smtp.timeout.")
        @TextInputWidget(pattern = "\\S+.*")
        @Layout(ConnectionPropertiesLayout.class)
        public String m_name;

        @Widget(title = "Value",
            description = "Property value e.g. 10 or true.")
        @TextInputWidget(pattern = "\\S+.*")
        @Layout(ConnectionPropertiesLayout.class)
        public String m_value;
    }
}
