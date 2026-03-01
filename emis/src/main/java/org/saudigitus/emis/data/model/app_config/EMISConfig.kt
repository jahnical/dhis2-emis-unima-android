package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.databind.ObjectMapper
import org.saudigitus.emis.utils.Mapper
import timber.log.Timber

open class EMISConfig {
    private fun toJson(): String = Mapper.translateJsonToObject().writeValueAsString(this)

    companion object {
        open fun fromJson(json: String?): List<EMISConfigItem>? = if (json != null) {
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
                Timber.e(ex, "Failed to parse EMISConfig from JSON")
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