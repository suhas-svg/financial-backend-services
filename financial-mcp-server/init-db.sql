-- Initialize databases for financial services

-- Create account service database
CREATE DATABASE IF NOT EXISTS myfirstdb;

-- Create transaction service database  
CREATE DATABASE IF NOT EXISTS transactiondb;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE myfirstdb TO postgres;
GRANT ALL PRIVILEGES ON DATABASE transactiondb TO postgres;