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

package org.knime.email.util;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.knime.email.TestUtil.CONFIG;
import static org.knime.email.TestUtil.SETUP;
import static org.knime.email.TestUtil.USER1;
import static org.knime.email.TestUtil.USER2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knime.email.TestUtil;
import org.knime.email.session.EmailSession;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.Flags.Flag;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

class EmailUtilTest {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    @Test
    void getMessageId() throws MessagingException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
        try (EmailSession session = TestUtil.getSessionUser1(greenMail)) {
            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "some other subject", "some  other body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "yet another subject", "and yet another body", serverSetup);
            try (Folder inbox = session.openFolderForWriting("INBOX")) {
                final Message[] messages = inbox.getMessages();
                for (final Message message : messages) {
                    final String id = EmailUtil.getMessageId(message);
                    assertTrue(TestUtil.STANDARD_M_ID_PATTERN.matcher(id).matches());
                }
            }
        }
        //create a message without a message id header
        final MimeMessage mimeMessage =
                GreenMailUtil.createTextEmail(USER1, USER2, "yet another subject", "and yet another body", serverSetup);
        mimeMessage.removeHeader(EmailUtil.MESSAGEID_HEADER);
        final String messageId = EmailUtil.getMessageId(mimeMessage);
        assertTrue(TestUtil.KNIME_M_ID_PATTERN.matcher(messageId).matches());
    }

    @Test
    void findMessageById() throws MessagingException {
        try (EmailSession session = TestUtil.getSessionUser1(greenMail)) {
            final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "some other subject", "some  other body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "yet another subject", "and yet another body", serverSetup);
            try (Folder inbox = session.openFolderForWriting("INBOX")) {
                final Message[] messages = inbox.getMessages();
                for (final Message message : messages) {
                    final String messageId = EmailUtil.getMessageId(message);
                    final Message message2 = EmailUtil.findMessageById(messageId, inbox);
                    assertEquals(message, message2);
                }
            }
        }
    }

    @Test
    void flagMessages() throws MessagingException {
        try (EmailSession session = TestUtil.getSessionUser1(greenMail)) {
            final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "some other subject", "some  other body", serverSetup);
            GreenMailUtil.sendTextEmail(USER1, USER2, "yet another subject", "and yet another body", serverSetup);
            try (Folder inbox = session.openFolderForWriting("INBOX")) {
                final Message[] messages = inbox.getMessages();
                for (final Message message : messages) {
                    assertFalse(message.isSet(Flag.FLAGGED));
                }
                EmailUtil.flagMessages(inbox, messages, Flag.FLAGGED, true);
            }
            try (Folder inbox = session.openFolder("INBOX")) {
                final Message[] messages = inbox.getMessages();
                for (final Message message : messages) {
                    assertTrue(message.isSet(Flag.FLAGGED));
                }
            }
        }
    }
}
