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
 *   Dec 27, 2023 (wiswedel): created
 */
package org.knime.email.nodes.sender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.container.filter.TableFilter;
import org.knime.core.data.v2.RowCursor;
import org.knime.core.data.v2.RowRead;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.report.IReportPortObject;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.webui.node.dialog.defaultdialog.DefaultNodeSettings;
import org.knime.core.webui.node.dialog.defaultdialog.layout.WidgetGroup;
import org.knime.core.webui.node.dialog.defaultdialog.persistence.PersistableSettings;
import org.knime.core.webui.node.dialog.defaultdialog.rule.ConstantSignal;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect;
import org.knime.core.webui.node.dialog.defaultdialog.rule.Effect.EffectType;
import org.knime.core.webui.node.dialog.defaultdialog.setting.filechooser.FileChooser;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ArrayWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ChoicesWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ColumnChoicesProvider;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Label;
import org.knime.core.webui.node.dialog.defaultdialog.widget.RichTextInputWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.ValueSwitchWidget;
import org.knime.core.webui.node.dialog.defaultdialog.widget.Widget;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.data.location.FSLocationValue;

/**
 * Settings around email sender messages (format, message, prios).
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
final class MessageSettings implements DefaultNodeSettings {

    /** EMail priority. */
    enum EMailPriority {
            @Label("Lowest")
            LOWEST("5 (Lowest)"), //
            @Label("Low")
            LOW("4 (Low)"), //
            @Label("Normal")
            NORMAL("3 (Normal)"), //
            @Label("Highest")
            HIGHEST("1 (Highest)"), //
            @Label("High")
            HIGH("2 (High)"); //

        private final String m_xPriority;

        EMailPriority(final String xPriority) {
            m_xPriority = xPriority;
        }

        /** @return for instance "1 (Highest)". */
        String toXPriority() {
            return m_xPriority;
        }
    }

    public static final class ReportIsConnectedInputSignal implements ConstantSignal {
        @Override
        public boolean applies(final DefaultNodeSettingsContext context) {
            return Stream.of(context.getInPortTypes()).anyMatch(IReportPortObject.TYPE::equals);
        }
    }

    public static final class AttachmentPortIsConnectedInputSignal implements ConstantSignal {
        @Override
        public boolean applies(final DefaultNodeSettingsContext context) {
            return Stream.of(context.getInPortTypes()).anyMatch(BufferedDataTable.TYPE::equals);
        }
    }

    static final class AttachmentColumnProvider implements ColumnChoicesProvider {
        @Override
        public DataColumnSpec[] columnChoices(final DefaultNodeSettingsContext context) {
            final PortType[] inTypes = context.getInPortTypes();
            final IntFunction<? extends Optional<PortObjectSpec>> specSupplier = context::getPortObjectSpec;
            return getValidPathColumnNames(inTypes, specSupplier).orElse(new DataColumnSpec[] {});
        }
    }

    static final class Attachment implements WidgetGroup, PersistableSettings {

        @Widget
        FileChooser m_attachment = new FileChooser();

        FSLocation toFSLocation() {
            return m_attachment.getFSLocation();
        }
    }

    /** EMail format. */
    enum EMailFormat {
            @Label("HTML")
            HTML, //
            @Label("Text")
            TEXT
    }

    @Widget(title = "Subject", description = "The email's subject line.")
    String m_subject;

    @Widget(title = "Message", description =
            """
            The email's message body. Formatting options can be selected in the menu bar on the top of the editor.
            The contents of flow variables can be inserted by using the replacement syntax
            "$${<i>&lt;TYPE&gt;&lt;flow-variable-name&gt;</i>}$$". The leading &lt;TYPE&gt; is one of
            <i>I</i> (integer), <i>D</i> (double) or <i>S</i> (string), depending on the type of variable.
            """)
    @RichTextInputWidget
    String m_message;

    @Widget(title = "Content type", advanced = true,
        description = "The mail body's content encoded as plain text or html.")
    @ValueSwitchWidget
    @Effect(signals = ReportIsConnectedInputSignal.class, type = EffectType.DISABLE)
    EMailFormat m_format = EMailFormat.HTML;

    @Widget(title = "Priority", advanced = true, description = "The 'X-Priority' field that is understood by some "
        + "email clients to denote a priority of an email. If unsure, leave unchanged ('Normal' priority).")
    @ValueSwitchWidget
    EMailPriority m_priority = EMailPriority.NORMAL;

    @Widget(title = "Attachments", //
            description = "The path to the file to be attached to the email.")
    @Effect(signals = AttachmentPortIsConnectedInputSignal.class, type = EffectType.DISABLE)
    @ArrayWidget(showSortButtons = true, addButtonText = "Add attachment")
    Attachment[] m_attachments = new Attachment[] {};

    @Widget(title = "Attachment Column",
        description = "The column in the attachment input table, if enabled, "
            + "containing the list of attachment locations (the column needs to be of type \"path\".",
        advanced = true)
    @Effect(signals = AttachmentPortIsConnectedInputSignal.class, type = EffectType.ENABLE)
    @ChoicesWidget(choices = AttachmentColumnProvider.class, showNoneColumn = true)
    String m_attachmentColumn;

    void validate() throws InvalidSettingsException {
        CheckUtils.checkSettingNotNull(m_subject, "Subject must not be null");
        CheckUtils.checkSettingNotNull(m_message, "Message must not be null");
        CheckUtils.checkSettingNotNull(m_priority, "Priority must not be null");
        CheckUtils.checkSettingNotNull(m_format, "Format must not be null");
    }

    void validateDuringConfiguration(final PortType[] inTypes,
        final IntFunction<? extends Optional<PortObjectSpec>> specSupplier) throws InvalidSettingsException {
        validate();
        if (m_attachmentColumn != null) {
            final Optional<DataColumnSpec[]> validPathColumns = getValidPathColumnNames(inTypes, specSupplier);
            if (validPathColumns.isEmpty()) {
                throw org.knime.core.node.message.Message.builder() //
                .withSummary("No 'attachment' input table connected.") //
                .addTextIssue(String.format("The node is configured to use an input table with column '%s' to "
                    + "specifying email attachment paths but no input table is provided", m_attachmentColumn)) //
                .addResolutions("Re-configure the node, unsetting the currently selected attachment column.") //
                .addResolutions("Enable the attachment table input and provide an appropriate table.") //
                .build().orElseThrow() //
                .toInvalidSettingsException();
            }
            CheckUtils.checkSetting(
                validPathColumns.stream().flatMap(Arrays::stream)
                    .anyMatch(col -> col.getName().equals(m_attachmentColumn)),
                "Selected path column ('%s') not present in attachment input column or not of correct (path) type",
                m_attachmentColumn);
        }
    }

    /**
     * Checks presence of input table. If there is a table, it contains a non-empty optional with the list
     * of selected locations (possibly empty, e.g. when no column was selected). The result is empty if, and only if,
     * there is no input type available (dynamic port not shown).
     */
    Optional<FSLocation[]> readAttachmentsFromInputTable(final PortType[] inTypes, final ExecutionContext exec,
        final PortObject[] inObjects) throws CanceledExecutionException {
        final OptionalInt attachmentPort =
                IntStream.range(0, inTypes.length).filter(i -> BufferedDataTable.TYPE.equals(inTypes[i])).findFirst();
        if (attachmentPort.isEmpty()) {
            return Optional.empty();
        }
        if (m_attachmentColumn != null) {
            // attachment column was selected and #configure passed (validates presence of input table and column)
            final BufferedDataTable attachmentTable = (BufferedDataTable)inObjects[attachmentPort.getAsInt()];
            final DataTableSpec attachmentSpec = attachmentTable.getSpec();
            final int attachmentColIndex = attachmentSpec.findColumnIndex(m_attachmentColumn);
            final FSLocation[] fsLocations = readAttachmentsFromColumn(exec, attachmentTable, attachmentColIndex);
            return Optional.of(fsLocations);
        }
        return Optional.of(new FSLocation[] {});
    }

    private static FSLocation[] readAttachmentsFromColumn(final ExecutionContext exec,
        final BufferedDataTable attachmentTable, final int attachmentColIndex) throws CanceledExecutionException {
        final List<FSLocation> attachments = new ArrayList<>();
        try (final RowCursor cursor = attachmentTable.cursor(TableFilter.materializeCols(attachmentColIndex))) {
            while (cursor.canForward()) {
                exec.checkCanceled();
                final RowRead read = cursor.forward();
                if (!read.isMissing(attachmentColIndex)) {
                    attachments.add(read.<FSLocationValue> getValue(attachmentColIndex).getFSLocation());
                }
            }
        }
        return attachments.toArray(FSLocation[]::new);
    }

    /**
     * Utility to extract from the inputs the attachment port (which may or may not be present) and then return
     * the list of valid path columns.
     */
    private static Optional<DataColumnSpec[]> getValidPathColumnNames(final PortType[] inTypes,
        final IntFunction<? extends Optional<PortObjectSpec>> specSupplier) {
        final OptionalInt attachmentPort =
                IntStream.range(0, inTypes.length).filter(i -> BufferedDataTable.TYPE.equals(inTypes[i])).findFirst();
        if (attachmentPort.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
            attachmentPort.stream().mapToObj(specSupplier).flatMap(Optional::stream).map(DataTableSpec.class::cast)
            .flatMap(DataTableSpec::stream).filter(col -> col.getType().isCompatible(FSLocationValue.class))
            .toArray(DataColumnSpec[]::new));
    }

}
