package sanskrit_coders

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.Materializer
import akka.util.ByteString
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A client robust to redirects and such. Copied and adapted from https://github.com/akka/akka-http/issues/195
  *
  * Usage: call httpClientWithRedirect().
  *   implicit val actorMaterializer = ActorMaterializer()
  *   implicit val executionContext = system.dispatcher
  *   private val simpleClient: HttpRequest => Future[HttpResponse] = Http(context.system).singleRequest(_: HttpRequest)
  *   private val redirectingClient: HttpRequest => Future[HttpResponse] = RichHttpClient.httpClientWithRedirect(simpleClient)
  *   RichHttpClient.httpResponseToString(redirectingClient(HttpRequest(uri = uri))).map(responseString => {
      *log.debug(responseString)
      *val archiveItem = jsonHelper.fromString[ItemInfo](responseString)
      *archiveItem.toPodcast(filePattern = podcastRequest.filePattern, useArchiveOrder = podcastRequest.useArchiveOrder, podcast = podcastRequest.podcastTemplate)
    *})
 **
 *
 To dump to file:
      *val httpResponseFuture = redirectingClient(HttpRequest(uri = dict.dictTarUrl))
 **
 val destinationPath = Paths.get(dict.destinationFolder,  dict.dictTarUrl.split("/").last)
      *assert(new java.io.File(dict.destinationFolder).mkdirs())
      *val fileSink = FileIO.toPath(destinationPath)
      *val ioResultFuture = httpResponseFuture.flatMap(response => {
        *response.entity.dataBytes.runWith(fileSink)
      *})
 *
 */
//noinspection ScalaDocMissingParameterDescription
object RichHttpClient {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  type HttpClient = HttpRequest ⇒ Future[HttpResponse]

  /**
    * Given a response, follow a redirect - or just yield the response.
    *
    * @param client A function of the type HttpClient
    * @param response
    * @param materializer
    * @return
    */
  // We'll use this function below in httpClientWithRedirect.
  def redirectOrResult(client: HttpClient)(response: HttpResponse)(implicit materializer: Materializer): Future[HttpResponse] =
    response.status match {
      case StatusCodes.Found | StatusCodes.MovedPermanently | StatusCodes.SeeOther ⇒
        val newUri = response.header[Location].get.uri
        // Always make sure you consume the response entity streams (of type Source[ByteString,Unit]) by for example connecting it to a Sink (for example response.discardEntityBytes() if you don’t care about the response entity), since otherwise Akka HTTP (and the underlying Streams infrastructure) will understand the lack of entity consumption as a back-pressure signal and stop reading from the underlying TCP connection!
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
    * @param ec
    * @param materializer
    * @return a recursive function of the type HttpClient
    */
  def httpClientWithRedirect(client: HttpClient)(implicit ec: ExecutionContext, materializer: Materializer): HttpClient = {
    // We are defining a function below!
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
  def httpResponseToString(responseFuture: Future[HttpResponse])(implicit ec: ExecutionContext, materializer: Materializer): Future[String] = {
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

}