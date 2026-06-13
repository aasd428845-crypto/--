package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// App Navigation Screens
enum class AppScreen {
    LOGIN,
    CEO_DASHBOARD,
    SUPERVISOR_DASHBOARD,
    SQL_GUIDE
}

// User Roles in Company
enum class UserRole {
    CEO,
    SUPERVISOR
}

// Mock Data Models
data class Institution(
    val id: String,
    val name: String,
    val type: String, // "مستشفى" or "فندق"
    val supervisorName: String,
    val employeesCount: Int,
    val progress: Float,
    val address: String = "المنطقة الوسطى",
    val monthlyContractValue: Double = 15000.0,
    val shiftStartTime: String = "06:00 AM" // Official Shift Start time
)

data class Supervisor(
    val id: String,
    val name: String,
    val assignedLocation: String,
    val phone: String,
    val activeSince: String,
    val submittedAttendanceToday: Boolean,
    val email: String = "",
    val passwordKey: String = "123",
    val address: String = "الجمهورية اليمنية",
    val monthlySalary: Double = 4500.0,
    val username: String = "",
    val bankName: String = "بنك الكريمي الإسلامي",
    val ibanCode: String = ""
)

data class Employee(
    val id: String,
    val name: String,
    val nationality: String,
    val iqamaId: String,
    val phone: String,
    val bankName: String,
    val iban: String,
    val baseSalary: Double,
    val location: String,
    val gender: String = "ذكر",
    val address: String = "سكن الشركة الرئيسي",
    val department: String = "قسم النظافة الأساسية",
    val workDaysScheduled: Int = 26,
    val shift: String = "صباحي",
    val bankAccountOwnerName: String = "",
    val bankAccountPhone: String = "",
    val status: String = "نشط" // "نشط", "مستقيل", "مسرّح"
)

data class AttendanceRecord(
    val employeeId: String,
    val employeeName: String,
    val iqamaId: String,
    var status: String, // "حاضر", "غائب", "إجازة", "متأخر"
    var lateMinutes: Int = 0
)

data class AttendanceRecordSubmission(
    val employeeId: String,
    val employeeName: String,
    val status: String, // "حاضر", "غائب", "متأخر", "إجازة"
    val lateMinutes: Int = 0
)

data class AttendanceSubmission(
    val date: String,
    val dayName: String,
    val institutionName: String,
    val shift: String,
    val records: List<AttendanceRecordSubmission>
)

data class PayrollEntry(
    val employeeId: String,
    val employeeName: String,
    val baseSalary: Double,
    val absentDaysCount: Int,
    val lateDaysCount: Int,
    val totalLateMinutes: Int = 0,
    val absentDeduction: Double = 0.0,
    val lateDeduction: Double = 0.0,
    val totalDeduction: Double,
    val netDue: Double,
    val bankName: String,
    val iban: String,
    val bankAccountOwnerName: String
)

data class NotificationItem(
    val id: String,
    val message: String,
    val roleTarget: String, // "CEO" or "SUPERVISOR"
    val timestamp: String,
    var isRead: Boolean = false
)

data class Invoice(
    val id: String,
    val institutionName: String,
    val description: String,
    val totalAmount: Double,
    val dateTime: String,
    val invoiceImageUrl: String = ""
)

