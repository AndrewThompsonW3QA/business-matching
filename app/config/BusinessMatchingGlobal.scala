/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package config

import akka.actor.ActorSystem
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Mode.Mode
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, ServicesConfig}
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}


trait WSHttp extends WSGet with HttpGet with WSPut with HttpPut with WSPost with HttpPost with WSDelete with HttpDelete with WSPatch with HttpPatch {
  override val hooks = NoneRequired
  override protected def actorSystem: ActorSystem = Play.current.actorSystem
  override protected def configuration: Option[Config] = Some(Play.current.configuration.underlying)
  protected def mode: Mode = Play.current.mode
  protected def runModeConfiguration: Configuration = Play.current.configuration
}
object WSHttp extends WSHttp

object MicroserviceAuditConnector extends AuditConnector {
  override lazy val auditingConfig = LoadAuditingConfig("auditing")
}

object MicroserviceAuthConnector extends AuthConnector with ServicesConfig with WSHttp {
  override val authBaseUrl = baseUrl("auth")
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport  {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
  override protected def appNameConfiguration: Configuration = Play.current.configuration
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport  {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport  {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector
  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object BusinessMatchingGlobal extends DefaultMicroserviceGlobal {
  override val auditConnector = MicroserviceAuditConnector
  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")
  override val loggingFilter = MicroserviceLoggingFilter
  override val microserviceAuditFilter = MicroserviceAuditFilter
  override val authFilter = Some(MicroserviceAuthFilter)
}
