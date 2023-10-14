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
 *   Sep 26, 2023 (wiswedel): created
 */
package org.knime.email.session;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.knime.core.node.NodeLogger;
import org.knime.core.node.util.CheckUtils;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * The main entrance point to work with emails.
 * @author wiswedel
 */
public final class EmailSession implements AutoCloseable {

    static final NodeLogger LOGGER = NodeLogger.getLogger(EmailSession.class);

    private final Store m_emailStore;

    EmailSession(final Store emailStore) {
        m_emailStore = emailStore;
    }

    /**
     * Returns all accessible email folders.
     *
     * @return the name of all available email folders
     * @throws MessagingException
     */
    public String[] listFolders() throws MessagingException {
        final var defaultFolder = m_emailStore.getDefaultFolder();
        List<String> folders = new ArrayList<>();
        collectSubFolders(defaultFolder, folders::add);
        return folders.toArray(String[]::new);
    }

    private static void collectSubFolders(final Folder parent, final Consumer<String> collector)
            throws MessagingException {
        for (var subfolder : parent.list()) {
            if ((subfolder.getType() & Folder.HOLDS_MESSAGES) != 0) {
                collector.accept(subfolder.getFullName());
            }
            if ((subfolder.getType() & Folder.HOLDS_FOLDERS) != 0) {
                collectSubFolders(subfolder, collector);
            }
        }
    }

    private Folder getFolder(final String folderFullName) throws MessagingException {
        Folder f = m_emailStore.getFolder(folderFullName);
        CheckUtils.check(f.exists(), MessagingException::new,
            () -> "Folder '%s' does not exist".formatted(folderFullName));
        return f;
    }

    /**
     * Opens the given folder in read only mode.
     * @param folderFullName the name of the folder to open
     * @return the {@link Folder}
     * @throws MessagingException if the folder cannot be accessed
     */
    public Folder openFolder(final String folderFullName) throws MessagingException {
        return openFolder(folderFullName, Folder.READ_ONLY);
    }

    /**
     * Opens the given folder in read and write mode.
     * @param folderFullName the name of the folder to open
     * @return the {@link Folder}
     * @throws MessagingException if the folder cannot be accessed
     */
    public Folder openFolderForWriting(final String folderFullName) throws MessagingException {
        return openFolder(folderFullName, Folder.READ_WRITE);
    }

    private Folder openFolder(final String folderFullName, final int flag) throws MessagingException {
        var f = getFolder(folderFullName);
        f.open(flag);
        return f;
    }

    @Override
    public void close() throws MessagingException {
        m_emailStore.close();
    }

}
