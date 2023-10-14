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
 *   21 Dec 2022 (jasper): created
 */
package org.knime.email.nodes.get;

import static org.knime.email.nodes.get.GetEmailNodeProcessor.COL_MESSAGE_ID;

import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Signal;
import org.knime.core.webui.node.dialog.defaultdialog.rule.TrueCondition;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.email.util.UIChoices.FolderProvider;

/**
 * Node Settings for the Value Lookup Node
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class GetEmailNodeSettings implements DefaultNodeSettings {

    /** The name of the lookup column in the data table */
    @Widget(title = "Folder name", description = "e.g. 'INBOX' or Folder/Subfolder")
    @ChoicesWidget(choices = FolderProvider.class, showSearch = true)
    String m_folder;

    @Widget(title = "Mark loaded messages as read",
            description = "If not selected, the node will reset the SEEN flag of all loaded messages to false after "
                + "downloading their content.",
                advanced = true)
    boolean m_markAsRead = true;

    @Section(title = "Filtering")
    interface FilteringSection {}

    @Widget(title = "Retrieve only",
            description = "Defines if only unseen, seen or all messages are retrieved from the server.")
    @Layout(FilteringSection.class)
    @ValueSwitchWidget
    MessageSeenStatus m_messageSeenStatus = MessageSeenStatus.Unseen;


    @Widget(title = "Retrieve only",
            description = "Defines if only unanswered, answered or all messages are retrieved from the server.")
    @Layout(FilteringSection.class)
    @ValueSwitchWidget
    MessageAnswerStatus m_messageAnsweredStatus = MessageAnswerStatus.Unanswered;


    @Section(title = "Additional information", advanced = true)
    interface AdditionalInfo {}

    @Widget(title = "Retrieve email flags",
            description = "If checked, the node will append an additional flags column to the message table. "
                + "Flags indicate the status of a message e.g. read, deleted, answered.",
                advanced = true)
    @Layout(value = AdditionalInfo.class)
    boolean m_retrieveFlags = false;

    @Widget(title = "Retrieve email attachments",
            description = "If checked, the node will provide all email attachments in an additional output table. "
                + "The table can be joined with the original email table via the " + COL_MESSAGE_ID + " column.",
                advanced = true)
    @Layout(value = AdditionalInfo.class)
    boolean m_retrieveAttachments = false;

    @Widget(title = "Retrieve email header",
            description = "If checked, the node will provide all email header in an additional output table. "
                + "The table can be joined with the original email table via the " + COL_MESSAGE_ID + " column.",
                advanced = true)
    @Layout(value = AdditionalInfo.class)
    boolean m_retrieveHeaders = false;

    @Section(title = "Message count")
    interface LimitMessages {}

    @Widget(title = "Limit number of messages",
            description = "If checked, the number of messages retrieved from the server is capped.")
    @Signal(id = LimitMessages.class, condition = TrueCondition.class)
    @Layout(value = LimitMessages.class)
    boolean m_limitMessages = true;

    @Widget(title = "Count", description = "The number of messages to retrieve at most.")
    @Layout(value = LimitMessages.class)
    @Effect(signals = LimitMessages.class, type = EffectType.SHOW)
    @NumberInputWidget(min = 1, max = Integer.MAX_VALUE)
    int m_limitMessagesCount = 100;

    @Widget(title = "Message selection", description = "Oldest or newest message")
    @Layout(LimitMessages.class)
    @Effect(signals = LimitMessages.class, type = EffectType.SHOW)
    @ValueSwitchWidget
    MessageSelector m_messageSelector = MessageSelector.Oldest;


    public enum MessageSeenStatus {
        @Label(value = "Unseen")
        Unseen,
        @Label(value = "Seen")
        Seen,
        @Label(value = "All")
        All
    }


    public enum MessageAnswerStatus {
        @Label(value = "Unanswered")
        Unanswered,
        @Label(value = "Answered")
        Answered,
        @Label(value = "All")
        All
    }

    public enum MessageSelector {
        @Label(value = "Oldest")
        Oldest,
        @Label(value = "Newest")
        Newest
    }

}
