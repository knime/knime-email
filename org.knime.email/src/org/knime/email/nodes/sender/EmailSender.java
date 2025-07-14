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
 * ---------------------------------------------------------------------
 *
 * Created on Aug 22, 2013 by wiswedel
 */
package org.knime.email.nodes.sender;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.knime.base.util.flowvariable.FlowVariableProvider;
import org.knime.base.util.flowvariable.FlowVariableResolver;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.KNIMEConstants;
import org.knime.core.node.KNIMEException;
import org.knime.core.node.port.report.IReportPortObject;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.FileUtil;
import org.knime.core.util.Pointer;
import org.knime.email.nodes.sender.MessageSettings.Attachment;
import org.knime.email.nodes.sender.MessageSettings.EMailFormat;
import org.knime.email.nodes.sender.MessageUtil.DocumentAndContentType;
import org.knime.email.session.EmailOutgoingSession;
import org.knime.email.session.EmailSessionKey;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFileSystem;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.connections.FSPath;
import org.knime.filehandling.core.connections.uriexport.URIExporterIDs;
import org.knime.filehandling.core.connections.uriexport.noconfig.NoConfigURIExporterFactory;
import org.knime.filehandling.core.defaultnodesettings.FileSystemHelper;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.FileFilterStatistic;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.defaultnodesettings.status.StatusMessage;
import org.knime.reporting2.nodes.htmlwriter.ReportHtmlImageHandler;
import org.knime.reporting2.nodes.htmlwriter.ReportHtmlWriterUtils;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import com.google.common.base.Strings;

import jakarta.activation.FileTypeMap;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.PreencodedMimeBodyPart;

/**
 * Sends emails via jakarta mail API. It's an adaption (copy) of class
 * {@link org.knime.base.node.util.sendmail.SendMailConfiguration}, which is soon going to be deprecated.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 */
final class EmailSender {

    /**
     * A system property that, if set, will disallow emails sent to recipients other than specified in a comma separate
     * list. For instance-D{@value #PROPERTY_ALLOWED_RECIPIENT_DOMAINS}=foo.com,bar.org would allow only emails to be
     * sent to foo.com and bar.org. If other recipients are specified the node will fail during execution. If this
     * property is not specified or empty all domains are allowed.
     */
    public static final String PROPERTY_ALLOWED_RECIPIENT_DOMAINS = "knime.sendmail.allowed_domains";

    /**
     * Message body is sanitized, currently a copy of the policy defined in
     * {@link org.knime.core.data.html.HTMLValueRenderer}, see also UIEXT-1672.
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowCommonInlineFormattingElements()
            .allowStandardUrlProtocols()
            .allowCommonBlockElements()
            .allowStyling()
            .allowElements("a", "hr", "pre", "code")
            .allowAttributes("href").onElements("a")
            .toFactory();

    private final EmailSenderNodeSettings m_settings;

    private final EmailSessionKey m_emailSessionKey;

    private IReportPortObject m_reportPortObject;

    private FSLocation[] m_attachmentsFromInputColumn;

    EmailSender(final EmailSessionKey emailSessionKey, final EmailSenderNodeSettings settings) {
        m_emailSessionKey = emailSessionKey;
        m_settings = CheckUtils.checkArgumentNotNull(settings);
    }

    void addReport(final IReportPortObject report) {
        m_reportPortObject = report;
    }

    /** Sets the attachment list as per input column. If set (not null) it will be used as attachment list. */
    void setAttachmentsFromInputColumn(final FSLocation[] attachmentsFromInputColumn) {
        m_attachmentsFromInputColumn = attachmentsFromInputColumn;
    }

    /**
     * Throws exception if the address list contains forbidden entries according to
     * {@link #PROPERTY_ALLOWED_RECIPIENT_DOMAINS}.
     *
     * @param addressString The non null string as entered in dialog (addresses separated by comma)
     * @return The list of addresses, passed through the validator.
     * @throws AddressException If parsing fails.
     * @thorws InvalidSettingsException If domain not allowed.
     */
    private static InternetAddress[] parseAndValidateRecipients(final String addressString)
        throws InvalidSettingsException, AddressException {
        var validDomainListString = System.getProperty(PROPERTY_ALLOWED_RECIPIENT_DOMAINS);
        InternetAddress[] addressArray = InternetAddress.parse(addressString, false);
        String[] validDomains = Strings.isNullOrEmpty(validDomainListString) //
            ? new String[0] : validDomainListString.toLowerCase().split(",");
        for (InternetAddress a : addressArray) {
            boolean isOK = validDomains.length == 0; // ok if domain list not specified
            final String address = a.getAddress().toLowerCase();
            for (String validDomain : validDomains) {
                isOK = isOK || address.endsWith(validDomain);
            }
            if (!isOK) {
                throw new InvalidSettingsException(String.format(
                    "Recipient '%s' is not valid as the domain is not in the allowed list. "
                        + "Check the system property \"%s\", which currently lists %s.",
                    address, PROPERTY_ALLOWED_RECIPIENT_DOMAINS, validDomainListString));
            }
        }
        return addressArray;
    }

