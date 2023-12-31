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
package org.knime.email.nodes.mover;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionMonitor;
import org.knime.email.session.EmailIncomingSession;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.EmailUtil;

import jakarta.mail.Flags.Flag;

/**
 *
 * @author wiswedel
 */
final class EmailMoverNodeProcessor {

    private final EmailSessionKey m_mailSessionKey;
    private EmailMoverNodeSettings m_settings;



    EmailMoverNodeProcessor(final EmailSessionKey mailSessionKey, final EmailMoverNodeSettings settings) {
        m_mailSessionKey = mailSessionKey;
        m_settings = settings;
    }

    void moveMessages(final ExecutionMonitor exec, final BufferedDataTable idTable) throws Exception {
        final int idIdx = idTable.getSpec().findColumnIndex(m_settings.m_messageIds);
        try (EmailIncomingSession session = m_mailSessionKey.connectIncoming();
                final var sourceFolder = session.openFolderForWriting(m_settings.m_sourceFolder);
             final var targetFolder = session.openFolder(m_settings.m_targetFolder);
             ){
            exec.setMessage("Processing input table..");
            final var messages = EmailUtil.findMessages(exec.createSubProgress(0.7), sourceFolder, idTable, idIdx);
            if (sourceFolder instanceof IMAPFolder imapfolder) {
                exec.checkCanceled();
                exec.setProgress(0.8,
                    "Moving " + messages.length + " messages to target folder: " + m_settings.m_targetFolder);
                imapfolder.moveMessages(messages, targetFolder);
            } else {
                // copy messages over and delete them
                exec.checkCanceled();
                exec.setProgress(0.8,
                    "Copying " + messages.length + " messages to target folder: " + m_settings.m_targetFolder);
                sourceFolder.copyMessages(messages, targetFolder);
                exec.setProgress(0.9,
                    "Deleting " + messages.length + " messages from source folder: " + m_settings.m_sourceFolder);
                EmailUtil.flagMessages(sourceFolder, messages, Flag.DELETED, true);
                sourceFolder.expunge();
            }
        }
        exec.setProgress(1);
    }

}
