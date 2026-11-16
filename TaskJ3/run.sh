#!/bin/bash
set -e
cd "$(dirname "$0")"

export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

if lsof -tiTCP:8080 -sTCP:LISTEN > /dev/null; then
    echo 'Порт 8080 занят. Останови старый сервер через Ctrl+C и повтори запуск.' >&2
    exit 1
fi

echo 'Сборка, тесты и отчёт покрытия...'
JAVA_OPTS="${JAVA_OPTS:-} --enable-native-access=ALL-UNNAMED" ./gradlew --quiet test installDist

echo 'Запуск сервера AnnaNerlikh...'
java -jar Task_J3-server.jar AnnaNerlikh > build/server.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT
trap 'exit 130' INT

sleep 3
kill -0 "$server_pid" 2>/dev/null || { cat build/server.log >&2; exit 1; }
echo 'Обход сервера — подожди около 1–2 минут.'
build/install/TaskJ3/bin/TaskJ3 > build/messages.txt

echo 'Сообщения: build/messages.txt'
echo 'Покрытие: build/reports/jacoco/test/html/index.html'
