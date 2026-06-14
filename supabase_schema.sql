-- ============================================================
-- قاعدة بيانات شركة نواعم لخدمات التنظيف
-- Nawaem Cleaning Services — Supabase PostgreSQL Schema
-- ============================================================
-- طريقة الاستخدام:
--   افتح Supabase Dashboard → SQL Editor → انسخ هذا الكود كاملاً وانقر Run
--   آمن للتشغيل على قاعدة بيانات موجودة أو جديدة (لا يحذف بيانات)
-- ============================================================

-- تفعيل امتداد UUID
create extension if not exists "uuid-ossp";

-- ============================================================
-- 1. جدول المؤسسات والعقود (institutions)
-- ============================================================
create table if not exists public.institutions (
    id            uuid primary key default gen_random_uuid(),
    name          text not null unique,
    type          text not null default 'مستشفى',
    address       text,
    monthly_contract_value numeric(12, 2) not null default 0.00,
    shift_start_time       text default '06:00 AM',
    created_at    timestamptz default now() not null
);

-- إضافة الأعمدة المفقودة إذا كان الجدول موجوداً مسبقاً
do $$ begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'institutions' and column_name = 'shift_start_time'
    ) then
        alter table public.institutions add column shift_start_time text default '06:00 AM';
    end if;
end $$;

-- ============================================================
-- 2. جدول المشرفين (supervisors)
-- ============================================================
create table if not exists public.supervisors (
    id                uuid primary key default gen_random_uuid(),
    full_name         text not null,
    email             text not null unique,
    password_key      text not null,
    phone             text,
    address           text,
    monthly_salary    numeric(12, 2) default 0.00,
    assigned_location text references public.institutions(name) on update cascade on delete set null,
    role              text not null default 'SUPERVISOR',
    username          text,
    bank_name         text default 'بنك الكريمي الإسلامي',
    iban_code         text,
    created_at        timestamptz default now() not null
);

-- إضافة الأعمدة المفقودة
do $$ begin
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='supervisors' and column_name='bank_name') then
        alter table public.supervisors add column bank_name text default 'بنك الكريمي الإسلامي';
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='supervisors' and column_name='iban_code') then
        alter table public.supervisors add column iban_code text;
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='supervisors' and column_name='username') then
        alter table public.supervisors add column username text;
    end if;
end $$;

-- ============================================================
-- 3. جدول الموظفين (employees)
-- ============================================================
create table if not exists public.employees (
    id                      uuid primary key default gen_random_uuid(),
    full_name               text not null,
    gender                  text not null default 'ذكر',
    nationality             text not null default 'يمني',
    iqama_id                text not null unique,
    phone                   text,
    address                 text default 'سكن الشركة الرئيسي',
    assigned_location       text references public.institutions(name) on update cascade on delete set null,
    department              text default 'قسم النظافة والخدمات العامة',
    work_days_scheduled     integer not null default 26,
    shift                   text not null default 'صباحي',
    base_salary             numeric(12, 2) not null default 0.00,
    bank_name               text not null default 'بنك الكريمي الإسلامي',
    iban_code               text not null default '',
    bank_account_owner_name text not null default '',
    bank_account_phone      text,
    status                  text not null default 'نشط',
    created_at              timestamptz default now() not null
);

-- إضافة الأعمدة المفقودة
do $$ begin
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='gender') then
        alter table public.employees add column gender text not null default 'ذكر';
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='bank_account_owner_name') then
        alter table public.employees add column bank_account_owner_name text not null default '';
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='bank_account_phone') then
        alter table public.employees add column bank_account_phone text;
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='status') then
        alter table public.employees add column status text not null default 'نشط';
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='department') then
        alter table public.employees add column department text default 'قسم النظافة والخدمات العامة';
    end if;
    if not exists (select 1 from information_schema.columns where table_schema='public' and table_name='employees' and column_name='work_days_scheduled') then
        alter table public.employees add column work_days_scheduled integer not null default 26;
    end if;
end $$;

-- ============================================================
-- 4. جدول الحضور (attendance)
-- ============================================================
create table if not exists public.attendance (
    id              bigserial primary key,
    attendance_date date not null default current_date,
    employee_id     text not null,
    status          text not null default 'حاضر',
    recorded_by     text not null,
    notes           text default 'تحضير عبر التطبيق',
    created_at      timestamptz default now() not null,
    constraint unique_attendance_per_employee_day unique (attendance_date, employee_id)
);

-- ============================================================
-- 5. جدول الفواتير (invoices)
-- ============================================================
create table if not exists public.invoices (
    id                  uuid primary key default gen_random_uuid(),
    institution_name    text,
    description         text,
    total_amount        numeric(12, 2) default 0.00,
    date_time           text,
    invoice_image_url   text,
    created_at          timestamptz default now() not null
);

-- ============================================================
-- 6. جدول الإشعارات (notifications)
-- ============================================================
create table if not exists public.notifications (
    id          text primary key default 'notif_' || replace(gen_random_uuid()::text, '-', ''),
    message     text not null,
    role_target text not null default 'CEO',
    timestamp   text not null,
    is_read     boolean not null default false,
    created_at  timestamptz default now() not null
);

-- ============================================================
-- الفهارس لتسريع الاستعلامات
-- ============================================================
create index if not exists idx_employees_status   on public.employees(status);
create index if not exists idx_employees_location on public.employees(assigned_location);
create index if not exists idx_attendance_date    on public.attendance(attendance_date);
create index if not exists idx_attendance_employee on public.attendance(employee_id);
create index if not exists idx_supervisors_location on public.supervisors(assigned_location);

-- ============================================================
-- صلاحيات القراءة والكتابة للتطبيق (Row Level Security مُعطَّل للمشروع)
-- ============================================================
alter table public.institutions  disable row level security;
alter table public.supervisors   disable row level security;
alter table public.employees     disable row level security;
alter table public.attendance    disable row level security;
alter table public.invoices      disable row level security;
alter table public.notifications disable row level security;

-- منح صلاحيات كاملة للـ anon key المستخدم في التطبيق
grant all on public.institutions  to anon, authenticated;
grant all on public.supervisors   to anon, authenticated;
grant all on public.employees     to anon, authenticated;
grant all on public.attendance    to anon, authenticated;
grant all on public.invoices      to anon, authenticated;
grant all on public.notifications to anon, authenticated;
grant usage, select on all sequences in schema public to anon, authenticated;

-- ============================================================
-- تحديث الـ schema cache في Supabase لحل أخطاء "column not found"
-- ============================================================
notify pgrst, 'reload schema';
