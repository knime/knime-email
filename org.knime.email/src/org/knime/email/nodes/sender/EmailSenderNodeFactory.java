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
 *   Dec 26, 2023 (wiswedel): created
 */
package org.knime.email.nodes.sender;

import java.util.Optional;

import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.report.IReportPortObject;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;

/**
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
public final class EmailSenderNodeFactory extends ConfigurableNodeFactory<EmailSenderNodeModel>
    implements NodeDialogFactory {

    private static final String FULL_DESCRIPTION = """
            Sends Emails to a list of recipients, supporting html content, reports, file attachments etc.
            """;

    private static final String INPUT_FLOWVAR_IDENTIFIER = "Flow Variables";

    private static final String INPUT_REPORT_IDENTIFIER = "Report";

    private static final WebUINodeConfiguration CONFIGURATION = WebUINodeConfiguration.builder() //
        .name("Email Sender (Labs)") //
        .icon("mailsend.png") //
        .shortDescription("Sends Emails to a recipient list.") //
        .fullDescription(FULL_DESCRIPTION) //
        .modelSettingsClass(EmailSenderNodeSettings.class) //
        .nodeType(NodeType.Other) //
        .addInputPort(INPUT_FLOWVAR_IDENTIFIER, FlowVariablePortObject.TYPE,
            "Any types of variables that can be used in the email body", true) //
        .addInputPort(INPUT_REPORT_IDENTIFIER, IReportPortObject.TYPE, "A report defining the content of the email. " //
            + "In case the email is sent in text format, the report is attached as PDF file", true) //
        .sinceVersion(5, 3, 0) //
        .build();


    @Override
    protected NodeDescription createNodeDescription() {
        return WebUINodeFactory.createNodeDescription(CONFIGURATION);
    }

    @Override
    protected boolean hasDialog() {
        return false;
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        return null; // not used
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    public EmailSenderNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new EmailSenderNodeModel(creationConfig.getPortConfig().orElseThrow());
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        b.addOptionalInputPortGroupWithDefault(INPUT_FLOWVAR_IDENTIFIER, FlowVariablePortObject.TYPE,
            FlowVariablePortObject.TYPE);
        b.addOptionalInputPortGroup(INPUT_REPORT_IDENTIFIER, IReportPortObject.TYPE);
        return Optional.of(b);
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, EmailSenderNodeSettings.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<EmailSenderNodeModel> createNodeView(final int viewIndex, final EmailSenderNodeModel nodeModel) {
        return null; // no view
    }

}
