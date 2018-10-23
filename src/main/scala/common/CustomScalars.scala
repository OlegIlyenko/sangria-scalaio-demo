package common

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import sangria.ast
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation

import scala.util.{Failure, Success, Try}

object CustomScalars {
  case object DateCoercionViolation extends ValueCoercionViolation("Date value expected")

  def parseDate(s: String) = Try(LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)) match {
    case Success(date) ⇒ Right(date)
    case Failure(_) ⇒ Left(DateCoercionViolation)
  }

  implicit val LocalDateType = ScalarType[LocalDate]("Date",
    description = Some("Represents local date. Serialized as ISO-formatted string."),
    coerceOutput = (d, _) ⇒ d.format(DateTimeFormatter.ISO_DATE),
    coerceUserInput = {
      case s: String ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    },
    coerceInput = {
      case ast.StringValue(s, _, _, _, _) ⇒ parseDate(s)
      case _ ⇒ Left(DateCoercionViolation)
    })
}
