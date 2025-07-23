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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.knime.core.node.NodeLogger;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.core.webui.node.dialog.defaultdialog.widget.handler.WidgetHandlerException;
import org.knime.email.port.EmailSessionPortObjectSpec;
import org.knime.email.session.EmailSessionKey;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.util.CompatibleColumnsProvider.StringColumnsProvider;

/**
 * Contains choicesProviders used by the email nodes
 *
 * @author Jannik Löscher, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class UIChoices {

    private UIChoices() {
        // utility class
    }

    /**
     * A choices provider to select a string column containing a message ID. The table with the column is expected to be
     * connected to the second port (1).
     */
    public static final class MessageIDColumnChoicesProvider extends StringColumnsProvider {

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public int getInputTableIndex() {
            return 1;
        }
    }

    /**
     * A choices provider to select folders from an email session. The email session port is expected to be connected to
     * the first port (1).
     */
    public static final class FolderProvider implements StringChoicesProvider {

        private static final NodeLogger LOGGER = NodeLogger.getLogger(UIChoices.FolderProvider.class);

        private static final String MISSING_SESSION_MSG = "Rexecute the connector node to restore the email session.";

        @Override
        public void init(final StateProviderInitializer initializer) {
            initializer.computeAfterOpenDialog();
        }

        @Override
        public List<String> choices(final NodeParametersInput context) {
            try {
                Optional<PortObjectSpec> optional = context.getInPortSpec(0);
                final EmailSessionPortObjectSpec in = (EmailSessionPortObjectSpec)optional.orElseThrow(() -> {
                    return new IllegalStateException(MISSING_SESSION_MSG);
                });
                final EmailSessionKey sessionKey =
                    in.getEmailSessionKey().orElseThrow(() -> new IllegalStateException(MISSING_SESSION_MSG));
                try (final var mailSession = sessionKey.connectIncoming()) {
                    final String[] folders = mailSession.listFolders();
                    Arrays.sort(folders);
                    return Arrays.asList(folders);
                }
            } catch (final Exception e) { // NOSONAR catch all exceptions here
                LOGGER.debug("Error fetching email folders", e);
                throw new WidgetHandlerException("Unable to retrieve email folders: " + e.getMessage());
            }
        }
    }
}
