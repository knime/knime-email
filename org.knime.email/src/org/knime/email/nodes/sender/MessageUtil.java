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
 *   Dec 31, 2023 (wiswedel): created
 */
package org.knime.email.nodes.sender;

import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * The text body of a rich text editor returns content in html, this utility strips html tags, but keeps line breaks.
 *
 * <p>
 * Copied and modified (simplified) from Jsoup Example (src/main/java/org/jsoup/examples/HtmlToPlainText.java)
 *
 * @author Bernd Wiswedel
 */
final class MessageUtil {

    private MessageUtil() {
    }

    /**
     * Strips html tags, and retains line breaks (best effort).
     *
     * @param document message
     * @return Plain text message.
     */
    static String documentToPlainText(final Document document) {
        final var formatter = new FormattingVisitor();
        NodeTraversor.traverse(formatter, document);
        return formatter.toString();
    }

    record DocumentAndContentType(Document messageDocument, MessageSettings.EMailFormat format) {
    }

    static String contentType(final boolean isHtml) {
        if (isHtml) {
            return "text/html; charset=\"utf-8\"";
        }
        return "text/plain; charset=\"utf-8\"";
    }

    /** jsoup visitor handing <p>, <br> and header (h1, etc) by replacing them with new line. */
    private static class FormattingVisitor implements NodeVisitor {

        private final StringBuilder m_accum = new StringBuilder();
        private boolean m_isAfterParagraphClose;
        private boolean m_isFirstElement = true;

        // hit when the node is first seen
        @Override
        public void head(final Node node, final int depth) {
            String name = node.nodeName();
            if (node instanceof TextNode text) {
                if (m_isAfterParagraphClose) {
                    append("\n");
                }
                append(text.text());
            } else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5")) {
                if (m_isFirstElement) {
                    m_isFirstElement = false; // ignore subsequently
                } else {
                    append("\n");
                }
            }
            m_isAfterParagraphClose = false;
        }

        // hit when all of the node's children (if any) have been visited
        @Override
        public void tail(final Node node, final int depth) {
            String name = node.nodeName();
            if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5")) {
                m_isAfterParagraphClose = true;
            }
        }

        // appends text to the string builder with a simple word wrap method
        private void append(final String text) {
            m_accum.append(text);
        }

        @Override
        public String toString() {
            return m_accum.toString();
        }
    }

}
