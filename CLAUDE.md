# Описание проекта
- [Module: Palette](./claude/project-overview.md) - высокоуровневое описание проекта

# Используемые технологии
1) clojurescript
2) re-frame
3) react
4) cypress for e2e tests

# Комманды
1) для проверки e2e тестов используй
`npm run watch` но только один раз. в следующие разы пропускай так как сервер уже запущен.
npm run cypress:run
после команды watch нужно подождать 10 секунд

# правила для e2e тестов
1) всегда добавляй data-testid
2) добавляй assert messages если проверка не одна