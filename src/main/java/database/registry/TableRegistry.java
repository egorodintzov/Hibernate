package database.registry;

import annotations.Column;
import annotations.OneToOne;
import annotations.Table;
import database.mapper.TableMapper;
import database.util.ConnectionUtil;
import database.util.EntityUtil;
import exceptions.NotFoundException;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TableRegistry {

    private Connection connection;

    public TableRegistry() {
        this.connection = ConnectionUtil.setNewConnection();
    }

    public void addAllTables() throws InstantiationException, IllegalAccessException, SQLException {
        Set<Class<?>> tableClasses = loadAllTableClasses();
        List<String> tableNames = loadAllTableNames(tableClasses);

        for(String tableName : tableNames) {
            String request = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + fieldsToString(getClassByTableName(tableName,tableClasses)) + ")";
            Statement statement = connection.createStatement();
            statement.execute(request);
        }
    }

    private Set<Class<?>> loadAllTableClasses() {
        Reflections reflections = new Reflections("model");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Table.class);
        return classes;
    }

    private List<String> loadAllTableNames(Set<Class<?>> tableClasses) {
        return tableClasses.stream().map(c -> {
            Table table = c.getAnnotation(Table.class);
            return table.name();
        }).collect(Collectors.toList());
    }

    public Class getClassByTableName(String tableName,Set<Class<?>> classes) {
        for(Class clazz : classes) {
            Table table = (Table) clazz.getAnnotation(Table.class);
            if(table.name().equals(tableName)) {
                return clazz;
            }
        }
        throw new NotFoundException(String.format("Table with name %s not found",tableName));
    }

    public String fieldsToString(Class clazz) throws IllegalAccessException, InstantiationException {
        TableMapper tableMapper = new TableMapper(clazz.newInstance());
        return tableMapper.fieldsToString();
    }

    /* ------------------------------------- */

    public void addAllNotCreatedFields() throws SQLException, InstantiationException, IllegalAccessException {
        Set<Class<?>> tableClasses = loadAllTableClasses();
        List<String> tableNames = loadAllTableNames(tableClasses);

        for(String tableName : tableNames) {
            String request = "select column_name  from INFORMATION_SCHEMA.columns where table_name=" + "'" + tableName + "'";
            Statement statement = connection.createStatement();
            statement.execute(request);
            ResultSet resultSet = statement.getResultSet();

            TableMapper tableMapper = new TableMapper(getClassByTableName(tableName,tableClasses).newInstance());
            while(resultSet.next()) {
                tableMapper.getAllNotCreatedFieldNames(resultSet);
            }

            for(Object field : tableMapper.getFields()) {
                Field f = (Field) field;
                Annotation annotation = f.getAnnotation(OneToOne.class);
                if(annotation!=null) {
                    addColumn(tableName,f.getAnnotation(Column.class).name(),"BIGINT");

                    EntityUtil entityUtil = new EntityUtil(f.getType().newInstance());
                    String request2 = "ALTER TABLE " + tableName + " ADD FOREIGN KEY (" + f.getAnnotation(Column.class).name() + ") REFERENCES " + f.getType().getAnnotation(Table.class).name() + " (" + entityUtil.getColumnNames().get(entityUtil.getIdField().getName()) + ")" ;
                    System.out.println(request2);
                    Statement statement2 = connection.createStatement();
                    statement2.execute(request2);
                    statement2.close();
                }
                else {
                    addColumn(tableName,f.getAnnotation(Column.class).name(),BasicTypeRegistry.getTypes().get(f.getType().getTypeName()));
                }
            }
        }
    }

    private void addColumn(String tableName, String columnName, String type) throws SQLException {
        String request = "ALTER TABLE " + tableName + " ADD " + columnName + " " + type;
        Statement statement = connection.createStatement();
        statement.execute(request);
        statement.close();
    }

}
