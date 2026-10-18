-- Organization catalog names are normalized by the application. The database
-- constraints close concurrent duplicate-insert races under the schema's
-- case- and accent-insensitive utf8mb4_0900_ai_ci collation.

ALTER TABLE faculties
    ADD UNIQUE KEY uq_faculties_name (name);

ALTER TABLE departments
    ADD UNIQUE KEY uq_departments_faculty_name (faculty_id, name);
