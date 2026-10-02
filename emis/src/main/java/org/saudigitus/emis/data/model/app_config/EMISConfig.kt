package org.saudigitus.emis.data.model.app_config

import com.fasterxml.jackson.databind.ObjectMapper
import org.saudigitus.emis.utils.Mapper
import timber.log.Timber

open class EMISConfig {
    private fun toJson(): String = Mapper.translateJsonToObject().writeValueAsString(this)

    companion object {
        private const val JSON_WRAPPER_PREFIX = "JsonWrapper(json="

        /**
         * DHIS2 Android SDK 1.14.x persists data store values as the toString() of its
         * internal JsonWrapper value class - e.g. `JsonWrapper(json={"a":1})` - instead of
         * the raw JSON the server holds. Unwrap that envelope when it is present so both
         * the wrapped and the plain form parse. Public because translateFromJson is inline.
         */
        fun unwrapDataStoreValue(json: String?): String? {
            if (json == null) return null
            val trimmed = json.trim()
            return if (trimmed.startsWith(JSON_WRAPPER_PREFIX) && trimmed.endsWith(")")) {
                trimmed.substring(JSON_WRAPPER_PREFIX.length, trimmed.length - 1)
            } else {
                json
            }
        }

        open fun fromJson(json: String?): List<EMISConfigItem>? = if (json != null) {
            val mapper = ObjectMapper()

            try {
                Mapper.translateJsonToObject()
                    .readValue(
                        unwrapDataStoreValue(json),
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
                    .readValue(unwrapDataStoreValue(json), T::class.java)
            } else {
                null
            }
    }
}