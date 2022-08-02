package pl.raportsa.rsmapper;

import pl.raportsa.rsmapper.annotations.Column;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import pl.raportsa.rsmapper.exceptions.ConvertException;
import pl.raportsa.rsmapper.exceptions.ParseTypeException;
import pl.raportsa.rsmapper.exceptions.SetterException;
import pl.raportsa.rsmapper.tools.DateTools;

public class RSMapper<T> {

    private final Class clazz;
    private final ResultSet rs;
    private final boolean closeResultSet;

    public RSMapper(Class clazz, ResultSet rs, boolean closeResultSet) {
        this.clazz = clazz;
        this.rs = rs;
        this.closeResultSet = closeResultSet;
    }

    public RSMapper(Class clazz, ResultSet rs) {
        this(clazz, rs, true);
    }

    public List<T> parseList() throws Exception {
        List<T> list = new ArrayList<>();
        while (rs.next()) {
            list.add(parse(getValuesMap()));
        }

        if (closeResultSet) {
            rs.close();
        }

        return list;
    }

    public T parseSingle() throws Exception {
        T obj = null;
        while (rs.next()) {
            obj = parse(getValuesMap());
            break;
        }
        if (closeResultSet) {
            rs.close();
        }
        return obj;
    }

    private LinkedHashMap<String, String> getValuesMap() {
        try {
            LinkedHashMap<String, String> rowData = new LinkedHashMap<>();
            for (int col = 1; col < rs.getMetaData().getColumnCount() + 1; col++) {
                String columnName = rs.getMetaData().getColumnLabel(col);
                rowData.put(columnName, rs.getString(col));
            }
            return rowData;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private String getValuesFromMap(String columnName, LinkedHashMap<String, String> valueMap) {
        Optional<String> column = valueMap.keySet()
                .stream()
                .filter(s -> s.equalsIgnoreCase(columnName))
                .findFirst();
        return column.map(valueMap::get).orElse(null);

    }

    private T parse(LinkedHashMap<String, String> valueMap) throws Exception {
        Object o = null;

        Constructor<? extends Object> cons = clazz.getConstructor();
        o = cons.newInstance();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            String columnName = getColumnName(field);
            String value = getValuesFromMap(columnName, valueMap);
            if (Objects.isNull(value)) {
                continue;
            }
            field.setAccessible(true);
            Object parseType = parseType(value, field.getType().getName());
            field.set(o, parseType);
//            invokeSetter(o, field.getName(),);
        }

        return (T) o;
    }

    private Object parseType(String value, String fieldType) throws Exception {
        try {
            switch (fieldType) {
                case "int":
                case "java.lang.Integer":
                    return Integer.parseInt(value);
                case "boolean":
                case "java.lang.Boolean":
                    return Boolean.valueOf(value.trim());
                case "byte":
                case "java.lang.Byte":
                    return Byte.valueOf(value);
                case "short":
                case "java.lang.Short":
                    return Short.valueOf(value);
                case "long":
                case "java.lang.Long":
                    return Long.valueOf(value);
                case "char":
                case "java.lang.Character":
                    return value.charAt(0);
                case "float":
                case "java.lang.Float":
                    return Float.valueOf(value);
                case "double":
                case "java.lang.Double":
                    return Double.valueOf(value);
                case "java.sql.Date":
                    try {
                        return new java.sql.Date(DateTools.sdfDateTime.parse(value).getTime());
                    } catch (Exception e) {
                        return new java.sql.Date(DateTools.sdfDate.parse(value).getTime());
                    }
                case "java.sql.Time":
                    return new java.sql.Time(DateTools.sdfTime.parse(value).getTime());
                case "java.util.Date":
                    try {
                        return DateTools.sdfDateTime.parse(value);
                    } catch (Exception e) {
                        return DateTools.sdfDate.parse(value);
                    }
                case "java.lang.String":
                    return value;
                case "java.sql.Timestamp":
                    return Timestamp.valueOf(value);
                default:
                    throw new ParseTypeException("Type " + fieldType + " is not avaliable to mapped.");
            }
        } catch (ParseTypeException pe) {
            throw pe;
        } catch (NumberFormatException | ParseException e) {
            throw new ConvertException("Value " + value + " cannot converted to type " + fieldType);
        }
    }

    @Deprecated
    private void invokeSetter(Object obj, String propertyName, Object variableValue) throws Exception {
        PropertyDescriptor pd;
        try {
            pd = new PropertyDescriptor(propertyName, obj.getClass());
            Method setter = pd.getWriteMethod();
            try {
                setter.invoke(obj, variableValue);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw e;
            }
        } catch (IntrospectionException e) {
            if (e.getMessage().contains("Method not found")) {
                throw new SetterException("Setter for field " + propertyName + " not found !");
            }
            throw e;
        }

    }

    private String getColumnName(Field field) {
        Column annotation = field.getAnnotation(Column.class);
        if (annotation == null) {
            return field.getName();
        }
        return annotation.name();
    }

}
