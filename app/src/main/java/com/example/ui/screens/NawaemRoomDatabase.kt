package com.example.ui.screens

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ==========================================
// 1. Instutution Entity
// ==========================================
@Entity(tableName = "institutions")
data class RoomInstitution(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val address: String,
    val monthlyContractValue: Double,
    val shiftStartTime: String
)

fun Institution.toRoom() = RoomInstitution(
    id = id,
    name = name,
    type = type,
    address = address,
    monthlyContractValue = monthlyContractValue,
    shiftStartTime = shiftStartTime
)

fun RoomInstitution.toDomain() = Institution(
    id = id,
    name = name,
    type = type,
    supervisorName = "جاري التحميل",
    employeesCount = 0,
    progress = 1.0f,
    address = address,
    monthlyContractValue = monthlyContractValue,
    shiftStartTime = shiftStartTime
)

// ==========================================
// 2. Supervisor Entity
// ==========================================
@Entity(tableName = "supervisors")
data class RoomSupervisor(
    @PrimaryKey val id: String,
    val name: String,
    val assignedLocation: String,
    val phone: String,
    val activeSince: String,
    val submittedAttendanceToday: Boolean,
    val email: String,
    val passwordKey: String,
    val address: String,
    val monthlySalary: Double,
    val username: String,
    val bankName: String,
    val ibanCode: String
)

fun Supervisor.toRoom() = RoomSupervisor(
    id = id,
    name = name,
    assignedLocation = assignedLocation,
    phone = phone,
    activeSince = activeSince,
    submittedAttendanceToday = submittedAttendanceToday,
    email = email,
    passwordKey = passwordKey,
    address = address,
    monthlySalary = monthlySalary,
    username = username,
    bankName = bankName,
    ibanCode = ibanCode
)

fun RoomSupervisor.toDomain() = Supervisor(
    id = id,
    name = name,
    assignedLocation = assignedLocation,
    phone = phone,
    activeSince = activeSince,
    submittedAttendanceToday = submittedAttendanceToday,
    email = email,
    passwordKey = passwordKey,
    address = address,
    monthlySalary = monthlySalary,
    username = username,
    bankName = bankName,
    ibanCode = ibanCode
)

// ==========================================
// 3. Employee Entity
// ==========================================
@Entity(tableName = "employees")
data class RoomEmployee(
    @PrimaryKey val id: String,
    val name: String,
    val nationality: String,
    val iqamaId: String,
    val phone: String,
    val bankName: String,
    val iban: String,
    val baseSalary: Double,
    val location: String,
    val gender: String,
    val address: String,
    val department: String,
    val workDaysScheduled: Int,
    val shift: String,
    val bankAccountOwnerName: String,
    val bankAccountPhone: String,
    val status: String
)

fun Employee.toRoom() = RoomEmployee(
    id = id,
    name = name,
    nationality = nationality,
    iqamaId = iqamaId,
    phone = phone,
    bankName = bankName,
    iban = iban,
    baseSalary = baseSalary,
    location = location,
    gender = gender,
    address = address,
    department = department,
    workDaysScheduled = workDaysScheduled,
    shift = shift,
    bankAccountOwnerName = bankAccountOwnerName,
    bankAccountPhone = bankAccountPhone,
    status = status
)

fun RoomEmployee.toDomain() = Employee(
    id = id,
    name = name,
    nationality = nationality,
    iqamaId = iqamaId,
    phone = phone,
    bankName = bankName,
    iban = iban,
    baseSalary = baseSalary,
    location = location,
    gender = gender,
    address = address,
    department = department,
    workDaysScheduled = workDaysScheduled,
    shift = shift,
    bankAccountOwnerName = bankAccountOwnerName,
    bankAccountPhone = bankAccountPhone,
    status = status
)

// ==========================================
// 4. Attendance Record Entity
// ==========================================
@Entity(
    tableName = "attendance",
    primaryKeys = ["date", "employeeId", "shift"]
)
data class RoomAttendanceRecord(
    val date: String,
    val employeeId: String,
    val employeeName: String,
    val status: String,
    val lateMinutes: Int,
    val institutionName: String,
    val shift: String
)