fun parseShiftMinutes(timeStr: String): Int {
    try {
        val clean = timeStr.trim().uppercase()
        val isPM = clean.contains("PM")
        val cleanParts = clean.replace("AM", "").replace("PM", "").trim().split(":")
        if (cleanParts.size >= 2) {
            var hour = cleanParts[0].toIntOrNull() ?: 6
            val min = cleanParts[1].toIntOrNull() ?: 0
            if (isPM && hour < 12) hour += 12
            if (!isPM && hour == 12) hour = 0
            return hour * 60 + min
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 6 * 60 // Default fallback to 06:00 AM
}

// State management for mock database
object MockDatabase {
    var isConnectedToSupabase by mutableStateOf(false)
    var isSyncing by mutableStateOf(false)
    var syncErrorMessage by mutableStateOf<String?>(null)
    var lastSyncTime by mutableStateOf<String?>(null)
    var hasLoadedInitialData by mutableStateOf(false) // Track if first sync completed

    fun isNetworkAvailable(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(
            android.content.Context.CONNECTIVITY_SERVICE
        ) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun getSupabaseCredentials(): Pair<String, String>? {
        val url = try { com.example.BuildConfig.SUPABASE_URL } catch (e: Exception) { "" }
        val key = try { com.example.BuildConfig.SUPABASE_KEY } catch (e: Exception) { "" }
        if (url.startsWith("http")) {
            return Pair(url, key)
        }
        return null
    }

    fun loadFromLocalRoom(context: android.content.Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                val dao = db.nawaemDao()

                val localInsts = dao.getAllInstitutions().map { it.toDomain() }
                val localSups = dao.getAllSupervisors().map { it.toDomain() }
                val localEmps = dao.getAllEmployees().map { it.toDomain() }
                val localNotifs = dao.getAllNotifications().map { it.toDomain() }
                val localAttendance = dao.getAllAttendance()
                val localInvoices = dao.getAllInvoices().map { it.toDomain() }

                // Group local attendance by Date, Institution, and Shift
                val submittedMap = localAttendance.groupBy { Pair(it.date, Pair(it.institutionName, it.shift)) }
                val submissionsList = submittedMap.map { (key, records) ->
                    val date = key.first
                    val instName = key.second.first
                    val shift = key.second.second
                    AttendanceSubmission(
                        date = date,
                        dayName = "اليوم",
                        institutionName = instName,
                        shift = shift,
                        records = records.map {
                            AttendanceRecordSubmission(
                                employeeId = it.employeeId,
                                employeeName = it.employeeName,
                                status = it.status,
                                lateMinutes = it.lateMinutes
                            )
                        }
                    )
                }

                // Map counts and supervisor links dynamically
                val finalInstitutions = localInsts.map { inst ->
                    val workers = localEmps.count { it.location.equals(inst.name, ignoreCase = true) }
                    val supervisor = localSups.find { it.assignedLocation.equals(inst.name, ignoreCase = true) }?.name ?: "غير محدد"
                    inst.copy(
                        employeesCount = workers,
                        supervisorName = supervisor
                    )
                }

                withContext(Dispatchers.Main) {
                    institutions.clear()
                    institutions.addAll(finalInstitutions)

                    supervisors.clear()
                    supervisors.addAll(localSups)

                    employees.clear()
                    employees.addAll(localEmps)

                    notifications.clear()
                    notifications.addAll(localNotifs)

                    attendanceSubmissions.clear()
                    attendanceSubmissions.addAll(submissionsList)

                    invoices.clear()
                    invoices.addAll(localInvoices)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncFromSupabase(context: android.content.Context, forceManual: Boolean = false) {
        // Pre-load from Google Room to make the UI interactive instantly
        loadFromLocalRoom(context)

        val creds = getSupabaseCredentials()
        if (creds == null) {
            isConnectedToSupabase = false
            if (forceManual) {
                Toast.makeText(context, "الرجاء ضبط بيانات الاتصال بـ Supabase في لوحة أسرار AI Studio أولاً! ⚠️", Toast.LENGTH_LONG).show()
            }
            return
        }

        isSyncing = true
          syncErrorMessage = null

          CoroutineScope(Dispatchers.IO).launch {
              try {
                  // Fetch Institutions via Supabase SDK
                  val fetchedInsts = mutableListOf<Institution>()
                  val instArray = JSONArray(supabase.from("institutions").select().data)
                  for (i in 0 until instArray.length()) {
                      val obj = instArray.getJSONObject(i)
                      val id = obj.optString("id", "")
                      val name = obj.optString("name", "")
                      val type = obj.optString("type", "مستشفى")
                      val address = obj.optString("address", "")
                      val monthlyContractValue = obj.optDouble("monthly_contract_value", 15000.0)
                      fetchedInsts.add(
                          Institution(
                              id = id,
                              name = name,
                              type = type,
                              supervisorName = "تحت الإشراف",
                              employeesCount = 0,
                              progress = 0.9f,
                              address = address,
                              monthlyContractValue = monthlyContractValue,
                              shiftStartTime = "06:00 AM"
                          )
                      )
                  }

                  // Fetch Supervisors via Supabase SDK
                  val fetchedSups = mutableListOf<Supervisor>()
                  val supArray = JSONArray(supabase.from("supervisors").select().data)
                  for (i in 0 until supArray.length()) {
                      val obj = supArray.getJSONObject(i)
                      val id = obj.optString("id", "")
                      val fullName = obj.optString("full_name", "")
                      val assignedLocation = obj.optString("assigned_location", "")
                      val phone = obj.optString("phone", "")
                      val email = obj.optString("email", "")
                      val passwordKey = obj.optString("password_key", "123")
                      val address = obj.optString("address", "")
                      val monthlySalary = obj.optDouble("monthly_salary", 5000.0)
                          val bankName = obj.optString("bank_name", "بنك الكريمي الإسلامي")
                          val ibanCode = obj.optString("iban_code", "")
                          fetchedSups.add(
                              Supervisor(
                                  id = id,
                                  name = fullName,
                                  assignedLocation = assignedLocation,
                                  phone = phone,
                                  activeSince = "2024-05-15",
                                  submittedAttendanceToday = false,
                                  email = email,
                                  passwordKey = passwordKey,
                                  address = address,
                                  monthlySalary = monthlySalary,
                                  username = obj.optString("username", email.substringBefore("@")),
                                  bankName = bankName,
                                  ibanCode = ibanCode
                              )
                          )
                  }

                  // Fetch Employees via Supabase SDK
                  val fetchedEmps = mutableListOf<Employee>()
                  val empArray = JSONArray(supabase.from("employees").select().data)
                  for (i in 0 until empArray.length()) {
                      val obj = empArray.getJSONObject(i)
                      val id = obj.optString("id", "")
                      val fullName = obj.optString("full_name", "")
                      val nationality = obj.optString("nationality", "")
                      val iqamaId = obj.optString("iqama_id", "")
                      val phone = obj.optString("phone", "")
                      val bankName = obj.optString("bank_name", "")
                      val iban = obj.optString("iban_code", "")
                      val baseSalary = obj.optDouble("base_salary", 2500.0)
                      val assignedLocation = obj.optString("assigned_location", "")
                      val gender = obj.optString("gender", "ذكر")
                      val address = obj.optString("address", "")
                      val department = obj.optString("department", "")
                      val workDaysScheduled = obj.optInt("work_days_scheduled", 26)
                      val shift = obj.optString("shift", "صباحي")
                      fetchedEmps.add(
                          Employee(
                              id = id,
                              name = fullName,
                              nationality = nationality,
                              iqamaId = iqamaId,
                              phone = phone,
                              bankName = bankName,
                              iban = iban,
                              baseSalary = baseSalary,
                              location = assignedLocation,
                              gender = gender,
                              address = address,
                              department = department,
                              workDaysScheduled = workDaysScheduled,
                              shift = shift,
                              bankAccountOwnerName = fullName,
                              bankAccountPhone = phone
                          )
                      )
                  }

                  // Fetch Invoices via Supabase SDK
                  val fetchedInvoices = mutableListOf<Invoice>()
                  try {
                      val invArray = JSONArray(supabase.from("invoices").select().data)
                      for (i in 0 until invArray.length()) {
                          val obj = invArray.getJSONObject(i)
                          fetchedInvoices.add(
                              Invoice(
                                  id = obj.optString("id", ""),
                                  institutionName = obj.optString("institution_name", ""),
                                  description = obj.optString("description", ""),
                                  totalAmount = obj.optDouble("total_amount", 0.0),
                                  dateTime = obj.optString("date_time", ""),
                                  invoiceImageUrl = obj.optString("invoice_image_url", "")
                              )
                          )
                      }
                  } catch (e: Exception) {
                      e.printStackTrace()
                  }

                                  val finalInstitutions = fetchedInsts.map { inst ->
                    val workers = fetchedEmps.count { it.location.equals(inst.name, ignoreCase = true) }
                    val supervisor = fetchedSups.find { it.assignedLocation.equals(inst.name, ignoreCase = true) }?.name ?: "غير محدد"
                    inst.copy(
                        employeesCount = workers,
                        supervisorName = supervisor
                    )
                }

                // Write downloaded state safely in direct cache tables in Google's Room database on the device:
                try {
                    val db = NawaemRoomDatabase.getDatabase(context)
                    val dao = db.nawaemDao()

                    // Do NOT clear tables! Just insert/upsert the synced records.
                    if (fetchedInsts.isNotEmpty()) {
                        dao.insertInstitutions(fetchedInsts.map { it.toRoom() })
                    }
                    if (fetchedSups.isNotEmpty()) {
                        dao.insertSupervisors(fetchedSups.map { it.toRoom() })
                    }
                    if (fetchedEmps.isNotEmpty()) {
                        dao.insertEmployees(fetchedEmps.map { it.toRoom() })
                    }
                    if (fetchedInvoices.isNotEmpty()) {
                        dao.insertInvoices(fetchedInvoices.map { it.toRoom() })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load everything from local Room so that we display local records as primary reference
                try {
                    val db = NawaemRoomDatabase.getDatabase(context)
                    val dao = db.nawaemDao()

                    val localInsts = dao.getAllInstitutions().map { it.toDomain() }
                    val localSups = dao.getAllSupervisors().map { it.toDomain() }
                    val localEmps = dao.getAllEmployees().map { it.toDomain() }
                    val localNotifs = dao.getAllNotifications().map { it.toDomain() }
                    val localAttendance = dao.getAllAttendance()
                    val localInvoices = dao.getAllInvoices().map { it.toDomain() }

                    // Group local attendance by Date, Institution, and Shift
                    val submittedMap = localAttendance.groupBy { Pair(it.date, Pair(it.institutionName, it.shift)) }
                    val submissionsList = submittedMap.map { (key, records) ->
                        val date = key.first
                        val instName = key.second.first
                        val shift = key.second.second
                        AttendanceSubmission(
                            date = date,
                            dayName = "اليوم",
                            institutionName = instName,
                            shift = shift,
                            records = records.map {
                                AttendanceRecordSubmission(
                                    employeeId = it.employeeId,
                                    employeeName = it.employeeName,
                                    status = it.status,
                                    lateMinutes = it.lateMinutes
                                )
                            }
                        )
                    }

                    // Map counts and supervisor links dynamically
                    val finalLocalInstitutions = localInsts.map { inst ->
                        val workers = localEmps.count { it.location.equals(inst.name, ignoreCase = true) }
                        val supervisor = localSups.find { it.assignedLocation.equals(inst.name, ignoreCase = true) }?.name ?: "غير محدد"
                        inst.copy(
                            employeesCount = workers,
                            supervisorName = supervisor
                        )
                    }

                    withContext(Dispatchers.Main) {
                        institutions.clear()
                        institutions.addAll(finalLocalInstitutions)

                        supervisors.clear()
                        supervisors.addAll(localSups)

                        employees.clear()
                        employees.addAll(localEmps)

                        notifications.clear()
                        notifications.addAll(localNotifs)

                        attendanceSubmissions.clear()
                        attendanceSubmissions.addAll(submissionsList)

                        invoices.clear()
                        invoices.addAll(localInvoices)

                        isConnectedToSupabase = true
                        isSyncing = false
                        hasLoadedInitialData = true
                        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        lastSyncTime = sdf.format(java.util.Calendar.getInstance().time)

                        Toast.makeText(context, "تم مطابقة ومزامنة البيانات مع السيرفر وتحديث قاعدة بيانات Google Room بنجاح!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isSyncing = false
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // Fetch failed or offline, load from local Room
                loadFromLocalRoom(context)
                withContext(Dispatchers.Main) {
                    isSyncing = false
                    isConnectedToSupabase = false
                    hasLoadedInitialData = true // Still mark as loaded for offline mode
                    syncErrorMessage = e.message
                    Toast.makeText(context, "وضع غير متصل - تعذر جلب البيانات سحابياً. يُستخدم التخزين المحلي.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun authenticateSupervisorFromSupabase(context: android.content.Context, username: String, password: String): Supervisor? {
        return withContext(Dispatchers.IO) {
            try {
                val creds = getSupabaseCredentials()
                if (creds == null) return@withContext null

                // First try by username field (if it exists in the table)
                val supArray = JSONArray(supabase.from("supervisors").select().data)
                for (i in 0 until supArray.length()) {
                    val obj = supArray.getJSONObject(i)
                    val email = obj.optString("email", "")
                    val passwordKey = obj.optString("password_key", "")
                    val fullName = obj.optString("full_name", "")
                    val usernameFromEmail = email.substringBefore("@")

                    // Match by email OR derived username
                    if ((email.equals(username, ignoreCase = true) || usernameFromEmail.equals(username, ignoreCase = true)) && passwordKey == password) {
                        val supervisor = Supervisor(
                            id = obj.optString("id", ""),
                            name = fullName,
                            assignedLocation = obj.optString("assigned_location", ""),
                            phone = obj.optString("phone", ""),
                            activeSince = "2024-05-15",
                            submittedAttendanceToday = false,
                            email = email,
                            passwordKey = passwordKey,
                            address = obj.optString("address", ""),
                            monthlySalary = obj.optDouble("monthly_salary", 5000.0),
                            username = usernameFromEmail,
                            bankName = obj.optString("bank_name", "بنك الكريمي الإسلامي"),
                            ibanCode = obj.optString("iban_code", "")
                        )

                        // Save to local Room for future offline login
                        try {
                            val db = NawaemRoomDatabase.getDatabase(context)
                            db.nawaemDao().insertSupervisor(supervisor.toRoom())
                            // Also add to in-memory list
                            withContext(Dispatchers.Main) {
                                if (!supervisors.any { it.id == supervisor.id }) {
                                    supervisors.add(supervisor)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }

                        return@withContext supervisor
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun postInstitutionToSupabase(context: android.content.Context, institution: Institution) {
        if (!isNetworkAvailable(context)) {
            android.widget.Toast.makeText(
                context,
                "⚠️ لا يوجد اتصال بالإنترنت. يجب الاتصال لحفظ البيانات.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Write to Room FIRST — ensures data survives even if Supabase fails
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().insertInstitution(institution.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("institutions").upsert(
                      buildJsonObject {
                          put("id", institution.id)
                          put("name", institution.name)
                          put("type", institution.type)
                          put("address", institution.address)
                          put("monthly_contract_value", institution.monthlyContractValue)
                          put("shift_start_time", institution.shiftStartTime)
                      }
                  )
              } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun postSupervisorToSupabase(context: android.content.Context, supervisor: Supervisor) {
        if (!isNetworkAvailable(context)) {
            android.widget.Toast.makeText(
                context,
                "⚠️ لا يوجد اتصال بالإنترنت. يجب الاتصال لحفظ البيانات.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Write to Room first for zero latency offline usage
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().insertSupervisor(supervisor.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("supervisors").upsert(
                      buildJsonObject {
                          put("id", supervisor.id)
                          put("full_name", supervisor.name)
                          put("email", supervisor.email)
                          put("password_key", supervisor.passwordKey)
                          put("phone", supervisor.phone)
                          put("address", supervisor.address)
                          put("monthly_salary", supervisor.monthlySalary)
                          put("assigned_location", supervisor.assignedLocation)
                          put("role", "SUPERVISOR")
                          put("bank_name", supervisor.bankName)
                          put("iban_code", supervisor.ibanCode)
                      }
                  )
              } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun postEmployeeToSupabase(context: android.content.Context, employee: Employee) {
        if (!isNetworkAvailable(context)) {
            android.widget.Toast.makeText(
                context,
                "⚠️ لا يوجد اتصال بالإنترنت. يجب الاتصال لحفظ البيانات.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Write to Room FIRST — ensures data survives even if Supabase fails
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().insertEmployee(employee.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("employees").upsert(
                      buildJsonObject {
                          put("id", employee.id)
                          put("full_name", employee.name)
                          put("gender", employee.gender)
                          put("nationality", employee.nationality)
                          put("iqama_id", employee.iqamaId)
                          put("phone", employee.phone)
                          put("address", employee.address)
                          put("assigned_location", employee.location)
                          put("department", employee.department)
                          put("work_days_scheduled", employee.workDaysScheduled)
                          put("shift", employee.shift)
                          put("base_salary", employee.baseSalary)
                          put("bank_name", employee.bankName)
                          put("iban_code", employee.iban)
                          put("bank_account_owner_name", employee.bankAccountOwnerName.ifBlank { employee.name })
                          put("bank_account_phone", employee.bankAccountPhone.ifBlank { employee.phone })
                      }
                  )
              } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteInstitution(context: android.content.Context, institution: Institution) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().deleteInstitution(institution.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("institutions").delete {
                      filter { eq("name", institution.name) }
                  }
              } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                institutions.remove(institution)
            }
        }
    }

    fun deleteSupervisor(context: android.content.Context, supervisor: Supervisor) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().deleteSupervisor(supervisor.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("supervisors").delete {
                      filter { eq("email", supervisor.email) }
                  }
              } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                supervisors.remove(supervisor)
            }
        }
    }

    fun deleteEmployee(context: android.content.Context, employee: Employee) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().deleteEmployee(employee.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("employees").delete {
                      filter { eq("iqama_id", employee.iqamaId) }
                  }
              } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                employees.remove(employee)
            }
        }
    }

    fun postAttendanceToSupabase(context: android.content.Context, submission: AttendanceSubmission) {
        if (!isNetworkAvailable(context)) {
            android.widget.Toast.makeText(
                context,
                "⚠️ لا يوجد اتصال بالإنترنت. يجب الاتصال لحفظ البيانات.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            // Write to Room first for zero latency offline usage
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                val recordsToInsert = submission.records.map { rec ->
                    RoomAttendanceRecord(
                        date = submission.date,
                        employeeId = rec.employeeId,
                        employeeName = rec.employeeName,
                        status = rec.status,
                        lateMinutes = rec.lateMinutes,
                        institutionName = submission.institutionName,
                        shift = submission.shift
                    )
                }
                db.nawaemDao().insertAttendance(recordsToInsert)
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  for (rec in submission.records) {
                      supabase.from("attendance").insert(
                          buildJsonObject {
                              put("attendance_date", submission.date)
                              put("employee_id", rec.employeeId)
                              put("status", rec.status)
                              put("recorded_by", submission.institutionName + " - " + submission.shift)
                              put("notes", "تحضير عبر التطبيق")
                          }
                      )
                  }
              } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun postInvoiceToSupabase(context: android.content.Context, invoice: Invoice) {
        CoroutineScope(Dispatchers.IO).launch {
            // Write to Room first for zero latency offline usage
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().insertInvoice(invoice.toRoom())
            } catch (e: Exception) { e.printStackTrace() }

              try {
                  supabase.from("invoices").upsert(
                      buildJsonObject {
                          put("id", invoice.id)
                          put("institution_name", invoice.institutionName)
                          put("description", invoice.description)
                          put("total_amount", invoice.totalAmount)
                          put("date_time", invoice.dateTime)
                          put("invoice_image_url", invoice.invoiceImageUrl)
                      }
                  )
              } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val notifications = mutableStateListOf<NotificationItem>()

    fun triggerNotification(context: android.content.Context, message: String, roleTarget: String) {
        val sdf = java.text.SimpleDateFormat("hh:mm a - yyyy-MM-dd", java.util.Locale.getDefault())
        val nowStr = sdf.format(java.util.Calendar.getInstance().time)
        val newNotif = NotificationItem(
            id = "notif_" + System.currentTimeMillis() + "_" + (10..99).random(),
            message = message,
            roleTarget = roleTarget,
            timestamp = nowStr,
            isRead = false
        )
        notifications.add(0, newNotif)

        // Save to Room Database locally
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = NawaemRoomDatabase.getDatabase(context)
                db.nawaemDao().insertNotification(newNotif.toRoom())
            } catch (e: Exception) { e.printStackTrace() }
        }

        try {
            LocalNotificationHelper.showNotification(
                context = context,
                title = if (roleTarget == "CEO") "📢 إشعار إداري فوري (المدير)" else "⏰ تنبيه هام للمشرف - شركة نواعم",
                message = message
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val institutions = mutableStateListOf<Institution>()

    val supervisors = mutableStateListOf<Supervisor>()

    val employees = mutableStateListOf<Employee>()

    // Payroll and Submission tracking states
    val institutionDebts = mutableStateMapOf<String, Double>()
    val employeePayments = mutableStateMapOf<String, String>() // Key: "employeeId_month", Value: txRef

    val attendanceSubmissions = mutableStateListOf<AttendanceSubmission>()

    val invoices = mutableStateListOf<Invoice>()
}

// Dialog to display role-targeted notifications
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterDialog(
    role: String, // "CEO" or "SUPERVISOR"
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val allNotifications = MockDatabase.notifications
    val filteredNotifs = allNotifications.filter { it.roleTarget.equals(role, ignoreCase = true) }
    
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                    
                    Text(
                        text = "مركز الإشعارات الموحد 🔔",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Right
                    )
                }
                
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                
                // Actions: Mark All as Read
                if (filteredNotifs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                allNotifications.forEach {
                                    if (it.roleTarget.equals(role, ignoreCase = true)) {
                                        it.isRead = true
                                    }
                                }
                                val temp = allNotifications.toList()
                                allNotifications.clear()
                                allNotifications.addAll(temp)
                                Toast.makeText(context, "تم تحديد جميع الإشعارات كمقروءة", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "تحديد الكل كمقروء ✔️",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = "لديك (${filteredNotifs.count { !it.isRead }}) إشعارات جديدة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                // Notifications List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredNotifs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🔔", fontSize = 36.sp)
                                Text(
                                    text = "لا توجد إشعارات واردة حتى الآن.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        items(filteredNotifs, key = { it.id }) { notif ->
                            val bgCardColor = if (notif.isRead) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            }
                            
                            val outlineBorderColor = if (notif.isRead) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val index = allNotifications.indexOfFirst { it.id == notif.id }
                                    if (index != -1) {
                                        allNotifications[index] = allNotifications[index].copy(isRead = true)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgCardColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, outlineBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = notif.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        
                                        if (!notif.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Professional Logo Component - NOAEM Cleaning Services
@Composable
fun NawaemLogo(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.logo),
        contentDescription = "شعار شركة نواعم",
        modifier = modifier.size(size),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit
    )
}

// أيقونة مصغرة للشاشات الأخرى والتطبيق
@Composable
fun NawaemIcon(size: Dp = 48.dp) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00ACC1).copy(alpha = 0.95f),
                        Color(0xFF0097A7)
                    )
                )
            )
    ) {
        val centerX = size.toPx() / 2
        val centerY = size.toPx() / 2

        // Small bucket
        drawRoundRect(
            color = Color(0xFF0F2849),
            topLeft = Offset(centerX - 12.dp.toPx(), centerY - 10.dp.toPx()),
            size = Size(20.dp.toPx(), 20.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Small broom stick
        drawLine(
            color = Color(0xFF1B5E20),
            start = Offset(centerX + 10.dp.toPx(), centerY - 5.dp.toPx()),
            end = Offset(centerX + 10.dp.toPx(), centerY + 15.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

// 1. Unified Login Screen
@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole, userName: String) -> Unit,
    onNavigateToSql: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // Pre-load from Google Room DB instantly so cached supervisors can log in offline
        MockDatabase.loadFromLocalRoom(context)
        // Silently sync from Supabase if not done yet
        if (!MockDatabase.isConnectedToSupabase && !MockDatabase.isSyncing) {
            MockDatabase.syncFromSupabase(context)
        }
    }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Integrated Official Brand Logo
            NawaemLogo(size = 180.dp)

            Text(
                text = "شركة نواعم",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            )

            Text(
                text = "مقاولات تشغيل ونظافة المستشفيات والفنادق",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main login card container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "تسجيل الدخول للنظام الموحد",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.align(Alignment.End)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = ""
                        },
                        label = { Text("اسم المستخدم أو البريد الإلكتروني", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = ""
                        },
                        label = { Text("كلمة المرور", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "أظهر كلمة المرور"
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left)
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.End),
                            textAlign = TextAlign.Right
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedUser = username.trim()
                            val trimmedPass = password.trim()

                            // Check CEO accounts first (including requested maher account)
                            val isCeo = (trimmedUser.lowercase() == "maher714995700@gmail.com" && trimmedPass == "772877595") ||
                                        (trimmedUser.lowercase() == "maher" && trimmedPass == "772877595") ||
                                        (trimmedUser.lowercase() == "ceo@nawaem.com" && trimmedPass == "123") ||
                                        (trimmedUser.lowercase() == "ceo" && trimmedPass == "123")

                            if (isCeo) {
                                onLoginSuccess(UserRole.CEO, "الأستاذ ماهر محمد")
                            } else {
                                // Step 1: Lookup in local Room (via MockDatabase memory cache)
                                val foundSupervisor = MockDatabase.supervisors.find {
                                    (it.username.equals(trimmedUser, ignoreCase = true) ||
                                     it.email.equals(trimmedUser, ignoreCase = true)) &&
                                    it.passwordKey == trimmedPass
                                }

                                if (foundSupervisor != null) {
                                    onLoginSuccess(UserRole.SUPERVISOR, foundSupervisor.name)
                                } else if (trimmedUser.lowercase() == "supervisor" && trimmedPass == "123") {
                                    onLoginSuccess(UserRole.SUPERVISOR, "المشرف الميداني")
                                } else {
                                    // Step 2: Not found in Room, try Supabase directly
                                    isLoggingIn = true
                                    errorMessage = "جاري التحقق من السيرفر..."
                                    scope.launch {
                                        val supabaseSupervisor = MockDatabase.authenticateSupervisorFromSupabase(context, trimmedUser, trimmedPass)
                                        isLoggingIn = false
                                        if (supabaseSupervisor != null) {
                                            // Step: Found in Supabase, saved to Room, now login
                                            onLoginSuccess(UserRole.SUPERVISOR, supabaseSupervisor.name)
                                        } else {
                                            // Step 3: Not found anywhere
                                            errorMessage = "عذراً! اسم المستخدم أو كلمة المرور غير صحيحة. الرجاء إعادة المحاولة."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "تسجيل الدخول للمنصّة",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }
            }
        }

        // Floating action guide button removed for production-grade clean look
    }
}

// 2. CEO Dashboard Screen
@Composable
fun CeoDashboardScreen(
    currentUserName: String,
    onLogout: () -> Unit,
    onNavigateToSql: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (!MockDatabase.isConnectedToSupabase && !MockDatabase.isSyncing) {
            MockDatabase.syncFromSupabase(context)
        }

        while (true) {
            kotlinx.coroutines.delay(30000)
            try {
                MockDatabase.syncFromSupabase(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Show loading screen while initial sync is in progress and data is empty
    if (MockDatabase.isSyncing && !MockDatabase.hasLoadedInitialData) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "جاري تحميل البيانات من السيرفر...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "الرجاء الانتظار",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Institutions, 1: Supervisors, 2: Employees, 3: Payroll
    var searchQuery by remember { mutableStateOf("") }

    // New state variables for CEO Payroll Tab
    var selectedPayrollInstitution by remember { mutableStateOf(MockDatabase.institutions.firstOrNull()?.name ?: "") }
    var selectedPayrollMonth by remember { mutableStateOf("2026-05") }
    var institutionPaymentType by remember { mutableStateOf("كامل") } // "كامل", "نصف", "ربع", "لم يدفع"
    
    // Calculated report list
    val calculatedReport = remember { mutableStateListOf<PayrollEntry>() }
    
    // To support "Pay this employee" window dialog
    var selectedEmployeeForPayment by remember { mutableStateOf<PayrollEntry?>(null) }
    var transferNumberInput by remember { mutableStateOf("") }
    var simulatedReceiptAttached by remember { mutableStateOf(false) }

    // Dialog trigger states
    var showAddInstitutionDialog by remember { mutableStateOf(false) }
    var showAddSupervisorDialog by remember { mutableStateOf(false) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Dialog implementations
    if (showNotificationsDialog) {
        NotificationCenterDialog(role = "CEO", onDismissRequest = { showNotificationsDialog = false })
    }

    if (showAddInstitutionDialog) {
        Dialog(onDismissRequest = { showAddInstitutionDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                var name by remember { mutableStateOf("") }
                var type by remember { mutableStateOf("مستشفى") } // "مستشفى" or "فندق"
                var address by remember { mutableStateOf("") }
                var monthlyValue by remember { mutableStateOf("") }
                var shiftStartTime by remember { mutableStateOf("06:00 AM") }
                var errorMsg by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "🏢 إضافة مؤسسة وعقد تشغيل جديد",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider(color = Color(0xFFF1F5F9))

                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المؤسسة/العقد", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: مستشفى دله فرع النخيل", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Type Segmented selection
                    Text("تصنيف جهة التعاقد:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { type = "فندق" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (type == "فندق") MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9),
                                contentColor = if (type == "فندق") Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Hotel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فندق")
                        }

                        Button(
                            onClick = { type = "مستشفى" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (type == "مستشفى") MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9),
                                contentColor = if (type == "مستشفى") Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مستشفى")
                        }
                    }

                    // Address
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("عنوان المؤسسة بالتفصيل", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: الرياض، حي السليمانية، طريق الملك عبدالعزيز", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Monthly due value
                    OutlinedTextField(
                        value = monthlyValue,
                        onValueChange = { monthlyValue = it },
                        label = { Text("المبلغ المستحق شهرياً كقيمة عقد للشركة (ريال)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: 45000", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Shift Official Start Time
                    OutlinedTextField(
                        value = shiftStartTime,
                        onValueChange = { shiftStartTime = it },
                        label = { Text("وقت بداية الوردية الرسمي", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: 06:00 AM", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddInstitutionDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء")
                        }

                        Button(
                            onClick = {
                                if (name.isBlank() || address.isBlank() || monthlyValue.isBlank() || shiftStartTime.isBlank()) {
                                    errorMsg = "الرجاء تعبئة جميع الحقول المطلوبة!"
                                } else {
                                    val value = monthlyValue.toDoubleOrNull() ?: 10000.0
                                    val newInst = Institution(
                                        id = "inst_${System.currentTimeMillis()}_${(1000..9999).random()}",
                                        name = name.trim(),
                                        type = type,
                                        supervisorName = "لم يعين",
                                        employeesCount = 0,
                                        progress = 0.5f,
                                        address = address.trim(),
                                        monthlyContractValue = value,
                                        shiftStartTime = shiftStartTime.trim()
                                    )
                                    MockDatabase.institutions.add(newInst)
                                    MockDatabase.postInstitutionToSupabase(context, newInst)
                                    showAddInstitutionDialog = false
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ وعقد جديد")
                        }
                    }
                }
            }
        }
    }

    if (showAddSupervisorDialog) {
        Dialog(onDismissRequest = { showAddSupervisorDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                var name by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var passwordText by remember { mutableStateOf("") }
                var phone by remember { mutableStateOf("") }
                var address by remember { mutableStateOf("") }
                var monthlySalary by remember { mutableStateOf("") }
                var bankName by remember { mutableStateOf("بنك الكريمي الإسلامي") }
                var ibanCode by remember { mutableStateOf("") }
                var selectedLocName by remember { mutableStateOf("") }
                var errorMsg by remember { mutableStateOf("") }
                var dropdownExpanded by remember { mutableStateOf(false) }

                if (selectedLocName.isEmpty() && MockDatabase.institutions.isNotEmpty()) {
                    selectedLocName = MockDatabase.institutions[0].name
                }

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "👮‍♂️ تسجيل وتعيين مشرف ميداني جديد",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider(color = Color(0xFFF1F5F9))

                    // Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم المشرف بالكامل", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Email for login
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني (لتسجيل الدخول)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("example@nawaem.com", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left)
                    )

                    // Password
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        label = { Text("كلمة سر الحساب الجديد", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left)
                    )

                    // Phone
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف المعتمد", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("05xxxxxxxx", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Address
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("عنوان المشرف والحي", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Monthly Salary
                    OutlinedTextField(
                        value = monthlySalary,
                        onValueChange = { monthlySalary = it },
                        label = { Text("الراتب الشهري للمشرف (ريال)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Bank Name
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("اسم البنك", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // IBAN Code
                    OutlinedTextField(
                        value = ibanCode,
                        onValueChange = { ibanCode = it },
                        label = { Text("رقم الحساب البنكي (IBAN)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left)
                    )

                    // Linked Contract institution (Dropdown)
                    Text("ربط المشرف بمؤسسة ومقر العمل للتحضير:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                Text(
                                    text = if (selectedLocName.isEmpty()) "إختر مؤسسة للربط" else selectedLocName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            MockDatabase.institutions.forEach { inst ->
                                DropdownMenuItem(
                                    text = { Text(inst.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                    onClick = {
                                        selectedLocName = inst.name
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddSupervisorDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء")
                        }

                        Button(
                            onClick = {
                                if (name.isBlank() || email.isBlank() || passwordText.isBlank() || phone.isBlank()) {
                                    errorMsg = "الرجاء تعبئة جميع الحقول المطلوبة!"
                                } else if (selectedLocName.isBlank()) {
                                    errorMsg = "الرجاء إضافة مؤسسة واحدة وتنسيقها أولاً من تبويب المؤسسات!"
                                } else {
                                    val rSalary = monthlySalary.toDoubleOrNull() ?: 4500.0
                                    val newId = "sup_${System.currentTimeMillis()}_${(1000..9999).random()}"
                                    val newSup = Supervisor(
                                        id = newId,
                                        name = name.trim(),
                                        assignedLocation = selectedLocName,
                                        phone = phone.trim(),
                                        activeSince = "2026-05-27",
                                        submittedAttendanceToday = false,
                                        email = email.trim(),
                                        passwordKey = passwordText.trim(),
                                        address = address.trim(),
                                        monthlySalary = rSalary,
                                        bankName = bankName.trim(),
                                        ibanCode = ibanCode.trim()
                                    )
                                    MockDatabase.supervisors.add(newSup)
                                    MockDatabase.postSupervisorToSupabase(context, newSup)
                                    // Also update institution responsible Supervisor Name
                                    val idx = MockDatabase.institutions.indexOfFirst { it.name == selectedLocName }
                                    if (idx != -1) {
                                        val cur = MockDatabase.institutions[idx]
                                        MockDatabase.institutions[idx] = cur.copy(supervisorName = name)
                                    }
                                    showAddSupervisorDialog = false
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ وتعيين")
                        }
                    }
                }
            }
        }
    }

    if (showAddEmployeeDialog) {
        Dialog(onDismissRequest = { showAddEmployeeDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                var empName by remember { mutableStateOf("") }
                var gender by remember { mutableStateOf("ذكر") } // "ذكر" or "أنثى"
                var phone by remember { mutableStateOf("") }
                var address by remember { mutableStateOf("") }
                var employeeLoc by remember { mutableStateOf("") }
                var nationality by remember { mutableStateOf("يمني") }
                var iqamaId by remember { mutableStateOf("") }

                // Department / Hotel reactive info
                var hospitalDept by remember { mutableStateOf("قسم الولادة والأطفال") }
                var hotelRoomCount by remember { mutableStateOf("25 غرفة") }

                // Duty
                var scheduledDays by remember { mutableStateOf("26") }
                var shiftPeriod by remember { mutableStateOf("صباحي") } // "صباحي" or "مسائي"
                var baseSalaryStr by remember { mutableStateOf("") }

                // Bank details
                var bankOwnerName by remember { mutableStateOf("") }
                var bankName by remember { mutableStateOf("بنك الكريمي الإسلامي") }
                var ibanStr by remember { mutableStateOf("") }
                var bankPhone by remember { mutableStateOf("") }

                var errorMsg by remember { mutableStateOf("") }
                var dropdownExpanded by remember { mutableStateOf(false) }

                if (employeeLoc.isEmpty() && MockDatabase.institutions.isNotEmpty()) {
                    employeeLoc = MockDatabase.institutions[0].name
                }

                val curInst = MockDatabase.institutions.find { it.name == employeeLoc }
                val isHospital = curInst?.type == "مستشفى"

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "🚀 موديول تسجيل العمالة الذكية",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "قم بتعبئة البيانات الشخصية وتفاصيل الاستحقاق مع ميزة التنسيق البنكي الذكي المتزامن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Right
                    )

                    Divider(color = Color(0xFFF1F5F9))

                    // Name & Gender
                    OutlinedTextField(
                        value = empName,
                        onValueChange = { empName = it },
                        label = { Text("الاسم الكامل للموظف", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gender layout
                        Button(
                            onClick = { gender = "أنثى" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (gender == "أنثى") MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9),
                                contentColor = if (gender == "أنثى") Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("أنثى 👩") }

                        Button(
                            onClick = { gender = "ذكر" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (gender == "ذكر") MaterialTheme.colorScheme.primary else Color(0xFFF1F5F9),
                                contentColor = if (gender == "ذكر") Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("ذكر 👨") }

                        Text("الجنس:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    }

                    // Phone & Iqama
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف الشخصي", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    OutlinedTextField(
                        value = iqamaId,
                        onValueChange = { iqamaId = it },
                        label = { Text("رقم هوية المقيم / الإقامة", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: 24xxxxxxxx", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Address & Nationality
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان السكني للموظف", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    OutlinedTextField(
                        value = nationality,
                        onValueChange = { nationality = it },
                        label = { Text("جنسية العامل", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Destination / Assigned Location dropdown
                    Text("جهة التعيين وموقع العمل ومقر الصرف:", fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                Text(employeeLoc, fontWeight = FontWeight.Bold)
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            MockDatabase.institutions.forEach { inst ->
                                DropdownMenuItem(
                                    text = { Text(inst.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                    onClick = {
                                        employeeLoc = inst.name
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // HOSPITAL OR HOTEL DYNAMIC FIELDS INSTRUCTION
                    if (isHospital) {
                        Text("🔬 طبيعة عمل المستشفيات (مزايا حيوية):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        var deptExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { deptExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    Text(hospitalDept, fontWeight = FontWeight.Bold)
                                }
                            }

                            DropdownMenu(expanded = deptExpanded, onDismissRequest = { deptExpanded = false }) {
                                listOf("قسم الولادة والأطفال", "قسم الترقيد والتنويم", "قسم الطوارئ والحوادث", "قسم التعقيم المركزي", "العيادات الخارجية").forEach { dep ->
                                    DropdownMenuItem(
                                        text = { Text(dep) },
                                        onClick = {
                                            hospitalDept = dep
                                            deptExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text("🏨 توزيع المهام للفنادق والمرافق السياحية:", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        OutlinedTextField(
                            value = hotelRoomCount,
                            onValueChange = { hotelRoomCount = it },
                            label = { Text("عدد الغرف والمرفق المسؤول عنه الفندقي", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                            placeholder = { Text("مثال: 30 غرفة بالدور الثالث", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )
                    }

                    // Shift details (scheduled work days, shift period)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = scheduledDays,
                            onValueChange = { scheduledDays = it },
                            label = { Text("أيام العمل بالشهر", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                        )

                        // Shift period toggle
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("فترة الدوام:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { shiftPeriod = "مسائي" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (shiftPeriod == "مسائي") MaterialTheme.colorScheme.secondary else Color(0xFFF1F5F9),
                                        contentColor = if (shiftPeriod == "مسائي") Color.White else Color(0xFF475569)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) { Text("مسائي", fontSize = 11.sp) }

                                Button(
                                    onClick = { shiftPeriod = "صباحي" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (shiftPeriod == "صباحي") MaterialTheme.colorScheme.secondary else Color(0xFFF1F5F9),
                                        contentColor = if (shiftPeriod == "صباحي") Color.White else Color(0xFF475569)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) { Text("صباحي", fontSize = 11.sp) }
                            }
                        }
                    }

                    // Financial base salary
                    OutlinedTextField(
                        value = baseSalaryStr,
                        onValueChange = { baseSalaryStr = it },
                        label = { Text("المستحق المالي الأساسي (ريال)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Banking Account Information header
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Smart autofill button trigger!
                                Button(
                                    onClick = {
                                        // Auto-populate owner name and bank phone
                                        bankOwnerName = empName
                                        bankPhone = phone
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تعبئة تلقائية ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Text("💳 بيانات الحساب البنكي وصرف الراتب:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }

                            OutlinedTextField(
                                value = bankOwnerName,
                                onValueChange = { bankOwnerName = it },
                                label = { Text("اسم صاحب الحساب البنكي المعتمد", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                            )

                            // Select Bank Name
                            var bankDropdownExpanded by remember { mutableStateOf(false) }
                            var isCustomBank by remember { mutableStateOf(false) }
                            var customBankName by remember { mutableStateOf("") }
                            Text("البنك المعتمد تحويل الرواتب (اليمن):", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { bankDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        Text(if (isCustomBank) "كتابة يدوية أخرى (إدخال يدوي)" else bankName, fontWeight = FontWeight.Bold)
                                    }
                                }

                                DropdownMenu(expanded = bankDropdownExpanded, onDismissRequest = { bankDropdownExpanded = false }) {
                                    listOf(
                                        "بنك الكريمي الإسلامي",
                                        "بنك العمقي والشركاء",
                                        "بنك التضامن",
                                        "بنك اليمن والكويت",
                                        "بنك اليمن الدولي",
                                        "مصرف البحرين الشامل",
                                        "كتابة يدوية أخرى (إدخال يدوي)"
                                    ).forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b) },
                                            onClick = {
                                                if (b == "كتابة يدوية أخرى (إدخال يدوي)") {
                                                    isCustomBank = true
                                                    bankName = ""
                                                } else {
                                                    isCustomBank = false
                                                    bankName = b
                                                }
                                                bankDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (isCustomBank) {
                                OutlinedTextField(
                                    value = customBankName,
                                    onValueChange = {
                                        customBankName = it
                                        bankName = it
                                    },
                                    label = { Text("اكتب اسم البنك / المحفظة يدوياً", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                                )
                            }

                            OutlinedTextField(
                                value = ibanStr,
                                onValueChange = { ibanStr = it },
                                label = { Text("رقم الحساب البنكي / المحفظة المالية", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left)
                            )

                            OutlinedTextField(
                                value = bankPhone,
                                onValueChange = { bankPhone = it },
                                label = { Text("رقم جوال الحساب البنكي لإشعارات الصرف", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                            )
                        }
                    }

                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddEmployeeDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("إلغاء") }

                        Button(
                            onClick = {
                                if (empName.isBlank() || iqamaId.isBlank() || baseSalaryStr.isBlank() || ibanStr.isBlank()) {
                                    errorMsg = "الرجاء تعبئة الاسم والإقامة والراتب والآيبان لتسجيل الموظف بنجاح!"
                                } else if (employeeLoc.isBlank()) {
                                    errorMsg = "الرجاء اختيار موقع العمل أو إضافة منشأة أولاً لربط الموظف بها!"
                                } else {
                                    val bSalary = baseSalaryStr.toDoubleOrNull() ?: 2500.0
                                    val days = scheduledDays.toIntOrNull() ?: 26
                                    val deptInfo = if (isHospital) hospitalDept else hotelRoomCount

                                    val newEmp = Employee(
                                        id = "emp_${System.currentTimeMillis()}_${(1000..9999).random()}",
                                        name = empName.trim(),
                                        nationality = nationality.trim(),
                                        iqamaId = iqamaId.trim(),
                                        phone = phone.trim(),
                                        bankName = bankName,
                                        iban = ibanStr.trim(),
                                        baseSalary = bSalary,
                                        location = employeeLoc,
                                        gender = gender,
                                        address = address.trim(),
                                        department = deptInfo,
                                        workDaysScheduled = days,
                                        shift = shiftPeriod,
                                        bankAccountOwnerName = bankOwnerName.trim().ifEmpty { empName },
                                        bankAccountPhone = bankPhone.trim().ifEmpty { phone }
                                    )

                                    MockDatabase.employees.add(newEmp)
                                    MockDatabase.postEmployeeToSupabase(context, newEmp)

                                    // Update institution employee count automatically
                                    val instIdx = MockDatabase.institutions.indexOfFirst { it.name == employeeLoc }
                                    if (instIdx != -1) {
                                        val cur = MockDatabase.institutions[instIdx]
                                        MockDatabase.institutions[instIdx] = cur.copy(employeesCount = cur.employeesCount + 1)
                                    }

                                    showAddEmployeeDialog = false
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("تسجيل وتعيين") }
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {},
        topBar = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onLogout,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "خروج", tint = Color.White)
                            }

                            // Notification Bell icon with unread indicator badge
                            val unreadCeoCount = MockDatabase.notifications.count { it.roleTarget == "CEO" && !it.isRead }
                            IconButton(
                                onClick = { showNotificationsDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (unreadCeoCount > 0) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.size(40.dp).testTag("ceo_notif_bell")
                            ) {
                                Box(modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "الإشعارات",
                                        tint = if (unreadCeoCount > 0) Color(0xFFFFEB3B) else Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    if (unreadCeoCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color.Red, shape = CircleShape)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = unreadCeoCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "مرحباً، $currentUserName 👋",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "المدير العام لشركة نواعم",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                )
                            }
                            
                            // High-polish mini icon representing the brand
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✨", fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Premium summary card with transparent-glass feel matching the "Professional Polish" HTML design
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Site counter
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "المواقع النشطة",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.75f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${MockDatabase.institutions.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            // Employee counter
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "إجمالي الموظفين",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.75f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${MockDatabase.employees.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            // Attendance gauge
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "الحضور اليومي",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.75f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (MockDatabase.employees.isEmpty()) "0%" else "100%",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    edgePadding = 8.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "المؤسسات",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.Business, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "المشرفون",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.PeopleOutline, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "الموظفون",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.Engineering, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Text(
                                "مسيرات الرواتب",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = {
                            Text(
                                "الفواتير والمشتريات",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.Receipt, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = {
                            Text(
                                "الأرباح والتحليل المالي",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
                    )
                }
            }
        }
    ) { paddingValues ->
        if (selectedTab == 3) {
            CeoPayrollTabContent(
                paddingValues = paddingValues,
                selectedPayrollInstitution = selectedPayrollInstitution,
                onSelectedPayrollInstitutionChange = { selectedPayrollInstitution = it },
                selectedPayrollMonth = selectedPayrollMonth,
                onSelectedPayrollMonthChange = { selectedPayrollMonth = it },
                institutionPaymentType = institutionPaymentType,
                onInstitutionPaymentTypeChange = { institutionPaymentType = it },
                calculatedReport = calculatedReport,
                selectedEmployeeForPayment = selectedEmployeeForPayment,
                onSelectedEmployeeForPaymentChange = { selectedEmployeeForPayment = it },
                transferNumberInput = transferNumberInput,
                onTransferNumberInputChange = { transferNumberInput = it },
                simulatedReceiptAttached = simulatedReceiptAttached,
                onSimulatedReceiptAttachedChange = { simulatedReceiptAttached = it }
            )
        } else if (selectedTab == 4) {
            CeoInvoicesTabContent(paddingValues = paddingValues)
        } else if (selectedTab == 5) {
            CeoFinancialDashboardTabContent(paddingValues = paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Live stats dashboard row
                Text(
                    text = "الإحصائيات المباشرة اليوم",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "نسبة العمل اليوم",
                        value = if (MockDatabase.employees.isEmpty()) "0%" else "100%",
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "المنشآت المشغلة",
                        value = "${MockDatabase.institutions.size}",
                        icon = Icons.Default.Domain,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "المشرفون الحاليون",
                        value = "${MockDatabase.supervisors.size}",
                        icon = Icons.Default.SupervisedUserCircle,
                        color = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "إجمالي العمالة",
                        value = "${MockDatabase.employees.size} عاملاً",
                        icon = Icons.Default.People,
                        color = Color(0xFF00ACC1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Layout balance spacer
                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = when (selectedTab) {
                            0 -> "قائمة العقود والمؤسسات الناشطة"
                            1 -> "طاقم الإشراف وتغطية التحضير اليومي"
                            else -> "قائمة الموظفين وتفاصيل الحسابات البنكية"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Search box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = when (selectedTab) {
                                0 -> "ابحث باسم المستشفى أو الفندق..."
                                1 -> "ابحث باسم المشرف..."
                                else -> "ابحث عن موظف (بالاسم أو الإقامة)..."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // Tab contents
            when (selectedTab) {
                0 -> {
                    // Add Institution Button at top
                    item {
                        Button(
                            onClick = { showAddInstitutionDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إضافة مؤسسة جديدة", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    // Contracts list
                    val filtered = MockDatabase.institutions.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateMessage(
                                if (searchQuery.isEmpty())
                                    "لا توجد مؤسسات أو منشآت مضافة حالياً. اضغط على زر الإضافة أعلاه للبدء بتأسيس أول منشأة"
                                else
                                    "لا توجد منشآت مطابقة لبحثك"
                            )
                        }
                    } else {
                        items(filtered) { inst ->
                            InstitutionCard(inst)
                        }
                    }
                }
                1 -> {
                    // Add Supervisor Button at top
                    item {
                        Button(
                            onClick = { showAddSupervisorDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إضافة مشرف جديد", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    // Supervisors List
                    val filtered = MockDatabase.supervisors.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateMessage(
                                if (searchQuery.isEmpty())
                                    "لا يوجد مشرفين ميدانيين مسجلين حالياً. اضغط على زر الإضافة أعلاه لتسجيل أول مشرف"
                                else
                                    "لا يوجد مشرفين مطابقين لبحثك"
                            )
                        }
                    } else {
                        items(filtered) { supervisor ->
                            SupervisorCard(supervisor)
                        }
                    }
                }
                2 -> {
                    // Add Employee Button at top
                    item {
                        Button(
                            onClick = { showAddEmployeeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل موظف جديد", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    // Employees with Banking Details List
                    val filtered = MockDatabase.employees.filter {
                        it.name.contains(searchQuery, ignoreCase = true) || it.iqamaId.contains(searchQuery)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateMessage(
                                if (searchQuery.isEmpty()) 
                                    "قائمة الطاقم والموظفين فارغة حالياً. أضف موظفاً يدوياً أو قم باستيراد ملف إكسل بالدفعات لتفعيل التحضير والرواتب 🚀"
                                else 
                                    "لا يوجد موظف مطابق للإقحام أو البحث حالياً"
                            )
                        }
                    } else {
                        items(filtered) { employee ->
                            EmployeeCard(employee)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }
    }
}

// 3. Supervisor Attendance Screen with Real-Time Lock & Period Selection
@Composable
fun SupervisorDashboardScreen(
    currentSupervisorName: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!MockDatabase.isConnectedToSupabase && !MockDatabase.isSyncing) {
            MockDatabase.syncFromSupabase(context)
        }
    }

    // Show loading screen while initial sync is in progress and data is empty
    if (MockDatabase.isSyncing && !MockDatabase.hasLoadedInitialData) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Text(
                    text = "جاري تحميل البيانات من السيرفر...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "الرجاء الانتظار",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // 1. Map logged-in supervisor name to assign assignedLocation
    val supervisor = remember {
        MockDatabase.supervisors.find {
            it.name.equals(currentSupervisorName, ignoreCase = true)
        }
    }
    val assignedLocation = supervisor?.assignedLocation ?: "غير محدد"

    // 2. Auto Generate Current Date & Day Name
    val sdfDate = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US) }
    val sdfDay = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale("ar")) }
    val formattedDate = remember { sdfDate.format(java.util.Date()) }
    val formattedDay = remember { sdfDay.format(java.util.Date()) }

    var selectedShift by remember { mutableStateOf("صباحي") } // "صباحي" or "مسائي"
    var todayNote by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showAddInvoiceDialog by remember { mutableStateOf(false) }

    if (showNotificationsDialog) {
        NotificationCenterDialog(role = "SUPERVISOR", onDismissRequest = { showNotificationsDialog = false })
    }

    if (showAddInvoiceDialog) {
        AddInvoiceDialog(
            initialInstitutionName = assignedLocation,
            onDismissRequest = { showAddInvoiceDialog = false },
            onInvoiceAdded = { showAddInvoiceDialog = false }
        )
    }

    // 3. Real-Time Check if Locked: does a lock exist for today + location + shift?
    val isShiftLocked = remember(selectedShift, formattedDate, MockDatabase.attendanceSubmissions.size) {
        MockDatabase.attendanceSubmissions.any {
            it.institutionName.equals(assignedLocation, ignoreCase = true) &&
            it.date == formattedDate &&
            it.shift.equals(selectedShift, ignoreCase = true)
        }
    }

    // 4. Load matching employees for this supervisor's location (Filter: Only show Active status employees)
    val localEmployees = remember(assignedLocation, MockDatabase.employees.map { it.status }) {
        MockDatabase.employees.filter { 
            it.location.equals(assignedLocation, ignoreCase = true) && 
            it.status == "نشط"
        }
    }

    // Local state state tracker for attendance
    val localRecords = remember { mutableStateListOf<AttendanceRecord>() }

    // 5. LaunchedEffect to sync state when shift or date changes
    LaunchedEffect(assignedLocation, selectedShift, formattedDate, MockDatabase.attendanceSubmissions.size, localEmployees) {
        localRecords.clear()
        // If there's a previous submission (either locked or pre-seeded), load its exact values
        val prevSubmit = MockDatabase.attendanceSubmissions.find {
            it.institutionName.equals(assignedLocation, ignoreCase = true) &&
            it.date == formattedDate &&
            it.shift.equals(selectedShift, ignoreCase = true)
        }

        if (prevSubmit != null) {
            localEmployees.forEach { emp ->
                val recordMatch = prevSubmit.records.find { it.employeeId == emp.id }
                localRecords.add(
                    AttendanceRecord(
                        employeeId = emp.id,
                        employeeName = emp.name,
                        iqamaId = emp.iqamaId,
                        status = recordMatch?.status ?: "معلق/لم يصل",
                        lateMinutes = recordMatch?.lateMinutes ?: 0
                    )
                )
            }
        } else {
            // New sheet: set everyone to Pending by default to represent real-world operation
            localEmployees.forEach { emp ->
                localRecords.add(
                    AttendanceRecord(
                        employeeId = emp.id,
                        employeeName = emp.name,
                        iqamaId = emp.iqamaId,
                        status = "معلق/لم يصل",
                        lateMinutes = 0
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onLogout,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "خروج", tint = Color.White)
                            }

                            // Notification Bell icon with unread indicator badge for Supervisor
                            val unreadSupervisorCount = MockDatabase.notifications.count { it.roleTarget == "SUPERVISOR" && !it.isRead }
                            IconButton(
                                onClick = { showNotificationsDialog = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (unreadSupervisorCount > 0) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.size(40.dp).testTag("supervisor_notif_bell")
                            ) {
                                Box(modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "الإشعارات",
                                        tint = if (unreadSupervisorCount > 0) Color(0xFFFFEB3B) else Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    if (unreadSupervisorCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color.Red, shape = CircleShape)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = unreadSupervisorCount.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Sync Button
                            IconButton(
                                onClick = { MockDatabase.syncFromSupabase(context, forceManual = true) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                if (MockDatabase.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "مزامنة", tint = Color.White)
                                }
                            }

                            // Add Invoice Button for Supervisor inline
                            Button(
                                onClick = { showAddInvoiceDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(40.dp).testTag("supervisor_add_invoice_btn")
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تسجيل فاتورة 🧾", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "مرحباً، المشرف: $currentSupervisorName 👷‍♂️",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "جهة العمل: $assignedLocation",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Offline warning banner
            if (!MockDatabase.isConnectedToSupabase && !MockDatabase.isSyncing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF856404))
                        Text("لا يوجد اتصال - البيانات قد لا تكون محدثة", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF856404))
                    }
                }
            }

            // Info guide card - strictly no billing or salaries displayed
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "تحضير العمالة والموظفين الميدانيين اليومي",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "قم باختيار الفترة وتعبئة حضور العمالة بدقة بالغة. سيقوم النظام بحفظ البيانات تلقائياً وتجميد التعديل لضمان دقة مسيرات الرواتب البنكية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right
                    )
                }
            }

            // period and date status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$formattedDay - $formattedDate",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Text(
                    text = "تحديد الفترة والوردية الحالية:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // morning/evening Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedShift = "مسائي" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedShift == "مسائي") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedShift == "مسائي") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.WbTwilight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الفترة المسائية", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { selectedShift = "صباحي" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedShift == "صباحي") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedShift == "صباحي") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الفترة الصباحية", fontWeight = FontWeight.Bold)
                }
            }

            // Lock Alert Banner
            if (isShiftLocked) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEBAA)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔒 تم إرسال واعتماد هذا التحضير وهو مقفل الآن لحماية الحسابات. لتعديله يرجى طلب إلغاء القفل من المدير العام (ماهر محمد).",
                            color = Color(0xFF856404),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Right
                        )
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF856404))
                    }
                }
            }

            // ⏰ Supervisor Reminder Notifications Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "نظام التذكير وجدولة الإشعارات بالخلفية 🔔",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    Text(
                        text = "يمكنك جدولة إشعارات تذكيرية صوتية تلقائية لتنبيهك في أوقات التحضير إذا لم يتم التحضير بعد. اضغط للجدولة ثم اقفل الشاشة للتجربة:",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val isMorningSubmitted = MockDatabase.attendanceSubmissions.any {
                                    it.institutionName.equals(assignedLocation, ignoreCase = true) &&
                                    it.date == formattedDate &&
                                    it.shift.equals("صباحي", ignoreCase = true)
                                }
                                if (!isMorningSubmitted) {
                                    LocalNotificationHelper.scheduleDelayedNotification(
                                        context = context,
                                        title = "⏰ تذكير التحضير الصباحي - شركة نواعم",
                                        message = "تنبيه هام: الوردية الصباحية لم يتم تحضيرها لموقعك (${assignedLocation}) حتى الآن! يرجى الدخول والتحضير فوراً.",
                                        delayMillis = 7000 // 7 seconds delay
                                    )
                                    Toast.makeText(context, "🔔 تمت جدولة تذكير الوردية الصباحية بعد 7 ثوانٍ بالخلفية! يمكنك الخروج لتجربتها بالصوت.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "الوردية الصباحية محضرَّة بالفعل لليوم 🎉", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("تذكير الصباح ⏰", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        
                        Button(
                            onClick = {
                                val isEveningSubmitted = MockDatabase.attendanceSubmissions.any {
                                    it.institutionName.equals(assignedLocation, ignoreCase = true) &&
                                    it.date == formattedDate &&
                                    it.shift.equals("مسائي", ignoreCase = true)
                                }
                                if (!isEveningSubmitted) {
                                    LocalNotificationHelper.scheduleDelayedNotification(
                                        context = context,
                                        title = "⏰ تذكير التحضير المسائي - شركة نواعم",
                                        message = "تنبيه هام: الوردية المسائية لم يتم تحضيرها لموقعك (${assignedLocation}) حتى الآن! يرجى الدخول والتحضير فوراً.",
                                        delayMillis = 7000 // 7 seconds delay
                                    )
                                    Toast.makeText(context, "🔔 تمت جدولة تذكير الوردية المسائية بعد 7 ثوانٍ بالخلفية! يمكنك الخروج لتجربتها بالصوت.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "الوردية المسائية محضرَّة بالفعل لليوم 🎉", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("تذكير المساء ⏰", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Scrollable Employees list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (localRecords.isEmpty()) {
                    item {
                        EmptyStateMessage("لا يوجد موظفون نشطون مسجلون في هاته المنشأة حالياً. يرجى من المدير إضافة موظفين وربطهم بموقعك لتبدأ في تحضيرهم 👮‍♂️")
                    }
                } else {
                    items(localRecords) { record ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            val badgeBgColor = when (record.status) {
                                "حاضر" -> Color(0xFFE8F5E9)
                                "غائب" -> Color(0xFFFFEBEE)
                                "متأخر" -> Color(0xFFFFF3E0)
                                "إجازة" -> Color(0xFFE3F2FD)
                                else -> Color(0xFFF1F5F9)
                            }
                            val badgeTextColor = when (record.status) {
                                "حاضر" -> Color(0xFF2E7D32)
                                "غائب" -> Color(0xFFC62828)
                                "متأخر" -> Color(0xFFE65100)
                                "إجازة" -> Color(0xFF1565C0)
                                else -> Color(0xFF475569)
                            }

                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "إقامة: ${record.iqamaId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    Text(
                                        text = record.employeeName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status Badge on Left
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = badgeBgColor),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = record.status,
                                            color = badgeTextColor,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = "الحالة الحالية:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Interactive action tools
                                val currentInstObjForCalc = MockDatabase.institutions.find { it.name.equals(assignedLocation, ignoreCase = true) }
                                val officialStartTime = currentInstObjForCalc?.shiftStartTime ?: "06:00 AM"

                                if (record.status == "معلق/لم يصل") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "⏱️ وقت الوردية الرسمي للمنشأة: $officialStartTime",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // The Real-Time check-in button
                                        Button(
                                            onClick = {
                                                if (!isShiftLocked) {
                                                    val cal = java.util.Calendar.getInstance()
                                                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                                                    val nowTimeStr = sdf.format(cal.time)

                                                    val oMinutes = parseShiftMinutes(officialStartTime)
                                                    val aMinutes = parseShiftMinutes(nowTimeStr)
                                                    val diff = aMinutes - oMinutes
                                                    val finalLate = if (diff > 0) diff else 0

                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(
                                                            status = if (finalLate > 0) "متأخر" else "حاضر",
                                                            lateMinutes = finalLate
                                                        )
                                                        
                                                        val supervisorMsg = "⚡ تم تحضير ${record.employeeName} في ${nowTimeStr} بـ ${if (finalLate > 0) "$finalLate دقيقة تأخير" else "حضور في الوقت"}"
                                                        MockDatabase.triggerNotification(context, supervisorMsg, "SUPERVISOR")

                                                        if (finalLate > 0) {
                                                            val ceoMsg = "🚨 الموظف ${record.employeeName} وصل متأخراً بـ $finalLate دقيقة في ($assignedLocation). وقت التحضير: $nowTimeStr"
                                                            MockDatabase.triggerNotification(context, ceoMsg, "CEO")
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isShiftLocked
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Yellow)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("⚡ تحضير لحظي بوقت الجوال الفعلي")
                                        }

                                        // Quick testing presets
                                        Text(
                                            text = "⚙️ محاكاة وقت وصول مختلف لتجربة احتساب التأخير التلقائي:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // On-time simulation button
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "حاضر", lateMinutes = 0)
                                                        val msg = "⚡ محاكاة: تم تحضير (${record.employeeName}) على الوقت الرسمي المستهدف (${officialStartTime})."
                                                        MockDatabase.triggerNotification(context, msg, "SUPERVISOR")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("على الوقت", style = MaterialTheme.typography.labelSmall)
                                            }

                                            // Late 15m simulation button
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "متأخر", lateMinutes = 15)
                                                        val supervisorMsg = "⚡ محاكاة: تم تحضير (${record.employeeName}) متأخراً بـ 15 دقيقة."
                                                        MockDatabase.triggerNotification(context, supervisorMsg, "SUPERVISOR")
                                                        
                                                        val ceoMsg = "🚨 تنبيه محاكاة: الموظف ${record.employeeName} متأخر بـ 15 دقيقة في ($assignedLocation)."
                                                        MockDatabase.triggerNotification(context, ceoMsg, "CEO")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFE65100)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("+15 دقيقة", style = MaterialTheme.typography.labelSmall)
                                            }

                                            // Late 45m simulation
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "متأخر", lateMinutes = 45)
                                                        val supervisorMsg = "⚡ محاكاة: تم تحضير (${record.employeeName}) متأخراً بـ 45 دقيقة."
                                                        MockDatabase.triggerNotification(context, supervisorMsg, "SUPERVISOR")
                                                        
                                                        val ceoMsg = "🚨 تنبيه محاكاة: الموظف ${record.employeeName} متأخر بـ 45 دقيقة في ($assignedLocation)."
                                                        MockDatabase.triggerNotification(context, ceoMsg, "CEO")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE0B2), contentColor = Color(0xFFE65100)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("+45 دقيقة", style = MaterialTheme.typography.labelSmall)
                                            }

                                            // Late 120m simulation
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "متأخر", lateMinutes = 120)
                                                        val supervisorMsg = "⚡ محاكاة: تم تحضير (${record.employeeName}) متأخراً بـ 120 دقيقة (ساعتان)."
                                                        MockDatabase.triggerNotification(context, supervisorMsg, "SUPERVISOR")
                                                        
                                                        val ceoMsg = "🚨 تنبيه محاكاة: الموظف ${record.employeeName} متأخر بـ 120 دقيقة في ($assignedLocation)."
                                                        MockDatabase.triggerNotification(context, ceoMsg, "CEO")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC80), contentColor = Color(0xFFE65100)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("+2 ساعة", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }

                                        // Manual Other modes (Absent, Vacation)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "غائب", lateMinutes = 0)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFF991B1B)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("تسجيل غياب الموظف ❌", style = MaterialTheme.typography.labelSmall)
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    val index = localRecords.indexOf(record)
                                                    if (index != -1) {
                                                        localRecords[index] = record.copy(status = "إجازة", lateMinutes = 0)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDBEAFE), contentColor = Color(0xFF1E40AF)),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("إجازة مصرحة 🌴", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                } else {
                                    // Checked in UI details
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Reset Button
                                            TextButton(
                                                onClick = {
                                                    if (!isShiftLocked) {
                                                        val index = localRecords.indexOf(record)
                                                        if (index != -1) {
                                                            localRecords[index] = record.copy(status = "معلق/لم يصل", lateMinutes = 0)
                                                        }
                                                    }
                                                },
                                                enabled = !isShiftLocked,
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text("🔄 إعادة تعيين وتحضير مجدداً", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            }

                                            Text(
                                                text = if (record.status == "متأخر") "تأخير مسجل: ${record.lateMinutes} دقيقة" else "التحضير مسجل بنجاح",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = badgeTextColor)
                                            )
                                        }
                                        
                                        // Manual override Status Segment Buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                AttendanceSegmentButton(
                                                    label = "حاضر",
                                                    isSelected = record.status == "حاضر",
                                                    activeColor = Color(0xFF2E7D32),
                                                    onClick = {
                                                        if (!isShiftLocked) {
                                                            val index = localRecords.indexOf(record)
                                                            if (index != -1) {
                                                                localRecords[index] = record.copy(status = "حاضر", lateMinutes = 0)
                                                            }
                                                        }
                                                    }
                                                )

                                                AttendanceSegmentButton(
                                                    label = "غائب",
                                                    isSelected = record.status == "غائب",
                                                    activeColor = MaterialTheme.colorScheme.error,
                                                    onClick = {
                                                        if (!isShiftLocked) {
                                                            val index = localRecords.indexOf(record)
                                                            if (index != -1) {
                                                                localRecords[index] = record.copy(status = "غائب", lateMinutes = 0)
                                                            }
                                                        }
                                                    }
                                                )

                                                AttendanceSegmentButton(
                                                    label = "متأخر",
                                                    isSelected = record.status == "متأخر",
                                                    activeColor = Color(0xFFF57C00),
                                                    onClick = {
                                                        if (!isShiftLocked) {
                                                            val index = localRecords.indexOf(record)
                                                            if (index != -1) {
                                                                localRecords[index] = record.copy(status = "متأخر")
                                                            }
                                                        }
                                                    }
                                                )
                                            }

                                            Text("تغيير يدوي:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }

                                        if (record.status == "متأخر") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                var minutesText by remember(record) { mutableStateOf(if (record.lateMinutes > 0) record.lateMinutes.toString() else "") }
                                                OutlinedTextField(
                                                    value = minutesText,
                                                    onValueChange = { newValue ->
                                                        if (!isShiftLocked) {
                                                            val cleanDigits = newValue.filter { it.isDigit() }
                                                            minutesText = cleanDigits
                                                            val minsVal = cleanDigits.toIntOrNull() ?: 0
                                                            val index = localRecords.indexOf(record)
                                                            if (index != -1) {
                                                                localRecords[index] = record.copy(lateMinutes = minsVal)
                                                            }
                                                        }
                                                    },
                                                    label = { Text("دقائق التأخير المتراكمة", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                                    modifier = Modifier.width(135.dp),
                                                    singleLine = true,
                                                    enabled = !isShiftLocked,
                                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                                Text("⏱️ تعديل يدوي للدقائق", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF57C00))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes input
            OutlinedTextField(
                value = todayNote,
                onValueChange = { if (!isShiftLocked) todayNote = it },
                placeholder = {
                    Text(
                        "اضف ملاحظات اليوم وعوائق العمل هنا...",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isShiftLocked,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
            )

            // Submit Daily Attendance button
            Button(
                onClick = {
                    if (!MockDatabase.isNetworkAvailable(context)) {
                        android.widget.Toast.makeText(
                            context,
                            "❌ لا يمكن إرسال التحضير بدون اتصال بالإنترنت.\nتأكد من اتصالك ثم أعد المحاولة.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (!isShiftLocked && localRecords.isNotEmpty()) {
                        // Persist dynamically to database simulating Supabase
                        val recordsToSubmit = localRecords.map {
                            AttendanceRecordSubmission(
                                employeeId = it.employeeId,
                                employeeName = it.employeeName,
                                status = it.status,
                                lateMinutes = it.lateMinutes
                            )
                        }

                        val submission = AttendanceSubmission(
                            date = formattedDate,
                            dayName = formattedDay,
                            institutionName = assignedLocation,
                            shift = selectedShift,
                            records = recordsToSubmit
                        )

                        // If previous entry existed, replace it (lock override simulation), otherwise append
                        val existingIndex = MockDatabase.attendanceSubmissions.indexOfFirst {
                            it.institutionName.equals(assignedLocation, ignoreCase = true) &&
                            it.date == formattedDate &&
                            it.shift.equals(selectedShift, ignoreCase = true)
                        }

                        if (existingIndex != -1) {
                            MockDatabase.attendanceSubmissions[existingIndex] = submission
                        } else {
                            MockDatabase.attendanceSubmissions.add(submission)
                        }
                        MockDatabase.postAttendanceToSupabase(context, submission)

                        // Trigger CEO Notification immediately with custom alerts sound on phone
                        try {
                            LocalNotificationHelper.showNotification(
                                context = context,
                                title = "📢 إشعار إداري فوري - شركة نواعم",
                                message = "المشرف ${currentSupervisorName} قام بإنهاء تحضير مؤسسة ${assignedLocation} لليوم."
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        showSuccessDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(bottom = 8.dp),
                enabled = !isShiftLocked &&
                          localRecords.isNotEmpty() &&
                          MockDatabase.isNetworkAvailable(context),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Icon(if (isShiftLocked) Icons.Default.Lock else Icons.Default.DoneAll, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isShiftLocked) "تم إرسال واعتماد التحضير مسبقاً (مقفل)" else "اعتماد وإرسال التحضير اليومي للموقع",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (!MockDatabase.isNetworkAvailable(context)) {
                Text(
                    text = "⚠️ يتطلب اتصالاً بالإنترنت",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Success confirmation feedback dialog
    if (showSuccessDialog) {
        Dialog(onDismissRequest = { showSuccessDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFE8F5E9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Text(
                        text = "تم اعتماد وإرسال التحضير! 🎉",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "تم حفظ سجل تحضير عمالة ($assignedLocation) لفترة ($selectedShift) بنجاح وقفل هذا اليوم لمنع التعديل والعبث المالي في قاعدة البيانات.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showSuccessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("موافق")
                    }
                }
            }
        }
    }
}

// 4. SQL DB & EXPO STUDY CARD (Supabase database setup & copy codes for CEO)
@Composable
fun SqlGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sqlCode = """
-- 1. جدول المؤسسات (الفنادق / المستشفيات)
CREATE TABLE institutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) CHECK (type IN ('مستشفى', 'فندق')) NOT NULL,
    address TEXT,
    monthly_contract_value NUMERIC(12, 2) DEFAULT 0,
    contract_start_date DATE DEFAULT CURRENT_DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. جدول المشرفين والمستخدمين (المدراء والمشرفين)
CREATE TABLE supervisors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_key VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    address TEXT,
    monthly_salary NUMERIC(10, 2) DEFAULT 0,
    assigned_location VARCHAR(255) REFERENCES institutions(name) ON DELETE SET NULL,
    role VARCHAR(50) DEFAULT 'SUPERVISOR',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. جدول الموظفين وطاقم النظافة (بيانات شخصية وبنكية متقدمة)
CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(150) NOT NULL,
    gender VARCHAR(10) CHECK (gender IN ('ذكر', 'أنثى')) NOT NULL,
    nationality VARCHAR(100) NOT NULL,
    iqama_id VARCHAR(50) UNIQUE NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    assigned_location VARCHAR(255) REFERENCES institutions(name) ON DELETE CASCADE,
    department VARCHAR(150), -- القسم بالمستشفى أو عدد الغرف بالفندق
    work_days_scheduled INTEGER DEFAULT 26,
    shift VARCHAR(20) CHECK (shift IN ('صباحي', 'مسائي')) DEFAULT 'صباحي',
    base_salary NUMERIC(10, 2) NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    iban_code VARCHAR(34) NOT NULL,
    bank_account_owner_name VARCHAR(150) NOT NULL,
    bank_account_phone VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. جدول التحضير اليومي (Attendance)
CREATE TABLE attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendance_date DATE DEFAULT CURRENT_DATE,
    employee_id UUID REFERENCES employees(id) ON DELETE CASCADE,
    status VARCHAR(50) CHECK (status IN ('حاضر', 'غائب', 'إجازة')) NOT NULL,
    recorded_by VARCHAR(150), -- اسم المشرف الذي قام بالتحضير
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(attendance_date, employee_id)
);

-- إضافة بيانات تجريبية فورية لتجربة تطبيق شركة نواعم
INSERT INTO institutions (name, type, address, monthly_contract_value) VALUES 
('مستشفى بخش المائي', 'مستشفى', 'جدة، حي الشرفية', 45000.00),
('فندق تلال المودة', 'فندق', 'مكة المكرمة، العزيزية', 85000.00)
ON CONFLICT DO NOTHING;

INSERT INTO supervisors (full_name, email, password_key, phone, address, monthly_salary, assigned_location) VALUES
('ماهر محمد', 'maher714995700@gmail.com', '772877595', '0547149957', 'مكة المكرمة', 5000.00, 'مستشفى بخش المائي')
ON CONFLICT DO NOTHING;
    """.trimIndent()

    Scaffold(
        topBar = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "قاعدة بيانات Supabase لشركة نواعم",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "تأسيس الشاشات وقاعدة البيانات للشركة 🗄️",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "هذه الشاشة تمكنك من جلب نظام جداول Supabase SQL لبناء الجداول المطلوبة بنجاح وإعداد تطبيق Expo للهاتف.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }

            item {
                Text(
                    text = "1. كود إنشاء الجداول (SQL Script)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = "يمكنك تشغيل هذا الكود في Supabase SQL Editor لإنشاء الهيكل للمرحلة 1 مباشرة:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    // Simulated Clipboard copying
                                    Toast.makeText(context, "تم نسخ الكود SQL بنجاح للاستخدام في Supabase!", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.sizeIn(minWidth = 80.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نسخ كود SQL", style = MaterialTheme.typography.labelSmall)
                            }

                            Text(
                                text = "SQL TABLES DEFINITIONS",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.Green, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = sqlCode,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            textAlign = TextAlign.Left
                        )
                    }
                }
            }

            item {
                Text(
                    text = "2. طريقة تشغيل Expo Go وتأسيس React Native",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("خطوات تشغيل تطبيق Expo Go على الهاتف 📱", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Text("1️⃣ قم بتثبيت Node.js ومحرر الأكواد VS Code على حاسوبك الشخصي.", textAlign = TextAlign.Right)
                        Text("2️⃣ افتح مبوب سطر الأوامر (Terminal) واكتب الأمر التالي لتثبيت مشروع جديد:\n`npx create-expo-app NawaemCleaning` ", textAlign = TextAlign.Right)
                        Text("3️⃣ قم بالدخول للمجلد عبر:\n`cd NawaemCleaning` ثم تثبيت حزمة سوبابيس:\n`npm install @supabase/supabase-js` ", textAlign = TextAlign.Right)
                        Text("4️⃣ قم بتثبيت تطبيق (Expo Go) المجاني على هاتفك الآيفون أو الأندرويد من متجر التطبيقات.", textAlign = TextAlign.Right)
                        Text("5️⃣ قم بتشغيل خادوم إكسبو في مجلد المشروع عبر:\n`npx expo start` ", textAlign = TextAlign.Right)
                        Text("6️⃣ سيظهر لك رمز الاستجابة السريعة (QR Code). امسحه بكاميرا الهاتف (على الآيفون) أو بتطبيق Expo Go (على الأندرويد)، وسيتصل التطبيق فوراً بهاتفك لتجربة كامل التغييرات!", textAlign = TextAlign.Right)

                        Text(
                            text = "💡 نصيحة للمبتدئين: هذا التطبيق الذي تتصفحه الآن مبرمج بلغة الاندرويد الرسمية والحديثة (Jetpack Compose مع Kotlin). لقد مكنّاك اليوم من رؤية الشكل النهائي للواجهات المطلوبة بانسجام تام مباشرة على هاتفك لتدرك روعة التصميم قبل المتابعة للتنفيذ الشامل في المرحلة القادمة!",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// Help Segment Sub-Composables
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF4A4A4A),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun InstitutionDetailsDialog(
    institution: Institution,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(institution.name) }
    var editedAddress by remember { mutableStateOf(institution.address) }
    var editedType by remember { mutableStateOf(institution.type) }
    var editedContractValue by remember { mutableStateOf(institution.monthlyContractValue.toString()) }
    var editedShiftTime by remember { mutableStateOf(institution.shiftStartTime) }
    val supervisor = MockDatabase.supervisors.find { it.assignedLocation.equals(institution.name, ignoreCase = true) }
    val employeesCount = MockDatabase.employees.count { it.location.equals(institution.name, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "تفاصيل المؤسسة",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Divider(color = Color(0xFFF1F5F9))

                if (isEditing) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("اسم المؤسسة", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                    OutlinedTextField(
                        value = editedAddress,
                        onValueChange = { editedAddress = it },
                        label = { Text("العنوان", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editedType = "مستشفى" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (editedType == "مستشفى") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) { Text("مستشفى") }
                        OutlinedButton(
                            onClick = { editedType = "فندق" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (editedType == "فندق") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        ) { Text("فندق") }
                    }
                    OutlinedTextField(
                        value = editedContractValue,
                        onValueChange = { editedContractValue = it },
                        label = { Text("قيمة العقد الشهري (ريال)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                    OutlinedTextField(
                        value = editedShiftTime,
                        onValueChange = { editedShiftTime = it },
                        label = { Text("وقت بدء الوردية", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { isEditing = false }) { Text("إلغاء") }
                        Button(onClick = {
                            val updatedInst = institution.copy(
                                name = editedName.trim(),
                                address = editedAddress.trim(),
                                type = editedType,
                                monthlyContractValue = editedContractValue.toDoubleOrNull() ?: institution.monthlyContractValue,
                                shiftStartTime = editedShiftTime.trim()
                            )
                            val idx = MockDatabase.institutions.indexOfFirst { it.id == institution.id }
                            if (idx != -1) {
                                MockDatabase.institutions[idx] = updatedInst
                                MockDatabase.postInstitutionToSupabase(context, updatedInst)
                            }
                            isEditing = false
                        }) { Text("حفظ") }
                    }
                } else {
                    DetailRow("اسم المؤسسة", institution.name)
                    DetailRow("النوع", institution.type)
                    DetailRow("العنوان", institution.address)
                    DetailRow("المشرف المسؤول", supervisor?.name ?: "غير محدد")
                    DetailRow("قيمة العقد الشهري", "${"%,.2f".format(institution.monthlyContractValue)} ريال")
                    DetailRow("عدد العمال", "$employeesCount عامل")
                    DetailRow("وقت الوردية", institution.shiftStartTime)
                }

                Divider(color = Color(0xFFF1F5F9))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("إغلاق", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SupervisorDetailsDialog(
    supervisor: Supervisor,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(supervisor.name) }
    var editedPhone by remember { mutableStateOf(supervisor.phone) }
    var editedAddress by remember { mutableStateOf(supervisor.address) }
    var editedEmail by remember { mutableStateOf(supervisor.email) }
    var editedPassword by remember { mutableStateOf(supervisor.passwordKey) }
    var editedSalary by remember { mutableStateOf(supervisor.monthlySalary.toString()) }
    var editedBankName by remember { mutableStateOf(supervisor.bankName) }
    var editedIbanCode by remember { mutableStateOf(supervisor.ibanCode) }
    var editedLocation by remember { mutableStateOf(supervisor.assignedLocation) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val employees = MockDatabase.employees.filter { it.location.equals(supervisor.assignedLocation, ignoreCase = true) }
    val todayAttendance = MockDatabase.attendanceSubmissions.filter {
        it.institutionName.equals(supervisor.assignedLocation, ignoreCase = true)
    }.lastOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 500.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            val text = """
                                تقرير المشرف: ${supervisor.name}
                                الموقع: ${supervisor.assignedLocation}
                                عدد العمال: ${employees.size}
                                الحاضرون اليوم: ${todayAttendance?.records?.count { it.status == "حاضر" } ?: 0}
                                الغائبون: ${todayAttendance?.records?.count { it.status == "غائب" } ?: 0}
                                IBAN: ${supervisor.ibanCode}
                                البنك: ${supervisor.bankName}
                            """.trimIndent()
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/?text=${android.net.Uri.encode(text)}"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة واتساب", tint = Color(0xFF25D366))
                        }
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "تفاصيل المشرف",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Divider(color = Color(0xFFF1F5F9))

                if (isEditing) {
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedPhone, onValueChange = { editedPhone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedAddress, onValueChange = { editedAddress = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedEmail, onValueChange = { editedEmail = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left))
                    OutlinedTextField(value = editedPassword, onValueChange = { editedPassword = it }, label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left))
                    OutlinedTextField(value = editedSalary, onValueChange = { editedSalary = it }, label = { Text("الراتب الشهري") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedBankName, onValueChange = { editedBankName = it }, label = { Text("اسم البنك") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedIbanCode, onValueChange = { editedIbanCode = it }, label = { Text("رقم الحساب IBAN") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { dropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (editedLocation.isEmpty()) "اختر الموقع" else editedLocation)
                        }
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            MockDatabase.institutions.forEach { inst ->
                                DropdownMenuItem(text = { Text(inst.name) }, onClick = { editedLocation = inst.name; dropdownExpanded = false })
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { isEditing = false }) { Text("إلغاء") }
                        Button(onClick = {
                            val updated = supervisor.copy(
                                name = editedName.trim(),
                                phone = editedPhone.trim(),
                                address = editedAddress.trim(),
                                email = editedEmail.trim(),
                                passwordKey = editedPassword.trim(),
                                monthlySalary = editedSalary.toDoubleOrNull() ?: supervisor.monthlySalary,
                                bankName = editedBankName.trim(),
                                ibanCode = editedIbanCode.trim(),
                                assignedLocation = editedLocation
                            )
                            val idx = MockDatabase.supervisors.indexOfFirst { it.id == supervisor.id }
                            if (idx != -1) {
                                MockDatabase.supervisors[idx] = updated
                                MockDatabase.postSupervisorToSupabase(context, updated)
                            }
                            isEditing = false
                        }) { Text("حفظ") }
                    }
                } else {
                    DetailRow("الاسم", supervisor.name)
                    DetailRow("الهاتف", supervisor.phone)
                    DetailRow("العنوان", supervisor.address)
                    DetailRow("الموقع", supervisor.assignedLocation)
                    DetailRow("حالة التحضير اليوم", if (supervisor.submittedAttendanceToday) "تم التحضير" else "لم يحضر بعد")
                    DetailRow("البنك", supervisor.bankName)
                    DetailRow("IBAN", supervisor.ibanCode)
                    Divider(color = Color(0xFFF1F5F9))
                    Text("العمال تحت إشرافه (${employees.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    employees.take(5).forEach { emp ->
                        val status = todayAttendance?.records?.find { it.employeeId == emp.id }?.status ?: "غير محدد"
                        Text("- ${emp.name} ($status)", style = MaterialTheme.typography.bodySmall, color = when(status) { "حاضر" -> Color(0xFF16A34A); "غائب" -> Color(0xFFDC2626); else -> Color.Gray })
                    }
                    if (employees.size > 5) Text("... و${employees.size - 5} آخرين", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                Divider(color = Color(0xFFF1F5F9))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
            }
        }
    }
}

@Composable
fun EmployeeDetailsDialog(
    employee: Employee,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(employee.name) }
    var editedPhone by remember { mutableStateOf(employee.phone) }
    var editedNationality by remember { mutableStateOf(employee.nationality) }
    var editedAddress by remember { mutableStateOf(employee.address) }
    var editedSalary by remember { mutableStateOf(employee.baseSalary.toString()) }
    var editedBankName by remember { mutableStateOf(employee.bankName) }
    var editedIban by remember { mutableStateOf(employee.iban) }
    var editedLocation by remember { mutableStateOf(employee.location) }
    var editedStatus by remember { mutableStateOf(employee.status) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val todayAttendance = MockDatabase.attendanceSubmissions.lastOrNull { submission ->
        submission.records.any { it.employeeId == employee.id }
    }
    val todayStatus = todayAttendance?.records?.find { it.employeeId == employee.id }?.status ?: "غير محدد"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 500.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("تفاصيل الموظف", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary)
                }

                Divider(color = Color(0xFFF1F5F9))

                if (isEditing) {
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedPhone, onValueChange = { editedPhone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedNationality, onValueChange = { editedNationality = it }, label = { Text("الجنسية") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedAddress, onValueChange = { editedAddress = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedSalary, onValueChange = { editedSalary = it }, label = { Text("الراتب الأساسي") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedBankName, onValueChange = { editedBankName = it }, label = { Text("اسم البنك") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right))
                    OutlinedTextField(value = editedIban, onValueChange = { editedIban = it }, label = { Text("رقم الحساب IBAN") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { dropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (editedLocation.isEmpty()) "اختر الموقع" else editedLocation) }
                        DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            MockDatabase.institutions.forEach { inst -> DropdownMenuItem(text = { Text(inst.name) }, onClick = { editedLocation = inst.name; dropdownExpanded = false }) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("نشط", "مستقيل", "مسرّح").forEach { s ->
                            OutlinedButton(onClick = { editedStatus = s }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (editedStatus == s) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) { Text(s) }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { isEditing = false }) { Text("إلغاء") }
                        Button(onClick = {
                            val updated = employee.copy(
                                name = editedName.trim(), phone = editedPhone.trim(), nationality = editedNationality.trim(),
                                address = editedAddress.trim(), baseSalary = editedSalary.toDoubleOrNull() ?: employee.baseSalary,
                                bankName = editedBankName.trim(), iban = editedIban.trim(), location = editedLocation, status = editedStatus
                            )
                            val idx = MockDatabase.employees.indexOfFirst { it.id == employee.id }
                            if (idx != -1) {
                                MockDatabase.employees[idx] = updated
                                MockDatabase.postEmployeeToSupabase(context, updated)
                            }
                            isEditing = false
                        }) { Text("حفظ") }
                    }
                } else {
                    DetailRow("الاسم", employee.name)
                    DetailRow("الجنسية", employee.nationality)
                    DetailRow("الهاتف", employee.phone)
                    DetailRow("الراتب الأساسي", "${"%,.2f".format(employee.baseSalary)} ريال")
                    DetailRow("البنك", employee.bankName)
                    DetailRow("IBAN", employee.iban)
                    DetailRow("حالة الحضور اليوم", todayStatus, when(todayStatus) { "حاضر" -> Color(0xFF16A34A); "غائب" -> Color(0xFFDC2626); "متأخر" -> Color(0xFFCA8A04); else -> Color.Gray })
                    DetailRow("الحالة", employee.status, if (employee.status == "نشط") Color(0xFF16A34A) else Color(0xFFDC2626))
                }

                Divider(color = Color(0xFFF1F5F9))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
fun InstitutionCard(inst: Institution) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        InstitutionDetailsDialog(
            institution = inst,
            onDismiss = { showDetailsDialog = false }
        )
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = {
                Text(
                    text = "تأكيد حذف المنشأة",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف هذه المؤسسة (${inst.name}) نهائياً؟ سيتم مسح السجل فوراً من الواجهة ومن قاعدة البيانات السحابية (Supabase).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteDialog = false
                        MockDatabase.deleteInstitution(context, inst)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("نعم، احذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailsDialog = true },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete Button & Type badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showConfirmDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف المنشأة",
                            tint = Color(0xFFDC2626)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (inst.type == "مستشفى") Color(0xFFE0F2F1) else Color(0xFFFFF3E0),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = inst.type,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (inst.type == "مستشفى") Color(0xFF00796B) else Color(0xFFE65100),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }

                // Name
                Text(
                    text = inst.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Divider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${inst.employeesCount} عاملاً بالخدمة",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "المشرف المسئول: ${inst.supervisorName}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏰ الوردية الرسمية: ${inst.shiftStartTime}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                )
                Text(
                    text = "📍 ${inst.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Simple sanitation compliance meter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(inst.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = inst.progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape),
                    color = if (inst.progress > 0.9f) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    trackColor = Color(0xFFF1F5F9)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تقييم الاستقرار",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun SupervisorCard(sv: Supervisor) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        SupervisorDetailsDialog(supervisor = sv, onDismiss = { showDetailsDialog = false })
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("تأكيد حذف المشرف", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = { Text("هل أنت متأكد من حذف هذا المشرف (${sv.name})؟", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            confirmButton = { Button(onClick = { showConfirmDeleteDialog = false; MockDatabase.deleteSupervisor(context, sv) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("نعم، احذف", color = Color.White) } },
            dismissButton = { TextButton(onClick = { showConfirmDeleteDialog = false }) { Text("إلغاء") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetailsDialog = true },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { showConfirmDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف المشرف", tint = Color(0xFFDC2626))
                    }

                    Box(
                        modifier = Modifier
                            .background(if (sv.submittedAttendanceToday) Color(0xFFD1FAE5) else Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (sv.submittedAttendanceToday) "تم التحضير" else "لم يحضر",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (sv.submittedAttendanceToday) Color(0xFF065F46) else Color(0xFF991B1B), fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }

                Text(sv.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
            }

            Divider(color = Color(0xFFF1F5F9))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الهاتف: ${sv.phone}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.secondary)
                Text("الموقع: ${sv.assignedLocation}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmployeeCard(emp: Employee) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        EmployeeDetailsDialog(employee = emp, onDismiss = { showDetailsDialog = false })
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("تأكيد حذف الموظف", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = { Text("هل أنت متأكد من حذف هذا الموظف (${emp.name}) نهائياً؟", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            confirmButton = { Button(onClick = { showConfirmDeleteDialog = false; MockDatabase.deleteEmployee(context, emp) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("نعم، احذف", color = Color.White) } },
            dismissButton = { TextButton(onClick = { showConfirmDeleteDialog = false }) { Text("إلغاء") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetailsDialog = true },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showConfirmDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف العامل",
                            tint = Color(0xFFDC2626)
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge indicating status
                    val (chipBg, chipFg, statusText) = when (emp.status) {
                        "مستقيل" -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "🍂 مستقيل")
                        "مسرّح" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "🚫 مسرّح")
                        else -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "🟢 نشط")
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = chipBg),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = chipFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = emp.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "مقر العمل: ${emp.location}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الجنسية: ${emp.nationality}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = "رقم الإقامة: ${emp.iqamaId}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "💳 البيانات والبنكية المعتمدة للموظف:",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text("اسم البنك المعين: ${emp.bankName}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                    Text("الآيبان البنكي (IBAN):", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = emp.iban,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("الراتب الأساسي الشهري: ${emp.baseSalary} ريال", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                    
                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "⚙️ تحديث حالة الموظف الإدارية:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple("مسرّح", Color(0xFFFFEBEE), Color(0xFFC62828)),
                            Triple("مستقيل", Color(0xFFFFF3E0), Color(0xFFE65100)),
                            Triple("نشط", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                        ).forEach { (statusName, bgColor, fgColor) ->
                            val isSelected = emp.status == statusName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) fgColor else bgColor.copy(alpha = 0.5f))
                                    .border(1.dp, if (isSelected) fgColor else fgColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val idx = MockDatabase.employees.indexOfFirst { it.id == emp.id }
                                        if (idx != -1) {
                                            MockDatabase.employees[idx] = MockDatabase.employees[idx].copy(status = statusName)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statusName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) Color.White else fgColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceSegmentButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}

@Composable
fun PayrollDetailsDialog(
    entry: PayrollEntry,
    selectedPayrollMonth: String,
    selectedPayrollInstitution: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var editedAbsentDays by remember { mutableStateOf(entry.absentDaysCount.toString()) }
    var editedLateMinutes by remember { mutableStateOf(entry.totalLateMinutes.toString()) }
    var deductionReason by remember { mutableStateOf("") }

    val employee = MockDatabase.employees.find { it.id == entry.employeeId }
    val workDays = employee?.workDaysScheduled ?: 26
    val actualWorkDays = workDays - entry.absentDaysCount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل يدوي", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("تفاصيل مسير الراتب", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary)
                }

                Divider(color = Color(0xFFF1F5F9))

                DetailRow("اسم الموظف", entry.employeeName)
                DetailRow("أيام العمل المجدولة", "$workDays يوم")
                DetailRow("أيام الغياب", "${entry.absentDaysCount} يوم")
                DetailRow("أيام العمل الفعلية", "$actualWorkDays يوم")
                DetailRow("دقائق التأخير", "${entry.totalLateMinutes} دقيقة")
                DetailRow("الراتب الأساسي", "${"%,.2f".format(entry.baseSalary)} ريال")
                DetailRow("خصم الغياب", "${"%,.2f".format(entry.absentDeduction)} ريال", Color(0xFFDC2626))
                DetailRow("خصم التأخير", "${"%,.2f".format(entry.lateDeduction)} ريال", Color(0xFFDC2626))
                DetailRow("إجمالي الخصومات", "${"%,.2f".format(entry.totalDeduction)} ريال", Color(0xFFDC2626))
                DetailRow("الصافي المستحق", "${"%,.2f".format(entry.netDue)} ريال", Color(0xFF16A34A))
                DetailRow("البنك", entry.bankName)
                DetailRow("IBAN", entry.iban)

                if (isEditing) {
                    Divider(color = Color(0xFFF1F5F9))
                    Text("تعديل يدوي للخصومات:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = editedAbsentDays,
                        onValueChange = { editedAbsentDays = it },
                        label = { Text("عدد أيام الخصم") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deductionReason,
                        onValueChange = { deductionReason = it },
                        label = { Text("سبب التعديل") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { isEditing = false }) { Text("إلغاء") }
                        Button(onClick = {
                            val newAbsentDays = editedAbsentDays.toIntOrNull() ?: entry.absentDaysCount
                            val dailyRate = entry.baseSalary / workDays
                            val newAbsentDeduction = newAbsentDays * dailyRate
                            val newTotalDeduction = newAbsentDeduction + entry.lateDeduction
                            val newNetDue = entry.baseSalary - newTotalDeduction
                            val idx = MockDatabase.employees.indexOfFirst { it.id == entry.employeeId }
                            if (idx != -1) {
                                Toast.makeText(context, "تم تحديث الخصم. الصافي الجديد: ${"%,.2f".format(newNetDue)} ريال", Toast.LENGTH_LONG).show()
                            }
                            isEditing = false
                        }) { Text("حفظ التعديل") }
                    }
                }

                Divider(color = Color(0xFFF1F5F9))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
            }
        }
    }
}

@Composable
fun CeoPayrollTabContent(
    paddingValues: PaddingValues,
    selectedPayrollInstitution: String,
    onSelectedPayrollInstitutionChange: (String) -> Unit,
    selectedPayrollMonth: String,
    onSelectedPayrollMonthChange: (String) -> Unit,
    institutionPaymentType: String,
    onInstitutionPaymentTypeChange: (String) -> Unit,
    calculatedReport: List<PayrollEntry>,
    selectedEmployeeForPayment: PayrollEntry?,
    onSelectedEmployeeForPaymentChange: (PayrollEntry?) -> Unit,
    transferNumberInput: String,
    onTransferNumberInputChange: (String) -> Unit,
    simulatedReceiptAttached: Boolean,
    onSimulatedReceiptAttachedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var monthExpanded by remember { mutableStateOf(false) }
    var instExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }
    var selectedPayrollEntry by remember { mutableStateOf<PayrollEntry?>(null) }

    if (selectedPayrollEntry != null) {
        PayrollDetailsDialog(
            entry = selectedPayrollEntry!!,
            selectedPayrollMonth = selectedPayrollMonth,
            selectedPayrollInstitution = selectedPayrollInstitution,
            onDismiss = { selectedPayrollEntry = null }
        )
    }

    // Resolve Contract Revenue
    val currentInstObj = MockDatabase.institutions.find { it.name == selectedPayrollInstitution }
    val contractValue = currentInstObj?.monthlyContractValue ?: 0.0

    // Compute remaining debt
    val (paidAmount, remainingDebt) = remember(contractValue, institutionPaymentType) {
        when (institutionPaymentType) {
            "كامل" -> Pair(contractValue, 0.0)
            "نصف" -> Pair(contractValue * 0.5, contractValue * 0.5)
            "ربع" -> Pair(contractValue * 0.25, contractValue * 0.75)
            else -> Pair(0.0, contractValue)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "💰 إدارة مسيرات الرواتب والشؤون المالية",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Right
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قم باختيار المنشأة لتوثيق التحصيل المالي للمنشأة وسداد القيمة التعاقدية، ثم شغّل نظام الاحتساب التلقائي لخصومات الغياب والتأخر للموظفين لإصدار الصرف البنكي الفوري مع الربط المباشر بـ WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        // Dropdown Selectors
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("⚙️ تصفية واختيار مسير الصرف المالي:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                    // Month Picker Selector
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text("شهر الصرف والاستحقاق:", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { monthExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    val readableMonth = when (selectedPayrollMonth) {
                                        "2026-05" -> "مايو 2026 م"
                                        "2026-06" -> "يونيو 2026 م"
                                        "2026-07" -> "يوليو 2026 م"
                                        else -> selectedPayrollMonth
                                    }
                                    Text(readableMonth, fontWeight = FontWeight.Bold)
                                }
                            }
                            DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                listOf(
                                    Pair("2026-05", "مايو 2026 م"),
                                    Pair("2026-06", "يونيو 2026 م"),
                                    Pair("2026-07", "يوليو 2026 م")
                                ).forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                        onClick = {
                                            onSelectedPayrollMonthChange(code)
                                            monthExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Institution Picker Selector
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        Text("المنشأة المتعاقد معها ومقر العمل:", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { instExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    Text(selectedPayrollInstitution.ifEmpty { "اختر المنشأة" }, fontWeight = FontWeight.Bold)
                                }
                            }
                            DropdownMenu(expanded = instExpanded, onDismissRequest = { instExpanded = false }) {
                                MockDatabase.institutions.forEach { inst ->
                                    DropdownMenuItem(
                                        text = { Text(inst.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                        onClick = {
                                            onSelectedPayrollInstitutionChange(inst.name)
                                            instExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Institution Contract Payment Card (Revenue & Arrears Tracker)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%,.2f ريال", contractValue),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "📊 القيمة التعاقدية الشهرية للمنشأة:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Divider(color = Color(0xFFF1F5F9))

                    // Change Contract payment type dropdown
                    Text("حالة تحصيل المطالبة المالية من المنشأة:", style = MaterialTheme.typography.labelSmall)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { paymentExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFB300))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFFFFB300))
                                val readableStatus = when (institutionPaymentType) {
                                    "كامل" -> "🟢 دفع كامل العقد (100%)"
                                    "نصف" -> "🟡 دفع نصف العقد (50%)"
                                    "ربع" -> "🟠 دفع ربع العقد (25%)"
                                    else -> "🔴 لم يدفع رصيداً بعد (0%)"
                                }
                                Text(readableStatus, fontWeight = FontWeight.Bold, color = Color(0xFFB76E00))
                            }
                        }
                        DropdownMenu(expanded = paymentExpanded, onDismissRequest = { paymentExpanded = false }) {
                            listOf(
                                Pair("كامل", "🟢 دفع كامل العقد (100%)"),
                                Pair("نصف", "🟡 دفع نصف العقد (50%)"),
                                Pair("ربع", "🟠 دفع ربع العقد (25%)"),
                                Pair("لم يدفع", "🔴 لم يدفع رصيداً بعد (0%)")
                            ).forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                                    onClick = {
                                        onInstitutionPaymentTypeChange(code)
                                        MockDatabase.institutionDebts[selectedPayrollInstitution] = when (code) {
                                            "كامل" -> 0.0
                                            "نصف" -> contractValue * 0.5
                                            "ربع" -> contractValue * 0.75
                                            else -> contractValue
                                        }
                                        paymentExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Show paid vs debt
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBE7)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("المحصل للشركة", style = MaterialTheme.typography.labelSmall, color = Color(0xFF33691E))
                                Text(
                                    text = String.format("%,.0f ريال", paidAmount),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF33691E)
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("المتبقي كمديونية", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                                Text(
                                    text = String.format("%,.0f ريال", remainingDebt),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Collect and Calculate Dues Action Trigger Button
        item {
            Button(
                onClick = {
                    if (selectedPayrollInstitution.isEmpty()) {
                        Toast.makeText(context, "الرجاء تحديد المنشأة المستهدفة أولاً", Toast.LENGTH_SHORT).show()
                    } else {
                        // Recalculation logic
                        // Find all employees of the selected location
                        val matchedEmps = MockDatabase.employees.filter {
                            it.location.equals(selectedPayrollInstitution, ignoreCase = true)
                        }

                        if (matchedEmps.isEmpty()) {
                            Toast.makeText(context, "لا يوجد موظفين مسجلين تحت هذه المنشأة حالياً!", Toast.LENGTH_LONG).show()
                        } else {
                            // Find all attendance records starting with selectedPayrollMonth (e.g., "2026-05")
                            val matchedSubmissions = MockDatabase.attendanceSubmissions.filter {
                                it.institutionName.equals(selectedPayrollInstitution, ignoreCase = true) &&
                                        it.date.startsWith(selectedPayrollMonth)
                            }

                            val entries = matchedEmps.map { emp ->
                                var absentCount = 0
                                var lateCount = 0
                                var totalLateMin = 0

                                // Parse attendance records
                                matchedSubmissions.forEach { sub ->
                                    val rec = sub.records.find { it.employeeId == emp.id }
                                    if (rec != null) {
                                        if (rec.status == "غائب" || rec.status == "غياب") {
                                            absentCount++
                                        } else if (rec.status == "متأخر" || rec.status == "تأخير") {
                                            lateCount++
                                            totalLateMin += rec.lateMinutes
                                        }
                                    }
                                }

                                // Apply Deduction formula requested:
                                // Base Days = Scheduled work days (default: 26)
                                val baseDays = if (emp.workDaysScheduled > 0) emp.workDaysScheduled else 26
                                val ratePerDay = emp.baseSalary / baseDays.toDouble()
                                val ratePerHour = ratePerDay / 8.0 // Assuming 8-hour shift
                                
                                val absentDeductionVal = ratePerDay * absentCount
                                val lateHours = totalLateMin / 60.0
                                val lateDeductionVal = ratePerHour * lateHours
                                val deduction = absentDeductionVal + lateDeductionVal
                                
                                val rawNet = emp.baseSalary - deduction
                                val netDue = if (rawNet < 0.0) 0.0 else rawNet

                                PayrollEntry(
                                    employeeId = emp.id,
                                    employeeName = emp.name,
                                    baseSalary = emp.baseSalary,
                                    absentDaysCount = absentCount,
                                    lateDaysCount = lateCount,
                                    totalLateMinutes = totalLateMin,
                                    absentDeduction = absentDeductionVal,
                                    lateDeduction = lateDeductionVal,
                                    totalDeduction = deduction,
                                    netDue = netDue,
                                    bankName = emp.bankName,
                                    iban = emp.iban,
                                    bankAccountOwnerName = emp.bankAccountOwnerName
                                )
                            }

                            // Populate the list
                            (calculatedReport as SnapshotStateList<PayrollEntry>).clear()
                            calculatedReport.addAll(entries)
                            Toast.makeText(context, "تم جمع واحتساب مستحقات ${entries.size} موظف بنجاح ⚡", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("collect_payroll_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Text("جمع واحتساب مستحقات الرواتب الميدانية ⚡", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }

        // Calculated Payroll Report Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (calculatedReport.isNotEmpty()) "سجل الرواتب الجاهزة للصرف (${calculatedReport.size})" else "لا يوجد بيانات مصرحة",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "📋 التقرير المالي ومسير صرف الرواتب:",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        if (calculatedReport.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💡 علم الاحتساب التلقائي", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "انقر على زر 'جمع واحتساب مستحقات الرواتب الميدانية' أعلاه لجلب موظفي المنشأة واحتساب الخصومات آلياً بناءً على تقارير الغياب والحضور المغلقة.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(calculatedReport) { entry ->
                // Check if paid in mockDatabase
                val paymentKey = "${entry.employeeId}_$selectedPayrollMonth"
                val txRef = MockDatabase.employeePayments[paymentKey]
                val isPaid = txRef != null

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isPaid) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { selectedPayrollEntry = entry }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Employee Info Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Paid / Unpaid Status Badge
                            if (isPaid) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "تم الصرف بالآيبان ✔",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "بانتظار التحويل ⏳",
                                        color = Color(0xFF546E7A),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = entry.employeeName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Divider(color = Color(0xFFF1F5F9))

                        // Financial Info Ledger Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format("%,.2f ريال", entry.baseSalary),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("المرتب الشهري الأساسي:", color = MaterialTheme.colorScheme.outline)
                        }

                        // Attendance Deductions detail
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (entry.absentDaysCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format("- %,.2f ريال", entry.absentDeduction),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "خصم الغياب (${entry.absentDaysCount} يوم):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (entry.totalLateMinutes > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format("- %,.2f ريال", entry.lateDeduction),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "خصم التأخر (${entry.totalLateMinutes} دقيقة / ${String.format("%.1f", entry.totalLateMinutes / 60.0)} س):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("- %,.2f ريال", entry.totalDeduction),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                )
                                Text("إجمالي الخصومات المستقطعة:", color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Net Due (Green highlighted)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الرواتب الصافية مستحقة الدفع:", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format("%,.2f ريال", entry.netDue),
                                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold)
                            )
                        }

                        if (isPaid) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(txRef ?: "", fontWeight = FontWeight.Bold, color = Color(0xFF33691E))
                                    Text("رقم الحوالة البنكية للإيصال:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF558B2F))
                                }
                            }
                        }

                        Divider(color = Color(0xFFF1F5F9))

                        // Action Buttons Trigger Layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // WhatsApp Sharing button
                            OutlinedButton(
                                onClick = {
                                    val arabicText = """
                                        *كشف راتب موظف بشركة نواعم للتشغيل والخدمات* 💰
                                        
                                        *المنشأة المتعاقد معها:* ${selectedPayrollInstitution}
                                        *الموظف الكريم:* ${entry.employeeName}
                                        *شهر الاستحقاق:* ${selectedPayrollMonth}
                                        
                                        - *الراتب الأساسي:* ${String.format("%.2f", entry.baseSalary)} ريال
                                        - *أيام الغياب:* ${entry.absentDaysCount} يوم (خصم: ${String.format("%.2f", entry.absentDeduction)} ريال)
                                        - *التأخير بالدقائق:* ${entry.totalLateMinutes} دقيقة (${String.format("%.1f", entry.totalLateMinutes / 60.0)} ساعة) (خصم: ${String.format("%.2f", entry.lateDeduction)} ريال)
                                        - *إجمالي الاستقطاع والخصم:* ${String.format("%.2f", entry.totalDeduction)} ريال
                                        - *صافي المستحق الصرف:* *${String.format("%.2f", entry.netDue)} ريال*
                                        
                                        ${if (isPaid) "- *حالة التحويل البنكي:* تم صرف المبلغ بنجاح برقم إيصال حوالة (${txRef})" else "- *حالة التحويل البنكي:* قيد التحويل والصرف"}
                                        
                                        _نواعم للخدمات المتكاملة والمقاولات_ 🇸🇦
                                    """.trimIndent()

                                    val encodedText = java.net.URLEncoder.encode(arabicText, "UTF-8")
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://api.whatsapp.com/send?text=$encodedText")
                                    )
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إشعار WhatsApp 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Payment confirmation modal trigger
                            Button(
                                onClick = {
                                    onSelectedEmployeeForPaymentChange(entry)
                                    onTransferNumberInputChange("")
                                    onSimulatedReceiptAttachedChange(false)
                                },
                                enabled = !isPaid,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB300),
                                    disabledContainerColor = Color(0xFFECEFF1)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("صرف الراتب والتحويل البنكي 💳", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (isPaid) Color.Gray else Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Payout Confirmation Dialog Backdrop
    if (selectedEmployeeForPayment != null) {
        Dialog(onDismissRequest = { onSelectedEmployeeForPaymentChange(null) }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "💳 تأكيد الحوالة وصرف الراتب",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "الرجاء مراجعة بيانات الحساب المالي المعتمد للموظف للتحويل الفوري عبر البنك أو الحساب اليمني الموضح بالأسفل:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Right
                    )

                    Divider(color = Color(0xFFF1F5F9))

                    // Beneficiary details box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("اسم المستفيد بالكامل:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(selectedEmployeeForPayment.bankAccountOwnerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)

                            Text("البنك المعتمد تحويل المستحق:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(selectedEmployeeForPayment.bankName, fontWeight = FontWeight.Bold)

                            Text("رقم حساب الآيبان (IBAN):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(selectedEmployeeForPayment.iban, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                            Spacer(modifier = Modifier.height(4.dp))
                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%,.2f ريال", selectedEmployeeForPayment.netDue),
                                    style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold)
                                )
                                Text("الصافي الفعلي المطلوب تحويله:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    // Form input for transfer reference number
                    OutlinedTextField(
                        value = transferNumberInput,
                        onValueChange = { onTransferNumberInputChange(it) },
                        label = { Text("أدخل رقم الحوالة البنكية الصادرة / مرجع الصرف", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        placeholder = { Text("مثال: Trx_291039", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    // Simulated receipt attachment button
                    Button(
                        onClick = { onSimulatedReceiptAttachedChange(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (simulatedReceiptAttached) Color(0xFF2E7D32) else Color(0xFF455A64)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (simulatedReceiptAttached) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Text("تم إرفاق صورة إيصال التحويل بنجاح ✔", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                                Text("📸 إرفاق وإثبات صورة إيصال التحويل البنكي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSelectedEmployeeForPaymentChange(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء الصرف")
                        }

                        Button(
                            onClick = {
                                if (transferNumberInput.isBlank()) {
                                    Toast.makeText(context, "الرجاء تسجيل رقم مرجع التحويل الصادر!", Toast.LENGTH_LONG).show()
                                } else {
                                    val key = "${selectedEmployeeForPayment.employeeId}_$selectedPayrollMonth"
                                    MockDatabase.employeePayments[key] = transferNumberInput.trim()
                                    Toast.makeText(context, "تم تأكيد الحوالة البنكية وصرف راتب ${selectedEmployeeForPayment.employeeName} بنجاح 🎉", Toast.LENGTH_LONG).show()
                                    onSelectedEmployeeForPaymentChange(null)
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("تأكيد وإتمام الصرف والتحويل ✔", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupabaseSyncDashboardCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MockDatabase.isConnectedToSupabase) Color(0xFFF0FDF4) else Color(0xFFFEFDF0)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (MockDatabase.isConnectedToSupabase) Color(0xFFBBF7D0) else Color(0xFFFEF08A)
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (MockDatabase.isConnectedToSupabase) Color(0xFF22C55E) else Color(0xFFEAB308),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = if (MockDatabase.isConnectedToSupabase) "متصل بـ Supabase 🟢" else "محاكاة محلية 🟡",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (MockDatabase.isConnectedToSupabase) Color(0xFF166534) else Color(0xFF854D0E)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الاتصال المباشر بقاعدة البيانات",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (MockDatabase.isConnectedToSupabase) Color(0xFF166534) else Color(0xFF854D0E),
                        textAlign = TextAlign.Right
                    )
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (MockDatabase.isConnectedToSupabase) Color(0xFF22C55E) else Color(0xFFEAB308)
                    )
                }
            }

            Divider(color = (if (MockDatabase.isConnectedToSupabase) Color(0xFFDCFCE7) else Color(0xFFFEF9C3)))

            val creds = MockDatabase.getSupabaseCredentials()
            val urlDomain = creds?.first?.replace("https://", "")?.replace("http://", "")?.substringBefore("/") ?: "غير معرّف"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "مشروع سوبابيس: $urlDomain",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.DarkGray,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "آخر مزامنة مجدولة: ${MockDatabase.lastSyncTime ?: "لم تتم المزامنة بعد"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Right
                    )
                }
            }

            if (MockDatabase.syncErrorMessage != null) {
                Text(
                    text = "⚠️ خطأ في الاتصال: ${MockDatabase.syncErrorMessage}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { MockDatabase.syncFromSupabase(context, forceManual = true) },
                enabled = !MockDatabase.isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (MockDatabase.isConnectedToSupabase) Color(0xFF15803D) else Color(0xFFCA8A04)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("supabase_sync_btn")
            ) {
                if (MockDatabase.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري الاتصال والمزامنة...", color = Color.White, style = MaterialTheme.typography.labelMedium)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (MockDatabase.isConnectedToSupabase) "تحديث ومزامنة البيانات 🔄" else "محاولة الاتصال بقاعدة البيانات الحية 🔌",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvoiceDialog(
    initialInstitutionName: String? = null,
    onDismissRequest: () -> Unit,
    onInvoiceAdded: () -> Unit
) {
    val context = LocalContext.current
    var selectedLoc by remember { mutableStateOf(initialInstitutionName ?: MockDatabase.institutions.firstOrNull()?.name ?: "") }
    var description by remember { mutableStateOf("") }
    var totalAmountInput by remember { mutableStateOf("") }
    var mockImageLoaded by remember { mutableStateOf(false) }
    var simulatedFileName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            mockImageLoaded = true
            simulatedFileName = "فاتورة_" + System.currentTimeMillis().toString().takeLast(6) + ".jpg"
            Toast.makeText(context, "📸 تم اختيار صورة الفاتورة بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Header
                Text(
                    text = "🧾 إضافة وتحميل فاتورة مشتريات",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Divider(color = Color(0xFFF1F5F9))

                // Institution Selection
                Text("المؤسسة المرتبطة:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (initialInstitutionName != null) {
                    // Locked for supervisor
                    OutlinedTextField(
                        value = initialInstitutionName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        )
                    )
                } else {
                    // CEO can select
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLoc,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                            trailingIcon = {
                                IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            MockDatabase.institutions.forEach { inst ->
                                DropdownMenuItem(
                                    text = { Text(inst.name, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                                    onClick = {
                                        selectedLoc = inst.name
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("بيان المشتريات (تفاصيل المواد)", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                    placeholder = { Text("مثال: أدوات ومواد تنظيف وملمعات أرضيات", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                    singleLine = false,
                    maxLines = 3
                )

                // Amount
                OutlinedTextField(
                    value = totalAmountInput,
                    onValueChange = { totalAmountInput = it },
                    label = { Text("المبلغ الإجمالي (شاملاً الضريبة) بالريال", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                    placeholder = { Text("0.00", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Capture image options
                Text("المستند وصورة الفاتورة الورقية:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Simulated Instant Upload
                    Button(
                        onClick = {
                            mockImageLoaded = true
                            simulatedFileName = "فاتورة_محاكية_" + (100..999).random() + ".png"
                            Toast.makeText(context, "✅ تم توليد ومحاكاة صورة الفاتورة فوراً بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("محاكاة الكاميرا 📸", style = MaterialTheme.typography.labelMedium)
                    }

                    // Real Photo Picker
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("رفع من الاستوديو 🖼️", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // If image loaded status
                if (mockImageLoaded) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("✅ تم ربط وحفظ ملف صورة الفاتورة ورقة المشتريات", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF15803D))
                                Text(simulatedFileName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF166534))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFEF3C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️ الرجاء التقاط أو رفع صورة الفاتورة لمطابقتها محاسبياً", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB45309), textAlign = TextAlign.Right)
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706))
                        }
                    }
                }

                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right)
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إلغاء الأمر")
                    }

                    Button(
                        onClick = {
                            val amt = totalAmountInput.toDoubleOrNull()
                            if (selectedLoc.isEmpty()) {
                                errorMsg = "الرجاء اختيار اسم المؤسسة المرتبطة"
                            } else if (description.trim().isEmpty()) {
                                errorMsg = "الرجاء كتابة تفاصيل المشتريات والبيان الكافي"
                            } else if (amt == null || amt <= 0.0) {
                                errorMsg = "الرجاء إدخال مبلغ إجمالي صحيح أكبر من الصفر"
                            } else if (!mockImageLoaded) {
                                errorMsg = "الرجاء التقاط أو رفع صورة الفاتورة لإتمام الدورة المستندية"
                            } else {
                                // Add invoice
                                val sdfTime = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", java.util.Locale.getDefault())
                                val nowStr = sdfTime.format(java.util.Calendar.getInstance().time)
                                val newInvoice = Invoice(
                                    id = "inv_" + System.currentTimeMillis(),
                                    institutionName = selectedLoc,
                                    description = description.trim(),
                                    totalAmount = amt,
                                    dateTime = nowStr,
                                    invoiceImageUrl = simulatedFileName
                                )
                                MockDatabase.invoices.add(0, newInvoice)
                                MockDatabase.postInvoiceToSupabase(context, newInvoice)

                                // Trigger CEO Notification immediately
                                MockDatabase.triggerNotification(
                                    context = context,
                                    message = "🧾 تم تسجيل فاتورة جديدة بمبلغ $amt ريال لـ $selectedLoc ببيان $description من قبل المشرف.",
                                    roleTarget = "CEO"
                                )

                                Toast.makeText(context, "🎉 تم تسجيل الفاتورة بنجاح وحفظها محلياً وسيرفر!", Toast.LENGTH_SHORT).show()
                                onInvoiceAdded()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ الفاتورة 💾")
                    }
                }
            }
        }
    }
}

@Composable
fun CeoInvoicesTabContent(
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    var showAddInvoiceDialog by remember { mutableStateOf(false) }
    var selectedInstitutionFilter by remember { mutableStateOf("الكل") }
    
    if (showAddInvoiceDialog) {
        AddInvoiceDialog(
            onDismissRequest = { showAddInvoiceDialog = false },
            onInvoiceAdded = { showAddInvoiceDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Title Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "🧾 نظام فواتير مشتريات المؤسسات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تابع وضبط كافة فواتير المشتريات ومواد التنظيف المصروفة للمنشآت وعقود التشغيل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Right
                )
            }
        }

        // Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAddInvoiceDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تسجيل فاتورة جديدة 💳", fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Quick drop filter status
            var expandedFilter by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandedFilter = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("المؤسسة: $selectedInstitutionFilter")
                }
                DropdownMenu(
                    expanded = expandedFilter,
                    onDismissRequest = { expandedFilter = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("الكل (كافة المنشآت)", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                        onClick = {
                            selectedInstitutionFilter = "الكل"
                            expandedFilter = false
                        }
                    )
                    MockDatabase.institutions.forEach { inst ->
                        DropdownMenuItem(
                            text = { Text(inst.name, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                            onClick = {
                                selectedInstitutionFilter = inst.name
                                expandedFilter = false
                            }
                        )
                    }
                }
            }
        }

        // List
        val filteredInvoices = if (selectedInstitutionFilter == "الكل") {
            MockDatabase.invoices
        } else {
            MockDatabase.invoices.filter { it.institutionName == selectedInstitutionFilter }
        }

        if (filteredInvoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "لا يوجد أي فواتير مسجلة للمؤسسة المحددة",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "المشرفون والمدراء يمكنهم رفع فواتير المشتريات من الهاتف فوراً وطباعتها محاسبياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredInvoices) { inv ->
                    InvoiceCardItem(inv)
                }
            }
        }
    }
}

@Composable
fun InvoiceCardItem(inv: Invoice) {
    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDetailDialog) {
        Dialog(onDismissRequest = { showDetailDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "📄 تفاصيل مستند الفاتورة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    
                    Text("📍 المنشأة: ${inv.institutionName}", fontWeight = FontWeight.Bold)
                    Text("💰 المبلغ: ${inv.totalAmount} ريال", color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                    Text("📝 البيان: ${inv.description}")
                    Text("⏰ التاريخ والوقت: ${inv.dateTime}")
                    Text("📁 اسم الملف: ${inv.invoiceImageUrl}", style = MaterialTheme.typography.bodySmall)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("صورة مستند الفاتورة محفوظة ومؤمنة بالنظام", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Button(
                        onClick = { showDetailDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Amount
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "${inv.totalAmount} ريال",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF15803D)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showDetailDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("عرض المرفق 📄", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Info
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = inv.institutionName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = inv.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Right
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = inv.dateTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun CeoFinancialDashboardTabContent(
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    
    // Helper calculation for institutions
    fun calculateDuesForInstitution(institutionName: String): Double {
        val instEmployees = MockDatabase.employees.filter { it.location.equals(institutionName, ignoreCase = true) && it.status == "نشط" }
        var totalDues = 0.0
        for (emp in instEmployees) {
            var absentCount = 0
            var lateCount = 0
            var totalLateMin = 0
            for (sub in MockDatabase.attendanceSubmissions) {
                if (sub.institutionName.equals(institutionName, ignoreCase = true)) {
                    val rec = sub.records.find { it.employeeId == emp.id }
                    if (rec != null) {
                        if (rec.status == "غائب" || rec.status == "غياب") {
                            absentCount++
                        } else if (rec.status == "متأخر" || rec.status == "تأخير") {
                            lateCount++
                            totalLateMin += rec.lateMinutes
                        }
                    }
                }
            }
            val baseDays = if (emp.workDaysScheduled > 0) emp.workDaysScheduled else 26
            val ratePerDay = emp.baseSalary / baseDays.toDouble()
            val ratePerHour = ratePerDay / 8.0
            val absentDeductionVal = ratePerDay * absentCount
            val lateHours = totalLateMin / 60.0
            val lateDeductionVal = ratePerHour * lateHours
            val deduction = absentDeductionVal + lateDeductionVal
            val rawNet = emp.baseSalary - deduction
            val netDue = if (rawNet < 0.0) 0.0 else rawNet
            totalDues += netDue
        }
        return totalDues
    }

    // Totals
    val totalRevenues = MockDatabase.institutions.sumOf { it.monthlyContractValue }
    
    val totalEmployeeDues = MockDatabase.institutions.sumOf { inst ->
        calculateDuesForInstitution(inst.name)
    }
    
    val totalPurchases = MockDatabase.invoices.sumOf { it.totalAmount }
    val totalExpenses = totalEmployeeDues + totalPurchases
    val totalNetProfit = totalRevenues - totalExpenses

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Welcome Header
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "📊 المركز المالي الشامل للمدير العام",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "أهلاً بك السيد ماهر. يوضح هذا التقرير التدفق المالي اللحظي للأرباح الصافية بعد خصم الرواتب والفواتير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        // Dashboard overall cards
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "خلاصة المركز والربح الكلي للشركة",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Divider()

                    // Revenue Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("إجمالي عقود الدخل 📥", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E40AF))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${"%,.2f".format(totalRevenues)} ريال",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF1E3A8A)
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("إجمالي المصاريف الكلية 📤", style = MaterialTheme.typography.labelSmall, color = Color(0xFF991B1B))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${"%,.2f".format(totalExpenses)} ريال",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }

                    // Combined Breakdown Details Sub-Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "المشتريات الإجمالية: ${"%,.2f".format(totalPurchases)} ريال",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            "مستحقات الرواتب: ${"%,.2f".format(totalEmployeeDues)} ريال",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Net Profit Card Hero
                    val profitColor = if (totalNetProfit >= 0.0) Color(0xFF15803D) else Color(0xFFB91C1C)
                    val profitBg = if (totalNetProfit >= 0.0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = profitBg),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, profitColor.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (totalNetProfit >= 0.0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = profitColor,
                                modifier = Modifier.size(32.dp)
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text("صافي أرباح شركة نواعم الفعلي 💰", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = profitColor)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${"%,.2f".format(totalNetProfit)} ريال",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = profitColor
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "📊 تفاصيل قائمة عقود الأرباح والتشغيل منفردة",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right
            )
        }

        // Institutions detail cards
        items(MockDatabase.institutions) { inst ->
            val instRevenues = inst.monthlyContractValue
            val instDues = calculateDuesForInstitution(inst.name)
            val instPurchases = MockDatabase.invoices.filter { it.institutionName.equals(inst.name, ignoreCase = true) }.sumOf { it.totalAmount }
            val instProfit = instRevenues - (instDues + instPurchases)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title Block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (inst.type == "مستشفى") Color(0xFFFFF1F2) else Color(0xFFEFF6FF)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = inst.type,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (inst.type == "مستشفى") Color(0xFFE11D48) else Color(0xFF2563EB)
                                )
                            )
                        }

                        Text(
                            text = inst.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Divider(color = Color(0xFFF1F5F9))

                    // 1. Contract Value Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${"%,.2f".format(instRevenues)} ريال", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        Text("قيمة العقد الشهري 📥", color = Color.Gray)
                    }

                    // 2. Employee Dues Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${"%,.2f".format(instDues)} ريال", fontWeight = FontWeight.Bold)
                        Text("مستحقات العمال الإجمالية (-الخصومات) 👤", color = Color.Gray)
                    }

                    // 3. Purchase row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${"%,.2f".format(instPurchases)} ريال", fontWeight = FontWeight.Bold)
                        Text("فواتير المشتريات والمنظفات 🧾", color = Color.Gray)
                    }

                    // Profit Equation Result Block
                    val isProfit = instProfit >= 0.0
                    val textCol = if (isProfit) Color(0xFF15803D) else Color(0xFFB91C1C)
                    val blockBg = if (isProfit) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = blockBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${"%,.2f".format(instProfit)} ريال",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = textCol
                            )
                            Text(
                                text = "صافي ربح نواعم الفعلي من العقد 🛡️",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = textCol
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

