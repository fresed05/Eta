package io.github.mangi.eta.agent.model

import java.net.URI
import java.util.Locale
import org.json.JSONObject

/** 工具摘要面向用户展示，不包含敏感参数；终端命令通过独立字段提供给用户核对。 */
internal class AgentTraceFormatter {

    private val isRu: Boolean get() = Locale.getDefault().language == "ru"
    private val isZh: Boolean get() = Locale.getDefault().language == "zh"
    private fun t(zh: String, ru: String, en: String = zh): String = when {
        isRu -> ru
        isZh -> zh
        else -> en
    }

    fun summarizeArguments(toolCall: AgentModelClient.ToolCall): String =
        when (toolCall.name) {
            BROWSER_TOOL_NAME -> summarizeBrowserArguments(toolCall.argumentsJson)
            "web_search" -> t("搜索网页", "Поиск в сети", "Web Search")
            "fetch_url" -> summarizeFetchArguments(toolCall.argumentsJson)
            "open_uri" -> summarizeOpenUriArguments(toolCall.argumentsJson)
            "terminal" -> summarizeTerminalArguments(toolCall.argumentsJson)
            "run_command" -> t("执行命令 · Android", "Выполнение команды · Android", "Run command · Android")
            "write_file" -> summarizeTextLength(t("写入文件", "Запись файла", "Write file"), toolCall.argumentsJson, "content")
            "read_file" -> t("读取文件", "Чтение файла", "Read file")
            "list_directory" -> t("列出目录", "Список файлов каталога", "List directory")
            "edit_file" -> t("精确编辑文件", "Точное редактирование файла", "Edit file")
            "stat_file" -> t("查看文件信息", "Сведения о файле", "Stat file")
            "glob_files" -> t("按路径查找文件", "Поиск файлов по маске", "Find files by pattern")
            "grep_files" -> t("搜索文件内容", "Поиск текста в файлах", "Grep files")
            "inspect_app" -> t("检查应用", "Инспекция приложения", "Inspect app")
            "type_text" -> summarizeTypeText(toolCall.argumentsJson)
            "input_text" -> summarizeTextLength(t("输入文本", "Ввод текста", "Input text"), toolCall.argumentsJson, "text")
            "replace_text" -> summarizeTextLength(t("替换文本", "Замена текста", "Replace text"), toolCall.argumentsJson, "text")
            "paste_text", "set_clipboard" ->
                summarizeTextLength(t("粘贴文本", "Вставка текста", "Paste text"), toolCall.argumentsJson, "text")
            "clear_text" -> t("清空文本", "Очистка текста", "Clear text")
            "get_clipboard" -> t("读取剪贴板", "Чтение буфера обмена", "Get clipboard")
            "search_apps" -> summarizeQueryArguments(t("搜索应用", "Поиск приложений", "Search apps"), toolCall.argumentsJson)
            "launch_app" -> t("打开应用", "Запуск приложения", "Launch app")
            "get_current_context" -> t("读取当前上下文", "Получение текущего контекста", "Get context")
            "observe_screen" -> summarizeObservationArguments(toolCall.argumentsJson)
            "tap" -> summarizePointArguments(t("点击屏幕", "Нажатие на экран", "Tap screen"), toolCall.argumentsJson)
            "long_press" -> summarizePointArguments(t("长按屏幕", "Долгое нажатие", "Long press"), toolCall.argumentsJson)
            "tap_area" -> t("点击区域", "Нажатие по области", "Tap area")
            "tap_element" -> summarizeElementArguments(t("点击元素", "Нажатие на элемент", "Tap element"), toolCall.argumentsJson)
            "long_press_element" -> summarizeElementArguments(t("长按元素", "Долгое нажатие на элемент", "Long press element"), toolCall.argumentsJson)
            "swipe" -> t("滑动屏幕", "Свайп по экрану", "Swipe screen")
            "scroll" -> summarizeScrollArguments(t("滚动屏幕", "Прокрутка экрана", "Scroll screen"), toolCall.argumentsJson)
            "scroll_element" ->
                summarizeScrollArguments(t("滚动元素", "Прокрутка элемента", "Scroll element"), toolCall.argumentsJson, withIndex = true)
            "press_key" -> summarizePressKeyArguments(toolCall.argumentsJson)
            "wait" -> summarizeWaitArguments(toolCall.argumentsJson)
            "wait_for_text" -> t("等待文本出现", "Ожидание текста", "Wait for text")
            "wait_for_package" -> t("等待应用就绪", "Ожидание готовности приложения", "Wait for app")
            "open_system_panel" -> t("打开系统面板", "Открытие системной панели", "Open system panel")
            "read_image" -> t("查看图片", "Просмотр изображения", "View image")
            "todo_write", "task_manage", "tasks" -> summarizeTodoArguments(toolCall.argumentsJson)
            AgentConversationToolCatalog.READ_HISTORY -> t("读取当前会话历史", "Чтение истории беседы", "Read conversation history")
            "memory_get", "character_memory_get" -> summarizeMemoryGetArguments(toolCall.argumentsJson)
            "memory_write", "character_memory_write" -> summarizeMemoryWriteArguments(toolCall.argumentsJson)
            "skills_list" -> t("查看技能列表", "Список навыков", "Skills list")
            "skills_read" -> t("读取技能", "Чтение навыка", "Read skill")
            "skills_read_resource" -> t("读取技能资源", "Чтение ресурса навыка", "Read skill resource")
            "skills_list_curated" -> t("浏览精选技能", "Каталог навыков", "Curated skills")
            "skills_inspect_github" -> t("查看技能详情", "Инспекция навыка на GitHub", "Inspect skill on GitHub")
            "skills_install_from_github" -> t("安装技能", "Установка навыка с GitHub", "Install skill from GitHub")
            else -> {
                val label = deviceActionLabel(toolCall.name)
                    ?: AgentPhoneToolCatalog.entries.firstOrNull { it.name == toolCall.name }?.title
                    ?: io.github.mangi.eta.agent.context.PersonalSearchTools.searches.firstOrNull { it.name == toolCall.name }?.title
                    ?: when (toolCall.name) {
                        "search_notes" -> t("查询便签", "Поиск в заметках", "Search notes")
                        "search_system_memories" -> t("查询系统记忆", "Поиск в воспоминаниях", "Search system memories")
                        "read_personal_item" -> t("读取检索详情", "Чтение элемента", "Read item details")
                        "summarize_bills" -> t("汇总账单", "Сводка по счетам", "Summarize bills")
                        else -> null
                    }
                when {
                    label == null -> t("准备执行", "Выполнение", "Ready to execute")
                    toolCall.name.startsWith("search_") ->
                        summarizeQueryArguments(label, toolCall.argumentsJson)
                    else -> label
                }
            }
        }

