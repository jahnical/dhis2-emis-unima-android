package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.databind.ObjectMapper
import org.saudigitus.emis.utils.Mapper
import timber.log.Timber

@PublishedApi
internal fun normalizeDataStoreJson(json: String): String {
    val wrapperPrefix = "JsonWrapper(json="
    return if (json.startsWith(wrapperPrefix) && json.endsWith(")")) {
        json.substring(wrapperPrefix.length, json.length - 1)
    } else {
        json
    }
}

open class EMISConfig {
    private fun toJson(): String = Mapper.translateJsonToObject().writeValueAsString(this)

    companion object {
        open fun fromJson(json: String?): List<EMISConfigItem>? = if (!json.isNullOrBlank()) {
            val mapper = ObjectMapper()
            val normalizedJson = normalizeDataStoreJson(json)

            try {
                Mapper.translateJsonToObject()
                    .readValue(
                        normalizedJson,
                        mapper.typeFactory.constructCollectionType(
                            List::class.java,
                            EMISConfigItem::class.java,
                        ),
                    )
            } catch (ex: Exception) {
                try {
                    val singleItem = Mapper.translateJsonToObject().readValue(normalizedJson, EMISConfigItem::class.java)
                    listOfNotNull(singleItem)
                } catch (ex2: Exception) {
                    Timber.e(ex2, "Failed to parse EMISConfig")
                    null
                }
            }
        } else {
            null
        }

        inline fun <reified T> translateFromJson(json: String?): T? =
            if (json != null) {
                Mapper.translateJsonToObject()
                    .readValue(normalizeDataStoreJson(json), T::class.java)
            } else {
                null
            }
    }
}