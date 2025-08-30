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
 *   18 Jul 2025 (Tobias): created
 */
package org.knime.email.nodes.connector;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Modification.WidgetGroupModifier;
import org.knime.credentials.base.CredentialPortObject;
import org.knime.credentials.base.oauth.api.AccessTokenAccessor;
import org.knime.credentials.base.oauth.api.JWT;
import org.knime.email.session.EmailSessionKey;
import org.knime.email.session.EmailSessionKey.SmtpConnectionSecurity;
import org.knime.node.parameters.Advanced;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.array.ArrayWidget.ElementLayout;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.HorizontalLayout;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.migration.Migrate;
import org.knime.node.parameters.persistence.Persist;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.updates.util.BooleanReference;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.credentials.Credentials;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MaxValidation;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsPositiveIntegerValidation;
import org.knime.node.parameters.widget.text.TextInputWidget;
import org.knime.node.parameters.widget.text.TextInputWidgetValidation.PatternValidation;

/**
 * The general email connector settings that are used by the email connector nodes.
 *
 * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("restriction")
public class EmailConnectorSettings implements NodeParameters {


    /**
     * Allows to change the advanced annotation of the IMAP and SMTP settings.
     * @author Tobias Koetter, KNIME GmbH, Konstanz, Germany
     */
    public static final class ChangeAdvancedAnnotation implements Modification.Modifier {

        interface ImapServerSettingsRef extends Modification.Reference { }
        interface ImapPortSettingsRef extends Modification.Reference { }
        interface ImapSecureSettingsRef extends Modification.Reference { }

        interface SmtpServerSettingsRef extends Modification.Reference { }
        interface SmtpPortSettingsRef extends Modification.Reference { }
        interface SmtpRequiresAuthenticationRef extends Modification.Reference { }
        interface SmtpSecuritySettingsRef extends Modification.Reference { }

        @Override
        public void modify(final WidgetGroupModifier group) {
            group.find(ImapServerSettingsRef.class).addAnnotation(Advanced.class).modify();
            group.find(ImapServerSettingsRef.class).modifyAnnotation(Layout.class)
            .withProperty("value", IncomingServerSectionAdvanced.class).modify();
            group.find(ImapPortSettingsRef.class).addAnnotation(Advanced.class).modify();
            group.find(ImapPortSettingsRef.class).modifyAnnotation(Layout.class)
            .withProperty("value", IncomingServerSectionAdvanced.class).modify();
            group.find(ImapSecureSettingsRef.class).addAnnotation(Advanced.class).modify();
            group.find(ImapSecureSettingsRef.class).modifyAnnotation(Layout.class)
            .withProperty("value", IncomingServerSectionAdvanced.class).modify();

            group.find(SmtpServerSettingsRef.class).addAnnotation(Advanced.class).modify();
            group.find(SmtpPortSettingsRef.class).addAnnotation(Advanced.class).modify();
            group.find(SmtpRequiresAuthenticationRef.class).addAnnotation(Advanced.class).modify();
            group.find(SmtpSecuritySettingsRef.class).addAnnotation(Advanced.class).modify();
        }
    }

    /**
     * Default constructor for the email connector settings.
     */
    protected EmailConnectorSettings(){

    }

    /**
     * Creates a new instance of the email connector settings and presets the OAuth2 user from the input
     * credentials if possible.
     * @param context the node parameters input context
     */
    protected EmailConnectorSettings(final NodeParametersInput context) {
        this(context, null, 993, true, null, 587, true, ConnectionSecurity.NONE);

    }

    /**
     * Creates a new instance of the email connector settings with preset values for
     * incoming and outgoing server settings.
     *
     * @param context NodeParameterInput context to read the OAuth2 user from the credentials port object
     *
     * @param imapServer the IMAP server address
     * @param imapPort the IMAP server port
     * @param imapUseSecureProtocol whether to use a secure protocol for the IMAP connection
     * @param smtpHost the SMTP server address
     * @param smtpPort the SMTP server port
     * @param smtpRequiresAuthentication whether the SMTP server requires authentication
     * @param smtpSecurity the connection security to use for the SMTP connection     *
     */
    protected EmailConnectorSettings(final NodeParametersInput context, final String imapServer, final int imapPort,
        final boolean imapUseSecureProtocol, final String smtpHost, final int smtpPort,
        final boolean smtpRequiresAuthentication, final ConnectionSecurity smtpSecurity) {
        m_imapServer = imapServer;
        m_imapPort = imapPort;
        m_imapUseSecureProtocol = imapUseSecureProtocol;
        m_smtpHost = smtpHost;
        m_smtpPort = smtpPort;
        m_smtpRequiresAuthentication = smtpRequiresAuthentication;
        m_smtpSecurity = smtpSecurity;

        //preset the OAuth2 user name and SMTP email address if credentials are available
        if (context == null || !StringUtils.isAllBlank(m_oauthUser) || !StringUtils.isAllBlank(m_smtpEmailAddress)) {
            return; // do not overwrite the user name if it is already set
        }
        final var inPortObjects = context.getInPortObjects();
        if (inPortObjects != null && inPortObjects.length == 1) {
            final var inPortObject = inPortObjects[0];
            if (inPortObject instanceof CredentialPortObject credPort) {
                final var spec = credPort.getSpec();
                try {
                    final var accessor = spec.toAccessor(AccessTokenAccessor.class);
                    final String accessToken = accessor.getAccessToken();
                    final var jwt = new JWT(accessToken);
                    final Map<String, Object> claims = jwt.getAllClaims();
                    if (claims != null && claims.containsKey("email")) {
                        final var email = claims.get("email").toString();
                        m_oauthUser = email;
                        m_smtpEmailAddress = email;
                        return;
                    }
                    if (claims != null && claims.containsKey("upn")) {
                        final var upn = claims.get("upn").toString();
                        m_oauthUser = upn;
                        m_smtpEmailAddress = upn;
                        return;
                    }
                    if (claims != null && claims.containsKey("sub")) {
                        final var sub = claims.get("sub").toString();
                        m_oauthUser = sub;
                        m_smtpEmailAddress = sub;
                        return;
                    }
                } catch (Exception ex) {
                    // we cannot read the access token or parse it and thus cannot preset the oauth user name
                }
            }
        }
    }

    @Layout(IncomingServerSection.class)
    @Widget(title = "Server", description = "The address of the incoming email server (IMAP) e.g. <i>imap.web.de.</i>")
    @TextInputWidget(patternValidation = ImapServerPatternValidation.class)
    @Persist(configKey = "server")
    @Migrate(loadDefaultIfAbsent = true)
    @Modification.WidgetReference(ChangeAdvancedAnnotation.ImapServerSettingsRef.class)
    String m_imapServer;

    private static boolean hasCredentialPort(final PortType[] types) {
        return Arrays.stream(types).anyMatch(CredentialPortObject.TYPE::equals);
    }

    /**
     * Constant signal to indicate whether the user has added a credential port or not.
     */
    static final class CredentialInputConnected implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(context -> hasCredentialPort(context.getInPortTypes()));
        }
    }

    interface ConnectionTypeRef extends ParameterReference<ConnectionType> {
    }

    static final class IsIncomingServerConnection implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ConnectionTypeRef.class).isOneOf(ConnectionType.INCOMING,
                ConnectionType.INCOMING_OUTGOING);
        }
    }

    static final class IsOutgoingServerConnection implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getEnum(ConnectionTypeRef.class).isOneOf(ConnectionType.OUTGOING,
                ConnectionType.INCOMING_OUTGOING);
        }
    }

    @Widget(title = "Connection type",
        description = """
                Choose the type of connection you want to create. Depending on the selected connection type you will be
                able to work with your email and/or send email.
                <ul>
                <li>
                Select "Incoming" if you want to work with your email e.g. by using the
                <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.reader.EmailReaderNodeFactory/">Email Reader</a> node
                to read your email or the
                <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.mover.EmailMoverNodeFactory/">Email Mover</a> node
                to move email to different folders.
                </li>
                <li>
                Select "Outgoing" if you only want to send email via the SMTP protocol by using the
                <a href="https://hub.knime.com/knime/extensions/org.knime.features.email/latest/org.knime.email.nodes.sender.EmailSenderNodeFactory/">Email Sender</a> node.
                </li>
                <li>Select "Incoming &amp; Outgoing" if you want to read and send email.</li>
                </ul>
                """)
    @ValueReference(ConnectionTypeRef.class)
    @Migrate(loadDefaultIfAbsent = true)
    @ValueSwitchWidget()
    ConnectionType m_type = ConnectionType.INCOMING;

    //  INCOMING SERVER SETTINGS
    @Section(title = "Incoming Mail Server (IMAP)")
    @Effect(predicate = IsIncomingServerConnection.class, type = EffectType.SHOW)
    @Advanced
    public interface IncomingServerSectionAdvanced {
        //This is used for the case that all incoming server settings are advanced.
    }

    //  INCOMING SERVER SETTINGS
    @Section(title = "Incoming Mail Server (IMAP)")
    @Effect(predicate = IsIncomingServerConnection.class, type = EffectType.SHOW)
    public interface IncomingServerSection {
        //This is the standard incoming server settings section.
    }

    public static final class ImapServerPatternValidation extends PatternValidation {

        @Override
        public String getErrorMessage() {
            return "IMAP host must not be empty.";
        }

        @Override
        protected String getPattern() {
            return "^(?!\\s*$).+"; // at least one non-whitespace character
        }
    }

    private static final class PortMaxValidation extends MaxValidation {
        @Override
        protected double getMax() {
            return 0xFFFF; // 65635
        }
    }

    @Layout(IncomingServerSection.class)
    @Widget(title = "Port",
        description = "The port of the incoming email server (e.g. 143 (non-secure) or 993 (secure)).") //
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class, maxValidation = PortMaxValidation.class)
    @Migrate(loadDefaultIfAbsent = true)
    @Persist(configKey = "port")
    @Modification.WidgetReference(ChangeAdvancedAnnotation.ImapPortSettingsRef.class)
    int m_imapPort = 993;

    @Layout(IncomingServerSection.class)
    @Widget(title = "Use secure protocol",
        description = "Choose whether to use an encrypted or unencrypted connection.")
    @Migrate(loadDefaultIfAbsent = true)
    @Persist(configKey = "useSecureProtocol")
    @Modification.WidgetReference(ChangeAdvancedAnnotation.ImapSecureSettingsRef.class)
    boolean m_imapUseSecureProtocol = true;

    //  OUTGOING SERVER SETTINGS
    @Section(title = "Outgoing Mail Server (SMTP)")
    @After(IncomingServerSection.class)
    @Effect(predicate = IsOutgoingServerConnection.class, type = EffectType.SHOW)
    interface OutgoingServerSection {
    }

    private static final class SmtpHostPatternValidation extends PatternValidation {

        @Override
        public String getErrorMessage() {
            return "SMTP host must must not be empty.";
        }

        @Override
        protected String getPattern() {
            return "^(?!\\s*$).+"; // at least one non-whitespace character
        }
    }

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Server", description = "The address of the outgoing email server (SMTP) e.g. <i>smtp.web.de.</i>")
    @TextInputWidget(patternValidation = SmtpHostPatternValidation.class)
    @Migrate(loadDefaultIfAbsent = true)
    @Modification.WidgetReference(ChangeAdvancedAnnotation.SmtpServerSettingsRef.class)
    String m_smtpHost;

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Port",
        description = "The port of the outgoing email server (e.g. 25 (non-secure), 465 (secure) or 587 (secure)).")
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class, maxValidation = PortMaxValidation.class)
    @Migrate(loadDefaultIfAbsent = true)
    @Modification.WidgetReference(ChangeAdvancedAnnotation.SmtpPortSettingsRef.class)
    int m_smtpPort = 587;

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Email address",
        description = "Some SMTP servers require the sender's email address, other "
            + "accept this to be not specified and will automatically derive it from the user account.")
    @Migrate(loadDefaultIfAbsent = true)
    String m_smtpEmailAddress;

    static final class SMTPRequiresAuthentication implements BooleanReference {
    }

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Outgoing mail server requires authentication.",
        description = "If the outgoing mail server "
            + "requires authentication, check this box and enter the credentials or use a credentials flow variable "
            + "to control them.")
    @Migrate(loadDefaultIfAbsent = true)
    @ValueReference(SMTPRequiresAuthentication.class)
    @Effect(predicate = CredentialInputConnected.class, type = EffectType.HIDE)
    @Modification.WidgetReference(ChangeAdvancedAnnotation.SmtpRequiresAuthenticationRef.class)
    boolean m_smtpRequiresAuthentication = true;

    @Layout(OutgoingServerSection.class)
    @Widget(title = "Connection Security",
        description = "Configures the connection security to the outgoing email server.")
    @ValueSwitchWidget
    @Migrate(loadDefaultIfAbsent = true)
    @Modification.WidgetReference(ChangeAdvancedAnnotation.SmtpSecuritySettingsRef.class)
    ConnectionSecurity m_smtpSecurity = ConnectionSecurity.NONE;

    static final class AuthenticationIsRequired implements EffectPredicateProvider {
        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getPredicate(IsIncomingServerConnection.class)
                .or(i.getPredicate(SMTPRequiresAuthentication.class));
        }
    }

    @Section(title = "Authentication")
    @Effect(predicate = AuthenticationIsRequired.class, type = EffectType.SHOW)
    @After(OutgoingServerSection.class)
    interface AuthenticationSection {
    }

    /** The email server login. */
    @Layout(AuthenticationSection.class)
    @Effect(predicate = CredentialInputConnected.class, type = EffectType.HIDE)
    @Widget(title = "Login", description = "The optional email server login.")
    Credentials m_login = new Credentials(); //set to empty credentials to prevent "No login set message"

    @Layout(AuthenticationSection.class)
    @Effect(predicate = CredentialInputConnected.class, type = EffectType.SHOW)
    @Widget(title = "User name", description = "The optional user name to login with the given OAuth2 access token. "
        + "In most cases this is the email address.")
    @Migrate(loadDefaultIfAbsent = true)
    String m_oauthUser = "";

    //  CONNECTION PROPERTIES
    @Section(title = "Connection Properties")
    @Advanced
    @After(AuthenticationSection.class)
    interface ConnectionPropertySection {
    }

    @Layout(ConnectionPropertySection.class)
    @Widget(title = "Connection timeout", advanced = true,
        description = "Timeout in seconds to establish a connection.")
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class)
    @Migrate(loadDefaultIfAbsent = true)
    int m_connectTimeout = EmailSessionKey.DEF_TIMEOUT_CONNECT_S;

    @Layout(ConnectionPropertySection.class)
    @Widget(title = "Read timeout", advanced = true,
        description = "Timeout in seconds to read a server response from a connection.")
    @NumberInputWidget(minValidation = IsPositiveIntegerValidation.class)
    @Migrate(loadDefaultIfAbsent = true)
    int m_readTimeout = EmailSessionKey.DEF_TIMEOUT_READ_S;

    @Widget(title = "Custom properties",
        description = """
                Allows to define additional connection properties. For details about the supported properties see
                <a href='https://javaee.github.io/javamail/docs/api//com/sun/mail/imap/package-summary.html#properties'>here.</a>
                """,
        advanced = true)
    @Layout(ConnectionPropertySection.class)
    @ArrayWidget(elementLayout = ElementLayout.HORIZONTAL_SINGLE_LINE, addButtonText = "Add custom property")
    ConnectionProperties[] m_properties = new ConnectionProperties[0];

    static final class ConnectionProperties implements NodeParameters {
        @HorizontalLayout
        interface ConnectionPropertiesLayout {
        }

        static final class StartsWithNonWhiteSpaceValidation extends PatternValidation {
            @Override
            protected String getPattern() {
                return "\\S+.*";
            }
        }

        @Widget(title = "Name", description = "Custom property name e.g. mail.smtp.timeout.")
        @TextInputWidget(patternValidation = StartsWithNonWhiteSpaceValidation.class)
        @Layout(ConnectionPropertiesLayout.class)
        public String m_name;

        @Widget(title = "Value", description = "Custom property value e.g. 10 or true.")
        @TextInputWidget(patternValidation = StartsWithNonWhiteSpaceValidation.class)
        @Layout(ConnectionPropertiesLayout.class)
        public String m_value;
    }

