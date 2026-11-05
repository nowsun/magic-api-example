-- =====================================================================
-- magic-api SaaS example —— SQL Server 建表脚本
-- 对应 magic-api-example-master 的数据库存储（resource.type = database）
-- 由 PostgreSQL 版 DDL 翻译而来，适用于 SQL Server 2012 及以上版本。
-- =====================================================================

-- 可选：创建数据库（按需修改名称与文件路径）
-- CREATE DATABASE magic_api;

-- 可选：建一个最小权限的应用账号
-- CREATE LOGIN magic_api_app WITH PASSWORD = 'replace-with-a-database-password';
-- CREATE USER  magic_api_app FOR LOGIN magic_api_app;

-- =====================================================================
-- 1. magic_api_file_v2 —— 接口/函数脚本存储
-- =====================================================================
-- 注意 file_path 用 nvarchar(450) 而不是 nvarchar(512)：
-- nvarchar(512) = 1024 字节，超过 SQL Server 聚集索引键 900 字节上限，
-- 会报错误 1919。而 magic-api 的路径含中文，必须用 Unicode，因此取 450。
-- 若确需 512 字符，改用 PRIMARY KEY NONCLUSTERED (file_path)。
IF OBJECT_ID(N'dbo.magic_api_file_v2', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.magic_api_file_v2 (
        file_path    nvarchar(450)  NOT NULL,
        file_content nvarchar(max)  NULL,
        CONSTRAINT PK_magic_api_file_v2 PRIMARY KEY (file_path)
    );
END;
GO

-- =====================================================================
-- 2. magic_backup_record_v2 —— 备份历史存储
-- =====================================================================
-- content 对应 Java 端 byte[]（Backup.content），故用 varbinary(max)。
IF OBJECT_ID(N'dbo.magic_backup_record_v2', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.magic_backup_record_v2 (
        id          nvarchar(32)   NOT NULL,
        create_date bigint         NOT NULL,
        tag         nvarchar(32)   NULL,
        type        nvarchar(32)   NULL,
        name        nvarchar(64)   NULL,
        content     varbinary(max) NULL,
        create_by   nvarchar(64)   NULL,
        CONSTRAINT PK_magic_backup_record_v2 PRIMARY KEY (id, create_date)
    );
END;
GO

-- =====================================================================
-- 3. magic_api_access —— 租户访问凭据
-- =====================================================================
-- bigserial → bigint IDENTITY(1,1)
-- int2      → smallint
-- timestamptz → datetime2（SQL Server 无真正带时区类型；需要偏移量可用 datetimeoffset）
-- access_key_id 使用大小写敏感排序规则，与 PostgreSQL 默认行为保持一致
-- （否则默认 CI 排序规则下 'abc' 与 'ABC' 会互相匹配）。
IF OBJECT_ID(N'dbo.magic_api_access', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.magic_api_access (
        id                     bigint         NOT NULL IDENTITY(1,1),
        tenant_id              bigint         NOT NULL,
        access_key_id          nvarchar(128)  COLLATE SQL_Latin1_General_CP1_CS_AS NOT NULL,
        access_key_secret      char(64)       NULL,
        access_key_secret_hash char(64)       NOT NULL,
        status                 smallint       NOT NULL CONSTRAINT DF_magic_api_access_status DEFAULT 1,
        expires_at             datetime2      NULL,
        created_at             datetime2      NOT NULL CONSTRAINT DF_magic_api_access_created_at DEFAULT sysdatetime(),
		remark                 nvarchar(128)  null,
        CONSTRAINT PK_magic_api_access PRIMARY KEY (id),
        CONSTRAINT UQ_magic_api_access_key_id UNIQUE (access_key_id)
    );
END;
GO

-- =====================================================================
-- 授权：应用账号对资源表/备份表具备增删改查，对凭据表只读
-- =====================================================================
-- GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.magic_api_file_v2     TO magic_api_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.magic_backup_record_v2 TO magic_api_app;
-- GRANT SELECT                          ON dbo.magic_api_access      TO magic_api_app;

-- =====================================================================
-- 4. 租户凭据种子数据（示例，请勿提交真实密钥）
-- =====================================================================
-- 用 HASHBYTES 替代 PostgreSQL 的 pgcrypto digest()：
--   LOWER(CONVERT(varchar(64), HASHBYTES('SHA2_256', '明文密钥'), 2))
-- CONVERT(..., 2) 输出不带 0x 前缀的十六进制；LOWER 对齐小写格式。
-- access_key_secret_hash 存的是明文的 SHA-256 小写十六进制摘要，不存明文。
--
-- INSERT INTO dbo.magic_api_access (tenant_id, access_key_id, access_key_secret_hash)
-- VALUES (123456789012345, 'replace-with-access-key-id',
--         LOWER(CONVERT(varchar(64), HASHBYTES('SHA2_256', 'replace-with-access-key-secret'), 2)));
-- GO
