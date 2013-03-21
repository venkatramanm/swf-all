package com.venky.swf.db.model.io.xls;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.NumericConverter;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.io.ModelWriter;
import com.venky.swf.db.model.reflection.ModelReflector;

public class XLSModelWriter<M extends Model> extends XLSModelIO<M> implements ModelWriter<M,Row>{
	
	private final HashMap<String, Class<? extends Model>> referedModelMap = new HashMap<String,Class<? extends Model>>();
	private final HashMap<String, SequenceSet<String>> referredModelFieldsToExport = new HashMap<String, SequenceSet<String>>();
	public XLSModelWriter(Class<M> modelClass){
		this(modelClass,false);
	}
	public XLSModelWriter(Class<M> modelClass,boolean reportMode){
		super(modelClass);
		this.reportMode = reportMode;
    	ModelReflector<M> ref = getReflector();
    	
    	Iterator<String> fi = ref.getFields().iterator();
    	while(fi.hasNext()){
    		String fieldName = fi.next();
    		Method getter = ref.getFieldGetter(fieldName);
			Method referredModelGetter = ref.getReferredModelGetterFor(getter);
			
			if (referredModelGetter != null ){
				Class<? extends Model> referredModelClass = ref.getReferredModelClass(referredModelGetter);
				ModelReflector<? extends Model> referredModelReflector = ModelReflector.instance(referredModelClass);
				String baseFieldHeading = referredModelGetter.getName().substring("get".length());
				SequenceSet<String> fieldsToExport = new SequenceSet<String>();
				referedModelMap.put(fieldName,referredModelClass);
				if (!ref.isFieldSettable(fieldName)){
					fieldsToExport.add(baseFieldHeading + "." + StringUtil.camelize(referredModelReflector.getDescriptionField()));
				}else {
					loadFieldsToExport(fieldsToExport, baseFieldHeading, referredModelReflector);
				}
				referredModelFieldsToExport.put(fieldName,fieldsToExport);
			}
			
    	}
	}
	private boolean reportMode = false;
	
	private static final int START_ROW = 0; 
	private static final int START_COLUMN = 0;
	
	public void write(List<M> records, OutputStream os, List<String> fields) throws IOException {
		Workbook wb = new HSSFWorkbook();
		write(records,wb,fields);
		wb.write(os);
	}
	public void write(List<M> records, Workbook wb, List<String> fields) {
		Font font = createDefaultFont(wb);
		
		CellStyle headerStyle = wb.createCellStyle();
		headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
		headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
		headerStyle.setFont(font);
		headerStyle.setWrapText(true);
		
		String sheetName = StringUtil.pluralize(getBeanClass().getSimpleName());
		Sheet sheet = wb.createSheet(sheetName);
		
    	Bucket rowNum = new Bucket(START_ROW); 
    	Bucket columnNum = new Bucket(START_COLUMN);
    	Row header = sheet.createRow(rowNum.intValue());

    	Iterator<String> fi = fields.iterator();
		while (fi.hasNext()){
			String fieldName = fi.next();
			if (referedModelMap.get(fieldName) == null){
				createCell(sheet, header, columnNum, StringUtil.camelize(fieldName), headerStyle);
			}else {
				for (String headerField : referredModelFieldsToExport.get(fieldName)){
					createCell(sheet, header, columnNum, StringUtil.camelize(headerField), headerStyle);
				}
			}
		}
    	
    	
    	for (int i = 0 ; i < records.size() ; i ++ ){
    		M m = records.get(i);
    		rowNum.increment();
    		Row r = sheet.createRow(rowNum.intValue());
    		if (reportMode){
    			ModelReflector<M> reflector = getReflector();
        		if (i > 0){
	    			M prev = records.get(i-1);
	    			M clone = m.cloneProxy();
	    			for (int fieldNumber = 0 ; fieldNumber < fields.size() ; fieldNumber ++ ){
	    				String field = fields.get(fieldNumber);
	    				Object prevFieldValue = reflector.get(prev,field);
	    				Object currentFieldValue = reflector.get(m, field);
	    				if (ObjectUtil.equals(prevFieldValue, currentFieldValue)){
	    					reflector.set(clone, field, null);
	    				}else {
	    					break;
	    				}
	    			}
	    			m = clone;
	    		}
    		}
    		write(m,r,font,fields);
    	}
    	
	}

	@Override
	public void write(M m, Row r, List<String> fields) {
		write(m,r,null,fields);
	}
	private void write(M m, Row r, Font font, List<String> fields) {
		Workbook wb = r.getSheet().getWorkbook();
		
		CreationHelper createHelper = wb.getCreationHelper();
		CellStyle decimalStyle = wb.createCellStyle();
		decimalStyle.setDataFormat(createHelper.createDataFormat().getFormat("#.0##"));
		decimalStyle.setFont(font);
		
		CellStyle integerStyle = wb.createCellStyle();
		integerStyle.setDataFormat(createHelper.createDataFormat().getFormat("0"));
		integerStyle.setFont(font);
		
		CellStyle dateStyle = wb.createCellStyle();
		dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("d/m/yyyy"));
		dateStyle.setFont(font);
		
		CellStyle stringStyle = wb.createCellStyle();
		stringStyle.setWrapText(true);
		stringStyle.setFont(font);
		
