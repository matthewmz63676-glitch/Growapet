/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.jdbc3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.growapet.libs.sqlite.core.CoreResultSet;
import me.growapet.libs.sqlite.core.CoreStatement;
import me.growapet.libs.sqlite.core.DB;
import me.growapet.libs.sqlite.date.FastDateFormat;

public abstract class JDBC3ResultSet
extends CoreResultSet {
    protected static final Pattern COLUMN_TYPENAME = Pattern.compile("([^\\(]*)");
    protected static final Pattern COLUMN_TYPECAST = Pattern.compile("cast\\(.*?\\s+as\\s+(.*?)\\s*\\)");
    protected static final Pattern COLUMN_PRECISION = Pattern.compile(".*?\\((.*?)\\)");

    protected JDBC3ResultSet(CoreStatement stmt) {
        super(stmt);
    }

    public int findColumn(String col) throws SQLException {
        this.checkOpen();
        Integer index = this.findColumnIndexInCache(col);
        if (index != null) {
            return index;
        }
        for (int i = 0; i < this.cols.length; ++i) {
            if (!col.equalsIgnoreCase(this.cols[i])) continue;
            return this.addColumnIndexInCache(col, i + 1);
        }
        throw new SQLException("no such column: '" + col + "'");
    }

    public boolean next() throws SQLException {
        if (!this.open || this.emptyResultSet || this.pastLastRow) {
            return false;
        }
        this.lastCol = -1;
        if (this.row == 0) {
            ++this.row;
            return true;
        }
        if (this.maxRows != 0L && (long)this.row == this.maxRows) {
            return false;
        }
        int statusCode = this.stmt.pointer.safeRunInt(DB::step);
        switch (statusCode) {
            case 101: {
                this.pastLastRow = true;
                return false;
            }
            case 100: {
                ++this.row;
                return true;
            }
        }
        this.getDatabase().throwex(statusCode);
        return false;
    }

    public int getType() {
        return 1003;
    }

    public int getFetchSize() {
        return this.limitRows;
    }

    public void setFetchSize(int rows) throws SQLException {
        if (0 > rows || this.maxRows != 0L && (long)rows > this.maxRows) {
            throw new SQLException("fetch size " + rows + " out of bounds " + this.maxRows);
        }
        this.limitRows = rows;
    }

    public int getFetchDirection() throws SQLException {
        this.checkOpen();
        return 1000;
    }

    public void setFetchDirection(int d) throws SQLException {
        this.checkOpen();
        if (d != 1000) {
            throw new SQLException("only FETCH_FORWARD direction supported");
        }
    }

    public boolean isAfterLast() {
        return this.pastLastRow && !this.emptyResultSet;
    }

    public boolean isBeforeFirst() {
        return !this.emptyResultSet && this.open && this.row == 0;
    }

    public boolean isFirst() {
        return this.row == 1;
    }

    public boolean isLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("not supported by sqlite");
    }

    public int getRow() {
        return this.row;
    }

    public boolean wasNull() throws SQLException {
        return this.safeGetColumnType(this.markCol(this.lastCol)) == 5;
    }

    public BigDecimal getBigDecimal(int col) throws SQLException {
        switch (this.safeGetColumnType(this.checkCol(col))) {
            case 5: {
                return null;
            }
            case 1: {
                return BigDecimal.valueOf(this.safeGetLongCol(col));
            }
        }
        String stringValue = this.safeGetColumnText(col);
        try {
            return new BigDecimal(stringValue);
        }
        catch (NumberFormatException e) {
            throw new SQLException("Bad value for type BigDecimal : " + stringValue);
        }
    }

    public BigDecimal getBigDecimal(String col) throws SQLException {
        return this.getBigDecimal(this.findColumn(col));
    }

    public boolean getBoolean(int col) throws SQLException {
        return this.getInt(col) != 0;
    }

    public boolean getBoolean(String col) throws SQLException {
        return this.getBoolean(this.findColumn(col));
    }

    public InputStream getBinaryStream(int col) throws SQLException {
        byte[] bytes = this.getBytes(col);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return null;
    }

    public InputStream getBinaryStream(String col) throws SQLException {
        return this.getBinaryStream(this.findColumn(col));
    }

    public byte getByte(int col) throws SQLException {
        return (byte)this.getInt(col);
    }

    public byte getByte(String col) throws SQLException {
        return this.getByte(this.findColumn(col));
    }

    public byte[] getBytes(int col) throws SQLException {
        return this.stmt.pointer.safeRun((db, ptr) -> db.column_blob(ptr, this.markCol(col)));
    }

    public byte[] getBytes(String col) throws SQLException {
        return this.getBytes(this.findColumn(col));
    }

    public Reader getCharacterStream(int col) throws SQLException {
        String string = this.getString(col);
        return string == null ? null : new StringReader(string);
    }

    public Reader getCharacterStream(String col) throws SQLException {
        return this.getCharacterStream(this.findColumn(col));
    }

    public Date getDate(int col) throws SQLException {
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Date(this.getConnectionConfig().getDateFormat().parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing date", e);
                }
            }
            case 2: {
                return new Date(this.julianDateToCalendar(this.safeGetDoubleCol(col)).getTimeInMillis());
            }
        }
        return new Date(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
    }

    public Date getDate(int col, Calendar cal) throws SQLException {
        this.requireCalendarNotNull(cal);
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    FastDateFormat dateFormat = FastDateFormat.getInstance(this.getConnectionConfig().getDateStringFormat(), cal.getTimeZone());
                    return new Date(dateFormat.parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }
            }
            case 2: {
                return new Date(this.julianDateToCalendar(this.safeGetDoubleCol(col), cal).getTimeInMillis());
            }
        }
        cal.setTimeInMillis(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
        return new Date(cal.getTime().getTime());
    }

    public Date getDate(String col) throws SQLException {
        return this.getDate(this.findColumn(col), Calendar.getInstance());
    }

    public Date getDate(String col, Calendar cal) throws SQLException {
        return this.getDate(this.findColumn(col), cal);
    }

    public double getDouble(int col) throws SQLException {
        if (this.safeGetColumnType(this.markCol(col)) == 5) {
            return 0.0;
        }
        return this.safeGetDoubleCol(col);
    }

    public double getDouble(String col) throws SQLException {
        return this.getDouble(this.findColumn(col));
    }

    public float getFloat(int col) throws SQLException {
        if (this.safeGetColumnType(this.markCol(col)) == 5) {
            return 0.0f;
        }
        return (float)this.safeGetDoubleCol(col);
    }

    public float getFloat(String col) throws SQLException {
        return this.getFloat(this.findColumn(col));
    }

    public int getInt(int col) throws SQLException {
        return this.stmt.pointer.safeRunInt((db, ptr) -> db.column_int(ptr, this.markCol(col)));
    }

    public int getInt(String col) throws SQLException {
        return this.getInt(this.findColumn(col));
    }

    public long getLong(int col) throws SQLException {
        return this.safeGetLongCol(col);
    }

    public long getLong(String col) throws SQLException {
        return this.getLong(this.findColumn(col));
    }

    public short getShort(int col) throws SQLException {
        return (short)this.getInt(col);
    }

    public short getShort(String col) throws SQLException {
        return this.getShort(this.findColumn(col));
    }

    public String getString(int col) throws SQLException {
        return this.safeGetColumnText(col);
    }

    public String getString(String col) throws SQLException {
        return this.getString(this.findColumn(col));
    }

    public Time getTime(int col) throws SQLException {
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Time(this.getConnectionConfig().getDateFormat().parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing time", e);
                }
            }
            case 2: {
                return new Time(this.julianDateToCalendar(this.safeGetDoubleCol(col)).getTimeInMillis());
            }
        }
        return new Time(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
    }

    public Time getTime(int col, Calendar cal) throws SQLException {
        this.requireCalendarNotNull(cal);
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    FastDateFormat dateFormat = FastDateFormat.getInstance(this.getConnectionConfig().getDateStringFormat(), cal.getTimeZone());
                    return new Time(dateFormat.parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing time", e);
                }
            }
            case 2: {
                return new Time(this.julianDateToCalendar(this.safeGetDoubleCol(col), cal).getTimeInMillis());
            }
        }
        cal.setTimeInMillis(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
        return new Time(cal.getTime().getTime());
    }

    public Time getTime(String col) throws SQLException {
        return this.getTime(this.findColumn(col));
    }

    public Time getTime(String col, Calendar cal) throws SQLException {
        return this.getTime(this.findColumn(col), cal);
    }

    public Timestamp getTimestamp(int col) throws SQLException {
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    return new Timestamp(this.getConnectionConfig().getDateFormat().parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }
            }
            case 2: {
                return new Timestamp(this.julianDateToCalendar(this.safeGetDoubleCol(col)).getTimeInMillis());
            }
        }
        return new Timestamp(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
    }

    public Timestamp getTimestamp(int col, Calendar cal) throws SQLException {
        this.requireCalendarNotNull(cal);
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 5: {
                return null;
            }
            case 3: {
                String dateText = this.safeGetColumnText(col);
                if ("".equals(dateText)) {
                    return null;
                }
                try {
                    FastDateFormat dateFormat = FastDateFormat.getInstance(this.getConnectionConfig().getDateStringFormat(), cal.getTimeZone());
                    return new Timestamp(dateFormat.parse(dateText).getTime());
                }
                catch (Exception e) {
                    throw new SQLException("Error parsing time stamp", e);
                }
            }
            case 2: {
                return new Timestamp(this.julianDateToCalendar(this.safeGetDoubleCol(col)).getTimeInMillis());
            }
        }
        cal.setTimeInMillis(this.safeGetLongCol(col) * this.getConnectionConfig().getDateMultiplier());
        return new Timestamp(cal.getTime().getTime());
    }

    public Timestamp getTimestamp(String col) throws SQLException {
        return this.getTimestamp(this.findColumn(col));
    }

    public Timestamp getTimestamp(String c, Calendar ca) throws SQLException {
        return this.getTimestamp(this.findColumn(c), ca);
    }

    public Object getObject(int col) throws SQLException {
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 1: {
                long val = this.getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return new Long(val);
                }
                return new Integer((int)val);
            }
            case 2: {
                return new Double(this.getDouble(col));
            }
            case 4: {
                return this.getBytes(col);
            }
            case 5: {
                return null;
            }
        }
        return this.getString(col);
    }

    public Object getObject(String col) throws SQLException {
        return this.getObject(this.findColumn(col));
    }

    public Statement getStatement() {
        return (Statement)((Object)this.stmt);
    }

    public String getCursorName() {
        return null;
    }

    public SQLWarning getWarnings() {
        return null;
    }

    public void clearWarnings() {
    }

    public ResultSetMetaData getMetaData() {
        return (ResultSetMetaData)((Object)this);
    }

    public String getCatalogName(int col) throws SQLException {
        return this.safeGetColumnTableName(col);
    }

    public String getColumnClassName(int col) throws SQLException {
        switch (this.safeGetColumnType(this.markCol(col))) {
            case 1: {
                long val = this.getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return "java.lang.Long";
                }
                return "java.lang.Integer";
            }
            case 2: {
                return "java.lang.Double";
            }
            case 4: 
            case 5: {
                return "java.lang.Object";
            }
        }
        return "java.lang.String";
    }

    public int getColumnCount() throws SQLException {
        this.checkCol(1);
        return this.colsMeta.length;
    }

    public int getColumnDisplaySize(int col) {
        return Integer.MAX_VALUE;
    }

    public String getColumnLabel(int col) throws SQLException {
        return this.getColumnName(col);
    }

    public String getColumnName(int col) throws SQLException {
        return this.safeGetColumnName(col);
    }

    public int getColumnType(int col) throws SQLException {
        String typeName = this.getColumnTypeName(col);
        int valueType = this.safeGetColumnType(this.checkCol(col));
        if (valueType == 1 || valueType == 5) {
            if ("BOOLEAN".equals(typeName)) {
                return 16;
            }
            if ("TINYINT".equals(typeName)) {
                return -6;
            }
            if ("SMALLINT".equals(typeName) || "INT2".equals(typeName)) {
                return 5;
            }
            if ("BIGINT".equals(typeName) || "INT8".equals(typeName) || "UNSIGNED BIG INT".equals(typeName)) {
                return -5;
            }
            if ("DATE".equals(typeName) || "DATETIME".equals(typeName)) {
                return 91;
            }
            if ("TIMESTAMP".equals(typeName)) {
                return 93;
            }
            if (valueType == 1 || "INT".equals(typeName) || "INTEGER".equals(typeName) || "MEDIUMINT".equals(typeName)) {
                long val = this.getLong(col);
                if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
                    return -5;
                }
                return 4;
            }
        }
        if (valueType == 2 || valueType == 5) {
            if ("DECIMAL".equals(typeName)) {
                return 3;
            }
            if ("DOUBLE".equals(typeName) || "DOUBLE PRECISION".equals(typeName)) {
                return 8;
            }
            if ("NUMERIC".equals(typeName)) {
                return 2;
            }
            if ("REAL".equals(typeName)) {
                return 7;
            }
            if (valueType == 2 || "FLOAT".equals(typeName)) {
                return 6;
            }
        }
        if (valueType == 3 || valueType == 5) {
            if ("CHARACTER".equals(typeName) || "NCHAR".equals(typeName) || "NATIVE CHARACTER".equals(typeName) || "CHAR".equals(typeName)) {
                return 1;
            }
            if ("CLOB".equals(typeName)) {
                return 2005;
            }
            if ("DATE".equals(typeName) || "DATETIME".equals(typeName)) {
                return 91;
            }
            if ("TIMESTAMP".equals(typeName)) {
                return 93;
            }
            if (valueType == 3 || "VARCHAR".equals(typeName) || "VARYING CHARACTER".equals(typeName) || "NVARCHAR".equals(typeName) || "TEXT".equals(typeName)) {
                return 12;
            }
        }
        if (valueType == 4 || valueType == 5) {
            if ("BINARY".equals(typeName)) {
                return -2;
            }
            if (valueType == 4 || "BLOB".equals(typeName)) {
                return 2004;
            }
        }
        return 2;
    }

    public String getColumnTypeName(int col) throws SQLException {
        String declType = this.getColumnDeclType(col);
        if (declType != null) {
            Matcher matcher = COLUMN_TYPENAME.matcher(declType);
            matcher.find();
            return matcher.group(1).toUpperCase(Locale.ENGLISH);
        }
        switch (this.safeGetColumnType(this.checkCol(col))) {
            case 1: {
                return "INTEGER";
            }
            case 2: {
                return "FLOAT";
            }
            case 4: {
                return "BLOB";
            }
            case 3: {
                return "TEXT";
            }
        }
        return "NUMERIC";
    }

    public int getPrecision(int col) throws SQLException {
        String declType = this.getColumnDeclType(col);
        if (declType != null) {
            Matcher matcher = COLUMN_PRECISION.matcher(declType);
            return matcher.find() ? Integer.parseInt(matcher.group(1).split(",")[0].trim()) : 0;
        }
        return 0;
    }

    private String getColumnDeclType(int col) throws SQLException {
        String declType = this.stmt.pointer.safeRun((db, ptr) -> db.column_decltype(ptr, this.checkCol(col)));
        if (declType == null) {
            Matcher matcher = COLUMN_TYPECAST.matcher(this.safeGetColumnName(col));
            declType = matcher.find() ? matcher.group(1) : null;
        }
        return declType;
    }

    public int getScale(int col) throws SQLException {
        String[] array;
        Matcher matcher;
        String declType = this.getColumnDeclType(col);
        if (declType != null && (matcher = COLUMN_PRECISION.matcher(declType)).find() && (array = matcher.group(1).split(",")).length == 2) {
            return Integer.parseInt(array[1].trim());
        }
        return 0;
    }

    public String getSchemaName(int col) {
        return "";
    }

    public String getTableName(int col) throws SQLException {
        String tableName = this.safeGetColumnTableName(col);
        if (tableName == null) {
            return "";
        }
        return tableName;
    }

    public int isNullable(int col) throws SQLException {
        this.checkMeta();
        return this.meta[this.checkCol(col)][0] ? 0 : 1;
    }

    public boolean isAutoIncrement(int col) throws SQLException {
        this.checkMeta();
        return this.meta[this.checkCol(col)][2];
    }

    public boolean isCaseSensitive(int col) {
        return true;
    }

    public boolean isCurrency(int col) {
        return false;
    }

    public boolean isDefinitelyWritable(int col) {
        return true;
    }

    public boolean isReadOnly(int col) {
        return false;
    }

    public boolean isSearchable(int col) {
        return true;
    }

    public boolean isSigned(int col) throws SQLException {
        String typeName = this.getColumnTypeName(col);
        return "NUMERIC".equals(typeName) || "INTEGER".equals(typeName) || "REAL".equals(typeName);
    }

    public boolean isWritable(int col) {
        return true;
    }

    public int getConcurrency() {
        return 1007;
    }

    public boolean rowDeleted() {
        return false;
    }

    public boolean rowInserted() {
        return false;
    }

    public boolean rowUpdated() {
        return false;
    }

    private Calendar julianDateToCalendar(Double jd) {
        return this.julianDateToCalendar(jd, Calendar.getInstance());
    }

    private Calendar julianDateToCalendar(Double jd, Calendar cal) {
        int A;
        if (jd == null) {
            return null;
        }
        double w = jd + 0.5;
        int Z = (int)w;
        double F = w - (double)Z;
        if (Z < 2299161) {
            A = Z;
        } else {
            int alpha = (int)(((double)Z - 1867216.25) / 36524.25);
            A = Z + 1 + alpha - (int)((double)alpha / 4.0);
        }
        int B = A + 1524;
        int C = (int)(((double)B - 122.1) / 365.25);
        int D = (int)(365.25 * (double)C);
        int E = (int)((double)(B - D) / 30.6001);
        int mm = E - ((double)E < 13.5 ? 1 : 13);
        int yyyy = C - ((double)mm > 2.5 ? 4716 : 4715);
        double jjd = (double)(B - D - (int)(30.6001 * (double)E)) + F;
        int dd = (int)jjd;
        double hhd = jjd - (double)dd;
        int hh = (int)(24.0 * hhd);
        double mnd = 24.0 * hhd - (double)hh;
        int mn = (int)(60.0 * mnd);
        double ssd = 60.0 * mnd - (double)mn;
        int ss = (int)(60.0 * ssd);
        double msd = 60.0 * ssd - (double)ss;
        int ms = (int)(1000.0 * msd);
        cal.set(yyyy, mm - 1, dd, hh, mn, ss);
        cal.set(14, ms);
        if (yyyy < 1) {
            cal.set(0, 0);
            cal.set(1, -(yyyy - 1));
        }
        return cal;
    }

    private void requireCalendarNotNull(Calendar cal) throws SQLException {
        if (cal == null) {
            throw new SQLException("Expected a calendar instance.", new IllegalArgumentException());
        }
    }

    protected int safeGetColumnType(int col) throws SQLException {
        return this.stmt.pointer.safeRunInt((db, ptr) -> db.column_type(ptr, col));
    }

    private long safeGetLongCol(int col) throws SQLException {
        return this.stmt.pointer.safeRunLong((db, ptr) -> db.column_long(ptr, this.markCol(col)));
    }

    private double safeGetDoubleCol(int col) throws SQLException {
        return this.stmt.pointer.safeRunDouble((db, ptr) -> db.column_double(ptr, this.markCol(col)));
    }

    private String safeGetColumnText(int col) throws SQLException {
        return this.stmt.pointer.safeRun((db, ptr) -> db.column_text(ptr, this.markCol(col)));
    }

    private String safeGetColumnTableName(int col) throws SQLException {
        return this.stmt.pointer.safeRun((db, ptr) -> db.column_table_name(ptr, this.checkCol(col)));
    }

    private String safeGetColumnName(int col) throws SQLException {
        return this.stmt.pointer.safeRun((db, ptr) -> db.column_name(ptr, this.checkCol(col)));
    }
}

