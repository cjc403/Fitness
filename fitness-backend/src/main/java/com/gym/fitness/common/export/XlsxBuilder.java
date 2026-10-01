package com.gym.fitness.common.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简的 xlsx 构建器：表头加粗、列宽、冻结首行、数字/日期按类型写入。
 *
 * <p>用途是"导出给人看"的报表。约定（与 CSV 导出一致的精神）：<b>中文表头、中文取值</b>，
 * 让文件在 WPS/Excel 里打开就是排好版的表格，不需要手动拉列宽。</p>
 */
public final class XlsxBuilder implements AutoCloseable {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Workbook workbook = new XSSFWorkbook();
    private final CellStyle headerStyle;
    private final CellStyle moneyStyle;
    private final List<String> sheetNames = new ArrayList<>();
    private Sheet sheet;
    private int rowIndex;

    public XlsxBuilder() {
        Font bold = workbook.createFont();
        bold.setBold(true);
        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(bold);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat format = workbook.createDataFormat();
        moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(format.getFormat("#,##0.00"));
    }

    /** 新建一张表：写入中文表头、加粗、并冻结首行。 */
    public XlsxBuilder sheet(String name, String... headers) {
        sheet = workbook.createSheet(name);
        sheetNames.add(name);
        rowIndex = 0;
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
        return this;
    }

    /** 追加一行；null 写成空串，BigDecimal 按金额格式，LocalDate/LocalDateTime 按可识别格式。 */
    public XlsxBuilder row(Object... values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object v = values[i];
            if (v == null) {
                cell.setCellValue("");
            } else if (v instanceof BigDecimal amount) {
                cell.setCellValue(amount.doubleValue());
                cell.setCellStyle(moneyStyle);
            } else if (v instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (v instanceof LocalDate date) {
                cell.setCellValue(DAY.format(date));
            } else if (v instanceof LocalDateTime stamp) {
                cell.setCellValue(STAMP.format(stamp));
            } else {
                cell.setCellValue(String.valueOf(v));
            }
        }
        return this;
    }

    /** 设置某列宽度（单位≈字符数）。 */
    public XlsxBuilder width(int column, int characters) {
        sheet.setColumnWidth(column, Math.max(6, characters) * 256);
        return this;
    }

    /** 有没有写过数据行（用于"没有差异"之类的占位判断）。 */
    public boolean isEmptySheet() {
        return rowIndex <= 1;
    }

    public List<String> sheetNames() {
        return sheetNames;
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 Excel 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (Exception ignored) {
            // 关闭失败不影响已生成的字节
        }
    }
}
