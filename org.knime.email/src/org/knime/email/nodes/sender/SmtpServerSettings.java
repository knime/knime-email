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
 *   Dec 27, 2023 (wiswedel): created
 */
package org.knime.email.nodes.sender;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.LayoutGroup;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.rule.TrueCondition;
import org.knime.core.webui.node.dialog.defaultdialog.setting.credentials.Credentials;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;

import com.google.common.base.Strings;

/**
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
final class SmtpServerSettings implements DefaultNodeSettings, LayoutGroup {

    private static final int DEFAULT_SMTP_CONNECTION_TIMEOUT_SEC = 2;

    private static final int DEFAULT_SMTP_READ_TIMEOUT_SEC = 30;

    enum ConnectionSecurity {
        @Label("None")
        NONE,
        @Label("SSL")
        SSL,
        @Label("STARTTLS")
        STARTTLS
    }

    interface SmtpRequiresAuth {
    }

    @Widget(title = "SMTP Host")
    @TextInputWidget(pattern = "^\\w[\\w\\.]*")
    String m_smtpHost;

    @Widget(title = "SMTP Port")
    @NumberInputWidget(min = 1)
    int m_smtpPort = 587;

    @Widget(title = "SMTP host requires authentication")
    @Signal(id = SmtpRequiresAuth.class, condition = TrueCondition.class)
    boolean m_smtpRequiresAuthentication = true;

    @Widget(title = "SMTP User Name")
    @Effect(signals = SmtpRequiresAuth.class, type = EffectType.SHOW)
    Credentials m_smtpCredentials;

    @Widget(title = "Connection Security")
    @ValueSwitchWidget
    ConnectionSecurity m_smtpSecurity = ConnectionSecurity.NONE;

    @Widget(title = "SMTP Connect Timeout [s]", advanced = true,
        description = "SMTP Host connection timeout in seconds")
    int m_smtpConnectTimeoutS= DEFAULT_SMTP_CONNECTION_TIMEOUT_SEC;

    @Widget(title = "SMTP Read Timeout [s]", advanced = true, description = "SMTP Host read timeout in seconds")
    int m_smtpReadTimeoutS= DEFAULT_SMTP_READ_TIMEOUT_SEC;

    void validate() throws InvalidSettingsException {
        CheckUtils.checkSetting(!Strings.isNullOrEmpty(m_smtpHost), "No SMTP host specified");
        CheckUtils.checkSetting(m_smtpPort > 0, "Invalid SMTP Port: %d", m_smtpPort);
        CheckUtils.checkSetting(!m_smtpRequiresAuthentication || m_smtpCredentials != null,
                "No credentials provided available although smtp server requires authentication");
        CheckUtils.checkSettingNotNull(m_smtpSecurity, "Connection security field must not be null");
        CheckUtils.checkSetting(m_smtpConnectTimeoutS > 0, "Connection timeout[s] must be greater than 0: %d",
            m_smtpConnectTimeoutS);
        CheckUtils.checkSetting(m_smtpReadTimeoutS > 0, "Read timeout[s] must be greater than 0: %d",
            m_smtpReadTimeoutS);
    }
}
