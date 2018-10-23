package common

import java.nio.charset.Charset

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.ByteString
import io.circe.Json
import pdi.jwt.{JwtAlgorithm, JwtCirce}
import sangria.ast.Document
import sangria.parser.QueryParser
import sangria.renderer.{QueryRenderer, QueryRendererConfig}

import scala.collection.immutable.Seq
import scala.util.{Failure, Success}

object GraphQLRequestUnmarshaller {
  val `application/graphql` = MediaType.applicationWithFixedCharset("graphql", HttpCharsets.`UTF-8`, "graphql")
  val AuthTokenRegexp = "Bearer (.*)".r

  def explicitlyAccepts(mediaType: MediaType): Directive0 =
    headerValuePF {
      case Accept(ranges) if ranges.exists(range ⇒ !range.isWildcard && range.matches(mediaType)) ⇒ ranges
    }.flatMap(_ ⇒ pass)

  def optionalJwtToken(secret: String): Directive1[Option[Json]] =
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(AuthTokenRegexp(value)) ⇒
        JwtCirce.decodeJson(value, secret, Seq(JwtAlgorithm.HS256)) match {
          case Success(jsonValue) ⇒ provide(Some(jsonValue))
          case Failure(_) ⇒ complete(StatusCodes.Unauthorized)
        }
      case Some(_) ⇒ complete(StatusCodes.Unauthorized)
      case _ ⇒ provide(None)
    }

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  def mediaTypes: Seq[MediaType.WithFixedCharset] =
    List(`application/graphql`)

  implicit final def documentMarshaller(implicit config: QueryRendererConfig = QueryRenderer.Compact): ToEntityMarshaller[Document] =
    Marshaller.oneOf(mediaTypes: _*) { mediaType ⇒
      Marshaller.withFixedContentType(ContentType(mediaType)) { json ⇒
        HttpEntity(mediaType, QueryRenderer.render(json, config))
      }
    }

  implicit final val documentUnmarshaller: FromEntityUnmarshaller[Document] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map {
        case ByteString.empty ⇒ throw Unmarshaller.NoContentException
        case data ⇒
          import sangria.parser.DeliveryScheme.Throw

          QueryParser.parse(data.decodeString(Charset.forName("UTF-8")))
      }
}
