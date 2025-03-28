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
package org.knime.email.port;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.knime.core.webui.node.port.PortView;
import org.knime.email.session.EmailSessionCache;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.session.EmailSessionKey.SmtpConnectionSecurity;
import org.knime.testing.core.ExecutionContextExtension;

import jakarta.mail.MessagingException;

/**
 * Tests port view implementation of {@link EmailSessionPortObject}, 
 * especially if we can inject (malicious) html.
 * 
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings({"java:S5960", "java:S112"}) // sonar believes this is production code
@ExtendWith({ExecutionContextExtension.class}) // requires NodeContext in port view = NodeModel#execute
class EmailSessionPortViewFactoriesTest {
    
    private UUID m_sessionCacheID;
    
    @BeforeEach
    final void before() {
        
        final var props = new Properties();
        props.put("a", "b");
        props.put("<script>alert('Injected!');</script>", "ignored");
        props.put("<broken>", "really broken>");
        final var key = EmailSessionKey.builder() // 
            .withImap(b -> b //
                .imapHost("foo", 20) //
                .imapSecureConnection(true)) //
            .withSmtp(b -> b //
                .smtpHost("smtp-host", 12) //
                .smtpEmailAddress("test@bar.com") //
                .security(SmtpConnectionSecurity.STARTTLS))
            .withAuth("<injected-broken-html>&foo<", "IGNORED") //
            .withProperties(props) //
            .build();
        m_sessionCacheID = EmailSessionCache.store(key);
    }
    
    @AfterEach
    final void after() throws MessagingException {
        EmailSessionCache.delete(m_sessionCacheID);
    }

    /** not much to test except for valid xml-like content. */
    @SuppressWarnings({"restriction"})
    @Test
    final void testRenderingFromPortObjectSpec() throws Exception {
        final var spec = new EmailSessionPortObjectSpec(m_sessionCacheID);
        final var portView = EmailSessionPortViewFactories.PORT_SPEC_VIEW_FACTORY.createPortView(spec);
        testRenderingFrom(portView);
    }
        
    /** not much to test except for valid xml-like content. */
    @SuppressWarnings({"restriction"})
    @Test
    final void testRenderingFromPortObject() throws Exception {
        final var object = new EmailSessionPortObject(m_sessionCacheID);
        final var portView = EmailSessionPortViewFactories.PORT_VIEW_FACTORY.createPortView(object);
        testRenderingFrom(portView);
    }
    
    @SuppressWarnings({"restriction"})
    private static final void testRenderingFrom(PortView portView) throws IOException {
        final byte[] content;
        try (final var inputStream = portView.getPage().getInputStream()) {
            content = IOUtils.toByteArray(inputStream);
        }
        assertDoesNotThrow(() -> DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()
            .parse(new ByteArrayInputStream(content)), () -> """
                        Parse HTML content as Document must not throw exception, content is:%n
                        %s%n
                        """.formatted(new String(content, StandardCharsets.UTF_8)));
    }
    
    

}
