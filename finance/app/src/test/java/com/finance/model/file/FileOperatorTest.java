package com.finance.model.file;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;

public class FileOperatorTest {

    @Test
    public void excludesTrumpTablesFromDatabaseTableList() {
        List<String> tables = FileOperator.getTableList();

        assertFalse(tables.contains("trump_d"));
        assertFalse(tables.contains("trump_3d"));
    }
}
