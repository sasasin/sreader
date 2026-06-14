CREATE DATABASE IF NOT EXISTS sreader
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS sreadertest
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'sreader'@'%' IDENTIFIED BY 'sreader';
CREATE USER IF NOT EXISTS 'sreadertest'@'%' IDENTIFIED BY 'sreadertest';

GRANT ALL PRIVILEGES ON sreader.* TO 'sreader'@'%';
GRANT ALL PRIVILEGES ON sreadertest.* TO 'sreadertest'@'%';
