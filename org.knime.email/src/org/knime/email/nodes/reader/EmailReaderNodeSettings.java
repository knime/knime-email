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
package org.knime.email.nodes.reader;

import static org.knime.email.nodes.reader.EmailReaderNodeProcessor.COL_EMAIL_ID;

import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.After;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Layout;
import org.knime.core.webui.node.dialog.defaultdialog.layout.Section;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Advanced;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.NumberInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.choices.ChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Predicate;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.PredicateProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.Reference;
import org.knime.core.webui.node.dialog.defaultdialog.widget.updates.ValueReference;
import org.knime.core.webui.node.dialog.defaultdialog.widget.validation.NumberInputWidgetValidation.MinValidation.IsPositiveIntegerValidation;
import org.knime.email.util.UIChoices.FolderProvider;

/**
 * Node Settings for the Value Lookup Node
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class EmailReaderNodeSettings implements DefaultNodeSettings {

    public enum MessageSeenStatus {
            @Label(value = "Unread") //
            Unread, //
            @Label(value = "Read") //
            Read, //
            @Label(value = "All") //
            All
    }

    public enum MessageAnswerStatus {
            @Label(value = "Unanswered") //
            Unanswered, //
            @Label(value = "Answered") //
            Answered, //
            @Label(value = "All") //
            All
    }

    public enum MessageSelector {
            @Label(value = "Newest") //
            Newest, //
            @Label(value = "Oldest") //
            Oldest, //
            @Label(value = "All") //
            All
    }

    static class MessageSelectorRef implements Reference<MessageSelector> {

    }

    static class IsLimitMessageCount implements PredicateProvider {


        @Override
        public Predicate init(final PredicateInitializer i) {
            return i.getEnum(MessageSelectorRef.class).isOneOf(MessageSelector.Newest, MessageSelector.Oldest);
        }

    }

    /** The name of the lookup column in the data table */
    @Widget(title = "Folder",
        description = "The full path to the email folder to read from e.g. 'INBOX' or Folder.Subfolder")
    @ChoicesProvider(FolderProvider.class)
    String m_folder;



    @Section(title = "Filtering")
    interface FilteringSection {
    }

    @Widget(title = "Read status",
        description = "Defines if only unseen, seen or all messages are retrieved from the server.")
    @Layout(FilteringSection.class)
    @ValueSwitchWidget
    MessageSeenStatus m_messageSeenStatus = MessageSeenStatus.Unread;

    @Widget(title = "Answered status",
        description = "Defines if only unanswered, answered or all messages are retrieved from the server.")
    @Layout(FilteringSection.class)
    @ValueSwitchWidget
    MessageAnswerStatus m_messageAnsweredStatus = MessageAnswerStatus.Unanswered;

    @Widget(title = "Limit number of emails",
        description = "Select if the oldest, newest or all emails should be retrieved.")
    @Layout(FilteringSection.class)
    @ValueReference(MessageSelectorRef.class)
    @ValueSwitchWidget
    MessageSelector m_messageSelector = MessageSelector.Newest;

    @Widget(title = "Maximum number of emails", description = "The number of messages to retrieve at most.")
    @Layout(value = FilteringSection.class)
    @Effect(predicate = IsLimitMessageCount.class, type = EffectType.SHOW)
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class)
    int m_limitMessagesCount = 100;



    @Section(title = "Output")
    @Advanced
    @After(FilteringSection.class)
    interface OutputSection {
    }

    //  Postponed to a later release since we are not sure if they are necessary and the returned
    //  collection is hard to work with.
    //    @Widget(title = "Retrieve email flags",
    //        description = "If checked, the node will append an additional flags column to the message table. "
    //            + "Flags indicate the status of a message e.g. read, deleted, answered.",
    //        advanced = true)
    //    @Layout(value = AdditionalInfo.class)
    //    boolean m_retrieveFlags = false;

    @Widget(title = "Output attachments table",
        description = "If checked, the node will provide all email attachments in an additional output table. "
            + "The table can be joined with the original email table via the " + COL_EMAIL_ID + " column.",
        advanced = true)
    @Layout(value = OutputSection.class)
    boolean m_outputAttachments = false;

    @Widget(title = "Output header table",
        description = "If checked, the node will provide all email header in an additional output table. "
            + "The table can be joined with the original email table via the " + COL_EMAIL_ID + " column.",
        advanced = true)
    @Layout(value = OutputSection.class)
    boolean m_outputHeaders = false;



    @Section(title = "Advanced")
    @Advanced
    @After(OutputSection.class)
    interface AdvancedSection {
    }

    @Widget(title = "Mark read emails as read",
        description = "By default all loaded emails are flagged as read. To prevent this, unselect this option in "
            + "which case the node will reset the read status of all loaded emails after downloading their content.",
        advanced = true)
    @Layout(value = AdvancedSection.class)
    boolean m_markAsRead = true;
}
