/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */

package org.knime.email.nodes.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.knime.email.TestUtil.CONFIG;
import static org.knime.email.TestUtil.SETUP;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knime.core.data.DataCell;
import org.knime.core.data.StringValue;
import org.knime.core.data.blob.BinaryObjectDataValue;
import org.knime.core.data.collection.ListDataValue;
import org.knime.core.data.html.HTMLValue;
import org.knime.core.data.time.localdatetime.LocalDateTimeValue;
import org.knime.core.data.v2.RowCursor;
import org.knime.core.data.v2.RowRead;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.util.Pair;
import org.knime.email.TestUtil;
import org.knime.email.nodes.reader.EmailReaderNodeProcessor;
import org.knime.email.nodes.reader.EmailReaderNodeSettings;
import org.knime.email.nodes.reader.EmailReaderNodeSettings.MessageAnswerStatus;
import org.knime.email.nodes.reader.EmailReaderNodeSettings.MessageSeenStatus;
import org.knime.email.nodes.reader.EmailReaderNodeSettings.MessageSelector;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.Attachment;
import org.knime.email.util.EmailUtil;
import org.knime.email.util.Header;
import org.knime.email.util.IDProvider;
import org.knime.email.util.Message;
import org.knime.testing.core.ExecutionContextExtension;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("javadoc")
@ExtendWith({ExecutionContextExtension.class})
public class GetEmailNodeProcessorTest {

    private static class MessageDateComparator implements Comparator<Message> {

        public static final MessageDateComparator INSTANCE = new MessageDateComparator();

