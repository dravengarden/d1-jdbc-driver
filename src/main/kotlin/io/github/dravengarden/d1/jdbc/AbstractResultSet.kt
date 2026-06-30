package io.github.dravengarden.d1.jdbc

import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.net.URL
import java.sql.Array
import java.sql.Blob
import java.sql.Clob
import java.sql.Date
import java.sql.NClob
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.RowId
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLType
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Statement
import java.sql.Time
import java.sql.Timestamp
import java.util.Calendar

private fun ni(name: String): Nothing =
    throw SQLFeatureNotSupportedException("d1: ResultSet.$name not supported")

public abstract class AbstractResultSet : ResultSet {
    // --- Wrapper ---

    override fun <T : Any?> unwrap(iface: Class<T>?): T = ni("unwrap")

    override fun isWrapperFor(iface: Class<*>?): Boolean = ni("isWrapperFor")

    // --- Cursor / lifecycle ---

    override fun next(): Boolean = ni("next")

    override fun close(): Unit = ni("close")

    override fun wasNull(): Boolean = ni("wasNull")

    // --- getXxx by column index ---

    override fun getString(columnIndex: Int): String? = ni("getString")

    override fun getBoolean(columnIndex: Int): Boolean = ni("getBoolean")

    override fun getByte(columnIndex: Int): Byte = ni("getByte")

    override fun getShort(columnIndex: Int): Short = ni("getShort")

    override fun getInt(columnIndex: Int): Int = ni("getInt")

    override fun getLong(columnIndex: Int): Long = ni("getLong")

    override fun getFloat(columnIndex: Int): Float = ni("getFloat")

    override fun getDouble(columnIndex: Int): Double = ni("getDouble")

    @Deprecated("Deprecated in Java")
    override fun getBigDecimal(columnIndex: Int, scale: Int): BigDecimal? = ni("getBigDecimal")

    override fun getBytes(columnIndex: Int): ByteArray? = ni("getBytes")

    override fun getDate(columnIndex: Int): Date? = ni("getDate")

    override fun getTime(columnIndex: Int): Time? = ni("getTime")

    override fun getTimestamp(columnIndex: Int): Timestamp? = ni("getTimestamp")

    override fun getAsciiStream(columnIndex: Int): InputStream? = ni("getAsciiStream")

    @Deprecated("Deprecated in Java")
    override fun getUnicodeStream(columnIndex: Int): InputStream? = ni("getUnicodeStream")

    override fun getBinaryStream(columnIndex: Int): InputStream? = ni("getBinaryStream")

    // --- getXxx by column label ---

    override fun getString(columnLabel: String?): String? = ni("getString")

    override fun getBoolean(columnLabel: String?): Boolean = ni("getBoolean")

    override fun getByte(columnLabel: String?): Byte = ni("getByte")

    override fun getShort(columnLabel: String?): Short = ni("getShort")

    override fun getInt(columnLabel: String?): Int = ni("getInt")

    override fun getLong(columnLabel: String?): Long = ni("getLong")

    override fun getFloat(columnLabel: String?): Float = ni("getFloat")

    override fun getDouble(columnLabel: String?): Double = ni("getDouble")

    @Deprecated("Deprecated in Java")
    override fun getBigDecimal(columnLabel: String?, scale: Int): BigDecimal? = ni("getBigDecimal")

    override fun getBytes(columnLabel: String?): ByteArray? = ni("getBytes")

    override fun getDate(columnLabel: String?): Date? = ni("getDate")

    override fun getTime(columnLabel: String?): Time? = ni("getTime")

    override fun getTimestamp(columnLabel: String?): Timestamp? = ni("getTimestamp")

    override fun getAsciiStream(columnLabel: String?): InputStream? = ni("getAsciiStream")

    @Deprecated("Deprecated in Java")
    override fun getUnicodeStream(columnLabel: String?): InputStream? = ni("getUnicodeStream")

    override fun getBinaryStream(columnLabel: String?): InputStream? = ni("getBinaryStream")

    // --- Warnings / cursor name / metadata ---

    override fun getWarnings(): SQLWarning? = ni("getWarnings")

    override fun clearWarnings(): Unit = ni("clearWarnings")

    override fun getCursorName(): String? = ni("getCursorName")

    override fun getMetaData(): ResultSetMetaData = ni("getMetaData")

    // --- getObject ---

    override fun getObject(columnIndex: Int): Any? = ni("getObject")

    override fun getObject(columnLabel: String?): Any? = ni("getObject")

    override fun getObject(columnIndex: Int, map: MutableMap<String, Class<*>>?): Any? = ni("getObject")

    override fun getObject(columnLabel: String?, map: MutableMap<String, Class<*>>?): Any? = ni("getObject")

    override fun <T : Any?> getObject(columnIndex: Int, type: Class<T>?): T = ni("getObject")

