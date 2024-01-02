/**
 *
 */
package org.knime.email.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.knime.core.node.NodeLogger;
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
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 *
 */
public class EmailSessionTest {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    private static final NodeLogger LOGGER = NodeLogger.getLogger(EmailSessionTest.class);

    @Test
    public void connect_validUser() throws MessagingException {
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        try (final var mailSession = mailSessionKey.connectIncoming()) {
            for (final String s : mailSession.listFolders()) {
                LOGGER.info("Folder: " + s);
            }
        }
    }

    @Test
    public void connect_invalidUser() {
        //use this test to check if username and password are really used
        final ImapServer imap = greenMail.getImap();
        final ServerSetup serverSetup = imap.getServerSetup();
        final var invalidUserMailSessionKey = EmailSessionKey.builder() //
                .host(serverSetup.getBindAddress(), serverSetup.getPort()) //
                .user("INVALID_USER", "INVALID_PASSWORD") //
                .protocol(imap.getProtocol(), false) //
                .properties(new Properties()).build();
        Assertions.assertThrows(AuthenticationFailedException.class, () -> invalidUserMailSessionKey.connectIncoming());
    }

    @Test
    public void listFolders() throws MessagingException {

        //INBOX is always there
        final List<String> first = new ArrayList<>(Arrays.asList("INBOX"));
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){
            List<String> second = new ArrayList<>(Arrays.asList(session.listFolders()));
            assertTrue(first.size() == second.size() && first.containsAll(second) && second.containsAll(first));

            //test if we also get the newly created folder
            final Store store = TestUtil.getStoreUser1(greenMail);
            final Folder someFolder = TestUtil.createFolder(store, "MyNewTestFolder");
            first.add(someFolder.getFullName());
            //also create and check for sub-folder that they use the full name
            final Folder subFolder = TestUtil.createSubFolder(someFolder, "MySubFolder");
            first.add(subFolder.getFullName());
            second = Arrays.asList(session.listFolders());
            assertTrue(first.size() == second.size() && first.containsAll(second) && second.containsAll(first));
        }
    }

    @Test
    public void openFolder() throws MessagingException {
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){

            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", greenMail.getSmtp().getServerSetup());
            Folder inbox = session.openFolder("INBOX");
            assertTrue(inbox.exists());
            Message[] messages = inbox.getMessages();
            assertEquals(1, messages.length);
            Message message = messages[0];
            assertEquals("some subject", message.getSubject());

            //write operation should fail on this folder
            message.setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);

            inbox = session.openFolder("INBOX");
            //message should be still there
            messages = inbox.getMessages();
            assertEquals(1, messages.length);
            message = messages[0];
            assertEquals("some subject", message.getSubject());
        }
    }

    @Test
    public void openFolderForWriting() throws MessagingException {
        try (EmailIncomingSession session = TestUtil.getSessionUser1(greenMail)){

            GreenMailUtil.sendTextEmail(USER1, USER2, "some subject", "some body", greenMail.getSmtp().getServerSetup());
            Folder inbox = session.openFolderForWriting("INBOX");
            assertTrue(inbox.exists());
            final Message[] messages = inbox.getMessages();
            assertEquals(1, messages.length);
            final Message message = messages[0];
            assertEquals("some subject", message.getSubject());

            //write operation should fail on this folder
            message.setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);

            inbox = session.openFolder("INBOX");
            assertEquals(0, inbox.getMessages().length);
        }

    }

    @Test
    public void close() throws MessagingException {
        final EmailIncomingSession session = TestUtil.getSessionUser1(greenMail);
        final Folder inbox = session.openFolder("INBOX");

        session.close();
        assertThrows(IllegalStateException.class, () -> session.openFolder("INBOX"));
    }
}
