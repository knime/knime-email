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

import java.io.IOException;
import java.util.Optional;

import org.apache.xmlbeans.XmlException;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ConfigurableNodeFactory;
import org.knime.core.node.NodeDescription;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeView;
import org.knime.core.node.context.NodeCreationConfiguration;
import org.knime.core.node.context.ports.ExtendablePortGroup;
import org.knime.core.node.context.ports.ModifiablePortsConfiguration;
import org.knime.core.node.workflow.NativeNodeContainer;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialog.OnApplyNodeModifier;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeDialog;
import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.knime.email.port.EmailSessionPortObject;
import org.xml.sax.SAXException;

/**
 * {@link NodeFactory} for the Get Email node, which retrieves emails.
 */
@SuppressWarnings("restriction") // New Node UI is not yet API
public final class EmailReaderNodeFactory extends ConfigurableNodeFactory<EmailReaderNodeModel> implements NodeDialogFactory {

    static final String OUTPUT_ATTACH_PORT_GROUP = "Attachments";

    static final String OUTPUT_HEADER_PORT_GROUP = "Headers";

    final class GetEmailModifier implements OnApplyNodeModifier {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onApply(final NativeNodeContainer nnc, final NodeSettingsRO previousModelSettings,
            final NodeSettingsRO updatedModelSettings, final NodeSettingsRO previousViewSettings,
            final NodeSettingsRO updatedViewSettings) {
            var creationConfig = nnc.getNode().getCopyOfCreationConfig().get();
            Optional<ModifiablePortsConfiguration> portConfig = creationConfig.getPortConfig();
            if (portConfig.isEmpty()) {
                return; // should never happen - setCurrentNodeCreationConfiguration would be broken
            }
            // update output ports
            ExtendablePortGroup outputConfig = (ExtendablePortGroup)portConfig.get().getGroup(OUTPUT_ATTACH_PORT_GROUP);
            while (outputConfig.hasConfiguredPorts()) {
                outputConfig.removeLastPort();
            }
            final boolean retrieverAttachments = updatedModelSettings.getBoolean("outputAttachments", false);
            if (retrieverAttachments) {
                outputConfig.addPort(BufferedDataTable.TYPE);
            }
            outputConfig = (ExtendablePortGroup)portConfig.get().getGroup(OUTPUT_HEADER_PORT_GROUP);
            while (outputConfig.hasConfiguredPorts()) {
                outputConfig.removeLastPort();
            }
            final boolean retrieverHeader = updatedModelSettings.getBoolean("outputHeaders", false);
            if (retrieverHeader) {
                outputConfig.addPort(BufferedDataTable.TYPE);
            }
            nnc.getParent().replaceNode(nnc.getID(), creationConfig);
        }
    }

    private static final WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder()//
        .name("Email Reader")//
        .icon("./emailReader.png")//
        .shortDescription("Reads email from a folder using a session provided by an Email Connector node.")//
        .fullDescription("""
                Reads email from a folder using a session provided by an Email Connector node.
                """)//
        .modelSettingsClass(EmailReaderNodeSettings.class)//
        .nodeType(NodeType.Source)//
        .addInputPort("Email Session", EmailSessionPortObject.TYPE, "The email session.")//
        .addOutputTable("Email Data", "The email data in a table, one row per email.")//
        .addOutputTable(OUTPUT_ATTACH_PORT_GROUP,
            "The email attachments in a table, one row per attachment. Can be joined with the original message via the "
                + EmailReaderNodeProcessor.COL_EMAIL_ID + " column.", true)//
        .addOutputTable(OUTPUT_HEADER_PORT_GROUP,
            "The email header in a table, one row per header. Can be joined with the original message via the "
                + EmailReaderNodeProcessor.COL_EMAIL_ID + " column.", true)//
        .sinceVersion(5, 2, 0).build();

    @Override
    protected NodeDescription createNodeDescription() throws SAXException, IOException, XmlException {
        //TODO: Does not support dynamic port descriptions -> Tooltips are wrong if only add headers is selected
        return WebUINodeFactory.createNodeDescription(CONFIG);
    }

    @Override
    protected EmailReaderNodeModel createNodeModel(final NodeCreationConfiguration creationConfig) {
        return new EmailReaderNodeModel(CONFIG, EmailReaderNodeSettings.class, creationConfig.getPortConfig().get());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new DefaultNodeDialog(SettingsType.MODEL, EmailReaderNodeSettings.class, new GetEmailModifier());
    }

    @Override
    protected Optional<PortsConfigurationBuilder> createPortsConfigBuilder() {
        final var b = new PortsConfigurationBuilder();
        // non-interactive means this cannot be controlled by the user via the user interface.
        // Instead, the node dialog updates the output ports according to the selected secrets.
        b.addFixedInputPortGroup("Input", EmailSessionPortObject.TYPE);
        b.addFixedOutputPortGroup("Emails", BufferedDataTable.TYPE);
        b.addNonInteractiveExtendableOutputPortGroup(OUTPUT_ATTACH_PORT_GROUP, BufferedDataTable.TYPE::equals);
        b.addNonInteractiveExtendableOutputPortGroup(OUTPUT_HEADER_PORT_GROUP, BufferedDataTable.TYPE::equals);
        return Optional.of(b);
    }

    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    @Override
    protected boolean hasDialog() {
        //not used
        return false;
    }

    @Override
    protected NodeDialogPane createNodeDialogPane(final NodeCreationConfiguration creationConfig) {
        //not used
        return null;
    }

    @Override
    public NodeView<EmailReaderNodeModel> createNodeView(final int viewIndex, final EmailReaderNodeModel nodeModel) {
        return null;
    }
}
