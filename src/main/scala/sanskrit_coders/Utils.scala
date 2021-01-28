package sanskrit_coders

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

//noinspection ScalaDocMissingParameterDescription
object Utils {
  /**
    * Map  Seq[Future[T]] to Seq[Future[Try[T]]]
    *
    * @param futures
    * @param ec
    * @tparam T
    * @return
    */
  def mapValuesToTrys[T](futures: Seq[Future[T]])(implicit ec: ExecutionContext): Seq[Future[Try[T]]] =
    futures.map(_.map { Success(_) }.recover { case t => Failure(t) })

  /**
    * Map Seq[Future[T]] to Future[Seq[Try[T]]]
    *
    * @param futures
    * @param ec
    * @tparam T
    * @return
    */
  def getFutureOfTrys[T](futures: Seq[Future[T]])(implicit ec: ExecutionContext): Future[Seq[Try[T]]] =
    Future.sequence(mapValuesToTrys(futures = futures))

  def appendToStringTillLimit(someStringBuilder: StringBuilder, newString: String, maxLines:Int=50) = {
    if (someStringBuilder.split('\n').size < maxLines) {
      someStringBuilder.append(newString)
    }
  }

  def runCommandLimitOutput(commandString: String, maxLines:Int=50): (Int, StringBuilder, StringBuilder) = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    import scala.sys.process._
    val status = s"$commandString" ! ProcessLogger(appendToStringTillLimit(someStringBuilder=stdout, _, maxLines), appendToStringTillLimit(someStringBuilder=stderr, _, maxLines))
    return Tuple3(status, stdout, stderr)
  }

  def runCommandSeqLimitOutput(commandSeq: Seq[String], maxLines:Int=50): (Int, StringBuilder, StringBuilder) = {
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    import scala.sys.process._
    val status = commandSeq ! ProcessLogger(appendToStringTillLimit(someStringBuilder=stdout, _, maxLines), appendToStringTillLimit(someStringBuilder=stderr, _, maxLines))
    return Tuple3(status, stdout, stderr)
  }

  
}

