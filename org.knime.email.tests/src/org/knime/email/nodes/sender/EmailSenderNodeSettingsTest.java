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
 */
package org.knime.email.nodes.sender;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortType;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.email.port.EmailSessionPortObject;
import org.knime.filehandling.core.data.location.cell.SimpleFSLocationCellFactory;
import org.knime.testing.node.dialog.DefaultNodeSettingsSnapshotTest;

/**
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
@SuppressWarnings("java:S5960")
final class EmailSenderNodeSettingsTest extends DefaultNodeSettingsSnapshotTest {

    EmailSenderNodeSettingsTest() {
        super(Map.of(SettingsType.MODEL, EmailSenderNodeSettings.class));
    }
    
    /** 
     * Some additional verification against the input table. Large parts are tested in the workflow test(s) but 
     * it's missing some corner cases.
     */
    @SuppressWarnings("static-method")
    @Test
    void testInputValidation() {
        EmailSenderNodeSettings settings = new EmailSenderNodeSettings();
        settings.m_messageSettings.m_attachmentColumn = "path";
        settings.m_messageSettings.m_message = "ignored message";
        settings.m_messageSettings.m_subject = "ignored subject";
        settings.m_recipientsSettings.m_to = "ignored@recipient";
        assertDoesNotThrow(() -> settings.validate(), "Settings validation"); // NOSONAR
        
        final InvalidSettingsException e1 = assertThrows(InvalidSettingsException.class, 
            () -> settings.validateDuringConfiguration(new PortType[] {EmailSessionPortObject.TYPE}, i -> null),
            "exception when input has no attachment table");
        assertThat("exception detail matches", e1.getMessage(), matchesPattern(".*input.*connected.*"));
        
        final InvalidSettingsException e2 = assertThrows(InvalidSettingsException.class,
            () -> settings.validateDuringConfiguration(
                new PortType[]{EmailSessionPortObject.TYPE, BufferedDataTable.TYPE},
                i -> i == 1 ? Optional.ofNullable(new DataTableSpec(
                    new DataColumnSpecCreator("other-path", SimpleFSLocationCellFactory.TYPE).createSpec())) : null),
            "exception when input table but no matching column");
        assertThat("exception detail matches", e2.getMessage(), matchesPattern(".*'path'.*not present.*"));
        
    }

}
