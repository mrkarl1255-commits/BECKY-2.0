package com.becky.bridge.capability

import com.becky.bridge.communication.BeckyCommandHandler
import com.becky.bridge.communication.BeckyCommandResult
import com.becky.bridge.communication.BeckyCommands
import com.becky.bridge.communication.asWatchBattery
import com.becky.bridge.communication.asWatchHeartRate
import com.becky.bridge.communication.asWatchNotifications
import com.becky.bridge.communication.asWatchStatus
import com.becky.bridge.communication.asWatchSteps
import com.becky.bridge.communication.asWatchTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The six BLE watch commands from Phase 2 ([BeckyCommands]), each
 * wrapped here as a [Capability] so the Assistant/Becky layer can
 * invoke them purely by id/description - exactly like it would invoke
 * any future non-BLE capability. This is precisely what keeps
 * GET_TIME/GET_HEART_RATE/etc. from becoming "the limit of the
 * conversation": they are just the first capabilities registered in
 * [com.becky.bridge.voice.VoiceRepository], not a special case anywhere
 * in the Assistant layer.
 *
 * None of this touches [BeckyCommandHandler]'s own logic - every class
 * here only calls its already-public `getXxx()` methods, same as the
 * manual test buttons in `DiagnosticsActivity`.
 */

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Maps a raw [BeckyCommandResult] (BLE/command layer) to a [CapabilityResult] (capability layer). */
private fun BeckyCommandResult.toCapabilityResult(onSuccess: (BeckyCommandResult.Success) -> String?): CapabilityResult =
    when (this) {
        is BeckyCommandResult.Success -> onSuccess(this)
            ?.let { CapabilityResult.Success(spokenText = it, raw = this.response) }
            ?: CapabilityResult.Failed("El reloj respondio pero no se pudo interpretar el resultado")
        BeckyCommandResult.NotConnected -> CapabilityResult.NotAvailable
        BeckyCommandResult.Timeout -> CapabilityResult.Timeout
        is BeckyCommandResult.Failed -> CapabilityResult.Failed(this.error)
    }

class GetTimeCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_TIME
    override val description: String = "Dice la hora actual reportada por el reloj conectado."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getTime().toCapabilityResult { result ->
            result.asWatchTime()?.let { "Son las ${timeFormat.format(Date(it.epochMillis))}." }
        }
}

class GetHeartRateCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_HEART_RATE
    override val description: String = "Reporta el ritmo cardiaco actual medido por el reloj."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getHeartRate().toCapabilityResult { result ->
            result.asWatchHeartRate()?.let { "Tu ritmo cardiaco es de ${it.bpm} pulsaciones por minuto." }
        }
}

class GetStepsCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_STEPS
    override val description: String = "Informa cuantos pasos ha dado el usuario segun el reloj."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getSteps().toCapabilityResult { result ->
            result.asWatchSteps()?.let { "Has dado ${it.steps} pasos." }
        }
}

class GetBatteryCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_BATTERY
    override val description: String = "Reporta el nivel de bateria actual del reloj."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getBattery().toCapabilityResult { result ->
            result.asWatchBattery()?.let { "El reloj tiene ${it.percent} por ciento de bateria." }
        }
}

class GetNotificationsCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_NOTIFICATIONS
    override val description: String = "Lee las notificaciones pendientes en el reloj."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getNotifications().toCapabilityResult { result ->
            result.asWatchNotifications()?.let {
                if (it.count == 0) "No tienes notificaciones pendientes."
                else "Tienes ${it.count} notificaciones pendientes."
            }
        }
}

class GetWatchStatusCapability(private val commandHandler: BeckyCommandHandler) : Capability {
    override val id: String = BeckyCommands.GET_WATCH_STATUS
    override val description: String = "Reporta el estado general del reloj."
    override suspend fun execute(): CapabilityResult =
        commandHandler.getWatchStatus().toCapabilityResult { result ->
            result.asWatchStatus()?.let { "El estado del reloj es: ${it.status}." }
        }
}