// ==========================================
// 5. Notification Entity
// ==========================================
@Entity(tableName = "notifications")
data class RoomNotification(
    @PrimaryKey val id: String,
    val message: String,
    val roleTarget: String,
    val timestamp: String,
    val isRead: Boolean
)

fun NotificationItem.toRoom() = RoomNotification(
    id = id,
    message = message,
    roleTarget = roleTarget,
    timestamp = timestamp,
    isRead = isRead
)

fun RoomNotification.toDomain() = NotificationItem(
    id = id,
    message = message,
    roleTarget = roleTarget,
    timestamp = timestamp,
    isRead = isRead
)

// ==========================================
// 6. Invoice Entity
// ==========================================
@Entity(tableName = "invoices")
data class RoomInvoice(
    @PrimaryKey val id: String,
    val institutionName: String,
    val description: String,
    val totalAmount: Double,
    val dateTime: String,
    val invoiceImageUrl: String
)

fun Invoice.toRoom() = RoomInvoice(
    id = id,
    institutionName = institutionName,
    description = description,
    totalAmount = totalAmount,
    dateTime = dateTime,
    invoiceImageUrl = invoiceImageUrl
)

fun RoomInvoice.toDomain() = Invoice(
    id = id,
    institutionName = institutionName,
    description = description,
    totalAmount = totalAmount,
    dateTime = dateTime,
    invoiceImageUrl = invoiceImageUrl
)

// ==========================================
// Room DAO Definition
// ==========================================
@Dao
interface NawaemDao {
    // Institutions
    @Query("SELECT * FROM institutions")
    suspend fun getAllInstitutions(): List<RoomInstitution>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstitutions(institutions: List<RoomInstitution>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstitution(institution: RoomInstitution)

    @Query("DELETE FROM institutions")
    suspend fun clearInstitutions()

    @Delete
    suspend fun deleteInstitution(institution: RoomInstitution)

    // Supervisors
    @Query("SELECT * FROM supervisors")
    suspend fun getAllSupervisors(): List<RoomSupervisor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupervisors(supervisors: List<RoomSupervisor>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupervisor(supervisor: RoomSupervisor)

    @Query("DELETE FROM supervisors")
    suspend fun clearSupervisors()

    @Delete
    suspend fun deleteSupervisor(supervisor: RoomSupervisor)

    // Employees
    @Query("SELECT * FROM employees")
    suspend fun getAllEmployees(): List<RoomEmployee>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployees(employees: List<RoomEmployee>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: RoomEmployee)

    @Query("DELETE FROM employees")
    suspend fun clearEmployees()

    @Delete
    suspend fun deleteEmployee(employee: RoomEmployee)

    // Attendance
    @Query("SELECT * FROM attendance")
    suspend fun getAllAttendance(): List<RoomAttendanceRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(records: List<RoomAttendanceRecord>)

    @Query("DELETE FROM attendance")
    suspend fun clearAttendance()

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotifications(): List<RoomNotification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<RoomNotification>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: RoomNotification)

    @Query("DELETE FROM notifications")
    suspend fun clearNotifications()

    // Invoices
    @Query("SELECT * FROM invoices ORDER BY dateTime DESC")
    suspend fun getAllInvoices(): List<RoomInvoice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: RoomInvoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoices(invoices: List<RoomInvoice>)

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()
}

// ==========================================
// Database Class
// ==========================================
@Database(
    entities = [
        RoomInstitution::class,
        RoomSupervisor::class,
        RoomEmployee::class,
        RoomAttendanceRecord::class,
        RoomNotification::class,
        RoomInvoice::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NawaemRoomDatabase : RoomDatabase() {
    abstract fun nawaemDao(): NawaemDao

    companion object {
        @Volatile
        private var INSTANCE: NawaemRoomDatabase? = null

        fun getDatabase(context: Context): NawaemRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NawaemRoomDatabase::class.java,
                    "nawaem_local_db"
                )
                .addMigrations(
                    object : Migration(3, 4) {
                        override fun migrate(database: SupportSQLiteDatabase) {
                            database.execSQL(
                                "ALTER TABLE supervisors ADD COLUMN bankName TEXT NOT NULL DEFAULT ''"
                            )
                            database.execSQL(
                                "ALTER TABLE supervisors ADD COLUMN ibanCode TEXT NOT NULL DEFAULT ''"
                            )
                        }
                    }
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
