package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.rpc.JsonRpcMessage
import com.agentclientprotocol.rpc.JsonRpcNotification
import com.agentclientprotocol.rpc.JsonRpcRequest
import com.agentclientprotocol.rpc.JsonRpcResponse
import com.agentclientprotocol.transport.BaseTransport
import com.agentclientprotocol.transport.StdioTransport
import com.agentclientprotocol.transport.Transport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule


class AcpSerializerTransport(
    private val parentScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val input: Source,
    private val output: Sink,
    private val name: String = StdioTransport::class.simpleName!!,
) : BaseTransport() {

    private val logger = KotlinLogging.logger {}

    private val childScope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + CoroutineName(name)
    )

    private val receiveChannel = Channel<JsonRpcMessage>(Channel.UNLIMITED)
    private val sendChannel = Channel<JsonRpcMessage>(Channel.UNLIMITED)

    override fun start() {
        if (_state.getAndUpdate { Transport.State.STARTING } != Transport.State.CREATED) error("Transport is not in ${Transport.State.CREATED.name} state")
        // Start reading messages from input
        childScope.launch(CoroutineName("$name.join-jobs")) {
            val readJob = launch(ioDispatcher + CoroutineName("$name.read-from-input")) {
                try {
                    while (currentCoroutineContext().isActive) {
                        currentCoroutineContext().ensureActive()
                        // ACP assumes working with ND Json (new line delimited Json) when working over stdio
                        val line = try {
                            input.readLine()
                        } catch (e: IllegalStateException) {
                            logger.trace(e) { "Input stream closed" }
                            break
                        } catch (e: IOException) {
                            logger.trace(e) { "Input stream likely closed" }
                            break
                        }
                        if (line == null) {
                            // End of stream
                            logger.trace { "End of stream" }
                            break
                        }

                        val jsonRpcMessage = try {
                            decodeJsonRpcMessage(line)
                        } catch (t: Throwable) {
                            logger.trace(t) { "Failed to decode JSON message: $line" }
                            continue
                        }
                        logger.trace { "Sending message to channel: $jsonRpcMessage" }
                        fireMessage(jsonRpcMessage)
                    }
                } catch (ce: CancellationException) {
                    logger.trace(ce) { "Read job cancelled" }
                    // don't throw as error
                } catch (e: Exception) {
                    logger.trace(e) { "Failed to read from input stream" }
                    fireError(e)
                } finally {
                    withContext(NonCancellable) {
                        close()
                    }
                }
                logger.trace { "Exiting read job..." }
            }
            val writeJob = launch(ioDispatcher + CoroutineName("$name.write-to-output")) {
                try {
                    for (message in sendChannel) {
                        val encoded = ACPJson.encodeToString(message)
                        try {
                            output.writeString(encoded)
                            output.writeString("\n")
                            output.flush()
                        } catch (e: IllegalStateException) {
                            logger.trace(e) { "Output stream closed" }
                            break
                        } catch (e: IOException) {
                            logger.trace(e) { "Output stream likely closed" }
                            break
                        }
                    }
                } catch (ce: CancellationException) {
                    logger.trace(ce) { "Write job cancelled" }
                    // don't throw as error
                } catch (e: Throwable) {
                    logger.trace(e) { "Failed to write to output stream" }
                    fireError(e)
                } finally {
                    withContext(NonCancellable) {
                        close()
                    }
                }
                logger.trace { "Exiting write job..." }
            }
            try {
                logger.trace { "Joining read/write jobs..." }
                if (_state.getAndUpdate { Transport.State.STARTED } != Transport.State.STARTING) logger.warn { "Transport is not in ${Transport.State.STARTING.name} state" }
                joinAll(readJob, writeJob)
            } catch (ce: CancellationException) {
                logger.trace(ce) { "Join cancelled" }
                // don't throw as error
            } catch (e: Exception) {
                logger.trace(e) { "Exception while waiting read/write jobs" }
                fireError(e)
            } finally {
                childScope.cancel()
                if (_state.getAndUpdate { Transport.State.CLOSED } != Transport.State.CLOSING) logger.warn { "Transport is not in ${Transport.State.CLOSING.name} state" }
                fireClose()
                logger.trace { "Transport closed" }
            }
        }
    }

    override fun send(message: JsonRpcMessage) {
        logger.trace { "Sending message: $message" }
        val channelResult = sendChannel.trySend(message)
        logger.trace { "Send result: $channelResult" }
    }

    override fun close() {
        val old = _state.value
        if (old == Transport.State.CLOSED || old == Transport.State.CLOSING) {
            logger.trace { "Transport is already closed or closing" }
            return
        }
        if (!_state.compareAndSet(old, Transport.State.CLOSING)) {
            logger.debug { "State changed concurrently. Do nothing" }
            return
        }

        if (sendChannel.close()) logger.trace { "Send channel closed" }
        if (receiveChannel.close()) logger.trace { "Receive channel closed" }

        runCatching { input.close() }.onFailure { logger.warn(it) { "Exception when closing input stream" } }
        runCatching { output.close() }.onFailure { logger.warn(it) { "Exception when closing output stream" } }
    }

    @OptIn(ExperimentalSerializationApi::class)
    val ACPJson: Json by lazy {
        Json {
            serializersModule = SerializersModule {
                include(com.agentclientprotocol.rpc.ACPJson.serializersModule)
            }
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }
    }

    fun decodeJsonRpcMessage(jsonString: String): JsonRpcMessage {
        val element = try {
            ACPJson.parseToJsonElement(jsonString)
        } catch (e: SerializationException) {
            // maybe there is some garbage output at the beginning of the like, try to find where JSON starts
            val jsonStart = jsonString.indexOfFirst { it == '{' }
            if (jsonStart == -1) {
                throw e
            }
            val jsonStartTrimmed = jsonString.substring(jsonStart)
            ACPJson.parseToJsonElement(jsonStartTrimmed)
        }
        require(element is JsonObject) { "Expected JSON object" }

        val hasId = element.containsKey("id")
        val hasMethod = element.containsKey("method")
        val hasResult = element.containsKey("result")
        val hasError = element.containsKey("error")

        return when {
            hasId && (hasResult || hasError) -> ACPJson.decodeFromJsonElement(JsonRpcResponse.serializer(), element)
            hasId && hasMethod -> ACPJson.decodeFromJsonElement(JsonRpcRequest.serializer(), element)
            hasMethod -> ACPJson.decodeFromJsonElement(JsonRpcNotification.serializer(), element)
            else -> error("Unable to determine JsonRpcMessage type from JSON structure")
        }
    }
}