//  HELPER SECTION
    public enum ConnectionType {
        @Label("Incoming")
        INCOMING,
        @Label("Outgoing")
        OUTGOING,
        @Label("Incoming & Outgoing")
        INCOMING_OUTGOING
    }

    // OUTGOING SERVER SETTINGS
    public enum ConnectionSecurity {
        @Label("None")
        NONE(SmtpConnectionSecurity.NONE),
        @Label("SSL")
        SSL(SmtpConnectionSecurity.SSL),
        @Label("STARTTLS")
        STARTTLS(SmtpConnectionSecurity.STARTTLS);

        private final SmtpConnectionSecurity m_smtpConnectionSecurity;

        ConnectionSecurity(final SmtpConnectionSecurity sec) {
            m_smtpConnectionSecurity = sec;
        }

        public SmtpConnectionSecurity toSmtpConnectionSecurity() {
            return m_smtpConnectionSecurity;
        }

    }

    @Override
    public void validate() throws InvalidSettingsException {
        switch (m_type) {
            case INCOMING:
                validateIncoming();
                break;
            case OUTGOING:
                validateOutgoing();
                break;
            case INCOMING_OUTGOING:
                validateIncoming();
                validateOutgoing();
                break;
        }
    }

    private void validateIncoming() throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNoneBlank(m_imapServer), "No incoming mail server set");
    }

    private void validateOutgoing() throws InvalidSettingsException {
        CheckUtils.checkSetting(StringUtils.isNoneBlank(m_smtpHost), "No outgoing mail server set");
    }
}
