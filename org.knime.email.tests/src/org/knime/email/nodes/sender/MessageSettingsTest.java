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
 *   Dec 29, 2023 (wiswedel): created
 */
package org.knime.email.nodes.sender;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * html to text conversion test. To be deprecated once the rich text editor can
 * return plain text.
 * 
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
class MessageSettingsTest {

    @SuppressWarnings("static-method")
    @Test
    final void testHtmlToTextWithParagraphAndTags() {
        String html = "<p>Greetings!</p><p></p><p>&lt;b&gt;Best&lt;/b&gt; regards.</p>";
        String plain = MessageSettings.messageToPlainText(html);
        Assertions.assertEquals(
            """
                Greetings!
                
                <b>Best</b> regards.""", 
                plain, "text with paragraph and tags in text");
    }

    @SuppressWarnings("static-method")
    @Test
    final void testHtmlToTextInlineTags() {
        String html = "<p>Greetings!</p>foo bar";
        String plain = MessageSettings.messageToPlainText(html);
        Assertions.assertEquals("Greetings!\nfoo bar", plain, "text with inline tags");
    }
    
//    @SuppressWarnings("static-method")
//    @Test
//    final void testHtmlComplexExample() {
//        String html = "<p>Hallo Bernd,</p><p></p><p>This is eval \\n. Is it on one line?</p><p></p><p>And hahaha - is that on the same line?</p><p></p><p>And how about tags like &lt;b&gt;content in tag&lt;/b&gt;.</p><p></p><p></p><p></p><p>&lt;b&gt;This&lt;/b&gt; is some <strong>text</strong>.</p>";
//        String plain = MessageSettings.messageToPlainText(html);
//        Assertions.assertEquals("""
//                Hallo Bernd,
//
//                This is eval \\n. Is it on one line?
//                
//                And hahaha - is that on the same line?
//                
//                And how about tags like <b>content in tag</b>.
//                
//                
//                
//                <b>This</b> is some text.""", plain, "text with inline tags");
//    }
    
}