		Iterator<String> fi = fields.iterator();
		ModelReflector<M> ref = getReflector();
		Bucket columnNum = new Bucket(START_COLUMN);
		while (fi.hasNext()){
			String f = fi.next();
			Object value = ref.get(m, f);
			if (referedModelMap.get(f) != null){
				for (String cf: referredModelFieldsToExport.get(f) ){
					writeNextColumn(r, columnNum, getValue(m,cf), integerStyle, decimalStyle,dateStyle,stringStyle);
				}
			}else {
				writeNextColumn(r, columnNum, value, integerStyle,decimalStyle, dateStyle,stringStyle);
			}
		}
	}
	

	
	protected Cell writeNextColumn(Row r, Bucket columnNum , Object value, CellStyle integerStyle, CellStyle decimalStyle,CellStyle dateStyle,CellStyle stringStyle){
		Cell cell = null; 
		if (!ObjectUtil.isVoid(value)){
			Class<?> colClass = value.getClass();
			if (isNumeric(colClass)){
				if (NumericConverter.class.isAssignableFrom(Database.getJdbcTypeHelper().getTypeRef(colClass).getTypeConverter().getClass())){
					cell = createCell(r.getSheet(),r,columnNum,value,decimalStyle);
				}else {
					cell = createCell(r.getSheet(),r,columnNum,value,integerStyle);
				}
            }else if (isDate(colClass)) {
            	cell = createCell(r.getSheet(), r, columnNum, value, dateStyle);
            }else if (isBoolean(colClass)) {
                cell = createCell(r.getSheet(), r, columnNum, value, null);
            }else{
                cell = createCell(r.getSheet(),r , columnNum, value , stringStyle);
            }
			
		}else {
			cell = createCell(r.getSheet(), r, columnNum, value, null);
		}
		return cell;
	}

	public static final int MAX_COLUMN_LENGTH = 50 ;
	public static final int CHARACTER_WIDTH = 293;
	public static final int CHARACTER_HEIGHT_IN_POINTS = 12 ;
	
	private int getColumnWidth(Sheet sheet,int columnIndex){
		if (sheet.getNumMergedRegions() == 0){
			return sheet.getColumnWidth(columnIndex);
		}else {
			boolean columnMergedInSomeRow = false; 
			Set<Integer> skipRows = new HashSet<Integer>();
			for (int i = 0 ; i < sheet.getNumMergedRegions() ; i ++ ){
				CellRangeAddress add = sheet.getMergedRegion(i);
				if (add.getFirstColumn() <= columnIndex && columnIndex <= add.getLastColumn() && (add.getFirstColumn() != add.getLastColumn()) ){
					columnMergedInSomeRow = true;
					for (int r = add.getFirstRow() ; r <= add.getLastRow() ; r ++ ){
						skipRows.add(r);
					}
				}
			}
			if (!columnMergedInSomeRow){
				return sheet.getColumnWidth(columnIndex);
			}else {
				int maxSkippedRow = Collections.max(skipRows);
				if (sheet.getPhysicalNumberOfRows() -1 <= maxSkippedRow + 1){
					return 0;
				}else {
					return sheet.getColumnWidth(columnIndex);	
				}
			}
		}
	}
	private int getRowHeight(String sValue){
		int vlen = 0; 
		int numRows = 1 ;
		StringTokenizer tok = new StringTokenizer(sValue," \n",true);
		
		while (tok.hasMoreTokens()){
			String token = tok.nextToken();
			int ctl = token.length() ;
			if (token.equals("\n")){
				vlen = (numRows * MAX_COLUMN_LENGTH);
			}
			if ( vlen + ctl > numRows * MAX_COLUMN_LENGTH) {
				vlen = (numRows * MAX_COLUMN_LENGTH) + ctl;
				numRows += (Math.ceil(ctl * 1.0/MAX_COLUMN_LENGTH)) ;
			}else {
				vlen += ctl ;
			}
		}
		return numRows ;
	}
	public static Font createDefaultFont(Workbook wb){
		Font font = wb.createFont();
		font.setFontName("Courier New");
		font.setFontHeightInPoints((short)(CHARACTER_HEIGHT_IN_POINTS-2));
		return font;
	}
	private void fixCellDimensions(Sheet sheet,Row row,Bucket columnNum,Object value){
		int currentColumnWidth = getColumnWidth(sheet,columnNum.intValue());
		String sValue = Database.getJdbcTypeHelper().getTypeRef(value.getClass()).getTypeConverter().toString(value);
		int currentValueLength = sValue.length() ;
		int maxRowHeight = getRowHeight(sValue);
		
		if (currentColumnWidth < MAX_COLUMN_LENGTH * CHARACTER_WIDTH){
			int currentValueWidth = (currentValueLength + 1)* CHARACTER_WIDTH; 
			currentColumnWidth = Math.min(Math.max(currentValueWidth,currentColumnWidth), MAX_COLUMN_LENGTH * CHARACTER_WIDTH);
			sheet.setColumnWidth(columnNum.intValue(), currentColumnWidth);
		}
		row.setHeightInPoints(Math.max(row.getHeightInPoints() , maxRowHeight*CHARACTER_HEIGHT_IN_POINTS));
		

	}
	public Cell createCell(Sheet sheet, Row row, Bucket columnNum , Object  value, CellStyle style){
		Cell cell = row.createCell(columnNum.intValue());
		if (style != null){
			cell.setCellStyle(style);
		}
		if (value != null){
			Class<?> colClass = value.getClass();
			if (style != null && style.getWrapText()){
				fixCellDimensions(sheet, row, columnNum, value);
			}
			if (isNumeric(colClass)){
				cell.setCellValue(Double.valueOf(String.valueOf(value)));
			}else if (isDate(colClass)){
				cell.setCellValue((Date)value);
			}else if (isBoolean(colClass)){
				cell.setCellValue(Boolean.valueOf(String.valueOf(value)));
			}else {
				cell.setCellValue(Database.getJdbcTypeHelper().getTypeRef(colClass).getTypeConverter().toString(value));
			}
		}
		columnNum.increment();
		return cell;
	}
}