    override fun <T : Any?> getObject(columnLabel: String?, type: Class<T>?): T = ni("getObject")

    override fun findColumn(columnLabel: String?): Int = ni("findColumn")

    // --- character streams ---

    override fun getCharacterStream(columnIndex: Int): Reader? = ni("getCharacterStream")

    override fun getCharacterStream(columnLabel: String?): Reader? = ni("getCharacterStream")

    // --- BigDecimal (non-scale) ---

    override fun getBigDecimal(columnIndex: Int): BigDecimal? = ni("getBigDecimal")

    override fun getBigDecimal(columnLabel: String?): BigDecimal? = ni("getBigDecimal")

    // --- cursor position queries ---

    override fun isBeforeFirst(): Boolean = ni("isBeforeFirst")

    override fun isAfterLast(): Boolean = ni("isAfterLast")

    override fun isFirst(): Boolean = ni("isFirst")

    override fun isLast(): Boolean = ni("isLast")

    override fun beforeFirst(): Unit = ni("beforeFirst")

    override fun afterLast(): Unit = ni("afterLast")

    override fun first(): Boolean = ni("first")

    override fun last(): Boolean = ni("last")

    override fun getRow(): Int = ni("getRow")

    override fun absolute(row: Int): Boolean = ni("absolute")

    override fun relative(rows: Int): Boolean = ni("relative")

    override fun previous(): Boolean = ni("previous")

    // --- fetch direction / size ---

    override fun setFetchDirection(direction: Int): Unit = ni("setFetchDirection")

    override fun getFetchDirection(): Int = ni("getFetchDirection")

    override fun setFetchSize(rows: Int): Unit = ni("setFetchSize")

    override fun getFetchSize(): Int = ni("getFetchSize")

    override fun getType(): Int = ni("getType")

    override fun getConcurrency(): Int = ni("getConcurrency")

    // --- row state ---

    override fun rowUpdated(): Boolean = ni("rowUpdated")

    override fun rowInserted(): Boolean = ni("rowInserted")

    override fun rowDeleted(): Boolean = ni("rowDeleted")

    // --- updateXxx by column index ---

    override fun updateNull(columnIndex: Int): Unit = ni("updateNull")

    override fun updateBoolean(columnIndex: Int, x: Boolean): Unit = ni("updateBoolean")

    override fun updateByte(columnIndex: Int, x: Byte): Unit = ni("updateByte")

    override fun updateShort(columnIndex: Int, x: Short): Unit = ni("updateShort")

    override fun updateInt(columnIndex: Int, x: Int): Unit = ni("updateInt")

    override fun updateLong(columnIndex: Int, x: Long): Unit = ni("updateLong")

    override fun updateFloat(columnIndex: Int, x: Float): Unit = ni("updateFloat")

    override fun updateDouble(columnIndex: Int, x: Double): Unit = ni("updateDouble")

    override fun updateBigDecimal(columnIndex: Int, x: BigDecimal?): Unit = ni("updateBigDecimal")

    override fun updateString(columnIndex: Int, x: String?): Unit = ni("updateString")

    override fun updateBytes(columnIndex: Int, x: ByteArray?): Unit = ni("updateBytes")

    override fun updateDate(columnIndex: Int, x: Date?): Unit = ni("updateDate")

    override fun updateTime(columnIndex: Int, x: Time?): Unit = ni("updateTime")