    /**
     * Send the mail.
     *
     * @throws MessagingException ... when sending fails, also authorization exceptions etc.
     * @throws IOException SSL problems or when copying remote URLs to temp local file.
     * @throws InvalidSettingsException on invalid referenced flow vars
     * @throws KNIMEException Any type of send message failure (e.g. timeout)
     */
    void send(final FlowVariableProvider flowVarResolver)
        throws MessagingException, IOException, InvalidSettingsException, KNIMEException {
        final var messageAndContentType = readMessage(flowVarResolver);

        // make sure to set class loader to jakarta.mail - this has caused problems in the past, see bug 5316
        try (final var outgoingSession = m_emailSessionKey.connectOutgoing();
                final var transport = outgoingSession.getEmailTransport()) {
            final var mimeMessage = initMessage(outgoingSession);

            // text or html message part
            final Multipart mp = initMessageBody(messageAndContentType, m_reportPortObject);
            send(transport, mimeMessage, mp);
        }
    }

    private void send(final Transport transport, final MimeMessage message, final Multipart mp)
        throws IOException, InvalidSettingsException, KNIMEException {
        List<File> tempDirs = new ArrayList<>();
        final FSLocation[] attachmentLocations;
        if (m_attachmentsFromInputColumn != null) {
            attachmentLocations = m_attachmentsFromInputColumn;
        } else {
            attachmentLocations = Stream.of(m_settings.m_messageSettings.m_attachments) //
                .map(Attachment::toFSLocation) //
                .filter(location -> StringUtils.isNotBlank(location.getPath())) //
                .toArray(FSLocation[]::new);
        }
        try {
            for (var i = 0; i < attachmentLocations.length; i++) {
                final Pointer<StatusMessage> messagePointer = new Pointer<>();
                try (final var pathAccessor = new FSLocationPathAccessor(attachmentLocations[i]);
                        final var fsConnection = pathAccessor.getConnection()) {
                    final var fsPath = pathAccessor.getRootPath(messagePointer::set);
                    final var localFile = toLocalFile(tempDirs, fsPath, fsConnection);
                    addAttachments(mp, localFile, Integer.toString(i));
                }
            }
            message.setContent(mp);
            transport.sendMessage(message, message.getAllRecipients());
        } catch (MessagingException e) {
            var isSocketTimeout = e.getCause() instanceof SocketTimeoutException;
            final var errorMessageBuilder = org.knime.core.node.message.Message.builder();
            if (isSocketTimeout) {
                errorMessageBuilder.withSummary("Unable to send mesage");
            } else {
                errorMessageBuilder //
                .withSummary("SMTP timeout occurred") //
                .addResolutions("Increase timeout values in node configuration (advanced settings)");
            }
            errorMessageBuilder //
                .addResolutions("Review network configuration (such as proxy settings etc)") //
                .addTextIssue(ExceptionUtils.getRootCauseMessage(e));
            throw errorMessageBuilder.build().orElseThrow().toKNIMEException(e);
        } finally {
            for (File d : tempDirs) {
                FileUtils.deleteQuietly(d);
            }
        }
    }

    /**
     * The message as per settings, with flow variable placeholders filled in. Message is (still) in html since the rich
     * text editor text is html (only).
     *
     * @param flowVarResolver The resolver for the flow variables (= NodeModel)
     * @return The message, flow variable placeholders replaced by their respective value.
     * @throws InvalidSettingsException
     */
    private DocumentAndContentType readMessage(final FlowVariableProvider flowVarResolver)
        throws InvalidSettingsException {
        final String rawMessageHTML = m_settings.m_messageSettings.m_message;
        final String messageHtml;
        try {
            messageHtml = FlowVariableResolver.parse(rawMessageHTML, flowVarResolver);
        } catch (NoSuchElementException nse) {
            throw new InvalidSettingsException(
                "A flow variable could not be resolved due to \"" + nse.getMessage() + "\".", nse);
        }
        final Document messageDoc = Jsoup.parse(POLICY.sanitize(messageHtml));
        return new DocumentAndContentType(messageDoc, m_settings.m_messageSettings.m_format);
    }

