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

package org.knime.email.nodes.mover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.knime.email.TestUtil.CONFIG;
import static org.knime.email.TestUtil.SETUP;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataTableSpecCreator;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.v2.RowContainer;
import org.knime.core.data.v2.RowWrite;
import org.knime.core.data.v2.RowWriteCursor;
import org.knime.core.data.v2.value.ValueInterfaces.StringWriteValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.email.TestUtil;
import org.knime.email.nodes.mover.EmailMoverNodeProcessor;
import org.knime.email.nodes.mover.EmailMoverNodeSettings;
import org.knime.email.util.Message;
import org.knime.testing.core.ExecutionContextExtension;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.MessagingException;

/**
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("javadoc")
@ExtendWith({ExecutionContextExtension.class})
public class MoveEmailNodeProcessorTest {

    private static final String ID_COL = "ID";
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    @Test
    public void testProcessor_all(final ExecutionContext exec) throws Exception {
        //create target folder
        final String targetFolderName =
                TestUtil.createSubFolder(TestUtil.getSessionKeyUser1(greenMail), TestUtil.FOLDER_INBOX, "targetFolder");
        final EmailMoverNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_messageIds = ID_COL;
        settings.m_targetFolder = targetFolderName;

        final List<Message> testMails = setupTestMails();
        List<Message> sourceMessages = getAllMessages(TestUtil.FOLDER_INBOX);
        TestUtil.checkMessages(testMails, sourceMessages);
        List<Message> targetMessages = getAllMessages(settings.m_targetFolder);
        assertEquals(0, targetMessages.size());

        //now move all messages to the target folder
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        final EmailMoverNodeProcessor processor = new EmailMoverNodeProcessor(mailSessionKey, settings);
        final BufferedDataTable table = createMsgIdTable(exec, testMails);
        processor.moveMessages(exec, table);
        sourceMessages = getAllMessages(settings.m_sourceFolder);
        assertEquals(0, sourceMessages.size());
        targetMessages = getAllMessages(settings.m_targetFolder);
        TestUtil.checkMessages(testMails, targetMessages);
    }

    @Test
    public void testProcessor_partial(final ExecutionContext exec) throws Exception {
        //create target folder
        final String targetFolderName =
                TestUtil.createSubFolder(TestUtil.getSessionKeyUser1(greenMail), TestUtil.FOLDER_INBOX, "targetFolder");
        final EmailMoverNodeSettings settings = createSettings(TestUtil.FOLDER_INBOX);
        settings.m_messageIds = ID_COL;
        settings.m_targetFolder = targetFolderName;

        final List<Message> testMails = setupTestMails();

        //now move all except for one messages to the target folder
        final var mailSessionKey = TestUtil.getSessionKeyUser1(greenMail);
        final EmailMoverNodeProcessor processor = new EmailMoverNodeProcessor(mailSessionKey, settings);
        final Message retainedMessage = testMails.remove(0);
        final BufferedDataTable table = createMsgIdTable(exec, testMails);
        processor.moveMessages(exec, table);
        final List<Message> sourceMessages = getAllMessages(settings.m_sourceFolder);
        TestUtil.checkMessages(List.of(retainedMessage), sourceMessages);
        final List<Message> targetMessages = getAllMessages(settings.m_targetFolder);
        TestUtil.checkMessages(testMails, targetMessages);
    }

    private static BufferedDataTable createMsgIdTable(final ExecutionContext exec, final List<Message> testMails)
            throws Exception {
        final DataTableSpec spec = new DataTableSpecCreator()
                .addColumns(new DataColumnSpecCreator(ID_COL, StringCell.TYPE).createSpec()).createSpec();
        try (final RowContainer rc = exec.createRowContainer(spec, false);
                final RowWriteCursor cursor = rc.createCursor();) {
            long idx = 0;
            for (final Message message : testMails) {
                final RowWrite write = cursor.forward();
                write.setRowKey(RowKey.createRowKey(idx++));
                write.<StringWriteValue> getWriteValue(0).setStringValue(message.id());
            }
            return rc.finish();
        }
    }

    private static EmailMoverNodeSettings createSettings(final String folder) {
        final EmailMoverNodeSettings settings = new EmailMoverNodeSettings();
        settings.m_sourceFolder = folder;
        return settings;
    }

    private static List<Message> setupTestMails() throws MessagingException, IOException {
        final ServerSetup serverSetup = greenMail.getSmtp().getServerSetup();
        TestUtil.setupTestMails(serverSetup);
        return getAllMessages(TestUtil.FOLDER_INBOX);
    }

    private static List<Message> getAllMessages(final String folder) throws MessagingException, IOException {
        return TestUtil.getAllGreenMailMessages(greenMail, false, folder);
    }
}

