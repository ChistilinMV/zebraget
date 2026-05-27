# Руководство по развертыванию (деплою) сервера Zebraget

Данный мануал описывает процесс запуска серверной части (`web` папка) на серверах под управлением **Linux** (Ubuntu/Debian) и **Windows**. В качестве менеджера процессов мы будем использовать `PM2`, чтобы сервер работал в фоновом режиме и автоматически перезапускался при падении или перезагрузке системы.

---

## 📦 Подготовка (Общее для обеих ОС)

Перед началом вам нужно перенести файлы проекта на сервер. Скопируйте содержимое папки `web` на ваш сервер (например, в `/opt/zebraget-web` для Linux или `C:\zebraget-web` для Windows).

**Что нужно скопировать:**

- `package.json`
- `package-lock.json`
- `server-express.js`
- `db.json` (или пустой файл, он создастся сам)
- `users.json` (файл с учетными данными пользователей)
- Папка `public/` (с `admin.html`)

---

## 🐧 Развертывание на Linux (Ubuntu/Debian)

### 1. Установка Node.js и npm

Если Node.js еще не установлен, выполните команды:

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
```

### 2. Установка зависимостей проекта

Перейдите в папку с проектом и установите пакеты:

```bash
cd /opt/zebraget-web
npm install
```

Так как мы копируем файлы package.json и package-lock.json, команда npm install, запущенная на самом сервере, автоматически скачает и установит в новую папку node_modules абсолютно все необходимые зависимости правильных версий и скомпилирует их под операционную систему вашего сервера. Никаких дополнительных модулей вручную ставить не придется.

### 3. Установка PM2

Установите менеджер процессов PM2 глобально:

```bash
sudo npm install pm2 -g
```

### 4. Запуск сервера через PM2

Запустите `server-express.js` через PM2:

```bash
pm2 start server-express.js --name "zebraget-api"
```

### 5. Настройка автозапуска

Чтобы сервер автоматически стартовал при перезагрузке машины, выполните:

```bash
pm2 startup
```

Вам выдаст команду (начинающуюся с `sudo env PATH...`), которую нужно скопировать и выполнить в терминале. После этого сохраните текущий список процессов:

```bash
pm2 save
```

> [!TIP]
> **Открытие порта (Firewall):** Если у вас включен UFW, не забудьте открыть порт 3000: `sudo ufw allow 3000/tcp`

---

## 🪟 Развертывание на Windows (Windows Server / 10 / 11)

### 1. Установка Node.js

Скачайте и установите LTS версию Node.js с официального сайта: [nodejs.org](https://nodejs.org/). Убедитесь, что в установщике стоит галочка "Add to PATH".

### 2. Установка зависимостей проекта

Откройте PowerShell от имени Администратора, перейдите в папку с проектом и установите пакеты:

```powershell
cd C:\zebraget-web
npm install
```

### 3. Установка PM2 и настройка Windows Service

Установите PM2 и специальный пакет `pm2-windows-service` для создания службы:

```powershell
npm install pm2 -g
npm install pm2-windows-startup -g
pm2-startup install
```

### 4. Запуск сервера через PM2

Запустите сервер и сохраните конфигурацию:

```powershell
pm2 start server-express.js --name "zebraget-api"
pm2 save
```

Теперь сервер работает в фоне и переживет перезагрузку Windows.

> [!IMPORTANT]
> **Открытие порта в Брандмауэре Windows:** По умолчанию Windows блокирует входящие подключения к нестандартным портам.
> 1. Откройте PowerShell от имени Администратора.
> 2. Выполните команду: `New-NetFirewallRule -DisplayName "Zebraget API Port 3000" -Direction Inbound -LocalPort 3000 -Protocol TCP -Action Allow`

---

## ⚙️ Полезные команды PM2 (Управление сервером)

Для управления сервером вы можете использовать следующие команды (работают и в Linux, и в Windows PowerShell):

- `pm2 status` — Посмотреть список запущенных приложений и их статус.
- `pm2 logs zebraget-api` — Посмотреть логи сервера (запросы, ошибки).
- `pm2 restart zebraget-api` — Перезагрузить сервер.
- `pm2 stop zebraget-api` — Остановить сервер.

---

## 🔒 Дополнительно (Reverse Proxy)

В продакшене рекомендуется не светить наружу порт `3000`, а использовать Nginx или IIS в качестве Reverse Proxy (чтобы сервер был доступен по стандартному порту `80` или `443` с SSL-сертификатом).

**Пример минимального конфига для Nginx (Linux):**

```nginx

server {

    listen 80;

    server_name your-domain.com;

    location / {

        proxy_pass http://localhost:3000;

        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;

        proxy_set_header Connection 'upgrade';

        proxy_set_header Host $host;

        proxy_cache_bypass $http_upgrade;

    }

}
```
