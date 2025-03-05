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
 *   Nov 15, 2023 (Leon Wenzler, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.email.port;

import static com.google.common.html.HtmlEscapers.htmlEscaper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.webui.data.InitialDataService;
import org.knime.core.webui.data.RpcDataService;
import org.knime.core.webui.node.port.PortSpecViewFactory;
import org.knime.core.webui.node.port.PortView;
import org.knime.core.webui.node.port.PortViewFactory;
import org.knime.core.webui.node.port.PortViewManager;
import org.knime.core.webui.page.Page;
import org.knime.core.webui.page.Page.StringSupplier;
import org.knime.email.session.EmailSessionKey.ViewContentSection;

/**
 * Same structure as <tt>org.knime.credentials.base.internal.PortViewFactories</tt>, but for the
 * {@link EmailSessionPortObject}.
 *
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction") // webui classes
public final class EmailSessionPortViewFactories {

    /**
     * {@link PortViewFactory} for the credential port object view.
     */
    static final PortViewFactory<EmailSessionPortObject> PORT_VIEW_FACTORY = obj -> createPortSpecView(obj.getSpec());

    /**
     * {@link PortSpecViewFactory} for the credential port object spec view.
     */
    static final PortSpecViewFactory<EmailSessionPortObjectSpec> PORT_SPEC_VIEW_FACTORY = //
        EmailSessionPortViewFactories::createPortSpecView;

    /**
     * Registers the views with the {@link PortViewManager}.
     */
    public static void register() {
        final var portName = "Email Connection";
        PortViewManager.registerPortViews(EmailSessionPortObject.TYPE, //
            List.of(
                new PortViewManager.PortViewDescriptor(portName, PORT_SPEC_VIEW_FACTORY),
                new PortViewManager.PortViewDescriptor(portName, PORT_VIEW_FACTORY)),
            List.of(0), //
            List.of(1));
    }

    /**
     * @param pos The port object spec.
     */
    private static PortView createPortSpecView(final EmailSessionPortObjectSpec pos) {
        // the port view needs the node context to access the session key in the WFM (see NXT-3266)
        final var nodeCtx = CheckUtils.checkNotNull(NodeContext.getContext(), "No `NodeContext` present for port view");
        final StringSupplier supplierWithContext = () -> {
            NodeContext.pushContext(nodeCtx);
            try {
                return createHtmlContent(pos);
            } finally {
                NodeContext.removeLastContext();
            }
        };

        return new PortView() {
            @Override
            public Page getPage() {
                return Page.builder(supplierWithContext, "index.html").build();
            }

            @Override
            public Optional<InitialDataService<Object>> createInitialDataService() {
                return Optional.empty();
            }

            @Override
            public Optional<RpcDataService> createRpcDataService() {
                return Optional.empty();
            }
        };
    }

    private static String createHtmlContent(final EmailSessionPortObjectSpec pos) {
        final var sb = new StringBuilder();
        sb.append("<html><head><style>\n");
        try (final var in = EmailSessionPortViewFactories.class.getResourceAsStream("table.css")) {
            sb.append(IOUtils.toString(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) { // NOSONAR ignore, should always work
        }
        sb.append("</style></head><body>\n");
        if (pos.getEmailSessionKey().isEmpty()) {
            sb.append("<h3>(No EMail Connection available)</h3>\n");
        } else {
            sb.append("<h3>EMail Connection</h3>\n");
            sb.append("<table>\n");
            pos.getEmailSessionKey().orElseThrow().toViewContent().stream().forEach(sec -> renderPortViewData(sec, sb));
            sb.append("</table>\n");
        }
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static final String TR_TWO_COLS = """
            <tr>
                <td>%s</td>
                <td>%s</td>
            </tr>
            """;

    private static void renderPortViewData(final ViewContentSection section, final StringBuilder sb) {
        Map<String, String> properties = section.properties().orElseGet(() -> Map.of("<not specified>", ""));
        sb.append("""
                <tr>
                    <td colspan="2"><strong>%s</strong></td>
                </tr>
                """.formatted(htmlEscaper().escape(section.header())));
        properties.entrySet().forEach(entry -> sb.append(
            TR_TWO_COLS.formatted(htmlEscaper().escape(entry.getKey()), htmlEscaper().escape(entry.getValue()))));
    }

    private EmailSessionPortViewFactories() {
    }
}
