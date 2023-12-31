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
import org.knime.core.webui.node.dialog.defaultdialog.setting.filechooser.FileChooser;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.RichTextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.filehandling.core.connections.FSLocation;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
final class MessageSettings implements DefaultNodeSettings, LayoutGroup {

    /** EMail priority. */
    enum EMailPriority {
            @Label("Highest")
            HIGHEST("1 (Highest)"), //
            @Label("High")
            HIGH("2 (High)"), //
            @Label("Normal")
            NORMAL("3 (Normal)"), //
            @Label("Low")
            LOW("4 (Low)"), //
            @Label("Lowest")
            LOWEST("5 (Lowest)");

        private final String m_xPriority;

        EMailPriority(final String xPriority) {
            m_xPriority = xPriority;
        }

        /** @return for instance "1 (Highest)". */
        String toXPriority() {
            return m_xPriority;
        }
    }

    /** EMail format. */
    enum EMailFormat {
            @Label("HTML")
            HTML, //
            @Label("Text")
            TEXT
    }

    @Widget(title = "Subject", description = "The email's subject line.")
    String m_subject;

    @Widget(title = "Message", description =
            """
            The email's message body. Formatting options can be selected in the menu bar on the top of the editor.
            The contents of flow variables can be inserted by using the replacement syntax
            "$${<i>&lt;TYPE&gt;&lt;flow-variable-name&gt;</i>}$$". The leading &lt;TYPE&gt; is one of
            <i>I</i> (integer), <i>D</i> (double) or <i>S</i> (string), depending on the type of variable.
            """)
    @RichTextInputWidget
    String m_message;

    @Widget(title = "Content type", advanced = true,
        description = "The mail body's content encoded as plain text or html.")
    @ValueSwitchWidget
    EMailFormat m_format = EMailFormat.HTML;

    @Widget(title = "Priority", advanced = true, description = "The 'X-Priority' field that is understood by some "
        + "email clients to denote a priority of an email. If unsure, leave unchanged ('Normal' priority).")
    @ValueSwitchWidget
    EMailPriority m_priority = EMailPriority.NORMAL;

    @Widget(title = "Attachments", //
            description = "The path to the file to be attached to the email.")
    @ArrayWidget(showSortButtons = true, addButtonText = "Add attachment")
    Attachment[] m_attachments = new Attachment[] {};

    void validate() throws InvalidSettingsException {
        CheckUtils.checkSettingNotNull(m_subject, "Subject must not be null");
        CheckUtils.checkSettingNotNull(m_message, "Message must not be null");
        CheckUtils.checkSettingNotNull(m_priority, "Priority must not be null");
        CheckUtils.checkSettingNotNull(m_format, "Format must not be null");
    }

    static final class Attachment implements DefaultNodeSettings {

        @Widget
        FileChooser m_attachment = new FileChooser();

        @JsonIgnore
        FSLocation toFSLocation() {
            return m_attachment.getFSLocation();
        }
    }

}
