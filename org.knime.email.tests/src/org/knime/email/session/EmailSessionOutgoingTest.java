/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */
package org.knime.email.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.knime.email.TestUtil.CONFIG;
import static org.knime.email.TestUtil.SETUP;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.knime.email.TestUtil;
import org.knime.email.session.EmailSessionKey.SmtpConnectionSecurity;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.smtp.SmtpServer;
import com.icegreen.greenmail.util.ServerSetup;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;

/**
 * Tests related to smtp/outgoing email connectivity.
 * 
 * @author Bernd Wiswedel, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("java:S5960") // assertions are ok in tests
final class EmailSessionOutgoingTest {
    
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(SETUP).withConfiguration(CONFIG);

    private static EmailSessionKey getSmtpSessionKey(final String user, final String pass) {
        final SmtpServer smtp = greenMail.getSmtp();
        final ServerSetup serverSetup = smtp.getServerSetup();
        return EmailSessionKey.builder() //
                .withSmtp(b -> b //
                    .smtpHost(serverSetup.getBindAddress(), serverSetup.getPort()) //
                    .smtpEmailAddress(user + "@localhost") //
                    .security(SmtpConnectionSecurity.NONE)) //
                .withAuth(user, pass) //
                .withProperties(new Properties()).build();
    }

    @SuppressWarnings("static-method")
    @Test
    void connectValidUser() throws MessagingException {
        final var mailSessionKey = getSmtpSessionKey(TestUtil.USER1, TestUtil.PWD1);
        Assertions.assertDoesNotThrow(() -> {
            try (final var mailSession = mailSessionKey.connectOutgoing()) {
                // just test if it connects
            }
        }, "Correct user/pass - should be able to connect");
    }

    @SuppressWarnings("static-method")
    @Test
    void connectInvalidUser() {
        //use this test to check if username and password are really used
        final var invalidUserMailSessionKey = getSmtpSessionKey("INVALID_USER", "INVALID_PASSWORD");
        Assertions.assertThrows(AuthenticationFailedException.class, //
            invalidUserMailSessionKey::connectOutgoing, //
            "fail on invalid user/pass");
    }
    
    @SuppressWarnings({"static-method", "resource"})
    @Test
    void testClose() throws MessagingException {
        final var mailSessionKey = getSmtpSessionKey(TestUtil.USER1, TestUtil.PWD1);
        final EmailOutgoingSession mailSession = mailSessionKey.connectOutgoing();
        assertTrue(mailSession.getEmailTransport().isConnected(), "Transport is connected after opening");
        mailSession.close();
        assertFalse(mailSession.getEmailTransport().isConnected(), "Transport is connected after closing");
        Assertions.assertDoesNotThrow(mailSession::close, "close cann be called multiple times");
    }
}
