package com.area9innovation.flow;

import java.util.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class Database extends NativeHost {

    private Struct illegal = null;

    private String dbErr = "";

    /*
      This two classes intended to store relations between
      connection and result set made with them.

      Also, for `lastInsertIdDb()` couple of things prepared:
      - LRU RS for storing RS
      - autoGeneratedRS for getting last insert id.

      Database exceptions stored at RSObject.err if possible or
      at DBObject.err if something wrong before RS available.
     */

    private class DBObject {
        public Connection con = null;
        public String err = "";
        public RSObject lrurs = null;
        public ArrayList<RSObject> rsList = new ArrayList<RSObject>();
    }

    private class RSObject {
        public ResultSet rs = null;
        public DBObject dbObj = null;
        public ResultSet autoGeneratedRS = null;
        public String err = "";

        public RSObject(DBObject dbo) {
            dbObj = dbo;
            dbObj.rsList.add(this);
        }
    }

    public Database() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
            Integer strid = runtime.struct_ids.get("IllegalStruct");
            illegal = runtime.struct_prototypes[strid];
        } catch (Exception e) {

        }
    }

    public final Object connectDb(String host, Integer port, String socket, String user, String password, String database) {
        DBObject db = new DBObject();
        try {
			if (socket.isEmpty()) {
	            db.con = DriverManager.getConnection(String.format("jdbc:mysql://%s:%d/%s?allowMultiQueries=true&zeroDateTimeBehavior=convertToNull&autoReconnect=true&characterEncoding=UTF-8", host, port, database), user, password);
			} else {
				// This makes H2 work
				Class.forName("org.h2.Driver");

				// If the socket is given, we just send the socket directly as is
				db.con = DriverManager.getConnection(socket, user, password);
			}
            db.err = "";
            dbErr = "";
            return db;
        } catch (SQLException se) {
            System.out.println(se.getMessage());
            db.err = se.getMessage();
            dbErr = se.getMessage();
            return null;
        } catch (Exception e) {
            printException(e);
            return null;
        }
    }

    public final String connectExceptionDb(Object database) {
        if (database == null) return dbErr;
        return ((DBObject) database).err;
    }

    public final Object closeDb(Object database) {
        try {
            if (database != null) {
                for (RSObject i : ((DBObject) database).rsList) {
                    if (i.rs != null)
                        i.rs.close();
                }
                ((DBObject) database).con.close();
            }
        } catch (SQLException se) {
            ((DBObject) database).err = se.getMessage();
        } catch (Exception e) {
            printException(e);
        }
        return null;
    }

    public final String escapeDb(Object database, String s) {
        return s.replace("\\", "\\\\")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\\x1A", "\\Z")
                .replace("\\x00", "\\0")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    public final Object requestDb(Object database, String query) {
        if (database == null) return null;
        try {
            RSObject rso = new RSObject(((DBObject) database));

            Statement stmt = rso.dbObj.con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            boolean resType = stmt.execute(query, Statement.RETURN_GENERATED_KEYS);
            if (resType) {
                rso.rs = stmt.getResultSet();
            } else {
                rso.autoGeneratedRS = stmt.getGeneratedKeys();
            }

            rso.err = "";
            rso.dbObj.lrurs = rso;

            return (Object) rso;
        } catch (SQLException se) {
            System.out.println(se.getMessage());
            ((DBObject) database).err = se.getMessage();
            return null;
        } catch (Exception e) {
            printException(e);
            return null;
        }
    }

    public final String requestExceptionDb(Object database) {
        if (database != null) {
            return ((DBObject) database).err;
        } else {
            return "";
        }
    }

    public final Integer lastInsertIdDb(Object database) {
        DBObject db = (DBObject) database;
        if (db == null) return -1;
        if (db.lrurs == null) return -1;
        if (db.lrurs.autoGeneratedRS == null) return -1;
        try {
            if (db.lrurs.autoGeneratedRS.next()) {
                int r = db.lrurs.autoGeneratedRS.getInt(1);
                db.lrurs.autoGeneratedRS.close();
                db.lrurs.autoGeneratedRS = null;
                return r;
            } else {
                return -1;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return -1;
        } catch (Exception e) {
            printException(e);
            return -1;
        }
    }

    public final Integer resultLengthDb(Object result) {
        RSObject res = (RSObject) result;
        try {
            int curPosition = res.rs.getRow();
            if (res.rs.last()) {
                int length = res.rs.getRow();
                res.rs.absolute(curPosition);
                return length;
            } else {
                return 0;
            }
        } catch (SQLException se) {
            System.out.println(se.getMessage());
            return 0;
        } catch (Exception e) {
            printException(e);
            return 0;
        }
    }

    public final Boolean hasNextResultDb(Object result) {
        RSObject res = (RSObject) result;
        try {
            if (res == null) return false;
            return notEmptyResultSet(res.rs);
        } catch (SQLException se) {
            res.err = se.getMessage();
            return false;
        } catch (Exception e) {
            printException(e);
            return false;
        }
    }

    private Boolean notEmptyResultSet(ResultSet rs) throws SQLException {
        return (rs.isBeforeFirst() || rs.getRow() > 0) && !(rs.isLast() || rs.isAfterLast());
    }

    // Collect single row of nulls in order to preserve columns names
    // Can be used as a special case for empty tables
    private Struct[] getNullRowValues(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        Struct[] values = new Struct[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            String name = rsmd.getColumnLabel(i);
            values[i - 1] = runtime.makeStructValue("DbNullField", new Object[]{name}, illegal);
        }

        return values;
    }

    private Struct[] getRowValues(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();
        Struct[] values = new Struct[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            Struct value;
            String name = rsmd.getColumnLabel(i);
            int type = rsmd.getColumnType(i);

            Struct anull = runtime.makeStructValue("DbNullField", new Object[]{name}, illegal);

            if (type == Types.NULL) {
                value = runtime.makeStructValue("DbNullField", new Object[]{name}, illegal);
            } else if (type == Types.DOUBLE) {
                Double dvalue = rs.getDouble(i);
                value = rs.wasNull() ? anull : runtime.makeStructValue("DbDoubleField", new Object[]{name, dvalue}, illegal);
            } else if (type == Types.INTEGER || type == Types.TINYINT) {
                Integer ivalue = rs.getInt(i);
                value = rs.wasNull() ? anull : runtime.makeStructValue("DbIntField", new Object[]{name, ivalue}, illegal);
            } else if (type == Types.DECIMAL || type == Types.REAL) {
                Double dvalue = rs.getDouble(i);
                value = rs.wasNull() ? anull : runtime.makeStructValue("DbDoubleField", new Object[]{name, dvalue}, illegal);
            } else if (type == Types.TIMESTAMP) {
                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                Timestamp t = rs.getTimestamp(i, calendar);
                if (t == null || rs.wasNull()) {
                    value = anull;
                } else {
                    DateFormat format = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    String svalue = format.format(t);
                    value = runtime.makeStructValue("DbStringField", new Object[]{name, svalue}, illegal);
                }
            } else {
                String svalue = rs.getString(i);
                value = rs.wasNull() ? anull : runtime.makeStructValue("DbStringField", new Object[]{name, svalue}, illegal);
            }

            values[i - 1] = value;
        }

        return values;
    }

    public final Struct[][][] requestDbMulti(Object database, Object[] queries) {
        Struct[][][] empty = new Struct[0][][];
        if (database == null || queries.length == 0) return empty;

        // Do not allow empty queries at all
        for (Object query : queries) {
            if (query == "") return empty;
        }

        ArrayList<Struct[][]> res = new ArrayList<Struct[][]>();
        try {

            DBObject dbo = (DBObject) database;
            String[] q1 = new String[queries.length];

            for (int i = 0; i < queries.length; i++) {
                q1[i] = (String) (queries[i]);
            }
            String sql = String.join(";", q1);

            Statement stmt = dbo.con.createStatement();
            boolean isResultSet = stmt.execute(sql);
            Integer updateCount = stmt.getUpdateCount();
            while (isResultSet || updateCount != -1) {
                if (isResultSet) {
                    ArrayList<Struct[]> table = new ArrayList<Struct[]>();
                    ResultSet rs = stmt.getResultSet();
                    while (notEmptyResultSet(rs)) {
                        rs.next();
                        table.add(getRowValues(rs));
                    }
                    if (table.isEmpty()) {
                        // The table is empty but we need to return columns names somehow
                        table.add(getNullRowValues(rs));
                    }
                    res.add(table.toArray(new Struct[0][]));
                } else {
                    // We ignore updateCount here
                    res.add(new Struct[0][]);
                }
                isResultSet = stmt.getMoreResults();
                updateCount = stmt.getUpdateCount();
            }

            return res.toArray(new Struct[res.size()][][]);
        } catch (SQLException se) {
            System.out.println(se.getMessage());
            ((DBObject) database).err = se.getMessage();
            return empty;
        }
    }

    public final Struct[] nextResultDb(Object result) {
        RSObject res = (RSObject) result;

        if (res == null) return new Struct[0];
        try {
            if (res.rs.next()) {
                res.dbObj.lrurs = res;
                return getRowValues(res.rs);
            } else {
                return getNullRowValues(res.rs);
            }
        } catch (SQLException e) {
            return new Struct[0];
        } catch (Exception e) {
            printException(e);
            return new Struct[0];
        }
    }

    public static void printException(Exception e) {
        System.out.println("Exception: '" + e.toString() + "' at:");
        e.printStackTrace();
    }
}
