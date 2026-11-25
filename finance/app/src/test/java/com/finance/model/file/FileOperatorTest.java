package com.finance.model.file;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FileOperatorTest {

    @Test
    public void excludesTrumpTablesFromDatabaseTableList() {
        List<String> tables = FileOperator.getTableList();

        assertFalse(tables.contains("trump_d"));
        assertFalse(tables.contains("trump_3d"));
    }

    @Test
    public void includesBmnrTablesAndTheirInitialResources() {
        assertEquals("BMNR", FileOperator.getSymbolMap().getProperty("bmnr"));
        List<String> tables = FileOperator.getTableList();
        for (String timeframe : new String[] {"d", "w", "m", "q", "y"}) {
            String table = "bmnr_" + timeframe;
            assertTrue(tables.contains(table));
            assertNotNull(FileOperator.class.getClassLoader().getResource(table + ".csv"));
        }
        assertFalse(tables.contains("bmnr_3d"));
    }
}
