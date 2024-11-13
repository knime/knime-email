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
 *   Sep 27, 2023 (wiswedel): created
 */
package org.knime.email.nodes.reader;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.RowKey;
import org.knime.core.data.blob.BinaryObjectCellFactory;
import org.knime.core.data.blob.BinaryObjectDataCell;
import org.knime.core.data.collection.ListCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.html.HTMLCellFactory;
import org.knime.core.data.time.localdatetime.LocalDateTimeCellFactory;
import org.knime.core.data.v2.RowBuffer;
import org.knime.core.data.v2.RowWrite;
import org.knime.core.data.v2.RowWriteCursor;
import org.knime.core.data.v2.WriteValue;
import org.knime.core.data.v2.time.DateTimeValueInterfaces.LocalDateTimeWriteValue;
import org.knime.core.data.v2.value.ValueInterfaces.StringListWriteValue;
import org.knime.core.data.v2.value.ValueInterfaces.StringWriteValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.EmailUtil;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchTerm;

/**
 *
 * @author wiswedel
 */
public final class EmailReaderNodeProcessor {

    /** The name of the Message ID column. */
    public static final String COL_EMAIL_ID = "Email ID";

    private static final class MatchAllSearchTerm extends SearchTerm {
        private static final long serialVersionUID = 1L;

        public static MatchAllSearchTerm INSTANCE = new MatchAllSearchTerm();

        @Override
        public boolean match(final Message msg) {
            return true;
        }
    }

