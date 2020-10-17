package io.parapet.core

import com.typesafe.scalalogging.StrictLogging
import io.parapet.core.Scheduler.SchedulerConfig
import shapeless.{Lens, lens}

/**
  * ∏åRÂπ∑†
  */
object Parapet extends StrictLogging {

  val DEBUG_MODE = false

  val ParapetPrefix = "parapet"

  case class ParConfig(
                        processBufferSize: Int,
                        schedulerConfig: SchedulerConfig)

  val processBufferSizeLens: Lens[ParConfig, Int] = lens[ParConfig].processBufferSize

  // Scheduler config lenses
  val numberOfWorkersLens: Lens[ParConfig, Int] = lens[ParConfig].schedulerConfig.numberOfWorkers


  object ParConfig {
    val default: ParConfig = ParConfig(
      processBufferSize = -1, // unbounded
      schedulerConfig = SchedulerConfig.default)
  }

}