import androidx.room3.RoomDatabase
import androidx.room3.RoomRawQuery
import androidx.room3.RoomSQLiteQuery
import androidx.room3.util.getColumnIndex
import androidx.room3.util.performBlocking
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteQuery
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.checkNotNull
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room3.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
internal class MyDao_Impl(
  __db: RoomDatabase,
) : MyDao {
  private val __db: RoomDatabase
  init {
    this.__db = __db
  }

  public override fun getEntitySupport(sql: SupportSQLiteQuery?): MyEntity? {
    checkNotNull(sql)
    val _rawQuery: RoomRawQuery = RoomSQLiteQuery.copyFrom(sql).toRoomRawQuery()
    val _sql: String = _rawQuery.sql
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _rawQuery.getBindingFunction().invoke(_stmt)
        val _result: MyEntity?
        if (_stmt.step()) {
          _result = __entityStatementConverter_MyEntity(_stmt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getEntity(query: RoomRawQuery?): MyEntity? {
    checkNotNull(query)
    val _sql: String = query.sql
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        query.getBindingFunction().invoke(_stmt)
        val _result: MyEntity?
        if (_stmt.step()) {
          _result = __entityStatementConverter_MyEntity(_stmt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __entityStatementConverter_MyEntity(statement: SQLiteStatement): MyEntity {
    val _entity: MyEntity
    val _columnIndexOfPk: Int = getColumnIndex(statement, "pk")
    _entity = MyEntity()
    if (_columnIndexOfPk != -1) {
      _entity.pk = statement.getLong(_columnIndexOfPk)
    }
    return _entity
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