    static DataTableSpec getMsgSpec(final boolean retrieveFlags) {
        final var specCreator = new DataTableSpecCreator() //
            .addColumns(new DataColumnSpecCreator(COL_EMAIL_ID, StringCell.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("Date", LocalDateTimeCellFactory.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("Subject", StringCell.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("Text (Plain)", StringCell.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("Text (HTML)", HTMLCellFactory.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("From", StringCell.TYPE).createSpec()) //
            .addColumns(new DataColumnSpecCreator("To", ListCell.getCollectionType(StringCell.TYPE)).createSpec())
            .addColumns(new DataColumnSpecCreator("Cc", ListCell.getCollectionType(StringCell.TYPE)).createSpec());
        if (retrieveFlags) {
            specCreator.addColumns(
                new DataColumnSpecCreator("Flags", ListCell.getCollectionType(StringCell.TYPE)).createSpec()); //
        }
        return specCreator.createSpec();
    }

    static final DataTableSpec ATTACH_TABLE_SPEC = new DataTableSpecCreator() //
        .addColumns(new DataColumnSpecCreator(COL_EMAIL_ID, StringCell.TYPE).createSpec()) //
        .addColumns(new DataColumnSpecCreator("File Name", StringCell.TYPE).createSpec()) //
        .addColumns(new DataColumnSpecCreator("Attachment", BinaryObjectDataCell.TYPE).createSpec()) //
        .createSpec();

    static final DataTableSpec HEADER_TABLE_SPEC = new DataTableSpecCreator() //
        .addColumns(new DataColumnSpecCreator(COL_EMAIL_ID, StringCell.TYPE).createSpec()) //
        .addColumns(new DataColumnSpecCreator("Header Name", StringCell.TYPE).createSpec()) //
        .addColumns(new DataColumnSpecCreator("Header Value", StringCell.TYPE).createSpec()) //
        .createSpec();

    private final EmailSessionKey m_mailSessionKey;

    private final EmailReaderNodeSettings m_settings;

    private long m_attachmentCounter = 0;

    private long m_headerCounter = 0;

    private BufferedDataTable m_msgTable;

    private BufferedDataTable m_attachTable;

    private BufferedDataTable m_headerTable;

    EmailReaderNodeProcessor(final EmailSessionKey mailSessionKey, final EmailReaderNodeSettings settings) {
        m_mailSessionKey = mailSessionKey;
        m_settings = settings;
    }

    void readEmailsAndFillTable(final ExecutionContext context) throws Exception {
        try (final var session = m_mailSessionKey.connectIncoming();
                //In order to have a message set as read we need to opened the folder in read_write mode.
                final var folder = session.openFolderForWriting(m_settings.m_folder);
//                for now we do not support header retrieval
//                final var msgRowContainer = context.createRowContainer(getMsgSpec(m_settings.m_retrieveFlags), false);
                final var msgRowContainer = context.createRowContainer(getMsgSpec(false), false); //
                var msgWriteCursor = msgRowContainer.createCursor(); //
                final var attachRowContainer = context.createRowContainer(ATTACH_TABLE_SPEC, false); //
                var attachWriteCursor = attachRowContainer.createCursor(); //
                final var headerRowContainer = context.createRowContainer(HEADER_TABLE_SPEC, false); //
                var headerWriteCursor = headerRowContainer.createCursor(); //
                var classLoaderCloseable = EmailUtil.runWithContextClassloader(Session.class)) {
            final var msgRowBuffer = msgRowContainer.createRowBuffer();
            final var attachRowBuffer = attachRowContainer.createRowBuffer();
            final var headerRowBuffer = headerRowContainer.createRowBuffer();
            final BinaryObjectCellFactory factory = new BinaryObjectCellFactory(context);
            final SearchTerm searchTerm = buildSearchTerm();
            var messages = folder.search(searchTerm);
            final int count = messages.length;
            final int indexStart;
            final int indexEnd;
            switch (m_settings.m_messageSelector) {
                case All:
                    indexStart = 1;
                    indexEnd = count;
                    break;
                case Newest:
                    indexStart = 1 + Math.max(0, count - m_settings.m_limitMessagesCount);
                    indexEnd = count;
                    break;
                case Oldest:
                    indexStart = 1;
                    indexEnd = Math.min(count, m_settings.m_limitMessagesCount);
                    break;
                default:
                    throw new IllegalStateException("Invalid message selector");
            }
            final List<Message> previouslyUnreadMessages = new ArrayList<>();
            long rowKey = 0;
            // the number of messages actually read/retrieved (e.g. 100 when only 100 are to be read)
            final var messageCount = indexEnd - indexStart + 1;
            for (int i = indexStart; i <= indexEnd;) {
                final var indexOfMessage = i - indexStart + 1;
                final var paddedNumber = "%" + Long.toString(messageCount).length() + "d";
                final var messageTemplateBuilder = new StringBuilder();
                // "Fetching message  12/100"
                messageTemplateBuilder.append("Fetching message ");
                messageTemplateBuilder.append(paddedNumber); // %d (from)
                messageTemplateBuilder.append("/");
                messageTemplateBuilder.append(paddedNumber); // %d (total)
                context.setProgress((indexOfMessage - 1) / (double)messageCount,
                    messageTemplateBuilder.toString().formatted(indexOfMessage, messageCount));
                context.checkCanceled();
                final Message message = messages[i - 1];
                if (!message.isExpunged()) {
                    if (!m_settings.m_markAsRead && !message.isSet(Flags.Flag.SEEN)) {
                        //Store only the previously unread messages if they need to be reset later
                        previouslyUnreadMessages.add(message);
                    }
                    msgRowBuffer.setRowKey(RowKey.createRowKey(rowKey++));
                    // message ID
                    final var messageId = EmailUtil.getMessageId(message);
                    writeMessageAndAttachments(context, factory, messageId, message, msgRowBuffer, attachWriteCursor, attachRowBuffer);
                    writeHeader(messageId, message, headerWriteCursor, headerRowBuffer);
                    msgWriteCursor.commit(msgRowBuffer);
                }
                i++;
            }
            if (!previouslyUnreadMessages.isEmpty()) {
                //explicitly mark message as un-seen since they are automatically set to seen when content is
                //downloaded https://jakarta.ee/specifications/mail/1.6/apidocs/javax/mail/flags.flag#SEEN
                EmailUtil.flagMessages(folder, previouslyUnreadMessages.toArray(new Message[0]), Flags.Flag.SEEN, false);
            }
            m_msgTable = msgRowContainer.finish();
            m_attachTable = attachRowContainer.finish();
            m_headerTable = headerRowContainer.finish();
        }
    }

    private SearchTerm buildSearchTerm() {
        final SearchTerm[] terms = new SearchTerm[2];
        switch (m_settings.m_messageSeenStatus) {
            case Read:
                terms[0] = new FlagTerm(new Flags(Flag.SEEN), true);
                break;
            case Unread:
                terms[0] = new FlagTerm(new Flags(Flag.SEEN), false);
                break;
            case All:
            default:
                terms[0] = MatchAllSearchTerm.INSTANCE;
                break;
        }
        switch (m_settings.m_messageAnsweredStatus) {
            case Answered:
                terms[1] = new FlagTerm(new Flags(Flag.ANSWERED), true);
                break;
            case Unanswered:
                terms[1] = new FlagTerm(new Flags(Flag.ANSWERED), false);
                break;
            case All:
            default:
                terms[1] = MatchAllSearchTerm.INSTANCE;
                break;
        }
        return new AndTerm(terms);
    }

    private void writeHeader(final String messageId, final Message message, final RowWriteCursor headerWriteCursor, final RowBuffer headerRowBuffer)
        throws MessagingException {
        if (m_settings.m_outputHeaders) {
            final Enumeration<Header> allHeaders = message.getAllHeaders();
            while (allHeaders.hasMoreElements()) {
                final Header header = allHeaders.nextElement();
                headerRowBuffer.setRowKey(RowKey.createRowKey(m_headerCounter++));
                headerRowBuffer.<StringWriteValue> getWriteValue(0).setStringValue(messageId);
                headerRowBuffer.<StringWriteValue> getWriteValue(1).setStringValue(header.getName());
                headerRowBuffer.<StringWriteValue> getWriteValue(2).setStringValue(header.getValue());
                headerWriteCursor.commit(headerRowBuffer);
            }
        }
    }

    /**
     * @return the msgTable
     */
    public BufferedDataTable getMsgTable() {
        return m_msgTable;
    }

    /**
     * @return the attachTable
     */
    public BufferedDataTable getAttachTable() {
        return m_attachTable;
    }

    /**
     * @return the headerTable
     */
    public BufferedDataTable getHeaderTable() {
        return m_headerTable;
    }

    private void writeMessageAndAttachments(final ExecutionContext context, final BinaryObjectCellFactory factory,
        final String messageId, final Message message, final RowWrite rowWrite, final RowWriteCursor attachWriteCurser,
        final RowBuffer attachRowBuffer) throws MessagingException, IOException, CanceledExecutionException {
        var index = 0;

        // message id
        rowWrite.<StringWriteValue> getWriteValue(index++).setStringValue(messageId);

        // received when
        final var receivedDate = message.getReceivedDate();
        if (receivedDate == null) {
            rowWrite.setMissing(index++);
        } else {
            final var receivedWhen = receivedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            rowWrite.<LocalDateTimeWriteValue> getWriteValue(index++).setLocalDateTime(receivedWhen);
        }

        // subject
        rowWrite.<StringWriteValue> getWriteValue(index++).setStringValue(message.getSubject());

        // part
        final StringBuilder textBuf = new StringBuilder();
        final StringBuilder htmlBuf = new StringBuilder();
        writePart(context, factory, messageId, textBuf, htmlBuf, message, attachWriteCurser, attachRowBuffer);
        if (textBuf.isEmpty()) {
            rowWrite.setMissing(index++);
        } else {
            rowWrite.<StringWriteValue> getWriteValue(index++).setStringValue(textBuf.toString());
        }
        if (htmlBuf.isEmpty()) {
            rowWrite.setMissing(index++);
        } else {
            rowWrite.<WriteValue<DataCell>> getWriteValue(index++)
                .setValue(HTMLCellFactory.INSTANCE.createCell(htmlBuf.toString()));
        }

        // from
        final var from = Optional.ofNullable(message.getFrom()).stream().flatMap(Arrays::stream).findFirst()
            .map(Address::toString).orElse(null);
        rowWrite.<StringWriteValue> getWriteValue(index++).setStringValue(from);

        // TO
        final String[] to = EmailUtil.extractRecipients(message, RecipientType.TO);
        if (to == null) {
            rowWrite.setMissing(index++);
        } else {
            rowWrite.<StringListWriteValue> getWriteValue(index++).setValue(to);
        }

        // CC
        final String[] cc = EmailUtil.extractRecipients(message, RecipientType.CC);
        if (cc == null) {
            rowWrite.setMissing(index++);
        } else {
            rowWrite.<StringListWriteValue> getWriteValue(index++).setValue(cc);
        }
    }

    private void writePart(final ExecutionContext context, final BinaryObjectCellFactory factory,
        final String messageId, final StringBuilder textBuf, final StringBuilder htmlBuf, final Part p,
        final RowWriteCursor attachWriteCurser, final RowBuffer attachRowBuffer)
        throws MessagingException, IOException, CanceledExecutionException {
        context.checkCanceled();
        //check if the content is plain text
        if (p.isMimeType("text/plain")) {
            if (isAttachment(p)) {
                writeAttachment(factory, messageId, attachWriteCurser, attachRowBuffer, p);
            } else { //and no attachment
                textBuf.append((String)p.getContent());
            }
        //check if the content is plain text
        } else if (p.isMimeType("text/html")) {
            if (isAttachment(p)) {
                writeAttachment(factory, messageId, attachWriteCurser, attachRowBuffer, p);
            } else { //and no attachment
                htmlBuf.append((String)p.getContent());
            }
        //check if the content is a multipart
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart)p.getContent();
            int count = mp.getCount();
            for (int i = 0; i < count; i++) {
                writePart(context, factory, messageId, textBuf, htmlBuf, mp.getBodyPart(i), attachWriteCurser, attachRowBuffer);
            }
        //check if the content is a nested message
        } else if (p.isMimeType("message/rfc822")) {
            writePart(context, factory, messageId, textBuf, htmlBuf, (Part)p.getContent(), attachWriteCurser, attachRowBuffer);
        //check if the content is a attached file
        } else if (isAttachment(p)) {
            writeAttachment(factory, messageId, attachWriteCurser, attachRowBuffer, p);
        //fallback for all other message parts we do not know
        } else {
            final Object o = p.getContent();
            if (o instanceof String) {
                textBuf.append((String)o);
            } else if (o instanceof InputStream is) {
                try (is) {
                    int c;
                    while ((c = is.read()) != -1) {
                        textBuf.append(c);
                    }
                }
            }
        }
    }

    private static boolean isAttachment(final Part p) throws MessagingException {
        if (p instanceof MimeBodyPart mp) {
            final String fileName = mp.getFileName();
            return fileName != null && !fileName.isBlank();
        }
        return false;
    }

    private void writeAttachment(final BinaryObjectCellFactory factory, final String messageId,
        final RowWriteCursor attachWriteCurser, final RowBuffer attachRowBuffer, final Part p) throws MessagingException, IOException {
        if (m_settings.m_outputAttachments) {
            if (p instanceof MimeBodyPart mp) {
                try (var is = mp.getInputStream()) {
                    attachRowBuffer.setRowKey(RowKey.createRowKey(m_attachmentCounter++));
                    attachRowBuffer.<StringWriteValue> getWriteValue(0).setStringValue(messageId);
                    attachRowBuffer.<StringWriteValue> getWriteValue(1).setStringValue(mp.getFileName());
                    attachRowBuffer.<WriteValue<DataCell>> getWriteValue(2).setValue(factory.create(is));
                    attachWriteCurser.commit(attachRowBuffer);;
                }
            }
        }
    }
}
