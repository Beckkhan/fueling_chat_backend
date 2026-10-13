package com.fuelingchat.infra.llm

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FakeEchoArgs(
    @property:LLMDescription("The id to echo.")
    @SerialName("echo_id")
    val echoId: String,
)

class FakeEchoTool :
    SimpleTool<FakeEchoArgs>(
        argsType = typeToken<FakeEchoArgs>(),
        name = NAME,
        description = "Fake tool returning fixed text.",
    ) {
    override suspend fun execute(args: FakeEchoArgs): String = FIXED_RESULT

    companion object {
        const val NAME = "fake_echo"
        const val FIXED_RESULT = "fake tool fixed text"
    }
}
