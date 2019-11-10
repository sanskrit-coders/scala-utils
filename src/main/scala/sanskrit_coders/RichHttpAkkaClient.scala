package sanskrit_coders

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.FileIO
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

/**
  * A client robust to redirects and such. Copied and adapted from https://github.com/akka/akka-http/issues/195
  *
  * Usage: call httpClientWithRedirect().
  *   (Optional:) implicit val actorSystem = ActorSystem("xyz")
  *   private val redirectingClient: HttpRequest => Future[HttpResponse] = getClientWithAkkaSystem()
  *   RichHttpClient.httpResponseToString(redirectingClient(HttpRequest(uri = uri))).map(responseString => {log.debug(responseString)})
 *
 */
//noinspection ScalaDocMissingParameterDescription
object RichHttpAkkaClient {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  type HttpClient = HttpRequest ⇒ Future[HttpResponse]

  def getClientWithAkkaSystem()(implicit system: ActorSystem = ActorSystem("httpAkka")): HttpRequest => Future[HttpResponse] = {
    import scala.concurrent._
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    val simpleClient: HttpRequest => Future[HttpResponse] = Http().singleRequest(_: HttpRequest)
    val redirectingClient: HttpRequest => Future[HttpResponse] = RichHttpAkkaClient.httpClientWithRedirect(simpleClient)
    redirectingClient
  }

  /**
    * Given a response, follow a redirect - or just yield the response.
    *
    * @param client A function of the type HttpClient
    * @param response
    * @param system
    * @return
    */
  // We'll use this function below in httpClientWithRedirect.
  def redirectOrResult(client: HttpClient)(response: HttpResponse)(implicit system: ActorSystem = ActorSystem("httpAkka")): Future[HttpResponse] =
    response.status match {
      case StatusCodes.Found | StatusCodes.MovedPermanently | StatusCodes.SeeOther ⇒
        val newUri = response.header[Location].get.uri
        // Always make sure you consume the response entity streams (of type Source[ByteString,Unit]) by for example connecting it to a Sink (for example response.discardEntityBytes() if you don’t care about the response entity), since otherwise Akka HTTP (and the underlying Streams infrastructure) will understand the lack of entity consumption as a back-pressure signal and stop reading from the underlying TCP connection!
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        response.discardEntityBytes()
        log.info(s"redirected to ${newUri.toString()}")

        // change to GET method as allowed by https://tools.ietf.org/html/rfc7231#section-6.4.3
        // TODO: keep HEAD if the original request was a HEAD request as well?
        // TODO: do we want to keep something of the original request like custom user-agents, cookies
        //       or authentication headers?
        client(HttpRequest(method = HttpMethods.GET, uri = newUri))
      // TODO: what to do on an error? Also report the original request/response?

      // TODO: also handle 307, which would require resending POST requests
      case _ ⇒ Future.successful(response)
    }

  /**
    * Turns a  HttpClient into a Http client which handles redirects.
    *
    * @param client
    * @param system
    * @return a recursive function of the type HttpClient
    */
  def httpClientWithRedirect(client: HttpClient)(implicit system: ActorSystem = ActorSystem("httpAkka")): HttpClient = {
    // We are defining a function below!
    implicit val context: ExecutionContextExecutor = system.dispatcher
    lazy val redirectingClient: HttpClient =
      req ⇒ client(req).flatMap(redirectOrResult(redirectingClient)) // recurse to support multiple redirects

    redirectingClient
  }

  /**
    * A convenience function to convert a Future[HttpResponse] to a Future[String]. There are potentially better ways of doing this using the akka stream API.
    *
    * @param responseFuture
    * @return
    */
  def httpResponseToString(responseFuture: Future[HttpResponse])(implicit system: ActorSystem = ActorSystem("httpAkka")): Future[String] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val context: ExecutionContextExecutor = system.dispatcher
    responseFuture.flatMap {
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        // The below is a Future[String] which is filled when the stream is read. That future is what we return!
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
      case resp@HttpResponse(code, headers, entity, protocol) =>
        val message = s"Request failed, response code: ${code}, Headers: {${headers.mkString(",")}}, entity: {${entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)}}, protocol: {${protocol.toString}}, Response: {${resp}}, " 
        log.warn(message)
        // Always make sure you consume the response entity streams (of type Source[ByteString,Unit]) by for example connecting it to a Sink (for example response.discardEntityBytes() if you don’t care about the response entity), since otherwise Akka HTTP (and the underlying Streams infrastructure) will understand the lack of entity consumption as a back-pressure signal and stop reading from the underlying TCP connection!
        resp.discardEntityBytes()
        Future.failed(new Exception(message))
    }
  }

  def dumpToFile(uri: String, destinationPathStr:String)(implicit system: ActorSystem = ActorSystem("httpAkka")) = {
    val redirectingClient = getClientWithAkkaSystem()
    val httpResponseFuture = redirectingClient(HttpRequest(uri = uri))
    val destinationPath = new java.io.File(destinationPathStr)
    assert(destinationPath.getParentFile.mkdirs())
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()
    val fileSink = FileIO.toPath(destinationPath.toPath)
    val ioResultFuture = httpResponseFuture.flatMap(response => {
      response.entity.dataBytes.runWith(fileSink)
    })
  }
}
