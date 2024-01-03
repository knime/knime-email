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

package org.knime.email;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

import org.knime.email.session.EmailIncomingSession;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.EmailUtil;
import org.knime.email.util.Message;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.imap.ImapServer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * Helper class that contains convenient methods for unit tests.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
public final class TestUtil {

    /** First email user. */
    public static final String USER1 = "knime-1@localhost";

    /** First email user password. */
    public static final String PWD1 = "knime1234";

    /** Second email user. */
    public static final String USER2 = "knime-2@localhost";

    /** Second email login. */
    public static final String PWD2 = "knime1234";

    /** Second email user. */
    public static final String USER3 = "knime-3@localhost";

    /** Second email login. */
    public static final String PWD3 = "knime1234";

    /** {@link GreenMailConfiguration} with the two test users. */
    public static final GreenMailConfiguration CONFIG =
            new GreenMailConfiguration().withUser(USER1, PWD1).withUser(USER2, PWD2).withUser(USER3, PWD3);

    /** {@link ServerSetup} for SMTP and IMAP tests. */
    public static final ServerSetup[] SETUP = new ServerSetup[]{ServerSetup.SMTP.dynamicPort().setVerbose(true),
        ServerSetup.IMAP.dynamicPort().setVerbose(true)};

    /** Name of the standard inbox folder where all send emails are located. */
    public static final String FOLDER_INBOX = "INBOX";

    /** Pattern that identifies a standard message id. */
    public static final Pattern STANDARD_M_ID_PATTERN = Pattern.compile("<.*@127.0.0.1>");

    /** Pattern that identifies a KNIME generated message id. */
    public static final Pattern KNIME_M_ID_PATTERN = Pattern.compile(EmailUtil.KNIME_PREFIX + ".*");

    public static EmailSessionKey getSessionKeyUser1(final GreenMailExtension greenMail) {
        return getSessionKey(greenMail, USER1, PWD1);
    }

    public static EmailSessionKey getSessionKey(final GreenMailExtension greenMail, final String user,
        final String pwd) {
        final ImapServer imap = greenMail.getImap();
        final ServerSetup serverSetup = imap.getServerSetup();
        final var mailSessionKey = EmailSessionKey.builder() //
            .withImap(b -> b //
                .imapHost(serverSetup.getBindAddress(), serverSetup.getPort()) //
                .imapSecureConnection(false)) //
            .withAuth(user, pwd) //
            .withProperties(new Properties()).build();
        return mailSessionKey;
    }

    public static EmailIncomingSession getSessionUser1(final GreenMailExtension greenMail) {
        final EmailSessionKey key = getSessionKeyUser1(greenMail);
        try {
            return key.connectIncoming();
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Store getStoreUser1(final GreenMailExtension greenMail) {
        try {
            final Store store = greenMail.getImap().createSession().getStore();
            store.connect(USER1, PWD1);
            return store;
        } catch (final MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Folder createFolder(final Store store, final String name) throws MessagingException {
        final Folder someFolder = store.getFolder(name);
        if (!someFolder.exists()) {
            if (someFolder.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS)) {
                someFolder.setSubscribed(true);
            }
        }
        return someFolder;
    }

    @SuppressWarnings("resource")
    public static String createSubFolder(final EmailSessionKey mailSessionKey, final String parentFolder,
        final String subFolderName)
                throws MessagingException {
        String resultFolderName = null;
        try (EmailIncomingSession session = mailSessionKey.connectIncoming();) {
            final Folder inbox = session.openFolderForWriting(parentFolder);
            final Folder subFolder = createSubFolder(inbox, subFolderName);
            resultFolderName = subFolder.getFullName();
        }
        return resultFolderName;
    }

    public static Folder createSubFolder(final Folder parent, final String name) throws MessagingException {
        final Folder subFolder = parent.getFolder(name);
        if (!subFolder.exists()) {
            if (subFolder.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS)) {
                subFolder.setSubscribed(true);
            }
        }
        return subFolder;
    }

    public static List<Message> getAllGreenMailMessages(final GreenMailExtension greenMail, final boolean html)
            throws MessagingException, IOException {
        return getAllGreenMailMessages(greenMail, html, null);
    }

    @SuppressWarnings("resource")
    public static List<Message> getAllGreenMailMessages(final GreenMailExtension greenMail, final boolean html,
        final String folderName) throws MessagingException, IOException {
        final jakarta.mail.Message[] messages;
        if (folderName == null) {
            messages = greenMail.getReceivedMessages();
        } else {
            final Store store = getStoreUser1(greenMail);
            final Folder inbox = store.getFolder(folderName);
            inbox.open(Folder.READ_ONLY);
            messages = inbox.getMessages();
        }
        final List<Message> result = new LinkedList<>();
        for (final jakarta.mail.Message message : messages) {
            if (folderName == null) {
                result.add(new Message(message, html));
            } else {
                final Folder folder = message.getFolder();
                if (folder != null && folderName.equals(folder.getFullName())) {
                    result.add(new Message(message, html));
                }
            }
        }
        return result;
    }

    public static void setupTestMails(final ServerSetup serverSetup) {
        final LinkedHashMap<String, String> content = new LinkedHashMap<>(3);
        content.put("subject1", "body1");
        content.put("subject2", "body2");
        content.put("subject3", "body3");
        for (final Entry<String, String> e : content.entrySet()) {
            GreenMailUtil.sendTextEmail(USER1, USER2, e.getKey(), e.getValue(), serverSetup);
        }
    }

    public static void removeContent(final List<Message> content, final Message msg) {
        //unfortunately the received date is somehow different which is why we need to use the msg id for removal
        for (final Message c : content) {
            if (TestUtil.sameMessage(msg, c)) {
                content.remove(c);
                return;
            }
        }
        throw new IllegalStateException("Message not found");
    }

    /**
     * This method is necessary since the record equals is not taking care of arrays and also the time has different
     * granularity!
     */
    public static boolean sameMessage(final Message m1, final Message m2) {
        if (!m1.id().equals(m2.id())) {
            return false;
        }
        if (!m1.subject().equals(m2.subject())) {
            return false;
        }
        //somehow the milliseconds are dropped when retrieving the messages via SMB
        if (!m1.time().truncatedTo(ChronoUnit.SECONDS).equals(m2.time().truncatedTo(ChronoUnit.SECONDS))) {
            return false;
        }
        String txt1 = m1.textHTML();
        String txt2 = m2.textHTML();
        if (txt1 == null ? txt2 != null : !txt1.equals(txt2)) {
            return false;
        }
        txt1 = m1.textPlain();
        txt2 = m2.textPlain();
        if (txt1 == null ? txt2 != null : !txt1.equals(txt2)) {
            return false;
        }
        if (!m1.from().equals(m2.from())) {
            return false;
        }
        if (!Arrays.equals(m1.to(), m2.to())) {
            return false;
        }
        if (!Arrays.equals(m1.cc(), m2.cc())) {
            return false;
        }
        return true;
    }

    public static void checkMessages(final List<Message> content, final List<Message> messages) {
        final List<Message> items2remove = new ArrayList<>(content);
        for (final Message message : messages) {
            removeContent(items2remove, message);
        }
        if (!items2remove.isEmpty()) {
            throw new IllegalStateException("Fewer messages found in result table");
        }
    }
}
