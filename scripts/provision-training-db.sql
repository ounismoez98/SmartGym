SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'training_db_user', :'training_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'training_db_user')
\gexec

SELECT format('ALTER ROLE %I PASSWORD %L', :'training_db_user', :'training_db_password')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'training_db_name', :'training_db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'training_db_name')
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'training_db_name', :'training_db_user')
\gexec

GRANT CONNECT ON DATABASE :"training_db_name" TO :"training_db_user";
