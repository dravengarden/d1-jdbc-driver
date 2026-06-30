package io.github.dravengarden.d1.jdbc

private fun ni(name: String): Nothing =
    throw java.sql.SQLFeatureNotSupportedException("d1: PreparedStatement.$name not supported")

public abstract class AbstractPreparedStatement :
    AbstractStatement(),
    java.sql.PreparedStatement {

    override fun executeQuery(): java.sql.ResultSet = ni("executeQuery")

    override fun executeUpdate(): Int = ni("executeUpdate")

    override fun execute(): Boolean = ni("execute")

    override fun addBatch(): Unit = ni("addBatch")

    override fun clearParameters(): Unit = ni("clearParameters")

    override fun getMetaData(): java.sql.ResultSetMetaData? = ni("getMetaData")

    override fun getParameterMetaData(): java.sql.ParameterMetaData = ni("getParameterMetaData")

    override fun setNull(parameterIndex: Int, sqlType: Int): Unit = ni("setNull")

    override fun setNull(parameterIndex: Int, sqlType: Int, typeName: String?): Unit = ni("setNull")

    override fun setBoolean(parameterIndex: Int, x: Boolean): Unit = ni("setBoolean")

    override fun setByte(parameterIndex: Int, x: Byte): Unit = ni("setByte")

    override fun setShort(parameterIndex: Int, x: Short): Unit = ni("setShort")

    override fun setInt(parameterIndex: Int, x: Int): Unit = ni("setInt")

    override fun setLong(parameterIndex: Int, x: Long): Unit = ni("setLong")

    override fun setFloat(parameterIndex: Int, x: Float): Unit = ni("setFloat")

    override fun setDouble(parameterIndex: Int, x: Double): Unit = ni("setDouble")

    override fun setBigDecimal(parameterIndex: Int, x: java.math.BigDecimal?): Unit = ni("setBigDecimal")

    override fun setString(parameterIndex: Int, x: String?): Unit = ni("setString")

    override fun setBytes(parameterIndex: Int, x: ByteArray?): Unit = ni("setBytes")

    override fun setDate(parameterIndex: Int, x: java.sql.Date?): Unit = ni("setDate")

    override fun setTime(parameterIndex: Int, x: java.sql.Time?): Unit = ni("setTime")

    override fun setTimestamp(parameterIndex: Int, x: java.sql.Timestamp?): Unit = ni("setTimestamp")

    override fun setDate(parameterIndex: Int, x: java.sql.Date?, cal: java.util.Calendar?): Unit = ni("setDate")

    override fun setTime(parameterIndex: Int, x: java.sql.Time?, cal: java.util.Calendar?): Unit = ni("setTime")

    override fun setTimestamp(parameterIndex: Int, x: java.sql.Timestamp?, cal: java.util.Calendar?): Unit =
        ni("setTimestamp")

    override fun setAsciiStream(parameterIndex: Int, x: java.io.InputStream?, length: Int): Unit = ni("setAsciiStream")

    override fun setAsciiStream(parameterIndex: Int, x: java.io.InputStream?, length: Long): Unit = ni("setAsciiStream")

    override fun setAsciiStream(parameterIndex: Int, x: java.io.InputStream?): Unit = ni("setAsciiStream")

    @Deprecated("JDBC")
    override fun setUnicodeStream(parameterIndex: Int, x: java.io.InputStream?, length: Int): Unit =
        ni("setUnicodeStream")

    override fun setBinaryStream(parameterIndex: Int, x: java.io.InputStream?, length: Int): Unit =
        ni("setBinaryStream")

    override fun setBinaryStream(parameterIndex: Int, x: java.io.InputStream?, length: Long): Unit =
        ni("setBinaryStream")

    override fun setBinaryStream(parameterIndex: Int, x: java.io.InputStream?): Unit = ni("setBinaryStream")

    override fun setCharacterStream(parameterIndex: Int, reader: java.io.Reader?, length: Int): Unit =
        ni("setCharacterStream")

    override fun setCharacterStream(parameterIndex: Int, reader: java.io.Reader?, length: Long): Unit =
        ni("setCharacterStream")

    override fun setCharacterStream(parameterIndex: Int, reader: java.io.Reader?): Unit = ni("setCharacterStream")

    override fun setNCharacterStream(parameterIndex: Int, value: java.io.Reader?, length: Long): Unit =
        ni("setNCharacterStream")

    override fun setNCharacterStream(parameterIndex: Int, value: java.io.Reader?): Unit = ni("setNCharacterStream")

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int): Unit = ni("setObject")

    override fun setObject(parameterIndex: Int, x: Any?): Unit = ni("setObject")

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int, scaleOrLength: Int): Unit = ni("setObject")

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: java.sql.SQLType?): Unit = ni("setObject")

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: java.sql.SQLType?, scaleOrLength: Int): Unit =
        ni("setObject")

    override fun setRef(parameterIndex: Int, x: java.sql.Ref?): Unit = ni("setRef")

    override fun setBlob(parameterIndex: Int, x: java.sql.Blob?): Unit = ni("setBlob")

    override fun setBlob(parameterIndex: Int, inputStream: java.io.InputStream?, length: Long): Unit = ni("setBlob")

    override fun setBlob(parameterIndex: Int, inputStream: java.io.InputStream?): Unit = ni("setBlob")

    override fun setClob(parameterIndex: Int, x: java.sql.Clob?): Unit = ni("setClob")

    override fun setClob(parameterIndex: Int, reader: java.io.Reader?, length: Long): Unit = ni("setClob")

    override fun setClob(parameterIndex: Int, reader: java.io.Reader?): Unit = ni("setClob")

    override fun setNClob(parameterIndex: Int, value: java.sql.NClob?): Unit = ni("setNClob")

    override fun setNClob(parameterIndex: Int, reader: java.io.Reader?, length: Long): Unit = ni("setNClob")

    override fun setNClob(parameterIndex: Int, reader: java.io.Reader?): Unit = ni("setNClob")

    override fun setArray(parameterIndex: Int, x: java.sql.Array?): Unit = ni("setArray")

    override fun setURL(parameterIndex: Int, x: java.net.URL?): Unit = ni("setURL")

    override fun setRowId(parameterIndex: Int, x: java.sql.RowId?): Unit = ni("setRowId")

    override fun setNString(parameterIndex: Int, value: String?): Unit = ni("setNString")

    override fun setSQLXML(parameterIndex: Int, xmlObject: java.sql.SQLXML?): Unit = ni("setSQLXML")

    override fun executeLargeUpdate(): Long = ni("executeLargeUpdate")
}
