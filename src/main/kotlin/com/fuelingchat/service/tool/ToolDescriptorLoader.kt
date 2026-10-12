package com.fuelingchat.service.tool

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ToolDescriptorData(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,
)

class ToolDescriptorLoader(
    private val resourceName: String = DEFAULT_RESOURCE,
) {
    fun load(): ToolDescriptorData {
        val resource =
            requireNotNull(javaClass.classLoader.getResource(resourceName)) {
                "tool descriptor missing: $resourceName"
            }
        val root = Json.parseToJsonElement(resource.readText()).jsonObject
        return ToolDescriptorData(
            name = root.getValue("name").jsonPrimitive.content,
            description = root.getValue("description").jsonPrimitive.content,
            inputSchema = root.getValue("input_schema"),
        )
    }

    companion object {
        const val DEFAULT_RESOURCE = "tools/get_fueling_info.json"
    }
}
