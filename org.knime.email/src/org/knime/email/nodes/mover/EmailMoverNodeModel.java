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
 *   21 Oct 2022 (jasper): created
 */
package org.knime.email.nodes.mover;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeModel;
import org.knime.email.port.EmailSessionPortObject;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.EmailNodeUtil;

/**
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public class EmailMoverNodeModel extends WebUINodeModel<EmailMoverNodeSettings> {

    static final NodeLogger LOGGER = NodeLogger.getLogger(EmailMoverNodeModel.class);

    /**
     * Instantiate a new Value Lookup Node
     *
     * @param configuration node description
     * @param modelSettingsClass a reference to {@link EmailMoverNodeSettings}
     */
    EmailMoverNodeModel(final WebUINodeConfiguration configuration,
        final Class<EmailMoverNodeSettings> modelSettingsClass) {
        super(configuration, modelSettingsClass);
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs, final EmailMoverNodeSettings modelSettings)
        throws InvalidSettingsException {
        EmailNodeUtil.checkIncomingAvailable(inSpecs);
        return new DataTableSpec[]{};
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec,
        final EmailMoverNodeSettings modelSettings)
        throws Exception {
        final EmailSessionPortObject in = (EmailSessionPortObject)inObjects[0];
        final EmailSessionKey mailSessionKey =
            in.getEmailSessionKey().orElseThrow(() -> new InvalidSettingsException("No mail session available"));
        final var table = (BufferedDataTable) inObjects[1];
        CheckUtils.checkSetting(table.getSpec().findColumnIndex(modelSettings.m_messageIds) >= 0,
                "Please specify an existing column for the Message-IDs.");
        final var processor = new EmailMoverNodeProcessor(mailSessionKey, modelSettings);
        processor.moveMessages(exec, table);
        return new BufferedDataTable[]{};
    }

}
