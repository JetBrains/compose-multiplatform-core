package androidx.room3.integration.kotlintestapp.test

import androidx.room3.migration.AutoMigrationSpec
import androidx.room3.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.`annotation`.processing.Generated
import kotlin.Suppress

@Generated(value = ["androidx.room3.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
internal class PreKmpDatabase_AutoMigration_1_2_Impl : Migration {
    private val callback: AutoMigrationSpec

    public constructor(callback: AutoMigrationSpec) : super(1, 2) {
        this.callback = callback
    }

    public override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE `DeletedEntity`")
        callback.onPostMigrate(db)
    }
}
