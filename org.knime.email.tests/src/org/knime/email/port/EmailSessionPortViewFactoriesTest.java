/**
 * 
 */
package org.knime.email.port;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.webui.node.port.PortView;
import org.knime.email.session.EmailSessionCache;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.session.EmailSessionKey.SmtpConnectionSecurity;

/**
 * Tests port view implementation of {@link EmailSessionPortObject}, 
 * especially if we can inject (malicious) html.
 * 
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("java:S5960") // sonar believes this is production code
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
    final void after() throws Exception {
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
    private final static void testRenderingFrom(PortView portView) throws Exception {
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
