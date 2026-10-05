# openide_0_21_0: падения тестов после переноса

Все тесты, которые сломали наши правки, починены. На `openide_0_21_0` падают те же 7 тестов, что и на чистом upstream `0.21.0`, и к нашему коду они не относятся. Осталось вручную проверить в IDE hover, переход к определению и Find Usages.

## Итоги прогонов

Ветка отведена от upstream-тэга `0.21.0`. Наши 34 коммита перенесены из `openide_0_19_4` через cherry-pick, версия поднята до `0.21.0.1`.

| Прогон | Всего | Упало | Из них наши |
| --- | --- | --- | --- |
| чистый `0.21.0` | 441 | 7 | 0 |
| `openide_0_21_0` сразу после переноса | 441 | 26 | 19 |
| `openide_0_21_0` после всех фиксов | 441 | 7 | 0 |

7 общих падений — это `File accessed outside allowed roots` при доступе к временным файлам macOS. Те же 19 наших тестов падали и на `openide_0_19_4`, то есть сам перенос ничего не сломал. Первый плохой коммит для каждого теста найден через `git bisect` по `0.21.0..openide_0_21_0`.

## Что было сломано и как починено

| Тесты | Причина | Фикс |
| --- | --- | --- |
| `TypeScriptFoldingRangeTest` (2), `TypeScriptCodeBlockProviderTest` (4), `TypeScriptClientSideFormatOnCloseBraceTest` (1) | `end--` из «Code Actions and Folding fixes» шагал на закрывающую скобку вложенного блока | `cd2c8b09`: шаг назад только когда скобки сбалансированы |
| `*SemanticTokensFileViewProviderTest.testDisabled` (4), `TypeScriptSelectionersTest` (2), `TypeScriptBreadcrumbsProviderTest.testDisabled`, `QuteRenameTest.testElementCannotBeRenamed`, `CssSemanticTokensFileViewProviderTest.testEnabled`, `*EnterBetweenBracesDisabled*` (3) | «Fix find usages» (`d26a5dc3`) и «Fix hover/definition» (`66c97cd9`) поменяли контракт `findElementAt` в `LSPSemanticTokensStructurelessFileViewProvider` | «Restore upstream findElementAt for structureless files», см. ниже |

### Изменение `findElementAt`

Чтобы Find Usages не подсвечивал весь TextMate-файл, `d26a5dc3` стал возвращать из `findElementAt` `null` вместо элемента на весь файл. Это сломало hover и переход к определению. `66c97cd9` починил их синтетическим элементом для слова: новый объект на каждый вызов и `null` на пробелах и пунктуации.

`findElementAt` вызывает почти каждая функция платформы, поэтому сломались и соседние:
- выделение (Extend Selection);
- сообщение «нельзя переименовать» в rename;
- breadcrumbs;
- обработка Enter между скобками, которая сравнивает элементы по ссылке;
- файлы без semantic tokens.

Решение — сужать элемент там, где это нужно, а не в общей точке:

- `LSPSemanticTokensStructurelessFileViewProvider` возвращён к версии upstream. `findElementAt` снова всегда возвращает непустой элемент, при необходимости file-level token на весь файл. Hover и переход к определению работают с ним, как в upstream: offset берётся через `getEffectiveOffset`.
- Новый метод `LSPSemanticTokensFileViewProvider.findNarrowElementAt(offset)` — `default` в интерфейсе, рядом с `findElementAt`. Он возвращает элемент, только если тот уже всего файла.
- `LSPTargetElementEvaluator` использует `findNarrowElementAt`, поэтому цель Find Usages не бывает «весь файл».
- `LSPUsagePsiElement` помечается флагом платформы `UsagePreviewPanel.DO_NOT_ADJUST_NAME_RANGE`. По этому флагу превью Find Usages подсвечивает LSP-диапазон самого usage, а не `file.findElementAt(textOffset)`.

### Ограничения

- `DO_NOT_ADJUST_NAME_RANGE` есть в 2025.3 и 2026.2, но его нет в 2024.2, а это минимальная платформа (`pluginSinceBuild=242`). Поэтому ключ ищется по имени через deprecated `Key.findKeyByName`. На 2024.2 превью Find Usages подсветит весь файл, если на месте usage нет semantic token, как в upstream.
- Тесты запускаются на 2024.2, поэтому флаг превью тестами не покрыт.

## Следующие шаги

- [ ] Вручную проверить в OpenIDE на TextMate-файле: hover, переход к определению, превью Find Usages. Лучше на сервере без semantic tokens.
- [ ] Написать тест на превью Find Usages, когда тестовая платформа будет 2025.x или новее.
