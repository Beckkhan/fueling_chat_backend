package com.fuelingchat.service.tool

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolDescriptorLoaderTest {
    @Test
    fun loadsDescriptorFromBundledResource() {
        val descriptor = ToolDescriptorLoader().load()

        assertEquals("get_fueling_info", descriptor.name)
        assertTrue(descriptor.description.contains("four internal databases"))
        val schema = descriptor.inputSchema.jsonObject
        val fuelingIdProperty = schema["properties"]!!.jsonObject["fueling_id"]!!.jsonObject
        assertEquals("string", fuelingIdProperty["type"]!!.jsonPrimitive.content)
        assertEquals(
            "fueling_id",
            schema["required"]!!
                .jsonArray
                .first()
                .jsonPrimitive.content,
        )
        assertEquals(false, schema["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun bundledDescriptorIsByteIdenticalToContractTool001() {
        val resource = javaClass.classLoader.getResource(ToolDescriptorLoader.DEFAULT_RESOURCE)!!
        val content = resource.readText()

        assertEquals(CONTRACT_TOOL_001, content)
    }

    @Test
    fun missingDescriptorFailsNamingTheFile() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                ToolDescriptorLoader("tools/does_not_exist.json").load()
            }

        assertTrue(error.message!!.contains("tools/does_not_exist.json"))
    }

    private companion object {
        val CONTRACT_TOOL_001 =
            """
            {
              "name": "get_fueling_info",
              "description": "Look up one fueling by its id across four internal databases (fueling, gas_station, payment, vendors). Returns the fueling record, its gas station, its payments and the vendor-side order rows as aggregated JSON. Timestamps are ISO-8601 UTC strings; JSON columns are returned as their JSON text.",
              "input_schema": {
                "type": "object",
                "properties": {
                  "fueling_id": {
                    "type": "string",
                    "description": "The fueling identifier to look up. Use exactly the value given by the user."
                  }
                },
                "required": ["fueling_id"],
                "additionalProperties": false
              }
            }
            """.trimIndent() + "\n"
    }
}