    private MimeMessage initMessage(final EmailOutgoingSession outgoingSession)
        throws MessagingException, InvalidSettingsException {

        final var session = outgoingSession.getSession();
        final var recipientSettings = m_settings.m_recipientsSettings;
        final var message = new MimeMessage(session);

        final Optional<String> from = outgoingSession.getEmailAddress();
        final String to = recipientSettings.getToNotNull();
        final String cc = recipientSettings.getCCNotNull();
        final String bcc = recipientSettings.getBCCNotNull();
        final String replyTo = recipientSettings.getReplyToNotNull();
        if (from.isPresent()) {
            message.setFrom(new InternetAddress(from.get()));
        } else {
            message.setFrom();
        }
        if (!Strings.isNullOrEmpty(to)) {
            message.addRecipients(Message.RecipientType.TO, parseAndValidateRecipients(to));
        }
        if (!Strings.isNullOrEmpty(cc)) {
            message.addRecipients(Message.RecipientType.CC, parseAndValidateRecipients(cc));
        }
        if (!Strings.isNullOrEmpty(bcc)) {
            message.addRecipients(Message.RecipientType.BCC, parseAndValidateRecipients(bcc));
        }
        if (!Strings.isNullOrEmpty(replyTo)) {
            message.setReplyTo(parseAndValidateRecipients(replyTo));
        }
        if (message.getAllRecipients() == null) {
            throw org.knime.core.node.message.Message.fromSummaryWithResolution("No recipients were specified.",
                "Provide at least one of To, CC, or BCC in the node configuration").toInvalidSettingsException();
        }

        message.setHeader("X-Mailer", "KNIME/" + KNIMEConstants.VERSION);

        final var messageSettings = m_settings.m_messageSettings;
        message.setSentDate(new Date()); // NOSONAR
        message.setSubject(messageSettings.m_subject, StandardCharsets.UTF_8.name());

        return message;
    }

    private static Multipart initMessageBody(final DocumentAndContentType messageRecord, final IReportPortObject report)
        throws MessagingException {
        var contentBody = new MimeBodyPart();
        // related = can use cid references (some clients would otherwise not show them inline, e.g. thunderbird)
        Multipart mp = new MimeMultipart("related");
        mp.addBodyPart(contentBody);
        Document document = messageRecord.messageDocument();
        if (report != null) {
            try {
                final Document reportDocument = appendReport(mp, report);
                if (StringUtils.isNotBlank(document.text())) {
                    reportDocument.body().insertChildren(0, document.body().childNodes());
                }
                // 95% of the document are style definition, making very simple reports as large as 200+kB
                // (GMail has a limitation of 120kB - mail body larger than that are clipped)
                // TODO: changes as soon UIEXT-1576 is addressed
                if (!Boolean.getBoolean("knime.email.report.keep.style")) {
                    reportDocument.select("style").remove();
                }
                document = reportDocument;
            } catch (IOException ioe) {
                throw new MessagingException("Unable to append report to email body: " + ioe.getMessage(), ioe);
            }
        }
        final boolean useHtmlFormat = report != null || messageRecord.format() == EMailFormat.HTML;
        final String content = useHtmlFormat ? document.html() : MessageUtil.documentToPlainText(document);
        contentBody.setContent(content, MessageUtil.contentType(useHtmlFormat));
        return mp;
    }

    private static Document appendReport(final Multipart mp, final IReportPortObject reportPortObject)
        throws IOException {
        final File tempDir = FileUtil.createTempDir("email-sender-report");
        try {
            final Path reportFilePath = tempDir.toPath().resolve("report.html");
            final AsPartImageHandler imageHandler = new AsPartImageHandler(mp);
            ReportHtmlWriterUtils.writeReportToHtml(reportFilePath, reportPortObject, imageHandler);
            final String asString = Files.readString(reportFilePath, StandardCharsets.UTF_8);
            return Jsoup.parse(asString);
        } finally {
            FileUtils.deleteQuietly(tempDir);
        }
    }

    private static void addAttachments(final Multipart mp, final File file, final String cid)
        throws IOException, MessagingException {
        var filePart = new MimeBodyPart();
        filePart.attachFile(file);
        String encodedFileName = MimeUtility.encodeText(file.getName(), StandardCharsets.UTF_8.name(), null);
        filePart.setFileName(encodedFileName);
        filePart.setHeader("Content-Type", FileTypeMap.getDefaultFileTypeMap().getContentType(file));
        // set content-id header, allows in-line embedding of attached images (AP-21415)
        filePart.setHeader("X-Attachment-Id", cid);
        filePart.setHeader("Content-ID", cid);
        mp.addBodyPart(filePart);
    }

