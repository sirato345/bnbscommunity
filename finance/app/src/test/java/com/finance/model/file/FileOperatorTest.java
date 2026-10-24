package com.finance.model.file;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class FileOperatorTest {

    @Test
    public void includesTrumpTablesInDatabaseTableList() {
        List<String> tables = FileOperator.getTableList();

        assertTrue(tables.contains("trump_d"));
        assertTrue(tables.contains("trump_3d"));
    }
}
