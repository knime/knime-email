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
package org.knime.email.nodes.reader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.context.ports.PortsConfiguration;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.email.port.EmailSessionPortObject;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.util.EmailNodeUtil;

/**
 * Get email node model.
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public class EmailReaderNodeModel extends NodeModel {

    static final NodeLogger LOGGER = NodeLogger.getLogger(EmailReaderNodeModel.class);

    private EmailReaderNodeSettings m_settings = new EmailReaderNodeSettings();

    /**
     * Instantiate a new Value Lookup Node
     *
     * @param configuration node description
     * @param modelSettingsClass a reference to {@link EmailReaderNodeSettings}
     * @param portsConfiguration
     */
    EmailReaderNodeModel(final WebUINodeConfiguration configuration,
        final Class<EmailReaderNodeSettings> modelSettingsClass, final PortsConfiguration portsConfiguration) {
        super(portsConfiguration.getInputPorts(), portsConfiguration.getOutputPorts());
    }

    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
        throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNotBlank(m_settings.m_folder), "Email folder name not selected");
        EmailNodeUtil.checkIncomingAvailable(inSpecs);
        return null;
    }

    @Override
    protected PortObject[] execute(final PortObject[] inObjects, final ExecutionContext exec)
        throws Exception {
        final EmailSessionPortObject in = (EmailSessionPortObject)inObjects[0];
        final EmailSessionKey mailSessionKey = in.getEmailSessionKey().orElseThrow(() ->
        new InvalidSettingsException("No mail session available"));
        final var processor = new EmailReaderNodeProcessor(mailSessionKey, m_settings);
        processor.readEmailsAndFillTable(exec);

        final List<PortObject> list = new ArrayList<>();
        list.add(processor.getMsgTable());
        if (m_settings.m_outputAttachments) {
            list.add(processor.getAttachTable());
        }
        if (m_settings.m_outputHeaders) {
            list.add(processor.getHeaderTable());
        }
        return list.toArray(PortObject[]::new);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings = DefaultNodeSettings.loadSettings(settings, EmailReaderNodeSettings.class);
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        DefaultNodeSettings.saveSettings(EmailReaderNodeSettings.class, m_settings, settings);
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
    }

    @Override
    protected void reset() {
    }

}
