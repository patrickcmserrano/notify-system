-- Arquivo de inicialização do banco de dados notifications
-- Criação de extensões úteis
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Configurações iniciais
SET timezone = 'UTC';

-- Comentário de confirmação
SELECT 'Database notifications initialized successfully' as status;
