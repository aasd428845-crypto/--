-- ==========================================
-- كود إنشاء قاعدة البيانات لشركة نواعم (Supabase PostgreSQL Schema)
-- متوافق تماماً مع الأنظمة والواجهات الأخيرة بالسوق اليمني والاتصال المباشر
-- ==========================================

-- تفعيل تمديد UUID لإنشاء معرفات عشوائية فريدة إذا لزم الأمر
create extension if not exists "uuid-ossp";

-- ==========================================
-- 1. جدول المؤسسات والعقود (institutions)
-- ==========================================
create table if not exists public.institutions (
    id text primary key default concat('inst_', replace(gen_random_uuid()::text, '-', '')),
    name text not null unique,                                            -- اسم المنشأة أو المؤسسة (مثل: فندق سبأ الدولي، مستشفى العلوم والتكنولوجيا)
    type text not null default 'مستشفى',                                  -- نوع المنشأة (مستشفى، فندق، جهة حكومية، شركة)
    address text,                                                         -- عنوان وموقع المنشأة الجغرافي
    monthly_contract_value numeric(12, 2) not null default 0.00,          -- القيمة الإجمالية للعقد الشهري بالريال
    shift_start_time text default '06:00 AM',                             -- توقيت بدء الوردية الافتراضي للتحضير
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- إضافة تعليقات توضيحية للجدول والعمود
comment on table public.institutions is 'جدول لتسجيل المؤسسات والمنشآت المتعاقد معها وقيمة عقودها';

-- ==========================================
-- 2. جدول المستخدمين والمشرفين (supervisors)
-- ==========================================
create table if not exists public.supervisors (
    id text primary key default concat('sup_', replace(gen_random_uuid()::text, '-', '')),
    full_name text not null,                                              -- الاسم الكامل للمشرف الميداني
    email text not null unique,                                           -- البريد الإلكتروني (لتسجيل الدخول)
    password_key text not null,                                           -- كلمة المرور / مفتاح الوصول
    phone text,                                                           -- رقم الهاتف المحمول (يمني)
    address text,                                                         -- السكن الحالي
    monthly_salary numeric(12, 2) default 0.00,                           -- الراتب المخصص للمشرف
    assigned_location text references public.institutions(name) on update cascade on delete set null, -- المنشأة المرتبطة بالمشرف للتحضير
    role text not null default 'SUPERVISOR',                              -- صلاحيات المستخدم (CEO أو SUPERVISOR)
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- ==========================================
-- 3. جدول الموظفين وطاقم العمل (employees)
-- ==========================================
create table if not exists public.employees (
    id text primary key default concat('emp_', replace(gen_random_uuid()::text, '-', '')),
    full_name text not null,                                              -- الاسم الكامل للموظف
    gender text not null default 'ذكر' check (gender in ('ذكر', 'أنثى')),   -- الجنس
    nationality text not null default 'يمني',                              -- الجنسية (موطن وسوق القائمة اليمني بشكل افتراضي)
    iqama_id text not null unique,                                        -- الهوية الوطنية أو رقم الإقامة / البطاقة الشخصية
    phone text,                                                           -- الهاتف المباشر
    address text default 'سكن الشركة الرئيسي',                             -- السكن ومدينة التعيين
    assigned_location text references public.institutions(name) on update cascade on delete set null, -- المنشأة الرياضية أو الخدمية المعين بها الموظف
    department text default 'قسم النظافة والخدمات العامة',                  -- القسم أو الوردية المخصصة (اسم الجناح، الممرات، غسل الملابس)
    work_days_scheduled integer not null default 26,                     -- عدد الأيام المجدولة للعمل شهرياً للاستحقاق
    shift text not null default 'صباحي' check (shift in ('صباحي', 'مسائي')), -- الوردية (صباحي / مسائي)
    base_salary numeric(12, 2) not null default 0.00,                     -- الراتب الأساسي الشهري
    
    -- تفاصيل الحسابات البنكية اليمنية (الكريمي، العمقي، التضامن...)
    bank_name text not null default 'بنك الكريمي الإسلامي',                 -- اسم المصرف اليمني أو المحفظة المالية
    iban_code text not null,                                              -- رقم الحساب البنكي أو رقم التحويل المالي المعتمد
    bank_account_owner_name text not null,                                -- اسم صاحب الحساب المالي بالكامل (المستفيد)
    bank_account_phone text,                                              -- رقم الهاتف المرتبط بالحساب البنكي للتأكيد والإرسال
    
    -- حالة الموظف العملية (نشط، مستقيل، مسرّح للعمل الفعلي المباشر)
    status text not null default 'نشط' check (status in ('نشط', 'مستقيل', 'مسرّح')),
    
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- ==========================================
-- 4. جدول الحضور والتحضير اللحظي (attendance)
-- ==========================================
create table if not exists public.attendance (
    id bigserial primary key,
    attendance_date date not null default current_date,                    -- تاريخ اليوم البسيط للتحضير
    employee_id text not null,                                            -- معرف الموظف المتطابق
    status text not null check (status in ('حاضر', 'غائب', 'متأخر', 'إجازة')), -- حالة الحضور اليومي
    recorded_by text not null,                                            -- اسم المنشأة والوردية التي قامت بالتسجيل والتحضير
    notes text default 'تحضير عبر التطبيق',                                -- ملاحظات إضافية مرسلة من المشرف الميداني
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    
    -- قيد فريد لضمان عدم تكرار تحضير نفس الموظف في نفس اليوم لتجنب ثغرات التحضير المزدوج
    constraint unique_attendance_per_employee_day unique (attendance_date, employee_id)
);

-- ==========================================
-- 5. جدول الإشعارات والتذكيرات الموحد (notifications)
-- ==========================================
create table if not exists public.notifications (
    id text primary key default concat('notif_', replace(gen_random_uuid()::text, '-', '')),
    message text not null,                                                -- محتوى الإشعار أو التنبيه الفوري
    role_target text not null check (role_target in ('CEO', 'SUPERVISOR')), -- الجهة والصلة المستهدفة بالاشعار
    timestamp text not null,                                              -- توقيت الإشعار بصيغة مقروءة للواجهة
    is_read boolean not null default false,                               -- حالة قراءة الإشعار من المستخدم
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- ==========================================
-- إنشاء الفهارس (Indexes) لسرعة معالجة وبحث البيانات والمطابقة
-- ==========================================
create index if not exists idx_employees_status on public.employees(status);
create index if not exists idx_employees_location on public.employees(assigned_location);
create index if not exists idx_attendance_date on public.attendance(attendance_date);
create index if not exists idx_attendance_employee on public.attendance(employee_id);
create index if not exists idx_supervisors_location on public.supervisors(assigned_location);

-- تم بحمد الله إنشاء البنية الفعلية لجداول مشروع شركة نواعم وإقران الصلاحيات
