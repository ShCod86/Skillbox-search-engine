# Поисковая система для веб-сайтов


## Описание проекта

Полнофункциональная поисковая система с возможностью индексации веб-сайтов и поиска по проиндексированному контенту.

1. **Модуль индексации** - сканирует сайты и сохраняет их контент в базу данных

2. **Модуль поиска** - обрабатывает поисковые запросы и выдает релевантные результаты

3. **API** - предоставляет REST интерфейс для управления и поиска

##  Ключевые возможности

- Индексация одного или нескольких сайтов одновременно
- Поиск по всем проиндексированным сайтам или конкретному сайту
- Подсветка найденных слов в результатах поиска
- Статистика по индексации и поиску

## Технологический стек

<div style="display: flex; gap 10px">
    <img src=".logo/java-svgrepo-com.svg" width="50">
    <img src=".logo/spring-svgrepo-com.svg" width="50">
    <img src=".logo/hibernate-svgrepo-com.svg" width="50">
    <img src=".logo/postgresql-logo-svgrepo-com.svg" width="50">
    <img src=".logo/docker-svgrepo-com.svg" width="50">
</div>

### Бэкенд

- **Java 17** - основной язык разработки
- **Spring Boot** - фреймворк для создания приложения
- **Hibernate** - ORM для работы с базой данных
- **Jsoup** - парсинг HTML контента
- **LuceneMorphology** - морфологический анализ слов

### База данных
- **PostgreSQL 15** - основное хранилище данных (в Docker-контейнере)
- **Liquibase** - управление миграциями базы данных

### Фронтенд
- **Thymeleaf** - шаблонизатор для веб-интерфейса
- **Bootstrap** - CSS фреймворк для стилизации

### Инфраструктура
- **Docker** - контейнеризация сервисов
- **Docker Compose** - оркестрация контейнеров

## Установка и запуск

### Требования
- Java 17+
- Docker 20.10+
- Docker Compose 2.0+

### Быстрый запуск с Docker Compose

1. Клонируйте репозиторий:

    ```bash
    git clone https://github.com/ShCod86/Skillbox-search-engine.git
    cd search-engine
    ```
2. Запустите сервисы
    ```bash
    docker-compose up -d
    ```
3. Приложение будет доступно по адресу: [http://localhost:8080](http://localhost:8080)

### Конфигурация

Настройте сайты для индексации в файле application.yml:
```yaml
  indexing-settings:
    sites:
      - url: https://example.com
        name: Example Site
      - url: https://another-site.com
        name: Another Site
```

## API Endpoints

### Индексация

- GET /api/startIndexing - запуск индексации всех сайтов
- GET /api/stopIndexing - остановка индексации
- POST /api/indexPage - индексация конкретной страницы

### Поиск

- GET /api/search?query=... - поиск по всем сайтам
- GET /api/search?query=...&site=url - поиск по конкретному сайту

### Статистика

- GET /api/statistics - статистика по индексации

