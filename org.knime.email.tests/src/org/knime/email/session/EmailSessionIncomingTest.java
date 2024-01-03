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
package org.knime.email.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.knime.email.TestUtil.CONFIG;
import static org.knime.email.TestUtil.SETUP;
import static org.knime.email.TestUtil.USER1;
import static org.knime.email.TestUtil.USER2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knime.email.TestUtil;

import com.icegreen.greenmail.imap.ImapServer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * Test IMAP connectivity, including folder listing etc.
 * 
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("java:S5960") // assertions are ok in tests
final class EmailSessionIncomingTest {
    
    private static final String INBOX = "INBOX";
    
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    @SuppressWarnings("static-method")
    @Test
    void connectValidUser() throws MessagingException {
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        try (final var mailSession = mailSessionKey.connectIncoming()) {
            final String[] folders = mailSession.listFolders();
            assertArrayEquals(new String[] {INBOX}, folders, "identical folder list");
        }
    }

    @SuppressWarnings("static-method")
    @Test
    void connectInvalidUser() {
        //use this test to check if username and password are really used
        final ImapServer imap = greenMail.getImap();
        final ServerSetup serverSetup = imap.getServerSetup();
        final var invalidUserMailSessionKey = EmailSessionKey.builder() //
            .withImap(b -> b //
                .imapHost(serverSetup.getBindAddress(), serverSetup.getPort()) //
                .imapSecureConnection(false)) //
            .withAuth("INVALID_USER", "INVALID_PASSWORD") //
            .withProperties(new Properties()).build();
        Assertions.assertThrows(AuthenticationFailedException.class, //
            invalidUserMailSessionKey::connectIncoming, //
            "unable to connecti with wrong user/pass");
    }

    @SuppressWarnings({"static-method", "resource"})
    @Test
    void listFolders() throws MessagingException {

        //INBOX is always there
        final List<String> first = new ArrayList<>(Arrays.asList(INBOX));
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){
            List<String> second = new ArrayList<>(Arrays.asList(session.listFolders()));
            assertIterableEquals(first, second, "identical folder list");

            //test if we also get the newly created folder
            final Store store = TestUtil.getStoreUser1(greenMail);
            final Folder someFolder = TestUtil.createFolder(store, "MyNewTestFolder");
            first.add(someFolder.getFullName());
            //also create and check for sub-folder that they use the full name
            final Folder subFolder = TestUtil.createSubFolder(someFolder, "MySubFolder");
            first.add(subFolder.getFullName());
            second = Arrays.asList(session.listFolders());
            assertIterableEquals(first, second, "identical folder list");
        }
    }

    @SuppressWarnings({"static-method","resource"})
    @Test
    void openFolder() throws MessagingException {
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){

            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body",
                greenMail.getSmtp().getServerSetup());
            Folder inbox = session.openFolder(INBOX);
            assertTrue(inbox.exists(), "inbox exists");
            Message[] messages = inbox.getMessages();
            assertEquals(1, messages.length, "message count");
            Message message = messages[0];
            assertEquals("some subject", message.getSubject(), "email subject matches");

            //write operation should fail on this folder
            message.setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);

            inbox = session.openFolder(INBOX);
            //message should be still there
            messages = inbox.getMessages();
            assertEquals(1, messages.length, "message count");
            message = messages[0];
            assertEquals("some subject", message.getSubject(), "email subject matches");
        }
    }

    @SuppressWarnings({"static-method", "resource"})
    @Test
    void openFolderForWriting() throws MessagingException {
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){

            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", greenMail.getSmtp().getServerSetup());
            Folder inbox = session.openFolderForWriting(INBOX);
            assertTrue(inbox.exists(), "inbox exists");
            final Message[] messages = inbox.getMessages();
            assertEquals(1, messages.length, "message count");
            final Message message = messages[0];
            assertEquals("some subject", message.getSubject(), "email subject matches");

            //write operation should fail on this folder
            message.setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);

            inbox = session.openFolder(INBOX);
            assertEquals(0, inbox.getMessages().length, "message count");
        }

    }

    @SuppressWarnings({"static-method", "resource"})
    @Test
    void testClose() throws MessagingException {
        final EmailIncomingSession session = TestUtil.getSessionUser1(greenMail);
        assertDoesNotThrow(() -> session.openFolder(INBOX), "can open folder");
        session.close();
        assertThrows(IllegalStateException.class, () -> session.openFolder(INBOX), "can not open folder after close");
    }
}
