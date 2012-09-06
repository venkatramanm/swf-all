package com.venky.swf.db.model.reflection;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.JdbcTypeHelper.TypeConverter;
import com.venky.swf.db.JdbcTypeHelper.TypeRef;
import com.venky.swf.db.model.Model;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

public class ModelReader<M extends Model> extends ModelIO<M> {

	public ModelReader(Class<M> modelClass) {
		super(modelClass);
	}
	
	public List<M> read(Sheet sheet){
		Iterator<Row> rowIterator = sheet.iterator();
        Row header = rowIterator.hasNext() ? rowIterator.next() : null;
        List<M> records = new ArrayList<M>();
        if (header == null){
        	return records;
        }
        
        String[] heading = new String[header.getLastCellNum()]; // as cell indexes start at zero,LastCellNum can be seen as Size of row  
        for (int i = 0 ; i < heading.length ; i ++ ){
            heading[i] = header.getCell(i).getStringCellValue();
        }
        while (rowIterator.hasNext()){
        	Row row = rowIterator.next();
        	M m = createInstance();
        	copyRowValuesToBean(m, row, heading);
        	records.add(m);
        }
        return records;
	}

	public CellStyle getHeaderStyle(Sheet sheet){ 
        return sheet.getRow(0).getCell(0).getCellStyle();
    }
    
	protected void copyRowValuesToBean(M m, Row row,String[] heading) {
		ModelReflector<M> ref = ModelReflector.instance(getBeanClass());

		for (int i = 0; i < heading.length; i++) {
			Method getter = getGetter(heading[i]);
			if (getter == null) {
				continue;
			}

			GetterType type = GetterType.UNKNOWN_GETTER;
			if (ref.getFieldGetterMatcher().matches(getter)) {
				type = GetterType.FIELD_GETTER;
			} else if (ref.getReferredModelGetterMatcher().matches(getter)) {
				type = GetterType.REFERENCE_MODEL_GETTER;
			}

			Method setter = null;
			if (type == GetterType.REFERENCE_MODEL_GETTER) {
				setter = ref.getFieldSetter(ref.getReferenceField(getter));
			} else if (type == GetterType.FIELD_GETTER) {
				setter = ref.getFieldSetter(ref.getFieldName(getter));
			}
			if (setter == null) {
				continue;
			}

			Cell cell = row.getCell(i);
			Object value = null;
			if (cell == null) {
				continue;
			}
			if (type == GetterType.REFERENCE_MODEL_GETTER) {
				String descriptionValue = cell.getStringCellValue();
				if (!ObjectUtil.isVoid(descriptionValue)) {
					Class<? extends Model> referredModelClass = ref
							.getReferredModelClass(getter);
					ModelReflector<? extends Model> referredModelReflector = ModelReflector
							.instance(referredModelClass);
					Select referredModelIdFinder = new Select().from(
							referredModelClass).where(
							new Expression(referredModelReflector
									.getDescriptionColumn(), Operator.EQ,
									descriptionValue));
					List<? extends Model> referredModelRecords = referredModelIdFinder
							.execute(referredModelClass);
					if (referredModelRecords.isEmpty()) {
						throw new RuntimeException(
								referredModelClass.getSimpleName() + ":"
										+ descriptionValue + " not setup ");
					} else if (referredModelRecords.size() > 1) {
						throw new RuntimeException(
								referredModelClass.getSimpleName() + ":"
										+ descriptionValue
										+ " not setup uniquely");
					} else {
						Model referredModel = referredModelRecords.get(0);
						value = referredModel.getId();
					}
				}
			} else if (type == GetterType.FIELD_GETTER) {
				TypeRef<?> tref = Database.getJdbcTypeHelper().getTypeRef(
						getter.getReturnType());
				TypeConverter<?> converter = tref.getTypeConverter();
				switch (cell.getCellType()) {
				case Cell.CELL_TYPE_NUMERIC:
					if (HSSFDateUtil.isCellDateFormatted(cell)) {
						value = cell.getDateCellValue();
					} else {
						value = cell.getNumericCellValue();
					}
					break;
				case Cell.CELL_TYPE_BOOLEAN:
					value = cell.getBooleanCellValue();
					break;
				case Cell.CELL_TYPE_FORMULA:
					Class<?> returnType = getter.getReturnType();
					if (isDate(returnType)) {
						value = cell.getDateCellValue();
					} else if (isBoolean(returnType)) {
						value = cell.getBooleanCellValue();
					} else if (isNumeric(returnType)) {
						value = cell.getNumericCellValue();
					} else {
						value = cell.getStringCellValue();
					}
					break;
				default:
					value = cell.getStringCellValue();
					break;
				}

				if (!ObjectUtil.isVoid(value)
						|| !ref.getColumnDescriptor(getter).isNullable()) {
					value = converter.valueOf(value);
				} else if (ref.getColumnDescriptor(getter).isNullable()) {
					value = null;
				}
			}

			try {
				setter.invoke(m, value);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Cannot set " + heading[i] + " as "
						+ value + " of Class " + value.getClass().getName(), e);
			}
		}

	}
}