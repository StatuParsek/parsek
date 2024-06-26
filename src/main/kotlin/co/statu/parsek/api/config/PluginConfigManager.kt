package co.statu.parsek.api.config

import co.statu.parsek.api.ParsekPlugin
import co.statu.parsek.config.ConfigManager
import com.google.gson.Gson
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PluginConfigManager<T : PluginConfig>(
    private val configManager: ConfigManager,
    plugin: ParsekPlugin,
    private val pluginConfigClass: Class<T>,
    private val migrations: List<PluginConfigMigration> = listOf(),
    private val exPluginIds: List<String> = listOf()
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private val gson = Gson()
    }

    private val pluginId = plugin.pluginId
    private var isMigrated = false

    private var configAsJsonObject = configManager.getConfig()
        .getJsonObject("plugins")
        .getJsonObject(pluginId)
        ?: JsonObject()

    var config: T = gson.fromJson(configAsJsonObject.toString(), pluginConfigClass)
        private set

    init {
        logger.info("Checking available config migrations")

        initialize()
        migrate()
    }

    fun saveConfig(configAsJsonObject: JsonObject = JsonObject(gson.toJson(config))) {
        configManager.getConfig().getJsonObject("plugins").put(pluginId, configAsJsonObject)

        configManager.saveConfig()

        this.configAsJsonObject = configAsJsonObject

        config = gson.fromJson(configAsJsonObject.toString(), pluginConfigClass)
    }

    private fun initialize() {
        if (configManager.getConfig()
                .getJsonObject("plugins")
                .getJsonObject(pluginId) == null
        ) {
            val pluginConfigs = configManager.getConfig().getJsonObject("plugins")

            val config = JsonObject(gson.toJson(config))

            if (exPluginIds.isNotEmpty()) {
                val foundExId = exPluginIds.reversed().firstOrNull {
                    pluginConfigs.getJsonObject(it) != null
                }

                if (foundExId != null) {
                    val currentConfigVersion = if (migrations.isNotEmpty()) {
                        migrations.maxBy { it.VERSION }.VERSION
                    } else {
                        1
                    }

                    val exConfig = pluginConfigs.getJsonObject(foundExId)

                    var shouldMigrate = false

                    if (exConfig.getInteger("version") == currentConfigVersion) {
                        val keysAreSame = config.map.keys.none { !exConfig.map.containsKey(it) }

                        if (keysAreSame) {
                            shouldMigrate = true
                        }
                    } else {
                        shouldMigrate = true
                    }

                    if (shouldMigrate) {
                        pluginConfigs.remove(foundExId)

                        saveConfig(exConfig)
                    }

                    return
                }
            }

            logger.warn("Couldn't find config for \"${pluginId}\". Saving default config")

            if (migrations.isNotEmpty()) {
                val highestVersion = migrations.maxBy { it.VERSION }.VERSION

                config.put("version", highestVersion)
            }

            saveConfig(config)
        }
    }

    private fun migrate(configVersion: Int = config.version, saveConfig: Boolean = true) {
        migrations
            .find { configMigration -> configMigration.isMigratable(configVersion) }
            ?.let { migration ->
                logger.info("Migration Found! Migrating config from version ${migration.FROM_VERSION} to ${migration.VERSION}: ${migration.VERSION_INFO}")

                configAsJsonObject.put("version", migration.VERSION)

                migration.migrate(configAsJsonObject)
                migration.migrateFully(configManager.getConfig())

                migrate(migration.VERSION, false)
                isMigrated = true
            }

        if (saveConfig && isMigrated) {
            saveConfig(configAsJsonObject)

            isMigrated = false
        }
    }
}