        @Override
        public int compare(final Message o1, final Message o2) {
            return o1.time().compareTo(o2.time());
        }

    }

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    @Test
    public void testProcessor_withHTML(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        final List<Message> content = setupTestHTMLMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        final EmailReaderNodeProcessor processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        final BufferedDataTable table = processor.getMsgTable();
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withCC(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = false;
        final List<Message> content = setupTestCCMails();

        var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        EmailReaderNodeProcessor processor;
        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        final List<Message> msgs = extractMessages(table, false, null, false);

        mailSessionKey = TestUtil.getSessionKey(greenMail, TestUtil.USER3, TestUtil.PWD3);
        processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        table = processor.getMsgTable();
        msgs.addAll(extractMessages(table, false, null, false));

        TestUtil.checkMessages(content, msgs);
    }

    @Test
    public void testProcessor_withFlagsRead(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        final BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withFlagsUnRead(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = false;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        final BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withFlagsOnlyUnseen(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.Unseen;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);

        //checking for mails again should return empty table since we have SEEN all messages before
        table = getMessagesTable(exec, settings, mailSessionKey);
        assertEquals(0, table.size());
    }

    @Test
    public void testProcessor_withFlagsOnlySeen(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.Seen;
        setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        final BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        //all messages are unseen so far
        assertEquals(0, table.size());
    }

    @Test
    public void testProcessor_withFlagsSeenUnseen(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);

        //checking for mails again should return the same result because now they are seen which we also return
        table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withFlagsOnlyUnanswered(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        settings.m_messageAnsweredStatus = MessageAnswerStatus.Unanswered;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);

        //mark one message as replied
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        final MimeMessage messageToReply = receivedMessages[0];
        //this sets the answered flag on the message
        messageToReply.reply(true);
        //remove the answered message from the result
        TestUtil.removeContent(content, new Message(messageToReply, false));
        table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withFlagsOnlyAnswered(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        settings.m_messageAnsweredStatus = MessageAnswerStatus.Answered;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        //all messages are un-answered so far
        assertEquals(0, table.size());

        //mark one message as replied
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        final MimeMessage messageToReply = receivedMessages[0];
        //this sets the answered flag on the message
        messageToReply.reply(true);
        table = getMessagesTable(exec, settings, mailSessionKey);
        //only one message is answered
        assertEquals(1, table.size());
        final List<Message> answeredContent = getSubMessages(content, messageToReply);
        checkMsgTable(answeredContent, table, settings.m_markAsRead, true, true);
    }

    @Test
    public void testProcessor_withFlagsAnsweredUnanswered(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        settings.m_messageAnsweredStatus = MessageAnswerStatus.All;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead, false, true);

        //mark one message as replied
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        final MimeMessage messageToReply = receivedMessages[0];
        //this sets the answered flag on the message
        messageToReply.reply(true);
        table = getMessagesTable(exec, settings, mailSessionKey);
        //should return all messages
        checkMsgTable(content, table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withFlagsReadAnswered(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        settings.m_messageSeenStatus = MessageSeenStatus.Seen;
        settings.m_messageAnsweredStatus = MessageAnswerStatus.Answered;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        //all messages are un-answered so far
        assertEquals(0, table.size());

        //mark one message as replied
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        final MimeMessage messageToReply = receivedMessages[0];
        //this sets the answered flag on the message
        messageToReply.reply(true);
        table = getMessagesTable(exec, settings, mailSessionKey);
        //all messages are un-seen so far
        assertEquals(0, table.size());

        settings.m_messageSeenStatus = MessageSeenStatus.Unseen;
        table = getMessagesTable(exec, settings, mailSessionKey);
        //should return only the answered but un-seen message
        final List<Message> answeredContent = getSubMessages(content, messageToReply);
        checkMsgTable(answeredContent, table, settings.m_markAsRead);

        table = getMessagesTable(exec, settings, mailSessionKey);
        //the only answered message is now seen
        assertEquals(0, table.size());
    }

    @Test
    public void testProcessor_withFolder(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_markAsRead = true;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        //create sub-folder
        final String subFolderName = TestUtil.createSubFolder(mailSessionKey, TestUtil.FOLDER_INBOX, "subFolder");
        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        checkMsgTable(content, table, settings.m_markAsRead);

        settings.m_folder = subFolderName;
        table = getMessagesTable(exec, settings, mailSessionKey);
        //there shouldn't be any messages in the sub-folder
        assertEquals(0, table.size());
    }

    @Test
    public void testProcessor_withLimit(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_retrieveFlags = true;
        settings.m_limitMessages = true;
        settings.m_limitMessagesCount = 1;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        //newest first
        settings.m_messageSelector = MessageSelector.Newest;
        BufferedDataTable table = getMessagesTable(exec, settings, mailSessionKey);
        //should return only one message
        assertEquals(1, table.size());
        Message msg = getTopMessage(content, settings.m_messageSelector);
        checkMsgTable(List.of(msg), table, settings.m_markAsRead);

        //oldest first
        settings.m_messageSelector = MessageSelector.Oldest;
        table = getMessagesTable(exec, settings, mailSessionKey);
        //should return only one message
        assertEquals(1, table.size());
        msg = getTopMessage(content, settings.m_messageSelector);
        checkMsgTable(List.of(msg), table, settings.m_markAsRead);
    }

    @Test
    public void testProcessor_withHeaders(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        final List<Message> content = setupTestMails();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        settings.m_retrieveHeaders = true;
        EmailReaderNodeProcessor processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        //email table should always be returned
        BufferedDataTable table = processor.getMsgTable();
        checkMsgTable(content, table, settings.m_markAsRead, null, false);

        BufferedDataTable headerTable = processor.getHeaderTable();
        final List<Header> headers = getHeaders(headerTable);
        //check that the message id header is present and has the correct value
        for (final Header header : headers) {
            if (header.name().equals(EmailUtil.MESSAGEID_HEADER)) {
                assertEquals(header.id(), header.value());
            }
        }
        final Set<String> contentIDs = extractMessageID(content);
        final Set<String> headerIDs = extractMessageID(headers);
        //check that we have for each message a header and vice versa
        assertEquals(contentIDs, headerIDs);


        settings.m_retrieveHeaders = false;
        processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        //should contain messages
        table = processor.getMsgTable();
        assertNotEquals(0, table.size());
        //should be empty
        headerTable = processor.getHeaderTable();
        assertEquals(0, headerTable.size());
    }

    @Test
    public void testProcessor_withAttachement(final ExecutionContext exec) throws Exception {
        final EmailReaderNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_messageSeenStatus = MessageSeenStatus.All;
        final Pair<List<Message>, List<Attachment>> contentAttachments = setupTestMailsWithAttachment();
        final List<Message> content = contentAttachments.getFirst();
        final List<Attachment> goldAttachments = contentAttachments.getSecond();
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);

        settings.m_retrieveAttachments = true;
        EmailReaderNodeProcessor processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        //email table should always be returned
        BufferedDataTable table = processor.getMsgTable();
        assertEquals(content.size(), table.size());

        //shouldn't be empty
        BufferedDataTable attachTable = processor.getAttachTable();
        assertEquals(content.size(), attachTable.size());
        final List<Attachment> attachments = getAttachments(attachTable);
        final Set<String> contentIDs = extractMessageID(content);
        final Set<String> attachmentIDs = extractMessageID(attachments);
        assertTrue(sameAttachment(goldAttachments, attachments));
        //check that we have for each message a header and vice versa
        assertEquals(contentIDs, attachmentIDs);

        final BufferedDataTable headerTable = processor.getHeaderTable();
        //should be empty
        assertEquals(0, headerTable.size());


        settings.m_retrieveAttachments = false;
        processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        //should contain messages
        table = processor.getMsgTable();
        assertEquals(content.size(), table.size());
        //should be empty
        attachTable = processor.getAttachTable();
        assertEquals(0, attachTable.size());
    }

    private static boolean sameAttachment(final List<Attachment> goldAttachments, final List<Attachment> attachments) {
        if (goldAttachments.size() != attachments.size()) {
            return false;
        }
        for (final Attachment gold : goldAttachments) {
            boolean found = false;
            for (final Attachment attachment : attachments) {
                if (gold.id().equals(attachment.id())) {
                    //they should have the same content
                    if (!gold.name().equals(attachment.name())) {
                        return false;
                    }
                    if (gold.name().endsWith("html")) {
                        //somehow on Windows the html attachment uses \r\n and on Linux it uses only \n for line endings
                        final String html = new String(attachment.file(), StandardCharsets.UTF_8).replace("\r", "");
                        final String goldHtml = new String(gold.file(), StandardCharsets.UTF_8).replace("\r", "");
                        if (!html.equals(goldHtml)) {
                            return false;
                        }
                    } else if (!Arrays.equals(gold.file(), attachment.file())) {
                        return false;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static Message getTopMessage(final List<Message> content, final MessageSelector messageSelector) {
        Collections.sort(content, MessageDateComparator.INSTANCE);
        if (MessageSelector.Newest.equals(messageSelector)) {
            return content.get(content.size() - 1);
        }
        return content.get(0);
    }

    private static List<Message> getSubMessages(final List<Message> content,
        final jakarta.mail.Message... msgs)
                throws MessagingException {
        final List<Message> result = new ArrayList<>(msgs.length);
        for (final jakarta.mail.Message msg : msgs) {
            for (final Message c : content) {
                if (c.subject().equals(msg.getSubject())) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    private static <I extends IDProvider> Set<String> extractMessageID(final List<I> items) {
        final Set<String> ids = new HashSet<>(items.size());
        for (final I i : items) {
            ids.add(i.id());
        }
        return ids;
    }

    private static List<Header> getHeaders(final BufferedDataTable table) {
        final List<Header> result = new LinkedList<>();
        try (RowCursor cursor = table.cursor()) {
            while (cursor.canForward()) {
                final RowRead read = cursor.forward();
                final StringValue msgId = read.getValue(0);
                final StringValue name = read.getValue(1);
                final StringValue value = read.getValue(2);
                result.add(new Header(msgId.getStringValue(), name.getStringValue(), value.getStringValue()));
            }
        }
        return result;
    }

    private static List<Attachment> getAttachments(final BufferedDataTable table) throws IOException {
        final List<Attachment> result = new LinkedList<>();
        try (RowCursor cursor = table.cursor()) {
            while (cursor.canForward()) {
                final RowRead read = cursor.forward();
                final StringValue msgId = read.getValue(0);
                final StringValue name = read.getValue(1);
                final BinaryObjectDataValue value = read.getValue(2);
                try (InputStream is = value.openInputStream()) {
                    result.add(new Attachment(msgId.getStringValue(), name.getStringValue(), IOUtils.toByteArray(is)));
                }
            }
        }
        return result;
    }

    private static void checkMsgTable(final List<Message> content, final BufferedDataTable table,
        final boolean seen) {
        checkMsgTable(content, table, seen, null, true);
    }

    private static void checkMsgTable(final List<Message> content, final BufferedDataTable table,
        final boolean seen, final Boolean answered, final boolean hasFlags) {
        final List<Message> messages = extractMessages(table, seen, answered, hasFlags);
        TestUtil.checkMessages(content, messages);
    }

    private static List<Message> extractMessages(final BufferedDataTable table, final boolean seen,
        final Boolean answered, final boolean hasFlags) {
        @SuppressWarnings("deprecation")
        final List<Message> result = new ArrayList<>(table.getRowCount());
        try (RowCursor cursor = table.cursor()) {
            while (cursor.canForward()) {
                final RowRead read = cursor.forward();
                result.add(extractMessage(read));
                final ListDataValue flags;
                if (hasFlags || answered != null) {
                    flags = read.getValue(8);
                    assertEquals(seen, getFlag(flags, "SEEN"));
                    if (answered != null) {
                        assertEquals(answered, getFlag(flags, "ANSWERED"));
                    }
                }
            }
        }
        return result;
    }

    private static Message extractMessage(final RowRead read) {
        final StringValue msgId = read.getValue(0);
        final LocalDateTimeValue received = read.getValue(1);
        final StringValue subject = read.getValue(2);
        final StringValue text_plain = read.isMissing(3) ? null : read.getValue(3);
        final HTMLValue text_html = read.isMissing(4) ? null : read.getValue(4);
        final StringValue from = read.getValue(5);
        final ListDataValue to = read.isMissing(6) ? null : read.getValue(6);
        final ListDataValue cc = read.isMissing(7) ? null : read.getValue(7);
        return new Message(msgId.getStringValue(), received.getLocalDateTime(), subject.getStringValue(),
            text_plain == null ? null : text_plain.getStringValue(), text_html == null ? null : text_html.toString(),
                from.getStringValue(), to != null ? to.stream().map(DataCell::toString).toArray(String[]::new) : null,
                    cc != null ? cc.stream().map(DataCell::toString).toArray(String[]::new) : null);
    }

    private static boolean getFlag(final ListDataValue flags, final String flagName) {
        final List<Boolean> matches = flags.stream().filter(c -> c.toString().startsWith(flagName))
                .map(c -> Boolean.valueOf(c.toString().split(": ")[1])).collect(Collectors.toList());
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Flag not found");
        }
        return matches.get(0);
    }

    public static EmailReaderNodeSettings createSettings(final String folder) {
        final EmailReaderNodeSettings settings = new EmailReaderNodeSettings();
        settings.m_folder = folder;
        return settings;
    }

    public static BufferedDataTable getMessagesTable(final ExecutionContext exec, final EmailReaderNodeSettings settings)
            throws Exception {
        return getMessagesTable(exec, settings, TestUtil.getSessionKeyUser1(greenMail));
    }

    public static BufferedDataTable getMessagesTable(final ExecutionContext exec, final EmailReaderNodeSettings settings,
        final EmailSessionKey mailSessionKey) throws Exception {
        final EmailReaderNodeProcessor processor = new EmailReaderNodeProcessor(mailSessionKey, settings);
        processor.readEmailsAndFillTable(exec);
        final BufferedDataTable table = processor.getMsgTable();
        return table;
    }

    private static List<Message> setupTestMails() throws MessagingException, IOException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
        TestUtil.setupTestMails(serverSetup);
        return getAllMessages(false);
    }

    private static List<Message> setupTestCCMails() throws MessagingException, IOException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
        final LinkedHashMap<String, String> content = new LinkedHashMap<>(3);
        content.put("subject1", "<h1>body1</h1>");
        content.put("subject2", "<h1>body2</h1>");
        content.put("subject3", "<h1>body3</h1>");
        final Session session = GreenMailUtil.getSession(serverSetup);
        for (final Entry<String, String> e : content.entrySet()) {
            final MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setSubject(e.getKey());
            mimeMessage.setSentDate(new Date());
            mimeMessage.setFrom(TestUtil.USER2);
            mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.TO, TestUtil.USER1);
            mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.CC, TestUtil.USER3);
            mimeMessage.setContent(e.getValue(), "text/html");
            GreenMailUtil.sendMimeMessage(mimeMessage);
        }
        return getAllMessages(true);
    }

    private static List<Message> setupTestHTMLMails() throws MessagingException, IOException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
        final LinkedHashMap<String, String> content = new LinkedHashMap<>(3);
        content.put("subject1", "<h1>body1</h1>");
        content.put("subject2", "<h1>body2</h1>");
        content.put("subject3", "<h1>body3</h1>");
        final Session session = GreenMailUtil.getSession(serverSetup);
        for (final Entry<String, String> e : content.entrySet()) {
            final MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setSubject(e.getKey());
            mimeMessage.setSentDate(new Date());
            mimeMessage.setFrom(TestUtil.USER2);
            mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.TO, TestUtil.USER1);
            mimeMessage.setContent(e.getValue(), "text/html");
            GreenMailUtil.sendMimeMessage(mimeMessage);
        }
        return getAllMessages(true);
    }

    private Pair<List<Message>, List<Attachment>> setupTestMailsWithAttachment()
            throws MessagingException, IOException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();

        final Map<String, Pair<String, byte[]>> files = new HashMap<>();
        final Collection<String> fileNames = List.of("test.docx", "test.html", "test.jpg", "test.pdf", "test.png",
            "test.pptx", "test.txt", "test.xls", "test.xlsx");
        for (final String fileName : fileNames) {
            try (InputStream is = getClass().getResourceAsStream("/attachments/" + fileName)) {
                final String subject = "Find attached " + fileName;
                final String msg = "Message with attached " + fileName;
                final byte[] fileContent = IOUtils.toByteArray(is);
                final String contentType = getContentType(fileName.substring(fileName.lastIndexOf(".") + 1));
                final String fileDescription = "This is a " + fileName;
                GreenMailUtil.sendAttachmentEmail(TestUtil.USER1, TestUtil.USER2, subject, msg, fileContent, contentType,
                    fileName, fileDescription, serverSetup);
                files.put(subject, new Pair<>(fileName, fileContent));
            }
        }

        final List<Attachment> attachments = new LinkedList<>();
        final List<Message> messages = getAllMessages(false);
        for (final Message message : messages) {
            final Pair<String, byte[]> attachment = files.get(message.subject());
            attachments.add(new Attachment(message.id(), attachment.getFirst(), attachment.getSecond()));
        }
        return new Pair<>(messages, attachments);
    }

    private static String getContentType(final String ext) {
        switch (ext) {
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "html":
                return "text/html";
            case "jpg":
                return "image/jpeg";
            case "pdf":
                return "application/pdf";
            case "png":
                return "image/png";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                throw new IllegalArgumentException("Unexpected value: " + ext);
        }
    }

    private static List<Message> getAllMessages(final boolean html) throws MessagingException, IOException {
        return TestUtil.getAllGreenMailMessages(greenMail, html);
    }
}

