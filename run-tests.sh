#!/bin/bash

# Script para executar testes do sistema de notificações

echo "🧪 Iniciando testes do sistema de notificações..."

# Verificar se o PostgreSQL está rodando
if ! sudo docker compose ps | grep -q "notify-postgres.*Up"; then
    echo "STARTING PostgreSQL..."
    sudo docker compose up -d
    sleep 3
fi

echo "PostgreSQL rodando"

# Executar testes
echo "🧪 Executando testes..."
clj -M:test

echo "Testes concluídos"
