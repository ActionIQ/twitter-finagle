package com.twitter.finagle.stats

import com.twitter.finagle.stats.StandardStatsReceiver.description
import com.twitter.finagle.stats.StandardStatsReceiver.rootStatsReceiver
import com.twitter.finagle.stats.StandardStatsReceiver.serverCount
import java.util.concurrent.atomic.AtomicInteger

object StandardStatsReceiver {

  // a shared counter among an application,
  // used to create incremental unique labels for servers with all protocols
  // exposed for testing set up
  private[finagle] val serverCount = new AtomicInteger(0)

  private val standardPrefix = "standard-service-metric-v1"
  private val description = "Standard Service Metrics"
  private val rootStatsReceiver = DefaultStatsReceiver.scope(standardPrefix).scope("srv")
}

/**
 * A StatsReceiver proxy that configures all counter, stat, and gauge with:
 * 1. standard labels other than application customized labels
 * 2. isStandard attribute in Metric Metadata
 * 3. default description in Metric Metadata
 *
 * Counters are configured with [[UnlatchedCounter]].
 *
 * @note This set of metrics are expected to be exported with uniform formats and attributes,
 *       they are set with a standard prefix and neglect the [[useCounterDeltas]] and
 *       [[scopeSeparator]] flags.
 *
 * @param sourceRole Represents the "role" this service plays with respect to this metric
 * @param protocol protocol library name, e.g., thriftmux, http
 */
private[finagle] case class StandardStatsReceiver(
  sourceRole: SourceRole,
  protocol: String)
    extends StatsReceiverProxy {

  require(sourceRole == SourceRole.Server, "StandardStatsReceiver should be apply to servers")

  private[this] val serverScope = sourceRole match {
    case SourceRole.Server => sourceRole.toString.toLowerCase + s"-${serverCount.getAndIncrement()}"
    case _ => "unsupported" // won't reach
  }

  val self: StatsReceiver =
    BroadcastStatsReceiver(
      Seq(rootStatsReceiver, rootStatsReceiver.scope(protocol).scope(serverScope)))

  override def counter(metricBuilder: MetricBuilder): Counter =
    self.counter(metricBuilder.withStandard.withUnlatchedCounter.withDescription(description))

  override def stat(metricBuilder: MetricBuilder): Stat =
    self.stat(metricBuilder.withStandard.withDescription(description))

  override def addGauge(metricBuilder: MetricBuilder)(f: => Float): Gauge =
    self.addGauge(metricBuilder.withStandard.withDescription(description))(f)
}
