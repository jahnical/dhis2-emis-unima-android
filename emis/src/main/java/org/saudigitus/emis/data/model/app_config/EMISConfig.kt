package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.databind.ObjectMapper
import org.saudigitus.emis.utils.Mapper
import timber.log.Timber

class EMISConfig {
    private fun toJson(): String = Mapper.translateJsonToObject().writeValueAsString(this)

    companion object {
        fun fromJson(json: String?): List<EMISConfigItem>? = if (json != null) {
            val mapper = ObjectMapper()

            try {
                Mapper.translateJsonToObject()
                    .readValue(
                        json,
                        mapper.typeFactory.constructCollectionType(
                            List::class.java,
                            EMISConfigItem::class.java,
                        ),
                    )
            } catch (ex: Exception) {
                val jsonPreview = if (json.length > 200) {
                    json.substring(0, 200) + "..."
                } else {
                    json
                }
                Timber.e(
                    ex,
                    "Failed to parse EMISConfig from JSON.\nError type: ${ex::class.simpleName}\nError message: ${ex.message}\nJSON length: ${json.length}\nJSON preview: $jsonPreview"
                )
                null
            }
        } else {
            null
        }

        inline fun <reified T> translateFromJson(json: String?): T? =
            if (json != null) {
                Mapper.translateJsonToObject()
                    .readValue(json, T::class.java)
            } else {
                null
            }
    }
}