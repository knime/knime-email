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
import org.knime.core.webui.node.dialog.defaultdialog.internal.file.FileSelection;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.data.location.FSLocationValue;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.WidgetGroup;
import org.knime.node.parameters.array.ArrayWidget;
import org.knime.node.parameters.array.ArrayWidget.ElementLayout;
import org.knime.node.parameters.persistence.Persistable;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.ColumnChoicesProvider;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.text.RichTextInputWidget;

/**
 * Settings around email sender messages (format, message, prios).
 *
 * @author wiswedel
 */
@SuppressWarnings("restriction")
final class MessageSettings implements NodeParameters {

    public static final class ReportIsConnected implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(ReportIsConnected::reportIsConnected);
        }

        private static boolean reportIsConnected(final NodeParametersInput context) {
            return Stream.of(context.getInPortTypes()).anyMatch(IReportPortObject.TYPE::equals);
        }

    }

    public static final class AttachmentPortIsConnected implements EffectPredicateProvider {

        @Override
        public EffectPredicate init(final PredicateInitializer i) {
            return i.getConstant(AttachmentPortIsConnected::attachmentPortIsConnected);
        }

        private static boolean attachmentPortIsConnected(final NodeParametersInput context) {
            return Stream.of(context.getInPortTypes()).anyMatch(BufferedDataTable.TYPE::equals);
        }

    }

    static final class AttachmentColumnProvider implements ColumnChoicesProvider {
        @Override
        public List<DataColumnSpec> columnChoices(final NodeParametersInput context) {
            final PortType[] inTypes = context.getInPortTypes();
            final IntFunction<? extends Optional<PortObjectSpec>> specSupplier = context::getInPortSpec;
            return getValidPathColumnNames(inTypes, specSupplier).stream().flatMap(Arrays::stream).toList();
        }
    }

    static final class Attachment implements WidgetGroup, Persistable {

        @Widget(title = "Attachment", description = "The location of a file to be attached to the email.")
        FileSelection m_attachment = new FileSelection();

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

    @Widget(title = "Message", description = """
            <p>
            The email's message body. Formatting options can be selected in the menu bar on the top of the editor.
            The contents of flow variables can be inserted by using the replacement syntax
            "$${<i>&lt;TYPE&gt;&lt;flow-variable-name&gt;</i>}$$". The leading &lt;TYPE&gt; is one of
            <i>I</i> (integer), <i>D</i> (double) or <i>S</i> (string), depending on the type of variable.
            </p>
            <p>
            If this entire message is controlled via flow variable assignment, e.g. via the
            control button on the top right of the editor, the value is interpreted as HTML. Specifically any
            occurrence of HTML tags is interpreted unless it is escaped. For instance, a value such as
            <tt>&lt;b&gt; Message &lt;/b&gt;</tt> will mark <i>Message</i> in bold. If that is not desired,
            reformat the variable value and escape it, i.e. as <tt>&amp;lt;b&amp;gt;
            Message &amp;lt;/b&amp;gt;</tt>. If the message is sent as Text (see Content Type below), any HTML-like
            tag is removed (stripped) from the value.
            </p>
            """)
    @RichTextInputWidget
    String m_message;

    @Widget(title = "Content type", advanced = true,
        description = "The mail body's content encoded as plain text or html.")
    @ValueSwitchWidget
    @Effect(predicate = ReportIsConnected.class, type = EffectType.HIDE)
    EMailFormat m_format = EMailFormat.HTML;

    @Widget(title = "Attachments (Manual Selection)", //
        description = "The path to the file to be attached to the email.")
    @Effect(predicate = AttachmentPortIsConnected.class, type = EffectType.HIDE)
    @ArrayWidget(elementLayout = ElementLayout.HORIZONTAL_SINGLE_LINE, showSortButtons = true,
        addButtonText = "Add attachment")
    Attachment[] m_attachments = new Attachment[]{};

    @Widget(title = "Attachments (Input Column)",
        description = "The column in the attachment input table, if enabled, "
            + "containing the list of attachment locations (the column needs to be of type \"path\".")
    @Effect(predicate = AttachmentPortIsConnected.class, type = EffectType.SHOW)
    @ChoicesProvider(AttachmentColumnProvider.class)
    String m_attachmentColumn;

    @Override
    public void validate() throws InvalidSettingsException {
        CheckUtils.checkSettingNotNull(m_subject, "Subject must not be null");
        CheckUtils.checkSettingNotNull(m_message, "Message must not be null");
        CheckUtils.checkSettingNotNull(m_format, "Format must not be null");
    }

    void validateDuringConfiguration(final PortType[] inTypes,
        final IntFunction<? extends Optional<PortObjectSpec>> specSupplier) throws InvalidSettingsException {
        validate();
        if (m_attachmentColumn != null) {
            final Optional<DataColumnSpec[]> validPathColumns = getValidPathColumnNames(inTypes, specSupplier);
            CheckUtils.checkSetting(validPathColumns.isEmpty() || //
                Stream.of(validPathColumns.get()).map(DataColumnSpec::getName).anyMatch(m_attachmentColumn::equals),
                "Selected path column ('%s') not present in attachment input column or not of correct (path) type",
                m_attachmentColumn);
        }
    }

    /**
     * Checks presence of input table. If there is a table, it contains a non-empty optional with the list of selected
     * locations (possibly empty, e.g. when no column was selected). The result is empty if, and only if, there is no
     * input type available (dynamic port not shown).
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
        return Optional.of(new FSLocation[]{});
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
     * Utility to extract from the inputs the attachment port (which may or may not be present) and then return the list
     * of valid path columns.
     */
    private static Optional<DataColumnSpec[]> getValidPathColumnNames(final PortType[] inTypes,
        final IntFunction<? extends Optional<PortObjectSpec>> specSupplier) {
        final OptionalInt attachmentPort =
            IntStream.range(0, inTypes.length).filter(i -> BufferedDataTable.TYPE.equals(inTypes[i])).findFirst();
        if (attachmentPort.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attachmentPort.stream().mapToObj(specSupplier).flatMap(Optional::stream)
            .map(DataTableSpec.class::cast).flatMap(DataTableSpec::stream)
            .filter(col -> col.getType().isCompatible(FSLocationValue.class)).toArray(DataColumnSpec[]::new));
    }

}
