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
 *   27 Sep 2023 (Tobias): created
 */
package org.knime.email.nodes.connector;

import org.knime.core.webui.node.impl.WebUINodeConfiguration;
import org.knime.core.webui.node.impl.WebUINodeFactory;
import org.knime.email.port.EmailSessionPortObject;

/**
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class EmailConnectorNodeFactory extends WebUINodeFactory<EmailConnectorNodeModel> {

    private static final WebUINodeConfiguration CONFIG = WebUINodeConfiguration.builder()//
            .name("Email Connector (Labs)")//
            .icon("./emailConnector.png")//
            .shortDescription("Connects to an email server using the IMAP and/or SMPT protocol.")//
            .fullDescription(
                """
                <p>
                Connects to an email server using the entered account. Once connected you can use various nodes to
                work with your email such as the
                <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.reader.EmailReaderNodeFactory/">Email Reader</a> node
                to read email or the
                <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.sender.EmailSenderNodeFactory/">Email Sender</a> node
                to send email.
                </p>
                <p>
                The Email Connector node uses the <a href="https://jakartaee.github.io/mail-api/">Jakarta Mail API</a>
                and the <a href="https://eclipse-ee4j.github.io/angus-mail/">Angus Mail implementation</a> to interact
                with compatible email servers.
                </p>
                <p>
                <b>Please notice that the node only supports email server that support the IMAP protocol for
                reading email and the SMTP protocol for sending email.</b>
                </p>
                """)//
            .modelSettingsClass(EmailConnectorSettings.class)//
            .nodeType(NodeType.Source)//
            .addOutputPort("Email Session", EmailSessionPortObject.TYPE, "The email session to use in subsequent nodes.")//
            .keywords("Email", "IMAP", "SMPT")//
            .sinceVersion(5, 2, 0).build();

    /**
     * Constructor.
     */
    public EmailConnectorNodeFactory() {
        super(CONFIG);
    }

    @Override
    public EmailConnectorNodeModel createNodeModel() {
        return new EmailConnectorNodeModel(CONFIG);
    }

}