    override fun updateTimestamp(columnIndex: Int, x: Timestamp?): Unit = ni("updateTimestamp")

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?, length: Int): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?, length: Int): Unit = ni("updateBinaryStream")

    override fun updateCharacterStream(columnIndex: Int, x: Reader?, length: Int): Unit = ni("updateCharacterStream")

    override fun updateObject(columnIndex: Int, x: Any?, scaleOrLength: Int): Unit = ni("updateObject")

    override fun updateObject(columnIndex: Int, x: Any?): Unit = ni("updateObject")

    // --- updateXxx by column label ---

    override fun updateNull(columnLabel: String?): Unit = ni("updateNull")

    override fun updateBoolean(columnLabel: String?, x: Boolean): Unit = ni("updateBoolean")

    override fun updateByte(columnLabel: String?, x: Byte): Unit = ni("updateByte")

    override fun updateShort(columnLabel: String?, x: Short): Unit = ni("updateShort")

    override fun updateInt(columnLabel: String?, x: Int): Unit = ni("updateInt")

    override fun updateLong(columnLabel: String?, x: Long): Unit = ni("updateLong")

    override fun updateFloat(columnLabel: String?, x: Float): Unit = ni("updateFloat")

    override fun updateDouble(columnLabel: String?, x: Double): Unit = ni("updateDouble")

    override fun updateBigDecimal(columnLabel: String?, x: BigDecimal?): Unit = ni("updateBigDecimal")

    override fun updateString(columnLabel: String?, x: String?): Unit = ni("updateString")

    override fun updateBytes(columnLabel: String?, x: ByteArray?): Unit = ni("updateBytes")

    override fun updateDate(columnLabel: String?, x: Date?): Unit = ni("updateDate")

    override fun updateTime(columnLabel: String?, x: Time?): Unit = ni("updateTime")

    override fun updateTimestamp(columnLabel: String?, x: Timestamp?): Unit = ni("updateTimestamp")

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?, length: Int): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?, length: Int): Unit = ni("updateBinaryStream")

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?, length: Int): Unit =
        ni("updateCharacterStream")

    override fun updateObject(columnLabel: String?, x: Any?, scaleOrLength: Int): Unit = ni("updateObject")

    override fun updateObject(columnLabel: String?, x: Any?): Unit = ni("updateObject")

    // --- row operations ---

    override fun insertRow(): Unit = ni("insertRow")

    override fun updateRow(): Unit = ni("updateRow")

    override fun deleteRow(): Unit = ni("deleteRow")

    override fun refreshRow(): Unit = ni("refreshRow")

    override fun cancelRowUpdates(): Unit = ni("cancelRowUpdates")

    override fun moveToInsertRow(): Unit = ni("moveToInsertRow")

    override fun moveToCurrentRow(): Unit = ni("moveToCurrentRow")

    override fun getStatement(): Statement? = ni("getStatement")

    // --- date/time with Calendar ---

    override fun getDate(columnIndex: Int, cal: Calendar?): Date? = ni("getDate")

    override fun getDate(columnLabel: String?, cal: Calendar?): Date? = ni("getDate")

    override fun getTime(columnIndex: Int, cal: Calendar?): Time? = ni("getTime")

    override fun getTime(columnLabel: String?, cal: Calendar?): Time? = ni("getTime")

    override fun getTimestamp(columnIndex: Int, cal: Calendar?): Timestamp? = ni("getTimestamp")

    override fun getTimestamp(columnLabel: String?, cal: Calendar?): Timestamp? = ni("getTimestamp")

    // --- SQL object getters ---

    override fun getRef(columnIndex: Int): Ref? = ni("getRef")

    override fun getRef(columnLabel: String?): Ref? = ni("getRef")

    override fun getBlob(columnIndex: Int): Blob? = ni("getBlob")

    override fun getBlob(columnLabel: String?): Blob? = ni("getBlob")

    override fun getClob(columnIndex: Int): Clob? = ni("getClob")

    override fun getClob(columnLabel: String?): Clob? = ni("getClob")

    override fun getArray(columnIndex: Int): Array? = ni("getArray")

    override fun getArray(columnLabel: String?): Array? = ni("getArray")

    override fun getURL(columnIndex: Int): URL? = ni("getURL")

    override fun getURL(columnLabel: String?): URL? = ni("getURL")

    // --- Ref / Blob / Clob / Array updaters ---

    override fun updateRef(columnIndex: Int, x: Ref?): Unit = ni("updateRef")

    override fun updateRef(columnLabel: String?, x: Ref?): Unit = ni("updateRef")

    override fun updateBlob(columnIndex: Int, x: Blob?): Unit = ni("updateBlob")

    override fun updateBlob(columnLabel: String?, x: Blob?): Unit = ni("updateBlob")

    override fun updateClob(columnIndex: Int, x: Clob?): Unit = ni("updateClob")

    override fun updateClob(columnLabel: String?, x: Clob?): Unit = ni("updateClob")

    override fun updateArray(columnIndex: Int, x: Array?): Unit = ni("updateArray")

    override fun updateArray(columnLabel: String?, x: Array?): Unit = ni("updateArray")

    // --- RowId ---

    override fun getRowId(columnIndex: Int): RowId? = ni("getRowId")

    override fun getRowId(columnLabel: String?): RowId? = ni("getRowId")

    override fun updateRowId(columnIndex: Int, x: RowId?): Unit = ni("updateRowId")

    override fun updateRowId(columnLabel: String?, x: RowId?): Unit = ni("updateRowId")

    // --- holdability / closed ---

    override fun getHoldability(): Int = ni("getHoldability")

    override fun isClosed(): Boolean = ni("isClosed")

    // --- NString ---

    override fun updateNString(columnIndex: Int, nString: String?): Unit = ni("updateNString")

    override fun updateNString(columnLabel: String?, nString: String?): Unit = ni("updateNString")

    // --- NClob ---

    override fun updateNClob(columnIndex: Int, nClob: NClob?): Unit = ni("updateNClob")

    override fun updateNClob(columnLabel: String?, nClob: NClob?): Unit = ni("updateNClob")

    override fun getNClob(columnIndex: Int): NClob? = ni("getNClob")

    override fun getNClob(columnLabel: String?): NClob? = ni("getNClob")

    // --- SQLXML ---

    override fun getSQLXML(columnIndex: Int): SQLXML? = ni("getSQLXML")

    override fun getSQLXML(columnLabel: String?): SQLXML? = ni("getSQLXML")

    override fun updateSQLXML(columnIndex: Int, xmlObject: SQLXML?): Unit = ni("updateSQLXML")

    override fun updateSQLXML(columnLabel: String?, xmlObject: SQLXML?): Unit = ni("updateSQLXML")

    // --- NString / NCharacterStream getters ---

    override fun getNString(columnIndex: Int): String? = ni("getNString")

    override fun getNString(columnLabel: String?): String? = ni("getNString")

    override fun getNCharacterStream(columnIndex: Int): Reader? = ni("getNCharacterStream")

    override fun getNCharacterStream(columnLabel: String?): Reader? = ni("getNCharacterStream")

    override fun updateNCharacterStream(columnIndex: Int, x: Reader?, length: Long): Unit =
        ni("updateNCharacterStream")

    override fun updateNCharacterStream(columnLabel: String?, reader: Reader?, length: Long): Unit =
        ni("updateNCharacterStream")

    // --- updateXxxStream with long length ---

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?, length: Long): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?, length: Long): Unit = ni("updateBinaryStream")

    override fun updateCharacterStream(columnIndex: Int, x: Reader?, length: Long): Unit = ni("updateCharacterStream")

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?, length: Long): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?, length: Long): Unit =
        ni("updateBinaryStream")

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?, length: Long): Unit =
        ni("updateCharacterStream")

    // --- Blob / Clob / NClob with InputStream/Reader + length ---

    override fun updateBlob(columnIndex: Int, inputStream: InputStream?, length: Long): Unit = ni("updateBlob")

    override fun updateBlob(columnLabel: String?, inputStream: InputStream?, length: Long): Unit = ni("updateBlob")

    override fun updateClob(columnIndex: Int, reader: Reader?, length: Long): Unit = ni("updateClob")

    override fun updateClob(columnLabel: String?, reader: Reader?, length: Long): Unit = ni("updateClob")

    override fun updateNClob(columnIndex: Int, reader: Reader?, length: Long): Unit = ni("updateNClob")

    override fun updateNClob(columnLabel: String?, reader: Reader?, length: Long): Unit = ni("updateNClob")

    // --- NCharacterStream without length ---

    override fun updateNCharacterStream(columnIndex: Int, x: Reader?): Unit = ni("updateNCharacterStream")

    override fun updateNCharacterStream(columnLabel: String?, reader: Reader?): Unit = ni("updateNCharacterStream")

    // --- updateXxxStream without length ---

    override fun updateAsciiStream(columnIndex: Int, x: InputStream?): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnIndex: Int, x: InputStream?): Unit = ni("updateBinaryStream")

    override fun updateCharacterStream(columnIndex: Int, x: Reader?): Unit = ni("updateCharacterStream")

    override fun updateAsciiStream(columnLabel: String?, x: InputStream?): Unit = ni("updateAsciiStream")

    override fun updateBinaryStream(columnLabel: String?, x: InputStream?): Unit = ni("updateBinaryStream")

    override fun updateCharacterStream(columnLabel: String?, reader: Reader?): Unit = ni("updateCharacterStream")

    // --- Blob / Clob / NClob with InputStream/Reader, no length ---

    override fun updateBlob(columnIndex: Int, inputStream: InputStream?): Unit = ni("updateBlob")

    override fun updateBlob(columnLabel: String?, inputStream: InputStream?): Unit = ni("updateBlob")

    override fun updateClob(columnIndex: Int, reader: Reader?): Unit = ni("updateClob")

    override fun updateClob(columnLabel: String?, reader: Reader?): Unit = ni("updateClob")

    override fun updateNClob(columnIndex: Int, reader: Reader?): Unit = ni("updateNClob")

    override fun updateNClob(columnLabel: String?, reader: Reader?): Unit = ni("updateNClob")

    // --- NClob getters via Reader updates already declared; NClob updaters with NClob already declared ---

    // --- JDBC 4.2 updateObject with SQLType ---

    override fun updateObject(columnIndex: Int, x: Any?, targetSqlType: SQLType?, scaleOrLength: Int): Unit =
        ni("updateObject")

    override fun updateObject(columnLabel: String?, x: Any?, targetSqlType: SQLType?, scaleOrLength: Int): Unit =
        ni("updateObject")

    override fun updateObject(columnIndex: Int, x: Any?, targetSqlType: SQLType?): Unit = ni("updateObject")

    override fun updateObject(columnLabel: String?, x: Any?, targetSqlType: SQLType?): Unit = ni("updateObject")
}
