package com.vactis.dto.excel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportSummary {
    private String sheetName;
    private int totalRows;
    private int validRows;
    private int errorRows;
    private String fromDate;
    private String toDate;
    private Long totalAmount;
    private String status;
    private List<String> columns = new ArrayList<>();
    private List<String> issues = new ArrayList<>();
    private List<Map<String, Object>> previewRows = new ArrayList<>();
    private int importedRows;
}