    /** Resolves the path to a local file, copying it locally if needed and adding it to the list of temp files. */
    private static File toLocalFile(final List<File> tempDirs, final FSPath path, final FSConnection fsConnection)
        throws IOException {
        final File file;

        final var factory = fsConnection.getURIExporterFactory(URIExporterIDs.KNIME_FILE);
        if (factory != null) { // we are local
            URI uri;
            try {
                uri = ((NoConfigURIExporterFactory)factory).getExporter().toUri(path);
            } catch (URISyntaxException ex) {
                throw new IOException(
                    String.format("Unable to resolve path to local file - \"%s\": %s", path, ex.getMessage()), ex);
            }
            file = Paths.get(uri).toFile();
        } else {
            var tempDir = FileUtil.createTempDir("send-mail-attachment");
            tempDirs.add(tempDir);
            file = new File(tempDir, path.getName(path.getNameCount() - 1).toString());
            Files.copy(path, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        CheckUtils.check(file.canRead(), IOException::new, () -> String
            .format("The KNIME AP does not have the permissions to read the file attachment at \"%s\".", path));
        return file;
    }

    /**
     * Temporary solution to enable FSLocation flow variables on all convenience file systems. Copied from
     * org.knime.google.api.nodes.authenticator.GoogleAuthenticatorNodeModel.FSLocationPathAccessor
     */
    static class FSLocationPathAccessor implements ReadPathAccessor {

        private final FSLocation m_fsLocation;

        private final FSConnection m_connection;

        private final FSFileSystem<?> m_fileSystem;

        FSLocationPathAccessor(final FSLocation fsLocation) throws IOException {
            m_fsLocation = fsLocation;
            m_connection = FileSystemHelper.retrieveFSConnection(Optional.empty(), fsLocation)
                    .orElseThrow(() -> new IOException("File system is not available"));
            m_fileSystem = m_connection.getFileSystem();
        }

        @Override
        public void close() throws IOException {
            m_fileSystem.close();
            m_connection.close();
        }

        @Override
        public List<FSPath> getFSPaths(final Consumer<StatusMessage> statusMessageConsumer)
            throws IOException, InvalidSettingsException {
            return List.of(getRootPath(statusMessageConsumer));
        }

        @Override
        public FSPath getRootPath(final Consumer<StatusMessage> statusMessageConsumer)
            throws IOException, InvalidSettingsException {
            return m_fileSystem.getPath(m_fsLocation);
        }

        @Override
        public FileFilterStatistic getFileFilterStatistic() {
            return new FileFilterStatistic(0, 0, 0, 1, 0, 0, 0);
        }

        FSConnection getConnection() {
            return m_connection;
        }
    }

    private static class AsPartImageHandler implements ReportHtmlImageHandler {

        private static final int MIME_LINE_LENGTH = 76;

        private static final String CRLF = "\r\n";

        /** Inline images start with this string, e.g.
         * <pre>
         *   &lt;img style="width:701px" src="data:image/png;base64,iVBORw0...
         * </pre>
         */
        private static final String IMG_SRC_INLINE = "data:image/png;base64,";

        private final Multipart m_multipart;
        private int m_imageCounter;

        AsPartImageHandler(final Multipart multipart) {
            m_multipart = multipart;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String handleImage(final String imageData) throws IOException {
            if (StringUtils.startsWith(imageData, IMG_SRC_INLINE)) {
                try {
                    String base64 = StringUtils.removeStart(imageData, IMG_SRC_INLINE);
                    m_imageCounter += 1;
                    final String cid = String.format("image_%03d", m_imageCounter) ;
                    final var imagePart = new PreencodedMimeBodyPart("base64");
                    base64 = mimeEncodeBase64(base64);
                    imagePart.setContent(base64, "image/png");
                    imagePart.setDisposition(Part.INLINE);
                    imagePart.setFileName(String.format("%s.png", cid));
                    imagePart.setHeader("X-Attachment-Id", cid);
                    // embedded in <..> - found out by looking at other examples created with gmail editor
                    imagePart.setContentID(String.format("<%s>", cid));
                    m_multipart.addBodyPart(imagePart);
                    return String.format("cid:%s", cid);
                } catch (MessagingException ex) {
                    throw new IOException("Failed to append inline images as multipart element", ex);
                }
            } else {
                return imageData;
            }
        }

        /**
         * MIME-compliant Base64 encoding (blocks of lines of length 76 chars, terminated by crlf)
         * https://www.ietf.org/rfc/rfc2045.txt * (search for '76 characters')
         * @param base64 The original base64 string (one long line)
         * @return mime encoded base64 (line split after each 76 chars)
         */
        private static String mimeEncodeBase64(String base64) {
            if (!StringUtils.contains(base64, CRLF)) {
                final StringBuilder mimeEncodedBase64 = new StringBuilder();
                for (int i = 0, length = StringUtils.length(base64); i < length; i += MIME_LINE_LENGTH) {
                    final int end = Math.min(i + MIME_LINE_LENGTH, length);
                    mimeEncodedBase64.append(base64.subSequence(i, end));
                    if (end < length) {
                        mimeEncodedBase64.append(CRLF);
                    }
                }
                base64 = mimeEncodedBase64.toString();
            }
            return base64;
        }

    }

}
