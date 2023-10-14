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
 *   29.09.2023 (loescher): created
 */
package org.knime.email.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import org.knime.core.data.container.filter.TableFilter;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.v2.RowCursor;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.NodeLogger;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.search.MessageIDTerm;

/**
 * Methods for processing Emails.
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
public final class EmailUtil {

    static final NodeLogger LOGGER = NodeLogger.getLogger(EmailUtil.class);

    /**Prefix used for message ids where the original message has no message id.*/
    public static final String KNIME_PREFIX = "knime:";

    /**The name of the message id header.*/
    public static final String MESSAGEID_HEADER = "Message-ID";

    private EmailUtil() {
        // utility method
    }

    /**
     * Find the messages by the IDs stored at a given index in a row cursor.
     *
     * @param folder the folder to search in
     * @param idTable the row cursor that produces rows with the message IDs.
     * @param idx the (column) index containing the string value
     *
     * @return the found messages, messages that are expunged or do not exist anymore will be removed from the output
     * @throws MessagingException - for Jakarta failures
     * @throws CanceledExecutionException
     */
    public static final Message[] findMessages(final ExecutionMonitor exec, final Folder folder,
        final BufferedDataTable idTable, final int idx)
        throws MessagingException, CanceledExecutionException {
        final var messages = new ArrayList<Message>();
        long size = idTable.size();
        long counter = 0;
        try(var cursor = getIDCursor(idTable, idx)){
            while (cursor.canForward()) {
                exec.checkCanceled();
                exec.setProgress(counter++ / (double) size, "Searching for id " + counter + " of " + size);
                final var row = cursor.forward();
                final var messageId = ((StringCell)row.getValue(idx)).getStringValue();
                final var message = findMessageById(messageId, folder);
                if (message != null && !message.isExpunged()) {
                    messages.add(message);
                }
            }
            exec.setProgress(1);
            return messages.toArray(new Message[0]);
        }
    }

    private static RowCursor getIDCursor(final BufferedDataTable table, final int index) {
        return table.cursor(TableFilter.materializeCols(index));
    }

    /**
     * Get an ID which can be used to uniquely identify a message.
     *
     * @param message the message to get the ID for
     * @return the message ID
     * @throws MessagingException - for Jakarta failures
     */
    public static String getMessageId(final Message message) throws MessagingException {
        final var ids = message.getHeader(MESSAGEID_HEADER);
        if (ids == null || ids.length == 0) {
            LOGGER.debug("Message has no Message-ID header field");
            return KNIME_PREFIX + getKnimeMessageId(message);
        }
        if (ids.length > 1) {
            LOGGER.warn("Message has more than one Message-ID header field");
        }
        // we take only the first field as MessageID(Search)Term searches though all Message-ID headers
        return ids[0];
    }

    /**
     * Searches for a message using its unique ID.
     *
     * @param id the message ID to use.
     * @param folder the opened folder to search through
     * @return the first found message, {@code null} if nothing was found
     * @throws MessagingException - for Jakarta failures
     */
    public static Message findMessageById(final String id, final Folder folder) throws MessagingException {
        if (id.startsWith(KNIME_PREFIX)) {
            return findMessageByKnimeId(Integer.parseInt(id.substring(KNIME_PREFIX.length())), folder);
        } else {
            return findMessageByMessageId(id, folder);
        }
    }

    /**
     * Sets the given flag for all given messages.
     *
     * @param folder the folder that contains the messages
     * @param messages to flag
     * @param flag the flag to set
     * @param set the flag value to set
     * @throws MessagingException
     */
    public static void flagMessages(final Folder folder, final Message[] messages, final Flag flag, final boolean set)
        throws MessagingException {
        folder.setFlags(messages, new Flags(flag), set);
    }

    private static Message findMessageByMessageId(final String id, final Folder folder) throws MessagingException {
        final var term = new MessageIDTerm(id);
        for (int i = 1, count = folder.getMessageCount(); i <= count;) {
            final var messages = folder.getMessages(i, Math.min(i + 10, count));
            final var results = folder.search(term, messages);
            if (results.length > 0) {
                if (results.length > 1) {
                    LOGGER.warn("Found multiple messages with same Message-ID");
                }
                return results[0];
            }
            i += messages.length;
        }
        return null;
    }

    private static Message findMessageByKnimeId(final int id, final Folder folder) throws MessagingException {
        for (int i = 0, count = folder.getMessageCount(); i < count;) {
            for (final var message : folder.getMessages(i, Math.min(i + 10, count))) {
                if (getKnimeMessageId(message) == id) {
                    return message;
                }
                i++;
            }
        }
        return null;
    }

    private static int getKnimeMessageId(final Message message) throws MessagingException {
        var hash = 0;
        final var e = message.getAllHeaders();
        while (e.hasMoreElements()) {
            // hash is a simple sum to ensure that header order doesn't matter
            hash += Objects.hashCode(e.nextElement());
        }
        return hash;
    }

    /**
     * @param message the {@link Message} to extract the recipient from
     * @param type the {@link RecipientType}
     * @return the recipient strings or null if none exists
     * @throws MessagingException
     */
    public static String[] extractRecipients(final Message message, final RecipientType type) throws MessagingException {
        final var recipients = message.getRecipients(type);
        if (recipients == null || recipients.length == 0) {
            return null;
        }
        final var cc = Arrays.stream(recipients).map(Address::toString).toArray(String[]::new);
        return cc;
    }

}
