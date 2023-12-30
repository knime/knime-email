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
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.TextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Strings;

/**
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
final class RecipientsSettings implements DefaultNodeSettings, LayoutGroup {

    /** (Weak) pattern for email addresses, should match...
     * <pre>
     * Name@Domain.com
     * &lt;Full Name> Name@Domain.com
     * &lt;Full Name1> Name1@Domain.com, &lt;Full Name 2> Name2@Domain.com
     * </pre>
     */
    private static final String EMAIL_ADDRESS_PATTERN = "^(.+)@(.+)$";

    @Widget(title = "To", description = "Recipients email addresses (To)")
    @TextInputWidget(pattern = EMAIL_ADDRESS_PATTERN)
    String m_to;

    @Widget(title = "CC", description = "Recipients email addresses (CC)")
    @ArrayWidget
    String m_cc;

    @Widget(title = "BCC", advanced = true, description = "Blind copy email addresses (BCC)")
    @ArrayWidget
    String m_bcc;

    @Widget(title = "Reply To", advanced = true, description = "ReplyTo field. By default, a reply to an email "
        + "will be addressed to the sender of the original email. This field allows changing the reply to address.")
    String m_replyTo;

    void validate() throws InvalidSettingsException {
        CheckUtils.checkSetting(!Strings.isNullOrEmpty(getToNotNull() + getCCNotNull() + getBCCNotNull()),
            "No recipient specified");
    }

    @JsonIgnore
    String getToNotNull() {
        return Strings.nullToEmpty(m_to);
    }

    @JsonIgnore
    String getCCNotNull() {
        return Strings.nullToEmpty(m_cc);
    }

    @JsonIgnore
    String getBCCNotNull() {
        return Strings.nullToEmpty(m_bcc);
    }

    @JsonIgnore
    String getReplyToNotNull() {
        return Strings.nullToEmpty(m_replyTo);
    }

}
