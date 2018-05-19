package controllers

import javax.inject._
import models.Team
import play.api._
import play.api.libs.json.JsResult.Exception
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val cc: ControllerComponents, val ws: WSClient, val configuration: play.api.Configuration) extends AbstractController(cc) {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def createLeague() = Action { implicit request: Request[AnyContent] =>
    val futureLeague: Future[WSResponse] = ws.url(configuration.underlying.getString("leaguemaker.leaguecontroller.uri") + "/createleague").get()
    val league: Try[WSResponse] = Await.ready(futureLeague, Duration.Inf).value.get
    val futureFixture: Future[WSResponse] = ws.url(configuration.underlying.getString("leaguemaker.leaguecontroller.uri") + "/createfixture").post(league.get.json)
    val fixture: Try[WSResponse] = Await.ready(futureFixture, Duration.Inf).value.get
    val futurePlayResults: Future[WSResponse] = ws.url(configuration.underlying.getString("leaguemaker.leaguecontroller.uri") + "/playfixture").post(fixture.get.json)
    val playResults: Try[WSResponse] = Await.ready(futurePlayResults, Duration.Inf).value.get
    val futureTable: Future[WSResponse] = ws.url(configuration.underlying.getString("leaguemaker.leaguecontroller.uri") + "/resulttable").post(playResults.get.json)
    val tables: Try[WSResponse] = Await.ready(futureTable, Duration.Inf).value.get

    val tablesAsJson: JsValue = tables.get.json
    val lastTable: JsValue = (tablesAsJson \ "tables").validate[JsArray].get.value.last
    val teamList: IndexedSeq[JsValue] = (lastTable \ "teams").validate[JsArray].get.value
    val teams = new ListBuffer[Team]()
    for(teamAsJson: JsValue <- teamList){
      val team: Team = teamAsJson.validate[Team].get
      teams += team
    }
    Ok(views.html.table(teams.toList))
  }

  implicit val teamReads: Reads[Team] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "points").read[Int] and
    (JsPath \ "wins").read[Int] and
    (JsPath \ "draws").read[Int] and
    (JsPath \ "losses").read[Int] and
    (JsPath \ "goalsScored").read[Int] and
    (JsPath \ "goalsConceded").read[Int]
  )(Team.apply _)
}