    /** 命令以脱敏后的用户可见投影进入运行轨迹；日志仍只记录长度。 */
    fun displayCommand(toolCall: AgentModelClient.ToolCall): String? =
        if (toolCall.name == "terminal" || toolCall.name == "run_command") {
            runCatching {
                JSONObject(toolCall.argumentsJson)
                    .optString("command")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= MAX_DISPLAY_COMMAND_CHARS }
                    ?.redactDisplaySecrets()
            }.getOrNull()
        } else {
            null
        }

    private fun String.redactDisplaySecrets(): String =
        replace(SENSITIVE_ASSIGNMENT) { match ->
            "${match.groupValues[1]}=<已隐藏>"
        }
            .replace(SENSITIVE_FLAG) { match ->
                "${match.groupValues[1]}<已隐藏>"
            }
            .replace(SENSITIVE_HEADER) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}<已隐藏>"
            }

    /** 外部 URI 摘要不记录 path、query、fragment 或用户信息。 */
    fun summarizeOpenUriArguments(argumentsJson: String): String =
        runCatching {
            val raw = JSONObject(argumentsJson).optString("uri").trim()
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase()?.take(24)
            val host = uri.host?.lowercase()?.take(160)
            listOfNotNull(t("交给外部应用", "Передать приложению", "Open in external app"), scheme, host).joinToString(" · ")
        }.getOrDefault(t("交给外部应用", "Передать приложению", "Open in external app"))

    /** browser_use 摘要只暴露动作和安全提取的 host。 */
    fun summarizeBrowserArguments(argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            val action = arguments.optString("action").browserActionLabel()
            val host = safeHttpHost(arguments.optString("url"))
            listOfNotNull(action, host).joinToString(" · ")
        }.getOrElse { t("浏览器操作", "Действие в браузере", "Browser operation") }

    private fun summarizeFetchArguments(argumentsJson: String): String = runCatching {
        val arguments = JSONObject(argumentsJson)
        val label = if (arguments.has("document_id")) {
            t("继续读取网页", "Продолжение чтения страницы", "Continue reading page")
        } else {
            t("读取网页", "Чтение страницы", "Read page")
        }
        listOfNotNull(label, safeHttpHost(arguments.optString("url"))?.removePrefix("www.")).joinToString(" · ")
    }.getOrDefault(t("读取网页", "Чтение страницы", "Read page"))

    private fun summarizeTerminalArguments(argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            val action = arguments.optString("action").terminalActionLabel()
            val environment = arguments.optString("environment", "android")
                .terminalEnvironmentLabel()
            val identity = arguments.optString("identity")
                .takeIf { it == "root" || it == "user" }
            buildList {
                add(t("终端", "Терминал", "Terminal"))
                add(action)
                add(environment)
                identity?.let(::add)
                if (arguments.optBoolean("async", false)) add(t("后台", "Фон", "Background"))
            }.joinToString(" · ")
        }.getOrDefault(t("终端", "Терминал", "Terminal"))

    private fun summarizeTextLength(
        label: String,
        argumentsJson: String,
        key: String,
    ): String =
        runCatching {
            val chars = JSONObject(argumentsJson).optString(key).length
            "$label · $chars " + t("字符", "симв.", "chars")
        }.getOrDefault(label)

    /** 只展示动作与长度，不回显正文：输入内容可能是密码或私人消息。 */
    private fun summarizeTypeText(argumentsJson: String): String =
        runCatching {
            val args = JSONObject(argumentsJson)
            val chars = args.optString("text").length
            val label = when {
                chars == 0 -> t("清空文本", "Очистка текста", "Clear text")
                args.optString("mode") == "append" -> t("追加文本", "Добавление текста", "Append text")
                else -> t("输入文本", "Ввод текста", "Input text")
            }
            buildString {
                append(label)
                if (chars > 0) append(" · $chars " + t("字符", "симв.", "chars"))
                if (args.optBoolean("submit")) append(" · " + t("提交", "Отправка", "Submit"))
            }
        }.getOrDefault(t("输入文本", "Ввод текста", "Input text"))

    /** 搜索关键词是用户自己发起的查询，直接展示；仍做单行化与长度截断。 */
    private fun summarizeQueryArguments(label: String, argumentsJson: String): String =
        runCatching {
            val query = sanitizeSummaryValue(
                JSONObject(argumentsJson).optString("query"),
                MAX_QUERY_SUMMARY_CHARS,
            )
            if (query.isNotBlank()) "$label · $query" else label
        }.getOrDefault(label)

    private fun summarizePointArguments(label: String, argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            "$label · (${arguments.optInt("x")}, ${arguments.optInt("y")})"
        }.getOrDefault(label)

    private fun summarizeElementArguments(label: String, argumentsJson: String): String =
        runCatching {
            val index = JSONObject(argumentsJson).optInt("index", -1)
            if (index >= 0) "$label · #$index" else label
        }.getOrDefault(label)

    private fun summarizeScrollArguments(
        label: String,
        argumentsJson: String,
        withIndex: Boolean = false,
    ): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            buildList {
                add(label)
                if (withIndex) {
                    arguments.optInt("index", -1).takeIf { it >= 0 }?.let { add("#$it") }
                }
                arguments.optString("direction").scrollDirectionLabel()?.let(::add)
            }.joinToString(" · ")
        }.getOrDefault(label)

    private fun summarizePressKeyArguments(argumentsJson: String): String =
        runCatching {
            val button = JSONObject(argumentsJson).optString("button").pressKeyLabel()
            listOfNotNull(t("按键", "Клавиша", "Key"), button).joinToString(" · ")
        }.getOrDefault(t("按键", "Клавиша", "Key"))

    private fun summarizeWaitArguments(argumentsJson: String): String =
        runCatching {
            val durationMs = JSONObject(argumentsJson).optInt("duration_ms", 1_000)
                .coerceAtLeast(0)
            val duration = if (durationMs >= 1_000) {
                String.format(Locale.US, "%.1f", durationMs / 1_000f)
                    .trimEnd('0').trimEnd('.') + " " + t("秒", "с", "s")
            } else {
                "$durationMs " + t("毫秒", "мс", "ms")
            }
            t("等待", "Ожидание", "Wait") + " · $duration"
        }.getOrDefault(t("等待", "Ожидание", "Wait"))

    private fun summarizeObservationArguments(argumentsJson: String): String =
        runCatching {
            val options = AgentScreenObservationContract.resolve(JSONObject(argumentsJson))
            buildList {
                add(t("观察屏幕", "Снимок экрана", "Observe screen"))
                if (options.includeScreenshot) add(t("含截图", "со скриншотом", "with screenshot"))
                if (options.includeUiTree) add(t("含界面树", "с деревом UI", "with UI tree"))
            }.joinToString(" · ")
        }.getOrDefault(t("观察屏幕", "Снимок экрана", "Observe screen"))

    private fun summarizeTodoArguments(argumentsJson: String): String =
        runCatching {
            val args = JSONObject(argumentsJson)
            val array = args.optJSONArray("todos")
            if (array != null && array.length() > 0) {
                val total = array.length()
                var completed = 0
                var inProgressTitle: String? = null
                for (i in 0 until total) {
                    val item = array.optJSONObject(i) ?: continue
                    val status = item.optString("status")
                    if (status == "completed") completed++
                    if (status == "in_progress" && inProgressTitle == null) {
                        inProgressTitle = item.optString("content").ifBlank { item.optString("title") }
                    }
                }
                buildList {
                    add(t("规划任务", "Задачи", "Tasks"))
                    add("$completed/$total")
                    inProgressTitle?.takeIf { it.isNotBlank() }?.let { add(it.take(30)) }
                }.joinToString(" · ")
            } else {
                val action = args.optString("action")
                when (action) {
                    "clear" -> t("清空任务", "Очистка задач", "Clear tasks")
                    "list" -> t("查看任务列表", "Список задач", "List tasks")
                    "update" -> {
                        val id = args.optString("id")
                        val status = args.optString("status")
                        t("更新任务", "Обновление задачи", "Update task") + " #$id: $status"
                    }
                    "add" -> {
                        val content = args.optString("content").ifBlank { args.optString("title") }
                        t("添加任务", "Новая задача", "Add task") + if (content.isNotBlank()) " · ${content.take(30)}" else ""
                    }
                    else -> t("规划任务", "Задачи", "Tasks")
                }
            }
        }.getOrDefault(t("规划任务", "Задачи", "Tasks"))

    private fun summarizeTodoResult(json: JSONObject): String =
        buildList {
            val total = json.optInt("total", 0)
            val completed = json.optInt("completed", 0)
            val inProgress = json.optInt("in_progress", 0)
            add(t("任务已更新", "Задачи обновлены", "Tasks updated"))
            add("$completed/$total " + t("完成", "выполнено", "done"))
            if (inProgress > 0) add("$inProgress " + t("进行中", "в процессе", "in progress"))
        }.joinToString(" · ")

    private fun summarizeMemoryGetArguments(argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            if (arguments.optString("query").isNotBlank()) {
                t("检索记忆", "Поиск в памяти", "Search memory")
            } else {
                t("读取记忆", "Чтение памяти", "Read memory")
            }
        }.getOrDefault(t("读取记忆", "Чтение памяти", "Read memory"))

    private fun summarizeMemoryWriteArguments(argumentsJson: String): String =
        runCatching {
            val arguments = JSONObject(argumentsJson)
            val mode = when (arguments.optString("mode")) {
                "replace_range" -> t("替换片段", "Замена диапазона", "Replace range")
                "append" -> t("追加", "Добавление", "Append")
                "clear" -> t("清空", "Очистка", "Clear")
                else -> null
            }
            val content = arguments.optString("content")
            val lines = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
            buildList {
                add(t("更新记忆", "Обновление памяти", "Update memory"))
                mode?.let(::add)
                add("$lines " + t("行", "строк", "lines"))
                add("${content.toByteArray(Charsets.UTF_8).size} " + t("字节", "байт", "bytes"))
            }.joinToString(" · ")
        }.getOrDefault(t("更新记忆", "Обновление памяти", "Update memory"))

    /** 结果成败供事件与 UI 状态使用，不再依赖摘要文本里的标记。 */
    fun isSuccessResult(result: AgentModelClient.ToolResult): Boolean =
        parseResultJson(result)?.optBoolean("ok", true) ?: true

    fun summarizeResult(
        toolName: String,
        result: AgentModelClient.ToolResult,
    ): String {
        val json = parseResultJson(result)
        if (toolName == "web_search" || toolName == "fetch_url") return summarizeWebResult(toolName, json)
        // 终端 exit_code != 0 时 ok=false 但没有 code 字段，必须走专用分支保留退出码与输出
        if (toolName == "terminal" || toolName == "run_command") {
            return summarizeTerminalResult(json)
        }
        if (json?.optString("status") == "unconfirmed") return t("操作已提交，效果未确认", "Действие отправлено, результат не подтвержден", "Action submitted, unconfirmed")
        if (!isSuccessResult(result)) return summarizeFailure(json)
        return when (toolName) {
            BROWSER_TOOL_NAME -> json?.let(::summarizeBrowserResult) ?: t("浏览器操作完成", "Операция в браузере завершена", "Browser operation completed")
            "todo_write", "task_manage", "tasks" -> json?.let(::summarizeTodoResult) ?: t("完成", "Готово", "Completed")
            AgentConversationToolCatalog.READ_HISTORY -> t("已读取历史分页", "Страница истории прочитана", "History page read")
            "memory_get", "memory_write", "character_memory_get", "character_memory_write" ->
                json?.let { summarizeMemoryResult(toolName, it) } ?: t("完成", "Готово", "Completed")
            "search_apps" -> json?.let(::summarizeSearchAppsResult) ?: t("完成", "Готово", "Completed")
            "launch_app" -> json?.let(::summarizeLaunchAppResult) ?: t("已打开", "Открыто", "Opened")
            else -> json?.let { summarizeGenericResult(it, result) } ?: t("完成", "Готово", "Completed")
        }
    }

    private fun parseResultJson(result: AgentModelClient.ToolResult): JSONObject? =
        runCatching { JSONObject(result.content) }.getOrNull()

    /**
     * 网页错误正文、搜索词和页面标题可能回显私密 URL，只保留计数。动作与主机已在参数摘要中，
     * 结果只给出结论，避免工作过程同一行重复。
     */
    private fun summarizeWebResult(toolName: String, json: JSONObject?): String {
        val label = if (toolName == "web_search") t("网页搜索", "Поиск в сети", "Web Search") else t("网页读取", "Чтение страницы", "Fetch URL")
        if (json == null) return t("完成", "Готово", "Done")
        if (!json.optBoolean("ok", true)) {
            val code = json.optString("code").takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,79}")) }
            return listOfNotNull("${label} " + t("失败", "ошибка", "failed"), code?.let { "code=$it" }).joinToString(" · ")
        }
        if (toolName == "web_search") {
            val count = json.optJSONArray("results")?.length() ?: json.firstNonNegativeInt("count")
            return when (count) {
                null -> t("完成", "Готово", "Done")
                0 -> t("没有找到结果", "Ничего не найдено", "No results found")
                else -> "$count " + t("条结果", "результатов", "results")
            }
        }
        val chars = (json.opt("text") as? String)?.length
            ?: json.firstNonNegativeInt("returned_chars", "text_chars")
            ?: return t("完成", "Готово", "Done")
        return if (json.optBoolean("has_more")) {
            t("已读前 ", "Прочитано первые ", "Read first ") + formatCharCount(chars)
        } else {
            formatCharCount(chars)
        }
    }

    /** 失败摘要保留 code= 标记，供运行日志提取稳定错误码；message 是工具侧给出的中文原因。 */
    private fun summarizeFailure(json: JSONObject?): String {
        val code = json?.optString("code")?.takeIf { it.isNotBlank() }
        val reason = json?.optString("message")
            ?.let(::sanitizeSummaryValue)
            ?.takeIf { it.isNotBlank() }
        return buildList {
            add(t("失败", "Ошибка", "Failed"))
            reason?.let(::add)
            code?.let { add("code=$it") }
        }.joinToString(" · ")
    }

    private fun summarizeMemoryResult(toolName: String, json: JSONObject): String =
        buildList {
            add(if (toolName.endsWith("memory_get")) {
                t("已读取记忆", "Память прочитана", "Memory read")
            } else {
                t("已更新记忆", "Память обновлена", "Memory updated")
            })
            if (json.has("line_count")) add("${json.optInt("line_count")} " + t("行", "строк", "lines"))
            if (json.has("bytes")) add("${json.optInt("bytes")} " + t("字节", "байт", "bytes"))
        }.joinToString(" · ")

    private fun summarizeGenericResult(
        json: JSONObject,
        result: AgentModelClient.ToolResult,
    ): String =
        buildList {
            add(if (json.has("verified") && !json.optBoolean("verified")) {
                t("已执行，效果未确认", "Выполнено, результат не подтвержден", "Executed, unconfirmed")
            } else {
                t("完成", "Готово", "Completed")
            })
            json.optJSONArray("entries")?.let { add("${it.length()} " + t("个条目", "записей", "entries")) }
            json.optJSONArray("matches")?.let { add("${it.length()} " + t("个匹配", "совпадений", "matches")) }
            if (json.has("bytes_read")) add(t("已读", "Прочитано", "Read") + " ${json.optLong("bytes_read")} " + t("字节", "байт", "bytes"))
            if (json.has("replacements")) add(t("替换", "Замен", "Replaced") + " ${json.optInt("replacements")} " + t("处", "", ""))
            if (json.optBoolean("truncated") || json.optBoolean("has_more")) add(t("可继续读取", "Есть продолжение", "More data available"))
            if (json.optBoolean("partial") || json.optBoolean("scan_limited")) add(t("采集范围有限", "Ограниченный охват", "Scan limited"))
            json.optJSONArray("apps")?.let { add(t("找到", "Найдено", "Found") + " ${it.length()} " + t("个应用", "приложений", "apps")) }
            json.optJSONArray("candidates")?.let { add("${it.length()} " + t("个候选", "кандидатов", "candidates")) }
            if (result.images.isNotEmpty()) add("${result.images.size} " + t("张图片", "изображений", "images"))
        }.joinToString(" · ")

    private fun summarizeSearchAppsResult(json: JSONObject): String {
        val notFound = t("未找到匹配应用", "Приложения не найдены", "No matching apps found")
        val apps = json.optJSONArray("apps") ?: return notFound
        val total = apps.length()
        if (total == 0) return notFound
        val names = (0 until total).mapNotNull { index ->
            apps.optJSONObject(index)?.optString("app_name")
                ?.let(::sanitizeSummaryValue)
                ?.takeIf { it.isNotBlank() }
        }
        return buildString {
            append(t("已找到 $total 个应用", "Найдено приложений: $total", "Found $total apps"))
            val shown = names.take(MAX_LISTED_APP_NAMES)
            if (shown.isNotEmpty()) {
                append(" · ").append(shown.joinToString("、"))
                if (total > shown.size) append(" " + t("等", "и др.", "etc."))
            }
        }
    }

    private fun summarizeLaunchAppResult(json: JSONObject): String {
        val appName = sanitizeSummaryValue(json.optString("app_name"))
        val prefix = t("已打开", "Открыто", "Opened")
        return if (appName.isNotBlank()) "$prefix · $appName" else prefix
    }

    /**
     * 终端结果面向用户展示退出状态与输出预览；输出可能很长，
     * 只保留开头几行，截断时追加省略标记。
     */
    private fun summarizeTerminalResult(json: JSONObject?): String {
        if (json == null) return t("终端", "Терминал", "Terminal")
        if (json.optString("code").isNotBlank()) return summarizeFailure(json)
        if (!json.has("exit_code") || json.isNull("exit_code")) {
            val action = json.optString("action").terminalActionLabel()
            return if (json.optBoolean("ok", true)) {
                t("终端", "Терминал", "Terminal") + " · $action"
            } else {
                t("失败", "Ошибка", "Failed") + " · $action"
            }
        }
        val exitCode = json.optInt("exit_code")
        val timedOut = json.optBoolean("timed_out", false)
        val status = when {
            timedOut -> t("失败 · 执行超时", "Ошибка · Превышено время ожидания", "Failed · Timed out")
            exitCode == 0 -> t("执行完成", "Выполнено", "Completed")
            else -> t("失败 · 退出码 $exitCode", "Ошибка · Код завершения $exitCode", "Failed · Exit code $exitCode")
        }
        val output = if (exitCode == 0) {
            json.optString("stdout")
        } else {
            json.optString("stderr").ifBlank { json.optString("stdout") }
        }
        val truncated = json.optBoolean("stdout_truncated", false) ||
            json.optBoolean("stderr_truncated", false)
        val preview = terminalOutputPreview(output, truncated) ?: return status
        return "$status\n$preview"
    }

    private fun terminalOutputPreview(output: String, truncated: Boolean): String? {
        val normalized = output.trim()
        if (normalized.isEmpty()) return null
        val allLines = normalized.lines()
        var preview = allLines.take(MAX_TERMINAL_PREVIEW_LINES).joinToString("\n")
        var capped = allLines.size > MAX_TERMINAL_PREVIEW_LINES || truncated
        if (preview.length > MAX_TERMINAL_PREVIEW_CHARS) {
            preview = preview.take(MAX_TERMINAL_PREVIEW_CHARS)
            capped = true
        }
        return if (capped) "$preview…" else preview
    }

    /**
     * 浏览器结果面向用户展示关键结论，不回显完整 HTML 或冗长内容。
     * 标题只在有实质变化时暴露，减少噪声。
     */
    private fun summarizeBrowserResult(json: JSONObject): String {
        val page = json.optJSONObject("page_info")
            ?: json.optJSONObject("pageInfo")
        val action = json.optString("action")
            .takeIf { it in BROWSER_ACTIONS }
            ?: "unknown"
        val host = sequenceOf(json, page)
            .filterNotNull()
            .flatMap { source ->
                sequenceOf("url", "current_url", "currentUrl", "final_url", "finalUrl")
                    .map(source::optString)
            }
            .mapNotNull(::safeHttpHost)
            .firstOrNull()
        val title = sequenceOf(json, page)
            .filterNotNull()
            .map { it.opt("title") }
            .filterIsInstance<String>()
            .map(::sanitizeSummaryValue)
            .firstOrNull { it.isNotBlank() }
        val textChars = sequenceOf(json, page)
            .filterNotNull()
            .mapNotNull { source ->
                source.firstNonNegativeInt("text_length", "textLength", "text_chars", "textChars")
            }
            .firstOrNull()
            ?: sequenceOf(json, page)
                .filterNotNull()
                .flatMap { source -> sequenceOf("text", "readable", "content").map(source::opt) }
                .filterIsInstance<String>()
                .map(String::length)
                .firstOrNull()
        val elementCount = json.firstNonNegativeInt("element_count", "elementCount", "elements_count")
            ?: json.optJSONArray("elements")?.length()

        return buildList {
            add(action.browserSuccessLabel())
            host?.let(::add)
            title?.let { add("«$it»") }
            if (action in BROWSER_TEXT_ACTIONS) {
                textChars?.let { add(t("约 ", "ок. ", "approx. ") + formatCharCount(it)) }
            }
            elementCount?.let { add("$it " + t("个元素", "элементов", "elements")) }
            if (json.optBoolean("truncated", false)) add(t("已截断", "обрезано", "truncated"))
        }.joinToString(" · ")
    }

    private fun formatCharCount(chars: Int): String =
        if (chars >= 10_000) {
            if (isZh) {
                String.format(Locale.US, "%.1f", chars / 10_000f).trimEnd('0').trimEnd('.') + " 万字"
            } else {
                String.format(Locale.US, "%.1f", chars / 1_000f).trimEnd('0').trimEnd('.') + "k " + t("字", "симв.", "chars")
            }
        } else {
            "$chars " + t("字", "симв.", "chars")
        }

    private fun JSONObject.firstNonNegativeInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            if (!has(key)) return@firstNotNullOfOrNull null
            optInt(key, -1).takeIf { it >= 0 }
        }

    private fun sanitizeSummaryValue(value: String, maxChars: Int = 80): String =
        value.replace(Regex("\\s+"), " ")
            .trim()
            .replace(',', '，')
            .replace('=', '＝')
            .let { if (it.length <= maxChars) it else it.take(maxChars) + "..." }

    private fun safeHttpHost(rawUrl: String): String? =
        rawUrl.trim()
            .takeIf(String::isNotEmpty)
            ?.let { value ->
                runCatching {
                    val uri = URI(value)
                    uri.host
                        ?.takeIf {
                            uri.scheme.equals("http", ignoreCase = true) ||
                                uri.scheme.equals("https", ignoreCase = true)
                        }
                        ?.lowercase()
                        ?.take(160)
                }.getOrNull()
            }

    private fun String.browserActionLabel(): String = when (this) {
        "navigate" -> t("打开网页", "Переход по ссылке", "Navigate")
        "get_readable" -> t("提取正文", "Чтение содержимого", "Extract readable")
        "get_text" -> t("读取文本", "Чтение текста", "Get text")
        "find_elements" -> t("查找元素", "Поиск элементов", "Find elements")
        "click" -> t("点击网页", "Клик на странице", "Click")
        "type" -> t("输入内容", "Ввод текста", "Type text")
        "scroll" -> t("滚动网页", "Прокрутка страницы", "Scroll")
        "screenshot" -> t("网页截图", "Скриншот страницы", "Screenshot")
        "get_page_info" -> t("查看网页信息", "Информация о странице", "Page info")
        "go_back" -> t("网页后退", "Назад", "Go back")
        "go_forward" -> t("网页前进", "Вперед", "Go forward")
        "reload" -> t("刷新网页", "Обновить", "Reload")
        "wait_for_selector" -> t("等待网页元素", "Ожидание элемента", "Wait for selector")
        else -> t("浏览器操作", "Действие в браузере", "Browser action")
    }

    private fun String.browserSuccessLabel(): String = when (this) {
        "navigate" -> t("已打开", "Открыто", "Opened")
        "get_readable" -> t("已提取正文", "Содержимое извлечено", "Extracted")
        "get_text" -> t("已读取文本", "Текст прочитан", "Text read")
        "find_elements" -> t("已找到元素", "Элементы найдены", "Elements found")
        "click" -> t("已点击网页", "Клик выполнен", "Clicked")
        "type" -> t("已输入内容", "Ввод выполнен", "Typed")
        "scroll" -> t("已滚动网页", "Прокрутка выполнена", "Scrolled")
        "screenshot" -> t("已截图", "Скриншот сделан", "Captured")
        "get_page_info" -> t("已读取页面信息", "Информация получена", "Page info read")
        "go_back" -> t("已后退", "Выполнен переход назад", "Went back")
        "go_forward" -> t("已前进", "Выполнен переход вперед", "Went forward")
        "reload" -> t("已刷新", "Страница обновлена", "Reloaded")
        "wait_for_selector" -> t("已等到目标元素", "Элемент обнаружен", "Element ready")
        else -> t("浏览器操作完成", "Операция в браузере завершена", "Browser operation completed")
    }

    private fun String.scrollDirectionLabel(): String? = when (lowercase(Locale.US)) {
        "up" -> t("向上", "вверх", "up")
        "down" -> t("向下", "вниз", "down")
        "left" -> t("向左", "влево", "left")
        "right" -> t("向右", "вправо", "right")
        else -> null
    }

    private fun String.pressKeyLabel(): String? = when (lowercase(Locale.US)) {
        "back" -> t("返回", "Назад", "Back")
        "home" -> t("主页", "Домой", "Home")
        "recents", "recent" -> t("最近任务", "Недавние", "Recents")
        "notifications" -> t("通知栏", "Уведомления", "Notifications")
        "quick_settings" -> t("控制中心", "Быстрые настройки", "Quick settings")
        "power" -> t("电源", "Питание", "Power")
        "volume_up" -> t("音量加", "Громкость +", "Volume up")
        "volume_down" -> t("音量减", "Громкость -", "Volume down")
        "mute" -> t("静音", "Без звука", "Mute")
        else -> null
    }

    private fun String.terminalActionLabel(): String = when (this) {
        "open" -> t("创建会话", "Создание сессии", "Open session")
        "exec" -> t("执行命令", "Выполнение команды", "Execute command")
        "open_and_exec" -> t("单次执行", "Разовое выполнение", "Exec once")
        "read_async_result" -> t("读取后台输出", "Чтение фонового вывода", "Read async result")
        "close" -> t("关闭终端", "Закрытие терминала", "Close terminal")
        "daemon_start" -> t("启动守护任务", "Запуск демона", "Start daemon")
        "daemon_list" -> t("守护任务列表", "Список демонов", "Daemon list")
        "daemon_logs" -> t("查看守护日志", "Логи демона", "Daemon logs")
        "daemon_stop" -> t("停止守护任务", "Остановка демона", "Stop daemon")
        else -> t("终端操作", "Операция терминала", "Terminal operation")
    }

    private fun String.terminalEnvironmentLabel(): String = when (this) {
        "linux" -> "Linux"
        "alpine" -> "Alpine"
        "debian" -> "Debian"
        else -> "Android"
    }

    private fun deviceActionLabel(name: String): String? = when (name) {
        "set_alarm" -> t("设置闹钟", "Установка будильника", "Set alarm")
        "set_timer" -> t("设置计时器", "Установка таймера", "Set timer")
        "device_status" -> t("查看设备状态", "Состояние устройства", "Device status")
        "network_info" -> t("查看网络信息", "Сведения о сети", "Network info")
        "top_memory_apps" -> t("查看内存占用排行", "Топ приложений по памяти", "Top memory apps")
        "top_storage_apps" -> t("查看存储占用排行", "Топ приложений по хранилищу", "Top storage apps")
        "media_control" -> t("控制媒体播放", "Управление медиа", "Media control")
        "set_volume" -> t("调整音量", "Настройка громкости", "Set volume")
        "get_setting" -> t("读取系统设置", "Чтение системных настроек", "Get system setting")
        "wifi_credentials" -> t("读取 Wi-Fi 密码", "Пароль Wi-Fi", "Wi-Fi credentials")
        "recent_notifications" -> t("读取最近通知", "Недавние уведомления", "Recent notifications")
        "search_notification_history" -> t("搜索通知历史", "Поиск в истории уведомлений", "Search notifications")
        "recent_app_activity" -> t("查看应用活动", "Недавняя активность приложений", "Recent app activity")
        "app_usage_summary" -> t("查看应用使用统计", "Статистика использования приложений", "App usage summary")
        "get_current_location" -> t("获取当前位置", "Текущее местоположение", "Current location")
        "get_device_environment" -> t("查看设备环境", "Среда устройства", "Device environment")
        "list_alarms" -> t("查看闹钟列表", "Список будильников", "List alarms")
        "list_active_timers" -> t("查看计时器", "Активные таймеры", "Active timers")
        "search_clipboard_history" -> t("搜索剪贴板历史", "Поиск в буфере обмена", "Search clipboard history")
        "get_health_summary" -> t("查看健康摘要", "Сводка здоровья", "Health summary")
        "read_sms_code" -> t("读取短信验证码", "Чтение кода из SMS", "Read SMS code")
        "get_logcat" -> t("读取系统日志", "Системный журнал (logcat)", "Get logcat")
        "search_media" -> t("搜索媒体文件", "Поиск медиафайлов", "Search media")
        "search_audio" -> t("搜索音频", "Поиск аудио", "Search audio")
        "search_recordings" -> t("搜索录音", "Поиск записей диктофона", "Search recordings")
        "search_files" -> t("搜索文件", "Поиск файлов", "Search files")
        "search_calendar_events" -> t("搜索日程", "Поиск событий календаря", "Search calendar")
        "search_contacts" -> t("搜索联系人", "Поиск контактов", "Search contacts")
        "search_call_history" -> t("搜索通话记录", "Поиск в истории вызовов", "Search call history")
        "search_messages" -> t("搜索短信", "Поиск сообщений", "Search messages")
        "search_downloads" -> t("搜索下载内容", "Поиск в загрузках", "Search downloads")
        "search_coloros_notes" -> t("搜索便签", "Поиск заметок", "Search notes")
        "search_coloros_recordings" -> t("搜索录音机", "Поиск записей диктофона", "Search recordings")
        "search_recording_summaries" -> t("搜索录音摘要", "Поиск резюме записей", "Search recording summaries")
        "search_coloros_memories" -> t("搜索小布记忆", "Поиск воспоминаний", "Search memories")
        "search_saved_places" -> t("搜索收藏地点", "Поиск сохраненных мест", "Search saved places")
        "search_personal_orders" -> t("搜索个人订单", "Поиск заказов", "Search orders")
        "search_qq_chat_images" -> t("搜索 QQ 聊天图片", "Поиск изображений в QQ", "Search QQ images")
        "search_wechat_chat_images" -> t("搜索微信聊天图片", "Поиск изображений в WeChat", "Search WeChat images")
        "set_setting" -> t("修改系统设置", "Изменение системной настройки", "Set system setting")
        "set_device_state" -> t("修改设备状态", "Изменение состояния устройства", "Set device state")
        "app_state_control" -> t("管理应用状态", "Управление состоянием приложения", "App state control")
        else -> null
    }

    private companion object {
        const val BROWSER_TOOL_NAME = "browser_use"
        const val MAX_DISPLAY_COMMAND_CHARS = 4_000
        const val MAX_QUERY_SUMMARY_CHARS = 30
        const val MAX_LISTED_APP_NAMES = 3
        const val MAX_TERMINAL_PREVIEW_LINES = 3
        const val MAX_TERMINAL_PREVIEW_CHARS = 240
        val SENSITIVE_ASSIGNMENT = Regex(
            """(?i)\b([A-Z0-9_]*(?:API[_-]?KEY|ACCESS[_-]?TOKEN|AUTH[_-]?TOKEN|TOKEN|PASSWORD|PASSWD|SECRET)[A-Z0-9_]*)\s*=\s*(?:"[^"]*"|'[^']*'|[^\s;&|]+)"""
        )
        val SENSITIVE_FLAG = Regex(
            """(?i)(--?(?:password|passwd|token|api[-_]?key|secret)(?:\s*=\s*|\s+))(?:"[^"]*"|'[^']*'|[^\s;&|]+)"""
        )
        val SENSITIVE_HEADER = Regex(
            """(?i)\b(Authorization|Proxy-Authorization|X-Api-Key)(\s*:\s*)[^'"\r\n;&|]+"""
        )
        val BROWSER_ACTIONS = setOf(
            "navigate",
            "get_readable",
            "get_text",
            "find_elements",
            "click",
            "type",
            "scroll",
            "screenshot",
            "get_page_info",
            "go_back",
            "go_forward",
            "reload",
            "wait_for_selector",
        )
        val BROWSER_TEXT_ACTIONS = setOf("get_readable", "get_text")
    }
}
