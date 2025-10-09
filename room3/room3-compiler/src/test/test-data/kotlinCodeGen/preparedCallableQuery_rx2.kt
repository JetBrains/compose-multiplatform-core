import androidx.room3.RoomDatabase
import androidx.room3.RxRoom.Companion.createCompletable
import androidx.room3.RxRoom.Companion.createMaybe
import androidx.room3.RxRoom.Companion.createSingle
import androidx.room3.util.getLastInsertedRowId
import androidx.sqlite.SQLiteStatement
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
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

  public override fun insertPublisherSingle(id: String, name: String): Single<Long> {
    val _sql: String = "INSERT INTO MyEntity (pk, other) VALUES (?, ?)"
    return createSingle(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _argIndex = 2
        _stmt.bindText(_argIndex, name)
        _stmt.step()
        getLastInsertedRowId(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun insertPublisherMaybe(id: String, name: String): Maybe<Long> {
    val _sql: String = "INSERT INTO MyEntity (pk, other) VALUES (?, ?)"
    return createMaybe(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _argIndex = 2
        _stmt.bindText(_argIndex, name)
        _stmt.step()
        getLastInsertedRowId(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun insertPublisherCompletable(id: String, name: String): Completable {
    val _sql: String = "INSERT INTO MyEntity (pk, other) VALUES (?, ?)"
    return createCompletable(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _argIndex = 2
        _stmt.bindText(_argIndex, name)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
